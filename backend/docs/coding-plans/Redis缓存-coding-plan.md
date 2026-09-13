# lingxi-hr Redis 缓存引入 - Coding Plan

> **所属模块：** lingxi-hr（成员D）| **类型：** 性能优化（现有功能不回滚，纯增量改造）
> **技术栈：** Spring Boot 2.7 + MyBatis + Feign + Redis（`lingxi-common` 已配 `RedisConfig`/`RedisUtil`）
> **背景：** Redis 基建已就绪但 HR 服务仅 1 处使用（`MockInterviewAgent` 会话号自增）。读路径存在：① 4 个 Service 重复实现 `fetchUsers` 跨服务 Feign；② 面试列表对每行发 Feign 取岗位标题（N+1）；③ 企业/岗位等低变数据反复查库。
> **范围：** 用户信息缓存 + 企业信息缓存 + 岗位信息缓存（A/B 三级，均 Cache-Aside）；D 级可选（Mock 配额改 Redis 计数）。
> **状态：** 🚧 待开发

---

## 〇、背景与决策

### 为什么用 Redis（而非本地 Map）
- 多实例部署（两机 ECS 计划）下本地缓存各实例不一致；Redis 全局唯一。
- `RedisConfig` + `RedisUtil`（`set/get/delete/hasKey/expire/increment`）已就绪，注入即用，无需新增依赖。
- 跨服务数据（用户/岗位）由 lingxi-user/lingxi-job 拥有，本服务只做「读透传缓存」，只能靠 TTL 最终一致——这正是 Redis 的典型场景。

### 决策
1. **统一走 `RedisUtil`（Cache-Aside 手工读写）**，不引入 Spring Cache `@Cacheable`。
   理由：列表读需要批量 `mget` + 未命中回源合并，注解式难表达；写路径需要按场景精确失效；现有代码风格即显式 `RedisUtil`。
2. **序列化**：`RedisTemplate<String,Object>` 已配 `GenericJackson2JsonRedisSerializer` + `DefaultTyping.NON_FINAL`，存储 DTO（`SysUserDTO`/`JobHcOverviewDTO` 均 `Serializable`）自带 `@class` 元信息，`RedisUtil.<T>get(key)` 强转可用；**不得**存 `StringRedisTemplate`（除非纯字符串计数）。
3. **失效策略**：本库数据（企业）写路径显式 `delete` + TTL 兜底；跨服务数据（用户/岗位）只靠 TTL 最终一致，写路径不联动。
4. **负数缓存**：跨服务查询失败/不存在的结果，用短 TTL 占位，防缓存穿透；Feign 降级逻辑（batch→单查→兜底）保留。
5. **不缓存**：成员权限校验 `selectByCompanyAndUser`（移除成员/改角色需即时生效，缓存有权限时效风险）；面试/评估记录（变更频繁且直接 PK 查询）。

---

## 一、缓存总览

| 级别 | 缓存 | Key | Value | TTL | 失效方式 | 主要解决 |
|---|---|---|---|---|---|---|
| A | 用户信息 | `hr:user:{userId}` | `SysUserDTO` | 10 min | TTL 自然过期 | 4 处 `fetchUsers` 重复 Feign |
| B | 企业信息 | `hr:company:{companyId}` | `HrCompany`(→VO 字段) | 30 min | `updateCompanyInfo` 显式删除 | 企业名/认证状态多处读 |
| B | 岗位信息 | `hr:job:{jobId}` | `JobHcOverviewDTO` | 60 s | TTL 自然过期 | Offer/Interview 反复调 lingxi-job |
| C(可选) | Mock 配额 | `mock:quota:{candidateId}:{yyyyMMdd}` | `Long`（计数） | 当天 24 点 | INCR 到上限即 40018 | `checkQuota` 每次查库 |

> 收益顺序：A（修 N+1 外最大头）> B-岗位（修面试列表 N+1）> B-企业 > C。

---

## 二、涉及文件清单

### 新建
| 文件 | 说明 |
|------|------|
| `service/cache/UserInfoCache.java` | 用户信息批量读穿缓存：`mget` 命中走缓存 → 未命中 Feign 批量回源 → 回填 |
| `service/cache/CompanyCache.java` | 企业信息：`get(companyId)` / `invalidate(companyId)` |
| `service/cache/JobInfoCache.java` | 岗位信息：`get(jobId)`（60s）/ `title(jobId)` 便捷方法 |

### 修改
| 文件 | 修改 |
|------|------|
| `service/impl/HrCandidateServiceImpl.java` | `fetchUsers` → 委托 `UserInfoCache`（`queryOfferRecordAppIds`/`queryLastOfferStatus` 保持直查，Offer 状态不缓存） |
| `service/impl/HrInterviewServiceImpl.java` | `fetchUsers` → 委托 `UserInfoCache`；`fetchJobTitle`（N+1）改走 `JobInfoCache.title(jobId)` |
| `service/impl/HrOfferServiceImpl.java` | `fetchUsers` → 委托 `UserInfoCache`；`queryJobBestEffort`/`queryJobOrThrow`/`fetchJobTitles` 改走 `JobInfoCache`；`queryCompanyNameBestEffort` 改走 `CompanyCache` |
| `service/impl/HrCompanyServiceImpl.java` | `getCompanyInfo`/`getCertificationStatus` 读 `CompanyCache`；`updateCompanyInfo`/`createCompany`/`submitCertification` 失效 |
| `service/impl/HrAccountServiceImpl.java` | `getProfile` 用户/企业读改走缓存（薄） |
| `service/impl/HrCompanyMemberServiceImpl.java` | `fetchUsers` → 委托 `UserInfoCache` |
| `agent/MockInterviewAgent.java`（D 级可选） | `checkQuota` 改 Redis 计数 |

### 复用（不改）
| 文件 | 说明 |
|------|------|
| `lingxi-common RedisUtil.java` / `RedisConfig.java` | 已提供 get/set/delete/increment/setIfAbsent，注入即用 |
| `feign/UserFeignClient.java` / `JobFeignClient.java` | 批量回源接口不变 |
| `application.yml` spring.redis | 已配 host/port/password，无需改 |

---

## 三、实现细节

### 3.1 UserInfoCache（A 级）

```java
@Component
@RequiredArgsConstructor
public class UserInfoCache {
    private static final String PREFIX = "hr:user:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final RedisUtil redisUtil;
    private final UserFeignClient userFeignClient;

    /**
     * 批量读取：缓存命中直接返回；未命中收集 userIds 走 batchUsers，
     * 降级单查，回填缓存后返回。失败返回空 map（调用方原有兜底逻辑不变）。
     */
    public Map<Long, SysUserDTO> batchGetOrLoad(Collection<Long> userIds) {
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, SysUserDTO> result = new HashMap<>();
        List<Long> miss = new ArrayList<>();
        for (Long id : distinct) {
            SysUserDTO cached = redisUtil.get(PREFIX + id);
            if (cached != null) {
                result.put(id, cached);
            } else {
                miss.add(id);
            }
        }
        if (miss.isEmpty()) {
            return result;
        }
        // 未命中回源（保留 batch→单查→兜底；结果含 null 的 userIds 写 null 占位，防穿透）
        Map<Long, SysUserDTO> loaded = fetchFromFeign(miss);
        for (Long id : miss) {
            SysUserDTO u = loaded.get(id);
            result.put(id, u);
            if (u != null) {
                redisUtil.set(PREFIX + id, u, TTL.toMinutes(), TimeUnit.MINUTES);
            } else {
                redisUtil.set(PREFIX + id, EMPTY_MARKER, NEG_TTL, TimeUnit.MINUTES); // 2min 占位
            }
        }
        return result;
    }

    public SysUserDTO get(Long userId) { /* 单查便捷方法，委托 batchGetOrLoad */ }
    public void invalidate(Long userId) { redisUtil.delete(PREFIX + userId); }
}
```

- 回源 `fetchFromFeign` 逻辑**原样搬**自现有各 Service 的 `fetchUsers`：`batchUsers(ids)` → 失败降级循环 `getUserById` → 兜底空。
- **负值占位**：查不到的用户写 2 min 占位（`EMPTY_MARKER` 用空 `SysUserDTO` 或专用常量），读侧判空跳过。
- 各 Service 的私有 `fetchUsers` 改为一行委托：`return userInfoCache.batchGetOrLoad(userIds);`（4 处重复实现删除）。

### 3.2 CompanyCache（B 级）

```java
@Component
@RequiredArgsConstructor
public class CompanyCache {
    private static final String PREFIX = "hr:company:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final RedisUtil redisUtil;
    private final HrCompanyMapper hrCompanyMapper;

    public HrCompany get(Long companyId) {
        if (companyId == null) return null;
        HrCompany cached = redisUtil.get(PREFIX + companyId);
        if (cached != null) return cached;
        HrCompany company = hrCompanyMapper.selectById(companyId);
        if (company != null) {
            redisUtil.set(PREFIX + companyId, company, TTL.toMinutes(), TimeUnit.MINUTES);
        }
        return company;
    }

    public void invalidate(Long companyId) { redisUtil.delete(PREFIX + companyId); }
}
```

- 读侧接入：`HrCompanyServiceImpl.getCompanyInfo` / `getCertificationStatus`、`HrOfferServiceImpl.queryCompanyNameBestEffort`、`HrAccountServiceImpl.getProfile`。
- 写侧失效：`updateCompanyInfo`（末尾 delete）；`createCompany`/`submitCertification`（insert 后 delete，防御性）。
- 注意：`getCertificationStatus` 读的是 `certStatus/certRejectReason/createdAt`，随 `HrCompany` 整体缓存；认证审核由 lingxi-admin 改库，TTL 30min 内状态有滞后——前端审核页可接受，**若要准实时则此接口改直查或 TTL 压到 60s**（待确认，见「七、依赖」）。

### 3.3 JobInfoCache（B 级）

```java
@Component
@RequiredArgsConstructor
public class JobInfoCache {
    private static final String PREFIX = "hr:job:";
    private static final Duration TTL = Duration.ofSeconds(60); // 含 HC 数字，用短 TTL 保证准实时

    private final RedisUtil redisUtil;
    private final JobFeignClient jobFeignClient;

    public JobHcOverviewDTO get(Long jobId) { /* 缓存优先，miss 调 getJobForValidation 回填；失败返回 null */ }
    public String title(Long jobId) {
        JobHcOverviewDTO dto = get(jobId);
        return dto != null && dto.getTitle() != null ? dto.getTitle() : "岗位" + jobId;
    }
}
```

- **单一短 TTL（60s）全量缓存**，覆盖 title + 薪资 + HC 数字。hc-overview 需要准实时，60s 内轻微滞后可接受；若评审认为不可接受，再拆 `title/salary`（10min）+ `HC`（30s）两把 key（见「七、依赖」）。
- 接入点：
  - `HrOfferServiceImpl.queryJobBestEffort` / `queryJobOrThrow` / `fetchJobTitles` → `jobInfoCache.get(jobId)`
  - `HrInterviewServiceImpl.fetchJobTitle` → **改用 `jobInfoCache.title(jobId)`**，消除 `listInterviews`/`pendingEvaluations` 每行一次 Feign 的 N+1；原 `fetchJobTitle` 里对 resume `getApplication` 的调用删除（岗位标题一致性以 lingxi-job 为准）。
- 公司级 hc-overview 的 `listCompanyJobsOrEmpty` 保持直调（岗位列表无单 key，频率低，不值得缓存）。

### 3.4 D 级可选：Mock 每日配额改 Redis 计数

```java
// MockInterviewAgent.checkQuota 替换：
String key = "mock:quota:" + candidateId + ":" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
Long count = redisTemplate.opsForValue().increment(key);
if (count != null && count == 1L) {
    redisTemplate.expire(key, Duration.ofDays(1)); // 次日自然过期
}
if (count != null && count > quota) {
    throw new BusinessException(HrErrorCode.MOCK_QUOTA_EXCEEDED);
}
```

- 好处：省 `countByCandidateIdAndStartAt` 查库；天然防并发超配额。注意 redis 宕机会放行（无配额限制），可接受。
- 若本期不做，保留 DB 计数即可。

---

## 四、边界场景与注意事项

| 场景 | 处理 |
|------|------|
| Redis 不可用 / 超时 | **所有缓存必须 fail-open**：`redisUtil` 调用包 try-catch，异常仅记 warn，走原 Feign/DB 路径，**绝不让缓存故障打挂主流程** |
| 缓存穿透（用户/企业/岗位不存在） | 回源结果为 null 时写短 TTL 占位（用户 2min / 企业 1min / 岗位不写） |
| 缓存击穿（热点 key 失效瞬间并发回源） | 用户/岗位为批量回源天然摊薄；企业单 key 并发低，暂不加互斥锁，TLT 内过期后多打一次 DB 可接受 |
| 序列化类型 | `SysUserDTO`/`JobHcOverviewDTO`/`HrCompany` 需可反序列化（均 `Serializable` + 无参构造 + Jackson 可见，已具备）；强转失败 try-catch 后删除 key 重新回源 |
| 数据一致性 | 用户/岗位：TTL 最终一致（≤10min / ≤60s）；企业：写路径 delete + TTL 兜底；Offer 状态**不缓存**（写路径 0 失效负担） |
| 权限校验（成员表） | **不缓存**，避免移除/改角色后仍放行 |
| 列表降级语义 | 用户回源失败原样返回空 map，调用方 `name="用户"+id` / `phone="***"` 兜底不变 |
| hc-overview 准实时 | 岗位缓存 60s，若评审要求更严再拆两把 key |

---

## 五、开发顺序

1. 新建 3 个 cache 组件（`UserInfoCache` / `CompanyCache` / `JobInfoCache`），依赖 `RedisUtil` + 对应 Feign/Mapper
2. `UserInfoCache` 回源方法照搬现有 `fetchUsers` 逻辑 → 4 个 Service 的 `fetchUsers` 改为委托（A 级落地）
3. `JobInfoCache` + `HrInterviewServiceImpl.fetchJobTitle` 改造（修 N+1）；`HrOfferServiceImpl` 岗位查询接入
4. `CompanyCache` + 企业读/写路径接入
5. （可选）Mock 配额 Redis 计数
6. JDK 8 编译（`zulu-8`，参考 memory：默认 Maven JDK 26 会让 Lombok 失败）
7. 本地联调验证（见下）

---

## 六、验证（本地联调）

环境：IDEA 起 hr + user + job + resume + gateway + nacos + redis + mysql。

1. **用户缓存**：候选人列表连翻 3 页 → 第 1 页首查走 Feign，之后 `hr:user:*` 命中，停 lingxi-user 后列表仍正常展示缓存姓名（TTL 内）且不 500
2. **岗位缓存**：面试列表接口日志确认不再逐行调 resume `getApplication`；`hr:job:{jobId}` 命中后 lingxi-job 停机列表仍可用
3. **企业缓存**：`GET /api/v1/hr/company/info` 两次命中；`PUT /info` 后 `hr:company:{id}` 被删，下次读取新值
4. **Redis 宕机降级**：停 Redis → 各列表/详情接口照常返回（走 Feign/DB），仅多打 warn 日志
5. **负值**：不存在 userId 的候选人列表，只打一次回源，后续命占位，无穿透打满下游

---

## 七、依赖 / 待确认

| # | 项 | 类型 | 现状 | 决策 |
|---|----|------|------|------|
| 1 | 企业认证状态滞后（CompanyCache TTL 30min） | 确认 | 审核由 lingxi-admin 改库，HR 读缓存 | 若审核页需准实时 → `getCertificationStatus` 直查或 `hr:company` TTL 压 60s；默认按 30min |
| 2 | 岗位 HC 准实时要求 | 确认 | 60s 短 TTL 全量缓存 | 若 hc-overview 不能容忍 60s 滞后 → 拆 title/salary（10min）+ HC（30s）两 key |
| 3 | 用户信息最终一致 ≤10min | 确认 | name/avatar 变更走 lingxi-user | 列表页展示可接受；候选人详情（简历）不经缓存不受影响 |
| 4 | Redis 是否要加连接池/哨兵 | 依赖 | 单机 127.0.0.1:6379 | 生产两机 ECS 各自本地 Redis（部署方案）够用，本期不动 |

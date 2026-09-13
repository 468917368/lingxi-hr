# HR 创建面试「同面试官时间冲突」并发竞态修复 - Coding Plan

> **所属模块：** lingxi-hr（成员D）
> **类型：** Bug 修复（并发正确性）
> **日期：** 2026-08-10
> **技术栈：** Spring Boot 2.7 + MyBatis + MySQL(InnoDB)
> **参考：** `后端开发规范文档.md` §12.2 悲观锁；`模块四-HR服务-测分文档.md` R-03 / §4.1；`docs/schema.sql` hr_interview / hr_company_member
> **状态：** ✅ **已实施 + 编译通过**（FOR UPDATE 已提交 `f3cb68f`；READ_COMMITTED 加固 2026-08-11 待提交）

---

## 〇、Bug 概述

**一句话：** HR 创建面试（`POST /api/v1/hr/interviews`，**非**「创建面试官」）时，若多条并发请求**都指定同一面试官、时间重叠**，「同面试官前后 60min 时间冲突校验」（4102）是**先查后插的无锁 TOCTOU 竞态**，并发下可被绕过，导致同一面试官同一时间段产生重叠面试、双成功。

**影响：** 面试官重复预约（同一时间段两场面试）；两个投递都被置为 INTERVIEWING，违背 4102 业务规则；极端并发可污染面试排期数据。

---

## 一、复现步骤与现象

1. **前置：** 候选人分别投递职位 5、8，均被 HR 标记「合适」（`SCREENED`）。
2. **并发：** 100 并发 `POST /api/v1/hr/interviews`，body 轮流使用两个 applicationId：
   ```json
   {"applicationId": <投递A/投递B 轮流>, "interviewerId": 15, "scheduledAt": "2026-08-12T14:00:00", "method": "ONLINE"}
   ```
3. **结果：** 查询面试列表出现**两条 14:00 + interviewerId=15** 的面试：
   - `interviewId 9`：applicationId `2086745453881331712`（职位 8）
   - `interviewId 10`：applicationId `2086745271131312128`（职位 5）

**串行正确结果：** 第 1 个请求成功创建 14:00 面试后，其余 99 个请求应被拦截——同投递的请求因投递状态已非 SCREENED 被拒，另一投递的请求因面试官时间冲突报 **4102**。即 100 并发最终应**仅 1 个成功**。

---

## 二、根因分析

`HrInterviewServiceImpl.createInterview`（`service/impl/HrInterviewServiceImpl.java:101-178`）关键时序：

| 步骤 | 逻辑 | 是否有串行保护 |
|------|------|:---:|
| ② | 读投递状态，仅 `SCREENED` 放行 | 无（普通 Feign 读） |
| ④ | `countByInterviewerInTimeRange`（同面试官 60min 重叠） | **无（无锁 COUNT）** |
| ⑤ | `INSERT hr_interview` | 无（`application_id` 无唯一约束） |
| ⑥ | Feign 调 resume：`SCREENED→INTERVIEWING` | ✅ resume 侧条件 UPDATE（`WHERE id=? AND status=?`，全局 CAS） |

**为什么同一投递没有重复：**
⑥ 的 resume 状态更新是**条件乐观锁**（`ResumeApplicationMapper.xml:159-168`），是唯一的全局串行点。同投递 100 并发只有 1 个请求 `rows=1` 成功，其余 `STATUS_CONFLICT` → HR 侧本地事务回滚、insert 撤掉。

**为什么面试官时间冲突被绕过：**
④ 是普通 `SELECT COUNT(*)`，无锁、无串行点。100 个请求**同时**执行 ④ 时，库中还没有任何一条已提交的面试，全部读到 `count=0` → 全部通过。且两个投递是**不同 applicationId**，各自的 ⑥ CAS 互不干扰 → 双双成功，最终 interviewer 15 被重复预约。

**结论：** per-application 唯一性靠 resume 远程 CAS 兜住；**per-interviewer 时间冲突没有任何串行保护**，即竞态漏洞所在。

---

## 三、修复方案

**悲观锁串行 + READ_COMMITTED**（对齐 `后端开发规范文档.md` §12.2 悲观锁规范，与 HC 预冻结同款思路）：

> ① 在时间冲突校验（④）**之前**，对面试官的 `hr_company_member` 行加 `SELECT ... FOR UPDATE` 悲观锁，串行化同一面试官的并发创建；
> ② `createInterview` 事务隔离级别改 `READ_COMMITTED`，保证拿到锁后 COUNT 读到**最新已提交**数据。
> 二者缺一不可（见下「为什么」）。

- `hr_company_member` 有 `UNIQUE KEY uk_company_user (company_id, user_id)`（`schema.sql:509`），`FOR UPDATE` 通过唯一索引精准锁面试官行。
- **同一面试官的并发创建被串行化**：后到者在 `FOR UPDATE` 处排队，等先到者整个事务提交后才拿到锁。
- **READ_COMMITTED 的必要性（关键，2026-08-11 加固）**：InnoDB 默认 REPEATABLE READ 下，事务内第一次普通 SELECT（`requireActiveMember`）即固定读快照；并发突发时后到者的快照早于先到者提交，即便等到锁，COUNT 用旧快照**看不到**先到者已提交的面试 → 仍双成功。改 `READ_COMMITTED` 后每次 SELECT 都读最新已提交数据，配合行锁串行 → COUNT 必然命中 4102。
- **锁粒度：** per-interviewer（粗于 per-time-slot），但 HR 面试创建属低频操作，正确性优先，串行开销可接受。
- per-application 唯一性维持现状（resume CAS），本次不引入 DDL。

---

## 四、涉及文件清单

### 需要修改（3 处）

| 文件 | 修改 |
|------|------|
| `mapper/HrCompanyMemberMapper.java` | 新增 `selectByCompanyAndUserForUpdate`（悲观锁查询） |
| `resources/mapper/HrCompanyMemberMapper.xml` | 新增对应 SQL（追加 `FOR UPDATE`） |
| `service/impl/HrInterviewServiceImpl.java` | `createInterview` 第③步改用它 + 注释说明原子性依据 |

### 可选加固（本期不做，需 DDL，见 §七风险）

- `hr_interview` 增加**生成列部分唯一索引**，DB 层兜底「同一投递仅一个有效面试」：
  ```sql
  ALTER TABLE hr_interview
    ADD COLUMN active_application_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (IF(status = 'CANCELLED', NULL, application_id)) STORED,
    ADD UNIQUE KEY uk_active_application (active_application_id);
  ```

---

## 五、具体改动

### 1. `mapper/HrCompanyMemberMapper.java`

在 `selectByCompanyAndUser` 后新增：

```java
/**
 * 按企业ID+用户ID查询成员（悲观锁 FOR UPDATE）
 *
 * <p>创建面试时用于串行化同一面试官的并发创建：锁持有至事务提交，
 * 使 {@code countByInterviewerInTimeRange} 时间冲突校验具备原子性（后到者可见先到者已提交记录）。
 */
HrCompanyMember selectByCompanyAndUserForUpdate(@Param("companyId") Long companyId,
                                                @Param("userId") Long userId);
```

### 2. `resources/mapper/HrCompanyMemberMapper.xml`

在 `selectByCompanyAndUser` 后新增：

```xml
<!-- 按企业ID+用户ID查询（悲观锁：创建面试串行化同一面试官，走 uk_company_user 唯一索引） -->
<select id="selectByCompanyAndUserForUpdate" resultMap="BaseResultMap">
    SELECT <include refid="Base_Column_List"/>
    FROM hr_company_member
    WHERE company_id = #{companyId} AND user_id = #{userId}
    FOR UPDATE
</select>
```

### 3. `service/impl/HrInterviewServiceImpl.java` `createInterview` 第③步

方法级改为 `@Transactional(isolation = Isolation.READ_COMMITTED)`（新增 import `org.springframework.transaction.annotation.Isolation`）：

```java
@Override
@Transactional(isolation = Isolation.READ_COMMITTED)   // 见 §三「READ_COMMITTED 必要性」
public InterviewVO createInterview(Long companyId, InterviewCreateDTO dto) {

// ③ 面试官校验：本企业成员 + ACTIVE
//    悲观锁 FOR UPDATE：串行化同一面试官的并发创建（同面试官并发在此排队，后到者等先到者提交）
HrCompanyMember interviewer = hrCompanyMemberMapper.selectByCompanyAndUserForUpdate(companyId, dto.getInterviewerId());
if (interviewer == null) {
    throw new BusinessException(HrErrorCode.MEMBER_NOT_FOUND);
}
if (!STATUS_ACTIVE.equals(interviewer.getStatus())) {
    throw new BusinessException(HrErrorCode.MEMBER_ACCOUNT_DISABLED);
}
```

（④⑤⑥ 逻辑不变；⑥ resume CAS 仍负责 per-application 唯一性。）

---

## 六、验证方案

### 1. 并发回归（核心）
- 复现场景原样执行：100 并发、同面试官 15、同 14:00、投递 A/B 轮流。
- **断言：** 面试列表 14:00 区间仅 1 条该面试官记录；返回 200 恰好 1 个，其余 99 个为 4102（投递B请求）或投递非 SCREENED（投递A请求）。

### 2. 串行功能回归
- 正常创建 → 200，面试 PENDING，投递变 INTERVIEWING，双端通知触发。
- 已存在重叠面试时创建 → 4102。
- 60min 边界：恰好 60min 前/后的面试 → 不冲突；60min 内 → 冲突。

### 3. 边界用例
- 同投递、**不同面试官**并发 → 仅 1 成功（resume CAS 兜底），无重复。
- 同面试官、**不同时间**（不重叠）并发 → 均成功，互不阻塞误伤。

### 4. 回归清单
- 开始/取消面试、待评估列表、录入/查看评估不受影响（未改动）。

---

## 七、风险与边界

| 项 | 说明 | 处置 |
|----|------|------|
| 锁持有时间 | ③ 加锁后到 ⑥ resume Feign 调用均在事务内，锁等待时长略增；HR 低频场景可接受 | 接受 |
| 隔离级别变更 | `createInterview` 改 `READ_COMMITTED`：仅影响本方法内 SELECT 读最新已提交；无一致性读依赖，业务语义不变 | 接受（2026-08-11 加固） |
| 死锁 | 本项目无反向加锁路径（仅此处对 hr_company_member FOR UPDATE），风险低 | 联调观察 |
| per-application 唯一性 | 仍依赖 resume 远程 CAS；resume 不可达时创建失败并回滚（现状行为不变） | 接受 |
| 可选加固 | 生成列部分唯一索引可把 per-application 唯一性下沉到 DB，双保险 | 本期不做（避免 DDL），后续如需再评审 |

---

## 八、参考

- `后端开发规范文档.md` §12.2 悲观锁：`SELECT ... FOR UPDATE` + 条件更新（HC 预冻结同款）
- `模块四-HR服务-测分文档.md`：
  - §4.1「并发创建面试（同面试官同时间）」：原设计标注「无锁弱约束，极端并发可能双成功→按设计可接受」
  - R-03「时间冲突校验无锁 🟢 低」：本次测分抓到后决定加固
- `docs/schema.sql`：`hr_company_member.uk_company_user`（509 行）、`hr_interview`（539 行，无 application_id 唯一约束）

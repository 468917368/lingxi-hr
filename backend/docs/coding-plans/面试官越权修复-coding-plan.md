# 面试官越权修复（候选人/面试列表权限收紧）- Coding Plan

> **所属模块：** lingxi-hr（成员D）+ lingxi-resume（成员C 契约扩展）
> **类型：** 权限修复（Security Fix）
> **技术栈：** Spring Boot 2.7 + MyBatis + Feign
> **背景：** bug「面试官可越权查看全公司候选人（含手机号）与全部面试」。根因：`listInterviews`/`listCandidates`/`topCandidates`/`pendingEvaluations` 只做 `requireActiveMember`（本企业 ACTIVE 成员），未区分 HR_ADMIN/INTERVIEWER；`interviewerId` 是客户端可选参数，不传就查全公司。
> **范围：** ① lingxi-hr 4 个列表入口加角色校验；② lingxi-resume 候选人列表支持按 `applicationIds` 过滤（面试官候选人落地的必要机制）。
> **状态：** 🚧 待开发

---

## 〇、背景与决策

### 确认的口径（与成员D 对齐 2026-08-11）

| 角色 | 面试列表 / 候选人列表 | 不带 `interviewerId` |
|------|----------------------|----------------------|
| HR_ADMIN | 放行（可看全公司，也可按人筛） | 放行 |
| INTERVIEWER | 必须带 `interviewerId` **且等于当前用户**，否则抛 4011 | 抛 4011 |

> **关键：后端必须校验「传的 interviewerId 是当前用户本人」**，否则面试官 A 传 `interviewerId=30` 仍能看 B 的数据（残留漏洞）。

### 决策
1. **错误码统一 4011** `MEMBER_NO_PERMISSION`（对齐现有 `getOwnInterview` 越权逻辑，前端可复用）。
2. **手机号不脱敏**（成员D 明确「手机号不用管」）：改完后列表暴露范围收敛到「管理员全量 / 面试官本人负责」，可接受。
3. **候选人列表的面试官范围 = 本地 `hr_interview` 中 `interviewer_id=当前用户` 的 `application_id` 集合**，通过 C 侧新增 `applicationIds` 过滤实现（C 侧分页在前，in-memory 过滤会破坏 total，必须下沉到 SQL）。
4. **`requireActiveMember` 改造为返回 `HrCompanyMember`**（原 void 改为返回值，现有调用点无需改动，兼容），调用方据此判角色。
5. **待评估列表、Top5 同规则收紧**（同根因，避免留同类洞；Top5 返回无手机号，但属全公司数据）。

---

## 一、涉及文件清单

### lingxi-hr（修改 6 个）

| 文件 | 改动 |
|------|------|
| `controller/HrCandidateController.java` | `/list`、`/top5` 增加可选 `@RequestParam Long interviewerId` |
| `service/impl/HrInterviewServiceImpl.java` | `listInterviews`/`pendingEvaluations` 角色校验；`requireActiveMember` 返回成员 |
| `service/impl/HrCandidateServiceImpl.java` | `listCandidates`/`topCandidates` 角色校验 + applicationIds 范围；`requireActiveMember` 返回成员 |
| `mapper/HrInterviewMapper.java` | 新增 `selectApplicationIdsByInterviewer`；`selectPendingEvaluations` 加 `interviewerId` 参数 |
| `resources/mapper/HrInterviewMapper.xml` | 新增 SQL + `selectPendingEvaluations` 条件 |
| `feign/ResumeFeignClient.java` | `getApplicationList` 增加 `applicationIds` 参数 |

### lingxi-resume（修改 5 个，成员C 契约）

| 文件 | 改动 |
|------|------|
| `domain/dto/ApplicationQuery.java` | 加 `List<Long> applicationIds` |
| `resources/mapper/ResumeApplicationMapper.xml` | `selectByCompanyId` 加 `AND ra.id IN (...)` |
| `controller/InternalApplicationController.java` | `getApplicationList` 加 `@RequestParam(required=false) List<Long> applicationIds` |
| `service/ApplicationService.java` | `listByCompany` 签名加 `applicationIds` |
| `service/impl/ApplicationServiceImpl.java` | `listByCompany` 透传到 `ApplicationQuery` |

---

## 二、权限规则（伪代码）

```
member = requireActiveMember(companyId)      // 返回 HrCompanyMember
if (!"HR_ADMIN".equals(member.getRole())):
    if (interviewerId == null || !interviewerId.equals(UserContext.getUserId())):
        throw 4011 MEMBER_NO_PERMISSION       // 面试官只能查本人，且必须显式传自己
    // 面试官 → 列表范围缩到本人
```

---

## 三、实现细节 —— lingxi-hr

### 3.1 公共：`requireActiveMember` 返回成员

两个 Service 中：

```java
// 原 void → 返回 HrCompanyMember（调用点 `requireActiveMember(companyId);` 可继续当语句用）
private HrCompanyMember requireActiveMember(Long companyId) {
    Long userId = UserContext.getUserId();
    if (userId == null) {
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    HrCompanyMember me = hrCompanyMemberMapper.selectByCompanyAndUser(companyId, userId);
    if (me == null || !STATUS_ACTIVE.equals(me.getStatus())) {
        throw new BusinessException(HrErrorCode.MEMBER_NO_PERMISSION);
    }
    return me;
}
```

### 3.2 `HrInterviewServiceImpl.listInterviews`（HrInterviewServiceImpl.java:186）

```java
HrCompanyMember me = requireActiveMember(companyId);
Long currentUserId = UserContext.getUserId();
if (!ROLE_HR_ADMIN.equals(me.getRole())) {
    if (interviewerId == null || !interviewerId.equals(currentUserId)) {
        log.warn("面试官越权查询面试列表: userId={}, requestedInterviewerId={}", currentUserId, interviewerId);
        throw new BusinessException(HrErrorCode.MEMBER_NO_PERMISSION);
    }
}
// 其余不变：interviewerId 已校验，直接透传 mapper 过滤
```

> `HrInterviewController.listInterviews` 已有 `interviewerId` 参数，Controller 不用改。

### 3.3 `HrInterviewServiceImpl.pendingEvaluations`（:300）

```java
HrCompanyMember me = requireActiveMember(companyId);
Long scopedInterviewerId = ROLE_HR_ADMIN.equals(me.getRole()) ? null : UserContext.getUserId();
List<HrInterview> interviews = hrInterviewMapper.selectPendingEvaluations(companyId, scopedInterviewerId);
```

Mapper 接口 + XML：

```java
List<HrInterview> selectPendingEvaluations(@Param("companyId") Long companyId,
                                           @Param("interviewerId") Long interviewerId);
```
```xml
<select id="selectPendingEvaluations" resultType="HrInterview">
    ...
    WHERE i.company_id = #{companyId}
    <if test="interviewerId != null">AND i.interviewer_id = #{interviewerId}</if>
      AND i.status = 'IN_PROGRESS'
      AND ev.id IS NULL
    ...
</select>
```

### 3.4 `HrCandidateServiceImpl.listCandidates`（:79）

新增面试官范围 + 传 C 侧：

```java
HrCompanyMember me = requireActiveMember(companyId);
Long currentUserId = UserContext.getUserId();
List<Long> applicationIds = null;
if (!ROLE_HR_ADMIN.equals(me.getRole())) {
    if (interviewerId == null || !interviewerId.equals(currentUserId)) {
        throw new BusinessException(HrErrorCode.MEMBER_NO_PERMISSION);
    }
    applicationIds = hrInterviewMapper.selectApplicationIdsByInterviewer(companyId, currentUserId);
    if (applicationIds == null || applicationIds.isEmpty()) {
        return PageResult.empty(p, s);      // 无本人负责面试 → 空列表
    }
}
// Feign 传参追加 applicationIds
result = resumeFeignClient.getApplicationList(
        companyId, applicationIds, status, jobId, minScore, keyword, sort, p, s);
```

`HrCandidateController` 加参：

```java
@GetMapping("/list")
public Result<PageResult<CandidateVO>> listCandidates(
        @RequestParam(required = false) Long interviewerId,   // ← 新增
        ... 其余不变 ...)
```

> `listCandidates` 当前没有 `interviewerId` 参数，需新增并透传到 Service（Service 接口 `HrCandidateService.listCandidates` 签名同步加 `Long interviewerId`）。

### 3.5 `HrCandidateServiceImpl.topCandidates`（:152）

同规则（HR_ADMIN 放行；INTERVIEWER 必须带本人 interviewerId，范围缩到本人 applicationIds，空返回空列表）。Controller `/top5` 加 `@RequestParam(required=false) Long interviewerId`。

### 3.6 新 Mapper：`selectApplicationIdsByInterviewer`

```java
List<Long> selectApplicationIdsByInterviewer(@Param("companyId") Long companyId,
                                             @Param("interviewerId") Long interviewerId);
```
```xml
<select id="selectApplicationIdsByInterviewer" resultType="long">
    SELECT DISTINCT application_id
    FROM hr_interview
    WHERE company_id = #{companyId}
      AND interviewer_id = #{interviewerId}
      AND application_id IS NOT NULL
</select>
```

### 3.7 `ResumeFeignClient.getApplicationList`

```java
@GetMapping("/applications/list")
Result<PageResult<ApplicationDTO>> getApplicationList(
        @RequestParam("companyId") Long companyId,
        @RequestParam(value = "applicationIds", required = false) List<Long> applicationIds,   // ← 新增
        @RequestParam(value = "status", required = false) String status,
        ... 其余不变 ...);
```

> Feign 传 `List<Long>` 序列化为重复参数（`applicationIds=1&applicationIds=2`），Spring MVC `@RequestParam List<Long>` 可绑定。
> `HrCandidateServiceImpl` 内现有 3 处 `getApplicationList` 调用（listCandidates / topCandidates / checkCandidateRelation）同步补 `null` 或实际值。

---

## 四、C 侧契约扩展 —— lingxi-resume

### 4.1 `ApplicationQuery`

```java
/** 投递ID集合过滤（HR 面试官本人负责范围，可选） */
private List<Long> applicationIds;
```

### 4.2 `ResumeApplicationMapper.xml` `selectByCompanyId`

```xml
WHERE jp.company_id = #{companyId}
<if test="applicationIds != null and applicationIds.size() > 0">
    AND ra.id IN
    <foreach collection="applicationIds" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
</if>
... 其余条件不变 ...
```

> 与分页（PageHelper）天然兼容：total 按过滤后统计。

### 4.3 接口/服务透传

`InternalApplicationController.getApplicationList`、`ApplicationService.listByCompany`、`ApplicationServiceImpl.listByCompany` 依次加 `List<Long> applicationIds` 可选参数并 set 到 `ApplicationQuery`。

---

## 五、边界场景与风险

| 场景 | 行为 |
|------|------|
| 面试官传 `interviewerId=自己` | 放行，列表缩到本人（候选=本人负责的投递） |
| 面试官传 `interviewerId=别人` | 4011（越权，日志记录 userId + requestedInterviewerId） |
| 面试官不传 `interviewerId` | 4011 |
| HR_ADMIN 不传 / 传任意 interviewerId | 放行（全公司 / 按人筛） |
| 面试官无任何负责面试 | 候选人列表返回空页（total=0），不报错 |
| C 侧 `applicationIds` 传空列表 | 条件 `size()>0` 不生效 → 退化为全量（防御：Service 层已保证面试官空集合提前返回，不会传空） |
| 已有接口调用方（`checkCandidateRelation`） | `applicationIds` 传 null，行为不变 |
| 手机号 | 不脱敏（成员D 确认不管） |

---

## 六、开发顺序

1. lingxi-hr：`requireActiveMember` 改返回成员（2 个 Service）
2. lingxi-hr：`listInterviews` / `pendingEvaluations` 校验 + Mapper 改动
3. lingxi-hr：新增 `selectApplicationIdsByInterviewer`（Mapper + XML）
4. lingxi-hr：`listCandidates` / `topCandidates` 校验 + Controller 加参 + Feign 加参
5. lingxi-resume：`ApplicationQuery` + XML + Controller + Service 透传（成员C 改动）
6. JDK 8 编译（`zulu-8`，lingxi-hr 与 lingxi-resume 两个模块）
7. 本地联调回归（见七）

> lingxi-resume 改动若与成员C 并行，可先合 lingxi-hr 侧（面试列表修复不依赖 C 侧）；候选人列表需 C 侧就绪后联调。

---

## 七、验证（本地联调）

环境：IDEA 起 hr + resume + gateway + nacos + mysql（面试列表只测 hr+gateway；候选人列表需 resume）。

| # | 场景 | 预期 |
|---|------|------|
| 1 | 面试官 sd(id=15) `GET /interviews`（不带 interviewerId） | 4011 |
| 2 | sd `GET /interviews?interviewerId=30`（他人） | 4011 |
| 3 | sd `GET /interviews?interviewerId=15` | 仅返回 15 的面试 |
| 4 | HR_ADMIN `GET /interviews` | 全公司 8 条 |
| 5 | sd `GET /candidates/list`（不带 interviewerId） | 4011 |
| 6 | sd `GET /candidates/list?interviewerId=15` | 仅返回 sd 负责投递的候选人 |
| 7 | HR_ADMIN `GET /candidates/list` | 全公司候选人 |
| 8 | sd `GET /interviews/pending-evaluations` | 仅本人待评估 |
| 9 | HR_ADMIN `GET /candidates/top5` | 正常；sd 带自己 interviewerId 仅本人 |

---

## 八、依赖 / 待确认

| # | 项 | 类型 | 现状 | 决策 |
|---|----|------|------|------|
| 1 | lingxi-resume `applicationIds` 契约 | 依赖 | 需成员C 配合改 5 个文件 | 若成员C 本周合入，候选人列表即可落地；否则面试列表先上线 |
| 2 | `getCandidateResume` / `getCandidateResumeByUserId` 简历越权 | 确认 | 同根因（INTERVIEWER 可看任意候选人简历） | 是否本次一并收紧（INTERVIEWER 仅本人面试候选人）？默认**本次不做**，另开 |
| 3 | `interviewerId` 参数加到候选人列表接口 | 契约变更 | 前端需同步传参 | 前端面试官端候选人列表需带 `interviewerId=当前用户` |
| 4 | 错误码 | 已定 | 4011 `MEMBER_NO_PERMISSION` | 与 `getOwnInterview` 一致 |

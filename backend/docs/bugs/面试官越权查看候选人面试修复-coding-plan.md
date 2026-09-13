# 面试官越权查看候选人/面试 - Bug 修复 Coding Plan

> **所属模块：** lingxi-hr（成员D）+ lingxi-resume（成员C 契约扩展）
> **类型：** Bug 修复（授权越权）
> **日期：** 2026-08-11
> **技术栈：** Spring Boot 2.7 + MyBatis + Feign
> **参考：** `面试官越权修复-coding-plan.md`（docs/coding-plans）、`模块四-HR服务-测分文档.md`
> **状态：** 🚧 待开发

---

## 〇、Bug 概述

**一句话：** 面试官可越权查看全公司候选人（含手机号）与全部面试——列表接口只校验「本企业 ACTIVE 成员」，未区分 HR_ADMIN / INTERVIEWER；`interviewerId` 为客户端可选参数，不传即查全公司。

**影响：** 面试官可看到非本人负责的候选人（含完整手机号）与全部面试排期，敏感数据越权泄露。

---

## 一、复现步骤与现象

1. 面试官 sd（`id=15`）登录。
2. `GET /api/v1/hr/candidates/list?page=1&size=3` → 返回**全公司候选人列表**，字段含完整 `phone`。
3. `GET /api/v1/hr/interviews?page=1&size=50`（不带 `interviewerId`）→ 返回**全部 8 条面试**（interviewerId 含 15、30 两人，含非本人负责的面试）。

**串行正确结果：** 面试官仅可见本人负责（作为面试官参与）的候选人与面试；不带 `interviewerId` 时非管理员应拒绝。

---

## 二、根因分析

列表查询只做了 `requireActiveMember(companyId)`（校验「本企业 ACTIVE 成员」），**没有区分角色**；「INTERVIEWER 仅本人面试」的约束只存在于单条操作（`getOwnInterview`），列表查询缺失。

| 越权路径 | 位置 | 问题 |
|---------|------|------|
| 候选人列表（含完整手机号） | `HrCandidateServiceImpl.listCandidates` :86（仅 `requireActiveMember`）、:138 `vo.setPhone(user.getPhone())` | 不按面试官过滤，phone 全量 |
| Top5 高潜 | `HrCandidateServiceImpl.topCandidates` :156 | 同上 |
| 全部面试 | `HrInterviewServiceImpl.listInterviews` :193，`interviewerId` 可选直接透传 mapper | 客户端不传即查全公司 |
| 待评估列表 | `HrInterviewServiceImpl.pendingEvaluations` :306 | 无 interviewerId 过滤 |

**另：** 候选人列表（投递列表）数据源在 lingxi-resume，`getApplicationList` 目前**无** interviewerId/applicationIds 过滤参数——面试官范围要下沉到 C 侧 SQL 才能保证分页 total 正确。

---

## 三、修复方案（确认口径 2026-08-11）

### 权限规则

| 角色 | 面试列表 / 候选人列表 | 不带 `interviewerId` |
|------|----------------------|----------------------|
| HR_ADMIN | 放行（可全公司 / 按人筛） | 放行 |
| INTERVIEWER | 必须带 `interviewerId` **且等于当前用户**，否则 4011 | 抛 4011 |

- 错误码统一 `4011 MEMBER_NO_PERMISSION`（对齐现有 `getOwnInterview` 越权逻辑）。
- **手机号不脱敏**（成员D 确认「手机号不用管」；改后暴露范围收敛，可接受）。
- 候选人列表的面试官范围 = 本地 `hr_interview` 中 `interviewer_id=当前用户` 的 `application_id` 集合，经 C 侧新增 `applicationIds` 过滤实现。

### 数据流（面试官候选人列表）

```
面试官 GET /candidates/list?interviewerId=15
  └─ lingxi-hr 校验 role=INTERVIEWER 且 interviewerId==15
       ├─ hr_interview 取 application_id 集合（interviewer_id=15）
       ├─ 空 → 返回空页；非空 → 传 applicationIds
       └─ Feign ──> lingxi-resume GET /internal/applications/list?applicationIds=1&applicationIds=2
            └─ SQL：AND ra.id IN (...)
```

---

## 四、涉及文件清单

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

## 五、具体改动

### 1. 公共：`requireActiveMember` 返回成员（两个 Service）

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

### 2. `HrInterviewServiceImpl.listInterviews`（:186）

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

> Controller 已有 `interviewerId` 参数，不用改。

### 3. `HrInterviewServiceImpl.pendingEvaluations`（:300）+ Mapper

```java
HrCompanyMember me = requireActiveMember(companyId);
Long scopedInterviewerId = ROLE_HR_ADMIN.equals(me.getRole()) ? null : UserContext.getUserId();
List<HrInterview> interviews = hrInterviewMapper.selectPendingEvaluations(companyId, scopedInterviewerId);
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

### 4. `HrCandidateServiceImpl.listCandidates`（:79）+ Controller 加参

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
result = resumeFeignClient.getApplicationList(
        companyId, applicationIds, status, jobId, minScore, keyword, sort, p, s);
```

`HrCandidateController` `/list` 增加 `@RequestParam(required = false) Long interviewerId`（`/top5` 同），`HrCandidateService.listCandidates` 签名同步加 `Long interviewerId`。

### 5. 新 Mapper `selectApplicationIdsByInterviewer`

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

### 6. `ResumeFeignClient.getApplicationList` + C 侧 4 处透传

```java
@GetMapping("/applications/list")
Result<PageResult<ApplicationDTO>> getApplicationList(
        @RequestParam("companyId") Long companyId,
        @RequestParam(value = "applicationIds", required = false) List<Long> applicationIds,   // ← 新增
        ... 其余不变 ...);
```
```xml
WHERE jp.company_id = #{companyId}
<if test="applicationIds != null and applicationIds.size() > 0">
    AND ra.id IN
    <foreach collection="applicationIds" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
</if>
```

> Feign 传 `List<Long>` 序列化为重复参数，Spring MVC `@RequestParam List<Long>` 可绑定；与 PageHelper 分页天然兼容。`HrCandidateServiceImpl` 内现有 3 处 `getApplicationList` 调用同步补参。

---

## 六、验证方案

| # | 场景 | 预期 |
|---|------|------|
| 1 | sd `GET /interviews`（不带 interviewerId） | 4011 |
| 2 | sd `GET /interviews?interviewerId=30`（他人） | 4011 |
| 3 | sd `GET /interviews?interviewerId=15` | 仅返回 15 的面试 |
| 4 | HR_ADMIN `GET /interviews` | 全公司 8 条 |
| 5 | sd `GET /candidates/list`（不带 interviewerId） | 4011 |
| 6 | sd `GET /candidates/list?interviewerId=15` | 仅返回 sd 负责投递的候选人 |
| 7 | HR_ADMIN `GET /candidates/list` | 全公司候选人 |
| 8 | sd `GET /interviews/pending-evaluations` | 仅本人待评估 |
| 9 | HR_ADMIN `GET /candidates/top5`；sd 带自己 interviewerId | 管理员正常；sd 仅本人 |

编译：JDK 8（`zulu-8`，lingxi-hr 与 lingxi-resume 两模块）。

---

## 七、风险与边界

| 场景 | 行为 |
|------|------|
| 面试官无任何负责面试 | 候选人列表返回空页（total=0），不报错 |
| C 侧 `applicationIds` 传空列表 | 条件 `size()>0` 不生效 → 退化为全量（Service 层已保证不传空） |
| 已有调用方 `checkCandidateRelation` | `applicationIds` 传 null，行为不变 |
| lingxi-resume 未合入 | lingxi-hr 面试列表修复可独立上线；候选人列表待 C 侧就绪后联调 |
| 手机号 | 不脱敏（成员D 确认） |

---

## 八、待确认 / 依赖

| # | 项 | 类型 | 决策 |
|---|----|------|------|
| 1 | lingxi-resume `applicationIds` 契约 | 依赖 | 需成员C 改 5 个文件；未合入前候选人列表不上 |
| 2 | `getCandidateResume` 简历越权 | 确认 | 同根因（面试官可看任意候选人简历），默认本次不做，另开 |
| 3 | 前端候选人列表接口带 `interviewerId` | 契约变更 | 前端面试官端需传 `interviewerId=当前用户` |

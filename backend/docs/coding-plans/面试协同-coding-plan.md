# 面试协同 - Coding Plan

> **所属模块：** lingxi-hr | **排期：** Day 4-5
> **技术栈：** Spring Boot 2.7 + MyBatis + Redis + RocketMQ + Feign
> **参考：** 成员D开发排期 Day 4-5、系分文档 5.5.3 / 5.6.1 / 5.7 节、`docs/schema.sql` hr_interview / hr_interview_evaluation 表
> **范围：** 创建面试 + 面试列表 + 开始/取消面试 + 待评估列表 + 录入评估（面试协同核心流程）；AI 反馈延后
> **跨服务依赖：** lingxi-resume（成员C，✅）投递详情/状态更新（SCREENED→INTERVIEWING→OFFERABLE/REJECTED；取消面试回退 `INTERVIEWING→SCREENED` ✅ C 已扩展白名单，`fe150947`）；lingxi-user（成员A，✅）候选人/面试官姓名头像；lingxi-chat（成员A，✅）通知
> **状态：** ✅ **已实现并联调通过（2026-08-06）**：8 接口代码完成、JDK 8 编译通过、**本地全链路联调通过**（验证步骤见 §八）

---

## 〇、核心决策摘要

1. **AI 反馈延后**：`GET /interviews/{id}/feedback`（SSE）**本期不实现**；评估录入的 `feedback` 字段本期留 null。AI 生成反馈（录用建议/落选反馈）后续接百宝箱（复用 `LlmClient` + `MockInterviewController` 的 SSE 基建）。落选反馈沿用 Day 3 `RejectFeedbackGenerator` 模板写入 `reject_feedback` 列，不阻塞本期。
2. **面试时间冲突校验（4102）实现**：创建面试时查同面试官 `scheduled_at` 前后 60 分钟重叠（status 非终态）→ 抛 4102。与系分 5.7「MVP 不强制校验」不同，按用户确认实现。
3. **面试状态机（复用 `com.lingxi.common.enums.InterviewStatus`）**：`PENDING(待安排) → start → IN_PROGRESS(进行中) → 评估提交(非草稿) → COMPLETED(已完成)`；任意非终态可 `cancel → CANCELLED(已取消)`。`SCHEDULED(已安排)` 为候选人确认后的中间态，本期无确认接口不自动产生（start 允许 PENDING/SCHEDULED 进入 IN_PROGRESS，预留后续候选人确认功能）。状态更新统一乐观锁 `UPDATE ... WHERE id AND status=fromStatus`（rows=0 → 4101）。**不自定义状态常量**，`statusDesc` 直接用枚举 `getDesc()`。**无独立「完成面试」接口（2026-08-06 用户确认）**：start→IN_PROGRESS 后录入评估（正式提交）一次性置 COMPLETED，前端删「完成面试」按钮。
4. **投递状态联动**：创建面试 `SCREENED→INTERVIEWING`；评估 `PASS→OFFERABLE`、`PENDING→保持 INTERVIEWING`、`REJECT→REJECTED`；取消面试 **`INTERVIEWING→SCREENED`（回退）**。✅ C 侧已扩展白名单（`ApplicationServiceImpl.java:76` TRANSITION_MAP 含 SCREENED，`fe150947` 2026-08-05 落地）。
5. **通知**：Feign 直调 lingxi-chat（同 Day 3 模式）。候选人 `INTERVIEW_INVITE`（C端面试邀请）+ 面试官 `INTERVIEW_SCHEDULE`（B端面试安排）。best-effort，失败记日志不阻塞。
6. **权限**：全部接口要求本企业 ACTIVE 成员（`requireActiveMember`，4011）。**操作权限收紧（2026-08-06 用户确认）**：start/cancel/录入评估/查看评估 —— HR_ADMIN 可操作本企业全部面试；INTERVIEWER 仅可操作 `interviewer_id == 当前用户` 的面试，否则 4011。
7. **Mapper 从零实现**：`HrInterviewMapper` / `HrInterviewEvaluationMapper` 目前是空壳（无方法无 SQL），本期补齐全部数据访问。

---

## 一、涉及文件清单

### 已就绪（无需修改）
| 文件 | 说明 |
|------|------|
| `domain/entity/HrInterview.java` / `HrInterviewEvaluation.java` | 实体已建（字段与 schema 一致） |
| `common/enums/InterviewStatus.java` | 面试状态枚举（PENDING/SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED + desc），复用 |
| `exception/HrErrorCode.java` | 已有 4100-4103，需补 4104 |
| `feign/ResumeFeignClient.java` | `getApplication` / `updateApplicationStatus`（投递校验/流转） |
| `feign/UserFeignClient.java` | `batchUsers` / `getUserById`（姓名头像） |
| `feign/NotificationFeignClient.java` | `createNotification`（通知落库 + WebSocket） |
| `service/ai/RejectFeedbackGenerator.java` | REJECT 落选反馈模板（Day 3 已实现） |
| `mapper/HrCompanyMemberMapper.java` | `selectByCompanyAndUser`（权限/面试官校验） |
| SSE 基建 | `MockInterviewController` 的 `SseEmitter` + `runWithContext` 模式（后续 feedback 复用） |

### 需要修改
| 文件 | 当前 | 修改 |
|------|:---:|------|
| `mapper/HrInterviewMapper.java` + XML | 空壳 | insert / selectById / selectByCompanyId(条件+分页) / selectPendingEvaluations / updateStatus(乐观锁) / countByInterviewerInTimeRange |
| `mapper/HrInterviewEvaluationMapper.java` + XML | 空壳 | insert / updateById / selectByInterviewId / selectByInterviewIds(批量) |
| `exception/HrErrorCode.java` | 4100-4103 | 补 `INTERVIEW_ALREADY_EVALUATED(4104, "该面试已评估，请勿重复提交")` |

### 需要新建
| 文件 | 说明 |
|------|------|
| `domain/dto/InterviewCreateDTO.java` | 创建面试入参 |
| `domain/dto/EvaluationDTO.java` | 评估入参 |
| `domain/vo/InterviewVO.java` | 面试列表项 |
| `domain/vo/PendingEvaluationVO.java` | 待评估项 |
| `domain/vo/EvaluationResultVO.java` | 评估出参 |
| `domain/vo/EvaluationDetailVO.java` | 评估详情（查看评估接口，2026-08-06 新增） |
| `service/HrInterviewService.java` | 服务接口 |
| `service/impl/HrInterviewServiceImpl.java` | 业务实现 |
| `service/notify/InterviewNotifier.java` | 面试通知（候选人 INTERVIEW_INVITE / 面试官 INTERVIEW_SCHEDULE） |
| `controller/HrInterviewController.java` | 7 个接口（新增 GET evaluation） |

---

## 二、API 接口（系分 5.5.3）

### 1. 创建面试安排
`POST /api/v1/hr/interviews`（本企业 ACTIVE 成员）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| applicationId | Long | 是 | 投递记录ID |
| interviewerId | Long | 是 | 面试官用户ID |
| scheduledAt | LocalDateTime | 是 | 预约时间（ISO 8601） |
| method | String | 是 | OFFLINE/ONLINE/PHONE |
| location | String | 否 | 地点/视频链接 |
| remark | String | 否 | 备注（HR 内部） |
| candidateNote | String | 否 | 给候选人留言 |

**响应** `Result<InterviewVO>`：`interviewId` / `status=PENDING` / `scheduledAt`

**业务逻辑：**
1. `requireActiveMember(companyId)`（4011）
2. `fetchApplication(applicationId, companyId)`：C 侧 `getApplication` + 跨企业校验（4300/4301，复用 Day 3 模式）
3. 投递状态校验：`status == SCREENED` 才可安排面试（C 状态机 SCREENED→INTERVIEWING）
4. 面试官校验：`hrCompanyMemberMapper.selectByCompanyAndUser(companyId, interviewerId)` 存在 + `status=ACTIVE`（否则 4010/4005）
5. **时间冲突校验（4102）**：`countByInterviewerInTimeRange(interviewerId, scheduledAt±60min)` > 0 → 4102
6. 插入 `hr_interview`（status=PENDING）
7. Feign `updateApplicationStatus(applicationId, "INTERVIEWING")`（失败透传 C 错误码）
8. 双端通知（best-effort）：候选人 `INTERVIEW_INVITE` + 面试官 `INTERVIEW_SCHEDULE`

### 2. 面试列表
`GET /api/v1/hr/interviews`（本企业 ACTIVE 成员）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|:---:|------|
| status | String | 否 | - | PENDING/SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED（InterviewStatus 枚举 code） |
| dateRange | String | 否 | - | TODAY/WEEK/MONTH（**按 scheduled_at 过滤**） |
| jobId | Long | 否 | - | 按岗位筛选 |
| interviewerId | Long | 否 | - | 按面试官筛选（面试官端「我的面试」传当前用户 id，2026-08-06 新增） |
| method | String | 否 | - | 按面试方式筛选 OFFLINE/ONLINE/PHONE（前端要求，2026-08-06 新增） |
| keyword | String | 否 | - | 候选人姓名搜索（子查询 sys_user.name LIKE，2026-08-06 新增） |
| page | Integer | 否 | 1 | 页码 |
| size | Integer | 否 | 20 | 每页条数（≤100） |

**响应** `Result<PageResult<InterviewVO>>`，字段：
```
interviewId applicationId candidateName candidateAvatar jobTitle interviewerName
scheduledAt method methodDesc location remark status statusDesc hasQuestions hasEvaluation
```
- 拼装：候选人/面试官姓名头像 → `fetchUsers`（batch→单查降级→兜底）；jobTitle → 循环 `getApplication` 取投递 jobTitle
- `hasEvaluation`：`selectByInterviewIds` 批量查评估
- `hasQuestions`：本期恒 false（AI 出题在岗位侧 Interview Agent）

### 3. 开始面试
`POST /api/v1/hr/interviews/{id}/start`（本企业 ACTIVE 成员）
- 校验存在 + 属于本企业（4100）、状态 ∈ {PENDING, SCHEDULED}（4101）
- 乐观锁更新 status=IN_PROGRESS
- 返回 `Result<Void>`

### 4. 取消面试
`POST /api/v1/hr/interviews/{id}/cancel`（本企业 ACTIVE 成员）
- 校验存在 + 属于本企业（4100）、非终态（COMPLETED 不可取消 → 4101）
- 更新 status=CANCELLED
- 投递状态**回退 SCREENED**（候选人可被再次安排面试）：`updateApplicationStatus(applicationId, "SCREENED")`，Feign 失败透传 C 侧错误码
- ✅ **前置依赖已落地**：C 侧已扩展状态机白名单 `INTERVIEWING→SCREENED`（`ApplicationServiceImpl.java:76`，`fe150947` 2026-08-05），此调用可正常回退，不会返回 3006

### 5. 待评估列表
`GET /api/v1/hr/interviews/pending-evaluations`（本企业 ACTIVE 成员）

**响应** `Result<List<PendingEvaluationVO>>`：
```
interviewId candidateName jobTitle scheduledAt isOverdue
```
- 查面试 `status=IN_PROGRESS` 且无**正式**评估（`LEFT JOIN hr_interview_evaluation ev ON ev.interview_id=id AND ev.is_draft=0`，`ev.id IS NULL`）——**2026-08-06 修正**：原查「COMPLETED 且无评估」恒空（评估提交才置 COMPLETED，二者矛盾）；改为「已开始面试（IN_PROGRESS）尚未正式评估」，存了草稿（is_draft=1）仍算待评估
- `isOverdue`：`scheduledAt` 距今 >24h（schema 无 `completed_at` 列，**completedAt 字段已删**，用预约时间兜底：面试已开始但预约时间过去 24h 仍未正式评估 → 逾期）

### 6. 录入面试评估
`PUT /api/v1/hr/interviews/{interviewId}/evaluation`（本企业 ACTIVE 成员）

| 字段 | 类型 | 必填* | 说明 |
|------|------|:---:|------|
| conclusion | String | 是 | PASS/PENDING/REJECT |
| techScore | Integer | 是 | 技术能力 1-5 |
| communicationScore | Integer | 是 | 沟通表达 1-5 |
| matchScore | Integer | 是 | 岗位匹配 1-5 |
| potentialScore | Integer | 是 | 发展潜力 1-5 |
| comment | String | 是 | 评语 ≥20 字 |
| isDraft | Boolean | 否 | false=正式提交（默认） |

（* `isDraft=true` 跳过必填/评语校验，仅存草稿不触发流转）

**响应** `Result<EvaluationResultVO>`：`evaluationId` / `conclusion` / `feedback`(本期 null) / `applicationStatus` / `notificationSent`

**业务逻辑：**
1. `requireActiveMember(companyId)`
2. 校验面试存在 + 属于本企业（4100）、状态=IN_PROGRESS（4101，仅进行中可评估；SCHEDULED 预留后续）
3. 已评估校验（**2026-08-06 修正：草稿可覆盖转正式**）：`selectByInterviewId` 已有记录 → 若 `is_draft=0` → 4104（已正式评估，禁止重复）；若 `is_draft=1` → 放行走 UPDATE 覆盖
4. 非草稿校验：conclusion 枚举 + 4 维度 1-5 + comment.trim()≥20 字（否则 4103）
5. 保存评估：无记录 → INSERT；已有草稿（is_draft=1）→ UPDATE 覆盖（is_draft 按本次入参）；uk_interview_id 兜底 DuplicateKeyException → 4104（并发双正式提交）
6. 非草稿流转：
   - **PASS** → 面试 COMPLETED + 投递 OFFERABLE + 通知候选人「面试通过，进入 Offer 环节」
   - **PENDING** → 面试 COMPLETED + 投递保持 INTERVIEWING + 通知 HR 安排复面
   - **REJECT** → 面试 COMPLETED + 投递 REJECTED + `RejectFeedbackGenerator` 模板落选反馈写 `reject_feedback` + 通知候选人
   - `feedback` 字段本期留 null（AI 反馈延后）
7. 返回 `applicationStatus`（投递最新状态）+ `notificationSent`

### 7. 查看面试评估（新增，2026-08-06 对齐前端「查看评估」）
`GET /api/v1/hr/interviews/{interviewId}/evaluation`（本企业 ACTIVE 成员 + HR_ADMIN 或本人）

**响应** `Result<EvaluationDetailVO>`：`interviewId` / `conclusion` / `techScore` / `communicationScore` / `matchScore` / `potentialScore` / `comment` / `feedback`(本期 null) / `isDraft` / `evaluatorName` / `createdAt`
- 校验面试存在 + 属于本企业（4100）+ 操作权限（4011）
- 无评估记录 → 404（前端用列表 `hasEvaluation=false` 判断不发请求）

### 8. AI 反馈预览（延后）
`GET /api/v1/hr/interviews/{id}/feedback`（SSE）—— **本期不实现**（占位）。AI 反馈后续接百宝箱：复用 `LlmClient.generate` + `MockInterviewController` 的 `SseEmitter`/`runWithContext`/事件格式（progress/result/done/error）。

---

## 三、Service 层

### HrInterviewService 接口
```java
InterviewVO createInterview(Long companyId, InterviewCreateDTO dto);
PageResult<InterviewVO> listInterviews(Long companyId, String status, String dateRange, Long jobId, Long interviewerId, Integer page, Integer size);
void startInterview(Long companyId, Long interviewId);
void cancelInterview(Long companyId, Long interviewId);
List<PendingEvaluationVO> pendingEvaluations(Long companyId);
EvaluationDetailVO getEvaluation(Long companyId, Long interviewId);
EvaluationResultVO submitEvaluation(Long companyId, Long interviewId, EvaluationDTO dto);
```

### 关键实现说明
- **权限/拼装**：`requireActiveMember` / `requireHrAdmin` / `fetchUsers` / `fetchApplication` / `updateStatus` 与 `HrCandidateServiceImpl` 重复，本期在 `HrInterviewServiceImpl` 内自包含复制（避免改动已稳定的 Day 3 代码），后续统一抽公共工具类。
- **乐观锁**：所有状态变更 `UPDATE ... SET status=? WHERE id=? AND status=?`，rows=0 → 4101。
- **面试状态机**：复用 `com.lingxi.common.enums.InterviewStatus`（PENDING/SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED），**不自定义常量**；`statusDesc` 用枚举 `getDesc()`。
- **时间冲突**：缓冲窗口 60 分钟（常量，后续可配置）。

---

## 四、边界场景

| 场景 | 处理 |
|------|------|
| 未登录 / 无企业 | 401 |
| 非本企业成员访问 | `selectByCompanyAndUser` 空 / status!=ACTIVE → 4011 |
| INTERVIEWER 操作他人面试（start/cancel/评估/查看） | `interviewer_id != 当前用户` 且非 HR_ADMIN → 4011 |
| 投递不存在 / 非本企业投递 | 4300 / 4301 |
| 投递非 SCREENED | 抛 C 侧错误码（3006 状态不可操作）或 4101 |
| 面试官非本企业成员 | 4010 成员不存在 |
| 面试官已禁用 | 4005 该账号已被禁用 |
| 同面试官时间冲突 | 4102 面试时间冲突 |
| 面试不存在 / 非本企业 | 4100 |
| 状态不可操作（start/cancel/评估） | 4101 面试状态不允许此操作 |
| 已评估重复提交 | 4104 该面试已评估，请勿重复提交 |
| 评语 <20 字（正式提交） | 4103 评估表单校验失败 |
| Feign 投递状态更新失败 | 透传 C 侧错误码/消息 |
| 通知失败 | best-effort，`notificationSent=false`，不阻塞 |

---

## 五、错误码（补充 4104）

| code | 说明 | 触发场景 |
|------|------|------|
| 4100 | 面试记录不存在 | 查询为空 / 非本企业 |
| 4101 | 面试状态不允许此操作 | start/cancel/评估状态不满足 |
| 4102 | 面试时间冲突 | 同面试官 60min 内重叠 |
| 4103 | 评估表单校验失败（评语少于20字） | 正式提交且评语过短 |
| 4104 | 该面试已评估，请勿重复提交 | uk_interview_id 已有记录 |

---

## 六、遗留确认（2026-08-05 已确认）

| 项 | 结论 | 状态 |
|------|------|:---:|
| AI 反馈（feedback/SSE） | 本期延后，feedback 留 null；Day 10 联调前接百宝箱（复用 LlmClient + SSE） | 已确认 |
| SCHEDULED 状态 | MVP 不产生（无候选人确认接口，创建后为 PENDING）；状态命名以后端枚举 `InterviewStatus` 为准（SCHEDULED），系分文档 CONFIRMED 仅供参考 | 已确认 |
| 取消面试投递状态 | **回退 SCREENED**，候选人可被再次安排面试；✅ **C 已扩展白名单** `INTERVIEWING→SCREENED`（`ApplicationServiceImpl.java:76`，`fe150947` 落地） | 已确认 + 已落地 |
| 面试列表 dateRange/jobId 筛选 | TODAY/WEEK/MONTH **按 scheduled_at** 过滤；jobId 按岗位筛选 | 已确认 |
| 时间冲突缓冲窗口 | 固定 60min（常量，后续可配置） | 已确认 |
| 完成面试按钮 | **删除**。无独立 complete 接口，评估提交即置 COMPLETED（start→IN_PROGRESS→评估提交→COMPLETED 一步到位） | 已确认（2026-08-06） |
| 面试官操作权限 | **收紧**：start/cancel/评估/查看 —— HR_ADMIN 全量；INTERVIEWER 仅 `interviewer_id==本人`，否则 4011 | 已确认（2026-08-06） |

---

## 七、开发顺序

1. 补 `HrErrorCode.INTERVIEW_ALREADY_EVALUATED(4104)`
2. 写 `HrInterviewMapper` + XML（insert/条件分页/待评估 LEFT JOIN/乐观锁/时间冲突）
3. 写 `HrInterviewEvaluationMapper` + XML（insert/单查/批量）
4. 新建 DTO/VO（InterviewCreateDTO/EvaluationDTO/InterviewVO/PendingEvaluationVO/EvaluationResultVO/EvaluationDetailVO）
5. 实现 `InterviewNotifier`（候选人 INTERVIEW_INVITE + 面试官 INTERVIEW_SCHEDULE）
6. 实现 `HrInterviewService` + `HrInterviewServiceImpl`
7. 实现 `HrInterviewController`
8. 编译（JDK 21）+ 本地联调验证

---

## 八、验证（本地联调）

环境：IDEA 起服务（hr 重启加载新代码），MySQL/Redis/Nacos/MQ 已就绪。面试官：user 15（INTERVIEWER，company 1）；HR：16735263528（hr_1）。

**数据准备：**
```sql
UPDATE resume_application SET status='SCREENED', screened_at=NOW() WHERE id=50003;
```

**接口链路：**
1. 创建面试（50003 + 面试官15 + 明日14:00 ONLINE）→ 投递变 INTERVIEWING、面试 PENDING、双端通知落库
2. 列表 → 含该面试，拼装候选人/面试官/岗位名
3. 时间冲突 → 同面试官同时间再创建 → 4102
4. start → IN_PROGRESS
5. 待评估列表 → 含该面试（IN_PROGRESS 未正式评估）；把 scheduled_at 改到 2 天前 → isOverdue=true
6. 存草稿（isDraft=true，评语 <20 字可过）→ 待评估列表**仍显示**（草稿不算正式评估）
7. 正式提交（isDraft=false）→ **覆盖草稿**、面试 COMPLETED + 投递 OFFERABLE + 通知；待评估列表不再含该面试
8. 查看评估 GET /interviews/{id}/evaluation → 返回四维评分/评语/结论（isDraft=false）
9. 重复正式提交 → 4104
10. 另建 REJECT 场景面试 → 投递 REJECTED + reject_feedback 落库
11. 权限：INTERVIEWER 对非本人面试 start/评估 → 4011；HR_ADMIN 操作任意本企业面试通过
8. 待评估列表 → COMPLETED 无评估的面试出现，评估后消失

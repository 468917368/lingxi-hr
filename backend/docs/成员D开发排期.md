# 成员D 开发排期

> **所属模块：** 模块四 — HR服务与Mock Interview Agent（lingxi-hr）
> **总工期：** 10 天
> **技术栈：** Spring Boot 2.7 + MyBatis + Redis + RocketMQ + Feign + DeepSeek/百宝箱
> **参考文档：** 后端总体系分文档 第5章（接口 37 个，核心表 6 张，错误码 4001-4999）

---

## 排期总览

```
Day 1-2   ████████ 基础设施 + 企业认证 + 成员管理
Day 3     ████     候选人管理
Day 4-5   ████████ 面试协同 + AI 反馈
Day 6-7   ████████ Mock Interview Agent
Day 8     ████     Offer 管理 + 定时任务
Day 9     ████     消息通知 + 内部接口 + 个人中心 + Dashboard
Day 10    ████     联调 + 回归
```

> **进度（2026-08-08）**：
> ✅ **Day 1-2 已完成**（基础设施 + HR注册 + 企业认证 + 成员管理）；\
> ✅ **Day 3 已完成并联调通过**（候选人管理：列表/Top5/标记，C 侧契约已落地，Feign 直调 lingxi-chat 通知，见 coding-plan）；\
> ✅ **Day 3 追加（2026-08-06）**：候选人「查看简历」`GET /api/v1/hr/candidates/{applicationId}/resume` D 侧已实现并编译通过，C 侧 `/internal/resumes/{id}/detail` 已落地（`fe150947`），**本地全链路联调通过**；\
> ✅ **Day 6-7 已完成**（Mock Interview Agent，mock 模式 + **真实百宝箱链路验证通过**：出题/评分/报告三工作流走通，岗位/简历 Feign 链路正常，见 coding-plan）；\
> ✅ **Day 4-5 已完成并联调通过（2026-08-06）**：面试协同 7 接口（创建/列表含 interviewerId/开始/取消/待评估/录入含草稿覆盖/查看评估；AI 反馈 SSE 裁剪 2026-08-08）代码完成、JDK 8 编译通过、**本地全链路联调通过**（见 coding-plan）；\
> ✅ **Day 8 已完成并联调通过（2026-08-07 代码提交 `5fb2ee5`，2026-08-08 **本地全链路联调通过**）**：Offer 管理 8 接口（发起/列表/HC概览/催促/撤回 + 候选人详情/接受/拒绝）+ 2 定时任务（过期扫描/HC 补偿对账），HC Feign 契约对齐 B 侧，候选人列表 `hasOfferRecord`/`lastOfferStatus` 前端对齐；**2026-08-08 补丁**：C 端接受/拒绝 Offer 反向同步 `POST /internal/offers/accept|reject`（commit `8622eed`，供求职者使用，D 侧已实现、C 侧接入待成员 C）；\
> ✅ **Day 9 已完成（2026-08-08）**：个人中心 7 接口（账号信息查询/修改 + 密码 + 手机号/邮箱修改，commit `48d27de`）；**消息通知/内部接口/Dashboard 裁剪**——通知走 Feign 复用 lingxi-chat `/api/v1/notifications/*`、admin 直接查 lingxi 库、Dashboard 前端复用已有接口，D 不新增；\
> ✅ **Day 10 已完成（2026-08-08）**：联调回归随各模块开发完成后陆续执行并通过（Day 3/4-5/8 本地全链路联调通过、个人中心已联调），当前无问题。\
> **🎉 模块四（lingxi-hr）排期全部完成（2026-08-08）**：Day 1-10 全部 ✅，D 实际交付 **42 个接口**。\
> **阻塞/依赖**：①成员A register 放开 `role=INTERVIEWER` ✅（已放开）；②成员A `/internal/users/batch?ids=` ✅（已落地）；③成员C 投递内部接口 ✅（`006249d` 已升级为 PageResult + 透传筛选 + rejectFeedback + 状态机直转，2026-08-05 合并本地并重启生效）；④**通知方案变更**：候选人通知不走 MQ，改 Feign 直调 lingxi-chat `POST /internal/notifications`（lingxi-chat 通知服务为直接调用式，`hr-notification` topic 无消费者）；⑤**Day4-5 前置**：取消面试回退投递 `SCREENED`，成员C 已扩展状态机白名单 `INTERVIEWING→SCREENED`（`ApplicationServiceImpl.java:76`，`fe150947` 2026-08-05 落地）✅；⑥**Day8 依赖-成员C** ✅：投递状态机白名单 `OFFERABLE→OFFERED` / `OFFERED→{OFFER_ACCEPTED,OFFER_DECLINED,OFFERABLE}` 已落地（`ApplicationServiceImpl.java:82-84`，原 `OFFERABLE→WITHDRAWN` 需求作废）；⑦**Day8 依赖-成员B** ✅（契约已对齐）：reserve/confirm/release + `getJobForValidation`/`listCompanyJobs` 已对接，`hc/flow` 流水查询**已删除**（对账直接调 B 幂等 release，B 后续落地流水查询仍可改"先查后放"）。\

---

## Day 1-2：基础设施 + 企业认证 + 成员管理 ✅ 已完成（2026-08-03）

### 项目配置 ✅
- Nacos 服务发现/配置中心（`bootstrap.yml`）、MySQL 数据源、Redis 连接、RocketMQ（`application.yml`）
- Feign 基础配置（超时、拦截器传递 Token）

### 公共类（上午优先）✅
- `Result<T>` / `PageResult<T>`（lingxi-common 已有）
- 用户上下文用 `UserContext`（`getUserId()` / `getCompanyId()`），替代排期中的 `SecurityUtils`，功能等价
- `HrErrorCode` 枚举（4100-4301 + 4001-4006 + 4008-4012，见错误码表）
- 全局异常处理器 `GlobalExceptionHandler`（lingxi-common 已有）

### 建表 + 实体 + Mapper ✅

> 实体 8 张已建（6 核心 + `msg_notification` + `user_notification_preference`），Mapper 8 个 + XML 就绪；DDL 在 `docs/schema.sql`。

**核心表 6 张**（系分文档 5.4 节）：

| 表名 | 说明 |
|------|------|
| `hr_company` | 企业信息 |
| `hr_company_member` | 企业成员 |
| `hr_company_certification` | 企业认证申请 |
| `hr_interview` | 面试记录 |
| `hr_interview_evaluation` | 面试评估 |
| `hr_offer` | Offer 记录 |

**附加表**（也在系分文档 5.4 节，按开发节奏分批建）：

| 表名 | 说明 | 建表时机 |
|------|------|:---:|
| `msg_notification` | 通知记录 | Day 1-2 |
| `user_notification_preference` | 用户通知偏好 | Day 1-2 |
| `mock_session` | 模拟面试会话 | Day 6 |
| `mock_answer` | 模拟面试答题记录 | Day 6 |
| `mock_report` | 模拟面试报告 | Day 6 |

> `msg_conversation`、`msg_message` 由公共模块负责，`admin_operation_log` 归属成员E，均不在 D 的建表范围。

### Feign 客户端定义 ✅（4 个，+AuthFeignClient）

| Feign 接口 | 目标服务 | 用途 |
|-----------|----------|------|
| `AuthFeignClient` | lingxi-user | `/api/v1/auth/register`（HR注册 + 创建面试官复用） |
| `UserFeignClient` | lingxi-user | `GET /internal/users/{id}`、`/batch?ids=`（成员列表） |
| `ResumeFeignClient` | lingxi-resume | 投递列表/简历信息/状态更新（Day 3+ 用） |
| `JobFeignClient` | lingxi-job | HC 预冻结/确认/释放、岗位考察重点、题库（Day 6+/8 用） |

> **依赖变更（2026-08-03）**：原计划 `by-phone` + `POST /internal/users` 已废弃；创建面试官改复用 `AuthFeignClient.register`（需 A 放开 role=INTERVIEWER），成员列表改 `batch?ids=` + `/{id}`（batch 待 A 落地）。

### 企业认证 ✅ — 5 个接口（系分文档 5.5.12 + 认证状态）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/hr/company/info` | 创建企业 |
| `GET` | `/api/v1/hr/company/info` | 查看企业信息 |
| `PUT` | `/api/v1/hr/company/info` | 更新企业信息 |
| `POST` | `/api/v1/hr/company/certification` | 提交认证申请 |
| `GET` | `/api/v1/hr/company/certification/status` | 认证状态查询（新增，pending 审核页依赖） |

**关键规则：**
- 创建企业：生成 6 位邀请码按 `invite_code` 查重（不是 name）、同一事务写 hr_company + hr_company_member(HR_ADMIN)、显式设置 `cert_status=PENDING`
- 提交认证：MultipartFile 上传营业执照(选填)+证明材料(选填)，提供时校验格式(jpg/png/pdf, ≤5MB)，上传 MinIO 后 URL 写入 `hr_company_certification`
- 重复提交认证：**拦截**（查已有 PENDING 记录则拒绝，对齐系分文档 2.7.3 节）
- 更新企业：`cert_status=APPROVED` 时忽略 name 字段不更新

### 成员管理 ✅ — 5 个接口（系分文档 5.5.7 + 邀请码加入/刷新/移除）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/hr/company/members/join-by-invite` | 邀请码加入企业（role=HR_ADMIN，与系分文档 2.2.6 差异待确认） |
| `PUT` | `/api/v1/hr/company/members/invite-code` | 刷新邀请码（HR_ADMIN） |
| `GET` | `/api/v1/hr/company/members` | 成员列表 |
| `POST` | `/api/v1/hr/company/members` | 创建面试官（复用 register） |
| `DELETE` | `/api/v1/hr/company/members/{memberId}` | 移除面试官（硬删除） |

**关键规则（按实现更新）：**
- 创建面试官：复用 `AuthFeignClient.register(role=INTERVIEWER)` 建号（HR 填手机号/验证码/密码/姓名）→ 校验 `uk_company_user` → insert → 发邀请短信（失败不阻塞，记日志）；需成员A 放开 INTERVIEWER
- 成员列表：按 `company_id` 过滤 → `batch?ids=`（未落地则单查 `/{id}`）→ 兜底 name="用户"+userId、phone=***
- 邀请码加入：校验企业 ACTIVE + cert_status=APPROVED + 未重复 → insert role=HR_ADMIN
- 移除面试官：仅 INTERVIEWER 可移除，移除 HR_ADMIN 抛 4012
- 添加/移除/刷新均需本企业 HR_ADMIN 权限

> **路径说明**：统一为 `/api/v1/hr/company/members`，需与前端确认。

---

## Day 3：候选人管理（系分文档 5.5.2）✅ 已完成（2026-08-05，联调通过）

### 3 个接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/hr/candidates/list` | 候选人列表（筛选/排序/分页，跨服务拼装） |
| `GET` | `/api/v1/hr/candidates/top5` | Top5 高潜推荐 |
| `PUT` | `/api/v1/hr/candidates/{applicationId}/mark` | 标记合适/不合适 |
| `GET` | `/api/v1/hr/candidates/{applicationId}/resume` | **查看候选人简历（追加 2026-08-06，需求单 v1.2）** |

> **追加说明（2026-08-06，需求单 v1.2）**：人才库「查看简历」按钮接真实接口。D 侧已实现并编译通过（`feign/dto/ResumeDetailDTO` 全量 15 字段含 `facePhotoUrl` + 错误码 4303「候选人简历不存在」）；C 侧内部接口 `GET /internal/resumes/{id}/detail`（全量简历、免归属校验）已落地（`fe150947`，2026-08-05），**本地全链路联调通过**。详见 `docs/coding-plans/简历查看-coding-plan.md`。

**关键规则：**
- 列表：Feign 调 C 获取投递分页 + 调 A 获取用户信息拼装 → Feign 降级时 name="用户"+userId, avatar=null
- Top5：C 按 matchScore DESC 取前 5（aiScore C 侧无 DB 列恒 null，实际只按 matchScore），不足 5 人返回实际数量
- 标记 SUITABLE → Feign 更新投递 SCREENED → **Feign 直调 lingxi-chat 通知**候选人；标记 UNSUITABLE → Feign 更新投递 REJECTED → AI 生成落选反馈（模板降级）→ Feign 直调 lingxi-chat 通知
- 重复标记拦截：已 SCREENED/REJECTED 或进入后续流程 → 4302「该候选人已处理，请勿重复操作」
- 状态机校验：仅 SUBMITTED/VIEWED 可标记（C 侧已支持直转 SCREENED/REJECTED）

**实现文件（`feat(lingxi-hr): 候选人管理` 提交 53b4039，16 文件 +893 行）：**
- 接口：`HrCandidateController`；服务：`HrCandidateService(+Impl)`；VO/DTO 5 个；错误码补 `4302`
- Feign：`ResumeFeignClient` 升级契约（PageResult + minMatchScore/keyword/sortBy + rejectFeedback）、`ApplicationDTO` +companyId/advantages/risks、`NotificationFeignClient`(lingxi-chat)
- 通知：`CandidateNotifier`（Feign 直调 lingxi-chat，type=RESUME_VIEWED）
- AI：`RejectFeedbackGenerator` + `TemplateRejectFeedbackGenerator`（模板降级，TODO 接 DeepSeek）

**联调验证（2026-08-05 本地全链路通过）：**
- 列表：返回 total=2、跨服务拼装姓名/手机号正常
- 标记合适/不合适：SUBMITTED→SCREENED、VIEWED→REJECTED（C 新状态机直转生效）
- 落选反馈 JSON 写入 `reject_feedback` 列；`screened_at`/`rejected_at` 时间戳落库
- 通知：`sys_notification` 两条 RESUME_VIEWED 落库（is_read=0）；重复标记返回 4302

### Day 3 依赖（2026-08-05 全部就绪）

| 依赖方 | 接口 | 用途 | 状态 |
|--------|------|------|:---:|
| 成员C | 投递列表分页 + 简历信息 + 状态更新 + 状态机直转 | 候选人数据 | ✅ `006249d` 落地 |
| 成员A | `GET /internal/users/{id}` + `/batch?ids=` | 用户基本信息 | ✅ 已落地 |

---

## Day 4-5：面试协同 + AI 反馈（系分文档 5.5.3）✅ 已完成（2026-08-06，联调通过）

### 8 个接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/hr/interviews` | 创建面试安排 |
| `GET` | `/api/v1/hr/interviews` | 面试列表（支持 `interviewerId` 筛选，面试官端「我的面试」用） |
| `POST` | `/api/v1/hr/interviews/{id}/start` | 开始面试 |
| `POST` | `/api/v1/hr/interviews/{id}/cancel` | 取消面试 |
| `GET` | `/api/v1/hr/interviews/pending-evaluations` | 待评估列表 |
| `PUT` | `/api/v1/hr/interviews/{interviewId}/evaluation` | 录入面试评估 |
| `GET` | `/api/v1/hr/interviews/{interviewId}/evaluation` | **查看面试评估（追加 2026-08-06，前端「查看评估」用）** |
| ~~`GET`~~ | ~~`/api/v1/hr/interviews/{id}/feedback`~~ | ~~AI 反馈预览（SSE）~~ 裁剪（2026-08-08 用户确认不做） |

**关键规则：**
- 创建面试：校验投递状态 SCREENED → 校验面试官 `status=ACTIVE` → 时间冲突(60min)→ 更新投递 INTERVIEWING → Feign 直调 lingxi-chat 双端通知（候选人 INTERVIEW_INVITE + 面试官 INTERVIEW_SCHEDULE）
- **权限收紧（2026-08-06 确认）**：start/cancel/录入/查看评估 —— HR_ADMIN 可操作本企业全部面试；INTERVIEWER 仅 `interviewer_id==本人`，否则 4011
- **无独立「完成面试」接口（2026-08-06 确认）**：start→IN_PROGRESS 后录入评估（正式提交）一次性置 COMPLETED，前端删「完成面试」按钮
- 录入评估：4 维度评分(1-5)+结论(PASS/PENDING/REJECT)，评语 ≥20 字(`isDraft=true` 跳过)，`uk_interview_id` 防重复；**草稿可覆盖转正式（2026-08-06 修正）**：已有 `is_draft=1` 记录 → UPDATE 覆盖，4104 仅对已正式评估（`is_draft=0`）生效
- **待评估列表 = `IN_PROGRESS` 且无正式评估（2026-08-06 修正）**：原「COMPLETED 且无评估」恒空（评估提交才置 COMPLETED，二者矛盾），`completedAt` 字段删除、`isOverdue` 改用 `scheduledAt` 距今 >24h
- 正式提交：PASS → 投递 OFFERABLE + Feign 通知；PENDING → 保留 INTERVIEWING + 通知 HR 复面；REJECT → 投递 REJECTED + AI 落选反馈 + Feign 通知
- AI 反馈超时(>10s)：先保存评估(feedback 留空)，异步重试

**实现文件（`feat(lingxi-hr): 面试协同` 提交，10 新建 + 10 修改）：**
- 新建：`HrInterviewController`（8 接口）、`HrInterviewService(+Impl)`、DTO/VO 6 个（InterviewCreateDTO/EvaluationDTO/InterviewVO/PendingEvaluationVO/EvaluationResultVO/EvaluationDetailVO）、`InterviewNotifier`
- 修改：`HrInterview` 实体、`HrInterviewMapper(+XML)`、`HrInterviewEvaluationMapper(+XML)`、`HrErrorCode` 补 `4104`、`application.yml`
- 权限：HR_ADMIN 操作本企业全部面试；INTERVIEWER 仅 `interviewer_id==本人`（4011）

**联调验证（2026-08-06 本地全链路通过）：**
- 创建面试：投递 50003（SCREENED）+ 面试官 15 → 面试 PENDING、投递 INTERVIEWING、双端通知落库（候选人 INTERVIEW_INVITE + 面试官 INTERVIEW_SCHEDULE）
- 列表：拼装候选人/面试官/岗位名正常，`interviewerId`/`method`/`keyword` 筛选生效
- 时间冲突：同面试官 60min 内重叠 → 4102
- start → IN_PROGRESS；待评估列表含该面试，`scheduled_at` 提前 2 天 → `isOverdue=true`
- 存草稿（评语 <20 字可过）→ 待评估仍显示；正式提交 → **覆盖草稿**、面试 COMPLETED + 投递 OFFERABLE + 通知；待评估列表移除
- 查看评估 `GET /interviews/{id}/evaluation` → 四维评分/评语/结论正常
- 重复正式提交 → 4104；REJECT 场景 → 投递 REJECTED + `reject_feedback` 落库
- 权限：INTERVIEWER 操作非本人面试 → 4011；HR_ADMIN 操作本企业全部通过

---

## Day 6-7：Mock Interview Agent（系分文档 5.5.9、5.6.2）✅ 已完成（2026-08-04，mock 模式 + 真实百宝箱链路验证通过）

> **实现说明（与排期差异，详见 `docs/coding-plans/模拟面试agent-coding-plan.md` + `模拟面试-百宝箱工作流设计.md`）：**
> - **出题不依赖数据库题库**：AI 基于【岗位考察要点（B `/internal/jobs/{jobId}/requirements`）】+【简历能力画像（C `/api/v1/resumes/{id}/ability-model`，能力模型为主 + 简历详情为辅）】动态出题
> - **LLM 接入百宝箱 tboxsdk 生成型工作流**（3 个独立工作流：出题/评分/报告；`hr.agent.mock=true` 走 MockTboxLlmClient 预设 JSON）
> - **代码收拢到 `com.lingxi.hr.agent` 包**（entity/mapper/dto/feign/config/controller/tools），Mapper XML 在 `resources/mapper/agent/`
> - 前置：Feign token 透传（lingxi-common `FeignConfig.tokenRelayInterceptor`，已实现）；`JobFeignClient` 追加 `requirements` 方法
> - 自测：generate → questions → answer → skip → report 全链路通过（SSE 事件、DB 落库均正常），测试数据已清理

### 建表（Day 6 上午）
- `mock_session`、`mock_answer`、`mock_report`

### Agent 框架
- System Prompt 设计 + Tool-Calling（最多 5 轮，单次 10s 超时，整体 60s 超时）
- 降级：LLM 不可用 → 本地题库随机抽题

### 3 个 Tool

| Tool | 功能 | 实现 |
|------|------|------|
| `get_job_requirements` | 获取岗位考察重点 | Feign → lingxi-job `job_profile.interview_focus` |
| `search_question_bank` | 按岗位+技术栈抽题 | Feign → lingxi-job `job_question`，四类题型按 30/40/20/10 比例 |
| `evaluate_answer` | AI 评分 | 3 维度：技术准确度(50%)+表达逻辑(30%)+知识深度(20%) |

### 5 个接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/mock-interview/generate` | 生成题目（SSE） |
| `GET` | `/api/v1/mock-interview/{sessionId}/questions` | 获取题目 |
| `POST` | `/api/v1/mock-interview/{sessionId}/answer` | 提交答案（SSE） |
| `POST` | `/api/v1/mock-interview/{sessionId}/skip` | 跳过题目 |
| `GET` | `/api/v1/mock-interview/{sessionId}/report` | 生成报告（SSE） |

---

## Day 8：Offer 管理 + 定时任务（系分文档 5.5.4、5.6.1）✅ 已完成（2026-08-08，联调通过）

> **进度（2026-08-08）**：✅ **已完成 + 本地全链路联调通过**（2026-08-08 联调通过；代码提交 `5fb2ee5`，已推 `origin/yuanquan_dev`）。核心决策落地：撤回→Offer `WITHDRAWN`（投递回退 `OFFERABLE`）；Offer 拒绝→投递 `OFFER_DECLINED`、接受→投递 `OFFER_ACCEPTED`；accept/reject/详情走候选人身份鉴权。**前端对齐（2026-08-07）**：offerId 字符串序列化、hc-overview jobId 可空（公司级聚合）、候选人列表 `hasOfferRecord`+`lastOfferStatus`、Offer 列表补 dateRange、薪资软提示 salaryWarning、expiresInDays 默认 3、新增候选人版 Offer 详情接口（跨端方案A，7→8 接口）。**前端清单2（2026-08-07，推翻「不可再发」）**：删 `hr_offer.uk_application_id` 唯一索引（本地+服务器已 ALTER），`WITHDRAWN`/`EXPIRED` 后可重发，`SENT`/`ACCEPTED`/`REJECTED` 仍拦 4205；候选人列表新增 `lastOfferStatus`。C 投递状态机新枚举（`OFFERED`=待录用 + `OFFER_ACCEPTED`/`OFFER_DECLINED`）✅ 白名单已落地 `52e1029`/`64b8b0d`。**补丁（2026-08-08，commit `8622eed`）**：C 端投递页接受/拒绝 Offer 反向同步——D 新增内部接口 `POST /internal/offers/accept|reject`（供求职者使用，body `{applicationId[,rejectReason]}`），只同步 `hr_offer` + confirm/release HC、不更新投递状态（C 先 transition 后调 D，避免行锁死锁），C 侧接入待成员 C。详见 `docs/coding-plans/Offer接受拒绝-反向同步-coding-plan.md`。

### 8 个接口（含 3 个候选人接口：详情/接受/拒绝）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/hr/offers` | 发起 Offer |
| `GET` | `/api/v1/hr/offers` | Offer 列表（status/dateRange/jobId 筛选） |
| `GET` | `/api/v1/hr/offers/hc-overview` | HC 概览（jobId 可空=公司级聚合） |
| `POST` | `/api/v1/hr/offers/{id}/urge` | 催促确认 |
| `POST` | `/api/v1/hr/offers/{id}/retract` | 撤回 Offer |
| `GET` | `/api/v1/hr/offers/{id}` | **候选人查看 Offer 详情（跨端方案A 新增，2026-08-07）** |
| `POST` | `/api/v1/hr/offers/{id}/accept` | 候选人接受（C端） |
| `POST` | `/api/v1/hr/offers/{id}/reject` | 候选人拒绝（C端） |

**关键规则：**
- 发起：Snowflake ID(JSON 字符串)、校验投递 OFFERABLE + 无重复 SENT + 入职在未来 + 薪资>0 → 事务内 Feign reserve HC → 插入 hr_offer(SENT, 默认3天) → 投递联动 `OFFERABLE→OFFERED` → 任一步失败补偿 release(D_PERSIST_FAILED)；薪资超岗位范围返回 `salaryWarning` 软提示（不拦截）
- 接受（候选人身份）：row lock → 校验 SENT + 未过期 → 幂等（已 ACCEPTED 直接成功）→ Feign confirm HC（失败提示"名额不足，请联系HR"，Offer 保持 SENT）→ ACCEPTED → 投递 `OFFERED→OFFER_ACCEPTED`
- 拒绝（候选人身份）：row lock → 校验 SENT → 幂等 → REJECTED(+rejectReason) → Feign release HC（失败记日志不阻塞，对账兜底）→ 投递 `OFFERED→OFFER_DECLINED`
- 催促：同 Offer 24h 内 ≤2 次（否则 4204）；仅 SENT 可催
- 撤回：row lock → SENT→WITHDRAWN → Feign release HC（REJECTED 语义）→ 投递回退 `OFFERED→OFFERABLE`（best-effort）→ 通知候选人
- 不可再次发起：`uk_application_id` 唯一约束 → 撤回/过期后同投递再发起 4205；候选人列表 `hasOfferRecord=true`（任意 Offer 记录含终态）前端禁用「发起 Offer」按钮

### 定时任务
- **Offer 过期扫描** `@Scheduled(cron = "0 */5 * * * ?")`：`WHERE status='SENT' AND expires_at<=NOW()` 条件更新 → EXPIRED → Feign release（EXPIRED 语义）→ 投递回退 OFFERABLE → 通知候选人 + HR（OFFER_MANAGE）
- **HC 补偿对账** `@Scheduled(cron = "0 0 * * * ?")`：查终态（REJECTED/EXPIRED/WITHDRAWN）且 `last_sync_time IS NULL` → **直接调 B 幂等 release**（不查流水，`hc/flow` 已删除）→ 回写 `last_sync_time`

---

## Day 9：消息通知 + 内部接口 + 个人中心 + Dashboard ✅ 已完成（2026-08-08，个人中心交付；其余裁剪/复用）

> **状态说明（2026-08-08 用户确认）**：
> - ✅ **个人中心已交付**（7 接口，见 `docs/coding-plans/个人中心-coding-plan.md`）；
> - 🚫 **消息通知**：D 不实现 —— 通知**不走 MQ，走 Feign 直调 lingxi-chat**（`hr-notification` topic 无消费者）；HR 端通知列表/未读/已读**复用 lingxi-chat** `/api/v1/notifications/*`（A 已实现）；
> - 🚫 **内部接口**：D 不实现 —— lingxi-admin **直接查 lingxi 库**（面试/Offer/企业统计），无需 D 提供 `/internal/*` 统计接口；
> - 🚫 **Dashboard**：D 不实现 —— 前端统计页**复用已有接口**（候选人/Offer/面试等列表）拼装。

### ~~RocketMQ 消费者~~（裁剪，2026-08-08）
- ~~Topic：`hr-notification`，事件类型 APPLICATION/INTERVIEW/OFFER/HC_WARNING/EVAL_TIMEOUT/JOB_REFRESH/SYSTEM~~ → 通知走 Feign 直调 lingxi-chat（见 Day 3 决策：lingxi-chat 通知为直接调用式，`hr-notification` 无消费者）

### ~~通知接口 4 个~~（裁剪，复用 lingxi-chat）
- ~~`GET /api/v1/hr/notifications/list` / `unread-count` / `{id}/read` / `read-all`~~ → HR 端前端直接调 lingxi-chat `/api/v1/notifications/*`（list/unread-count/read/read-all 已有）

### 个人中心 ✅ 已完成（7 接口，2026-08-08，commit `48d27de`）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/hr/account/profile` | 账号信息查询（name/avatar/email/phone/department/companyName/role；**position 不展示**） |
| `PUT` | `/api/v1/hr/account/profile` | 更新账号信息（name/avatar 走 lingxi-user + department 本地） |
| `PUT` | `/api/v1/hr/account/password` | 修改密码（薄转发 lingxi-user） |
| `POST` | `/api/v1/hr/account/phone/send-code` | 发送手机号验证码（验证码发新手机号） |
| `POST` | `/api/v1/hr/account/phone` | 验证并修改手机号 |
| `POST` | `/api/v1/hr/account/email/send-code` | 发送邮箱验证码（验证码发新邮箱） |
| `POST` | `/api/v1/hr/account/email` | 验证并更新邮箱 |

> HR 与面试官共用（`requireActiveMember`）；操作日志取消、position 不展示；详见 `docs/coding-plans/个人中心-coding-plan.md`。

### ~~Dashboard 4 个~~（裁剪，前端复用已有接口）
- ~~`GET /api/v1/hr/dashboard/stats` / `trend` / `funnel` / `todos`~~ → 前端用候选人/Offer/面试等已有接口拼装统计页

### ~~内部接口 5 个~~（裁剪，admin 查库）
- ~~`GET /internal/interviews/count` / `today-count` / `offers/count` / `today-count` / `companies/rank`~~ → lingxi-admin 直接查 lingxi 库

---

## Day 10：联调 + 回归 ✅ 已完成（2026-08-08，联调随各模块开发完成，当前无问题）

> **状态说明（2026-08-08 用户确认）**：联调回归已在每个功能模块开发完成后陆续执行并通过——Day 3（候选人/查看简历）、Day 4-5（面试协同）、Day 8（Offer 全链路含反向同步）均**本地全链路联调通过**，个人中心已联调；当前无阻塞问题。

### 联调矩阵

| 对端 | 联调内容 | 系分文档参考 |
|------|------|:---:|
| 成员B | HC reserve/confirm/release 全链路 + 补偿对账 | 3.5.4 |
| 成员B | 岗位考察重点 + 题库 Feign 查询 | 3.4.2 / 3.5.4 |
| 成员C | 投递列表 + 简历信息 + 状态更新 Feign | 4.4.4 |
| 成员C | 评估结果 MQ 消息格式对齐 | 4.5.3 |
| 成员A | 用户信息查询 + Gateway 路由 | 2.5.7 / 1.2 |

### 端到端回归
- 投递 → 筛选 → 面试 → 评估 → Offer → 接受/拒绝 → HC 释放 全流程 ✅
- Mock Interview：出题 → 答题 → 评分 → 报告 全流程 ✅
- 通知：事件产生 → Feign 直调 lingxi-chat → `sys_notification` + WebSocket 推送 → 前端展示（**不走 MQ**）✅

---

## 关键依赖时序

| 依赖方 | 依赖内容 | 需要就绪 |
|--------|----------|:---:|
| 成员A | Gateway 路由 `/api/v1/hr/**`、`/api/v1/mock-interview/**` | ✅ 已就绪 |
| 成员A | `GET /internal/users/{id}` | ✅ 已就绪 |
| 成员A | `POST /api/v1/auth/register` 放开 `role=INTERVIEWER` | ✅ 已放开 |
| 成员A | `GET /internal/users/batch?ids=` | ✅ 已落地 |
| 成员C | 投递/简历/状态更新 Feign + 状态机直转 | ✅ `006249d` 已落地（Day 3 联调通过） |
| 成员C | 投递状态机扩展（`OFFERABLE→OFFERED`、`OFFERED→OFFER_ACCEPTED/OFFER_DECLINED/OFFERABLE`） | ✅ 已落地（`ApplicationServiceImpl.java:82-84`，2026-08-07） |
| 成员C | 评估结果 MQ 消息格式 | Day 5 前 |
| 成员B | 题库 Feign + 岗位考察重点 | Day 6 前 |
| 成员B | HC reserve/confirm/release（契约 D 已对齐 `companyId/offerId/candidateId/reason`） | ✅ 已就绪（2026-08-07 核实） |
| 成员B | HC 流水查询 `GET /internal/jobs/{jobId}/hc/flow` | ✅ 已删除（对账直接调幂等 release，不依赖；B 后续落地可改"先查后放"） |

---

## 错误码（对齐系分文档 5.5.1 节）

| code | HTTP | 说明 |
|------|:---:|------|
| 4001 | 409 | 企业名称已存在 |
| 4002 | 409 | 该成员已加入企业 |
| 4003 | 409 | 已有待审核的认证申请 |
| 4004 | 400 | 营业执照文件格式或大小不符合要求 |
| 4005 | 403 | 该账号已被禁用 |
| 4006 | 404 | 邀请码不存在或格式不正确 |
| 4008 | 409 | 您已是该企业成员，无需重复加入 |
| 4009 | 409 | 该企业尚未通过认证，无法加入 |
| 4010 | 404 | 成员不存在 |
| 4011 | 403 | 无权限操作该成员 |
| 4012 | 409 | 不能移除企业管理员 |
| 4100 | 404 | 面试记录不存在 |
| 4101 | 409 | 面试状态不可操作 |
| 4102 | 409 | 面试时间冲突 |
| 4103 | 400 | 评估表单校验失败（评语少于 20 字） |
| 4200 | 404 | Offer 不存在 |
| 4201 | 409 | Offer 状态不可操作 |
| 4202 | 410 | Offer 已过期 |
| 4203 | 409 | HC 不足，无法发起 Offer |
| 4204 | 409 | 催促频率限制（24h 内最多 2 次） |
| 4205 | 409 | 已有待确认 Offer |
| 4300 | 404 | 候选人不存在 |
| 4301 | 403 | 无权查看该候选人 |
| 4302 | 409 | 该候选人已处理，请勿重复操作 |

---

## 接口统计

| 功能模块 | 接口数 | 系分文档 |
|----------|:---:|:---:|
| HR注册 | 1 | 新增（复用 lingxi-user register） |
| 公司信息 + 认证 | 5 | 5.5.12 + 认证状态 |
| 成员管理 | 5 | 5.5.7 + 邀请码加入/刷新/移除 |
| 候选人管理 | 4（含追加查看简历） | 5.5.2 |
| 面试协同 | 7（含追加查看评估；AI 反馈 SSE 裁剪） | 5.5.3 |
| Offer 管理 | 8（含候选人接口 3：详情/接受/拒绝） | 5.5.4 |
| Mock Interview | 5 | 5.5.9 |
| 个人中心 | 7（含手机号/邮箱修改 4；position 不展示） | 5.5.6 |
| **合计（D 实际交付）** | **42** | |
| ~~消息通知~~ | ~~4~~ 裁剪：复用 lingxi-chat `/api/v1/notifications/*` | 5.5.11 |
| ~~Dashboard~~ | ~~4~~ 裁剪：前端复用已有接口拼装 | 5.5.8 |
| ~~内部接口（admin 用）~~ | ~~5~~ 裁剪：admin 直接查 lingxi 库 | 5.5.13 |
| ~~AI 反馈 SSE~~ | ~~1~~ 裁剪（Day 4-5 延后，2026-08-08 用户确认不做） | 5.5.3 |

> 系分文档规划 52 个，其中 15 个裁剪（AI 反馈 SSE 1 + 消息通知 4 + Dashboard 4 + 内部接口 5）由复用/查库/前端拼装承担，个人中心 3→7 多 4，**D 实际交付 42 个**。

> Day 1-2 已交付 11 个接口：HR注册 1 + 企业认证 5 + 成员管理 5。
> Day 3 已交付 4 个接口：候选人管理 3 + 追加「查看简历」1（合计已交付 **15** 个）。
> Day 4-5 已交付 7 个接口：面试协同 7（合计已交付 **22** 个；AI 反馈 SSE 裁剪）。
> Day 6-7 已交付 5 个接口：Mock Interview 5（合计已交付 **27** 个）。
> Day 8 已交付 8 个接口：Offer 管理 8（合计已交付 **35** 个；2026-08-08，**本地全链路联调通过**；补丁：C 端反向同步 `POST /internal/offers/accept|reject` D 侧已实现、C 侧接入待成员 C）。
> Day 9 已交付 7 个接口：个人中心 7（合计已交付 **42** 个；2026-08-08，commit `48d27de`）。
>
> **当前总进度 42 个（D 实际交付，接口全部完成，2026-08-08）**：Day 1-10 全部 ✅，无剩余未实现项。

> 系分文档 5.5.5 节消息沟通接口（会话列表/聊天记录/发送消息/获取或创建会话/轮询新消息，共 5 个）由公共模块负责，不在本排期范围。
>
> 系分文档 5.5.13 节中 Feign 调用 lingxi-job 的 HC 操作（reserve/confirm/release）是消费者侧调用，不计入 D 暴露的接口数。

# 灵犀互聘 — 模块四（HR服务与Mock Interview Agent）测分文档

> **文档版本：** V1.0  
> **创建日期：** 2026-08-09  
> **被测模块：** 模块四 HR服务与Mock Interview Agent（lingxi-hr，成员D）  
> **文档类型：** 测试分析（测分）  
> **依据：** 《后端总体系分文档.md》第 5 章 + `docs/coding-plans/*` + lingxi-hr 实际代码

---

## 1、产品概述

### 1.1 产品背景

灵犀互聘是一套 B 端（HR/企业）+ C 端（求职者）双端的智能招聘平台，后端按微服务拆分，五个成员各负责一个服务。模块四为 **HR 服务与 Mock Interview Agent**（`lingxi-hr`，端口 8084），承担 B 端 HR 从「候选人筛选 → 面试协同 → Offer 录用」的全流程管理，以及 C 端求职者的 AI 模拟面试能力。

**开发背景：**
- 技术栈：Spring Boot 2.7 + MyBatis + Redis + RocketMQ(生产者) + Feign + SSE + 百宝箱 tboxsdk
- 按成员D开发排期 Day 1-10 分阶段交付，全部完成并联调通过（2026-08-08）
- 接口前缀：`/api/v1/hr/*`（42 个）、`/api/v1/mock-interview/*`（5 个）、`/internal/*`（2 个，C 端反向同步）
- 错误码范围：4001-4999
- 数据表：6 核心表（hr_company/hr_company_member/hr_company_certification/hr_interview/hr_interview_evaluation/hr_offer）+ mock 3 表（mock_session/mock_answer/mock_report）

### 1.2 相关文档

| 文档 | 说明 |
|------|------|
| 《后端总体系分文档.md》§5 | 模块四系统设计（接口/表/状态机/降级策略） |
| docs/coding-plans/HR注册-coding-plan.md | HR 注册 |
| docs/coding-plans/企业认证-coding-plan.md | 企业信息与认证 |
| docs/coding-plans/成员管理-coding-plan.md | 成员管理 |
| docs/coding-plans/候选人管理-coding-plan.md | 候选人列表/Top5/标记 |
| docs/coding-plans/简历查看-coding-plan.md + 简历查看-后端需求单.md | 候选人简历查看 |
| docs/coding-plans/面试协同-coding-plan.md | 面试协同 8 接口 |
| docs/coding-plans/Offer管理-coding-plan.md | Offer 管理 8 接口 + 2 定时任务 |
| docs/coding-plans/Offer接受拒绝-反向同步-coding-plan.md | C 端接受/拒绝 Offer 反向同步 |
| docs/coding-plans/模拟面试agent-coding-plan.md + 模拟面试-百宝箱工作流设计.md | Mock Interview Agent |
| docs/coding-plans/个人中心-coding-plan.md | 个人中心 7 接口 |
| docs/coding-plans/Redis缓存-coding-plan.md | 🚧 缓存优化（待开发，本期不在交付范围） |
| lingxi-hr/src 实际代码 | 接口/逻辑最终以代码为准（本文已核对） |

---

## 2、项目整体分析

### 2.1 功能性需求清单

| 模块 | 内容 | 交付状态 |
|------|------|:---:|
| HR注册 | 复用 lingxi-user 注册，注册即登录返回 token | ✅ 已交付 |
| 企业信息+认证 | 创建企业/查看/更新企业信息、提交认证（营业执照）、认证状态查询 | ✅ 已交付 |
| 成员管理 | 邀请码加入、刷新邀请码、成员列表、创建面试官、移除面试官 | ✅ 已交付 |
| 候选人管理 | 候选人列表（筛选/排序/分页）、Top5高潜、标记合适/不合适、查看简历 | ✅ 已交付 |
| 面试协同 | 创建/列表/开始/取消面试、待评估列表、录入/查看评估（8 接口） | ✅ 已交付 |
| Offer管理 | 发起/列表/HC概览/催促/撤回、候选人详情/接受/拒绝 + 2 定时任务 | ✅ 已交付 |
| 个人中心 | 账号信息查询/修改、改密码、改手机号、改邮箱（7 接口） | ✅ 已交付 |
| Mock Interview | 出题/取题/答题/跳过/报告（5 接口，3 个 SSE，百宝箱 3 工作流） | ✅ 已交付 |
| 消息通知 | Feign 直调 lingxi-chat `POST /internal/notifications`（best-effort） | ✅ 已交付（方案） |
| 内部接口 | `POST /internal/offers/accept|reject`（C 端反向同步） | ✅ 已交付 |
| 定时任务 | Offer 过期扫描（5min）+ HC 补偿对账（1h） | ✅ 已交付 |
| Redis 缓存 | 用户/企业/岗位三级 Cache-Aside | 🚧 **未实施**（coding-plan 状态：待开发） |

> **说明**：Redis 缓存-coding-plan 为性能优化（纯增量），状态「🚧 待开发」，**不在本次测分范围**（仅列遗留确认）。`MsgNotification`/`UserNotificationPreference` 实体+Mapper 为 stub（XML 空），**未被通知主流程使用**。

### 2.2 改动范围

| 编号 | 模块 | 需求点 | 接口编号 | 变更类型 |
|------|------|--------|:---:|:---:|
| REQ-001 | HR注册 | 手机号+验证码+密码注册，注册即登录 | IF-001 | 新增 |
| REQ-002 | 企业认证 | 创建企业（生成邀请码，同事务建 HR_ADMIN 成员） | IF-002 | 新增 |
| REQ-003 | 企业认证 | 查看/更新企业信息（认证后 name 不可改） | IF-003/004 | 新增 |
| REQ-004 | 企业认证 | 提交认证申请（营业执照选填，MinIO 上传） | IF-005 | 新增 |
| REQ-005 | 企业认证 | 认证状态查询 | IF-006 | 新增 |
| REQ-006 | 成员管理 | 邀请码加入企业（校验认证通过） | IF-007 | 新增 |
| REQ-007 | 成员管理 | 刷新邀请码（旧码立即失效） | IF-008 | 新增 |
| REQ-008 | 成员管理 | 成员列表（batch 拼装） | IF-009 | 新增 |
| REQ-009 | 成员管理 | 创建面试官（复用 register role=INTERVIEWER） | IF-010 | 新增 |
| REQ-010 | 成员管理 | 移除面试官（硬删除，HR_ADMIN 不可移除） | IF-011 | 新增 |
| REQ-011 | 候选人管理 | 候选人列表（C 侧分页/筛选/排序 + 跨服务拼装） | IF-012 | 新增 |
| REQ-012 | 候选人管理 | Top5 高潜推荐 | IF-013 | 新增 |
| REQ-013 | 候选人管理 | 标记合适/不合适（状态机直转 + 落选反馈 + 通知） | IF-014 | 新增 |
| REQ-014 | 候选人管理 | 查看候选人简历（Feign 调 C 内部接口） | IF-015 | 新增 |
| REQ-015 | 面试协同 | 创建面试（时间冲突 60min 校验 + 投递 INTERVIEWING + 双端通知） | IF-016 | 新增 |
| REQ-016 | 面试协同 | 面试列表（多条件筛选） | IF-017 | 新增 |
| REQ-017 | 面试协同 | 开始/取消面试（取消回退投递 SCREENED） | IF-018/019 | 新增 |
| REQ-018 | 面试协同 | 待评估列表（IN_PROGRESS 无正式评估 + isOverdue） | IF-020 | 新增 |
| REQ-019 | 面试协同 | 录入面试评估（草稿可覆盖转正式 + 三结论流转） | IF-021 | 新增 |
| REQ-020 | 面试协同 | 查看面试评估 | IF-022 | 新增 |
| REQ-021 | Offer管理 | 发起 Offer（HC 预冻结 + 薪资软提示 + 投递 OFFERED） | IF-023 | 新增 |
| REQ-022 | Offer管理 | Offer 列表 / HC 概览（jobId 可空=公司级聚合） | IF-024/025 | 新增 |
| REQ-023 | Offer管理 | 催促（24h≤2次）/ 撤回（WITHDRAWN + 回退 OFFERABLE） | IF-026/027 | 新增 |
| REQ-024 | Offer管理 | 候选人版详情/接受/拒绝（userId==candidateId） | IF-028/029/030 | 新增 |
| REQ-025 | Offer管理 | Offer 过期扫描（5min）+ HC 补偿对账（1h） | 定时任务 | 新增 |
| REQ-026 | 个人中心 | 账号信息查询/修改/改密码/改手机号/改邮箱 | IF-031~037 | 新增 |
| REQ-027 | Mock Interview | 出题（SSE）/取题/答题评分（SSE）/跳过/报告（SSE） | IF-038~042 | 新增 |
| REQ-028 | 内部接口 | C 端接受/拒绝 Offer 反向同步 hr_offer + HC | IF-043/044 | 新增 |
| REQ-029 | 通知 | 业务事件 Feign 直调 lingxi-chat 写通知 | — | 新增（方案变更） |

### 2.3 术语表

| 术语 | 说明 |
|------|------|
| 投递（application） | 候选人向岗位投递的记录（`resume_application`，C 侧） |
| HR_ADMIN / INTERVIEWER | 企业内成员角色（`hr_company_member.role`）；与 `sys_user.role`（全局）是两套角色 |
| Offer 状态 | SENT（待确认）→ ACCEPTED/REJECTED/EXPIRED/WITHDRAWN（后四者为终态） |
| 面试状态 | PENDING/SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED |
| HC | Headcount 编制；预冻结 RESERVED → 转正式 CONFIRMED / 释放 RELEASED（B 侧 job_hc_reservation） |
| 预冻结 | 发起 Offer 时先占用 HC（reserve），候选人确认后转正式（confirm） |
| 百宝箱 | 阿里内部 AI 工作流平台，tboxsdk 调用 3 个生成型工作流 |
| salaryWarning | 薪资超岗位范围时的软提示（不拦截） |
| 反向同步 | C 端接受/拒绝 Offer 时调 D 内部接口同步 hr_offer |

### 2.4 系统架构分析

#### 2.4.1 系统依赖关系

```
B端前端 ──> API Gateway ──> lingxi-hr (8084)
                              │
        ┌──────────┬──────────┼──────────────┬──────────────┐
        ▼          ▼          ▼              ▼              ▼
   lingxi-user  lingxi-job  lingxi-resume  lingxi-chat   Redis/MySQL
    (成员A)      (成员B)      (成员C)        (成员A)         本地
   注册/登录     HC reserve  投递详情/列表   通知写入       用户/公司名
   用户信息     confirm/      状态机更新     sys_notification 缓存(未启用)
   个人中心      release      简历详情       + WebSocket
                 岗位HC/薪资  reject_feedback
```

**依赖明细（Feign）：**

| 依赖方 | 接口 | 用途 | 失败降级 |
|--------|------|------|:---:|
| lingxi-user | `POST /api/v1/auth/register` | HR 注册/创建面试官 | 透传错误码 |
| lingxi-user | `GET /internal/users/{id}`、`GET /internal/users/batch?ids=` | 姓名/电话/头像拼装 | batch→单查→兜底"用户"+id |
| lingxi-user | `PUT /api/v1/user/info`、`POST /auth/change-password`、`user/phone/*`、`user/email/*` | 个人中心薄转发 | 透传 A 错误码 |
| lingxi-job | `POST /internal/jobs/{jobId}/hc/reserve\|confirm\|release` | HC 预冻结/确认/释放 | 关键路径透传错误；非关键记日志 |
| lingxi-job | `GET /internal/jobs/{jobId}`、`GET /internal/jobs/company/{companyId}`、`GET /internal/jobs/{jobId}/requirements` | 岗位 HC/薪资/标题/考察要点 | best-effort 降级"岗位"+id |
| lingxi-resume | `GET /internal/applications/{id}`、`GET /internal/applications/list`、`PUT /internal/applications/{id}/status` | 投递详情/分页/状态更新 | 关键路径抛错；Top5 返回空 |
| lingxi-resume | `GET /internal/resumes/{id}/detail` | 候选人简历详情 | 网络异常→500；3101→4303 |
| lingxi-resume | `GET /api/v1/resumes/{id}/ability-model` 等（token 透传） | Mock 面试简历输入 | 简历降级为空 |
| lingxi-chat | `POST /internal/notifications` | 通知写入 sys_notification + WebSocket | best-effort，`notificationSent=false` |

#### 2.4.2 被测系统总体接口

> 共 **44 个接口**：对外 42 个（`/api/v1/hr/*` 37 + `/api/v1/mock-interview/*` 5）+ 内部 2 个（`/internal/offers/*`）。

| 接口ID | 方法+路径 | 功能 | 权限 | 关键入参 | 关键出参 |
|--------|-----------|------|------|----------|----------|
| IF-001 | POST `/api/v1/hr/register` | HR注册（注册即登录） | 匿名 | phone/code/password/name | token+user |
| IF-002 | POST `/api/v1/hr/company/info` | 创建企业 | 登录 | name/industry/scale... | HrCompanyVO |
| IF-003 | GET `/api/v1/hr/company/info` | 查看企业信息 | 登录 | — | HrCompanyVO |
| IF-004 | PUT `/api/v1/hr/company/info` | 更新企业信息 | 登录 | shortName/desc/logo/address/website | Void |
| IF-005 | POST `/api/v1/hr/company/certification` | 提交认证申请 | 登录 | 企业信息+营业执照(选填)+材料(选填) | Void |
| IF-006 | GET `/api/v1/hr/company/certification/status` | 认证状态查询 | 登录 | — | certStatus/rejectReason/submittedAt |
| IF-007 | POST `/api/v1/hr/company/members/join-by-invite` | 邀请码加入企业 | 登录 | inviteCode | companyId/companyName/role/status |
| IF-008 | PUT `/api/v1/hr/company/members/invite-code` | 刷新邀请码 | HR_ADMIN | — | 新6位邀请码 |
| IF-009 | GET `/api/v1/hr/company/members` | 成员列表 | ACTIVE成员 | role/status | List<HrMemberVO> |
| IF-010 | POST `/api/v1/hr/company/members` | 创建面试官 | HR_ADMIN | phone/code/password/name/department/techDirection | Void |
| IF-011 | DELETE `/api/v1/hr/company/members/{memberId}` | 移除面试官 | HR_ADMIN | memberId | Void |
| IF-012 | GET `/api/v1/hr/candidates/list` | 候选人列表 | ACTIVE成员 | jobId/status/minMatchScore/keyword/sortBy/page/size | PageResult<CandidateVO> |
| IF-013 | GET `/api/v1/hr/candidates/top5` | Top5高潜推荐 | ACTIVE成员 | jobId | List<TopCandidateVO> |
| IF-014 | PUT `/api/v1/hr/candidates/{applicationId}/mark` | 标记合适/不合适 | HR_ADMIN | action=SUITABLE/UNSUITABLE | newStatus/notificationSent |
| IF-015 | GET `/api/v1/hr/candidates/{applicationId}/resume` | 查看候选人简历 | ACTIVE成员 | applicationId | ResumeDetailDTO(15字段) |
| IF-016 | POST `/api/v1/hr/interviews` | 创建面试安排 | ACTIVE成员 | applicationId/interviewerId/scheduledAt/method/... | interviewId/status=PENDING |
| IF-017 | GET `/api/v1/hr/interviews` | 面试列表 | ACTIVE成员 | status/dateRange/jobId/interviewerId/method/keyword/page/size | PageResult<InterviewVO> |
| IF-018 | POST `/api/v1/hr/interviews/{id}/start` | 开始面试 | HR_ADMIN或本人 | id | Void |
| IF-019 | POST `/api/v1/hr/interviews/{id}/cancel` | 取消面试 | HR_ADMIN或本人 | id | Void |
| IF-020 | GET `/api/v1/hr/interviews/pending-evaluations` | 待评估列表 | ACTIVE成员 | — | List<PendingEvaluationVO> |
| IF-021 | PUT `/api/v1/hr/interviews/{interviewId}/evaluation` | 录入面试评估 | HR_ADMIN或本人 | conclusion/4维度/comment/isDraft | evaluationId/applicationStatus |
| IF-022 | GET `/api/v1/hr/interviews/{interviewId}/evaluation` | 查看面试评估 | HR_ADMIN或本人 | interviewId | EvaluationDetailVO |
| IF-023 | POST `/api/v1/hr/offers` | 发起Offer | HR_ADMIN | applicationId/salary/entryDate/level/remark/expiresInDays | offerId(字符串)/status/expiresAt/salaryWarning |
| IF-024 | GET `/api/v1/hr/offers` | Offer列表 | HR_ADMIN | status/dateRange/jobId/page/size | PageResult<OfferVO> |
| IF-025 | GET `/api/v1/hr/offers/hc-overview` | HC概览 | HR_ADMIN | jobId(可空) | HcOverviewVO |
| IF-026 | POST `/api/v1/hr/offers/{offerId}/urge` | 催促确认 | HR_ADMIN | offerId | Void |
| IF-027 | POST `/api/v1/hr/offers/{offerId}/retract` | 撤回Offer | HR_ADMIN | offerId | Void |
| IF-028 | GET `/api/v1/hr/offers/{offerId}` | 候选人查看Offer详情 | 候选人本人 | offerId | OfferDetailVO |
| IF-029 | POST `/api/v1/hr/offers/{offerId}/accept` | 候选人接受Offer | 候选人本人 | offerId | offerStatus/applicationStatus |
| IF-030 | POST `/api/v1/hr/offers/{offerId}/reject` | 候选人拒绝Offer | 候选人本人 | offerId + rejectReason(选填) | offerStatus/applicationStatus |
| IF-031 | GET `/api/v1/hr/account/profile` | 获取账号信息 | ACTIVE成员 | — | AccountProfileVO |
| IF-032 | PUT `/api/v1/hr/account/profile` | 更新账号信息 | ACTIVE成员 | name/avatar/department | Void |
| IF-033 | PUT `/api/v1/hr/account/password` | 修改密码 | ACTIVE成员 | oldPassword/newPassword/confirmPassword | Void |
| IF-034 | POST `/api/v1/hr/account/phone/send-code` | 发手机号验证码 | ACTIVE成员 | newPhone | Void |
| IF-035 | POST `/api/v1/hr/account/phone` | 验证并改手机号 | ACTIVE成员 | newPhone+code | Void |
| IF-036 | POST `/api/v1/hr/account/email/send-code` | 发邮箱验证码 | ACTIVE成员 | email | Void |
| IF-037 | POST `/api/v1/hr/account/email` | 验证并改邮箱 | ACTIVE成员 | email+code | Void |
| IF-038 | POST `/api/v1/mock-interview/generate` | 生成题目（SSE） | 登录(求职者) | jobId/jobTitle/questionCount/resumeId(选填) | SSE progress/result/done |
| IF-039 | GET `/api/v1/mock-interview/{sessionId}/questions` | 获取题目 | 登录(本人) | sessionId | List<MockQuestionVO> |
| IF-040 | POST `/api/v1/mock-interview/{sessionId}/answer` | 提交答案评分（SSE） | 登录(本人) | sessionId + questionNumber/answer | SSE result(三维分) |
| IF-041 | POST `/api/v1/mock-interview/{sessionId}/skip` | 跳过题目 | 登录(本人) | sessionId + questionNumber | Void |
| IF-042 | GET `/api/v1/mock-interview/{sessionId}/report` | 生成报告（SSE） | 登录(本人) | sessionId | SSE result(完整报告) |
| IF-043 | POST `/internal/offers/accept` | C端接受Offer反向同步 | 内部(免鉴权) | applicationId | Void |
| IF-044 | POST `/internal/offers/reject` | C端拒绝Offer反向同步 | 内部(免鉴权) | applicationId + rejectReason(选填) | Void |

**错误码表（HrErrorCode，代码已核对）：**

| code | HTTP | 说明 | 触发场景 |
|------|:---:|------|----------|
| 4001 | 409 | 企业名称已存在/您已创建企业 | 重复建企业 |
| 4002 | 409 | 该成员已加入企业 | 创建面试官重复 |
| 4003 | 409 | 已有待审核的认证申请 | 重复提交认证 |
| 4004 | 400 | 营业执照文件格式或大小不符合要求 | 非jpg/png/pdf或>5MB |
| 4005 | 403 | 该账号已被禁用 | 面试官 status=DISABLED |
| 4006 | 404 | 邀请码不存在或格式不正确 | 码错/企业已禁用 |
| 4007 | 404 | 模拟面试会话不存在 | session_id 不存在 |
| 4008 | 409 | 您已是该企业成员，无需重复加入 | 重复加入 |
| 4009 | 409 | 该企业尚未通过认证，无法加入 | cert_status≠APPROVED |
| 4010 | 404 | 成员不存在 | 移除目标不存在/跨企业 |
| 4011 | 403 | 无权限操作该成员 | 非本企业/非HR_ADMIN/面试官越权 |
| 4012 | 409 | 不能移除企业管理员 | 移除 HR_ADMIN |
| 4100 | 404 | 面试记录不存在 | 面试不存在/跨企业 |
| 4101 | 409 | 面试状态不允许此操作 | start/cancel/评估状态不满足 |
| 4102 | 409 | 面试时间冲突 | 同面试官 60min 重叠 |
| 4103 | 400 | 评估表单校验失败（评语少于20字） | 正式提交评语过短 |
| 4104 | 409 | 该面试已评估，请勿重复提交 | 已有正式评估 |
| 4200 | 404 | Offer不存在 | 不存在/跨企业 |
| 4201 | 409 | Offer状态不允许此操作 | 非SENT操作/投递非OFFERABLE |
| 4202 | 410 | Offer已过期 | 接受/拒绝时已过期 |
| 4203 | 409 | HC不足/confirm失败 | reserve 2201 / confirm 失败 |
| 4204 | 409 | 催促频率限制（24h内最多2次） | 24h 内 urge≥2 |
| 4205 | 409 | 已有待确认Offer | SENT/ACCEPTED/REJECTED 重复发起 |
| 4300 | 404 | 候选人不存在 | 投递不存在/查询失败 |
| 4301 | 403 | 无权查看该候选人 | 投递跨企业 |
| 4302 | 409 | 该候选人已处理，请勿重复操作 | 已标记/已进入后续流程 |
| 4303 | 404 | 候选人简历不存在 | resumeId 空/C返回3101 |
| 40014 | 409 | 面试已完成（Mock） | session COMPLETED 再答题 |
| 40015 | 400 | 暂无答题记录，无法生成报告 | 无评分记录 |
| 40016 | 400 | 简历不存在或无权限（Mock） | C 简历查询失败 |
| 40017 | 500 | 出题服务繁忙 | LLM+岗位数据均不可用 |
| 40018 | 429 | 今日模拟面试次数已达上限 | 超过 quota |

> **注意**：40014~40018 为枚举值 40014/40015/40016/40017/40018（非 4014-4018），与 4001-4012 段不冲突。

#### 2.4.3 数据模型变更

> D 共建 11 张表；本期**无新增/变更 DDL**（hr_offer 删唯一索引除外，见下）。

| 表 | 用途 | 关键字段/约束 | 变更 |
|----|------|----------------|------|
| hr_company | 企业信息 | uk_name、uk_invite_code、cert_status | — |
| hr_company_member | 企业成员 | uk_company_user、role(HR_ADMIN/INTERVIEWER)、department NOT NULL | — |
| hr_company_certification | 认证申请 | status(PENDING/APPROVED/REJECTED) | — |
| hr_interview | 面试记录 | idx_interviewer_id、status(PENDING...) | — |
| hr_interview_evaluation | 面试评估 | **uk_interview_id**（并发兜底）、is_draft | — |
| hr_offer | Offer | status(SENT/ACCEPTED/REJECTED/EXPIRED/WITHDRAWN)、idx_status_expires、idx_status_sync | **⚠️ 已删 uk_application_id 唯一索引**（2026-08-07，允许 WITHDRAWN/EXPIRED 重发） |
| mock_session | 模拟面试会话 | uk_session_id、status(IN_PROGRESS/COMPLETED) | — |
| mock_answer | 答题记录 | session_id+question_number（逻辑唯一，UPDATE 覆盖） | — |
| mock_report | 面试报告 | uk_session_id、highlights/weaknesses/improvement_plan(JSON) | — |
| msg_notification | 通知（**未被主流程使用**） | 实体+Mapper 为 stub | 方案变更遗留 |
| user_notification_preference | 通知偏好（**DDL未落地**） | 实体+Mapper 为 stub | 方案变更遗留 |

> **Redis 变更**：Mock 会话号使用 `mock:session:seq:{yyyyMMdd}` INCR。通知走 lingxi-chat 的 `sys_notification` 表（A 侧），非本库。

---

## 3、功能性需求测试分析

> 按「六条推演路径」逐模块展开：功能分支、用户流程分支、数据口径、接口五维（正常/参数异常/返回异常/超时/并发）、状态机、配置、链路、差异。

### 3.1 各模块功能测试分析

#### 3.1.1 HR注册（IF-001）

**需求描述：** HR 首次入驻，手机号+短信验证码+密码注册，注册即登录返回 token，`companyId=null` 待建企业。

**系统设计：** 薄转发 lingxi-user `POST /api/v1/auth/register`（role 固定 HR），零本地存储；失败原样透传 A 错误码。

**业务流程图：**
```
手机号+验证码+密码+姓名 → D 转发 lingxi-user register(role=HR)
   ├─ 验证码错误/过期 → 透传 A 1001/1002
   ├─ 手机号已注册 → 透传 A
   ├─ 密码强度不足 → D 侧 @Pattern 400
   └─ 成功 → 返回 token + user(companyId=null)
```

**测试场景推演：**

| 场景ID | 场景 | 预期 | 优先级 |
|--------|------|------|:---:|
| HR-REG-01 | 正常注册（合法手机号+6位验证码+强密码+姓名） | 返回 token+user，companyId=null | P0 |
| HR-REG-02 | 验证码错误/过期 | 透传 A 错误码（1001/1002） | P0 |
| HR-REG-03 | 手机号已注册 | 透传 A 重复注册错误 | P1 |
| HR-REG-04 | 手机号格式非法 | D 侧 @Phone 400 | P1 |
| HR-REG-05 | 验证码非 6 位数字 | D 侧 @Pattern 400 | P1 |
| HR-REG-06 | 密码强度不足（<8位/无特殊字符等） | 400 参数校验 | P1 |
| HR-REG-07 | 姓名为空/超 32 字符 | 400 参数校验 | P2 |
| HR-REG-08 | lingxi-user 服务不可用/超时 | 抛"注册服务异常，请重试"（500） | P0 |
| HR-REG-09 | 注册成功但未建企业 | companyId=null，前端引导创建企业 | P1 |
| HR-REG-10 | 重复注册同手机号 | 幂等/唯一约束兜底，报重复 | P2 |

#### 3.1.2 企业信息与认证（IF-002 ~ IF-006）

**需求描述：** HR 创建企业（或随认证表单一次创建）、查看/更新企业信息、提交认证（营业执照选填）、认证状态查询。

**系统设计：**
- `createCompany`：校验无已有企业（4001）→ 生成唯一 6 位邀请码 → 同事务写 hr_company(PENDING) + hr_company_member(HR_ADMIN)
- `updateCompanyInfo`：`cert_status==APPROVED` 时 name 忽略不更新；仅更新非 null 字段
- `submitCertification`：校验文件（jpg/png/pdf、≤5MB，否则 4004）→ MinIO 上传 → 同事务写 hr_company + member + certification(PENDING)；已有 PENDING → 4003
- `getCertificationStatus`：**按 userId 反查成员关系**（不依赖 companyId claim）

**状态机：** 企业认证 `PENDING →(admin审核)→ APPROVED / REJECTED`（审核由成员 E 操作，D 只读）。

**测试场景推演：**

| 场景ID | 场景 | 预期 | 优先级 |
|--------|------|------|:---:|
| CMP-01 | 首次创建企业（正常） | hr_company + HR_ADMIN 成员同事务创建，返回邀请码 | P0 |
| CMP-02 | 已创建企业再创建 | 4001"您已创建企业" | P0 |
| CMP-03 | 企业名重复（uk_name） | DuplicateKeyException→4001 | P1 |
| CMP-04 | 认证后（APPROVED）更新 name | name 忽略不更新，其余字段更新 | P0 |
| CMP-05 | 认证前更新 name | 可更新 | P1 |
| CMP-06 | 更新企业信息（非 null 字段合并更新） | 仅更新传入字段 | P1 |
| CMP-07 | 提交认证：营业执照格式错误 | 4004 | P1 |
| CMP-08 | 提交认证：营业执照 >5MB | 4004 | P1 |
| CMP-09 | 提交认证：无文件 | 成功（文件选填） | P1 |
| CMP-10 | 提交认证：已有企业（selectActiveByUserId 非空） | 4001 | P0 |
| CMP-11 | 已有 PENDING 认证再提交 | 4003 | P1 |
| CMP-12 | 认证成功后查询状态 | certStatus=APPROVED + submittedAt | P0 |
| CMP-13 | 认证拒绝后查询状态 | certStatus=REJECTED + certRejectReason | P1 |
| CMP-14 | 注册当次 token（无 companyId claim）查认证状态 | 按 userId 反查，返回正确企业状态 | P0 |
| CMP-15 | 无企业用户查认证状态 | 404 | P1 |
| CMP-16 | MinIO 上传失败 | 抛系统异常，前端提示重试 | P2 |
| CMP-17 | 并发同用户创建企业 | 一个成功一个 4001（DB 幂等） | P2 |
| CMP-18 | 邀请码生成冲突 | 循环重试直到唯一 | P2 |

#### 3.1.3 成员管理（IF-007 ~ IF-011）

**需求描述：** 邀请码加入企业、刷新邀请码、成员列表、创建面试官、移除面试官。

**系统设计（代码已核对）：**
- `joinByInvite`：格式校验（6位大写+数字，4006）→ 企业存在/ACTIVE（4006）→ cert_status==APPROVED（4009）→ 已是成员（4008）→ insert 成员 role=**HR_ADMIN**、department="待定"
- `createMember`：HR_ADMIN 权限（4011）→ 复用 register(role=INTERVIEWER) 建号 → uk_company_user 兜底 4002 → insert 成员 → SmsUtil 发邀请短信（失败仅记日志）
- `removeMember`：HR_ADMIN（4011）→ 存在/本企业（4010）→ role==HR_ADMIN → 4012 → **硬删除**（不影响 sys_user）
- `refreshInviteCode`：HR_ADMIN（4011）→ 生成新码更新（旧码立即失效）

> **⚠️ 与系分差异**：系分 2.2.6 约定邀请码加入者 role=INTERVIEWER，实际为 **HR_ADMIN**（2026-08-03 用户确认）。

**测试场景推演：**

| 场景ID | 场景 | 预期 | 优先级 |
|--------|------|------|:---:|
| MEM-01 | 有效邀请码加入（企业已认证） | 成为 HR_ADMIN 成员，department="待定" | P0 |
| MEM-02 | 邀请码格式非法 | 4006 | P1 |
| MEM-03 | 邀请码不存在 | 4006 | P0 |
| MEM-04 | 企业已禁用 | 4006 | P1 |
| MEM-05 | 企业未通过认证 | 4009 | P0 |
| MEM-06 | 已在该企业重复加入 | 4008 | P0 |
| MEM-07 | 刷新邀请码（HR_ADMIN） | 返回新码，旧码立即失效，已加入成员不受影响 | P0 |
| MEM-08 | 非 HR_ADMIN 刷新邀请码 | 4011 | P0 |
| MEM-09 | 创建面试官（正常，复用 register） | A 建号 INTERVIEWER + hr_company_member 落库 + 邀请短信 | P0 |
| MEM-10 | 创建面试官验证码错误/过期 | 透传 A 错误码 | P1 |
| MEM-11 | 创建面试官手机号已注册 | 透传 A（或 4002 uk_company_user） | P1 |
| MEM-12 | 创建面试官：面试官已在该企业 | 4002 | P1 |
| MEM-13 | 成员列表（无筛选） | 全部 ACTIVE 成员，拼装 name/phone | P0 |
| MEM-14 | 成员列表按 role/status 筛选 | 过滤正确 | P1 |
| MEM-15 | 成员列表 batch 查用户失败 | 降级单查→兜底"用户"+id、phone="***" | P1 |
| MEM-16 | 移除面试官（正常） | 硬删除成员关系，sys_user 保留 | P0 |
| MEM-17 | 移除 HR_ADMIN | 4012 | P0 |
| MEM-18 | 移除不存在/跨企业成员 | 4010 | P1 |
| MEM-19 | 非 HR_ADMIN 添加/移除 | 4011 | P0 |
| MEM-20 | 被移除账号在其他企业 | 其他企业成员关系不受影响 | P2 |
| MEM-21 | 邀请短信发送失败 | 成员关系仍创建，仅记日志 | P2 |

#### 3.1.4 候选人管理（IF-012 ~ IF-015）

**需求描述：** 候选人列表（筛选/排序/分页，跨服务拼装）、Top5 高潜推荐、标记合适/不合适（状态机直转+落选反馈+通知）、查看候选人简历。

**系统设计（代码已核对）：**
- `listCandidates`：投递分页来自 C（透传筛选/排序），用户信息 A（batch→单查→兜底）；**列表失败抛 500 不降级为空**；拼装 `hasOfferRecord`（任意 Offer 记录）+ `lastOfferStatus`（最新状态）
- `topCandidates`：C 按 matchScore DESC/aiScore DESC 取前 5；**失败返回空列表**（推荐性质）
- `markCandidate`：HR_ADMIN（4011）→ 投递归属（4300/4301）→ **仅 SUBMITTED/VIEWED 可标记**（否则 4302）→ SUITABLE→SCREENED；UNSUITABLE→REJECTED + `TemplateRejectFeedbackGenerator` 模板反馈写 reject_feedback + 通知（best-effort）
- `getCandidateResume`：ACTIVE 成员（4011）→ fetchApplication（4300/4301）→ resumeId 空→4303 → Feign C `/internal/resumes/{id}/detail`（3101→4303；网络异常→500）；**查看成功 SUBMITTED→VIEWED**（best-effort）

**投递状态机（C 侧，D 联动/校验）：**
```
SUBMITTED → VIEWED → SCREENED → INTERVIEWING → OFFERABLE → OFFERED →{OFFER_ACCEPTED, OFFER_DECLINED}
              ↘ REJECTED（标记不合适/面试淘汰）
```

**测试场景推演：**

| 场景ID | 场景 | 预期 | 优先级 |
|--------|------|------|:---:|
| CAN-01 | 候选人列表（正常分页） | total 正确，姓名/电话/头像拼装 | P0 |
| CAN-02 | 列表按 jobId/status/minMatchScore/keyword 筛选 | C 侧过滤正确 | P1 |
| CAN-03 | 列表 sortBy=matchScore/submittedAt/aiScore | 排序正确 | P1 |
| CAN-04 | size>100 / <1 归一化 | 上限 100，下限 1 | P2 |
| CAN-05 | 列表 C 接口失败/非成功 | 500"候选人列表查询失败" | P0 |
| CAN-06 | 列表 A 用户信息不可用 | 兜底 name="用户"+id、phone="***"、avatar=null，列表不阻塞 | P1 |
| CAN-07 | 有 Offer 记录的投递 hasOfferRecord=true | 含 WITHDRAWN/EXPIRED 等终态 | P0 |
| CAN-08 | lastOfferStatus 取每投递最新 Offer 状态 | SENT/ACCEPTED/.../null | P0 |
| CAN-09 | Top5 正常 | rank 1-5，含 advantages/risks | P0 |
| CAN-10 | Top5 不足 5 人 | 返回实际数量 | P1 |
| CAN-11 | Top5 C 接口失败 | 空列表（不阻塞） | P1 |
| CAN-12 | 标记合适（SUBMITTED/VIEWED→SCREENED） | 新状态 SCREENED + 通知 | P0 |
| CAN-13 | 标记不合适（→REJECTED + 落选反馈 JSON 落库） | reject_feedback 有 reason+suggestions | P0 |
| CAN-14 | 重复标记（SCREENED/REJECTED） | 4302 | P0 |
| CAN-15 | 标记已进入 INTERVIEWING/OFFERABLE/OFFERED/WITHDRAWN | 4302 | P0 |
| CAN-16 | 标记投递不存在 | 4300 | P1 |
| CAN-17 | 标记跨企业投递 | 4301 | P0 |
| CAN-18 | 非 HR_ADMIN 标记 | 4011 | P0 |
| CAN-19 | action 非法值 | 400（@Pattern） | P1 |
| CAN-20 | 通知失败 | notificationSent=false，标记成功 | P1 |
| CAN-21 | 查看简历正常 | 15 字段全量返回（含 facePhotoUrl） | P0 |
| CAN-22 | 简历存在但 resumeId 为空 | 4303 | P1 |
| CAN-23 | C 返回 RESUME_NOT_FOUND(3101) | 4303 | P0 |
| CAN-24 | C 网络异常 | 500（不降级为空） | P1 |
| CAN-25 | 查看简历 SUBMITTED→VIEWED + 通知 | 状态更新成功 | P1 |
| CAN-26 | 查看简历投递已 VIEWED | 不重复更新状态 | P2 |
| CAN-27 | 跨企业查看简历 | 4301 | P0 |

#### 3.1.5 面试协同（IF-016 ~ IF-022）

**需求描述：** 面试安排、列表、开始/取消、待评估、录入/查看评估（8 接口）。

**系统设计（代码已核对）：**
- `createInterview`：ACTIVE 成员 → 投递 SCREENED（4101）→ 面试官本企业 ACTIVE（4010/4005）→ **时间冲突 60min**（4102）→ insert PENDING → 投递 SCREENED→INTERVIEWING → 双端通知
- `startInterview`：状态 ∈{PENDING,SCHEDULED}→IN_PROGRESS（乐观锁 4101）
- `cancelInterview`：非 COMPLETED/CANCELLED → CANCELLED → **投递 INTERVIEWING→SCREENED**（仅当仍 INTERVIEWING）
- `pendingEvaluations`：`status=IN_PROGRESS` 且无正式评估（is_draft=0）LEFT JOIN；**isOverdue=scheduledAt>24h**
- `submitEvaluation`：IN_PROGRESS（4101）→ 已正式评估（4104）/ 草稿覆盖 → 校验（正式：结论枚举+四维1-5+评语≥20字 4103）→ INSERT/UPDATE → 正式提交置 COMPLETED + 三结论流转（PASS→OFFERABLE / PENDING→保持INTERVIEWING+通知HR / REJECT→REJECTED+模板反馈）
- `getEvaluation`：HR_ADMIN 或本人（4011）+ 4100；无记录→404

**面试状态机：**
```
PENDING ──start──> IN_PROGRESS ──正式评估──> COMPLETED
   │                    │
   └──cancel──> CANCELLED（任意非终态可取消）
```
> SCHEDULED 为预留中间态，MVP 不自动产生（创建后即 PENDING）；start 允许 PENDING/SCHEDULED→IN_PROGRESS。

**测试场景推演：**

| 场景ID | 场景 | 预期 | 优先级 |
|--------|------|------|:---:|
| INT-01 | 创建面试（正常，投递 SCREENED） | hr_interview PENDING + 投递 INTERVIEWING + 双端通知 | P0 |
| INT-02 | 投递非 SCREENED 创建面试 | 4101（或透传 C 3006） | P0 |
| INT-03 | 面试官非本企业成员 | 4010 | P1 |
| INT-04 | 面试官已禁用 | 4005 | P1 |
| INT-05 | 同面试官时间重叠（60min 内） | 4102 | P0 |
| INT-06 | 时间冲突边界（恰好 60min） | 临界判断 | P2 |
| INT-07 | 面试列表正常 | 候选人/面试官/岗位名拼装、hasEvaluation | P0 |
| INT-08 | 列表按 status/dateRange/jobId/interviewerId/method/keyword 筛选 | 过滤正确（dateRange 按 scheduled_at） | P1 |
| INT-09 | 列表 size 归一化 | 上限 100 | P2 |
| INT-10 | 列表 A 用户信息不可用 | 兜底展示不阻塞 | P1 |
| INT-11 | 开始面试（PENDING→IN_PROGRESS） | 成功 | P0 |
| INT-12 | 开始面试状态非法（COMPLETED/CANCELLED） | 4101 | P1 |
| INT-13 | 取消面试（非终态→CANCELLED） | 投递回退 SCREENED | P0 |
| INT-14 | 取消已完成面试 | 4101 | P1 |
| INT-15 | 取消时投递非 INTERVIEWING | 只置 CANCELLED，不回退投递 | P2 |
| INT-16 | 待评估列表（IN_PROGRESS 无正式评估） | 含该面试 | P0 |
| INT-17 | 存草稿后待评估列表 | **仍显示**（草稿不算正式评估） | P0 |
| INT-18 | 正式评估后待评估列表 | 不再显示 | P0 |
| INT-19 | isOverdue 判定（scheduledAt>24h） | true/false 正确 | P1 |
| INT-20 | 录入评估（正式，PASS） | 面试 COMPLETED + 投递 OFFERABLE + 通知 | P0 |
| INT-21 | 录入评估（正式，REJECT） | 投递 REJECTED + reject_feedback 落库 + 通知 | P0 |
| INT-22 | 录入评估（正式，PENDING） | 投递保持 INTERVIEWING + 通知 HR | P1 |
| INT-23 | 存草稿（评语<20字） | 草稿保存成功，不触发流转 | P0 |
| INT-24 | 草稿后正式提交（覆盖转正式） | UPDATE 覆盖 is_draft=0，触发流转 | P0 |
| INT-25 | 重复正式评估 | 4104 | P0 |
| INT-26 | 正式提交评语<20字 | 4103 | P0 |
| INT-27 | 评分超出 1-5 | 4103 | P1 |
| INT-28 | 结论非法值 | 4103 | P1 |
| INT-29 | 评估时面试非 IN_PROGRESS | 4101 | P0 |
| INT-30 | 查看评估正常 | 四维/评语/结论/evaluatorName | P0 |
| INT-31 | 查看评估无记录 | 404 | P1 |
| INT-32 | 面试不存在/跨企业操作 | 4100 | P0 |
| INT-33 | INTERVIEWER 操作他人面试（start/评估/查看） | 4011 | P0 |
| INT-34 | HR_ADMIN 操作本企业全部面试 | 通过 | P1 |
| INT-35 | 并发双正式提交评估 | uk_interview_id 兜底→4104 | P1 |

#### 3.1.6 Offer管理（IF-023 ~ IF-030 + 定时任务）

**需求描述：** 发起/列表/HC概览/催促/撤回、候选人详情/接受/拒绝、过期扫描与 HC 补偿对账。

**三层状态流转（hr_offer / 投递 / HC 流水）：**

| 操作 | hr_offer | 投递(resume_application) | HC(job_hc_reservation) |
|------|----------|---------------------------|------------------------|
| 发起 | SENT（expiresAt=+3天） | OFFERABLE→OFFERED | RESERVED（预冻结） |
| 接受 | SENT→ACCEPTED | OFFERED→OFFER_ACCEPTED（终态） | RESERVED→CONFIRMED |
| 拒绝 | SENT→REJECTED | OFFERED→OFFER_DECLINED（终态） | RESERVED→RELEASED |
| 撤回 | SENT→WITHDRAWN（终态） | OFFERED→OFFERABLE（best-effort回退） | RESERVED→RELEASED |
| 过期 | SENT→EXPIRED（终态） | OFFERED→OFFERABLE（best-effort回退） | RESERVED→RELEASED |
| 对账 | 终态回写 last_sync_time | — | RELEASED（幂等兜底） |

**系统设计（代码已核对）：**
- `createOffer`：HR_ADMIN → 投递 OFFERABLE（4201）→ entryDate>today/salary>0/expiresInDays∈[1,30] → **重复发起校验**（最新一条 SENT/ACCEPTED/REJECTED→4205；WITHDRAWN/EXPIRED 放行）→ Snowflake id → 事务内 reserve（B 2201→4203）→ insert → 投递 OFFERED；异常补偿 release(D_PERSIST_FAILED) → 薪资软提示 salaryWarning（salaryNegotiable=false 且超范围）
- `listOffers`：status/dateRange（按 createdAt）/jobId 筛选，offerId 为 JSON 字符串
- `getHcOverview`：jobId 有→单岗；**无→公司级聚合**（listCompanyJobs→逐岗求和，best-effort 跳过错岗）
- `urgeOffer`：SENT（4201）+ 频率（24h≥2→4204）
- `retractOffer`：SENT→WITHDRAWN（乐观锁）→ release(REJECTED) → 投递回退 OFFERABLE（best-effort）
- `acceptOffer/rejectOffer`：候选人鉴权（401）→ 行锁 FOR UPDATE → 幂等（已 ACCEPTED/REJECTED 直接成功）→ SENT 且未过期（4201/4202）→ confirm/release → 投递联动
- 内部接口 `acceptOfferByApplication/rejectOfferByApplication`（syncApplication=false，不更新投递状态，避免死锁）

**Offer 状态机：**
```
                 ┌──> ACCEPTED（终态）
SENT ──┬──accept─┤
       ├──reject─┴──> REJECTED（终态）
       ├──retract────> WITHDRAWN（终态）
       └──过期扫描────> EXPIRED（终态）
```

**测试场景推演：**

| 场景ID | 场景 | 预期 | 优先级 |
|--------|------|------|:---:|
| OFR-01 | 发起 Offer（正常） | reserve 成功 + hr_offer SENT + 投递 OFFERED + offerId 字符串 | P0 |
| OFR-02 | 投递非 OFFERABLE 发起 | 4201 | P0 |
| OFR-03 | 已有 SENT/ACCEPTED/REJECTED Offer 重复发起 | 4205 | P0 |
| OFR-04 | WITHDRAWN/EXPIRED 后重发 | **放行**（新 SENT 记录） | P0 |
| OFR-05 | 并发双发同投递 | 应用层校验兜底（无 DB 唯一约束） | P1 |
| OFR-06 | entryDate 非未来 | 400"入职时间必须在未来日期" | P1 |
| OFR-07 | salary<=0 | 400"薪资必须大于0" | P1 |
| OFR-08 | expiresInDays 不在 1-30 | 400 | P1 |
| OFR-09 | reserve 时 HC 不足（B 2201） | 4203 | P0 |
| OFR-10 | reserve 成功但本地插入/投递联动失败 | 补偿 release(D_PERSIST_FAILED) 后回滚 | P0 |
| OFR-11 | 薪资超岗位范围（salaryNegotiable=false） | salaryWarning.warn=true，**不拦截** | P0 |
| OFR-12 | salaryNegotiable=true 超范围 | 不提示 | P1 |
| OFR-13 | Offer 列表正常 | 候选人名/岗位名/statusDesc 拼装，offerId 字符串 | P0 |
| OFR-14 | 列表按 status/dateRange/jobId 筛选 | dateRange 按 createdAt | P1 |
| OFR-15 | HC概览（传 jobId） | 单岗四项 HC 正确 | P0 |
| OFR-16 | HC概览（不传 jobId） | 公司级聚合求和，jobTitle="全公司" | P0 |
| OFR-17 | 公司无岗位时 HC概览 | 全 0 | P2 |
| OFR-18 | 聚合中单岗查询失败 | 跳过该岗，其余正常（best-effort） | P2 |
| OFR-19 | 催促（SENT 正常） | urgeCount+1 | P1 |
| OFR-20 | 催促 24h 内 ≥2 次 | 4204 | P0 |
| OFR-21 | 催促非 SENT | 4201 | P1 |
| OFR-22 | 撤回（SENT→WITHDRAWN） | release + 投递回退 OFFERABLE + 通知 | P0 |
| OFR-23 | 撤回非 SENT | 4201 | P1 |
| OFR-24 | 候选人查看 Offer 详情 | salary/entryDate/jobTitle/companyName/expiresAt | P0 |
| OFR-25 | 非候选人查看详情 | 401 | P0 |
| OFR-26 | 详情 Offer 不存在 | 4200 | P1 |
| OFR-27 | 候选人接受（正常） | Offer ACCEPTED + 投递 OFFER_ACCEPTED + confirm | P0 |
| OFR-28 | 接受已过期 Offer | 4202 | P0 |
| OFR-29 | 接受 confirm 失败（名额不足） | 4203，Offer 保持 SENT | P0 |
| OFR-30 | 重复接受 | 幂等直接成功 | P0 |
| OFR-31 | 候选人拒绝（正常） | Offer REJECTED + 投递 OFFER_DECLINED + release | P0 |
| OFR-32 | 拒绝 release 失败 | 记日志不阻塞（对账兜底） | P1 |
| OFR-33 | 非候选人接受/拒绝 | 401 | P0 |
| OFR-34 | INTERVIEWER 访问 Offer 接口 | 4011 | P0 |
| OFR-35 | 并发接受（D 端 + C 端同 offer） | 一个成功一个 4201，无重复 confirm | P1 |

**定时任务测试：**

| 场景ID | 场景 | 预期 | 优先级 |
|--------|------|------|:---:|
| SCH-01 | 过期扫描：SENT 且 expires_at<=NOW | 条件更新 EXPIRED + release + 通知 + 投递回退 | P0 |
| SCH-02 | 过期扫描 rows=0（已被确认/撤回） | 跳过，不释放/不通知 | P1 |
| SCH-03 | 过期扫描 release 失败 | 记日志，继续下一条 | P1 |
| SCH-04 | 对账：终态且 last_sync_time IS NULL | release 幂等 + 回写 last_sync_time | P0 |
| SCH-05 | 对账 ACCEPTED | 不入对账 | P1 |
| SCH-06 | 对账 release 失败 | 记日志，下轮重试 | P1 |
| SCH-07 | 过期扫描与候选人确认并发 | 行锁/条件更新互斥，无重复释放 | P1 |

**C 端反向同步（IF-043/044）测试：**

| 场景ID | 场景 | 预期 | 优先级 |
|--------|------|------|:---:|
| SYN-01 | C 端接受（PUT /applications/{id}/offer/accept） | C transition OFFERED→OFFER_ACCEPTED + D hr_offer ACCEPTED + confirm | P0 |
| SYN-02 | C 端拒绝 | C transition + D hr_offer REJECTED + release | P0 |
| SYN-03 | D 幂等重试（已 ACCEPTED 投递重调） | 直接成功 | P0 |
| SYN-04 | C 调 D 时 Offer 已过期 | 4202，C 回滚投递 | P1 |
| SYN-05 | C 调 D 时 Offer 已被并发处理 | 4201，C 回滚 | P1 |
| SYN-06 | C 调 D confirm 失败（名额不足） | 4203，C 回滚，提示"名额不足" | P1 |
| SYN-07 | D 成功但 C 提交失败 | 投递 OFFERED、hr_offer ACCEPTED；重试 D 幂等自愈 | P2 |
| SYN-08 | D 端独立 accept/reject 仍正常 | 联动投递 OFFER_ACCEPTED/OFFER_DECLINED | P0 |
| SYN-09 | 内部接口免鉴权放行 | `/internal/offers/**` 网关白名单 | P1 |

#### 3.1.7 个人中心（IF-031 ~ IF-037）

**需求描述：** 账号信息查询/修改、改密码、改手机号、改邮箱（7 接口，HR 与面试官共用）。

**系统设计（代码已核对）：** 全部 `requireActiveMember`（4011）；name/avatar/密码/手机号/邮箱**薄转发 lingxi-user**（AccountFeignClient，透传 token，失败透传 A 错误码）；department 本地 `updateDepartment`；profile 查询 A 失败降级（name="用户"+id 等）。

> **裁剪**：position 不展示、操作日志不实现、邮箱改需发验证码到新邮箱确认（非免验证码）。

**测试场景推演：**

| 场景ID | 场景 | 预期 | 优先级 |
|--------|------|------|:---:|
| ACC-01 | GET profile 正常 | id/name/phone/email/avatar/department/companyName/role，**无 position** | P0 |
| ACC-02 | A 查询失败 | 降级（name="用户"+id、phone="***"），不 500 | P1 |
| ACC-03 | 无成员/企业 | department/companyName=null | P2 |
| ACC-04 | PUT profile 改 name+avatar | A sys_user 更新（透传 token） | P0 |
| ACC-05 | PUT profile 改 department | hr_company_member.department 更新 | P0 |
| ACC-06 | PUT profile 全空 | 400"请至少填写一项" | P1 |
| ACC-07 | name 每月修改超限 | 透传 A NAME_UPDATE_LIMIT | P2 |
| ACC-08 | 改密码 confirmPassword 不一致 | 400 | P0 |
| ACC-09 | 改密码正确 | A 改密成功，token 失效需重登 | P0 |
| ACC-10 | 旧密码错误/新密码强度不足 | 透传 A 400 | P1 |
| ACC-11 | phone/send-code 正常 | 新手机号收验证码 | P1 |
| ACC-12 | phone 改号验证码错误 | 透传 A 错误码 | P1 |
| ACC-13 | phone 新手机号格式非法 | D 侧 @Phone 400 | P1 |
| ACC-14 | email/send-code + email 改邮箱 | 新邮箱收验证码 → 更新 sys_user.email | P1 |
| ACC-15 | 手机号/邮箱修改成功后 | A 清 token 重新登录 | P1 |
| ACC-16 | 非本企业成员调个人中心 | 4011 | P0 |
| ACC-17 | 面试官（INTERVIEWER）调个人中心 | 正常共用 | P1 |
| ACC-18 | 网关路由 `/api/v1/hr/**` 通配生效 | 不再 404 | P1 |

#### 3.1.8 Mock Interview Agent（IF-038 ~ IF-042）

**需求描述：** 求职者 C 端 AI 模拟面试：出题（SSE）→ 答题评分（SSE）→ 报告（SSE）。

**系统设计（代码已核对）：**
- 百宝箱 tboxsdk 3 个生成型工作流：出题 `202608APE9vh20999665` / 评分 `202608APlpZR21033809` / 报告 `202608AP7lse21015615`；`hr.agent.mock=true` 走 MockTboxLlmClient 预设 JSON
- `generate`：每日次数校验（`hr.mock-interview.quota` 默认 3，0=不限，超限 40018 不建会话）→ 建 mock_session（session_id=Redis INCR `mock-YYYYMMDD-NNN`）→ FETCH_JOB（B requirements，失败降级仅 jobTitle）→ FETCH_RESUME（C 能力模型为主+简历详情为辅，无简历 resumeUsed:false）→ GENERATING（LLM 失败→内置模板题）→ 逐题写 mock_answer 快照 → SSE result+done
- `answer`：session IN_PROGRESS（40014/4007）→ 评分工作流（15s 超时→overall=0"评分服务繁忙"）→ overall=tech×0.5+expr×0.3+depth×0.2 → 同题 UPDATE 覆盖
- `skip`：写 is_skipped=1
- `report`：无评分记录→40015 → 聚合各题均分 → 报告工作流（LLM 失败→分数阈值规则）→ 写 mock_report + session COMPLETED
- `getQuestions`：继续面试取题

**会话状态机：** `IN_PROGRESS →(生成报告)→ COMPLETED`

**SSE 协议：** generate/report：progress→result→done（失败 error）；answer：progress→result。

**测试场景推演：**

| 场景ID | 场景 | 预期 | 优先级 |
|--------|------|------|:---:|
| MOCK-01 | 生成题目（正常，mock=true） | SSE progress(FETCH_JOB/FETCH_RESUME/GENERATING)→result(题目)→done | P0 |
| MOCK-02 | 每日次数超限（quota=3） | 40018，不建会话，SSE error | P0 |
| MOCK-03 | quota=0 | 不限次数 | P1 |
| MOCK-04 | 岗位要求获取失败 | 降级仅 jobTitle 出题，不阻塞 | P1 |
| MOCK-05 | 无简历/简历获取失败 | 简历输入为空，resumeUsed:false | P1 |
| MOCK-06 | LLM 出题失败 | 内置模板题（SSE 降级） | P1 |
| MOCK-07 | 输出题目数≠questionCount | 多截取/少补齐 | P1 |
| MOCK-08 | questionCount=5/8/10 | 题量正确；其他值默认 5 | P1 |
| MOCK-09 | session_id 生成唯一 | mock-日期-三位序号递增 | P1 |
| MOCK-10 | 获取题目（继续面试） | 返回题目列表 | P1 |
| MOCK-11 | 提交答案（正常） | SSE result 三维分+overall+aiComment，落库 | P0 |
| MOCK-12 | 同题重复提交 | UPDATE 覆盖最后一次 | P0 |
| MOCK-13 | 评分 LLM 超时（>15s） | overall=0"评分服务繁忙" | P1 |
| MOCK-14 | 分数越界 | clamp 0-100 | P2 |
| MOCK-15 | 已 COMPLETED 会话再答题 | 40014 | P0 |
| MOCK-16 | session 不存在 | 4007 | P0 |
| MOCK-17 | 题目不存在（questionNumber 非法） | 4007"题目不存在，请先重新生成面试" | P1 |
| MOCK-18 | 跳过题目 | is_skipped=1，answer 为空 | P1 |
| MOCK-19 | 生成报告（正常） | SSE result 完整报告 + mock_report 落库 + session COMPLETED | P0 |
| MOCK-20 | 无有效答题记录生成报告 | 40015 | P0 |
| MOCK-21 | 报告 LLM 失败 | 分数阈值规则生成 | P1 |
| MOCK-22 | COMPLETED 后重看报告 | 幂等返回历史报告 | P1 |
| MOCK-23 | 非本人访问他人 session | 4007（归属校验） | P0 |
| MOCK-24 | 未登录调 mock 接口 | 401 | P0 |
| MOCK-25 | 岗位已下线 | 不校验岗位状态，可完成面试 | P2 |

#### 3.1.9 消息通知（方案：Feign 直调 lingxi-chat）

**需求描述：** 业务事件（筛选/面试/Offer）向候选人/HR 发送通知。

**系统设计（代码已核对）：** 3 个 Notifier（CandidateNotifier/InterviewNotifier/OfferNotifier）→ `NotificationFeignClient` `POST /internal/notifications` → lingxi-chat 写 sys_notification + WebSocket 推送；type 用 lingxi-chat ChatConstant（RESUME_VIEWED / INTERVIEW_INVITE / INTERVIEW_SCHEDULE / OFFER_RECEIVED / OFFER_MANAGE）。**全程 best-effort**：失败记 warn、`notificationSent=false`。

**测试场景推演：**

| 场景ID | 场景 | 预期 | 优先级 |
|--------|------|------|:---:|
| NTF-01 | 标记合适→通知候选人 | RESUME_VIEWED，通知落库 + WebSocket | P0 |
| NTF-02 | 标记不合适→通知（含落选反馈） | 落库内容含反馈 | P1 |
| NTF-03 | 创建面试→双端通知 | 候选人 INTERVIEW_INVITE + 面试官 INTERVIEW_SCHEDULE | P0 |
| NTF-04 | 评估 PASS/REJECT/PENDING→通知 | 对应 type | P1 |
| NTF-05 | 发起/催促/撤回/过期→Offer 通知 | OFFER_RECEIVED（候选人）/ OFFER_MANAGE（HR） | P0 |
| NTF-06 | lingxi-chat 不可用 | best-effort，notificationSent=false，主流程不阻塞 | P0 |
| NTF-07 | 通知列表/未读/已读 | 复用 lingxi-chat（D 不实现） | P2 |

#### 3.1.10 数据权限隔离

**系统设计（代码已核对）：**

| 角色 | 候选人可见范围 | 面试可见范围 | Offer 可见范围 |
|------|----------------|--------------|----------------|
| HR_ADMIN | 本企业全部投递 | 本企业全部面试 | 本企业全部 Offer |
| INTERVIEWER | 列表/Top5/简历可看 | 仅本人面试（`interviewer_id==本人`） | **不可见**（4011） |
| 候选人 | 本人投递 | — | 本人 Offer（userId==candidateId，否则 401） |

**测试场景推演：** 覆盖 3.1.4/3.1.5/3.1.6 中的越权用例（CAN-18、INT-33、OFR-25/33/34、ACC-16 等）。

### 3.2 测试用例设计说明

- **Xmind 场景树**（建议）：以「模块四」为根，一级节点为 9 大模块，二级节点为上述场景ID，三级为具体步骤/预期。
- **用例组织**：按模块+场景ID 编号（如 CAN-01），每条用例含前置条件、请求数据、预期结果、优先级。
- **优先级排序原则**：🔴 P0 = 核心主链路/权限/数据一致性；🟡 P1 = 异常分支/边界；🟢 P2 = 次要/展示。
- **测试数据准备**：参考 `docs/hr_test_data.sql`（HR 16735263528 / company 1 / 投递 50003、50113 等），本地与服务器基线不同，推服务器前需先对比 max(id) 段。

### 3.3 外部接口测试分析

**跨服务 Feign 契约（需 Mock/联调验证）：**

| 外部接口 | 参数/契约 | 关键校验点 |
|----------|-----------|-----------|
| `POST /internal/applications/{id}/status` | status + rejectFeedback(可选 JSON) | 投递状态机直转是否被 C 支持（SUBMITTED/VIEWED→SCREENED/REJECTED；INTERVIEWING→SCREENED；OFFERABLE→OFFERED；OFFERED→OFFER_ACCEPTED/OFFER_DECLINED/OFFERABLE） |
| `GET /internal/applications/list` | companyId/status/jobId/minMatchScore/keyword/sortBy/page/pageSize | 返回 PageResult + total；透传筛选排序 |
| `GET /internal/applications/{id}` | — | companyId/resumeId/jobTitle/candidateId/status |
| `GET /internal/resumes/{id}/detail` | — | 全量 15 字段；免归属校验 |
| `POST /internal/jobs/{jobId}/hc/reserve\|confirm\|release` | companyId/offerId/candidateId（confirm 无 candidateId；release 带 reason） | B 2201→4203；release 幂等 |
| `GET /internal/jobs/{jobId}` | — | 岗位 title/HC/薪资字段；B 不存在→透传 |
| `GET /internal/jobs/company/{companyId}` | — | 岗位列表（jobId/title/status/availableHc） |
| `GET /internal/jobs/{jobId}/requirements` | — | interviewFocus/coreSkills；失败降级 |
| `POST /api/v1/auth/register` | phone/code/password/name/role | role=HR/INTERVIEWER 是否被 A 接受 |
| `POST /internal/notifications` | userId/type/title/content/targetType/targetId | type 用 lingxi-chat ChatConstant；best-effort |
| `GET /api/v1/user/info` 等 6 个个人中心接口 | 透传 token | A 校验透传、错误码透传 |
| `GET /api/v1/resumes/{id}/ability-model` 等 | token 透传（求职者身份） | owner 校验；能力模型 5 维 |

**超时/降级：** 连接超时 3s、读超时 5s（application.yml 已配）；关键路径（HC 确认/投递联动）失败直接报错；非关键（用户姓名/公司名）降级默认值；列表类接口不降级为空（前端依赖分页）。

### 3.4 迁移测试（可选）

- **hr_offer 删唯一索引**：本地与服务器需执行 `ALTER TABLE hr_offer DROP INDEX uk_application_id; ADD INDEX idx_application_id`（schema.sql 已改，运行库需手工同步）。
- **无新增表/字段**（本期无 DDL 变更除上述删索引）；`mock_*` 三表 DDL 已由 schema.sql 提供。
- **兼容性**：Offer 状态 WITHDRAWN 列宽 VARCHAR(16) 足够；offerId Snowflake Long 序列化为字符串，前端按字符串处理。

### 3.5 全链路测试分析

**主链路 1（候选人筛选→面试→Offer 录用）：**
```
投递 SUBMITTED → 标记合适 SCREENED → 创建面试 INTERVIEWING → 开始 IN_PROGRESS
→ 评估 PASS → OFFERABLE → 发起 Offer → OFFERED → 候选人接受 → OFFER_ACCEPTED
（同时：HC RESERVED→CONFIRMED；通知逐节点发送）
```
- 全链路验证：状态每一步对齐 C 投递状态机；HC 每一步对齐 B 流水；通知落库。

**主链路 2（淘汰链路）：**
```
标记不合适 / 面试 REJECT → 投递 REJECTED + reject_feedback 落库 + 通知候选人
```

**主链路 3（Offer 反向同步，双链路并存）：**
```
C 端投递页接受/拒绝 → C transition → D /internal/offers/accept|reject → hr_offer + HC
（D 端独立入口 /api/v1/hr/offers/{id}/accept|reject 仍可用，联动投递）
```

**主链路 4（Mock 面试）：**
```
generate(SSE) → 建会话+出题 → answer/skip → report(SSE) → session COMPLETED
```

**错误码/异步提示点：** 各接口错误码透传；通知失败 notificationSent=false；SSE error 事件。

### 3.6 配置测试

| 配置项 | 位置 | 边界/场景 |
|--------|------|-----------|
| `hr.mock-interview.quota` | application.yml（默认 3，0=不限） | quota=0 放行；quota=3 第 4 次 40018 |
| `hr.agent.mock` | application.yml | true→MockTboxLlmClient 预设 JSON；false→真百宝箱 |
| `hr.agent.llm.timeout-ms` / `score-timeout-ms` | application.yml | 出题/报告 60s；评分 15s |
| `baibaoxiang.*` appId/key | application.yml | 3 个工作流 appId 正确 |
| `spring.redis` | application.yml | 会话号 INCR；Redis 宕机→genSessionId 用 seq=1（可接受） |
| 时间冲突窗口 | 代码常量 60min | 边界重叠 |
| 有效期默认 expiresInDays | 代码常量 3 | 默认 3 天 |
| 面试评估评语 ≥20 字 | 代码常量 | 边界 19/20/21 字 |
| `storage.type` | application-dev.yml | aliyun-oss/minio/本地；生产与本地需一致 |
| 网关路由 `/api/v1/hr/**` 通配 | lingxi-gateway | **需同步成员A/生产**，否则 account 等 404 |

---

## 4、非功能性需求测试分析

### 4.1 并发测试分析

| 并发场景 | 保护机制 | 验证点 |
|----------|----------|--------|
| 并发发起同投递 Offer | 应用层校验（已删唯一索引，无 DB 兜底） | 两个请求并发→一个 4205（概率场景，可 SQL 造数） |
| 并发接受/拒绝同 Offer（D端+C端） | `selectByIdForUpdate` 行锁 + 乐观锁 WHERE status='SENT' | 一个成功一个 4201 |
| 并发双正式提交评估 | `uk_interview_id` 唯一约束 DuplicateKeyException→4104 | 一个成功一个 4104 |
| 过期扫描 vs 候选人确认并发 | 条件更新 rows=0 / 行锁互斥 | 无重复 release |
| 并发标记候选人 | C 侧状态机（直转校验） | 后到者 4302 |
| 并发创建同编码企业/邀请码 | uk_name / uk_invite_code + 循环查重 | 一个成功一个 4001 |
| 并发创建面试（同面试官同时间） | `countByInterviewerInTimeRange`（无锁，弱约束） | 极端并发可能双成功→按设计可接受（缓冲区窗口） |
| 并发刷新邀请码 | updateInviteCode 单行更新 | 最终一个码生效 |

> ⚠️ **并发弱项（风险）**：Offer 重发无 DB 唯一约束、面试时间冲突无锁，极端并发下可能双成功。设计中已接受（概率低），测分标注「应用层校验兜底」。

### 4.2 权限安全

| 场景 | 验证点 |
|------|--------|
| 候选人越权访问他人 Offer（详情/接受/拒绝） | 401 |
| INTERVIEWER 操作他人面试 | 4011 |
| INTERVIEWER 访问 Offer 管理接口 | 4011 |
| 非 HR_ADMIN 标记候选人/管理成员/刷新邀请码 | 4011 |
| 非本企业成员访问列表/简历 | 4011 |
| 跨企业投递/面试/Offer | 4301 / 4100 / 4200（隔离） |
| 未登录访问 `/api/v1/hr/**` | 401 |
| 内部接口 `/internal/offers/**` 免鉴权 | 网关白名单；与现有内部接口一致（风险同 D→C，本期不加服务 token） |
| 面试官访问候选人列表（数据范围） | 本企业全部可看（系分约定） |
| Mock 会话归属 | 非本人 session 4007 |

### 4.3 数据一致性

| 场景 | 机制 | 验证点 |
|------|------|--------|
| 发起 Offer 事务一致性 | @Transactional + 补偿 release(D_PERSIST_FAILED) | 中间任一失败整体回滚，HC 不残留 |
| accept 后投递联动失败 | 同一事务回滚，Offer 仍 SENT | 无半程状态 |
| 撤回/过期投递回退失败 | best-effort 记日志（不回退） | Offer 终态 + 投递 OFFERED 可能短暂不一致（HR 可重新发起） |
| reject/retract release 失败 | 记日志，HC 补偿对账（1h）幂等兜底 | 对账后流水 RELEASED + last_sync_time |
| C 端反向同步失败 | C 回滚 transition；D 幂等自愈 | 重试后一致 |
| 通知不一致 | best-effort，notificationSent=false | 不影响主数据 |
| 用户信息降级 | batch→单查→兜底 | 展示兜底，无 500 |

---

## 5、三板斧

### 5.1 可监控

- **业务日志**：各 Service 关键操作均有 `log.info`（发起Offer/接受/拒绝/过期/对账/标记/面试等，含 offerId/applicationId/companyId/notificationSent）。
- **定时任务**：OfferExpireScheduler / HcReconcileScheduler 处理条数 `log.info`；异常 `log.error`（单条隔离）。
- **降级监控**：Feign 失败 `log.warn`；通知失败 `notificationSent=false`。
- **建议补充**：Offer 各状态数量/过期数/对账成功数指标大盘；`hr_offer` 中 SENT 超期未处理 Offer 数；SSE 失败率。

### 5.2 可灰度

- **Mock Interview Agent 可配置灰度**：`hr.agent.mock=true/false` 一键切换 mock 预设 JSON 与真百宝箱，可先灰度再全量。
- **功能开关**：`hr.mock-interview.quota` 可调限流。
- **路由白名单**：`/api/v1/hr/**` 通配已放开；如需灰度可按前缀精确控制（需网关配合）。

### 5.3 可回滚

- **代码**：模块四已全部交付；如遇问题可整体回退 lingxi-hr 版本。
- **数据**：`hr_offer` 删唯一索引为不可逆 DDL——回滚需重新 `ADD UNIQUE INDEX uk_application_id`（但会阻止重发功能）；无新增表，回滚无残留。
- **依赖**：C 投递状态机扩展（OFFER_ACCEPTED/OFFER_DECLINED）为 C 侧既有；B HC release 幂等。
- **版本回切**：通知方案为 Feign 直调（无 MQ 消费者），无消息积压风险。

---

## 6、风险评估

| 编号 | 风险 | 等级 | 说明/建议 |
|------|------|:---:|-----------|
| R-01 | **PRD/系分 vs 实际差异：邀请码加入 role=HR_ADMIN**（系分约定 INTERVIEWER） | 🟡 中 | 已确认（2026-08-03）。测分按 HR_ADMIN 实现验证，遗留产品确认 |
| R-02 | **Offer 重发无 DB 唯一约束**（已删 uk_application_id） | 🟡 中 | 并发双发仅应用层校验兜底；验证并发场景，必要时恢复唯一索引方案 |
| R-03 | **时间冲突校验无锁** | 🟢 低 | 极端并发双成功可接受；验证常规冲突拦截 |
| R-04 | **C 端反向同步依赖 C 侧接入**（HrOfferFeignClient） | 🔴 高 | D 侧已就绪，C 侧未落地（2026-08-08）；联调需拉前端 C，未接入前 C 端投递页接受/拒绝存在 hr_offer 不一致 |
| R-05 | **通知 best-effort** | 🟢 低 | 通知失败不阻塞主流程；前端需容忍 notificationSent=false |
| R-06 | **AI 反馈 SSE 已裁剪**（feedback 留 null） | 🟡 中 | 与系分 5.5.3 的 AI 反馈预览不一致；已确认不做，前端按 null 处理 |
| R-07 | **Top5 匹配度门槛未实现**（成员D系分 line 1116 <70% 不进 Top5） | 🟡 中 | 与排期冲突，本期不实现；需产品确认 |
| R-08 | **advantages/risks 未落地**（C 侧未生成，返回 null） | 🟢 低 | Top5 不阻塞；前端 null 兜底 |
| R-09 | **Redis 缓存优化未实施**（coding-plan 待开发） | 🟢 低 | 不影响功能；性能热点（面试列表 N+1 fetchJobTitle）留待优化 |
| R-10 | **网关路由通配需同步** | 🟡 中 | `/api/v1/hr/**` 通配依赖成员A/生产同步，未同步则 account 等 404 |
| R-11 | **`hr_offer` 删索引 DDL 需在运行库执行** | 🟡 中 | 本地/服务器需手工 ALTER，否则 WITHDRAWN/EXPIRED 重发撞唯一键 |
| R-12 | **文件存储环境不一致**（本地 OSS/MinIO 混用） | 🟡 中 | 头像/营业执照上传本地与生产配置需一致；MinIO 未起则传文件失败 |
| R-13 | **邮箱验证码 SMTP 发件人校验**（QQ 501） | 🟡 中 | 已修（setFrom 取 spring.mail.username）；QQ 每日限额，联调注意限流 |
| R-14 | **Mock 面试真百宝箱稳定性**（hr.agent.mock=false） | 🟡 中 | 依赖第三方平台；超时走降级；验证 appId/token 与 SDK 版本引用 |
| R-15 | **内部接口免鉴权** | 🟢 低 | `/internal/offers/**` 网关白名单，与现有内部接口一致；本期不加服务 token（已确认） |
| R-16 | **数据权限两套角色易混淆** | 🟢 低 | sys_user.role 与 hr_company_member.role 独立；验证勿以全局角色判断企业权限 |

---

## 7、工时评估（测试计划）

| 阶段 | 内容 | 预估（人日） |
|------|------|:---:|
| 测试准备 | 环境搭建、测试数据准备、Feign 契约 mock | 1 |
| 功能测试 | 9 大模块 P0/P1 用例执行（注册/企业/成员/候选人/面试/Offer/个人中心/Mock/内部） | 3 |
| 非功能测试 | 并发、权限、数据一致性、定时任务 | 1 |
| 全链路回归 | 4 条主链路 + C 端反向同步 + 通知 | 1 |
| 缺陷跟踪与回归 | 缺陷修复回归 | 1 |
| **合计** | | **7** |

**建议测试顺序：** ① 企业认证与成员管理（地基）→ ② 候选人管理 → ③ 面试协同 → ④ Offer 管理（含定时任务）→ ⑤ C 端反向同步 → ⑥ 个人中心 → ⑦ Mock Interview → ⑧ 全链路回归。

# 5. 模块四：HR服务与Mock Interview Agent（成员D）
**接口数量：** 43个（另有 7 个内部接口：Offer接受/拒绝反向同步 2 + 新投递通知HR 1 + 企业内部查询 4） | **API前缀：** `/api/v1/hr/*`, `/api/v1/mock-interview/*`, `/internal/*`  
**数据库表：** 11张（6核心：hr_company/hr_company_member/hr_company_certification/hr_interview/hr_interview_evaluation/hr_offer；附加：msg_notification/user_notification_preference/mock_session/mock_answer/mock_report）  
**错误码范围：** 4001-4999

> **实现状态（2026-08-08）：** 模块四开发全部完成，本地全链路联调通过。实际交付 **43 个接口**（系分规划 52 个中 **15 个裁剪/复用**：AI反馈SSE 1 + 消息通知 4 + Dashboard 4 + 内部统计接口 5，分别由"复用 lingxi-chat `/api/v1/notifications/*`"、"前端复用已有接口拼装"、"lingxi-admin 直接查 lingxi 库"承担；另 2026-08-10 新增 HR 公开信息 `GET /api/v1/hr/{hrId}/public`），另交付 **7 个内部接口**（C端接受/拒绝 Offer 反向同步 2 + 新投递通知 HR 1 + 企业内部查询 4，见 5.5.12）。
>
> 
> 1. **通知方案变更**：候选人/HR 通知**不走 RocketMQ**（`hr-notification` topic 无消费者），改 **Feign 直调 lingxi-chat `POST /internal/notifications`**（写 `sys_notification` + WebSocket 实时推送），best-effort 不阻塞主流程；
> 2. **Mock Interview 出题不依赖数据库题库**：AI 基于【岗位考察要点（B `/internal/jobs/{jobId}/requirements`）】+【简历能力模型（C `/api/v1/resumes/{id}/ability-model`，能力模型为主 + 简历详情为辅）】动态出题，接百宝箱 tboxsdk **3 个生成型工作流**（出题/评分/报告），代码收拢到 `com.lingxi.hr.agent` 包；
> 3. **Offer 状态机对齐 C 新枚举**：投递 `OFFERED`=待录用、`OFFER_ACCEPTED`/`OFFER_DECLINED`=终态；撤回→`WITHDRAWN`、过期→`EXPIRED` 后投递回退 `OFFERABLE` 且**可重发**（已删 `hr_offer.uk_application_id` 唯一索引）；接受/拒绝/详情为候选人身份接口；
> 4. **消息沟通（5.5.5）由公共模块/lingxi-chat 负责**（会话/消息表在 lingxi-user），不在 D 交付范围；Dashboard/内部统计接口裁剪。

---

## 5.1 功能模块
### 功能模块树
```plain
lingxi-hr 服务
├── HR注册（复用 lingxi-user /auth/register）
├── 企业信息 + 认证
│   ├── 创建企业（生成 6 位邀请码，同一事务建 HR_ADMIN 成员）
│   ├── 查看/更新企业信息（认证后 name 不可改）
│   ├── 提交认证申请（营业执照/证明材料选填，MinIO 上传）
│   └── 认证状态查询（pending 审核页依赖）
├── 成员管理
│   ├── 邀请码加入企业（校验认证通过，role=HR_ADMIN）
│   ├── 刷新邀请码（HR_ADMIN）
│   ├── 成员列表（batch?ids= 跨服务拼装）
│   ├── 创建面试官（复用 register，role=INTERVIEWER）
│   └── 移除面试官（硬删除，HR_ADMIN 不可移除）
├── 候选人管理
│   ├── 候选人列表（筛选/排序/分页，跨服务拼装）
│   ├── Top5高潜推荐
│   ├── 标记合适/不合适（AI 模板落选反馈 + 通知）
│   └── 查看候选人简历（Feign 调 C /internal/resumes/{id}/detail）
├── 面试协同
│   ├── 面试安排（创建/列表/开始/取消，时间冲突 60min 校验）
│   ├── 待评估列表（IN_PROGRESS 且无正式评估）
│   ├── 面试评估录入（4维度评分+评语+结论，草稿可覆盖转正式）
│   └── 查看面试评估
├── Offer管理
│   ├── 发起Offer（HC预冻结 + 薪资软提示 salaryWarning）
│   ├── Offer列表 / HC概览（jobId 可空=公司级聚合）
│   ├── 催促/撤回 Offer
│   ├── 候选人版详情/接受/拒绝（C端反向同步内部接口）
│   └── Offer过期扫描（5min）+ HC补偿对账（每小时）定时任务
├── 个人中心
│   ├── 账号信息查询/修改（name/avatar/department）
│   ├── 修改密码（薄转发 lingxi-user）
│   ├── 手机号修改（验证码发新手机号）
│   └── 邮箱修改（验证码发新邮箱）
├── HR公开信息（2026-08-10 新增，供求职者查看，含企业信息）
└── C端 Mock Interview Agent（百宝箱 3 生成型工作流）
    ├── 动态出题（岗位考察要点 + 简历能力模型，AI 定制 5/8/10 题）
    ├── AI 评分与点评（技术准确度 50% + 表达逻辑 30% + 知识深度 20%）
    └── 生成面试报告（亮点/短板/提升方案）
```

## 5.2 核心流程图
### 5.2.1 候选人筛选到面试安排全流程
```plain
求职者投递简历
     │
     ▼
HR进入候选人列表 → 查看Top5高潜推荐 → 查看候选人简历（Feign调C /internal/resumes/{id}/detail）
     │
     ├── 标记"合适" → 投递 SUBMITTED/VIEWED→SCREENED → Feign直调lingxi-chat通知求职者"通过简历筛选"
     │         │
     │         ▼
     │    安排面试（填写时间/方式/面试官；校验投递 SCREENED + 面试官 ACTIVE + 时间冲突60min）
     │         │
     │         ▼
     │    投递 INTERVIEWING → 双端通知（候选人 INTERVIEW_INVITE + 面试官 INTERVIEW_SCHEDULE）
     │         │
     │         ▼
     │    面试官查看候选人简历 → AI出题（岗位侧 Interview Agent / Mock Interview Agent）
     │         │
     │         ▼
     │    开始面试（IN_PROGRESS）→ 进行面试
     │         │
     │         ▼
     │    录入面试评估（4维度评分+评语+结论，草稿可覆盖转正式）
     │         │
     │         ├── 通过(PASS) → 面试 COMPLETED + 投递 OFFERABLE → 进入Offer流程
     │         ├── 待定(PENDING) → 面试 COMPLETED + 投递保持 INTERVIEWING + 通知HR安排复面
     │         └── 淘汰(REJECT) → 面试 COMPLETED + 投递 REJECTED + 模板落选反馈 + 通知求职者
     │
     └── 标记"不合适" → 投递 SUBMITTED/VIEWED→REJECTED → AI模板落选反馈（原因+提升建议）→ 通知求职者

取消面试（非终态） → 面试 CANCELLED → 投递回退 INTERVIEWING→SCREENED（候选人可被再次安排）
```

### 5.2.2 Offer全流程（预冻结方案）
```plain
面试评估通过（投递 OFFERABLE）
     │
     ▼
HR点击"发起Offer"
     │
     ▼
填写Offer信息（薪资/入职时间/职级/备注，expiresInDays 默认3天）
     │
     ▼
系统校验：
  ├─ 投递状态 == OFFERABLE（否则 4201）
  ├─ 入职时间在未来 + 薪资>0
  ├─ 该投递无活跃 SENT / 终态（ACCEPTED/REJECTED）Offer → 4205（WITHDRAWN/EXPIRED 可重发）
  └─ 薪资是否在岗位薪资范围内（超出仅返回 salaryWarning 软提示，不拦截）
     │
     ▼
创建Offer记录（Snowflake ID，状态SENT，expiresAt=NOW+3天）
     │
     ▼
事务内 Feign调lingxi-job → 预冻结Headcount（reserve HC，B 幂等）
     │      任一步失败 → 补偿 release(D_PERSIST_FAILED) 后整体回滚
     ▼
投递联动 OFFERABLE→OFFERED（待录用）→ Feign直调lingxi-chat推送Offer通知
     │
     ▼
候选人（求职者）操作分支（两条入口并存：D端 /offers/{id}/accept|reject；C端投递页 → 内部 /internal/offers/accept|reject）：
  ├── 确认接受
  │     ├─ 行锁 + 校验 Offer 状态 SENT 且未过期（4201/4202）
  │     ├─ 幂等（已 ACCEPTED 直接成功）
  │     ├─ Feign调lingxi-job确认占用HC（confirm，失败提示"名额不足，请联系HR"，Offer保持SENT）
  │     ├─ Offer: ACCEPTED, 投递: OFFERED→OFFER_ACCEPTED（已录用，终态）
  │     └─ 剩余可招聘HC==0 → 岗位自动关闭（B 侧）
  ├── 拒绝
  │     ├─ 选填拒绝原因
  │     ├─ Offer: REJECTED, 投递: OFFERED→OFFER_DECLINED（已拒绝，终态）
  │     └─ Feign调lingxi-job释放预冻结HC（release，失败记日志由对账兜底）
  ├── HR 撤回
  │     ├─ Offer: SENT→WITHDRAWN（终态）
  │     ├─ Feign释放HC（REJECTED 语义）
  │     └─ 投递回退 OFFERED→OFFERABLE（best-effort）→ 可再次发起
  └── 超时未确认 → 定时任务（5min扫描）处理
        ├─ Offer: SENT→EXPIRED（终态）
        ├─ 投递回退 OFFERABLE（best-effort）→ 可再次发起
        └─ Feign释放预冻结HC（EXPIRED 语义）
```

### 5.2.3 Mock Interview Agent面试流程
```plain
求职者选择目标岗位 → 点击"开始模拟面试"（先校验每日次数 hr.mock-interview.quota，默认3次）
     │
     ▼
【Step 1】生成会话 mock_session(status=IN_PROGRESS)，session_id 由 Redis INCR 生成（mock-YYYYMMDD-NNN）
     │
     ▼
【Step 2】FetchJobRequirementsTool → Feign调B /internal/jobs/{jobId}/requirements 获取岗位考察要点
     │    失败 → 岗位输入降级为仅 jobTitle（不阻塞）
     ▼
【Step 3】FetchCandidateResumeTool → Feign调C 能力模型 + 简历详情（token透传）
     │    能力模型为主（5维分+subDimensions）+ 简历详情为辅（cardStructure 截断~2000字）
     │    无简历/失败 → 简历输入为空，按通用岗位要求出题（resumeUsed:false）
     ▼
【Step 4】百宝箱"出题工作流"（appId 202608APE9vh20999665）→ AI 基于岗位+简历动态生成 N 道题
     │    出题策略：BASIC 30% + PROJECT 40% + BOUNDARY 20% + COMPREHENSIVE 10%，由浅入深
     │    逐题写 mock_answer 快照
     ▼
【Step 5】展示第1题 → 求职者作答 → 百宝箱"评分工作流"（appId 202608APlpZR21033809）评分与点评
     │    评分维度：技术准确度 50% + 表达逻辑 30% + 知识深度 20%
     │    同题重复提交 = UPDATE 覆盖；跳过 → is_skipped=1
     ▼
【Step 6】循环直到所有题目完成 → 百宝箱"报告工作流"（appId 202608AP7lse21015615）
     │    综合评分 + 分维度评分 + 亮点 + 短板 + 提升方案
     ▼
【Step 7】写 mock_report + mock_session(status=COMPLETED, overall_score) → SSE 返回面试报告
```

### 5.2.4 消息通知流程
```plain
业务事件产生（标记筛选/安排面试/Offer发起/接受/拒绝/过期等）
     │
     ▼
D 侧构造通知请求（{userId, type, title, content, targetType, targetId}）
     │
     ▼
Feign 直调 lingxi-chat POST /internal/notifications（NotificationFeignClient）
     │     type 用 lingxi-chat ChatConstant（如 RESUME_VIEWED / INTERVIEW_INVITE / INTERVIEW_SCHEDULE / OFFER_RECEIVED / OFFER_MANAGE）
     │
     ▼
lingxi-chat 写 sys_notification 表 + 用户在线时 WebSocket 实时推送（/ws/message）
     │
     ▼
HR/候选人端通知列表/未读/已读 → 复用 lingxi-chat /api/v1/notifications/*（D 不实现）
     │
     ▼
全程 best-effort：Feign 失败记 warn 日志，notificationSent=false，不阻塞主流程
```

## 5.3 时序图
### 5.3.1 Offer确认 — Headcount预冻结转正确认时序
```plain
候选人(C端)      前端           lingxi-hr        lingxi-job       lingxi-chat(通知)
    │             │              │                │               │
    │──点击"确认"─▶│              │                │               │
    │             │──POST /offers/{id}/accept────▶│                │
    │             │              │──SELECT offer FOR UPDATE       │
    │             │              │──校验status==SENT且未过期       │
    │             │              │──幂等：已ACCEPTED直接成功        │
    │             │              │                │               │
    │             │              │──Feign: POST /internal/jobs/{jobId}/hc/confirm──▶│
    │             │              │                │──UPDATE reservation: RESERVED→CONFIRMED
    │             │              │                │──UPDATE job_post: reserved_hc--, confirmed_hc++
    │             │              │◀─确认成功──────│               │
    │             │              │                │               │
    │             │              │──UPDATE offer: status=ACCEPTED, accepted_at=NOW() │
    │             │              │──Feign: PUT /applications/{id}/status?status=OFFER_ACCEPTED
    │             │              │──Feign直调: POST /internal/notifications─────────▶│
    │             │◀─返回成功────│                │               │
    │◀─展示成功───│              │                │               │
```

### 5.3.2 候选人拒绝Offer — 释放预冻结HC时序
```plain
候选人(C端)      前端           lingxi-hr        lingxi-job       lingxi-chat(通知)
    │             │              │                │               │
    │──点击"拒绝"─▶│              │                │               │
    │             │──POST /offers/{id}/reject────▶│                │
    │             │              │──SELECT offer FOR UPDATE       │
    │             │              │──校验status==SENT              │
    │             │              │                │               │
    │             │              │──UPDATE offer: status=REJECTED, rejected_at, reject_reason
    │             │              │──Feign: POST /internal/jobs/{jobId}/hc/release──▶│
    │             │              │                │──UPDATE reservation: RESERVED→RELEASED
    │             │              │◀─释放成功(幂等)─│               │
    │             │              │                │               │
    │             │              │──Feign: PUT /applications/{id}/status?status=OFFER_DECLINED
    │             │              │──Feign直调: POST /internal/notifications─────────▶│
    │             │◀─返回成功────│                │               │
    │◀─展示成功───│              │                │               │
```

### 5.3.3 面试评估录入 + 落选反馈生成时序
```plain
HR          前端           后端(lingxi-hr)     lingxi-resume     lingxi-chat(通知)
 │            │              │                    │                 │
 │──填写评估──▶│              │                    │                 │
 │            │──提交评估────▶│                    │                 │
 │            │              │──校验面试 IN_PROGRESS + 必填项        │
 │            │              │──INSERT评估(草稿/正式)│                │
 │            │              │──结论=REJECT → 模板落选反馈RejectFeedbackGenerator
 │            │              │──Feign更新投递状态────────────────▶│
 │            │              │  (PASS→OFFERABLE / PENDING→保持INTERVIEWING / REJECT→REJECTED)
 │            │              │──Feign直调: POST /internal/notifications─────────────▶│
 │            │◀─返回成功────│                    │                 │
 │◀─提示成功──│              │                    │                 │
```
> **注**：AI 生成反馈（录用建议/落选反馈 SSE）**已裁剪**（2026-08-08 用户确认不做）。`feedback` 字段本期留 null；REJECT 落选反馈用 Day 3 模板 `TemplateRejectFeedbackGenerator` 写入 `resume_application.reject_feedback`。

### 5.3.4 Offer过期处理时序
```plain
定时任务(5min)   后端(lingxi-hr)     lingxi-job       lingxi-chat(通知)
 │              │                    │                 │
 │──触发──▶│                    │                 │
 │              │──selectExpiredSent(): WHERE status='SENT' AND expires_at<=NOW()
 │              │  → 条件更新 status=EXPIRED（rows=1 才处理）│
 │              │──Feign释放HC─────────────────────────▶│
 │              │                    │  release(reason=EXPIRED，幂等)
 │              │──Feign: PUT /applications/{id}/status?status=OFFERABLE（回退，best-effort）
 │              │──Feign直调通知──────────────────────▶│
 │              │                    通知候选人(OFFER_RECEIVED) + HR(OFFER_MANAGE)
 │◀─任务完成────│                    │                 │
```

### 5.3.5 消息沟通时序（公共模块，D 不交付）
```plain
HR              前端           lingxi-chat(公共)  MySQL          WebSocket
 │               │              │                │               │
 │──进入消息页面─▶│              │                │               │
 │               │──GET /api/v1/chat/conversations────────────────▶│
 │               │              │──查询会话列表──▶│               │
 │               │              │◀─返回列表──────│               │
 │               │◀─会话列表────│                │               │
 │               │              │                │               │
 │──点击某个会话──▶│              │                │               │
 │               │──GET /conversations/{id}/messages────────────▶│
 │               │              │──查询消息列表──▶│               │
 │               │              │──标记已读──────────────▶│       │
 │               │              │◀─返回消息──────│               │
 │               │◀─消息列表────│                │               │
 │               │              │                │               │
 │──输入消息发送──▶│              │                │               │
 │               │──POST /conversations/{id}/messages───────────▶│
 │               │              │──INSERT消息────▶│               │
 │               │              │──更新会话最后消息──▶│           │
 │               │              │──推送新消息给对方────────────────▶│
 │               │◀─发送成功────│                │               │
```
> **说明（2026-08-08）**：消息沟通（会话/消息/已读未读）由公共模块/lingxi-chat 负责，表 `msg_conversation`/`msg_message` 归属 lingxi-user（成员A），**不在 lingxi-hr 交付范围**。会话表结构见 5.4 节参考。

## 5.4 数据库设计
> **实际表清单（D 共建 11 张）**：6 核心表（`hr_company`/`hr_company_member`/`hr_company_certification`/`hr_interview`/`hr_interview_evaluation`/`hr_offer`）+ 模拟面试 3 表（`mock_session`/`mock_answer`/`mock_report`，Day 6）。
> **通知说明（2026-08-08）**：通知实际**走 Feign 直调 lingxi-chat** 写入其 `sys_notification` 表（非本库 `msg_notification`）。`msg_notification`（DDL 在 schema.sql 公共段）/`user_notification_preference`（仅实体+Mapper，DDL 未落地）已建，但因通知方案变更（RocketMQ 裁剪）**当前未被通知主流程使用**。6 核心表 + mock 3 表 DDL 在 `docs/schema.sql`。
>
> `msg_conversation`/`msg_message`（消息沟通）归属公共模块/lingxi-user，`admin_operation_log` 归属成员E，均不在 D 建表范围；下列结构仅作接口设计参考。

### ER图
> ```mermaid
> erDiagram
>     hr_company ||--o{ hr_company_member : "拥有成员"
>     hr_company ||--o{ hr_company_certification : "提交认证"
>     hr_company ||--o{ hr_interview : "发起面试"
>     hr_company ||--o{ hr_offer : "发放Offer"
> 
>     hr_interview ||--|| hr_interview_evaluation : "对应评估"
>     hr_interview }o--|| lingxi_user : "关联候选人"
>     hr_interview }o--|| lingxi_job : "关联岗位"
>     hr_interview }o--|| hr_company_member : "关联面试官"
> 
>     hr_offer }o--|| lingxi_user : "发放给候选人"
>     hr_offer }o--|| lingxi_job : "关联岗位"
> 
>     mock_session ||--o{ mock_answer : "包含答题"
>     mock_session ||--|| mock_report : "生成报告"
>     mock_session }o--|| lingxi_user : "模拟候选人"
>     mock_session }o--|| lingxi_job : "模拟岗位"
> 
>     hr_company {
>         bigint id PK
>         string name
>         string short_name
>         text description
>         string industry
>         string scale
>         string logo_url
>         string address
>         string website
>         string invite_code UK
>         string business_license_url
>         string cert_status
>         string cert_reject_reason
>         string status
>         datetime created_at
>         datetime updated_at
>     }
> 
>     hr_company_member {
>         bigint id PK
>         bigint company_id FK
>         bigint user_id FK
>         string role
>         string department
>         string tech_direction
>         int interview_count
>         string status
>         datetime created_at
>         datetime updated_at
>     }
> 
>     hr_company_certification {
>         bigint id PK
>         bigint company_id FK
>         bigint applicant_id FK
>         string business_license_url
>         string cert_material_url
>         string status
>         string reject_reason
>         bigint reviewer_id
>         datetime reviewed_at
>         datetime created_at
>         datetime updated_at
>     }
> 
>     hr_interview {
>         bigint id PK
>         bigint company_id FK
>         bigint application_id FK
>         bigint interviewer_id FK
>         bigint candidate_id FK
>         bigint job_id FK
>         datetime scheduled_at
>         string method
>         string location
>         string remark
>         string candidate_note
>         string status
>         datetime created_at
>         datetime updated_at
>     }
> 
>     hr_interview_evaluation {
>         bigint id PK
>         bigint interview_id FK
>         string conclusion
>         int tech_score
>         int communication_score
>         int match_score
>         int potential_score
>         string comment
>         string feedback
>         tinyint is_draft
>         bigint evaluator_id
>         datetime created_at
>         datetime updated_at
>     }
> 
>     hr_offer {
>         bigint id PK
>         bigint company_id FK
>         bigint application_id FK
>         bigint candidate_id FK
>         bigint job_id FK
>         int salary
>         date entry_date
>         string level
>         string remark
>         string status
>         datetime expires_at
>         datetime accepted_at
>         datetime rejected_at
>         string reject_reason
>         tinyint urge_count
>         datetime last_urge_at
>         datetime last_sync_time
>         datetime created_at
>         datetime updated_at
>     }
> 
>     mock_session {
>         bigint id PK
>         string session_id UK
>         bigint candidate_id FK
>         bigint job_id FK
>         string job_title
>         tinyint total_questions
>         decimal overall_score
>         string status
>         datetime started_at
>         datetime completed_at
>     }
> 
>     mock_answer {
>         bigint id PK
>         string session_id FK
>         tinyint question_number
>         string question_content
>         string question_dimension
>         string question_type
>         text candidate_answer
>         tinyint is_skipped
>         decimal tech_accuracy_score
>         decimal expression_score
>         decimal knowledge_depth_score
>         decimal overall_score
>         string ai_comment
>         datetime answered_at
>         datetime created_at
>     }
> 
>     mock_report {
>         bigint id PK
>         string session_id FK
>         bigint candidate_id
>         decimal overall_score
>         string overall_level
>         decimal tech_accuracy_score
>         decimal expression_score
>         decimal knowledge_depth_score
>         decimal project_score
>         json highlights
>         json weaknesses
>         json improvement_plan
>         int total_duration_sec
>         tinyint answered_count
>         tinyint skipped_count
>         datetime created_at
>     }
> 
>     lingxi_user {
>         bigint id PK
>     }
> 
>     lingxi_job {
>         bigint id PK
>     }
> ```
>
> 

### UML 图
> ```mermaid
> classDiagram
>     class Company {
>         +Long id
>         +String name
>         +String shortName
>         +String industry
>         +String scale
>         +String inviteCode
>         +String certStatus
>         +List~CompanyMember~ members
>         +List~Certification~ certifications
>         +List~Interview~ interviews
>         +List~Offer~ offers
>     }
> 
>     class CompanyMember {
>         +Long id
>         +Long userId
>         +String role
>         +String department
>         +String techDirection
>         +Integer interviewCount
>         +String status
>     }
> 
>     class Certification {
>         +Long id
>         +Long applicantId
>         +String status
>         +String rejectReason
>         +Long reviewerId
>         +DateTime reviewedAt
>     }
> 
>     class Interview {
>         +Long id
>         +Long applicationId
>         +Long interviewerId
>         +Long candidateId
>         +Long jobId
>         +DateTime scheduledAt
>         +String method
>         +String status
>         +Evaluation evaluation
>     }
> 
>     class Evaluation {
>         +Long id
>         +String conclusion
>         +Integer techScore
>         +Integer communicationScore
>         +Integer matchScore
>         +Integer potentialScore
>         +String comment
>         +String feedback
>         +Boolean isDraft
>         +Long evaluatorId
>     }
> 
>     class Offer {
>         +Long id
>         +Long applicationId
>         +Long candidateId
>         +Long jobId
>         +Integer salary
>         +Date entryDate
>         +String level
>         +String status
>         +DateTime expiresAt
>         +Integer urgeCount
>         +DateTime lastUrgeAt
>     }
> 
>     class MockSession {
>         +String sessionId
>         +Long candidateId
>         +Long jobId
>         +String jobTitle
>         +Integer totalQuestions
>         +BigDecimal overallScore
>         +String status
>         +List~MockAnswer~ answers
>         +MockReport report
>     }
> 
>     class MockAnswer {
>         +Integer questionNumber
>         +String questionContent
>         +String questionType
>         +String candidateAnswer
>         +Boolean isSkipped
>         +BigDecimal techAccuracyScore
>         +BigDecimal expressionScore
>         +BigDecimal overallScore
>         +String aiComment
>     }
> 
>     class MockReport {
>         +BigDecimal overallScore
>         +String overallLevel
>         +BigDecimal techAccuracyScore
>         +BigDecimal expressionScore
>         +BigDecimal knowledgeDepthScore
>         +Json highlights
>         +Json weaknesses
>         +Json improvementPlan
>         +Integer totalDurationSec
>     }
> 
>     Company "1" --> "0..*" CompanyMember : contains
>     Company "1" --> "0..*" Certification : submits
>     Company "1" --> "0..*" Interview : conducts
>     Company "1" --> "0..*" Offer : issues
> 
>     Interview "1" --> "1..1" Evaluation : has
> 
>     MockSession "1" --> "0..*" MockAnswer : contains
>     MockSession "1" --> "1..1" MockReport : generates
> ```
>
> 

### 面试记录表 `hr_interview`
| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | :---: | --- | --- |
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | 面试记录ID |
| `company_id` | BIGINT UNSIGNED | **是** | - | 企业ID（数据隔离 + 高频查询） |
| `application_id` | BIGINT UNSIGNED | **是** | - | 投递记录ID（关联 lingxi_resume.job_application） |
| `interviewer_id` | BIGINT UNSIGNED | **是** | - | 面试官用户ID |
| `candidate_id` | BIGINT UNSIGNED | **是** | - | 候选人用户ID |
| `job_id` | BIGINT UNSIGNED | **是** | - | 岗位ID（关联 lingxi_job.job_post） |
| `scheduled_at` | DATETIME | **是** | - | 预约面试时间 |
| `method` | VARCHAR(16) | **是** | OFFLINE | 面试方式：OFFLINE=线下面试 ONLINE=视频面试 PHONE=电话面试 |
| `location` | VARCHAR(256) | 否 | - | 面试地点或视频链接 |
| `remark` | VARCHAR(500) | 否 | - | 面试备注（HR内部使用） |
| `candidate_note` | VARCHAR(500) | 否 | - | 给候选人的留言 |
| `status` | VARCHAR(20) | **是** | PENDING | 面试状态：PENDING=待安排 SCHEDULED=已安排 IN_PROGRESS=进行中 COMPLETED=已完成 CANCELLED=已取消 |
| `created_at` | DATETIME | **是** | NOW() | 创建时间 |
| `updated_at` | DATETIME | 否 | NOW() ON UPDATE | 更新时间 |


**索引**：

| 索引名 | 类型 | 字段 |
| --- | --- | --- |
| `PRIMARY` | 主键 | `id` |
| `idx_company_id` | 普通 | `company_id` |
| `idx_application_id` | 普通 | `application_id` |
| `idx_interviewer_id` | 普通 | `interviewer_id`, `status` |
| `idx_candidate_id` | 普通 | `candidate_id` |
| `idx_scheduled_at` | 普通 | `scheduled_at` |


---

### 面试评估表 `hr_interview_evaluation`
| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | :---: | --- | --- |
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | 主键ID |
| `interview_id` | BIGINT UNSIGNED | **是** | - | 面试记录ID |
| `conclusion` | VARCHAR(16) | **是** | - | 面试结论：PASS=通过 PENDING=待定 REJECT=淘汰 |
| `tech_score` | TINYINT | **是** | - | 技术能力评分(1-5) |
| `communication_score` | TINYINT | **是** | - | 沟通表达评分(1-5) |
| `match_score` | TINYINT | **是** | - | 岗位匹配评分(1-5) |
| `potential_score` | TINYINT | **是** | - | 发展潜力评分(1-5) |
| `comment` | VARCHAR(2000) | **是** | - | 面试评语（最少20字） |
| `feedback` | VARCHAR(2000) | 否 | - | AI 生成的反馈（录用建议或落选原因+提升建议，HR可修改） |
| `is_draft` | TINYINT | **是** | 0 | 是否草稿：0=正式提交 1=草稿 |
| `evaluator_id` | BIGINT UNSIGNED | **是** | - | 评估人用户ID |
| `created_at` | DATETIME | **是** | NOW() | 创建时间 |
| `updated_at` | DATETIME | 否 | NOW() ON UPDATE | 更新时间 |


**索引**：

| 索引名 | 类型 | 字段 |
| --- | --- | --- |
| `PRIMARY` | 主键 | `id` |
| `uk_interview_id` | 唯一 | `interview_id` |
| `idx_evaluator_id` | 普通 | `evaluator_id` |
| `idx_conclusion` | 普通 | `conclusion` |


---

### Offer 表 `hr_offer`
| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | :---: | --- | --- |
| `id` | BIGINT UNSIGNED | **是** | Snowflake 应用层生成 | Offer ID（非自增） |
| `company_id` | BIGINT UNSIGNED | **是** | - | 企业ID（数据隔离 + 统计查询） |
| `application_id` | BIGINT UNSIGNED | **是** | - | 投递记录ID（关联 lingxi_resume.job_application） |
| `candidate_id` | BIGINT UNSIGNED | **是** | - | 候选人用户ID |
| `job_id` | BIGINT UNSIGNED | **是** | - | 岗位ID（关联 lingxi_job.job_post） |
| `salary` | INT | **是** | - | 月薪（元） |
| `entry_date` | DATE | **是** | - | 预计入职日期 |
| `level` | VARCHAR(16) | 否 | - | 职级（如 P6、高级工程师） |
| `remark` | VARCHAR(500) | 否 | - | 备注 |
| `status` | VARCHAR(16) | **是** | SENT | Offer状态：SENT=待确认 ACCEPTED=已确认 REJECTED=已拒绝 EXPIRED=已过期 WITHDRAWN=已撤回 |
| `expires_at` | DATETIME | **是** | - | Offer有效期截止时间 |
| `accepted_at` | DATETIME | 否 | - | 确认时间 |
| `rejected_at` | DATETIME | 否 | - | 拒绝时间 |
| `reject_reason` | VARCHAR(500) | 否 | - | 拒绝原因（候选人填写，选填） |
| `urge_count` | TINYINT UNSIGNED | **是** | 0 | 催促次数（24h内最多2次） |
| `last_urge_at` | DATETIME | 否 | - | 最近一次催促时间 |
| `last_sync_time` | DATETIME | 否 | - | 最后一次HC补偿对账时间 |
| `created_at` | DATETIME | **是** | NOW() | 发起时间 |
| `updated_at` | DATETIME | 否 | NOW() ON UPDATE | 更新时间 |


**索引**：

| 索引名 | 类型 | 字段 |
| --- | --- | --- |
| `PRIMARY` | 主键 | `id` |
| `idx_application_id` | 普通 | `application_id` |
| `idx_company_id` | 普通 | `company_id` |
| `idx_candidate_id` | 普通 | `candidate_id` |
| `idx_job_id` | 普通 | `job_id` |
| `idx_status_expires` | 普通 | `status`, `expires_at` |
| `idx_status_sync` | 普通 | `status`, `last_sync_time` |

> **2026-08-07 变更**：已删除 `uk_application_id` 唯一索引（`ALTER TABLE hr_offer DROP INDEX uk_application_id; ADD INDEX idx_application_id`），允许 `WITHDRAWN`/`EXPIRED` 后对同一投递再次发起 Offer；`SENT`/`ACCEPTED`/`REJECTED` 仍由应用层校验拦 `4205`。Offer ID 由 Snowflake 应用层生成（约 4~5×10¹⁸，JSON 序列化为字符串，`@JsonSerialize(ToStringSerializer)`）。发起默认有效期 `expiresInDays=3` 天。

### 企业成员表 `hr_company_member`
| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | :---: | --- | --- |
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | 主键ID |
| `company_id` | BIGINT UNSIGNED | **是** | - | 企业ID |
| `user_id` | BIGINT UNSIGNED | **是** | - | 用户ID（关联 lingxi_user.user） |
| `role` | VARCHAR(16) | **是** | INTERVIEWER | 角色：HR_ADMIN=HR管理员 INTERVIEWER=面试官 |
| `department` | VARCHAR(64) | **是** | - | 所属部门 |
| `tech_direction` | VARCHAR(32) | 否 | - | 技术方向：前端/后端/全栈/产品/设计/数据/其他（面试官必填） |
| `interview_count` | INT UNSIGNED | **是** | 0 | 累计面试场次 |
| `status` | VARCHAR(16) | **是** | ACTIVE | 状态：ACTIVE=正常 DISABLED=已禁用 |
| `created_at` | DATETIME | **是** | NOW() | 加入时间 |
| `updated_at` | DATETIME | 否 | NOW() ON UPDATE | 更新时间 |


**索引**：

| 索引名 | 类型 | 字段 |
| --- | --- | --- |
| `PRIMARY` | 主键 | `id` |
| `uk_company_user` | 唯一 | `company_id`, `user_id` |
| `idx_user_id` | 普通 | `user_id` |
| `idx_role` | 普通 | `company_id`, `role` |


---

### 企业信息表 `hr_company`
| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | :---: | --- | --- |
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | 企业ID |
| `name` | VARCHAR(128) | **是** | - | 企业全称（认证后不可修改） |
| `short_name` | VARCHAR(64) | 否 | - | 企业简称（展示用） |
| `description` | TEXT | 否 | - | 企业简介（富文本，最多5000字） |
| `industry` | VARCHAR(32) | **是** | - | 所属行业：互联网/金融/电商/教育/游戏/医疗/企业服务/其他 |
| `scale` | VARCHAR(20) | **是** | - | 企业规模：0-50/50-100/100-500/500-2000/2000+ |
| `logo_url` | VARCHAR(512) | 否 | - | 企业Logo URL |
| `address` | VARCHAR(256) | 否 | - | 详细办公地址 |
| `website` | VARCHAR(256) | 否 | - | 企业官网 |
| `invite_code` | VARCHAR(6) | **是** | - | 企业邀请码（6位数字+字母，HR加入企业时使用） |
| `business_license_url` | VARCHAR(512) | 否 | - | 营业执照URL |
| `cert_status` | VARCHAR(20) | **是** | PENDING | 认证状态：PENDING=待审核 APPROVED=已通过 REJECTED=已拒绝 |
| `cert_reject_reason` | VARCHAR(500) | 否 | - | 认证拒绝原因 |
| `status` | VARCHAR(16) | **是** | ACTIVE | 企业状态：ACTIVE=正常 DISABLED=已禁用 |
| `created_at` | DATETIME | **是** | NOW() | 创建时间 |
| `updated_at` | DATETIME | 否 | NOW() ON UPDATE | 更新时间 |


**索引**：

| 索引名 | 类型 | 字段 |
| --- | --- | --- |
| `PRIMARY` | 主键 | `id` |
| `uk_name` | 唯一 | `name` |
| `uk_invite_code` | 唯一 | `invite_code` |
| `idx_cert_status` | 普通 | `cert_status` |
| `idx_status` | 普通 | `status` |


---

### 企业认证申请表 `hr_company_certification`
| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | :---: | --- | --- |
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | 申请ID |
| `company_id` | BIGINT UNSIGNED | **是** | - | 企业ID（关联 hr_company 表） |
| `applicant_id` | BIGINT UNSIGNED | **是** | - | 申请人用户ID（关联 lingxi_user.user） |
| `business_license_url` | VARCHAR(512) | 否 | - | 营业执照URL（MinIO/OSS） |
| `cert_material_url` | VARCHAR(512) | 否 | - | 其他证明材料URL |
| `status` | VARCHAR(20) | **是** | PENDING | 审核状态：PENDING=待审核 APPROVED=已通过 REJECTED=已拒绝 |
| `reject_reason` | VARCHAR(500) | 否 | - | 拒绝原因（审核拒绝时填写） |
| `reviewer_id` | BIGINT UNSIGNED | 否 | - | 审核人管理员ID（关联 lingxi_admin.admin_user） |
| `reviewed_at` | DATETIME | 否 | - | 审核时间 |
| `created_at` | DATETIME | **是** | NOW() | 申请时间 |
| `updated_at` | DATETIME | 否 | NOW() ON UPDATE | 更新时间 |


**索引**：

| 索引名 | 类型 | 字段 |
| --- | --- | --- |
| `PRIMARY` | 主键 | `id` |
| `idx_company_id` | 普通 | `company_id` |
| `idx_applicant_id` | 普通 | `applicant_id` |
| `idx_status` | 普通 | `status` |


---

### 模拟面试会话表 `mock_session`
| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | :---: | --- | --- |
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | 主键ID |
| `session_id` | VARCHAR(32) | **是** | - | 会话唯一标识（如 mock-20260729-001） |
| `candidate_id` | BIGINT UNSIGNED | **是** | - | 求职者用户ID |
| `job_id` | BIGINT UNSIGNED | 否 | - | 目标岗位ID（通过 Feign 获取 lingxi_job.job_post 信息） |
| `job_title` | VARCHAR(128) | 否 | - | 目标岗位名称（冗余，便于历史查询；岗位可能被下线） |
| `total_questions` | TINYINT | **是** | 5 | 总题数：5/8/10 |
| `overall_score` | DECIMAL(5,2) | 否 | - | 综合评分(0-100)，完成后计算 |
| `status` | VARCHAR(20) | **是** | IN_PROGRESS | 状态：IN_PROGRESS=进行中 COMPLETED=已完成 |
| `started_at` | DATETIME | **是** | NOW() | 开始时间 |
| `completed_at` | DATETIME | 否 | - | 完成时间 |


**索引**：

| 索引名 | 类型 | 字段 |
| --- | --- | --- |
| `PRIMARY` | 主键 | `id` |
| `uk_session_id` | 唯一 | `session_id` |
| `idx_candidate_id` | 普通 | `candidate_id`, `status` |


---

### 模拟面试答题记录表 `mock_answer`
| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | :---: | --- | --- |
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | 主键ID |
| `session_id` | VARCHAR(32) | **是** | - | 会话ID |
| `question_number` | TINYINT | **是** | - | 题号（从1开始） |
| `question_content` | VARCHAR(2000) | **是** | - | 题目内容 |
| `question_dimension` | VARCHAR(64) | 否 | - | 考察维度 |
| `question_type` | VARCHAR(20) | 否 | - | 题目类型：BASIC/PROJECT/BOUNDARY/COMPREHENSIVE |
| `candidate_answer` | TEXT | 否 | - | 求职者作答内容（跳过时为空） |
| `is_skipped` | TINYINT | **是** | 0 | 是否跳过：0=正常作答 1=跳过 |
| `tech_accuracy_score` | DECIMAL(5,2) | 否 | - | 技术准确度评分(0-100) |
| `expression_score` | DECIMAL(5,2) | 否 | - | 表达逻辑评分(0-100) |
| `knowledge_depth_score` | DECIMAL(5,2) | 否 | - | 知识深度评分(0-100) |
| `overall_score` | DECIMAL(5,2) | 否 | - | 本题综合评分(0-100) |
| `ai_comment` | VARCHAR(500) | 否 | - | AI 点评 |
| `answered_at` | DATETIME | 否 | - | 作答时间 |
| `created_at` | DATETIME | **是** | NOW() | 创建时间 |


**索引**：

| 索引名 | 类型 | 字段 |
| --- | --- | --- |
| `PRIMARY` | 主键 | `id` |
| `idx_session_id` | 普通 | `session_id`, `question_number` |


---

### 模拟面试报告表 `mock_report`
| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | :---: | --- | --- |
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | 主键ID |
| `session_id` | VARCHAR(32) | **是** | - | 会话ID |
| `candidate_id` | BIGINT UNSIGNED | **是** | - | 求职者用户ID |
| `overall_score` | DECIMAL(5,2) | **是** | - | 综合评分(0-100) |
| `overall_level` | VARCHAR(16) | **是** | - | 综合等级：EXCELLENT/GOOD/AVERAGE/NEED_IMPROVE |
| `tech_accuracy_score` | DECIMAL(5,2) | 否 | - | 技术准确度评分 |
| `expression_score` | DECIMAL(5,2) | 否 | - | 表达逻辑评分 |
| `knowledge_depth_score` | DECIMAL(5,2) | 否 | - | 知识深度评分 |
| `project_score` | DECIMAL(5,2) | 否 | - | 项目经验评分 |
| `highlights` | JSON | 否 | - | 亮点列表 |
| `weaknesses` | JSON | 否 | - | 短板列表 |
| `improvement_plan` | JSON | 否 | - | 提升方案 |
| `total_duration_sec` | INT UNSIGNED | 否 | - | 总用时（秒） |
| `answered_count` | TINYINT | 否 | - | 已答题数 |
| `skipped_count` | TINYINT | 否 | - | 跳过题数 |
| `created_at` | DATETIME | **是** | NOW() | 创建时间 |


**索引**：

| 索引名 | 类型 | 字段 |
| --- | --- | --- |
| `PRIMARY` | 主键 | `id` |
| `uk_session_id` | 唯一 | `session_id` |
| `idx_candidate_id` | 普通 | `candidate_id` |


---

## 5.5 API设计
### 5.5.1 错误码表

| code | HTTP | 说明 |
|------|:---:|------|
| 4001 | 409 | 企业名称已存在（含"您已创建企业"） |
| 4002 | 409 | 该成员已加入企业 |
| 4003 | 409 | 已有待审核的认证申请，请勿重复提交 |
| 4004 | 400 | 营业执照文件格式或大小不符合要求 |
| 4005 | 403 | 该账号已被禁用 |
| 4006 | 404 | 邀请码不存在或格式不正确（含企业已禁用） |
| 4007 | 404 | 模拟面试会话不存在 |
| 4008 | 409 | 您已是该企业成员，无需重复加入 |
| 4009 | 409 | 该企业尚未通过认证，无法加入 |
| 4010 | 404 | 成员不存在 |
| 4011 | 403 | 无权限操作该成员（非本企业 / 非 HR_ADMIN / 面试官操作他人面试） |
| 4012 | 409 | 不能移除企业管理员 |
| 40014 | 409 | 面试已完成（Mock Interview） |
| 40015 | 400 | 暂无答题记录，无法生成报告 |
| 40016 | 400 | 简历不存在或无权限（Mock Interview 简历获取失败） |
| 40017 | 500 | 出题服务繁忙，请稍后重试 |
| 40018 | 429 | 今日模拟面试次数已达上限 |
| 4100 | 404 | 面试记录不存在 |
| 4101 | 409 | 面试状态不允许此操作 |
| 4102 | 409 | 面试时间冲突（同面试官 60min 内重叠） |
| 4103 | 400 | 评估表单校验失败（评语少于20字） |
| 4104 | 409 | 该面试已评估，请勿重复提交（仅正式评估 is_draft=0） |
| 4200 | 404 | Offer不存在 |
| 4201 | 409 | Offer状态不允许此操作 |
| 4202 | 410 | Offer已过期 |
| 4203 | 409 | HC不足，无法发起Offer / confirm 失败"名额不足，请联系HR" |
| 4204 | 409 | 催促频率限制（24h内最多2次） |
| 4205 | 409 | 已有待确认Offer（SENT/ACCEPTED/REJECTED 时拦截重复发起） |
| 4300 | 404 | 候选人不存在 |
| 4301 | 403 | 无权查看该候选人（跨企业） |
| 4302 | 409 | 该候选人已处理，请勿重复操作 |
| 4303 | 404 | 候选人简历不存在 |

> **错误码段说明**：4001-4012 企业认证/成员管理；4100-4104 面试协同；4200-4205 Offer；4300-4303 候选人；4007/40014-40018 Mock Interview（4007 为排期预留空号）。


### 5.5.2 候选人管理接口
#### 1. 候选人列表
**GET** `/api/v1/hr/candidates/list`

**请求参数**：

| **参数** | **类型** | **必填** | **描述** |
| --- | --- | :---: | --- |
| jobId | Long | 否 | 按岗位筛选 |
| status | String | 否 | 按投递状态筛选：SUBMITTED/VIEWED/SCREENED/INTERVIEWING |
| minMatchScore | Integer | 否 | 最低匹配度筛选（默认0） |
| keyword | String | 否 | 搜索关键词（候选人姓名/技能标签，C 侧过滤） |
| sortBy | String | 否 | 排序方式：matchScore(默认)/submittedAt/aiScore（C 侧排序） |
| page | Integer | 否 | 页码，默认1 |
| size | Integer | 否 | 每页条数，默认20（上限100） |

**响应**：`Result<PageResult<CandidateVO>>`，字段：

```
id(=applicationId) candidateId candidateName phone avatar jobId jobTitle status matchScore aiScore appliedAt
hasOfferRecord lastOfferStatus
```
- 数据链路：投递分页来自 C（`GET /internal/applications/list`，契约已升级为 PageResult + 透传筛选/排序）；`candidateName`/`phone`/`avatar` 来自 A（`batch?ids=` 优先 → 单查降级 → 兜底 name="用户"+userId、avatar=null）
- `hasOfferRecord`（2026-08-07 前端 P0）：该投递**是否已存在任意 Offer 记录**（含 WITHDRAWN/EXPIRED/REJECTED 等终态），前端据此禁用「发起 Offer」按钮
- `lastOfferStatus`：该投递最新一条 Offer 的状态（SENT/ACCEPTED/REJECTED/EXPIRED/WITHDRAWN，无 Offer 为 null）

```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 3001,
        "candidateId": 123,
        "candidateName": "李婷",
        "phone": "138****8888",
        "jobId": 101,
        "jobTitle": "高级前端工程师",
        "status": "SCREENED",
        "matchScore": 92.0,
        "aiScore": 85,
        "appliedAt": "2026-07-29T16:00:00",
        "hasOfferRecord": false,
        "lastOfferStatus": null
      }
    ],
    "total": 50,
    "page": 1,
    "pageSize": 20
  }
}
```

---

#### 2. Top5高潜推荐
**GET** `/api/v1/hr/candidates/top5`

**请求参数**：

| **参数** | **类型** | **必填** | **描述** |
| --- | --- | :---: | --- |
| jobId | Long | 否 | 按岗位筛选，不传则返回本企业所有岗位的Top5 |

**响应**：`Result<List<TopCandidateVO>>`，字段：

```
rank candidateId applicationId candidateName matchScore aiScore advantages risks
```
- 取数：C 按 `matchScore DESC, aiScore DESC` 返回前 5，不足 5 人返回实际数量
- `applicationId` 透传投递ID：前端 Top5 卡片「查看简历」直接调 `GET /candidates/{applicationId}/resume`
- `advantages`/`risks` 来自 C 的 `ApplicationDTO`（Job Agent/AI 生成，未就绪前为 null，不阻塞）

```json
{
  "code": 200,
  "data": [
    {
      "rank": 1,
      "candidateId": 123,
      "applicationId": 5001,
      "candidateName": "李婷",
      "matchScore": 92.0,
      "aiScore": 85,
      "advantages": ["React项目经验3年实战", "TypeScript超出岗位基本要求"],
      "risks": ["架构设计经验偏少"]
    }
  ]
}
```

---

#### 3. 标记候选人合适/不合适
**PUT** `/api/v1/hr/candidates/{applicationId}/mark`（**HR_ADMIN**）

**请求参数：**

| 参数 | 类型 | 必填 | 描述 |
| --- | --- | :---: | --- |
| applicationId | Long | Y | 投递记录ID（路径参数） |
| action | String | Y | 操作：SUITABLE / UNSUITABLE（请求体，`@Pattern` 校验） |

**响应：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "applicationId": 5001,
    "newStatus": "SCREENED",
    "newStatusDesc": "筛选通过",
    "notificationSent": true
  }
}
```

**业务逻辑：**

+ 标记"合适"（SUITABLE）：投递 `SUBMITTED/VIEWED→SCREENED`（C 状态机直转），Feign 直调 lingxi-chat 通知求职者"通过简历筛选"（type=`RESUME_VIEWED`，best-effort）
+ 标记"不合适"（UNSUITABLE）：投递 `→REJECTED`，`RejectFeedbackGenerator` 生成落选反馈（原因+2-3条提升建议，当前为模板实现，TODO 接真 LLM）JSON 写入 `resume_application.reject_feedback`，Feign 直调 lingxi-chat 通知求职者
+ 重复标记拦截：仅 `SUBMITTED/VIEWED` 可标记；已 `SCREENED/REJECTED` 或进入 `INTERVIEWING/OFFERABLE/OFFERED/WITHDRAWN` → **4302**「该候选人已处理，请勿重复操作」
+ 跨企业校验：投递 `companyId != 当前企业` → **4301**；投递不存在 → **4300**
+ 通知失败 best-effort：`notificationSent=false`，不阻塞标记

**错误码：** 4300, 4301, 4302

---

#### 4. 查看候选人简历（2026-08-06 追加，需求单 v1.2）
**GET** `/api/v1/hr/candidates/{applicationId}/resume`

| 参数 | 类型 | 必填 | 描述 |
| --- | --- | :---: | --- |
| applicationId | Long | 是 | 投递记录 ID（= 前端候选人列表 `record.id`） |

**响应**：`Result<ResumeDetailDTO>`，字段对齐 lingxi-resume `ResumeDetailVO`（全量 15 字段）：

```
id fileName fileFormat fileSize isDefault parseStatus resumeMdUrl
facePhotoUrl cardStructure candidateName phone email wechat createdAt updatedAt
```
- `cardStructure`：JSON 对象（DB 存 JSON 字符串，C 已反序列化返回，D 透传）；前端渲染二选一：`resumeMdUrl`（Markdown 预览）或 `cardStructure`（卡片渲染）

**业务逻辑：** 校验本企业 ACTIVE 成员（4011）→ `fetchApplication` 拿 `resumeId`（不存在/跨企业 4300/4301）→ Feign 调 C **`GET /internal/resumes/{id}/detail`**（全量、免归属校验）→ 透传返回。`resumeId` 为空或 C 返回 `RESUME_NOT_FOUND` → **4303**「候选人简历不存在」；C 网络异常 → 500（不降级为空）。

**错误码：** 4011, 4300, 4301, 4303

### 5.5.3 面试协同接口
#### 1. 创建面试安排
**POST** `/api/v1/hr/interviews`（本企业 ACTIVE 成员）

**请求参数：**

| 参数 | 类型 | 必填 | 描述 |
| --- | --- | :---: | --- |
| applicationId | Long | Y | 投递记录ID（须为 SCREENED） |
| interviewerId | Long | Y | 面试官用户ID（须本企业 ACTIVE 成员） |
| scheduledAt | DateTime | Y | 预约面试时间（ISO 8601） |
| method | String | Y | 面试方式：OFFLINE/ONLINE/PHONE |
| location | String | N | 面试地点/视频链接 |
| remark | String | N | 面试备注（HR内部使用） |
| candidateNote | String | N | 给候选人的留言 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "interviewId": 6001,
    "status": "PENDING",
    "scheduledAt": "2026-07-30T14:00:00"
  }
}
```

**业务逻辑：**

+ 校验投递 `status==SCREENED`（C 状态机 SCREENED→INTERVIEWING，否则透传 C 错误码）
+ 校验面试官本企业成员且 `status=ACTIVE`（4010/4005）
+ **时间冲突校验**：同面试官 `scheduled_at` 前后 60 分钟重叠（status 非终态）→ **4102**
+ 插入 `hr_interview(status=PENDING)` → Feign 更新投递 `SCREENED→INTERVIEWING` → 双端通知（候选人 `INTERVIEW_INVITE` + 面试官 `INTERVIEW_SCHEDULE`，Feign 直调 lingxi-chat，best-effort）

---

#### 2. 面试列表
**GET** `/api/v1/hr/interviews`（本企业 ACTIVE 成员）

**请求参数：**

| 参数 | 类型 | 必填 | 描述 |
| --- | --- | :---: | --- |
| status | String | N | 按状态筛选：PENDING/SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED |
| dateRange | String | N | 按时间筛选：TODAY/WEEK/MONTH（**按 scheduled_at** 过滤，默认全部） |
| jobId | Long | N | 按岗位筛选 |
| interviewerId | Long | N | 按面试官筛选（面试官端「我的面试」传当前用户 id，2026-08-06 新增） |
| method | String | N | 按面试方式筛选 OFFLINE/ONLINE/PHONE（2026-08-06 新增） |
| keyword | String | N | 候选人姓名搜索（子查询 sys_user.name LIKE，2026-08-06 新增） |
| page | Integer | N | 页码，默认1 |
| size | Integer | N | 每页条数，默认20（上限100） |

**响应**：`Result<PageResult<InterviewVO>>`，字段：

```
interviewId applicationId candidateName candidateAvatar jobTitle interviewerName
scheduledAt method methodDesc location remark status statusDesc hasQuestions hasEvaluation
```
- 拼装：候选人/面试官姓名头像 → `fetchUsers`（batch→单查降级→兜底）；jobTitle → 循环 `getApplication` 取投递 jobTitle
- `hasEvaluation`：`selectByInterviewIds` 批量查评估
- `hasQuestions`：恒 false（AI 出题在岗位侧 Interview Agent / Mock Interview）

```json
{
  "code": 200,
  "data": {
    "total": 8,
    "records": [
      {
        "interviewId": 6001,
        "applicationId": 5001,
        "candidateName": "李芳",
        "candidateAvatar": "...",
        "jobTitle": "高级前端工程师",
        "interviewerName": "王工",
        "scheduledAt": "2026-07-30T10:00:00",
        "method": "OFFLINE",
        "methodDesc": "线下面试",
        "location": "公司3楼会议室",
        "status": "SCHEDULED",
        "statusDesc": "已安排",
        "hasQuestions": false,
        "hasEvaluation": false
      }
    ]
  }
}
```

---

#### 3. 开始面试
**POST** `/api/v1/hr/interviews/{id}/start`（HR_ADMIN 或本面试面试官）

- 校验存在 + 本企业（4100）、状态 ∈ {PENDING, SCHEDULED}（4101）
- 乐观锁更新 `status=PENDING/SCHEDULED→IN_PROGRESS`（rows=0 → 4101）
- **响应：** `{"code": 200, "data": null}`
- **权限（2026-08-06 收紧）**：HR_ADMIN 可操作本企业全部面试；INTERVIEWER 仅 `interviewer_id==本人`，否则 4011

---

#### 4. 取消面试
**POST** `/api/v1/hr/interviews/{id}/cancel`（HR_ADMIN 或本面试面试官）

- 校验存在 + 本企业（4100）、非终态（COMPLETED 不可取消 → 4101）
- 更新 `status=CANCELLED` → 投递状态**回退 `INTERVIEWING→SCREENED`**（Feign 更新投递，候选人可被再次安排；C 白名单已扩展，`fe150947`）
- **响应：** `{"code": 200, "data": null}`

---

#### 5. 待评估列表
**GET** `/api/v1/hr/interviews/pending-evaluations`（本企业 ACTIVE 成员）

**响应**：`Result<List<PendingEvaluationVO>>`：

```
interviewId candidateName jobTitle scheduledAt isOverdue
```
- 查面试 `status=IN_PROGRESS` 且无**正式**评估（`LEFT JOIN hr_interview_evaluation ev ON ev.interview_id=id AND ev.is_draft=0`，`ev.id IS NULL`）；存了草稿（is_draft=1）仍算待评估
- `isOverdue`：`scheduledAt` 距今 >24h（面试已开始但预约时间过去 24h 仍未正式评估 → 逾期；`completed_at` 字段已删）

```json
{
  "code": 200,
  "data": [
    {
      "interviewId": 5001,
      "candidateName": "李婷",
      "jobTitle": "高级前端工程师",
      "scheduledAt": "2026-07-30T14:00:00",
      "isOverdue": false
    }
  ]
}
```

---

#### 6. 录入面试评估
**PUT** `/api/v1/hr/interviews/{interviewId}/evaluation`（HR_ADMIN 或本面试面试官）

**请求参数：**

| 参数 | 类型 | 必填* | 描述 |
| --- | --- | :---: | --- |
| interviewId | Long | Y | 面试记录ID（路径参数） |
| conclusion | String | Y | 面试结论：PASS / PENDING / REJECT |
| techScore | Integer | Y | 技术能力评分（1-5） |
| communicationScore | Integer | Y | 沟通表达评分（1-5） |
| matchScore | Integer | Y | 岗位匹配评分（1-5） |
| potentialScore | Integer | Y | 发展潜力评分（1-5） |
| comment | String | Y | 面试评语（正式提交 ≥20 字） |
| isDraft | Boolean | N | 是否保存为草稿（默认false=正式提交；草稿跳过必填/评语校验） |

**响应：**

```json
{
  "code": 200,
  "data": {
    "evaluationId": 7001,
    "conclusion": "PASS",
    "feedback": null,
    "applicationStatus": "OFFERABLE",
    "notificationSent": true
  }
}
```

**业务逻辑：**

+ 校验面试存在 + 本企业（4100）、状态 `IN_PROGRESS`（4101，仅进行中可评估）
+ **草稿可覆盖转正式（2026-08-06 修正）**：已有 `is_draft=1` 草稿 → 本次入参 UPDATE 覆盖；已有正式评估（`is_draft=0`）→ **4104** 禁止重复
+ `isDraft=true` 跳过必填校验，仅存草稿不触发流转；`uk_interview_id` 唯一约束兜底并发（DuplicateKeyException → 4104）
+ 正式提交流转：
    - **PASS** → 面试 COMPLETED + 投递 `OFFERABLE` + 通知候选人「面试通过，进入 Offer 环节」
    - **PENDING** → 面试 COMPLETED + 投递保持 INTERVIEWING + 通知 HR 安排复面
    - **REJECT** → 面试 COMPLETED + 投递 `REJECTED` + `TemplateRejectFeedbackGenerator` 落选反馈写入 `reject_feedback` + 通知候选人
+ **无独立「完成面试」接口（2026-08-06 确认）**：评估正式提交一次性置 COMPLETED，前端已删「完成面试」按钮
+ `feedback` 字段本期留 null（**AI 反馈 SSE 已裁剪，2026-08-08 用户确认不做**）

**错误码：** 4100, 4101, 4103, 4104

---

#### 7. 查看面试评估（2026-08-06 追加，前端「查看评估」）
**GET** `/api/v1/hr/interviews/{interviewId}/evaluation`（HR_ADMIN 或本面试面试官）

**响应**：`Result<EvaluationDetailVO>`：

```
interviewId conclusion techScore communicationScore matchScore potentialScore comment feedback isDraft evaluatorName createdAt
```
- 校验面试存在 + 本企业（4100）+ 操作权限（4011）
- 无评估记录 → 404（前端用列表 `hasEvaluation=false` 判断不发请求）

---

#### ~~8. AI反馈预览（SSE）~~ 已裁剪
**~~GET~~** **~~`/api/v1/hr/interviews/{id}/feedback`~~** —— **2026-08-08 用户确认不做**。评估 `feedback` 字段留 null；REJECT 落选反馈沿用 Day 3 模板 `RejectFeedbackGenerator`（写入 `reject_feedback` 列）。

### 5.5.4 Offer管理接口
> **权限**：发起/列表/HC概览/催促/撤回 5 个接口仅 **HR_ADMIN**（面试官 Offer 不可见，INTERVIEWER 访问 4011）；详情/接受/拒绝 3 个为**候选人身份接口**（`userId==offer.candidateId`，不校验企业）。

#### 1. 发起Offer
**POST** `/api/v1/hr/offers`（**HR_ADMIN**）

**请求参数：**

| 参数 | 类型 | 必填 | 描述 |
| --- | --- | :---: | --- |
| applicationId | Long | Y | 投递记录ID（须为 OFFERABLE） |
| salary | Integer | Y | 月薪（元，>0） |
| entryDate | LocalDate | Y | 预计入职日期（必须在未来） |
| level | String | N | 职级，如P6、高级工程师 |
| remark | String | N | 备注 |
| expiresInDays | Integer | N | 有效期天数，默认3天（用户确认 2026-08-07，系分原7） |

**响应：**

```json
{
  "code": 200,
  "data": {
    "offerId": "8001...",
    "status": "SENT",
    "expiresAt": "2026-08-06T14:30:00",
    "notificationSent": true,
    "salaryWarning": { "warn": false }
  }
}
```

**业务逻辑：**

+ 校验投递 `status==OFFERABLE`（4201/C 侧 3006 透传）；`entryDate>today`、`salary>0`、`expiresInDays∈[1,30]`
+ **重复发起校验（2026-08-07 已删唯一索引）**：取该投递**最新一条** Offer，仅 `SENT`（活跃）/`ACCEPTED`/`REJECTED`（终态）→ **4205**；`WITHDRAWN`/`EXPIRED` 旧记录**放行可重发**（并发双发由应用层校验兜底）
+ 生成 `offerId = SnowflakeIdUtil.nextId()`（Long，JSON 序列化为**字符串** `@JsonSerialize(ToStringSerializer)`）
+ **事务内** Feign `reserveHc`（B 返回 2201 无可用HC → 转 4203；其余失败透传）→ 插入 hr_offer（status=SENT，expiresAt=NOW()+days）→ 投递联动 `OFFERABLE→OFFERED`；任一步异常 → **补偿 `release(D_PERSIST_FAILED)`** 后整体回滚
+ **薪资软提示**：`getJobForValidation` 取 `salaryMinAmount/salaryMaxAmount/salaryNegotiable`；`salaryNegotiable=false` 且超范围 → 响应 `salaryWarning.warn=true`（不拦截）
+ 通知候选人（`OFFER_RECEIVED`，Feign 直调 lingxi-chat，best-effort）

**错误码：** 4203(HC不足), 4205(已有待确认Offer), 4201(状态不可操作)

---

#### 2. Offer列表
**GET** `/api/v1/hr/offers`（**HR_ADMIN**）

**请求参数：**

| 参数 | 类型 | 必填 | 描述 |
| --- | --- | :---: | --- |
| status | String | N | 按状态筛选：SENT/ACCEPTED/REJECTED/EXPIRED/WITHDRAWN |
| dateRange | String | N | 按时间筛选：TODAY/WEEK/MONTH（**按 createdAt** 过滤，2026-08-07 前端 P0 补） |
| jobId | Long | N | 按岗位筛选 |
| page | Integer | N | 页码，默认1 |
| size | Integer | N | 每页条数，默认20（上限100） |

**响应**：`Result<PageResult<OfferVO>>`，字段：

```
offerId(字符串) applicationId candidateId candidateName jobId jobTitle salary entryDate level
status statusDesc(枚举desc) expiresAt urgeCount rejectReason acceptedAt rejectedAt createdAt
```
- 拼装：candidateName → `fetchUsers`（batch→单查→兜底"用户"+id）；jobTitle → `fetchApplication` 或降级"岗位"+jobId

```json
{
  "code": 200,
  "data": {
    "total": 5,
    "records": [
      {
        "offerId": "8001...",
        "applicationId": 5001,
        "candidateName": "李芳",
        "jobTitle": "高级前端工程师",
        "salary": 35000,
        "entryDate": "2026-08-15",
        "level": "P6",
        "status": "SENT",
        "statusDesc": "待确认",
        "expiresAt": "2026-08-06T14:30:00",
        "urgeCount": 0,
        "rejectReason": null,
        "acceptedAt": null,
        "createdAt": "2026-07-30T14:30:00"
      }
    ]
  }
}
```

---

#### 3. HC概览
**GET** `/api/v1/hr/offers/hc-overview`（**HR_ADMIN**，**jobId 可空**）

| 参数 | 类型 | 必填 | 描述 |
| --- | --- | :---: | --- |
| jobId | Long | N | 传 → 单岗概览；**不传 → 公司级聚合**（listCompanyJobs 取全部岗位求和，jobTitle="全公司"） |

**响应：**

```json
{
  "code": 200,
  "data": {
    "jobId": 101,
    "jobTitle": "高级前端工程师",
    "totalHc": 5,
    "reservedHc": 1,
    "confirmedHc": 2,
    "availableHc": 2
  }
}
```
- 传 jobId → Feign `getJobForValidation(jobId)` 单岗映射；本企业无岗位 → 返回全 0；聚合期间单个岗位查询失败 → 记日志跳过（best-effort）

---

#### 4. 催促确认
**POST** `/api/v1/hr/offers/{offerId}/urge`（**HR_ADMIN**）

| **参数** | **类型** | **必填** | **描述** |
| --- | --- | :---: | --- |
| offerId | Long | 是 | Offer ID（路径参数） |

**响应：** `{"code": 200, "data": null}`

**业务逻辑：** 校验 Offer 存在 + 本企业（4200）、`status==SENT`（4201）；`lastUrgeAt` 距今 <24h 且 `urgeCount>=2` → **4204**；否则 `urgeCount+1`、`lastUrgeAt=NOW()`；通知候选人（best-effort）

**错误码：** 4200, 4201, 4204

---

#### 5. 撤回Offer
**POST** `/api/v1/hr/offers/{id}/retract`（**HR_ADMIN**）

**响应：** `{"code": 200, "data": null}`

**业务逻辑：** 校验存在 + 本企业（4200）、`status==SENT`（4201）→ 条件更新 `SENT→WITHDRAWN`（乐观锁 rows=0 → 4201）→ Feign `releaseHc(reason=REJECTED)`（幂等，失败记日志不阻塞）→ 投递回退 `OFFERED→OFFERABLE`（best-effort）→ 通知候选人「Offer 已撤回」。**撤回后同投递可再次发起。**

---

#### 6. 候选人查看 Offer 详情（2026-08-07 跨端方案A 新增）
**GET** `/api/v1/hr/offers/{offerId}`（**候选人身份：`userId==offer.candidateId`**）

**响应**：`Result<OfferDetailVO>`：

```
offerId(字符串) applicationId jobId jobTitle companyName salary entryDate level remark
status statusDesc expiresAt acceptedAt rejectedAt rejectReason createdAt
```
- 鉴权：非候选人本人 → 401；Offer 不存在 → 4200
- 拼装：jobTitle → `fetchApplication`（降级"岗位"+jobId）；companyName → `hr_company` 按 company_id（失败置 null）
- 供 C 端投递追踪页「Offer 待确认」卡片展示 + accept/reject 跳转（`OFFER_RECEIVED` 通知 targetId=offerId）

---

#### 7. 候选人接受 Offer
**POST** `/api/v1/hr/offers/{offerId}/accept`（**候选人身份**，或 C 端反向走 `/internal/offers/accept`）

1. 鉴权：`UserContext.getUserId() != offer.candidateId` → 401
2. `selectByIdForUpdate`（行锁）→ 存在校验（4200）
3. 幂等：`status==ACCEPTED` → 直接成功（不重复走流程）
4. 状态校验：`status==SENT` 且 `expires_at > NOW()`（否则 4201/4202）
5. Feign `confirmHc`：失败（B 2204 已释放/状态非法）→ 提示 **"名额不足，请联系HR"**，Offer 保持 SENT
6. confirm 成功 → Offer `SENT→ACCEPTED`、`accepted_at=NOW()`（乐观锁）
7. 投递 `OFFERED→OFFER_ACCEPTED`（已录用，C 新状态机；失败回滚 Offer）
8. 返回 `OfferActionResultVO`（offerStatus/applicationStatus/notificationSent）

---

#### 8. 候选人拒绝 Offer
**POST** `/api/v1/hr/offers/{offerId}/reject`（**候选人身份**，或 C 端反向走 `/internal/offers/reject`）

请求体：`{"rejectReason": "薪资不匹配"}`（选填）

1. 鉴权同上 → 行锁 → 存在校验（4200）→ 幂等（已 REJECTED 直接成功）→ `status==SENT` 且未过期（4201/4202）
2. Offer `SENT→REJECTED`、`rejected_at=NOW()`、`rejectReason`（乐观锁）
3. Feign `releaseHc(reason=REJECTED)`：失败仅记日志不阻塞（对账兜底）
4. 投递 `OFFERED→OFFER_DECLINED`（已拒绝，C 新状态机）
5. 返回 `OfferActionResultVO`

> **🔀 双链路并存（2026-08-08 补丁）**：候选人接受/拒绝 Offer 除 D 端上述接口外，还有 **C 端投递追踪页** `PUT /api/v1/applications/{id}/offer/accept|decline` → C 调 D 内部接口 `POST /internal/offers/accept|reject`（body `{applicationId[,rejectReason]}`）**反向同步 hr_offer + confirm/release HC**（`syncApplication=false` 不更新投递状态，避免行锁死锁；D 幂等自愈）。详见 5.5.12。

---

### 5.5.5 消息沟通接口（公共模块，D 不交付）
> **说明（2026-08-08）**：消息沟通（会话列表/聊天记录/发送消息/获取或创建会话/轮询新消息，原 5 个接口）由**公共模块/lingxi-chat** 负责（`msg_conversation`/`msg_message` 表归属 lingxi-user 成员A），**不在 lingxi-hr 交付范围**。HR 端消息页面直接调 lingxi-chat 的 `/api/v1/chat/**` 接口。会话/消息表结构见 5.4 节参考。

---

### 5.5.6 个人中心接口
> **裁剪（2026-08-08 用户确认）**：`position` **不展示**（`hr_company_member` 无此列、lingxi-user 无来源）；操作日志不实现（前端不展示）。HR 与面试官共用（`requireActiveMember`，4011）。name/avatar/密码/手机号/邮箱均**薄转发 lingxi-user**（`AccountFeignClient`），失败透传 A 错误码。

#### 1. 获取账号信息
**GET** `/api/v1/hr/account/profile`（HR_ADMIN / INTERVIEWER 共用）

**响应：**

```json
{
  "code": 200,
  "data": {
    "id": 2001,
    "name": "李明",
    "phone": "138****8888",
    "email": "liming@company.com",
    "avatar": "https://oss.lingxi.com/avatars/2001.jpg",
    "department": "人力资源部",
    "companyName": "灵犀科技",
    "role": "HR"
  }
}
```
- 字段来源：id/name/phone/email/avatar/role → A `GET /internal/users/{id}`；department → `hr_company_member`；companyName → `hr_company`
- **role 说明**：返回 A 侧 `sys_user.role`（全局角色，HR 注册为 `HR`），**非** 企业内 `hr_company_member.role`（HR_ADMIN/INTERVIEWER）；如需企业内角色需另行查询成员表
- 降级（try-catch 不阻塞）：A 失败 → name="用户"+userId、avatar=null、phone="***"、email=null；无成员/企业 → department/companyName=null

#### 2. 更新账号信息
**PUT** `/api/v1/hr/account/profile`

| 参数 | 类型 | 必填 | 描述 |
| --- | --- | :---: | --- |
| name | String | N | 姓名（1-32 字符，A 校验每月一次修改限制） |
| avatar | String | N | 头像URL |
| department | String | N | 部门（本地 `hr_company_member`） |

- 全空 → 400「请至少填写一项需要修改的信息」；name/avatar → Feign A `PUT /api/v1/user/info`（透传 token）；department → 本地 `updateDepartment`
- **响应：** `{"code": 200, "data": null}`

#### 3. 修改密码
**PUT** `/api/v1/hr/account/password`

| 参数 | 类型 | 必填 | 描述 |
| --- | --- | :---: | --- |
| oldPassword | String | Y | 当前密码 |
| newPassword | String | Y | 新密码（A 校验 8-20 位含大小写字母数字特殊字符） |
| confirmPassword | String | Y | 确认新密码 |

- D 校验 `confirmPassword == newPassword`（不一致 → 400）；Feign A `POST /api/v1/auth/change-password`；成功后 A 清 token → 重新登录

#### 4. 发送手机号验证码
**POST** `/api/v1/hr/account/phone/send-code` — 入参 `{newPhone}`（@Phone）；Feign A `POST /api/v1/user/phone/send-code`（验证码发到新手机号）

#### 5. 验证并修改手机号
**POST** `/api/v1/hr/account/phone` — 入参 `{newPhone, code}`（code 6 位数字）；Feign A `POST /api/v1/user/phone`（A 校验验证码 + 改 sys_user.phone）；成功后 A 清 token → 重新登录

#### 6. 发送邮箱验证码
**POST** `/api/v1/hr/account/email/send-code` — 入参 `{email}`（@Email）；Feign A `POST /api/v1/user/email/send-code`（验证码发到新邮箱）

#### 7. 验证并更新邮箱
**POST** `/api/v1/hr/account/email` — 入参 `{email, code}`（@Email + 6 位数字）；Feign A `POST /api/v1/user/email/verify`（A 校验验证码 + 改 sys_user.email）

#### 8. HR公开信息（2026-08-10 新增，供求职者查看）
**GET** `/api/v1/hr/{hrId}/public`（CANDIDATE 角色）

**响应**：`Result<HrPublicInfoVO>`：

```
id name avatar role department position company{id,name,industry,scale,description,logo}
```
- 校验：HR 用户不存在或无企业成员关系 → 404；**不含手机号/邮箱等隐私字段**
- `role` 固定 `HR`；`position` 取 `hr_company_member.techDirection`；company 为 null 时省略
- 供 C 端面试/沟通等场景展示 HR 及其所属企业公开信息

---

### 5.5.7 成员管理接口
> 路径统一为 `/api/v1/hr/company/members`（网关已配 `/api/v1/hr/company/**`）。添加/移除/刷新邀请码均需本企业 **HR_ADMIN**（4011）；列表本企业 ACTIVE 成员可看。`sys_user.role`（全局）与 `hr_company_member.role`（企业内 HR_ADMIN/INTERVIEWER）为两套角色，勿混淆。

#### 1. 邀请码加入企业
**POST** `/api/v1/hr/company/members/join-by-invite`

**请求体：** `{"inviteCode": "A3K9M2"}`

**响应**：`Result<HrJoinCompanyVO>`：`companyId / companyName / role=HR_ADMIN / status`

**业务逻辑：** 校验 inviteCode 格式 6 位大写字母+数字（4006）→ 查企业（不存在/已禁用 4006）→ `cert_status != APPROVED` → 4009「该企业尚未通过认证」→ 已是成员 → 4008；插入成员 `role=HR_ADMIN`、`department="待定"`（NOT NULL 兜底）

> **与系分差异**：系分文档 2.2.6 约定加入者 `role=INTERVIEWER`，实际实现为 **HR_ADMIN**（用户确认，2026-08-03）。
>
> **⚠ 已知缺陷（2026-08-11）**：当前实现落库 `hr_company_member.role=HR_ADMIN`，但响应 `HrJoinCompanyVO.role` 仍返回 `INTERVIEWER`（`HrCompanyMemberServiceImpl.joinByInvite`），与上述「实际为 HR_ADMIN」不符，**待修复**。

#### 2. 刷新邀请码
**PUT** `/api/v1/hr/company/members/invite-code`（HR_ADMIN）

**响应：** `Result<String>`（新 6 位邀请码）；旧码立即失效，已加入成员不受影响

#### 3. 成员列表
**GET** `/api/v1/hr/company/members?role=&status=`

| **参数** | **类型** | **必填** | **描述** |
| --- | --- | :---: | --- |
| role | String | 否 | 按角色筛选：HR_ADMIN/INTERVIEWER |
| status | String | 否 | 按状态筛选：ACTIVE/DISABLED（默认 ACTIVE） |

**响应**：`Result<List<HrMemberVO>>`：

```
id userId name phone avatar role department techDirection interviewCount status createdAt
```
- `id` 为企业成员ID（前端可作 memberId 使用）；**无 `roleDesc` 字段**，角色文案由前端按 `role` 枚举映射；`avatar` 为头像URL
- `name`/`phone` 通过 Feign 调 A：`batch?ids=` 优先 → 循环单查降级 → 兜底 name="用户"+userId、phone="***"
- 无需分页（企业成员数量有限）

```json
{
  "code": 200,
  "data": [
    {
      "id": 13001,
      "userId": 2002,
      "name": "王工",
      "phone": "139****9999",
      "avatar": null,
      "role": "INTERVIEWER",
      "department": "技术部-前端组",
      "techDirection": "前端",
      "interviewCount": 28,
      "status": "ACTIVE",
      "createdAt": "2026-07-20T10:00:00"
    }
  ]
}
```

#### 4. 创建面试官
**POST** `/api/v1/hr/company/members`（HR_ADMIN）

**请求参数**：

| **参数** | **类型** | **必填** | **描述** |
| --- | --- | :---: | --- |
| phone | String | 是 | 手机号（作为登录账号） |
| code | String | 是 | 短信验证码（前端先调 A `/auth/send-code`） |
| password | String | 是 | 密码（8-20 位强度校验） |
| name | String | 是 | 真实姓名 |
| department | String | 是 | 所属部门 |
| techDirection | String | 否 | 技术方向：前端/后端/全栈/产品/设计/数据/其他 |

**响应：** `Result<Void>`

**业务逻辑：** 复用 `AuthFeignClient.register(role=INTERVIEWER)` 建号（验证码/手机号重复错误透传 A）→ 校验 `uk_company_user`（重复 4002）→ insert `hr_company_member`（role=INTERVIEWER）→ 发送邀请短信（`SmsUtil.send`，失败仅记日志不阻塞）

#### 5. 移除面试官
**DELETE** `/api/v1/hr/company/members/{memberId}`（HR_ADMIN）

**响应：** `Result<Void>`

**业务逻辑：** 校验成员存在 + 本企业（4010）→ `role==HR_ADMIN` → **4012**「不能移除企业管理员」（仅面试官可移除）→ `deleteById` 硬删除（只删本企业成员关系，不影响 `sys_user` 账号及其它企业关系）

---

### 5.5.8 Dashboard接口（裁剪）
> **说明（2026-08-08）**：Dashboard 4 个接口（stats/trend/funnel/todos）**D 不实现**——前端统计页**复用已有接口**（候选人列表/Offer列表/面试列表等）拼装。

---

### 5.5.9 Mock Interview接口
> 接口收拢到 `com.lingxi.hr.agent` 包；LLM 走百宝箱 tboxsdk 3 个生成型工作流；`hr.agent.mock=true` 时用 `MockTboxLlmClient` 预设 JSON 联调。出题**不依赖数据库题库**，基于【岗位考察要点 B + 简历能力模型 C】动态生成。

#### 1. 生成题目（SSE）
**POST** `/api/v1/mock-interview/generate`

**请求体：** `{"jobId": 101, "jobTitle": "高级前端工程师", "questionCount": 5, "resumeId": 3001}`（resumeId 选填，不传取默认简历）

**业务逻辑：** 前置校验每日次数 `hr.mock-interview.quota`（默认3，0=不限，超限 **40018** 不建会话）→ 建 `mock_session`（session_id=`mock-YYYYMMDD-NNN`，Redis INCR）→ progress(FETCH_JOB) 调 B `requirements`（失败降级为仅 jobTitle）→ progress(FETCH_RESUME) 调 C 能力模型+简历详情（token 透传；无简历 → resumeUsed:false）→ progress(GENERATING) 调百宝箱**出题工作流**（appId `202608APE9vh20999665`）→ 逐题写 mock_answer 快照 → SSE `result(questions)` → `done`

**SSE事件：**

```plain
event: progress
data: {"sequence":1,"code":"FETCH_JOB","message":"正在获取岗位考察重点..."}
event: progress
data: {"sequence":2,"code":"FETCH_RESUME","message":"正在读取简历能力画像..."}
event: progress
data: {"sequence":3,"code":"GENERATING","message":"AI正在基于岗位与简历定制题目..."}
event: result
data: {"sessionId":"mock-20260804-001","questions":[{"questionNumber":1,"questionType":"PROJECT","dimension":"项目深挖","content":"..."}]}
event: done
data: {"timestamp":"2026-08-04T10:00:10.000Z"}
```
- 出题策略：BASIC 30% + PROJECT 40% + BOUNDARY 20% + COMPREHENSIVE 10%，由浅入深；题目数 5/8/10
- 降级：岗位/简历数据均不可用或 LLM 失败 → 内置通用模板题（SSE 标降级）；`40016` 简历不存在或无权限、`40017` 出题服务繁忙

#### 2. 获取题目
**GET** `/api/v1/mock-interview/{sessionId}/questions`

**响应：** `Result<List<MockQuestionVO>>`：`questionNumber / questionType / dimension / content`（支持「继续面试」）

#### 3. 提交答案（SSE）
**POST** `/api/v1/mock-interview/{sessionId}/answer`

**请求体：** `{"questionNumber": 1, "answer": "React虚拟DOM是..."}`

**业务逻辑：** 校验 session IN_PROGRESS（COMPLETED → **40014**，不存在 → 4007）→ 组装评分 prompt → 调百宝箱**评分工作流**（appId `202608APlpZR21033809`）→ 三维分 + AI 点评；`overall = tech×0.5 + expr×0.3 + depth×0.2`；超时(>15s) → overall=0 + "评分服务繁忙"；UPDATE 覆盖同题答案

**SSE事件：** `result` → `{"questionNumber":1,"techAccuracyScore":82,"expressionScore":75,"knowledgeDepthScore":68,"overallScore":76.5,"aiComment":"..."}`

#### 4. 跳过题目
**POST** `/api/v1/mock-interview/{sessionId}/skip?questionNumber=N`

**请求参数：** `questionNumber`（Query，必填）——待跳过的题号

**响应：** `Result<Void>`；写 `is_skipped=1`、`candidate_answer` 为空

#### 5. 生成报告（SSE）
**GET** `/api/v1/mock-interview/{sessionId}/report`

**业务逻辑：** 校验 session 归属本人；`IN_PROGRESS` 正常生成报告，`COMPLETED` 幂等返回历史报告（2026-08-10）；无有效答题记录 → **40015** → 聚合各题三维分（未评分题剔除）→ 调百宝箱**报告工作流**（appId `202608AP7lse21015615`）生成 highlights/weaknesses/improvementPlan（LLM 不可用 → 按分数阈值规则生成）→ 写 `mock_report` + `mock_session(status=COMPLETED, overall_score, completed_at)` → SSE 返回完整报告

**SSE事件：** `result` → `{"sessionId":"mock-...","overallScore":78.5,"overallLevel":"GOOD","techAccuracyScore":...,"expressionScore":...,"knowledgeDepthScore":...,"projectScore":...,"highlights":[],"weaknesses":[],"improvementPlan":[],"answeredCount":4,"skippedCount":1,"totalDurationSec":600}`

---

### 5.5.10 公司信息与认证接口

#### 1. 创建企业
**POST** `/api/v1/hr/company/info`

**请求体**：`HrCompanyDTO`（name/shortName/description/industry/scale/logoUrl/address/website）

**业务逻辑：** 校验用户已有企业（`hr_company_member` role=HR_ADMIN 存在 → 4001）→ 生成 6 位邀请码按 `invite_code` 查重（非 name）→ **同一事务**写 `hr_company`（`cert_status=PENDING`）+ `hr_company_member(HR_ADMIN)` → 返回 `Result<HrCompanyVO>`

**响应：** `Result<HrCompanyVO>`

#### 2. 查看企业信息
**GET** `/api/v1/hr/company/info`

**响应：**

```json
{
  "code": 200,
  "data": {
    "id": 201,
    "name": "灵犀科技有限公司",
    "shortName": "灵犀科技",
    "description": "AI驱动的智能招聘平台",
    "industry": "互联网/IT",
    "scale": "100-499人",
    "logoUrl": "https://oss.lingxi.com/logos/company_201.png",
    "address": "北京市海淀区中关村大街1号",
    "website": "https://www.lingxi.com",
    "inviteCode": "A3K9M2",
    "certStatus": "APPROVED"
  }
}
```

#### 3. 更新企业信息
**PUT** `/api/v1/hr/company/info`

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | :---: | --- |
| shortName | String | N | 企业简称 |
| description | String | N | 企业简介 |
| logoUrl | String | N | 企业Logo URL |
| address | String | N | 办公地址 |
| website | String | N | 企业官网 |

**业务逻辑：** 企业全称（name）认证后（`cert_status=APPROVED`）**忽略不更新**；仅更新非 null 字段。**响应：** `{"code": 200, "data": null}`

#### 4. 提交认证申请
**POST** `/api/v1/hr/company/certification`（multipart 表单）

**请求参数：** `HrCompanyDTO`（企业信息随表单）+ `businessLicense`（选填）+ `certMaterial`（选填）

**业务逻辑：** 校验 HR 无已有企业（存在 → 4001「您已创建企业」）→ 提供文件时校验格式 jpg/png/pdf、≤5MB（否则 **4004**），上传 MinIO 得 URL → **同一事务**写 `hr_company`（cert_status=PENDING）+ `hr_company_member(HR_ADMIN)` + `hr_company_certification`（status=PENDING）→ **重复提交拦截（实现说明）**：本接口前置要求「HR 无已有企业成员关系」，同一 HR 无法再次走提交流程，故实际命中 **4001**；`4003`（CERT_PENDING_DUPLICATE）已定义但未使用。**响应：** `Result<Void>`

#### 5. 认证状态查询（2026-08-03 追加，pending 审核页依赖）
**GET** `/api/v1/hr/company/certification/status`

**响应**：`Result<HrCertificationStatusVO>`：`certStatus / certRejectReason / submittedAt`
- **注意**：注册当次 token 无 companyId claim，按 `userId` 反查成员关系定位企业（不依赖 `UserContext.getCompanyId()`）

---

### 5.5.11 消息通知接口（裁剪）
> **说明（2026-08-08）**：原 4 个通知接口（list/unread-count/read/read-all）**D 不实现**——HR 端通知列表/未读/已读**复用 lingxi-chat** `/api/v1/notifications/*`（成员A 已实现）。通知产生走 Feign 直调 lingxi-chat `POST /internal/notifications`（见 5.6.3）。

---

### 5.5.12 内部接口（C端 Offer 反向同步 + 跨服务查询，2026-08-08/08-10）
> lingxi-admin 的统计接口（面试/Offer/企业排行）**裁剪**——admin **直接查 lingxi 库**（D 不提供 `/internal/*` 统计接口）。D 实际提供 **7 个内部接口**：`/internal/offers/*` 2 个（C 端接受/拒绝 Offer 反向同步）+ `/internal/applications/{id}/notify-hr` 1 个（新投递通知）+ `/internal/companies/*` 4 个（供 lingxi-user 查企业/成员）。均经 gateway `/internal/**` 免鉴权白名单；`/internal/companies/**` 已配 `lingxi-internal-hr` 路由，而 `/internal/applications/**` 被 `lingxi-internal-resume` 路由占用，故 notify-hr **仅经 Feign 服务名直调**。

#### 1. 接受 Offer（C端反向同步）
**POST** `/internal/offers/accept`

请求体：
```json
{ "applicationId": 5001 }
```

**响应：** `Result<Void>`，`code=200` 成功

**业务逻辑**（`doAcceptOffer(offer, syncApplication=false)`）：`selectByApplicationIdForUpdate` 行锁定位最新 Offer（不存在 4200）→ 幂等（已 ACCEPTED 直接成功）→ 校验 SENT 且未过期（4201/4202）→ Feign `confirmHc`（失败 **4203**「名额不足，请联系HR」，Offer 保持 SENT）→ `SENT→ACCEPTED`+accepted_at → **不更新投递状态**（C 已自己 transition，避免行锁死锁）

#### 2. 拒绝 Offer（C端反向同步）
**POST** `/internal/offers/reject`

请求体：
```json
{ "applicationId": 5001, "rejectReason": "薪资未谈拢" }   // rejectReason 选填
```

**响应：** `Result<Void>`，`code=200` 成功

**业务逻辑**（`doRejectOffer(offer, rejectReason, syncApplication=false)`）：行锁定位 → 幂等 → 校验 SENT 且未过期 → `SENT→REJECTED`+rejected_at+reject_reason → Feign `releaseHc(reason=REJECTED)`（best-effort，失败对账兜底）→ **不更新投递状态**

> **调用约定（C 侧落地，成员C）**：C 端 `PUT /api/v1/applications/{id}/offer/accept|decline` 先本地 `transition()`（OFFERED→OFFER_ACCEPTED/OFFER_DECLINED）后调 D 上述接口；D 失败 → C 抛异常回滚本地 transition（D 幂等自愈）。D 端独立入口 `POST /api/v1/hr/offers/{id}/accept|reject`（syncApplication=true，联动投递状态）**两条链路并存**。
>
> **错误码（透传）**：4200 Offer 不存在 / 4201 状态不允许 / 4202 Offer 已过期 / 4203 HC 不足或 confirm 失败

#### 3. 新投递通知 HR（2026-08-10 新增，C 端投递成功后回调）
**POST** `/internal/applications/{id}/notify-hr`

**响应：** `Result<Void>`，`code=200` 成功

**业务逻辑：** 取投递详情（companyId/jobTitle/candidateId）→ 查本企业全部 `HR_ADMIN` → 拼候选人姓名 → Feign 直调 lingxi-chat 发送 `NEW_APPLICATION` 通知（type 走 lingxi-chat ChatConstant）。**全程 best-effort**：任何环节失败仅告警，不抛异常，不影响 C 投递闭环。

> **调用方（成员C）**：`lingxi-resume` 在投递 submit/reapply 成功后经 `HrNotifyFeignClient` 调本接口。路径 `/internal/applications/**` 在网关被 `lingxi-internal-resume` 路由占用，故本接口**仅经 Feign 服务名直调**，不走网关转发。

#### 4. 企业内部查询（2026-08-10 新增，供 lingxi-user 调用）
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/internal/companies/{companyId}` | 按企业ID查询（返回 id/name/certStatus） |
| GET | `/internal/companies/by-name?name=` | 按企业名称查询企业ID |
| GET | `/internal/companies/member/company-id?userId=` | 查询用户所属企业ID |
| GET | `/internal/companies/members/hr-user-ids` | 查询全部 HR 用户ID |

> **调用方（成员A）**：`lingxi-user` 经 `HrCompanyFeignClient` 调用。

## 5.6 关键技术设计

### 5.6.1 Offer 定时任务（2 个）

**① Offer 过期扫描** `@Scheduled(cron = "0 */5 * * * ?")`（每 5 分钟）：
```java
public int expireOffers() {
    // 1. selectExpiredSent(): WHERE status='SENT' AND expires_at<=NOW()
    //    条件更新 status='EXPIRED'（rows=1 才处理，0=已被确认/撤回跳过）
    // 2. Feign releaseHc(reason=EXPIRED)（幂等；失败记日志不阻塞）
    // 3. Feign直调 lingxi-chat 通知候选人(OFFER_RECEIVED「Offer已过期」) + HR(OFFER_MANAGE) best-effort
    // 4. 投递回退 OFFERED→OFFERABLE（best-effort，失败仅记日志）
}
```

**② HC 补偿对账** `@Scheduled(cron = "0 0 * * * ?")`（每小时）：
```java
public int reconcileHc() {
    // 1. selectTerminalForReconcile(): 终态(REJECTED/EXPIRED/WITHDRAWN) 且 last_sync_time IS NULL
    // 2. 逐条 → Feign releaseHc(reason 按 offer.status 映射 EXPIRED/REJECTED)（幂等，B 已 RELEASED 无副作用）
    // 3. release 成功 → 回写 last_sync_time=NOW()；失败记日志下轮重试
    // 注：ACCEPTED 不入对账（应 CONFIRMED，无需释放）
}
```
> **2026-08-07 决策**：原计划「调 B 查 HC 流水先查后放」的 `GET /internal/jobs/{jobId}/hc/flow` **B 侧未实现**，对账改为**直接调幂等 release** 兜底（`getHcFlow` 死代码已删除；如 B 后续补流水查询可改回先查后放）。

### 5.6.2 Mock Interview Agent架构（百宝箱 3 生成型工作流）
| 属性 | 说明 |
| --- | --- |
| LLM 接入 | 百宝箱 tboxsdk `TboxClient.chat(appId, query, userId)`，**3 个独立生成型工作流**：出题 `202608APE9vh20999665` / 评分 `202608APlpZR21033809` / 报告 `202608AP7lse21015615`（`hr.agent.mock=true` 走 `MockTboxLlmClient` 预设 JSON 联调） |
| 出题输入 | 岗位考察要点（B `/internal/jobs/{jobId}/requirements`）+ 简历能力模型为主（C `/api/v1/resumes/{id}/ability-model` 5 维分+subDimensions）、简历详情为辅（cardStructure 截断 ~2000 字）——**不依赖数据库题库** |
| 出题策略 | 基础验证 BASIC 30% + 项目深挖 PROJECT 40% + 能力边界 BOUNDARY 20% + 综合素养 COMPREHENSIVE 10%，由浅入深（AI 依据能力模型动态调节难度） |
| 评分维度 | 技术准确度 50% + 表达逻辑 30% + 知识深度 20%（`overall = tech×0.5 + expr×0.3 + depth×0.2`） |
| 超时控制 | 单次 LLM 调用整体 60s（出题/报告），评分场景 15s；超时走降级 |
| 降级策略 | 出题 → 内置通用模板题；评分 → overall=0 + "评分服务繁忙"；报告 → 按分数阈值规则生成 |
| 每日次数限制 | `hr.mock-interview.quota`（默认 3，0=不限），`generate` 前按当天已建 `mock_session` 数计数，超限 **40018** 且不建会话 |
| 代码收拢 | `com.lingxi.hr.agent` 包（entity/mapper/dto/feign/config/controller/tools），Mapper XML 在 `resources/mapper/agent/` |
| token 透传 | lingxi-common `FeignConfig.tokenRelayInterceptor`（2026-08-04）：以求职者身份调 B/C 用户接口，异步线程/已手动设置时跳过 |

### 5.6.3 消息通知（Feign 直调 lingxi-chat，替代 RocketMQ）
> **2026-08-08 方案变更**：~~RocketMQ 消费者（`hr-notification` topic）~~ **废弃**。lingxi-chat 通知服务为**直接调用式**、`hr-notification` topic 无消费者、WebSocket session 仅 lingxi-chat 进程持有，故改为 **Feign 直调**：

```java
// D 侧通知器（CandidateNotifier / InterviewNotifier / OfferNotifier）
// Feign 直调 lingxi-chat POST /internal/notifications
NotificationFeignClient.createNotification(CreateNotificationRequest
    { userId, type, title, content, targetType, targetId })
```
- type 用 lingxi-chat `ChatConstant`：`RESUME_VIEWED`（筛选）/ `INTERVIEW_INVITE`（候选人面试邀约）/ `INTERVIEW_SCHEDULE`（面试官安排）/ `OFFER_RECEIVED`（候选人 Offer）/ `OFFER_MANAGE`（HR 催促/过期提醒）
- lingxi-chat 内部写 `sys_notification` 表 + 用户在线时 WebSocket 实时推送（`/ws/message`）
- **HR 端通知列表/未读/已读复用 lingxi-chat** `/api/v1/notifications/*`（A 已实现），D 不实现
- **全程 best-effort**：Feign 失败记 warn 日志、`notificationSent=false`，不阻塞主流程

### 5.6.4 Mock Interview Agent答题评分逻辑
```java
// 评分维度及权重（百宝箱"评分工作流"返回三维分，D 侧按权重合成综合分）
public AnswerScore evaluate(String questionContent, String referenceAnswer, String userAnswer) {
    // 1. 调百宝箱评分工作流（appId 202608APlpZR21033809）
    ScoreResult r = llmClient.score(questionContent, questionType, referenceAnswer, userAnswer);
    // 2. 技术准确度 (50%) + 表达逻辑 (30%) + 知识深度 (20%)
    double totalScore = r.techAccuracy * 0.5 + r.expression * 0.3 + r.knowledgeDepth * 0.2;
    // 3. 超时(>15s) → totalScore=0，aiComment="评分服务繁忙，本题未评分"
    // 4. 越界 clamp 0-100；同题重复提交 UPDATE 覆盖
    return new AnswerScore(totalScore, r.techAccuracy, r.expression, r.knowledgeDepth, r.aiComment);
}
```

### 5.6.5 数据权限隔离
| 角色 | 候选人可见范围 | 面试记录可见范围 | Offer可见范围 |
| --- | --- | --- | --- |
| HR管理员 | 投递本企业的所有候选人 | 本企业所有面试 | 本企业所有Offer（发起/列表/概览/催促/撤回） |
| 面试官 | 仅被分配面试的候选人 | 仅本人参与的面试（`interviewer_id==本人`，否则 4011） | 不可见（访问 4011） |
| 候选人 | 本人投递/Offer（详情/接受/拒绝，`userId==candidateId`） | 本人面试 | 本人 Offer（`userId==candidateId`，否则 401） |

在 Service 层通过 `UserContext.getUserId()` / `getCompanyId()` 注入数据过滤条件（`requireActiveMember` / `requireHrAdmin` / `requireCandidateOwner`），所有列表查询强制注入 `company_id` 过滤。

---



## 5.7 边界与异常场景处理

在各模块的正常流程之外，明确边界情况、异常情况和降级策略，避免线上故障。

### 一、候选人管理
| 场景 | 处理策略 |
| --- | --- |
| 候选人列表为空 | 返回空列表 `{ total: 0, records: [] }`，前端展示"暂无候选人投递"引导文案 |
| Top5 推荐不足 5 人 | 实际匹配到几人返回几人，不填充，接口返回实际数量（可能为 0）；Feign 失败返回空列表 `[]`（推荐性质，不阻塞） |
| 重复标记合适/不合适 | 校验当前投递状态：已标记过（SCREENED/REJECTED）或已进入后续流程/终态（INTERVIEWING/OFFERABLE/OFFERED/WITHDRAWN）→ **4302** "该候选人已处理，请勿重复操作" |
| 投递不存在 / 跨企业 | `getApplication` 空/失败 → **4300**；投递 `companyId != 当前企业` → **4301** |
| 查看简历无 resumeId / C 返回 RESUME_NOT_FOUND | **4303** "候选人简历不存在"；C 网络异常 → 500（不降级为空） |
| Feign 依赖不可用 | 候选人姓名/头像调 `lingxi_user` 失败时，返回 `candidateName="用户"+userId`、`phone="***"`、`avatar=null` 降级，不阻塞列表查询 |

### 二、面试协同
| 场景 | 处理策略 |
| --- | --- |
| 面试安排时间冲突 | **强制校验（2026-08-06 用户确认）**：同面试官 `scheduled_at` 前后 60 分钟重叠（status 非终态）→ **4102**（缓冲窗口常量，后续可配置） |
| 面试官不存在或已禁用 | 创建面试时校验 `hr_company_member.status=ACTIVE`，不满足 → 4010 成员不存在 / 4005 账号已禁用 |
| 投递状态非 SCREENED | 创建面试前校验投递 `status==SCREENED`，否则透传 C 侧错误码（3006）或 **4101** |
| 面试已取消后操作 | start/录入评估前校验状态，非终态（COMPLETED 不可取消 → 4101） |
| 重复录入评估 | 已有正式评估（`is_draft=0`）→ **4104**；已有草稿（`is_draft=1`）→ **UPDATE 覆盖转正式**（2026-08-06 修正）；并发双正式提交 `uk_interview_id` DuplicateKeyException 兜底 → 4104 |
| 评语字数不足 20 字 | 正式提交参数校验层拦截 → **4103**（草稿跳过） |
| 面试状态流转异常 | 仅 `IN_PROGRESS` 允许录入评估；start 仅 `PENDING/SCHEDULED` → `IN_PROGRESS` |
| 权限（2026-08-06 收紧） | HR_ADMIN 操作本企业全部面试；INTERVIEWER 仅 `interviewer_id==本人`，否则 **4011** |
| AI 反馈 | **已裁剪（2026-08-08）**：`feedback` 字段留 null；REJECT 落选反馈用模板 `TemplateRejectFeedbackGenerator` 写 `reject_feedback` |

### 三、Offer 管理
| 场景 | 处理策略 |
| --- | --- |
| **发起 Offer 时 HC 不足** | Feign `reserveHc` 返回 B 2201 → **4203** "HC 不足，无法发起 Offer" |
| **重复发起 Offer** | **已删 `uk_application_id` 唯一索引（2026-08-07）**：仅最新一条为 `SENT`（活跃）/`ACCEPTED`/`REJECTED`（终态）→ **4205**；`WITHDRAWN`/`EXPIRED` 旧记录**放行可重发**（并发双发由应用层校验兜底） |
| **发起事务失败补偿** | reserve 成功但本地插入/投递联动失败 → 补偿 `releaseHc(reason=D_PERSIST_FAILED)` 后整体回滚 |
| **候选人确认时 Offer 已过期** | `FOR UPDATE` 读 Offer → 校验 `status==SENT && expires_at > NOW()`，已过期 → **4202** |
| **候选人确认时 Offer 已被他人处理（并发）** | 行级锁 + 乐观锁 `WHERE status='SENT'`，后到者 rows=0 → **4201** |
| **confirm HC 时 Feign 失败** | 不更新 `hr_offer.status`，返回 **4203** "名额不足，请联系HR"（Offer 保持 SENT，可重试） |
| **拒绝/撤回时释放 HC 失败** | Offer 状态先更新为终态，释放 HC 失败仅记日志（WARN）不阻塞——由每小时 **HC 补偿对账**幂等兜底 |
| **Offer 过期定时任务与确认并发** | 定时任务条件更新 `WHERE status='SENT' AND expires_at<=NOW()`，确认用 `FOR UPDATE` 行级锁，双方互斥，只有一个成功 |
| **催促次数超限** | 24h 内 `urge_count >= 2` → **4204** "催促频率限制（24h内最多2次）" |
| **入职时间不在未来 / 薪资<=0** | 参数校验 → 400 "入职时间必须在未来日期" / "薪资必须大于 0" |
| **薪资超出岗位范围** | 不拦截，响应 `salaryWarning.warn=true`（`salaryNegotiable=false` 且超范围时） |
| **候选人身份鉴权** | accept/reject/详情要求 `userId==offer.candidateId`，否则 **401**；INTERVIEWER 访问 Offer → **4011** |
| **Feign 超时降级** | 见下方"跨服务调用降级"通用策略 |

### 四、消息通知（方案已变更）
| 场景 | 处理策略 |
| --- | --- |
| ~~RocketMQ 消费失败~~ | **已废弃**（`hr-notification` topic 无消费者）——通知改 **Feign 直调 lingxi-chat `POST /internal/notifications`** |
| Feign 通知失败 | best-effort：记 warn 日志、`notificationSent=false`，不阻塞主流程 |
| 通知列表为空 | 复用 lingxi-chat `/api/v1/notifications/*`，空返回 `{ total: 0, unreadCount: 0, records: [] }` |
| 重复标记已读 | 幂等处理，已读再次标记不报错（lingxi-chat 侧） |

### 五、消息沟通（公共模块）
> **说明（2026-08-08）**：会话/消息/已读未读由**公共模块/lingxi-chat** 负责（`msg_conversation`/`msg_message`），D 不交付。以下原边界策略由 lingxi-chat 实现：会话已存在返回已有 `conversationId`；消息为空 400；对象已禁用禁止发送；未读数最终一致。

### 六、题库管理 / Mock Interview 出题（2026-08-04 方案变更）
| 场景 | 处理策略 |
| --- | --- |
| **出题依赖** | **不依赖数据库题库**：AI 基于【岗位考察要点（B `/requirements`）】+【简历能力模型（C `/ability-model`）】动态出题（`job_question` 题库由成员B维护，D 不调用搜题） |
| 岗位要求 Feign 失败 | 岗位输入降级为仅 `jobTitle`，正常出题（不阻塞） |
| 简历获取失败 / 无简历 | 简历输入为空，按通用岗位要求出题（SSE 标 `resumeUsed:false`）；C 简历查询失败 → **40016** |
| LLM 不可用（tboxsdk 调用异常/超时） | 出题走内置通用模板题（SSE 标降级）；评分/报告走规则兜底 |
| 输出题目数 ≠ questionCount | D 侧校验：多则截取前 N 道，少则补齐兜底模板题 |

### 七、成员管理
| 场景 | 处理策略 |
| --- | --- |
| 手机号已注册为求职者 | 该手机号 `sys_user.role=CANDIDATE`，仍允许创建为面试官（一个手机号可拥有多个角色身份，复用 register role=INTERVIEWER） |
| 面试官已在该企业 | `uk_company_user` 唯一约束 → **4002** "该成员已加入企业" |
| 邀请码不存在 / 企业已禁用 | **4006** "邀请码不存在或格式不正确" |
| 邀请码对应企业未通过认证 | `cert_status != APPROVED` → **4009** "该企业尚未通过认证，无法加入" |
| 已在该企业（重复加入） | **4008** "您已是该企业成员，无需重复加入" |
| 邀请短信发送失败 | 记录失败日志，成员关系仍创建成功（`hr_company_member` 写入），不阻塞 |
| 移除成员不存在 / 跨企业 | **4010** "成员不存在" |
| 移除 HR_ADMIN | **4012** "不能移除企业管理员"（仅面试官可移除） |
| 非 HR_ADMIN 操作成员 | 添加/移除/刷新邀请码均需 `role=HR_ADMIN`，否则 **4011** |
| 被移除账号在其他企业 | 只删本企业成员关系（硬删除），不影响 `sys_user` 账号及其它企业关系 |

### 八、C 端模拟面试
| 场景 | 处理策略 |
| --- | --- |
| 每日次数超限 | `generate` 前校验当天已建会话数 `hr.mock-interview.quota`（默认3，0=不限），超限 → **40018**，不创建会话，SSE 发 error |
| 求职者中途退出 | `mock_session.status` 保持 `IN_PROGRESS`，再次进入时可选择"继续面试"或"重新开始" |
| 作答超过 5 分钟 | 前端倒计时结束时自动提交（`is_skipped=1`，`candidate_answer` 为空），评分跳过该题 |
| 作答少于 20 字 | 不强制拦截，AI 评分标注"回答过短" |
| 面试已完成后再次提交答案 | 校验 `mock_session.status != COMPLETED`，已完成的返回 **40014** "面试已完成" |
| session_id 不存在 | **4007** "模拟面试会话不存在" |
| 同一题多次提交 | 以最后一次提交覆盖（UPDATE `candidate_answer` WHERE `session_id` + `question_number`） |
| LLM 评分超时 | 超时（>15s）时本题 `overall_score=0`，`ai_comment="评分服务繁忙，本题未评分"`，不阻塞其他题目 |
| 生成报告时无有效答题记录 | **40015** "暂无答题记录，无法生成报告" |
| 岗位已下线 | 模拟面试不校验岗位状态（`job_title` 已冗余存储），已下线的岗位仍可完成面试 |
| AI 输出非 JSON | 解析失败重试 1 次；仍失败走兜底模板/规则 |

### 九、跨服务调用降级（通用）
| 场景 | 处理策略 |
| --- | --- |
| Feign 调用超时 | 连接超时 3s，读超时 5s。超时后根据场景决定：关键路径（HC 确认/投递状态联动）直接返回错误；非关键路径（获取用户姓名/公司名）降级返回默认值 |
| Feign 调用 500 错误 | 不重试（避免雪崩），记录错误日志，返回业务错误码给前端 |
| 对方服务熔断 | Sentinel 熔断后快速失败，5s 后进入半开状态尝试恢复 |

### 十、定时任务
| 场景 | 处理策略 |
| --- | --- |
| 定时任务执行超时 | `@Scheduled` 默认单线程，上次未执行完不会触发新任务（无需额外控制） |
| 一次扫出大量过期 Offer | 逐条处理（try-catch 单条隔离），Offer 过期不是高频操作，单次扫出量不会超过百级别 |
| 条件更新影响 0 行 | 说明 Offer 已被确认/撤回，跳过 Feign 释放 HC 和发送通知 |
| 释放 HC 时 Feign 失败 | 记录错误日志并继续处理下一条，不阻塞其他 Offer；未回写 `last_sync_time` 的下轮重试 |
| HC 补偿对账 | 每小时扫终态（REJECTED/EXPIRED/WITHDRAWN）且 `last_sync_time IS NULL` → 直接调 B 幂等 release（不查流水）→ 回写 `last_sync_time` |




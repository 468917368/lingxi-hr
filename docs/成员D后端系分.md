## 变更记录

> 记录每次修订的内容，方便追溯。

| **日期**   | **版本** | **修订说明** | **作者** |
| ---------- | -------- | ------------ | -------- |
| 2026-07-29 | 1.0      | 初稿         | D       |

## 项目背景

> 对本次项目的背景以及目标进行描述，方便开发者理解需求，对齐上下文。

本模块来源于灵犀互聘——AI全链路智能招聘平台产品需求文档（PRD V5.0）中第 6-7 部分。成员 D 负责 `lingxi-hr` 微服务（端口 8084），涵盖 B 端 HR 工作台的核心业务——候选人管理、面试协同、Offer 管理、消息通知、消息沟通、成员管理，以及 C 端模拟面试功能。本模块作为招聘闭环的关键枢纽，连接求职者投递与最终录用，需与 `lingxi-job`（B）、`lingxi-resume`（C）通过 Feign 协同完成 Headcount 扣减/回退和候选人简历数据获取。

### 相关资料

- [灵犀互聘 PRD V5.0](./prd.md)
- [成员分工表](./成员分工表.md)

### 参与人

| **项目负责人** | D |
| -------------- | ---- |
| **工程师**     | D |

## 功能模块

> 描述 lingxi-hr 微服务涉及的功能与场景。

本模块核心功能包括：

1. **候选人管理**：候选人列表（按匹配度/时间排序、按状态/岗位筛选）、Top5 高潜推荐、标记合适/不合适
2. **面试安排**：创建面试记录、分配面试官、面试状态管理（待面试/已完成/已取消）
3. **面试评估**：4 维度评分录入（技术能力/沟通表达/岗位匹配/发展潜力各 1-5 分）、评语（≥20 字）、结论（通过/待定/淘汰）
4. **AI 生成反馈**：通过→录用建议，淘汰→落选原因 + 2-3 条提升建议
5. **Offer 管理**：发起 Offer（薪资/入职时间/职级/备注）、状态追踪（SENT→ACCEPTED/REJECTED/EXPIRED）、Feign 调 lingxi-job 预冻结/确认占用/释放 Headcount
6. **Offer 过期**：定时任务扫描过期 Offer，条件更新状态，释放预冻结 HC
7. **消息通知**：RocketMQ 消费者，投递通知/面试提醒/Offer 通知/HC 告警/评估超时提醒
8. **消息沟通**：站内消息存储、会话管理、消息已读/未读状态、HR 与求职者双向文字聊天
9. **题库管理**：题目 CRUD、审核状态管理、去重机制（相似度 > 80% 提示）
10. **成员管理**：创建面试官账号（手机号+姓名+部门+技术方向）、发送邀请短信、成员列表管理
11. **C 端模拟面试**：Mock Interview Agent，选择岗位→逐题提问→AI评分→生成面试报告

### 功能模块树

```plain
lingxi-hr 服务
├── 候选人管理
│   ├── 候选人列表（筛选/排序）
│   ├── Top5 高潜推荐
│   └── 标记合适/不合适
├── 面试协同
│   ├── 面试安排（创建/分配面试官/状态管理）
│   ├── 面试评估录入（4维度评分+评语+结论）
│   ├── AI 生成反馈（录用建议/落选反馈）
│   └── AI 出题数据（提供给 Interview Agent）
├── Offer 管理
│   ├── 发起 Offer
│   ├── Offer 状态追踪
│   ├── Headcount 扣减/回退（Feign 调 lingxi-job）
│   └── Offer 过期定时任务
├── 消息通知
│   └── RocketMQ 消费者（投递/面试/Offer/HC/评估超时）
├── 消息沟通
│   ├── 会话管理
│   ├── 站内消息发送/接收
│   └── 已读/未读状态
├── 题库管理
│   ├── 题目 CRUD
│   ├── 审核状态管理
│   └── 相似度去重
├── 成员管理
│   ├── 创建面试官账号
│   ├── 发送邀请短信
│   └── 成员列表管理
└── C 端 Mock Interview Agent
    ├── 获取岗位考察重点（Feign 调 lingxi-job）
    ├── 搜索题库出题
    ├── 生成定制面试题
    ├── AI 评分与点评
    └── 生成面试报告
```

## 流程图

> 对 lingxi-hr 服务涉及的核心流程进行梳理。

### 1. 候选人筛选到面试安排全流程

```plain
求职者投递简历
     │
     ▼
HR 进入候选人列表 → 查看 Top5 高潜推荐
     │
     ├── 标记"合适" → 投递状态变更 → 通知求职者"通过简历筛选"
     │         │
     │         ▼
     │    安排面试（填写时间/方式/面试官）
     │         │
     │         ▼
     │    推送面试邀约通知给求职者
     │         │
     │         ▼
     │    面试官查看候选人简历 → AI 出题（Interview Agent）
     │         │
     │         ▼
     │    进行面试
     │         │
     │         ▼
     │    录入面试评估（4维度评分+评语+结论）
     │         │
     │         ├── 通过 → AI生成录用建议 → 进入Offer流程
     │         ├── 待定 → 通知HR安排复面
     │         └── 淘汰 → AI生成落选反馈 → 推送给求职者
     │
     └── 标记"不合适" → 投递状态变更 → AI生成落选原因+提升建议 → 推送给求职者
```

### 2. Offer 全流程（预冻结方案）

```plain
面试评估通过
     │
     ▼
HR 点击"发起Offer"
     │
     ▼
填写 Offer 信息（薪资/入职时间/职级/备注）
     │
     ▼
系统校验：
  ├─ 岗位剩余HC > 0（总HC - 已确认 - 已预冻结，HC不足则拦截发起）
  ├─ 薪资是否在岗位薪资范围内（超出仅提示不拦截）
  ├─ 入职时间是否在未来
  └─ 该候选人是否已有待确认的 Offer
     │
     ▼
创建 Offer 记录（状态：SENT）→ 设置有效期
     │
     ▼
Feign 调 lingxi-job → 预冻结 Headcount（job_post.reserved_hc++，写入 job_hc_reservation 状态=RESERVED）
     │
     ▼
推送 Offer 通知给求职者
     │
     ▼
求职者操作分支：
  ├── 确认接受
  │     ├─ 校验 Offer 状态是否为 SENT
  │     ├─ offer_id 幂等校验
  │     ├─ Feign 调 lingxi-job 确认占用 HC（reservation: RESERVED→CONFIRMED, reserved_hc--, confirmed_hc++）
  │     ├─ Offer: ACCEPTED, 投递: OFFERED
  │     ├─ 扣减失败 → 提示"名额不足，请联系HR"
  │     └─ 剩余可招聘HC==0 → 岗位自动关闭
  ├── 拒绝
  │     ├─ 选填拒绝原因
  │     ├─ Offer: REJECTED, 投递: WITHDRAWN
  │     └─ Feign 调 lingxi-job 释放预冻结 HC（reservation: RESERVED→RELEASED, reserved_hc--）
  └── 超时未确认 → 定时任务处理
        ├─ Offer: EXPIRED
        ├─ 投递保持 OFFERABLE
        └─ Feign 调 lingxi-job 释放预冻结 HC
```

### 3. 消息通知流程

```plain
RocketMQ 消息到达
     │
     ▼
消费者解析消息体（事件类型/目标用户/通知内容）
     │
     ▼
写入 msg_notification 表（user_id, type, title, content, is_read=0）
     │
     ▼
根据用户通知偏好决定是否推送：
  ├── 站内通知（默认全部推送）
  ├── 短信通知（面试提醒类可配置）
  └── 邮件通知（Offer/面试邀请类可配置）
     │
     ▼
前端轮询/WebSocket 获取未读通知数 → 红点提示
```

### 4. Mock Interview Agent 面试流程

```plain
求职者选择目标岗位 → 点击"开始模拟面试"
     │
     ▼
【Step 1】get_job_requirements → Feign 调 lingxi-job 获取岗位考察重点
     │
     ▼
【Step 2】search_question_bank → 按岗位+技术栈从题库抽取 5-8 题
     │    出题策略：基础验证30% + 项目深挖40% + 能力边界20% + 综合素养10%
     │    由浅入深排序
     │
     ▼
【Step 3】展示第1题（题号/总题数/内容/考察维度）
     │
     ▼
【Step 4】求职者作答 → 提交回答
     │    超时处理：3分钟提醒、5分钟提示跳过
     │    过短处理：<20字 → AI追问
     │
     ▼
【Step 5】evaluate_answer → AI后台评分（技术准确度/表达逻辑/知识深度 各0-100）→ 评分仅后台记录，不展示给求职者
     │
     ▼
【Step 6】直接展示下一题 → 循环 Step 3-6，直到完成所有题目
     │    每道题的评分和点评在后台累积，不在单题完成后展示
     │
     ▼
【Step 7】generate_report → 汇总所有题目的评分
     统一展示综合评分 + 分维度评分 + 逐题详情(题目/回答/评分/点评) + 亮点(2-3条) + 短板(2-3条) + 提升方案
     综合评分 + 分维度评分 + 亮点(2-3条) + 短板(2-3条) + 提升方案
```

## UML 图
> 📊 **在线查看**: [点击打开PlantUML在线编辑器](https://www.processon.com/plantuml?editor_content=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)

> 描述 lingxi-hr 服务的核心领域模型和依赖关系。

## 时序图

> 描述 lingxi-hr 服务核心交互的调用时序。

### 1. Offer 确认 — Headcount 预冻结转正确认时序
> 📊 **在线查看**: [点击打开PlantUML在线编辑器](https://www.processon.com/plantuml?editor_content=19SPgd1E23hbAlkfjIN9IOPOxh8ZtBpbffwggN34HBfbsPWfNsl7V9TWrT6DGnaho07oteLgSo4dIqLm2PaYXRVvVi4NxDbMLRdPLeoy58TdnLNmbLR2UDFDIC2TQ9dTM2H8AfUid3C7lJQ1cYEGx6dJ1Aeje60L2T6JJFlCRwtVyVkC1Tw1RYSNmqIxx22bdhHSzLZkUF5mL02XSXKdZK5WvHjfB4GHjoljljxEuxsENlqsk1u8v38ZkfLrmVXSCn38U8QpKtdfYTqSJXVKpI9yLamFQ2C0eYx2a95Wq9ukw3RbycCu5FIjUOIKPJz0x0I33SqhF34N1Q3rLF2voAtAJXfttcFRGFToiqr7Kx7JL3TIA4gzajKESx5gtX1Lnmqk3OMSVIAoTKrW1fi3iupWbClN3UrE6TsztLjDElunY8hIGgRkF8w74kW3jAZNpTh62w2IcTwnUhKxtn2X0pgNb65yuJJegsDUQMK29Rrm8f4tf0KlJjnIG7LcY77LSQGfFNZkJz3rhc2047zJJRQIMWY9wCsz2ikxtDPeqGSIz4M4bhY1CfLzkJOUlbiAztzX4Gy5EFPOvti9c7ZcprHzmJhp57a0Grc7OeppQY05MRG5fXZhc0EaiLGRk8Z8JH0yJPJf1tNSvMlt8e5q06k8Q94gf53HFxxReCcoG1dcKtGk8ic2B8X5E40t8x2mV5u02OEgM4MOwBK0huuVaz9aXcovsZFYWV6gSeoLsOWSy6fAraMT1ba3ADfYHHpX1aM42PljxLXSUgYIApy2JTBd8Q9CN3LdyhHrr8gZNYPa8GKg7Vu75ukIWJHtkdffdhhnP6XvBSnBHxyfC3683ZFGguURFg5jHntSMMQlKceqCsFoZ1sR4pJAWGuZk1SEJPwasyqgMnCqUCxu7sFXy7Oj571WGUvOWzDIbMJKHI6J1BdpmSlt0nWkrw11oSdAuYGdTF5MoB8GBn6RjYdiaBXqVKQKXmVwdvF44mjft2tJk69s0HTLY8mb1oSE5eUSHolWWVVYtOYiMUpuNXYTXUkQ9t04XPwiRQGsicrmn6JxyL9XgydODOttfcnzY0pzFd7knHkFAkCTO
)
```plain
@startuml
actor Candidate as c
participant "C端前端" as cfe
participant "lingxi-gateway" as gateway
participant "lingxi-hr" as hr
participant "lingxi-job" as job
participant "lingxi-resume" as resume
participant "RocketMQ" as mq
database "lingxi_hr DB" as hrdb
database "lingxi_job DB" as jobdb

c -> cfe : 点击"确认接受Offer"
cfe -> gateway : POST /api/v1/offers/{id}/accept
gateway -> hr : 转发请求
hr -> hrdb : SELECT hr_offer WHERE id=? FOR UPDATE
hrd --> hr : 返回Offer记录
hr -> hr : 校验 status==SENT
hr -> hr : offer_id 幂等校验
hr -> job : Feign: POST /internal/jobs/{jobId}/confirm-hc\n{ offerId }
job -> jobdb : UPDATE job_hc_reservation SET status='CONFIRMED'\nWHERE offer_id=? AND status='RESERVED'
job -> jobdb : UPDATE job_post SET reserved_hc=reserved_hc-1,\nconfirmed_hc=confirmed_hc+1 WHERE id=?
jobdb --> job : 返回影响行数
alt 确认成功
    job --> hr : 确认成功
    hr -> hrdb : UPDATE hr_offer SET status='ACCEPTED', accepted_at=NOW()
    hr -> resume : Feign: PUT /internal/applications/{id}/status?status=OFFERED
    hr -> mq : 发送Offer确认通知
    mq -> hr : 消费通知 → 写入 msg_notification 表
    hr --> gateway : { code:0, message:"Offer已确认" }
    gateway --> cfe : 成功响应
    cfe --> c : 展示"确认成功"
else 确认失败(预冻结已释放或已确认)
    job --> hr : 确认失败
    hr --> gateway : { code:40001, message:"岗位剩余名额不足，请联系HR处理" }
    gateway --> cfe : 失败响应
    cfe --> c : 展示错误提示
end
@enduml
```

### 2. 候选人拒绝 Offer — 释放预冻结 HC 时序

> 📊 **在线查看**: [点击打开PlantUML在线编辑器](https://www.processon.com/plantuml?editor_content=2hqVxHzxp6wilUpL8J78qusCzeiyllPLAEoK7kmHtqnE5pYsvY1hWkCjVyKGW2vSKTX2EEQlr1oL53Z0a0Uf6BTDVSg5chvdjbEXdhQVPkHLyasBwl0p0bJfiszTtvzKBi5xQ2dNaLUMbOo8Xm3xn1oJbGPm1MFcvKhMJleevBR1vk3z0YQGJOGkrd2XUwa65t04yBIxviMRLfR0WeHtKa1a5nKByo4gonnUHLmWoWfO2DQv2raadoggZjKyhaMaOBdhaiFTlHK1nheeFS8RE2Ia2w65sWzteKzZspAOdNtK14X1UYoKioEfvlnGeK8csgtUR7BojpC8PPlQ1JzEjA4z3aaijNcGbHIzPkk3MpEm1EwdBfsociAzxQzli6owVjaFU4zxVLAIr4LadddgybEPa2deOY7ueP16mh0WzcYVVcbLZkQXtJ5LaZMvV5dNZqENGAubQPeV56PL2cUIhij0lceKD6q6NoeEis9DZvlxp4FaqC1SdXTdByp6LpdyeEuqEG6eKatKVActVzuRZ6XHv3dKehKHj0prWOxvqZYI9lt28q6LuVx6oB5brFv498RqKSIby2gkSgNv5Dqm3CClVcT7dTXCSYwINHjAdRC83zREP2y6GbNEyNqNWpXvExn1MYALusnAbDdAl7b5dDXuc8T7holqpN0lnLftCnPXT5ifsr72lzk1PKT4ltZibSgUoS72vsCQV3H5JAT2hRgQ0DloaMkKOrnoI8gogi8s0vSOjk9srA1gwjQt72Fx6byRu)
```plain
@startuml
actor Candidate as c
participant "C端前端" as cfe
participant "lingxi-gateway" as gateway
participant "lingxi-hr" as hr
participant "lingxi-job" as job
participant "lingxi-resume" as resume
participant "RocketMQ" as mq
database "lingxi_hr DB" as hrdb

c -> cfe : 点击"拒绝Offer"
cfe -> gateway : POST /api/v1/offers/{id}/reject
gateway -> hr : 转发请求
hr -> hrdb : SELECT hr_offer WHERE id=? FOR UPDATE
hrd --> hr : 返回Offer记录
hr -> hr : 校验 status==SENT
hr -> hrdb : UPDATE hr_offer SET status='REJECTED', rejected_at=NOW(), reject_reason=?
hr -> job : Feign: POST /internal/jobs/{jobId}/release-hc\n{ offerId, reason:"REJECTED" }
hr -> resume : Feign: PUT /internal/applications/{id}/status?status=WITHDRAWN
hr -> mq : 发送Offer拒绝通知
mq -> hr : 消费通知 → 写入 msg_notification 表
hr --> gateway : { code:0, message:"已拒绝Offer" }
gateway --> cfe : 成功响应
cfe --> c : 展示"拒绝成功"
@enduml
```

### 3. 面试评估录入 + AI 反馈生成时序
> 📊 **在线查看**: [点击打开PlantUML在线编辑器](https://www.processon.com/plantuml?editor_content=ixYAJBSMzqtI6diI5e9OhokN1Wt8Yayk7u38LdS7yW1t7weDr7MSwhHtd5SqyUoOwOxbcc6c9sIz4MR0LAEW7aCJFp6HAO9Zh349V549VDRhBSNJECpRhA9vessHsbDrzRWRr9KGTPgUmcwH9OD9mUfHHFUtdPrkAMasJLxTGlNHT9UEs4wITdOr5dbuBgBEF9Wi6YPtsuvuOJDDqo6h4ugaGxQkJx4zTVJaaDv6xyvIhooty7GNxksmV6FW6CXSxFzgxSd7V7P0nP21t0DMJqJ2vYz9grGH1Y8tpi5E2xaPcFKV6roLJ0hbLFJbzB2xl2u0VuijocZYj2NaP6z78LoMus5cHWfNE6CkpqKRf7nGnTzbvNj0yOxGoph0muXUKAmlIWp8y3tWSNeCx6zwEKD0PU1bHovbUk0UQfyBngb1wac5PSSNoPIoVkVfu1CKw55nOo9WqLcUwHV07pguqe2tp21XrR17xUMnwLTFKnRJ2CIeCBeTu9mat0HD3A1HyMSCPHF91f1EzRIyfruWszeiWsTDGCxnv3xjnUs9tiTNOqqLAxiV9XWJct4aZajYlZTTqmDKby519SIC0L8aeosDSg0MN3U9Bi9dSPMuB2nNCekEvNd7PZ8bSUaU40tTaY8xa1ODzQlwBixNg9YqToSnL3MHOwhEqm9LLbaK4YNB4jq1NUuVS7QVzdE2E3CAjzfWbS7ttfsb9Zac1p1cv6C8XH80lZScTI0OTP0nfwoClSH0R6klsPpHkvVJBzTmVYUGWsBPpXIGdiqWDiw4MtP86IkwSa9t9zaBqcrwe5xhFM4VQmV0URbeYNd5gEiMxhbdkyDeoPubvgJxkEEXOWjQvIntwMdGuF8eujukbjntlzeg0DYDlLknBMU9582nvLzt5IkAdrTTzkPu3RES0TBlw44JcdjvD3ER3qzUNMBIAwRzV65oTj8X38oPPE2ShcRKaMHPCU)
```plain
@startuml
actor HR as hr_user
participant "B端前端" as bfe
participant "lingxi-gateway" as gateway
participant "lingxi-hr" as hr
participant "LLM服务" as llm
participant "lingxi-resume" as resume
participant "RocketMQ" as mq
database "lingxi_hr DB" as hrdb

hr_user -> bfe : 填写面试评估表单 → 提交
bfe -> gateway : PUT /api/v1/interviews/{id}/evaluation
gateway -> hr : 转发请求
hr -> hr : 校验必填项(结论/4维度评分/评语≥20字)
hr -> hrdb : INSERT INTO hr_interview_evaluation
hrd --> hr : 保存成功
hr -> llm : 请求AI生成反馈
note right: 淘汰→落选原因+提升建议\n通过→录用建议+流程指引
llm --> hr : 返回AI反馈内容
hr -> hrdb : UPDATE hr_interview_evaluation SET feedback=?
alt 结论=通过
    hr -> hrdb : UPDATE hr_interview SET status='COMPLETED'
    hr -> mq : 发送面试结果通知
    mq -> hr : 消费通知 → "恭喜您已通过面试"
else 结论=淘汰
    hr -> hrdb : UPDATE hr_interview SET status='COMPLETED'
    hr -> resume : Feign: PUT /internal/applications/{id}/status?status=REJECTED
    hr -> mq : 发送面试结果通知
    mq -> hr : 消费通知 → "查看落选反馈"
end
hr --> gateway : { code:0, data:{ evaluationId, feedback } }
gateway --> bfe : 返回评估结果
bfe --> hr_user : 展示评估提交成功
@enduml
```

### 4. 消息沟通时序
> 📊 **在线查看**: [点击打开PlantUML在线编辑器](https://www.processon.com/plantuml?editor_content=12DBhVdHvJWwK3BdAwic9e8jg820KGPWlfdRKvo50GChFoSJqJ8DZI7uE35pEB2VZIQz5W8BQ1rYuFq8YIOgCLO4YV26sgRvzUk7KegPWYE4oXIo9rLkqMSxPFaaqa7fWSkhVfchvij800rlTbUs0bhiNOTc40Yy6C8x30V0tvbFCvReYWS1YDV6hGiEwex1RFocryISOQdJZZIaZZ2Vb2w3EEMDTrPEWemNg3sPzTG0IryFCDyGsnpOfkRxAm5sorJ4aw1mqKT9GGSl3baMPUdVZSshZ2Y4nHtVrpbblW27w4SsppwdfJHEGqpmVqIE4EMifiUBrsQEIVNG8rhRjk96svvYEcfrtbxCRKmAzUHhFlH2AUNVZMe5nnY58X8iNRvmtQwj4jwQB5Ug4tSA3TDsP05GqAO3nelgqOpbHJAzyEUDhVb9WfNi2wcgpmpsqucWlXAV2k59eZRn90WQ4k4h76WpX4yXnL0p5PJXqOM4DrLsAO5Rw7k2jo5mnLGWW6MhF4FF84E7v0DlElEBVKNwA8LqJNu9xkpnwsQLeKz5ad1QalfmcADNQzmDBG0ACQ0qSi1fPe1CZGpKRT2BuMzYJPqV5dqIJptr30erFLjkB6tBjIV1M5WhHMdpkyoYxlQTXc5WIqQc0HhjtJ661KTAzHtWJRTHZMVgulepv8YrfyJp5ld6EqedJRt5v7R2lIv0jSTKMwalQuc32gTVCXN4ZfoyHOqbvef3gtRIpz8259bQggk72EBXGaaNAZtwD9n3nczcl053GEtHgJ2HzwWn)

```plain
@startuml
actor HR as hr_user
participant "B端前端" as bfe
participant "lingxi-gateway" as gateway
participant "lingxi-hr" as hr
participant "WebSocket" as ws
database "lingxi_hr DB" as hrdb

hr_user -> bfe : 进入消息沟通页面
bfe -> gateway : GET /api/v1/conversations?page=1&size=20
gateway -> hr : 转发请求
hr -> hrdb : SELECT * FROM conversation WHERE hr_id=? ORDER BY last_message_at DESC
hrdb --> hr : 返回会话列表
hr --> bfe : 会话列表数据

hr_user -> bfe : 点击某个会话
bfe -> gateway : GET /api/v1/conversations/{id}/messages?page=1&size=50
gateway -> hr : 转发请求
hr -> hrdb : SELECT * FROM message WHERE conversation_id=? ORDER BY created_at
hr -> hrdb : UPDATE message SET is_read=1 WHERE conversation_id=? AND sender_role='CANDIDATE'
hrdb --> hr : 返回消息列表
hr --> bfe : 消息列表数据 + 标记已读

hr_user -> bfe : 输入消息 → 点击发送
bfe -> gateway : POST /api/v1/conversations/{id}/messages
gateway -> hr : 转发请求
hr -> hrdb : INSERT INTO message (conversation_id, sender_id, sender_role, content_type, content)
hr -> hrdb : UPDATE conversation SET last_message=?, last_message_at=NOW()
hr -> ws : 推送新消息给求职者
hr --> bfe : 发送成功
@enduml
```

### 5. 定时任务 — Offer 过期处理时序
> 📊 **在线查看**: [点击打开PlantUML在线编辑器](https://www.processon.com/plantuml?editor_content=42pBzj42MgrJRJkMRhehECRl2Rr23Jn2jeZpR3Y1kM6X3Z5grU2yLxvpSW1wgYXFRs6Tl6ZVJNhjqOMWiN9YM5XA9ibMA57BPHISU1Yk47rXqsNDcXiSG8iJ4qDQCGrZ7QnFAEfS4VHlUDrLxMPcIP9LKhGu4fSme0kmpR2iMMt3LiNwOCSE2l77XLmpLd8iZfD5wcy6C6DYkXJjio8YxHdJ92iweaNvWFxHONsFDO1YCSy96bn0wFdVRvXHlPlU44CF8sYzxyXvv15VdQVUPxRk1AJLq0fbgztVLMHbe3urbObMnTsP8D5NTX7OvjxFG51Z7RXJHtqS3TiQOEscOTLeBuv78ULzW9MRUlz8vdlsjVAZlbcIVR2mAznvvo1xC5rZSzTHc0x1lFoDnbXBd2Ip89siAK0jRU27A6BVSoJ9ZueclVbneghJB2MRszbf7WEBOAS9KXssEgt7XmW9K54GuwuArLVhe7lJuaSvcutybmYAf280K5vkKLtE0Q7O6kC8FAeC0tRxHbmTqCz0zK75qpAZLDsIf6ezTsdE8jVKyEbhhuPV2rSPurqHsz3O1csC28e2ceDk5AxgOIh88yWRwCTZORafJyiYISir0kZgxGXJ1SI0PkxzkKVoMhKhaLsgVko5wdYAX7xy7nrA)

```plain
@startuml
participant "XXL-Job Scheduler" as scheduler
participant "lingxi-hr" as hr
participant "RocketMQ" as mq
database "lingxi_hr DB" as hrdb

scheduler -> hr : 每小时触发 OfferExpireTask
hr -> hrdb : SELECT * FROM hr_offer WHERE status='SENT' AND expires_at <= NOW()
hrdb --> hr : 返回过期Offer列表
loop 每条过期Offer
    hr -> hrdb : UPDATE hr_offer SET status='EXPIRED', expired_at=NOW()\nWHERE id=? AND status='SENT'
    alt 条件更新成功
        hr -> mq : 发送Offer过期通知
        mq -> hr : 消费通知 → 通知候选人 + 通知HR
    else 条件更新失败(已被确认/拒绝)
        note right: 跳过，Offer已被处理
    end
end
hr --> scheduler : 任务执行完成
@enduml
```

## 数据库设计

> lingxi_hr 数据库涉及的核心数据表。

### 面试记录表 `hr_interview`

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
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
| `status` | VARCHAR(20) | **是** | PENDING | 面试状态：PENDING=待面试 CONFIRMED=已确认 IN_PROGRESS=进行中 COMPLETED=已完成 CANCELLED=已取消 |
| `created_at` | DATETIME | **是** | NOW() | 创建时间 |
| `updated_at` | DATETIME | 否 | NOW() ON UPDATE | 更新时间 |

**索引**：
| 索引名 | 类型 | 字段 |
|--------|------|------|
| `PRIMARY` | 主键 | `id` |
| `idx_company_id` | 普通 | `company_id` |
| `idx_application_id` | 普通 | `application_id` |
| `idx_interviewer_id` | 普通 | `interviewer_id`, `status` |
| `idx_candidate_id` | 普通 | `candidate_id` |
| `idx_job_id` | 普通 | `job_id` |
| `idx_scheduled_at` | 普通 | `scheduled_at` |
| `idx_company_status` | 普通 | `company_id`, `status` |

---

### 面试评估表 `hr_interview_evaluation`

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
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
|--------|------|------|
| `PRIMARY` | 主键 | `id` |
| `uk_interview_id` | 唯一 | `interview_id` |
| `idx_evaluator_id` | 普通 | `evaluator_id` |
| `idx_conclusion` | 普通 | `conclusion` |

---

### Offer 表 `hr_offer`

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | Offer ID |
| `company_id` | BIGINT UNSIGNED | **是** | - | 企业ID（数据隔离 + 统计查询） |
| `application_id` | BIGINT UNSIGNED | **是** | - | 投递记录ID（关联 lingxi_resume.job_application） |
| `candidate_id` | BIGINT UNSIGNED | **是** | - | 候选人用户ID |
| `job_id` | BIGINT UNSIGNED | **是** | - | 岗位ID（关联 lingxi_job.job_post） |
| `salary` | INT | **是** | - | 月薪（元） |
| `entry_date` | DATE | **是** | - | 预计入职日期 |
| `level` | VARCHAR(16) | 否 | - | 职级（如 P6、高级工程师） |
| `remark` | VARCHAR(500) | 否 | - | 备注 |
| `status` | VARCHAR(16) | **是** | SENT | Offer状态：SENT=待确认 ACCEPTED=已确认 REJECTED=已拒绝 EXPIRED=已过期 |
| `expires_at` | DATETIME | **是** | - | Offer有效期截止时间 |
| `accepted_at` | DATETIME | 否 | - | 确认时间 |
| `rejected_at` | DATETIME | 否 | - | 拒绝时间 |
| `reject_reason` | VARCHAR(500) | 否 | - | 拒绝原因（候选人填写，选填） |
| `urge_count` | TINYINT UNSIGNED | **是** | 0 | 催促次数（24h内最多2次） |
| `last_urge_at` | DATETIME | 否 | - | 最近一次催促时间 |
| `created_at` | DATETIME | **是** | NOW() | 发起时间 |
| `updated_at` | DATETIME | 否 | NOW() ON UPDATE | 更新时间 |

**索引**：
| 索引名 | 类型 | 字段 |
|--------|------|------|
| `PRIMARY` | 主键 | `id` |
| `uk_application_id` | 唯一 | `application_id` |
| `idx_company_id` | 普通 | `company_id` |
| `idx_candidate_id` | 普通 | `candidate_id` |
| `idx_job_id` | 普通 | `job_id` |
| `idx_status_expires` | 普通 | `status`, `expires_at` |
| `idx_company_status` | 普通 | `company_id`, `status` |

---

### 通知表 `msg_notification`

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | 通知ID |
| `user_id` | BIGINT UNSIGNED | **是** | - | 接收用户ID |
| `type` | VARCHAR(32) | **是** | - | 通知类型：APPLICATION=投递通知 INTERVIEW=面试提醒 OFFER=Offer通知 HC_WARNING=HC告警 EVAL_TIMEOUT=评估超时 JOB_REFRESH=岗位刷新建议 SYSTEM=系统通知 MATCH_RECOMMEND=高匹配岗位推荐 |
| `title` | VARCHAR(128) | **是** | - | 通知标题 |
| `content` | VARCHAR(500) | **是** | - | 通知内容 |
| `target_type` | VARCHAR(32) | 否 | - | 关联目标类型：application/interview/offer/job/conversation |
| `target_id` | BIGINT UNSIGNED | 否 | - | 关联目标ID |
| `is_read` | TINYINT | **是** | 0 | 是否已读：0=未读 1=已读 |
| `created_at` | DATETIME | **是** | NOW() | 通知时间 |

**索引**：
| 索引名 | 类型 | 字段 |
|--------|------|------|
| `PRIMARY` | 主键 | `id` |
| `idx_user_read` | 普通 | `user_id`, `is_read` |
| `idx_user_created` | 普通 | ``user_id`, `created_at` DESC` |
| `idx_type` | 普通 | `type` |

---

### 会话表 `msg_conversation`

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | 会话ID |
| `company_id` | BIGINT UNSIGNED | **是** | - | 企业ID |
| `candidate_id` | BIGINT UNSIGNED | **是** | - | 求职者用户ID |
| `hr_id` | BIGINT UNSIGNED | **是** | - | HR用户ID |
| `application_id` | BIGINT UNSIGNED | 否 | - | 关联投递记录ID（咨询HR入口创建时可能为空） |
| `last_message_preview` | VARCHAR(100) | 否 | - | 最后一条消息摘要（前50字） |
| `last_message_at` | DATETIME | 否 | - | 最后消息时间 |
| `candidate_unread` | INT | **是** | 0 | 求职者未读数 |
| `hr_unread` | INT | **是** | 0 | HR未读数 |
| `created_at` | DATETIME | **是** | NOW() | 创建时间 |
| `updated_at` | DATETIME | 否 | NOW() ON UPDATE | 更新时间 |

**索引**：
| 索引名 | 类型 | 字段 |
|--------|------|------|
| `PRIMARY` | 主键 | `id` |
| `uk_company_candidate_hr` | 唯一 | ``company_id`, `candidate_id`, `hr_id`` |
| `idx_company_hr` | 普通 | ``company_id`, `hr_id`, `last_message_at`` |
| `idx_candidate_id` | 普通 | `candidate_id` |

---

### 消息表 `msg_message`

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | 消息ID |
| `conversation_id` | BIGINT UNSIGNED | **是** | - | 会话ID |
| `sender_id` | BIGINT UNSIGNED | **是** | - | 发送者用户ID |
| `sender_role` | VARCHAR(16) | **是** | - | 发送者角色：CANDIDATE=求职者 HR=HR |
| `content_type` | VARCHAR(32) | **是** | TEXT | 消息类型：TEXT=文字 CARD_INTERVIEW=面试邀约卡片 CARD_RESUME=简历卡片 CARD_JD=岗位JD卡片 |
| `content` | TEXT | **是** | - | 消息内容（TEXT=文字，卡片类型=JSON） |
| `is_read` | TINYINT | **是** | 0 | 是否已读：0=未读 1=已读 |
| `created_at` | DATETIME | **是** | NOW() | 发送时间 |

**索引**：
| 索引名 | 类型 | 字段 |
|--------|------|------|
| `PRIMARY` | 主键 | `id` |
| `idx_conversation_time` | 普通 | `conversation_id`, `created_at` |
| `idx_sender_id` | 普通 | `sender_id` |
| `idx_conversation_unread` | 普通 | ``conversation_id`, `sender_role`, `is_read`` |

---

### 题库表 `job_question`

> 题库由成员 B（lingxi-job）维护，成员 D（lingxi-hr）通过 Feign 调用获取题目数据，Mock Interview Agent 出题也通过 Feign 查询。以下保留字段说明供接口设计参考。

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | 企业题目ID |
| `company_id` | BIGINT UNSIGNED | **是** | - | 企业ID（题库隔离） |
| `job_type` | VARCHAR(64) | **是** | - | 通用岗位类型 |
| `skill_tags` | JSON | 否 | - | 标准化技能标签数组 |
| `question_type` | VARCHAR(24) | **是** | - | 题目类型：BASIC/PROJECT/BOUNDARY/COMPREHENSIVE |
| `difficulty` | VARCHAR(16) | **是** | MEDIUM | 难度：EASY/MEDIUM/HARD |
| `content` | VARCHAR(2000) | **是** | - | 题目内容 |
| `content_sha256` | CHAR(64) | **是** | - | 标准化题干SHA-256（企业内精确去重） |
| `key_points` | VARCHAR(1000) | 否 | - | 考察要点 |
| `reference_answer` | VARCHAR(4000) | 否 | - | 参考答案 |
| `evaluation_points` | JSON | 否 | - | 结构化评分要点 |
| `source` | VARCHAR(20) | **是** | DEMO_SEED | 来源：DEMO_SEED/HR_CREATED/AI_GENERATED |
| `status` | VARCHAR(20) | **是** | ACTIVE | 状态：PENDING_REVIEW=待审核 ACTIVE=已启用 INACTIVE=已停用 |
| `version` | INT UNSIGNED | **是** | 0 | 乐观锁版本号 |
| `created_by` | BIGINT UNSIGNED | 否 | - | 创建人用户ID |
| `created_at` | DATETIME | **是** | NOW() | 创建时间 |
| `updated_at` | DATETIME | 否 | NOW() ON UPDATE | 更新时间 |
| `deleted_at` | DATETIME | 否 | - | 逻辑删除时间 |

**索引**：
| 索引名 | 类型 | 字段 |
|--------|------|------|
| `PRIMARY` | 主键 | `id` |
| `uk_question_content` | 唯一 | `company_id`, `content_sha256` |
| `idx_question_search` | 普通 | `company_id`, `status`, `job_type`, `question_type`, `difficulty`, `deleted_at` |

---

### 企业成员表 `hr_company_member`

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
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
|--------|------|------|
| `PRIMARY` | 主键 | `id` |
| `uk_company_user` | 唯一 | `company_id`, `user_id` |
| `idx_user_id` | 普通 | `user_id` |
| `idx_role` | 普通 | `company_id`, `role` |
| `idx_status` | 普通 | `status` |

---

### 企业信息表 `hr_company`

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
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
|--------|------|------|
| `PRIMARY` | 主键 | `id` |
| `uk_name` | 唯一 | `name` |
| `uk_invite_code` | 唯一 | `invite_code` |
| `idx_cert_status` | 普通 | `cert_status` |
| `idx_status` | 普通 | `status` |
| `idx_industry` | 普通 | `industry` |

---

### 企业认证申请表 `hr_company_certification`

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
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
|--------|------|------|
| `PRIMARY` | 主键 | `id` |
| `idx_company_id` | 普通 | `company_id` |
| `idx_applicant_id` | 普通 | `applicant_id` |
| `idx_status` | 普通 | `status` |
| `idx_created_at` | 普通 | `created_at` |

---

### 用户通知偏好表 `user_notification_preference`

> 此表当前仅存在于系分文档，需补充到 schema.sql（建议放入 sys_ 模块）。在 RocketMQ 消费者写入通知前，查询此表判断用户是否关闭了对应类型的通知。

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | 主键ID |
| `user_id` | BIGINT UNSIGNED | **是** | - | 用户ID |
| `email_enabled` | TINYINT | **是** | 1 | 邮件通知：0=关闭 1=开启 |
| `sms_enabled` | TINYINT | **是** | 0 | 短信通知：0=关闭 1=开启 |
| `browser_push_enabled` | TINYINT | **是** | 1 | 浏览器推送：0=关闭 1=开启 |
| `match_notification` | TINYINT | **是** | 1 | 高匹配岗位推送（C端专用）：0=关闭 1=开启 |
| `created_at` | DATETIME | **是** | NOW() | 创建时间 |
| `updated_at` | DATETIME | 否 | NOW() ON UPDATE | 更新时间 |

**索引**：
| 索引名 | 类型 | 字段 |
|--------|------|------|
| `PRIMARY` | 主键 | `id` |
| `uk_user_id` | 唯一 | `user_id` |

---

### 操作日志表 `admin_operation_log`

> 此表归属成员 E（admin_ 模块），记录全平台操作审计。lingxi-hr 调用 admin 模块 Feign 接口写入操作日志。

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | 日志ID |
| `company_id` | BIGINT UNSIGNED | 否 | - | 企业ID（企业级操作时记录） |
| `user_id` | BIGINT UNSIGNED | **是** | - | 操作用户ID |
| `op_type` | VARCHAR(32) | **是** | - | 操作类型：CREATE_JOB=创建岗位 PUBLISH_JOB=发布岗位 SEND_OFFER=发送Offer INTERVIEW_EVALUATE=面试评估 LOGIN=登录 INVITE_MEMBER=邀请成员 UPDATE_COMPANY=修改企业信息 |
| `detail` | VARCHAR(500) | **是** | - | 操作详情 |
| `ip_address` | VARCHAR(45) | 否 | - | 操作IP（支持IPv6） |
| `created_at` | DATETIME | **是** | NOW() | 操作时间 |

**索引**：
| 索引名 | 类型 | 字段 |
|--------|------|------|
| `PRIMARY` | 主键 | `id` |
| `idx_user_id` | 普通 | `user_id`, `created_at` |
| `idx_company_id` | 普通 | `company_id`, `created_at` |
| `idx_op_type` | 普通 | `op_type` |

---

### 模拟面试会话表 `mock_session`

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `id` | BIGINT UNSIGNED | **是** | AUTO_INCREMENT | 主键ID |
| `session_id` | VARCHAR(32) | **是** | - | 会话唯一标识（如 mock-20260729-001） |
| `candidate_id` | BIGINT UNSIGNED | **是** | - | 求职者用户ID |
| `job_id` | BIGINT UNSIGNED | 否 | - | 目标岗位ID（通过 Feign 获取 lingxi_job.job_post 信息） |
| `job_title` | VARCHAR(128) | 否 | - | 目标岗位名称（冗余，便于历史查询；岗位可能被下线） |
| `total_questions` | TINYINT | **是** | 5 | 总题数：5 或 8 |
| `overall_score` | DECIMAL(5,2) | 否 | - | 综合评分(0-100)，完成后计算 |
| `status` | VARCHAR(20) | **是** | IN_PROGRESS | 状态：IN_PROGRESS=进行中 COMPLETED=已完成 |
| `started_at` | DATETIME | **是** | NOW() | 开始时间 |
| `completed_at` | DATETIME | 否 | - | 完成时间 |

**索引**：
| 索引名 | 类型 | 字段 |
|--------|------|------|
| `PRIMARY` | 主键 | `id` |
| `uk_session_id` | 唯一 | `session_id` |
| `idx_candidate_id` | 普通 | `candidate_id`, `status` |

---

### 模拟面试答题记录表 `mock_answer`

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
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
| `project_experience_score` | DECIMAL(5,2) | 否 | - | 项目经验评分(0-100) |
| `overall_score` | DECIMAL(5,2) | 否 | - | 本题综合评分(0-100) |
| `ai_comment` | VARCHAR(500) | 否 | - | AI 点评 |
| `answered_at` | DATETIME | 否 | - | 作答时间 |
| `created_at` | DATETIME | **是** | NOW() | 创建时间 |

**索引**：
| 索引名 | 类型 | 字段 |
|--------|------|------|
| `PRIMARY` | 主键 | `id` |
| `idx_session_id` | 普通 | `session_id`, `question_number` |
| `idx_question_type` | 普通 | `question_type` |

---

### 模拟面试报告表 `mock_report`

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
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
|--------|------|------|
| `PRIMARY` | 主键 | `id` |
| `uk_session_id` | 唯一 | `session_id` |
| `idx_candidate_id` | 普通 | `candidate_id` |

---

### 数据库实体关系总览-ER图
> 📊 **在线查看**: [点击打开PlantUML在线编辑器](https://www.processon.com/plantuml?editor_content=30Od860i9a0Mp49axvhDm8haXxGK9O3IDUUYbcYXNuSGepXcmSxqHvfjCU8wHnmMrpHbN4J1v7iS2Zk8FxRYozFaadpH2X1mUkZiJ508c9YvC96IWVVY7rrAw6VXuU4VSkpBf6I5GYQtThYiJ8ToUQs6ozHkp0mCPbKnTra2jq7KshwS53rkeus2r7Mx5E6dXPfyF5YYCGydQBm8tL77VDtVGh9yxqFwtw3sSwWxb1inxsqr8Bs3c124ygoQIkCNPRO0Wu9hGq0egh6s2vDrTnGsvoyEEL8xzb1N7bToYG6VOwrQEtWGVya5Av4pboTUAMARQg8EIMgGNL1Prv0ewusDir2UeeMTeuKvk03shrPa9sVuA8WzqT9hYFGfIQzYOQIyY5rsHzU3308py4gvcswz6WZ6MZu4TIhhJMDtdJCwvhzzezUg8w02xxYCFmVzc6Z7pqwjBykwlpGrDgmnaLcusyU867DCvutP54GbGTJJ894tiGAfl8XFbyBb5UbKaKsyP4PQdu3YScXZUG2HCGMwWlYDyipUmYc4lnZ54QBeAOOKvMH5zoB9M0OAPTSTpW8LAmcvjEmSUOW15VAsioaacJQLPL2kPEf61FpAJpxMN2wcO3Tzj4QIOIuuKC3l2Rs7zbl5jFeckXkFIBLncX8TgsciKAJivXju1xtIQxZfmrZxtSQfVt9IIfWYJzbLajNixutomOTMBRmAPPjINQo3Nor9a4XgzEbVkfAcxIoOxy1typHnjwl6zGKeV1avW0GEyo5tpTXCr7qwyxEk9wzD0XhsTu63wlebK5iLENBFmvaaBgeUZ6xnopU5C9t8tcUwjf6vIQqshrUQMC8LstcyxW9PRZGHp9Q7coa6vhoTxNsgcoc20q3az48YdFycZsomjfqmduxe44xf4IWG7UAdGxBCZ3qofv8vepvkr3AhZaGfc1l3NOaI0oJaxtWTtUIcq7o9y9Vm6p4KO5gmDA6ZB3FqvgBpDDXfiUJBVV7LkcBj8Ctei4wc8AxtiGDgvnWJlaTlnySnyiRbQS1lz1ApdSsKQfvAf2rAl85AOeKwupB0QFEHB3tdIBqSzM26vfUFhud9Zl8FOE84i78CZdWez8Z3EgnFCog0eu3KuwLwvsaFUwY3WmfeU1sVq3botLEZ8tKJmCgZ9D2enLpZ2Rhs6rdfjPeTi74aTWOYIb8Perf9RjsY5lJo93KUCA8FiLs3R2G0304ddLvrscboofQeFhbHDICmfr4yVy1q7a096DdzdcXkU586lBGD2n57aHQMQOeXqhplUdfWDgn55fIu5ghS8bS2Mc3plOql63A0YoQQOaUttkcsqZCMEzn4szvZrhfFrO0gdLuUQd5C1XfYbV9ynEXEXLO6aHvh2QG29lWPEKCAX1WP3KH0PptVhkdHRkFroFqwbnILpH0m9J4nkMVTZB8baLTc96puD3IH5Cb9OQ5bGqwSlSf8mjxAV5Pg89jJmmHyA5R0YNNt0GISPSzfrz2TAgmNy4rkjgdMwiobS2qaSgAuvyjfYBt3vAplSQiTU7KdlMkkZCimKz6SL54T9vP1MmIY9QasS88EzZ8c4vRviVgxlarmNID6npzLxvP5OOh2VmJN4UzHAfmPrfjJJFbIvIL9uMWAB0icms0uL8KghBYMzm6YnSr5LxGc0QkEJQl8mpJtCpjja0fX3r405ymR2hPkYCPOgkO4LbSnVZaAhwN3lss9KpwB9FdR5A3ntKx66DwaO4yFGeOm6veIuRK6EOR5b1g0aFKE9SkuJSEzZxCpDlCuWJakmOaLAIvokLCiHwwl9EpYRLUDkvVFF4T4IR7xLVqMygYXxACkkBo3GwR0bvv9oBiVRz3XM93tVr4F1SMqVJ7DLW6bhEJKrLt45xduJXwpIrxEpaN6gjSgwmslI7kEWyWZEJSDmAvIXLNNapKEbnhUn8a4C1FxeAZ0QC00FtxGRuWwZetRxaQ87rsKKHr7hKmCl8UCNUyc4qb64kVN6Y2oNYUHZEUOh5DvPivxxXkLmAuZAQZZ23h6CpsN39jaDI7nXz8ZMoNa6kCxKqGmOfPpEeSEHDdhpXfCrbLKtYGqhkG33ygOmCNpvvEeSIVLKAa4KCcScaCe31rhHm33KUdE8roJLfSWgfylBAqeiGiRVPqteSgQV5SA2QwfhjPdbVkh4oURvdR6bhUJSnHG6IuhDgK1g2dO7QoDLnrKbO8sb7w71SKD6n28JuRElB463FQ361OxrauaEPdWIlfm9tazxSZjipGZHj5OIDtXDRFoiylGeqHT0KFky520rlcNEhP8CJuJv2eSKByxE6iV7sWvPzeI8FlC6XUooJCRZQ0OBt3PUal2jEST0oi3d48bawZ8TZ5gpoawbtGDUH8Js9JJjezDpkB01qEhxbaXiMcuI0aLPrb99Ms7IVAlnomJ7zGBuNqzsQ4UAa8sTuKkanHlaM5TO8v939gTlbeOU2NHbGrh2RJS0Imd7OtW7bei1eCvCqKkQDWPJcsCZq7iOU8fffilldkXtBFdy79RAr9IFCoMRzDio4QwBe4oqeUqyiDXz0eJvbLMtrOju47aDTmpbCInSkKVpcIgQGfrfIsHVsryj89V5L5xh51GrPgvcjiCvwPi6rZxcoZ8M9pzry6uIoV8935OJ0KhdI0MlwYQ3jKNS5BfEX9FhJ2DUs6WtRnNamVU6OlgItbTbVS9yrC1cc4sp2t8IEmHkWOFkq1uobgESCKFzYR1D7wMd6dc0zyk0qV8AEXEmG0LF0Cwxbtzs0KJZgmLLMCMECy2BUD1Vz7tWRJyJJY1rzD4aqclKTKneSJnRNvuv8zuQ9YZOb)

> **ER 图说明**：
> - `job_question`（题库）归属成员 B（lingxi-job），mock interview 通过 Feign 调用获取题目
> - `admin_operation_log`（操作日志）归属成员 E（lingxi-admin），lingxi-hr 通过 Feign 写入
> - `user_notification_preference`（用户通知偏好）当前仅存在于系分文档，需补充到 schema.sql 放入 sys_ 模块
> - `mock_report` 在面试完成后一次性生成，与 mock_session 一对一

## 跨服务 Feign 接口定义

> lingxi-hr 需要调用其他微服务的内部接口，以下为接口契约。

### 1. 调用 lingxi-job（成员 B）

#### 1.1 预冻结 Headcount（HR 发起 Offer 时调用）

```plain
POST /internal/jobs/{jobId}/reserve-hc
```

| **参数** | **类型** | **必填** | **描述**                              |
| -------- | -------- | :------: | ------------------------------------- |
| jobId    | Long     |    是    | 岗位ID（路径参数）                    |
| offerId  | Long     |    是    | Offer ID（请求体，用于幂等校验，唯一约束 uk_hc_offer） |

**响应格式**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "reservationId": 1,
    "status": "RESERVED"
  }
}
```

> **说明**：预冻结失败（HC 不足）时返回 `code=40001`，lingxi-hr 不创建 Offer 记录，提示 HR "岗位 HC 不足，无法发起 Offer"。lingxi-job 内部：`UPDATE job_post SET reserved_hc=reserved_hc+1 WHERE id=? AND total_hc - confirmed_hc - reserved_hc > 0`，同时写入 `job_hc_reservation` 表（status=RESERVED）。

#### 1.2 确认占用 Headcount（候选人接受 Offer 时调用）

```plain
POST /internal/jobs/{jobId}/confirm-hc
```

| **参数** | **类型** | **必填** | **描述**                              |
| -------- | -------- | :------: | ------------------------------------- |
| jobId    | Long     |    是    | 岗位ID（路径参数）                    |
| offerId  | Long     |    是    | Offer ID（请求体，用于定位预冻结记录） |

**响应格式**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "remainingVacancy": 2,
    "jobClosed": false
  }
}
```

> **说明**：确认失败时返回 `code=40001`。lingxi-job 内部条件更新：`UPDATE job_hc_reservation SET status='CONFIRMED' WHERE offer_id=? AND status='RESERVED'`，同时 `job_post.reserved_hc--, confirmed_hc++`。利用 `uk_hc_offer` 唯一约束保证幂等。

#### 1.3 释放预冻结 Headcount（候选人拒绝 Offer / Offer 过期时调用）

```plain
POST /internal/jobs/{jobId}/release-hc
```

| **参数** | **类型** | **必填** | **描述**                              |
| -------- | -------- | :------: | ------------------------------------- |
| jobId    | Long     |    是    | 岗位ID（路径参数）                    |
| offerId  | Long     |    是    | Offer ID（请求体）                    |
| reason   | String   |    是    | 释放原因：REJECTED / EXPIRED / HR_WITHDRAW |

**响应格式**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "jobReactivated": false
  }
}
```

> **说明**：lingxi-job 内部条件更新：`UPDATE job_hc_reservation SET status='RELEASED', release_reason=? WHERE offer_id=? AND status='RESERVED'`，同时 `job_post.reserved_hc--`。若岗位此前因招满关闭且非人工锁定，释放后自动重新激活。

#### 1.4 获取岗位要求

```plain
GET /internal/jobs/{jobId}/requirements
```

| **参数** | **类型** | **必填** | **描述**           |
| -------- | -------- | :------: | ------------------ |
| jobId    | Long     |    是    | 岗位ID（路径参数） |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "jobId": 1001,
    "title": "高级前端工程师",
    "coreSkills": ["React(精通)", "TypeScript(熟练)", "前端工程化(熟练)"],
    "softSkills": ["架构设计能力(必须)", "团队管理(加分)"],
    "hiddenRequirements": ["5年以上经验", "性能优化实战经验"],
    "techStack": ["React", "TypeScript", "Node.js", "Webpack"]
  }
}
```

> **说明**：Mock Interview Agent 使用此接口获取岗位考察重点来生成面试题。

### 2. 调用 lingxi-resume（成员 C）

#### 2.1 获取候选人简历亮点

```plain
GET /internal/resumes/{resumeId}/highlights
```

| **参数**   | **类型** | **必填** | **描述**             |
| ---------- | -------- | :------: | -------------------- |
| resumeId   | Long     |    是    | 简历ID（路径参数）   |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "resumeId": 2001,
    "skillTags": ["React", "TypeScript", "Redux", "Webpack"],
    "projectHighlights": [
      {
        "projectName": "电商秒杀系统",
        "techStack": ["Redis", "Spring Boot", "Kafka"],
        "achievement": "QPS从500优化到5000"
      }
    ],
    "weakPoints": ["架构设计经验偏少"],
    "abilityModel": {
      "frontend": 85,
      "backend": 60,
      "architecture": 40,
      "softSkill": 75,
      "industry": 70,
      "complexity": 80,
      "growth": 72,
      "engineering": 78,
      "leadership": 35,
      "jobMatch": 0
    }
  }
}
```

> **说明**：Interview Agent 和 Mock Interview Agent 均依赖此接口获取候选人能力信息来生成定制题目。

#### 2.2 更新投递状态

```plain
PUT /internal/applications/{applicationId}/status
```

| **参数**        | **类型** | **必填** | **描述**           |
| --------------- | -------- | :------: | ------------------ |
| applicationId   | Long     |    是    | 投递记录ID（路径参数） |
| status          | String   |    是    | 目标状态（请求体） |
| reason          | String   |    否    | 变更原因（请求体） |

**响应格式**：
```json
{
  "code": 0,
  "message": "success"
}
```

> **说明**：面试淘汰时推送 REJECTED，Offer 确认时推送 OFFERED，Offer 拒绝时推送 WITHDRAWN。

## API 设计

API 前缀统一为 `/api/v1`，JSON 格式，统一响应结构 `{ code, message, data, timestamp }`。鉴权通过 `Authorization: Bearer <JWT>` 头部。

---

### 一、候选人管理

#### 1. 获取候选人列表

```plain
GET /api/v1/hr/candidates
```

**请求参数**：

| **参数**     | **类型** | **必填** | **描述**                                               |
| ------------ | -------- | :------: | ------------------------------------------------------ |
| jobId        | Long     |    否    | 按岗位筛选                                             |
| status       | String   |    否    | 按投递状态筛选：SUBMITTED/VIEWED/SCREENED/INTERVIEWING |
| minMatchScore| Integer  |    否    | 最低匹配度筛选（默认0）                                |
| keyword      | String   |    否    | 搜索关键词（候选人姓名/技能标签）                      |
| sortBy       | String   |    否    | 排序方式：matchScore(默认)/submittedAt/aiScore           |
| page         | Integer  |    否    | 页码，默认1                                            |
| size         | Integer  |    否    | 每页条数，默认20                                       |

**响应格式**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 156,
    "page": 1,
    "size": 20,
    "records": [
      {
        "applicationId": 5001,
        "candidateId": 3001,
        "candidateName": "李婷",
        "avatar": "https://cdn.example.com/avatars/3001.png",
        "jobId": 1001,
        "jobTitle": "高级前端工程师",
        "matchScore": 92,
        "aiScore": 85,
        "status": "SUBMITTED",
        "statusDesc": "已投递",
        "skillTags": ["React", "TypeScript", "Node.js"],
        "submittedAt": "2026-07-27T14:30:00"
      }
    ]
  }
}
```

> **说明**：仅返回投递本企业岗位的候选人。`candidateName`/`avatar` 通过 Feign 调 `lingxi_user.user` 获取，`jobTitle`/`skillTags` 通过 Feign 调 `lingxi_job.job_post` 和 `lingxi_resume.resume` 获取。Top5 高潜推荐接口单独提供。

---

#### 2. 获取 Top5 高潜推荐

```plain
GET /api/v1/hr/candidates/top
```

**请求参数**：

| **参数** | **类型** | **必填** | **描述** |
| -------- | -------- | :------: | -------- |
| jobId    | Long     |    否    | 按岗位筛选，不传则返回本企业所有岗位的Top5 |

**响应格式**：
```json
{
  "code": 0,
  "data": [
    {
      "rank": 1,
      "applicationId": 5001,
      "candidateName": "李婷",
      "matchScore": 92,
      "aiScore": 85,
      "advantages": [
        "React项目经验：3年实战，与岗位核心需求高度匹配",
        "TypeScript能力：工程化经验丰富，超出岗位基本要求",
        "团队协作：有跨部门协作项目经验"
      ],
      "risks": [
        "架构设计经验偏少，该岗要求有独立设计架构的能力"
      ],
      "actions": {
        "canMarkSuitable": true,
        "canInviteInterview": true,
        "canDismiss": true
      }
    }
  ]
}
```

> **说明**：匹配度 < 70% 的候选人不进入 Top5。Top5 按匹配度降序排列。用户点击"不感兴趣"后从推荐列表移除，作为匹配引擎负反馈。

---

#### 3. 标记候选人合适/不合适

```plain
PUT /api/v1/hr/candidates/{applicationId}/mark
```

**请求参数**：

| **参数**        | **类型** | **必填** | **描述**           |
| --------------- | -------- | :------: | ------------------ |
| applicationId   | Long     |    是    | 投递记录ID（路径参数） |
| action          | String   |    是    | 操作：SUITABLE / UNSUITABLE（请求体） |

**响应格式**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "applicationId": 5001,
    "newStatus": "SCREENED",
    "newStatusDesc": "筛选通过",
    "notificationSent": true
  }
}
```

> **说明**：标记"不合适"时自动触发 AI 生成落选反馈（落选原因 + 2-3 条提升建议），存入 `job_application.reject_reason`，并推送通知给求职者。标记操作需要二次确认弹窗。

---

### 二、面试协同

#### 4. 创建面试安排

```plain
POST /api/v1/hr/interviews
```

**请求参数**：

| **参数**        | **类型** | **必填** | **描述**                    |
| --------------- | -------- | :------: | --------------------------- |
| applicationId   | Long     |    是    | 投递记录ID                  |
| interviewerId   | Long     |    是    | 面试官用户ID                |
| scheduledAt     | DateTime |    是    | 预约面试时间（ISO 8601）    |
| method          | String   |    是    | 面试方式：OFFLINE/ONLINE/PHONE |
| location        | String   |    否    | 面试地点/视频链接           |
| remark          | String   |    否    | 面试备注（HR内部使用）      |
| candidateNote   | String   |    否    | 给候选人的留言              |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "interviewId": 6001,
    "status": "PENDING",
    "scheduledAt": "2026-07-30T14:00:00"
  }
}
```

> **说明**：创建面试后自动更新投递状态为 INTERVIEWING，推送面试邀约通知给求职者，推送面试安排通知给面试官。

---

#### 5. 获取面试列表（HR视角）

```plain
GET /api/v1/hr/interviews
```

**请求参数**：

| **参数**   | **类型** | **必填** | **描述**                                    |
| ---------- | -------- | :------: | ------------------------------------------- |
| status     | String   |    否    | 按状态筛选：PENDING/CONFIRMED/IN_PROGRESS/COMPLETED/CANCELLED |
| dateRange  | String   |    否    | 按时间筛选：TODAY/WEEK/MONTH（默认全部）    |
| jobId      | Long     |    否    | 按岗位筛选                                   |
| page       | Integer  |    否    | 页码，默认1                                  |
| size       | Integer  |    否    | 每页条数，默认20                             |

**响应格式**：
```json
{
  "code": 0,
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
        "remark": "重点关注架构设计能力",
        "candidateNote": "请携带作品集",
        "status": "CONFIRMED",
        "statusDesc": "已确认",
        "hasQuestions": false,
        "hasEvaluation": false
      }
    ]
  }
}
```

> **说明**：`candidateName`/`candidateAvatar` 通过 Feign 调 `lingxi_user.user` 获取，`jobTitle` 通过 Feign 调 `lingxi_job.job_post` 获取，`interviewerName` 通过 Feign 调 `lingxi_user.user` 获取。

---

#### 6. 录入面试评估

```plain
PUT /api/v1/hr/interviews/{interviewId}/evaluation
```

**请求参数**：

| **参数**           | **类型** | **必填** | **描述**                                    |
| ------------------ | -------- | :------: | ------------------------------------------- |
| interviewId        | Long     |    是    | 面试记录ID（路径参数）                       |
| conclusion         | String   |    是    | 面试结论：PASS / PENDING / REJECT            |
| techScore          | Integer  |    是    | 技术能力评分（1-5）                          |
| communicationScore | Integer  |    是    | 沟通表达评分（1-5）                          |
| matchScore         | Integer  |    是    | 岗位匹配评分（1-5）                          |
| potentialScore     | Integer  |    是    | 发展潜力评分（1-5）                          |
| comment            | String   |    是    | 面试评语（最少20字）                         |
| isDraft            | Boolean  |    否    | 是否保存为草稿（默认 false=正式提交）        |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "evaluationId": 7001,
    "conclusion": "PASS",
    "feedback": "候选人在前端技术方面表现扎实，React性能优化实战经验丰富，能清晰阐述优化前后数据对比。建议录用为高级前端工程师，试用期重点关注架构设计能力培养。",
    "applicationStatus": "OFFERABLE",
    "notificationSent": true
  }
}
```

> **说明**：提交时校验必填项（结论/4个维度评分/评语≥20字）。`isDraft=true` 时跳过必填项校验，仅保存草稿不触发后续流程。正式提交（`isDraft=false`）后自动调用 LLM 生成 AI 反馈：
> - 结论=PASS → 生成录用建议 + 更新投递状态为 OFFERABLE
> - 结论=PENDING → 投递状态保持 INTERVIEWING，通知 HR 安排复面
> - 结论=REJECT → 生成落选原因 + 2-3条提升建议 + 更新投递状态为 REJECTED
>
> AI 反馈内容 HR 可审核修改后再推送。

---

#### 7. 获取候选人简历亮点（内部接口，供 Agent 使用）

```plain
GET /internal/hr/candidates/{candidateId}/highlights
```

| **参数**      | **类型** | **必填** | **描述**               |
| ------------- | -------- | :------: | ---------------------- |
| candidateId   | Long     |    是    | 候选人用户ID（路径参数） |

> **说明**：此接口为内部 Feign 接口，非对外 API。内部调用 lingxi-resume 获取候选人简历解析结果和能力模型，封装后返回给 Interview Agent 出题使用。

---

### 三、Offer 管理

#### 8. 发起 Offer

```plain
POST /api/v1/hr/offers
```

**请求参数**：

| **参数**        | **类型**    | **必填** | **描述**                            |
| --------------- | ----------- | :------: | ----------------------------------- |
| applicationId   | Long        |    是    | 投递记录ID                          |
| salary          | Integer     |    是    | 月薪（元，整数）                    |
| entryDate       | LocalDate   |    是    | 预计入职日期（必须在未来）          |
| level           | String      |    否    | 职级，如 P6、高级工程师             |
| remark          | String      |    否    | 备注                                |
| expiresInDays   | Integer     |    否    | 有效期天数，默认7天                 |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "offerId": 8001,
    "status": "SENT",
    "expiresAt": "2026-08-06T14:30:00",
    "notificationSent": true
  }
}
```

> **校验规则**：
> - 该候选人投递状态必须为 OFFERABLE（仅面试通过后可以发起）
> - 岗位剩余可招聘 HC > 0（total_hc - confirmed_hc - reserved_hc，HC 不足则拦截发起）
> - 入职时间必须在未来日期
> - 该候选人不能已有 SENT 状态的 Offer（防止重复发起）
> - 薪资超出岗位范围时提示但不阻止
> - **发起成功后**：Feign 调 `lingxi-job: POST /internal/jobs/{jobId}/reserve-hc` 预冻结 HC

---

#### 9. 获取 Offer 列表

```plain
GET /api/v1/hr/offers
```

**请求参数**：

| **参数** | **类型** | **必填** | **描述**                                      |
| -------- | -------- | :------: | --------------------------------------------- |
| status   | String   |    否    | 按状态筛选：SENT/ACCEPTED/REJECTED/EXPIRED    |
| jobId    | Long     |    否    | 按岗位筛选                                     |
| page     | Integer  |    否    | 页码，默认1                                    |
| size     | Integer  |    否    | 每页条数，默认20                               |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "total": 5,
    "records": [
      {
        "offerId": 8001,
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

> **说明**：`candidateName` 通过 Feign 调 `lingxi_user.user` 获取，`jobTitle` 通过 Feign 调 `lingxi_job.job_post` 获取。`urgeCount` 显示当前催促次数，`acceptedAt`/`rejectReason` 仅在对应终态下非空。

---

#### 10. 候选人确认 Offer

```plain
POST /api/v1/offers/{offerId}/accept
```

| **参数** | **类型** | **必填** | **描述**             |
| -------- | -------- | :------: | -------------------- |
| offerId  | Long     |    是    | Offer ID（路径参数） |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "offerId": 8001,
    "status": "ACCEPTED",
    "acceptedAt": "2026-07-30T15:00:00",
    "applicationStatus": "OFFERED",
    "hcRemaining": 1,
    "jobClosed": false
  }
}
```

> **并发控制**：
> 1. `SELECT ... FOR UPDATE` 行级锁读取 hr_offer 记录
> 2. 校验 `status == SENT`，否则拒绝
> 3. offer_id 幂等校验（重复请求返回相同结果，不重复确认）
> 4. Feign 同步调 `lingxi-job: POST /internal/jobs/{jobId}/confirm-hc`（预冻结转正）
> 5. HC 确认成功后：`UPDATE hr_offer SET status='ACCEPTED'`，通知 lingxi-resume 更新投递状态为 OFFERED
> 6. HC 确认失败：保持 hr_offer 状态为 SENT，提示候选人"岗位剩余名额不足，请联系HR处理"

---

#### 11. 候选人拒绝 Offer

```plain
POST /api/v1/offers/{offerId}/reject
```

| **参数**      | **类型** | **必填** | **描述**             |
| ------------- | -------- | :------: | -------------------- |
| offerId       | Long     |    是    | Offer ID（路径参数） |
| rejectReason  | String   |    否    | 拒绝原因（选填）     |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "offerId": 8001,
    "status": "REJECTED",
    "rejectedAt": "2026-07-30T16:00:00",
    "applicationStatus": "WITHDRAWN"
  }
}
```

> **说明**：拒绝后 Feign 调 lingxi-job 释放预冻结 HC（reserved_hc--），投递状态由 OFFERABLE 变为 WITHDRAWN。

---

#### 12. HR 催促候选人确认 Offer

```plain
POST /api/v1/hr/offers/{offerId}/remind
```

| **参数** | **类型** | **必填** | **描述**             |
| -------- | -------- | :------: | -------------------- |
| offerId  | Long     |    是    | Offer ID（路径参数） |

**响应格式**：
```json
{
  "code": 0,
  "message": "催促通知已发送"
}
```

> **限频规则**：同一 Offer 24 小时内最多催促 2 次，超出返回 `code=40002` "今日催促次数已达上限"。

---

### 四、消息通知

#### 13. 获取通知列表

```plain
GET /api/v1/notifications
```

**请求参数**：

| **参数** | **类型** | **必填** | **描述**                                                     |
| -------- | -------- | :------: | ------------------------------------------------------------ |
| type     | String   |    否    | 按类型筛选：APPLICATION/INTERVIEW/OFFER/HC_WARNING/EVAL_TIMEOUT/JOB_REFRESH/SYSTEM/MATCH_RECOMMEND |
| isRead   | Boolean  |    否    | 按已读状态筛选                                               |
| page     | Integer  |    否    | 页码，默认1                                                  |
| size     | Integer  |    否    | 每页条数，默认20                                             |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "total": 25,
    "unreadCount": 3,
    "records": [
      {
        "id": 9001,
        "type": "APPLICATION",
        "typeDesc": "投递通知",
        "title": "新简历投递",
        "content": "李婷投递了「高级前端工程师」，匹配度92%",
        "targetType": "application",
        "targetId": 5001,
        "isRead": false,
        "createdAt": "2026-07-28T10:00:00"
      }
    ]
  }
}
```

---

#### 14. 标记通知已读

```plain
PUT /api/v1/notifications/read
```

**请求参数**：

| **参数**        | **类型**  | **必填** | **描述**                                    |
| --------------- | --------- | :------: | ------------------------------------------- |
| notificationIds | Long[]    |    否    | 指定通知ID列表（不传则全部标为已读）        |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "updatedCount": 3
  }
}
```

---

### 五、消息沟通

#### 15. 获取会话列表

```plain
GET /api/v1/conversations
```

**请求参数**：

| **参数** | **类型** | **必填** | **描述**           |
| -------- | -------- | :------: | ------------------ |
| page     | Integer  |    否    | 页码，默认1        |
| size     | Integer  |    否    | 每页条数，默认20   |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "total": 12,
    "records": [
      {
        "conversationId": 10001,
        "targetUserId": 3001,
        "targetUserName": "张伟",
        "targetAvatar": "...",
        "jobTitle": "高级前端工程师",
        "applicationId": 5002,
        "lastMessagePreview": "您好，我对这个岗位很感兴趣...",
        "lastMessageAt": "2026-07-29T14:30:00",
        "unreadCount": 2
      }
    ]
  }
}
```

> **说明**：根据当前用户角色自动判断是 HR 视角还是求职者视角。HR视角通过 `company_id + hr_id` 查询，求职者视角通过 `candidate_id` 查询。`targetUserName`/`targetAvatar` 通过 Feign 调 `lingxi_user.user` 获取。

---

#### 16. 获取会话消息列表

```plain
GET /api/v1/conversations/{conversationId}/messages
```

**请求参数**：

| **参数**         | **类型** | **必填** | **描述**           |
| ---------------- | -------- | :------: | ------------------ |
| conversationId   | Long     |    是    | 会话ID（路径参数） |
| page             | Integer  |    否    | 页码，默认1        |
| size             | Integer  |    否    | 每页条数，默认50   |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "total": 35,
    "records": [
      {
        "messageId": 11001,
        "senderId": 3001,
        "senderRole": "CANDIDATE",
        "contentType": "TEXT",
        "content": "您好，我对这个岗位很感兴趣，想了解一下团队规模",
        "isRead": true,
        "createdAt": "2026-07-29T14:28:00"
      },
      {
        "messageId": 11002,
        "senderId": 2001,
        "senderRole": "HR",
        "contentType": "CARD_INTERVIEW",
        "content": "{\"interviewId\":6001,\"scheduledAt\":\"2026-07-30T14:00:00\",\"method\":\"OFFLINE\",\"location\":\"公司3楼会议室\"}",
        "isRead": true,
        "createdAt": "2026-07-29T14:30:00"
      }
    ]
  }
}
```

> **说明**：获取消息列表时自动将该会话中对方发来的未读消息标记为已读。

---

#### 17. 发送消息

```plain
POST /api/v1/conversations/{conversationId}/messages
```

**请求参数**：

| **参数**         | **类型** | **必填** | **描述**                                                      |
| ---------------- | -------- | :------: | ------------------------------------------------------------- |
| conversationId   | Long     |    是    | 会话ID（路径参数）                                            |
| contentType      | String   |    是    | 消息类型：TEXT / CARD_INTERVIEW / CARD_RESUME / CARD_JD       |
| content          | String   |    是    | 消息内容（TEXT类型为纯文本，卡片类型为JSON）                  |

**卡片类型 JSON 格式**：

`CARD_INTERVIEW`：
```json
{
  "interviewId": 6001,
  "scheduledAt": "2026-07-30T14:00:00",
  "method": "OFFLINE",
  "location": "公司3楼会议室",
  "interviewerName": "王工",
  "jobTitle": "高级前端工程师"
}
```

`CARD_RESUME`：
```json
{
  "resumeId": 2001,
  "candidateName": "李婷",
  "jobTitle": "高级前端工程师"
}
```

`CARD_JD`：
```json
{
  "jobId": 1001,
  "title": "高级前端工程师",
  "salaryRange": "25K-40K",
  "city": "杭州"
}
```

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "messageId": 11003,
    "createdAt": "2026-07-29T14:32:00"
  }
}
```

> **说明**：发送消息后更新 msg_conversation 表的 `last_message_preview` 和 `last_message_at`。通过 WebSocket 实时推送新消息给对方。

---

#### 18. 创建会话（求职者咨询 HR）

```plain
POST /api/v1/conversations
```

**请求参数**：

| **参数**        | **类型** | **必填** | **描述**                                    |
| --------------- | -------- | :------: | ------------------------------------------- |
| jobId           | Long     |    是    | 岗位ID（岗位详情页"咨询HR"入口）            |
| applicationId   | Long     |    否    | 投递记录ID（已投递时传入）                  |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "conversationId": 10005,
    "hrId": 2001,
    "hrName": "李明",
    "isNew": true
  }
}
```

> **说明**：同一个求职者、同一个企业、同一个 HR 之间只会存在一个会话（唯一约束 `uk_company_candidate_hr`）。后端通过 `job_id` Feign 调 `lingxi_job.job_post` 获取 `company_id`，再查 `hr_company_member` 找到该企业的 HR。若会话已存在则返回已有会话 ID（`isNew=false`）。

---

### 六、题库管理（P1）

#### 19. 题目列表查询

```plain
GET /api/v1/hr/questions
```

**请求参数**：

| **参数**       | **类型** | **必填** | **描述**                                     |
| -------------- | -------- | :------: | -------------------------------------------- |
| jobType        | String   |    否    | 岗位类型：前端/后端/产品/设计/数据            |
| techStack      | String   |    否    | 技术栈标签                                    |
| difficulty     | String   |    否    | 难度：EASY/MEDIUM/HARD                       |
| questionType   | String   |    否    | 题目类型：BASIC/PROJECT/BOUNDARY/COMPREHENSIVE |
| status         | String   |    否    | 审核状态：PENDING_REVIEW/APPROVED/REJECTED   |
| page           | Integer  |    否    | 页码，默认1                                   |
| size           | Integer  |    否    | 每页条数，默认20                              |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "total": 120,
    "records": [
      {
        "questionId": 12001,
        "content": "请描述React组件设计原则...",
        "keyPoints": "组件设计原则、受控/非受控组件",
        "referenceAnswer": "1. 单一职责...",
        "evaluationPoints": [{"criterion":"完整性","weight":40},{"criterion":"准确性","weight":60}],
        "difficulty": "MEDIUM",
        "techStack": "React",
        "jobType": "前端",
        "questionType": "BASIC",
        "status": "APPROVED",
        "usageCount": 15,
        "source": "AI_GENERATED",
        "companyId": null,
        "createdAt": "2026-07-25T10:00:00"
      }
    ]
  }
}
```

---

#### 20. 手动添加题目

```plain
POST /api/v1/hr/questions
```

**请求参数**：

| **参数**         | **类型** | **必填** | **描述**                                     |
| ---------------- | -------- | :------: | -------------------------------------------- |
| content          | String   |    是    | 题目内容                                     |
| keyPoints        | String   |    是    | 考察要点                                     |
| referenceAnswer  | String   |    是    | 参考答案要点                                  |
| evaluationPoints | JSON     |    否    | 结构化评分要点：[{criterion:"完整性",weight:40}] |
| difficulty       | String   |    是    | 难度：EASY/MEDIUM/HARD                       |
| techStack        | String   |    是    | 技术栈标签                                    |
| jobType          | String   |    是    | 岗位类型                                     |
| questionType     | String   |    是    | 题目类型：BASIC/PROJECT/BOUNDARY/COMPREHENSIVE |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "questionId": 12002,
    "status": "APPROVED",
    "similarQuestions": []
  }
}
```

> **去重规则**：入库前计算题目内容相似度哈希，与现有题库对比：
> - 相似度 > 80% → 返回 `similarQuestions` 列表，提示"题库中已有相似题目"
> - 相似度 ≤ 80% → 正常入库
> - 手动添加的题目自动标记为 `APPROVED`（不由 AI 生成的默认审核状态），`source=MANUAL`

---

### 七、成员管理（P1）

#### 21. 获取成员列表

```plain
GET /api/v1/hr/members
```

**请求参数**：

| **参数** | **类型** | **必填** | **描述**                                     |
| -------- | -------- | :------: | -------------------------------------------- |
| role     | String   |    否    | 按角色筛选：HR_ADMIN/INTERVIEWER             |
| status   | String   |    否    | 按状态筛选：ACTIVE/DISABLED                  |

**响应格式**：
```json
{
  "code": 0,
  "data": [
    {
      "memberId": 13001,
      "userId": 2002,
      "name": "王工",
      "phone": "139****9999",
      "role": "INTERVIEWER",
      "roleDesc": "面试官",
      "department": "技术部-前端组",
      "techDirection": "前端",
      "interviewCount": 28,
      "status": "ACTIVE",
      "createdAt": "2026-07-20T10:00:00"
    }
  ]
}
```

> **说明**：`name`/`phone` 通过 Feign 调 `lingxi_user.user` 获取。列表按本企业（当前登录 HR 的 company_id）过滤，无需分页（企业成员数量有限）。

---

#### 22. 添加成员（创建面试官）

```plain
POST /api/v1/hr/members
```

**请求参数**：

| **参数**       | **类型** | **必填** | **描述**                       |
| -------------- | -------- | :------: | ------------------------------ |
| phone          | String   |    是    | 手机号（作为登录账号）          |
| name           | String   |    是    | 真实姓名                        |
| department     | String   |    是    | 所属部门                        |
| techDirection  | String   |    否    | 技术方向：前端/后端/全栈/产品/设计/数据/其他 |
| email          | String   |    否    | 邮箱（接收面试通知）             |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "memberId": 13002,
    "name": "陈工",
    "role": "INTERVIEWER",
    "smsNotificationSent": true
  }
}
```

> **说明**：HR 创建面试官后，若该手机号未注册则自动创建 `lingxi_user.user` 记录（role=INTERVIEWER），同时在 `hr_company_member` 插入成员关系。系统发送邀请短信（含登录账号和初始密码）。被邀请人首次登录后自动加入本企业。`email` 写入 `lingxi_user.user.email`，非 `company_member` 表字段。

---

### 八、C端模拟面试

#### 23. 开始模拟面试

```plain
POST /api/v1/mock-interviews/start
```

**请求参数**：

| **参数** | **类型** | **必填** | **描述**   |
| -------- | -------- | :------: | ---------- |
| jobId    | Long     |    是    | 目标岗位ID |

**响应格式**（SSE 流式推送）：
```json
{
  "code": 0,
  "data": {
    "sessionId": "mock-20260729-001",
    "totalQuestions": 6,
    "currentQuestion": {
      "questionNumber": 1,
      "content": "请描述React组件设计原则...",
      "dimension": "React组件设计",
      "type": "BASIC"
    }
  }
}
```

> **出题策略**：按基础验证 30% + 项目深挖 40% + 能力边界 20% + 综合素养 10% 从题库中抽取 5-8 题，由浅入深排序。

---

#### 24. 提交模拟面试回答

```plain
POST /api/v1/mock-interviews/{sessionId}/answer
```

**请求参数**：

| **参数**    | **类型** | **必填** | **描述**         |
| ----------- | -------- | :------: | ---------------- |
| sessionId   | String   |    是    | 面试会话ID（路径参数） |
| answer      | String   |    是    | 求职者的文字回答 |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "isLastQuestion": false,
    "nextQuestion": {
      "questionNumber": 2,
      "content": "请描述你在项目中遇到的性能优化挑战...",
      "dimension": "项目深挖",
      "type": "PROJECT"
    }
  }
}
```

> **说明**：每道题的 AI 评分和点评仅后台记录，不在单题完成后展示。所有评分统一在最终面试报告中一次展示。

---

#### 25. 获取模拟面试报告

```plain
GET /api/v1/mock-interviews/{sessionId}/report
```

**请求参数**：

| **参数**    | **类型** | **必填** | **描述**               |
| ----------- | -------- | :------: | ---------------------- |
| sessionId   | String   |    是    | 面试会话ID（路径参数） |

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "sessionId": "mock-20260729-001",
    "jobTitle": "高级前端工程师",
    "totalQuestions": 6,
    "overallScore": 78,
    "dimensionScores": {
      "techAccuracy": 82,
      "expression": 75,
      "knowledgeDepth": 70,
      "projectExperience": 85
    },
    "highlights": [
      "React性能优化实战经验丰富，能清晰阐述优化前后数据",
      "项目描述运用了STAR法则，逻辑完整"
    ],
    "weakPoints": [
      "'浏览器渲染原理'回答停留在表面，未涉及Critical Rendering Path",
      "系统设计题缺乏从业务需求→技术方案的推导过程"
    ],
    "improvementPlan": [
      {
        "title": "阅读React源码解析系列文章",
        "focus": "Fiber架构",
        "deadline": "本周"
      },
      {
        "title": "练习3道系统设计题",
        "focus": "按'需求→方案→trade-off'结构输出",
        "deadline": "本周"
      }
    ],
    "questionDetails": [
      {
        "questionNumber": 1,
        "content": "请描述React组件设计原则...",
        "myAnswer": "React组件设计应遵循...",
        "score": 78,
        "comment": "..."
      }
    ],
    "completedAt": "2026-07-29T15:00:00"
  }
}
```

---

### 九、内部接口（供其他微服务调用）

#### 26. 获取候选人面试亮点（供 Interview Agent）

```plain
GET /internal/hr/applications/{applicationId}/interview-context
```

| **参数**        | **类型** | **必填** | **描述**              |
| --------------- | -------- | :------: | --------------------- |
| applicationId   | Long     |    是    | 投递记录ID（路径参数）|

**响应格式**：
```json
{
  "code": 0,
  "data": {
    "applicationId": 5001,
    "candidateId": 3001,
    "jobId": 1001,
    "jobTitle": "高级前端工程师",
    "resumeHighlights": {
      "skillTags": ["React", "TypeScript", "Redux"],
      "projectHighlights": [
        {
          "projectName": "电商秒杀系统",
          "techStack": ["Redis", "Spring Boot"],
          "achievement": "QPS优化至5000"
        }
      ],
      "weakPoints": ["架构设计经验偏少"]
    },
    "jobRequirements": {
      "coreSkills": ["React(精通)", "TypeScript(熟练)"],
      "softSkills": ["架构设计能力(必须)"]
    }
  }
}
```

> **说明**：此接口聚合了 lingxi-resume 的简历数据和 lingxi-job 的岗位要求数据，供 Interview Agent 出题使用。内部通过 Feign 调用 B 和 C 服务组装。

---

## 关键技术设计

### 1. Offer 与 Headcount 预冻结并发安全

Offer 与 HC 交互涉及三个阶段：发起时预冻结、确认时转正、拒绝/过期时释放。并发安全采用数据库行级锁 + 条件更新 + 唯一约束三重保障：

**阶段一：HR 发起 Offer — 预冻结 HC**
1. lingxi-hr 校验岗位剩余可招聘 HC（total_hc - confirmed_hc - reserved_hc > 0）
2. lingxi-hr 创建 `hr_offer` 记录（status=SENT）
3. Feign 同步调用 `lingxi-job: POST /internal/jobs/{jobId}/reserve-hc`
4. lingxi-job 内部：`UPDATE job_post SET reserved_hc=reserved_hc+1 WHERE id=? AND total_hc - confirmed_hc - reserved_hc > 0`（行级锁），写入 `job_hc_reservation`（status=RESERVED，uk_hc_offer 防重复）
5. 预冻结失败 → lingxi-hr 回滚 hr_offer 创建

**阶段二：候选人确认 Offer — 预冻结转正**
1. `SELECT * FROM hr_offer WHERE id = ? FOR UPDATE` — 行级锁
2. 校验 `status == SENT`，已处理则拒绝（幂等：已 ACCEPTED 返回已有结果）
3. Feign 同步调用 `lingxi-job: POST /internal/jobs/{jobId}/confirm-hc`
4. lingxi-job 内部条件更新：`UPDATE job_hc_reservation SET status='CONFIRMED' WHERE offer_id=? AND status='RESERVED'` + `UPDATE job_post SET reserved_hc--, confirmed_hc++`
5. 确认成功 → `UPDATE hr_offer SET status = 'ACCEPTED'`
6. 确认失败 → 保持 hr_offer SENT 状态，提示候选人"名额不足"

**阶段三：候选人拒绝 / Offer 过期 — 释放预冻结**
1. 拒绝：`UPDATE hr_offer SET status='REJECTED'` + Feign `release-hc`
2. 过期：定时任务条件更新 `UPDATE hr_offer SET status='EXPIRED' WHERE status='SENT'` + Feign `release-hc`
3. lingxi-job 内部条件更新：`UPDATE job_hc_reservation SET status='RELEASED' WHERE offer_id=? AND status='RESERVED'` + `job_post.reserved_hc--`

**并发安全保证**：
- 数据库行级锁（`FOR UPDATE`）保证 Offer 状态变更互斥
- lingxi-job 端条件更新（`WHERE status='RESERVED'`）保证预冻结只被确认/释放一次
- `job_hc_reservation.uk_hc_offer` 唯一约束防止重复预冻结
- 不引入分布式锁（MVP 阶段）

### 2. Offer 过期定时任务

```java
@XxlJob("offerExpireHandler")
public void handleOfferExpire() {
    // 1. 查询所有过期且状态为 SENT 的 Offer
    List<Offer> expiredOffers = offerMapper.selectList(
        new LambdaQueryWrapper<Offer>()
            .eq(Offer::getStatus, "SENT")
            .le(Offer::getExpiresAt, LocalDateTime.now())
    );

    // 2. 逐条条件更新
    for (Offer offer : expiredOffers) {
        int updated = offerMapper.update(null,
            new LambdaUpdateWrapper<Offer>()
                .eq(Offer::getId, offer.getId())
                .eq(Offer::getStatus, "SENT")       // 条件更新，防止与确认/拒绝并发冲突
                .set(Offer::getStatus, "EXPIRED")
                .set(Offer::getUpdatedAt, LocalDateTime.now())
        );
        if (updated > 0) {
            // Feign 调 lingxi-job 释放预冻结 HC
            hcClient.releaseHc(offer.getJobId(), offer.getId(), "EXPIRED");
            // 推送过期通知（RocketMQ 异步）
            notificationProducer.sendExpireNotification(offer);
        }
    }
}
```

### 3. 消息通知 RocketMQ 消费者

消息通知采用 **异步消费** 模式，解耦业务操作与通知推送：

**消息体结构**：
```json
{
  "eventType": "APPLICATION_RECEIVED",
  "targetUserId": 2001,
  "title": "新简历投递",
  "content": "李婷投递了「高级前端工程师」，匹配度92%",
  "targetType": "application",
  "targetId": 5001,
  "timestamp": "2026-07-29T14:30:00"
}
```

**消费者处理流程**：
1. 接收 RocketMQ 消息
2. 查询用户通知偏好配置（是否关闭此类通知）
3. 未关闭 → 写入 `notification` 表（`is_read=0`）
4. 根据通知类型决定是否触发短信/邮件（面试提醒类可配短信通知）
5. 通过 WebSocket 实时推送未读数更新给前端

### 4. Mock Interview Agent 答题评分逻辑

```java
// 评分维度及权重
public AnswerScore evaluate(String questionContent, String referenceAnswer, String userAnswer) {
    // 1. 技术准确度 (50%权重)：LLM 判断回答中的概念是否正确
    int techAccuracy = llm.evaluateAccuracy(questionContent, referenceAnswer, userAnswer);

    // 2. 表达逻辑 (30%权重)：LLM 判断回答结构是否清晰（STAR法则）
    int expression = llm.evaluateExpression(userAnswer);

    // 3. 知识深度 (20%权重)：LLM 判断是否涉及原理/源码层面
    int knowledgeDepth = llm.evaluateDepth(questionContent, userAnswer);

    // 综合评分
    int totalScore = (int)(techAccuracy * 0.5 + expression * 0.3 + knowledgeDepth * 0.2);

    // AI 生成 1-2 句点评
    String comment = llm.generateComment(techAccuracy, expression, knowledgeDepth);

    return new AnswerScore(totalScore, techAccuracy, expression, knowledgeDepth, comment);
}
```

### 5. 数据权限隔离

| **角色** | 候选人可见范围 | 面试记录可见范围 | Offer 可见范围 |
| -------- | -------------- | ---------------- | -------------- |
| HR 管理员 | 投递本企业的所有候选人 | 本企业所有面试 | 本企业所有 Offer |
| 面试官 | 仅被分配面试的候选人 | 仅本人参与的面试 | 不可见 |

在 Service 层通过 `SecurityUtils.getCurrentUserId()` 和 `SecurityUtils.getCurrentCompanyId()` 注入数据过滤条件。

---

## 边界与异常场景处理

> 在各模块的正常流程之外，明确边界情况、异常情况和降级策略，避免线上故障。

### 一、候选人管理

| 场景 | 处理策略 |
|------|----------|
| 候选人列表为空 | 返回空列表 `{ total: 0, records: [] }`，前端展示"暂无候选人投递"引导文案 |
| Top5 推荐不足 5 人 | 实际匹配到几人返回几人，不填充，接口返回实际数量（可能为 0） |
| 重复标记合适/不合适 | 校验当前投递状态：已标记过的（SCREENED/REJECTED）拒绝重复操作，返回 `code=40001` "该候选人已处理，请勿重复操作" |
| 候选人已撤回投递 | 标记操作前校验 `status`，已撤回（WITHDRAWN）或终态的不允许操作 |
| 跨企业操作 | Service 层强制注入 `company_id` 过滤，防止 HR 操作其他企业的候选人 |
| Feign 依赖不可用 | 候选人姓名/头像调 `lingxi_user` 失败时，返回 `candidateName="用户"+userId`、`avatar=null` 降级，不阻塞列表查询 |

### 二、面试协同

| 场景 | 处理策略 |
|------|----------|
| 面试安排时间冲突 | 不强制校验冲突（MVP 阶段允许同一面试官同时段多场面试），后续可加软提示 |
| 面试官不存在或已禁用 | 创建面试时校验 `hr_company_member.status=ACTIVE`，不满足返回 `code=40002` "面试官不存在或已被禁用" |
| 面试已取消后操作 | 安排面试、录入评估前校验 `hr_interview.status != CANCELLED`，已取消返回错误 |
| 重复录入评估 | `uk_interview_id` 唯一约束防重复，重复提交返回 `code=40003` "该面试已有评估记录" |
| 评语字数不足 20 字 | 参数校验层拦截，返回 `code=40004` "评语至少 20 字" |
| 草稿与正式提交 | `is_draft=1` 时跳过必填校验，允许不完整保存；`is_draft=0` 时全量校验，且一个面试只允许一份正式评估 |
| AI 生成反馈超时 | LLM 调用超时（>10s）时先保存评估记录，`feedback` 字段留空，异步重试补充反馈内容 |
| 面试状态流转异常 | 仅 `CONFIRMED/IN_PROGRESS` 状态允许录入评估；`PENDING/CANCELLED/COMPLETED` 拒绝操作 |

### 三、Offer 管理

| 场景 | 处理策略 |
|------|----------|
| **发起 Offer 时 HC 不足** | `total_hc - confirmed_hc - reserved_hc <= 0`，拦截发起，返回 `code=40005` "岗位 HC 不足，无法发起 Offer" |
| **重复发起 Offer** | `uk_application_id` 唯一约束 + 校验该投递不存在 SENT 状态的 Offer，存在则返回 `code=40006` "该候选人已有待确认的 Offer" |
| **候选人确认时 Offer 已过期** | `FOR UPDATE` 读 Offer → 校验 `status==SENT && expires_at > NOW()`，已过期拒绝，返回 `code=40007` "Offer 已过期" |
| **候选人确认时 Offer 已被他人处理（并发）** | 行级锁互斥，后来的请求校验 `status!=SENT`，直接返回"Offer 已被处理" |
| **确认 HC 时 Feign 调用失败** | 不更新 `hr_offer.status`，返回 `code=50001` "系统繁忙，请稍后重试"；可重试（预冻结记录仍在，幂等安全） |
| **拒绝时释放 HC 失败** | Offer 状态先更新为 REJECTED，释放 HC 失败仅记录日志（WARN），不作为硬错误返回——已拒绝的 Offer 不会重复占用，预冻结记录由定时对账兜底 |
| **Offer 过期定时任务与确认并发** | 定时任务条件更新 `WHERE status='SENT'`，确认用 `FOR UPDATE` 行级锁，双方互斥，只有一个成功 |
| **过期释放 HC 失败** | 记录错误日志，不阻塞后续 Offer 处理。可加对账任务补偿 |
| **催促次数超限** | 24h 内 `urge_count >= 2`，返回 `code=40008` "今日催促次数已达上限（2次），请明天再试" |
| **入职时间不在未来** | `entry_date <= LocalDate.now()` ，返回 `code=40009` "入职时间必须在未来日期" |
| **薪资 <= 0** | 参数校验返回 `code=40010` "薪资必须大于 0" |
| **Feign 超时降级** | 见下方"跨服务调用降级"通用策略 |

### 四、消息通知

| 场景 | 处理策略 |
|------|----------|
| RocketMQ 消费失败 | 消息消费异常重试 3 次，仍失败写入死信表 `msg_notification_dlq`，人工兜底 |
| 用户关闭某类通知 | 写入 `msg_notification` 前查 `user_notification_preference`，已关闭的跳过不写 |
| 通知列表为空 | 返回空列表 `{ total: 0, unreadCount: 0, records: [] }`，前端展示"暂无通知" |
| 重复标记已读 | 幂等处理，已读再次标记不报错 |
| 批量标记已读传空数组 | 视为不操作，返回 `{ updatedCount: 0 }` |

### 五、消息沟通

| 场景 | 处理策略 |
|------|----------|
| 创建会话时已存在 | `uk_company_candidate_hr` 唯一约束，已存在返回已有 `conversationId`，`isNew=false`，不报错 |
| 发送消息到不存在的会话 | 校验 `conversation_id` 存在性，不存在返回 404 |
| 消息内容为空 | 参数校验拦截，返回 `code=40011` "消息内容不能为空" |
| 会话对象已禁用 | 查询对方 `sys_user.status`，已禁用时允许查看历史消息但禁止发送新消息，返回 `code=40012` "对方账号已禁用" |
| 获取消息列表时标记已读 | 仅标记 `sender_role != 当前用户角色` 的未读消息，不标记自己发的消息 |
| 未读数冗余计数器 | 消息标记已读时同步 `UPDATE msg_conversation SET xx_unread=0`，允许少量误差（最终一致性） |

### 六、题库管理（调用成员 B）

| 场景 | 处理策略 |
|------|----------|
| 通过 Feign 搜题返回空 | Mock Interview 降级：使用预设的通用题目模板（5 道默认题），确保模拟面试不中断 |
| 题目相似度判断 | 由成员 B 的 `job_question` 接口负责，成员 D 仅透传调用 |
| 手动添加题目字段校验 | `content` 必填且 ≤2000 字，`job_type` 必填，校验失败返回具体字段错误 |

### 七、成员管理

| 场景 | 处理策略 |
|------|----------|
| 手机号已注册为求职者 | 该手机号 `sys_user.role=CANDIDATE`，仍允许创建为面试官（一个手机号可拥有多个角色身份） |
| 面试官已在该企业 | `uk_company_user` 唯一约束，返回 `code=40013` "该成员已加入企业" |
| 面试官账号被全局禁用 | `sys_user.status=DISABLED`，不允许创建新成员关系 |
| 邀请短信发送失败 | 记录失败日志，成员关系仍创建成功（`hr_company_member` 写入），前端提示"邀请短信发送失败，可稍后重发" |
| 移除有未完成面试的面试官 | MVP 阶段不拦截，直接禁用成员关系（`status=DISABLED`），该面试官已分配的面试不受影响 |
| 成员列表为空 | 至少展示创建者本人（HR_ADMIN），不会完全为空 |

### 八、C 端模拟面试

| 场景 | 处理策略 |
|------|----------|
| 题库题目不足 | 按出题策略尝试抽取，实际题目数 < 5 时以实际数量为准（最少 1 题），生成报告时按实际题数计算 |
| 求职者中途退出 | `mock_session.status` 保持 `IN_PROGRESS`，再次进入时可选择"继续面试"或"重新开始" |
| 作答超过 5 分钟 | 前端倒计时结束时自动提交（`is_skipped=1`，`candidate_answer` 为空），后台评分跳过该题 |
| 作答少于 20 字 | 不强制拦截，但 AI 评分标注"回答过短" |
| 面试已完成后再次提交答案 | 校验 `mock_session.status != COMPLETED`，已完成的返回 `code=40014` "面试已完成" |
| session_id 不存在 | 返回 404 |
| 同一题多次提交 | 以最后一次提交覆盖（UPDATE `candidate_answer` WHERE `session_id` + `question_number`） |
| LLM 评分超时 | 超时（>15s）时本题 `overall_score=0`，`ai_comment="评分服务繁忙，本题未评分"`，不阻塞其他题目 |
| 生成报告时无有效答题记录 | 返回 `code=40015` "暂无答题记录，无法生成报告" |
| 岗位已下线 | 模拟面试不校验岗位状态（`job_title` 已冗余存储），已下线的岗位仍可完成面试 |

### 九、跨服务调用降级（通用）

| 场景 | 处理策略 |
|------|----------|
| Feign 调用超时 | 连接超时 3s，读超时 5s。超时后根据场景决定：关键路径（HC 确认）直接返回错误；非关键路径（获取用户姓名）降级返回默认值 |
| Feign 调用 500 错误 | 不重试（避免雪崩），记录错误日志，返回业务错误码给前端 |
| 对方服务熔断 | Sentinel 熔断后快速失败，5s 后进入半开状态尝试恢复 |

### 十、定时任务

| 场景 | 处理策略 |
|------|----------|
| 定时任务执行超时 | `@Scheduled` 默认单线程，上次未执行完不会触发新任务（无需额外控制） |
| 一次扫出大量过期 Offer | 逐条处理即可，Offer 过期不是高频操作，单次扫出量不会超过百级别 |
| 条件更新影响 0 行 | 说明 Offer 已被确认/拒绝，跳过 Feign 释放 HC 和发送通知 |
| 释放 HC 时 Feign 失败 | 记录错误日志并继续处理下一条，不阻塞其他 Offer |

---

## 排期

> 对研发时间计划进行排期。

| **阶段**   | **内容**                                                         | **预估工期** |
| ---------- | ---------------------------------------------------------------- | ------------ |
| 数据库开发 | 建表（hr_interview / hr_interview_evaluation / hr_offer / msg_notification / user_notification_preference / msg_conversation / msg_message / job_question（B端维护，D端 Feign 调用）/ hr_company / hr_company_certification / hr_company_member / admin_operation_log（E端维护）/ mock_session / mock_answer / mock_report）15 张表 | Day 1 |
| 后端开发   | 候选人管理（列表/Top5/标记合适不合适）+ 内部接口暴露                | Day 2-3 |
| 后端开发   | 面试安排 + 面试评估录入 + 消息通知 RocketMQ 消费者                  | Day 3-4 |
| 后端开发   | AI 生成反馈 + 消息沟通（会话管理 + 站内消息）                      | Day 4-5 |
| 后端开发   | Offer 管理（发起/确认/拒绝/状态追踪）+ Feign HC 扣减/回退          | Day 5-6 |
| 后端开发   | Mock Interview Agent（System Prompt + 6 个工具）                  | Day 6-7 |
| 后端开发   | 题库管理（CRUD/审核/去重）+ 成员管理（创建面试官/邀请短信）+ Offer 过期定时任务 | Day 7 |
| 联调测试   | 与 lingxi-job（扣减 HC）/ lingxi-resume（简历亮点）联调           | Day 8 |
| 联调测试   | 与 A 端消息沟通联调 + C 端模拟面试联调                            | Day 8 |
| Mock 数据  | 模拟候选人/面试/Offer/题库数据，修复 Bug                          | Day 9 |
| 演示交付   | 演示脚本 + 答辩准备 + 部署验证                                    | Day 10 |

> **总预估工期**：约 10 天（与团队整体计划对齐）

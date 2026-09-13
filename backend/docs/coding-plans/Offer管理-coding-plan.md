# Offer 管理 - Coding Plan

> **所属模块：** lingxi-hr | **排期：** Day 8
> **技术栈：** Spring Boot 2.7 + MyBatis + Redis + RocketMQ + Feign
> **参考：** 成员D开发排期 Day 8、系分文档 5.5.4 / 5.6.1 / 5.7、`docs/schema.sql` hr_offer 表、lingxi-job `InternalJobController` HC 接口
> **范围：** 发起/列表/HC概览/催促/撤回 Offer + 候选人详情/接受/拒绝 + Offer 过期扫描与 HC 补偿对账定时任务
> **跨服务依赖：** lingxi-job（成员B）：HC reserve/confirm/release + 岗位 HC 信息；lingxi-resume（成员C）：投递详情/状态更新（`OFFERABLE→OFFERED`、`OFFERED→OFFER_ACCEPTED/OFFER_DECLINED/OFFERABLE`）；lingxi-user（成员A）：候选人姓名；lingxi-chat（成员A）：Offer 通知
> **状态：** ✅ **已实现 + 本地全链路联调通过**（2026-08-07 代码 commit `5fb2ee5`；2026-08-08 联调通过）；✅ **C 端反向同步补丁已实现**（2026-08-08，commit `8622eed`：D 新增 `POST /internal/offers/accept|reject` 供求职者 C 端接受/拒绝 Offer，详见 `Offer接受拒绝-反向同步-coding-plan.md`）

---

## 〇、核心决策摘要

1. **HC Feign 契约修正（必做）**：Day 1-2 写的 `HcReserveRequest`/`HcReleaseRequest`/`confirmHc` 字段与 B 侧契约**不匹配**，本期必须对齐（详见 §三），否则发起/接受/拒绝全部失败。
   - reserve：B 需要 `{companyId, offerId, candidateId}`（D 现有 `{offerId, count}` ❌）
   - confirm：B 需要 `{companyId, offerId}`（D 现复用 HcReserveRequest ❌）
   - release：B 需要 `{companyId, offerId, reason}`（D 现有 `{offerId, reason}` ❌，缺 companyId）
   - **影响范围**：改动 `HcReserveRequest`/`HcReleaseRequest` 两个既有 DTO + `JobFeignClient` 三个方法签名。这两 DTO 目前仅被 JobFeignClient 引用、无业务调用方，改动安全。

2. **Offer 状态机（复用 common `OfferStatus`）**：`SENT → ACCEPTED/REJECTED/EXPIRED`，**撤回 → `WITHDRAWN`**（common 枚举已有「已撤回」，schema 注释未含但列宽 VARCHAR(16) 足够，本期不更 DDL）。终态判定用枚举 `isFinal()`。

3. **投递状态联动（C 新状态机对齐，2026-08-07）**：C 已更新 `ApplicationStatus`（新增 `OFFER_ACCEPTED`/`OFFER_DECLINED`；`OFFERED` 语义改为「待录用」过程态）。D 侧联动：
   - 发起 Offer → 投递 `OFFERABLE→OFFERED`（待录用）
   - 候选人接受 → `OFFERED→OFFER_ACCEPTED`（已录用，终态）
   - 候选人拒绝 → `OFFERED→OFFER_DECLINED`（已拒绝，终态）
   - 撤回/过期 → `OFFERED→OFFERABLE`（回退，best-effort 不阻塞）
   - **需成员C 白名单支持**：`OFFERABLE→OFFERED`、`OFFERED→OFFER_ACCEPTED`、`OFFERED→OFFER_DECLINED`、`OFFERED→OFFERABLE`（原提的 `OFFERABLE→WITHDRAWN` **作废**）✅ C 已落地（`52e1029`/`64b8b0d`，2026-08-07 已拉取确认）
   - **可再次发起（前端清单 2026-08-07，推翻原「不可再发」决策）**：删除 `uk_application_id` 唯一索引（schema.sql 已改，本地/服务器需 ALTER），仅 `WITHDRAWN`/`EXPIRED` 旧记录可重发；`SENT`（活跃）/`ACCEPTED`/`REJECTED`（终态）仍拦 **4205**；并发双发由应用层校验兜底（无 DB 唯一约束）

4. **发起 Offer 顺序（reserve 幂等可补偿）**：Snowflake 生成 id → 校验（投递 OFFERABLE / 无重复 SENT / 入职未来 / 薪资>0）→ **事务内** `reserve`（⚠️ 实际实现为整体 `@Transactional`，reserve 在事务内，与 plan 原「事务外先 reserve」不同）→ 插入 hr_offer → 投递联动 OFFERABLE→OFFERED → 任一步抛异常**补偿 `release`（reason=D_PERSIST_FAILED）** 后整体回滚。reserve 按 company+offer 幂等，B 已实现。

5. **薪资超范围软提示（已确认 2026-08-07）**：发起 Offer 时不拦截薪资，但响应返回 `salaryWarning`（`salaryNegotiable=false` 且 `salary` 超出岗位 `[salaryMinAmount, salaryMaxAmount]` 时置 true + 提示文案；B `getJobForValidation` 已返回薪资字段）。

6. **accept/reject 是候选人接口（非 HR 接口）**：`@RequireLogin` 但**不走 `requireActiveMember`**（候选人在目标企业无成员关系），鉴权改为 `UserContext.getUserId() == offer.candidateId`，不满足抛 401。其余 5 个 Offer 接口**仅 HR_ADMIN**（系分 5.6.5：面试官 Offer 不可见），INTERVIEWER 访问抛 4011。

7. **HC 补偿对账不依赖流水查询**：排期「调 B 查流水」的 `GET /internal/jobs/{jobId}/hc/flow` **B 侧未实现**（`InternalJobController` 无此端点，D 的 `JobFeignClient.getHcFlow` 是 Day 1-2 预留的死代码）。因 B 的 `release` **幂等**（RELEASED 幂等返回），对账改为**直接调 release** 兜底，不再先查流水。`getHcFlow` 本期删除（避免误导），如 B 后续提供再恢复。

8. **HC 概览数据源 + jobId 可空（前端对齐 2026-08-07）**：D 的 `JobFeignClient` **新增 `getJobForValidation(jobId)`** → `GET /internal/jobs/{jobId}`（B 已实现）+ **新增 `listCompanyJobs(companyId)`** → `GET /internal/jobs/company/{companyId}`（B 已实现，仅返回 jobId/title/status/availableHc）。hc-overview **jobId 可空**：传 jobId → 单岗 `getJobForValidation`；**不传 → 公司级聚合**：`listCompanyJobs` 拿本企业全部 jobId → 逐个 `getJobForValidation` 求和 total/reserved/confirmed/available（岗位数有限，N 次 Feign 可接受；前端维持全公司汇总卡片）。薪资软提示用 `getJobForValidation` 的 salaryMinAmount/salaryMaxAmount/salaryNegotiable。

9. **通知（Feign 直调 lingxi-chat，best-effort）**：候选人侧 `OFFER_RECEIVED`（ChatConstant 已有，line 130）；HR 侧（催促/过期提醒）用 `OFFER_MANAGE`（line 118）。失败记 warn 日志不阻塞。

10. **offerId JSON 字符串序列化（前端 P0，2026-08-07）**：Snowflake offerId ≈4~5×10¹⁸ 超出 JS Number.MAX_SAFE_INTEGER，**响应字段必须为字符串**。方案：`OfferVO.offerId`、`OfferCreateResultVO.offerId` 标注 `@JsonSerialize(using = ToStringSerializer.class)`（实体保持 Long，仅序列化层转字符串）；路径参数 `{id}` 前端回传字符串、Spring 自动转 Long 无需处理。**不做全局 Long→String**（影响其他接口契约）。

11. **候选人列表 `hasOfferRecord`（前端 P0，2026-08-07）**：Day 3 候选人列表 `GET /candidates/list?status=OFFERABLE` 的 `CandidateVO` **新增 `hasOfferRecord`**（该投递**是否已存在任意 Offer 记录**，含 WITHDRAWN/EXPIRED/REJECTED 等终态），前端据此禁用「发起 Offer」按钮。实现：`HrCandidateServiceImpl` 列表拼装时对 page 内 `applicationId` 批量查 `hr_offer`（`application_id IN (...)`，**无状态过滤**）→ set。⚠️ **涉及修改 Day 3 已稳定代码**（VO 加字段 + 拼装逻辑）。**2026-08-07 前端反馈修正**：原语义「查 SENT」在新状态机下恒 false（有 SENT Offer 的投递已是 OFFERED 而非 OFFERABLE），无法禁用撤回/过期回退 OFFERABLE 的候选人（再发起撞 `uk_application_id` → 4205）；故改为任意 Offer 记录，字段改名 `hasOfferRecord`。

12. **Offer 列表补 `dateRange`（前端 P0，2026-08-07）**：列表新增 `dateRange` 参数（TODAY/WEEK/MONTH，**按 createdAt 过滤**），复用面试列表 `normalizeDateRange` 模式（HrInterviewServiceImpl 同款，自包含复制）。

13. **候选人查看 Offer 详情（跨端方案A，2026-08-07 确认）**：C 端投递追踪页展示「Offer 待确认」卡片的数据源走**方案A**——候选人点 `OFFER_RECEIVED` 通知（targetId=offerId）跳转 → D 新增候选人版详情接口 `GET /api/v1/hr/offers/{offerId}`（`userId==offer.candidateId` 鉴权，返回 OfferDetailVO：offerId 字符串/salary/entryDate/level/remark/expiresAt/status/jobTitle/companyName 等）→ 页面只调详情 + accept/reject，**不依赖投递侧改动**。接口数 7→8。

14. **候选人接受/拒绝 Offer 双链路 + C 端反向同步（2026-08-08 补丁，commit `8622eed`）**：候选人接受/拒绝 Offer **两条入口并存**——① D 端 `POST /api/v1/hr/offers/{id}/accept|reject`（候选人 token，方案A 详情页/通知跳转）；② C 端投递追踪页 `PUT /api/v1/applications/{id}/offer/accept|decline`（候选人直接操作）。**问题**：C 侧 `ApplicationServiceImpl.acceptOffer/declineOffer` 只走本地状态机改投递状态（OFFERED→OFFER_ACCEPTED/OFFER_DECLINED），**不更新 `hr_offer`**（表在 D 库）→ Offer 停留 SENT、被 5min 过期扫描误判 EXPIRED、HC 不 confirm、accepted_at 不落库。**修复**：C 调 D 新增内部接口 `POST /internal/offers/accept|reject`（body `{applicationId[,rejectReason]}`）**反向同步 hr_offer + confirm/release HC**；D 内部路径 `syncApplication=false` **不更新投递状态**（C 先本地 `transition()` 后调 D，若 D 再反查 C 会行锁死锁，故避免）；D 幂等（已 ACCEPTED/REJECTED 直接成功、confirm 以 offerId 幂等）自愈。D 侧已实现，C 侧改造（新建 `HrOfferFeignClient` + 改 acceptOffer/declineOffer）由成员 C 落地，详见 `Offer接受拒绝-反向同步-coding-plan.md`。

---

## 涉及数据表

### D 侧直接读写（lingxi-hr 本地 MySQL）

| 表 | 读写 | 用途 | 字段要点 |
|----|:---:|------|---------|
| `hr_offer` | 读写 | Offer 记录：发起 insert（Snowflake id）、列表 select、状态流转（SENT→ACCEPTED/REJECTED/EXPIRED/WITHDRAWN，乐观锁）、催促（urge_count/last_urge_at）、对账（last_sync_time） | id/company_id/application_id/candidate_id/job_id/salary/entry_date/level/status/expires_at/accepted_at/rejected_at/reject_reason/urge_count/last_urge_at/last_sync_time；索引 `uk_application_id`（重复发起兜底）、`idx_status_expires`（过期扫描）、`idx_status_sync`（对账） |

### 跨服务涉及（Feign 操作，D 不直接访问）

| 表 | 归属 | 操作（D 侧 Feign） | 用途 |
|----|------|------|------|
| `job_hc_reservation` | B（lingxi-job） | `reserveHc` / `confirmHc` / `releaseHc` | HC 预冻结流水：发起建 `RESERVED`、接受转 `CONFIRMED`、拒绝/撤回/过期转 `RELEASED`（B 幂等）；HC 补偿对账目标 |
| `job_post` | B（lingxi-job） | `getJobForValidation(jobId)` | HC 计数（total_hc/reserved_hc/confirmed_hc/available_hc）+ 岗位标题，供发起校验与 hc-overview |
| `resume_application` | C（lingxi-resume） | `updateApplicationStatus` / `getApplication` | 投递状态联动：接受→`OFFERED`、拒绝→`WITHDRAWN`（待 C 扩展）/`REJECTED`（兜底）；发起前校验投递 `OFFERABLE`；拼装岗位标题 |
| `sys_notification` | A（lingxi-chat） | `createNotification` | Offer 通知落库（候选人 `OFFER_RECEIVED` / HR `OFFER_MANAGE`），best-effort |
| `sys_user` | A（lingxi-user） | `batchUsers` / `getUserById` | Offer 列表候选人姓名拼装（降级"用户"+id） |

### 定时任务涉及的读写
- **Offer 过期扫描**：`hr_offer`（查 `status='SENT' AND expires_at<=NOW()` → 条件更新 `EXPIRED`）→ `job_hc_reservation`（release，reason=EXPIRED）→ `sys_notification`（过期通知）
- **HC 补偿对账**：`hr_offer`（查终态 REJECTED/EXPIRED/WITHDRAWN 且 `last_sync_time IS NULL`）→ `job_hc_reservation`（release，幂等）→ 回写 `hr_offer.last_sync_time`

> ⚠️ D 侧本期**无新增表**；`hr_offer` 表已存在于 `docs/schema.sql`（589 行，含 `WITHDRAWN` 列宽预留）。`job_hc_reservation`/`job_post`/`resume_application`/`sys_notification`/`sys_user` 均非 D 库，通过 Feign 操作，不建不读。

---

## 一、涉及文件清单

### 需要修改
| 文件 | 当前 | 修改 |
|------|:---:|------|
| `feign/dto/HcReserveRequest.java` | `{offerId, count}` | 对齐 B：`{companyId, offerId, candidateId}` |
| `feign/dto/HcReleaseRequest.java` | `{offerId, reason}` | 对齐 B：`{companyId, offerId, reason}` |
| `feign/JobFeignClient.java` | reserve/confirm/release + getHcFlow + requirements | reserve/confirm/release 改 DTO 类型；`confirmHc` 改用独立 confirm DTO（或 HcReserveRequest 去掉 count）；**删除 `getHcFlow`**；**新增 `getJobForValidation(jobId)` + `listCompanyJobs(companyId)`** |
| `mapper/HrOfferMapper.java` + `resources/mapper/HrOfferMapper.xml` | 空壳 | insert / selectById / selectByApplicationId(取最新) / selectByIdForUpdate / 条件分页+dateRange / 乐观锁流转 / updateUrge / updateSyncTime / selectExpiredSnt / selectTerminalForReconcile / selectOfferRecordAppIds(任意状态) / selectLastOfferStatus(每投递最新状态)；**反向同步补丁**：新增 `selectByApplicationIdForUpdate`（按 application_id 取最新一条 + FOR UPDATE 行锁，供 C 反向 accept/reject 并发控制） |
| `domain/vo/CandidateVO.java` | Day 3 已稳定 | **已有 `hasOfferRecord`**；新增 **`lastOfferStatus`**（每投递最新 Offer 状态，2026-08-07 前端清单） |
| `service/impl/HrCandidateServiceImpl.java` | Day 3 已稳定 | 列表拼装批量查 `hasOfferRecord`（任意状态）+ `lastOfferStatus`（最新状态） |
| `feign/dto/`（新增 3 个） | - | `JobHcOverviewDTO`（jobId/jobTitle/totalHc/reservedHc/confirmedHc/availableHc + 薪资字段，映射 B 的 InternalJobValidationResponse）、`HcConfirmRequest`（{companyId, offerId}）、`CompanyJobDTO`（jobId/title/status/availableHc，映射 B InternalCompanyJobVO） |
| `service/HrOfferService.java` | 8 接口 | **反向同步补丁（2026-08-08）**：接口新增 `acceptOfferByApplication(applicationId)` / `rejectOfferByApplication(applicationId, rejectReason)` |
| `service/impl/HrOfferServiceImpl.java` | 8 接口业务实现 | **反向同步补丁**：抽核心 `doAcceptOffer(offer, syncApplication)` / `doRejectOffer(offer, rejectReason, syncApplication)` 复用；原 `acceptOffer/rejectOffer` 改调核心（syncApplication=true）；新增两个 `*ByApplication`（syncApplication=false，**不更新投递状态**） |

### 需要新建
| 文件 | 说明 |
|------|------|
| `domain/dto/OfferCreateDTO.java` | 发起 Offer 入参（applicationId/salary/entryDate/level/remark/expiresInDays） |
| `domain/dto/OfferRejectDTO.java` | 候选人拒绝 Offer 入参（rejectReason 选填） |
| `domain/vo/OfferVO.java` | Offer 列表项（offerId **ToStringSerializer 字符串**、statusDesc/candidateName/jobTitle） |
| `domain/vo/OfferCreateResultVO.java` | 发起出参（offerId **ToStringSerializer 字符串**/status/expiresAt/notificationSent/salaryWarning） |
| `domain/vo/HcOverviewVO.java` | HC 概览出参（jobId 可空，公司级聚合时 jobId=null/jobTitle="全公司"） |
| `domain/vo/OfferActionResultVO.java` | 接受出参（offerStatus/applicationStatus/notificationSent） |
| `domain/vo/OfferDetailVO.java` | **候选人版 Offer 详情出参（方案A 新增）**：offerId(字符串)/jobId/jobTitle/companyName/salary/entryDate/level/remark/status/expiresAt/acceptedAt/rejectedAt/rejectReason/createdAt |
| `service/HrOfferService.java` + `service/impl/HrOfferServiceImpl.java` | 8 接口业务实现 |
| `domain/dto/InternalOfferAcceptDTO.java` | 内部接口入参（`{applicationId @NotNull}`，C 仅持有投递ID）—— 反向同步补丁（2026-08-08） |
| `domain/dto/InternalOfferRejectDTO.java` | 内部接口入参（`{applicationId @NotNull, rejectReason 选填}`）—— 反向同步补丁 |
| `controller/InternalOfferController.java` | `POST /internal/offers/accept` / `POST /internal/offers/reject`，供 C 端反向同步 hr_offer + confirm/release HC，返回 `Result<Void>` —— 反向同步补丁 |
| `service/notify/OfferNotifier.java` | Offer 通知（候选人 OFFER_RECEIVED / HR OFFER_MANAGE） |
| `controller/HrOfferController.java` | 8 个接口 |
| `scheduler/OfferExpireScheduler.java` | Offer 过期扫描（5min） |
| `scheduler/HcReconcileScheduler.java` | HC 补偿对账（每小时） |

### 无需改动
| 文件 | 说明 |
|------|------|
| `common/enums/OfferStatus.java` | SENT/ACCEPTED/REJECTED/EXPIRED/WITHDRAWN + `isFinal()`，复用 |
| `exception/HrErrorCode.java` | 4200-4205 已齐（OFFER_NOT_FOUND/OFFER_STATUS_ERROR/OFFER_EXPIRED/OFFER_HC_INSUFFICIENT/OFFER_URGE_LIMIT/OFFER_ALREADY_EXISTS） |
| `feign/ResumeFeignClient.java` | `getApplication` / `updateApplicationStatus`（OFFERABLE→OFFERED/REJECTED） |
| `feign/UserFeignClient.java` | 候选人姓名拼装 |
| `feign/NotificationFeignClient.java` | Offer 通知落库 |
| `HrApplication.java` | `@EnableScheduling` 已在 |

---

## 二、API 接口（系分 5.5.4）

### 1. 发起 Offer
`POST /api/v1/hr/offers`（**HR_ADMIN**）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| applicationId | Long | 是 | 投递记录ID |
| salary | Integer | 是 | 月薪（元，>0） |
| entryDate | LocalDate | 是 | 入职日期（>今天） |
| level | String | 否 | 职级 |
| remark | String | 否 | 备注 |
| expiresInDays | Integer | 否 | 有效期天数，默认 3（用户确认 2026-08-07；系分原 7） |

**响应** `Result<OfferCreateResultVO>`：`offerId` / `status=SENT` / `expiresAt` / `notificationSent` / `salaryWarning`（薪资超范围软提示，见步骤 8）

**业务逻辑：**
1. `requireHrAdmin(companyId)`（INTERVIEWER → 4011）
2. `fetchApplication(applicationId, companyId)`：C 侧 getApplication + 跨企业校验（4300/4301）
3. 校验：投递 `status==OFFERABLE`（否则 4201）；`entryDate > today`、`salary > 0`（否则 400 参数错误）；`expiresInDays∈[1,30]`
4. 重复发起校验（2026-08-07 前端清单，已删唯一索引）：`selectByApplicationId` 取**最新一条**，仅 `SENT`（活跃）/`ACCEPTED`/`REJECTED`（终态）→ **4205**；`WITHDRAWN`/`EXPIRED` 旧记录**放行可重发**（并发双发由应用层校验兜底，无 DB 唯一约束）
5. 生成 `offerId = SnowflakeIdUtil.nextId()`（Long）
6. Feign `getJobForValidation(jobId)`：取 `salaryMinAmount`/`salaryMaxAmount`/`salaryNegotiable` 供薪资软提示（B 岗位不存在/删除 → 透传 B 错误码）
7. 事务内 Feign `reserveHc(jobId, {companyId, offerId, candidateId})`：B 返回 2201(无可用HC) → 转 **4203**；其余失败 → 透传（✅ 实际在事务内，失败整体回滚）
8. 事务内插入 hr_offer（status=SENT，expiresAt=NOW()+days）→ 投递联动 `OFFERABLE→OFFERED`；**插入/联动抛异常 → 补偿 `releaseHc(reason=D_PERSIST_FAILED)`**（幂等）后抛出
9. 通知候选人（`OFFER_RECEIVED`，best-effort），返回。`salaryWarning`：`salaryNegotiable==false` 且 `salary` 超出 `[salaryMinAmount, salaryMaxAmount]`（范围存在时）→ `{warn:true, message:"薪资超出岗位范围，请确认"}`，不拦截

### 2. Offer 列表
`GET /api/v1/hr/offers`（**HR_ADMIN**）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|:---:|------|
| status | String | 否 | - | SENT/ACCEPTED/REJECTED/EXPIRED/WITHDRAWN |
| dateRange | String | 否 | - | TODAY/WEEK/MONTH，**按 createdAt 过滤**（前端 P0，2026-08-07 确认补） |
| jobId | Long | 否 | - | 按岗位筛选 |
| page | Integer | 否 | 1 | 页码 |
| size | Integer | 否 | 20 | 每页（≤100） |

**响应** `Result<PageResult<OfferVO>>`，字段：
```
offerId(字符串) applicationId candidateId candidateName jobId jobTitle salary entryDate level
status statusDesc(枚举desc) expiresAt urgeCount rejectReason acceptedAt rejectedAt createdAt
```
- 拼装：candidateName → `fetchUsers`（batch→单查→兜底"用户"+id）；jobTitle → `fetchApplication(jobId)` 或降级"岗位"+jobId
- `statusDesc` 用 `OfferStatus.fromCode(status).getDesc()`（try-catch 兜底原值）
- `offerId` 用 `@JsonSerialize(ToStringSerializer)` 以字符串返回

### 3. HC 概览
`GET /api/v1/hr/offers/hc-overview?jobId=`（**HR_ADMIN**，**jobId 可空**，前端 P0 2026-08-07 确认）

**响应** `Result<HcOverviewVO>`：`jobId` / `jobTitle` / `totalHc` / `reservedHc` / `confirmedHc` / `availableHc`
- **传 jobId** → Feign `getJobForValidation(jobId)` 单岗映射；B 岗位不存在/删除 → 透传 B 错误码
- **不传 jobId** → **公司级聚合**：Feign `listCompanyJobs(companyId)` 取本企业全部 jobId → 逐个 `getJobForValidation` 求和（`jobId=null`、`jobTitle="全公司"`、四项 HC 求和）；本企业无岗位 → 返回全 0；聚合期间单个岗位查询失败 → 记日志跳过该岗（best-effort），不整体失败

### 4. 催促确认
`POST /api/v1/hr/offers/{offerId}/urge`（**HR_ADMIN**）
- 校验 Offer 存在 + 本企业（4200）、`status==SENT`（4201）
- 频率限制：`lastUrgeAt` 距今 <24h 且 `urgeCount>=2` → **4204**；否则 `urgeCount+1`、`lastUrgeAt=NOW()`（`updateUrge` 条件更新）
- 通知候选人（`OFFER_RECEIVED`，best-effort）

### 5. 撤回 Offer
`POST /api/v1/hr/offers/{id}/retract`（**HR_ADMIN**）
- 校验存在 + 本企业（4200）、`status==SENT`（4201）
- 条件更新 `status→WITHDRAWN`（乐观锁 rows=0 → 4201）
- Feign `releaseHc(reason=REJECTED)`（幂等；B 释放原因白名单无 WITHDRAWN，用 REJECTED 语义为"offer 被拒/撤回"）→ 失败仅记日志不阻塞
- **投递回退 `OFFERED→OFFERABLE`**（best-effort，失败仅记日志；C 新状态机 2026-08-07）
- 通知候选人（`OFFER_RECEIVED`「Offer 已撤回」，best-effort）

### 6. 候选人接受 Offer（内部/C端）
`POST /api/v1/hr/offers/{id}/accept`（**候选人身份：`userId==offer.candidateId`**）
1. 鉴权：`UserContext.getUserId() != offer.candidateId` → 401；`getCompanyId()` 为空不校验（候选人无企业）
2. `selectByIdForUpdate`（行锁）→ 校验存在（4200）
3. 幂等：`status==ACCEPTED` → 直接返回成功（不重复走流程）
4. 状态校验：`status==SENT` 且 `expires_at > NOW()`（否则 **4201** 或 **4202**）
5. Feign `confirmHc(jobId, {companyId, offerId})`：失败（B 2204 已释放/状态非法 → 提示**"名额不足，请联系HR"**，Offer 保持 SENT，**不更新本地**）
6. confirm 成功 → 更新 Offer `status=ACCEPTED`、`accepted_at=NOW()`（乐观锁）
7. 投递 `OFFERED→OFFER_ACCEPTED`（已录用，C 新状态机；失败抛异常回滚 Offer 状态）
8. 返回 `OfferActionResultVO`（offerStatus/applicationStatus/notificationSent）

### 7. 候选人拒绝 Offer（内部/C端）
`POST /api/v1/hr/offers/{id}/reject`（**候选人身份**）

请求体：`{"rejectReason": "薪资不匹配"}`（选填）

1. 鉴权同上
2. 行锁 → 存在校验（4200）→ 幂等（已 REJECTED 直接成功）→ `status==SENT` 且未过期（4201/4202）
3. 更新 Offer `status=REJECTED`、`rejected_at=NOW()`、`rejectReason`（乐观锁）
4. Feign `releaseHc(reason=REJECTED)`：**失败仅记日志不阻塞**（排期明确；已拒绝不重复占用，由对账兜底）
5. 投递 `OFFERED→OFFER_DECLINED`（已拒绝，C 新状态机）
6. 返回 `OfferActionResultVO`

### 8. 候选人查看 Offer 详情（跨端方案A，新增）
`GET /api/v1/hr/offers/{offerId}`（**候选人身份：`userId==offer.candidateId`**）

**响应** `Result<OfferDetailVO>`：
```
offerId(字符串) applicationId jobId jobTitle companyName salary entryDate level remark
status statusDesc expiresAt acceptedAt rejectedAt rejectReason createdAt
```
- 鉴权：`UserContext.getUserId() != offer.candidateId` → 401（候选人无企业，不校验 companyId）
- Offer 不存在 → 4200
- 拼装：jobTitle → `fetchApplication`（降级"岗位"+jobId）；companyName → `hr_company` 按 `company_id` 查询（失败置 null，不阻塞）
- 供 C 端投递追踪页「Offer 待确认」卡片展示 + accept/reject 跳转（OFFER_RECEIVED 通知 targetId=offerId）

> **🔀 双链路并存（2026-08-08 补丁）**：候选人接受/拒绝 Offer 除上述 D 端接口外，还有 **C 端投递追踪页链路** `PUT /api/v1/applications/{id}/offer/accept|decline`（C 侧改投递状态 + 调 D `/internal/offers/accept|reject` 反向同步 hr_offer + confirm/release HC）。D 内部抽 `doAcceptOffer/doRejectOffer(offer, syncApplication)`：D 端入口 `syncApplication=true`（联动投递状态），C 反向入口 `syncApplication=false`（投递状态由 C 自己改，避免行锁死锁）。详见 `Offer接受拒绝-反向同步-coding-plan.md`。

---

## 二·五、Offer 三层状态流转明细（hr_offer / resume_application / job_hc_reservation）

> 三层同时流转：Offer 本身（hr_offer）、投递状态（resume_application，C 侧）、HC 流水（job_hc_reservation，B 侧）。
> 投递状态为 C 新状态机（OFFERED=待录用过程态 + OFFER_ACCEPTED/OFFER_DECLINED 终态，2026-08-07）。

### ① 发起 Offer（HR）
```
投递: OFFERABLE（可录用） ──► OFFERED（待录用）
Offer:                    ──► SENT（expiresAt=NOW+3天）
HC:                       ──► RESERVED（预冻结）
```

### ② 候选人接受（POST /offers/{id}/accept）
```
投递: OFFERED（待录用） ──► OFFER_ACCEPTED（已录用）★终态
Offer: SENT ──► ACCEPTED（acceptedAt）
HC:   RESERVED ──► CONFIRMED（转正式占用）★终态
```

### ③ 候选人拒绝（POST /offers/{id}/reject）
```
投递: OFFERED（待录用） ──► OFFER_DECLINED（已拒绝）★终态
Offer: SENT ──► REJECTED（rejectedAt + rejectReason）
HC:   RESERVED ──► RELEASED（释放）★终态
```

### ④ HR 撤回（POST /offers/{id}/retract）
```
投递: OFFERED（待录用） ──► OFFERABLE（回退，best-effort）
Offer: SENT ──► WITHDRAWN ★终态
HC:   RESERVED ──► RELEASED ★终态
```

### ⑤ 超时过期（定时任务 5min 扫描）
```
投递: OFFERED（待录用） ──► OFFERABLE（回退，best-effort）
Offer: SENT ──► EXPIRED ★终态
HC:   RESERVED ──► RELEASED ★终态
```

### ⑥ HC 补偿对账（定时任务 1h，兜底）
```
Offer: REJECTED / EXPIRED / WITHDRAWN（终态）
       │ 若 HC 流水仍 RESERVED/CONFIRMED
       ▼
HC:   RESERVED / CONFIRMED ──► RELEASED（幂等）
       │
       ▼
Offer: 回写 last_sync_time
```

**终态汇总：**

| 层 | 终态 | 含义 |
|----|------|------|
| hr_offer | ACCEPTED / REJECTED / EXPIRED / WITHDRAWN | 除 SENT 外全为终态 |
| 投递 | OFFER_ACCEPTED / OFFER_DECLINED（+ 其他流程的 REJECTED/WITHDRAWN） | 候选人表态后终态 |
| HC 流水 | CONFIRMED / RELEASED | 占用或释放 |

**关键联动约束：**
- 接受/拒绝互斥：Offer 行锁 `SELECT FOR UPDATE` + 乐观锁 `WHERE status='SENT'`，先到者成功，后者 4201
- 幂等：已 ACCEPTED/REJECTED 重复点击直接返回成功
- 接受前置：SENT 且未过期，否则 4201/4202；confirm HC 失败 → Offer 保持 SENT，提示「名额不足」
- 撤回/过期回退：投递 OFFERED→OFFERABLE 为 best-effort（失败仅记日志）
- 唯一约束：**已删除 `uk_application_id`**（2026-08-07）→ `WITHDRAWN`/`EXPIRED` 后可重发；`SENT`/`ACCEPTED`/`REJECTED` 仍拦 4205（应用层校验，无 DB 兜底）
- release 失败不阻塞：由每小时 HC 对账幂等兜底

---

## 三、HC Feign 契约（对齐 B 侧 `InternalJobController`）

| D 方法 | B 端点 | 请求体（对齐后） | 响应 |
|--------|--------|------------------|------|
| `reserveHc` | `POST /internal/jobs/{jobId}/hc/reserve` | `{companyId, offerId, candidateId}` | `HcReserveResponse`（totalHc/reservedHc/confirmedHc/availableHc/…） |
| `confirmHc` | `POST /internal/jobs/{jobId}/hc/confirm` | `{companyId, offerId}` | `HcConfirmResponse` |
| `releaseHc` | `POST /internal/jobs/{jobId}/hc/release` | `{companyId, offerId, reason}` | `HcReleaseResponse` |
| `getJobForValidation`（新增） | `GET /internal/jobs/{jobId}` | - | `InternalJobValidationResponse`（B 已实现，含 HC/薪资字段） |
| `listCompanyJobs`（新增） | `GET /internal/jobs/company/{companyId}` | - | `List<InternalCompanyJobVO>`（jobId/title/status/availableHc，B 已实现） |

- B 错误码映射：`2201 无可用HC` → `4203 OFFER_HC_INSUFFICIENT`；`2202 流水不存在`/`2204 非法状态` → 透传或转 `4201`
- B 释放原因白名单：`REJECTED / EXPIRED / NOT_ONBOARDED / D_PERSIST_FAILED`（撤回与拒绝均用 `REJECTED`，过期与对账用 `EXPIRED`）
- `getHcFlow`（`/jobs/{jobId}/hc/flow`）B 未实现 → **删除**；如后续 B 补流水查询，对账可改为先查后放

---

## 四、Service 层

### HrOfferService 接口
```java
OfferCreateResultVO createOffer(Long companyId, OfferCreateDTO dto);
PageResult<OfferVO> listOffers(Long companyId, String status, String dateRange, Long jobId, Integer page, Integer size);
HcOverviewVO getHcOverview(Long companyId, Long jobId);   // jobId 可空，空=公司级聚合
void urgeOffer(Long companyId, Long offerId);
void retractOffer(Long companyId, Long offerId);
OfferDetailVO getOfferDetail(Long offerId);               // 候选人版，userId==candidateId
OfferActionResultVO acceptOffer(Long offerId);
OfferActionResultVO rejectOffer(Long offerId, String rejectReason);
OfferActionResultVO acceptOfferByApplication(Long applicationId);   // C 端反向（/internal/offers/accept），syncApplication=false
OfferActionResultVO rejectOfferByApplication(Long applicationId, String rejectReason); // C 端反向（/internal/offers/reject），syncApplication=false
int expireExpiredOffers();   // 定时任务：Offer 过期扫描（SENT 且过期 → EXPIRED + release + 通知）
int reconcileHc();           // 定时任务：HC 补偿对账（终态未对账 → release 幂等 → 回写 last_sync_time）
```

### 关键实现说明
- **权限**：`requireHrAdmin`（`requireActiveMember` + role==HR_ADMIN）供 5 个 HR 接口；accept/reject 单独 `requireCandidateOwner(offer)`（`userId==candidateId`）。`requireActiveMember`/`fetchApplication`/`fetchUsers`/`updateApplicationStatus` 与 HrInterviewServiceImpl 重复，按既有约定自包含复制。
- **行锁/并发**：accept/reject 用 `selectByIdForUpdate`；状态变更统一乐观锁 `UPDATE ... WHERE id AND status=fromStatus`（rows=0 → 4201）。
- **事务边界**：createOffer 中 reserve 在事务外（失败直接抛，无本地脏数据）；仅 hr_offer insert 在事务内，插入异常补偿 release。accept 中 confirm → Offer 更新 → 投递更新在一个事务内（投递失败整体回滚，Offer 仍 SENT）。
- **定时任务**：两个 `@Scheduled` 方法 + `@Scheduled(cron=...)`，均 try-catch 单条隔离，失败记日志不中断。注意 `OfferExpireScheduler` 处理完一条后再处理下一条（避免长事务）。

---

## 五、定时任务

### 1. Offer 过期扫描 `@Scheduled(cron = "0 */5 * * * ?")`
```
1. selectExpiredSent()：WHERE status='SENT' AND expires_at<=NOW() 条件更新为 EXPIRED（rows=1 才处理，0 跳过=已被确认/撤回）
2. 更新成功 → Feign releaseHc(reason=EXPIRED)（幂等；失败记日志）
3. 通知候选人（OFFER_RECEIVED「Offer已过期」）+ HR（OFFER_MANAGE）best-effort
```

### 2. HC 补偿对账 `@Scheduled(cron = "0 0 * * * ?")`
```
1. selectTerminalForReconcile()：终态（REJECTED/EXPIRED/WITHDRAWN）且 last_sync_time IS NULL
2. 逐条 → Feign releaseHc(reason 按 offer.status 映射 EXPIRED/REJECTED)（幂等，B 已 RELEASED 无副作用）
3. release 成功或 B 返回"已释放" → 更新 last_sync_time=NOW()；失败记日志，下轮重试
```
> ACCEPTED 不入对账（应 CONFIRMED，无需释放）。仅扫描"本应释放却可能未释放"的终态。

---

## 六、边界场景

| 场景 | 处理 |
|------|------|
| 未登录 / 无企业 | 401 |
| INTERVIEWER 访问 Offer（发起/列表/概览/催促/撤回） | 4011（面试官 Offer 不可见，系分 5.6.5） |
| 候选人非 offer.candidateId 调 accept/reject/详情 | 401 |
| 投递不存在 / 非本企业投递 | 4300 / 4301 |
| 投递非 OFFERABLE 发起 Offer | 4201 Offer状态不可操作（或 C 侧 3006 透传） |
| 已有 SENT/ACCEPTED/REJECTED Offer（重复发起） | 4205（最新一条状态为活跃/终态） |
| WITHDRAWN/EXPIRED 后再次发起（同投递） | **放行**（2026-08-07 已删唯一索引，前端 `hasOfferRecord`+`lastOfferStatus` 区分） |
| 并发双发起 | 应用层校验兜底（无 DB 唯一约束；概率低） |
| 薪资超出岗位范围 | 不拦截，响应 `salaryWarning`（salaryNegotiable=false 且超范围时） |
| reserve 时 HC 不足 | B 2201 → 4203 |
| reserve 成功但本地插入失败 | 补偿 release(D_PERSIST_FAILED) |
| accept 时 Offer 已过期 | 4202（或 4201） |
| accept 时 confirm HC 失败 | Offer 保持 SENT，提示"名额不足，请联系HR"（不落 ACCEPTED） |
| accept 后投递 OFFERABLE→OFFERED 失败 | 整体回滚，Offer 仍 SENT |
| reject 时 release 失败 | 记日志不阻塞（对账兜底） |
| 催促 24h ≥2 次 | 4204 |
| Offer 不存在 / 非本企业 | 4200 |
| 状态不可操作 | 4201 |
| 通知失败 | best-effort，notificationSent=false |

---

## 七、开发顺序

✅ **已完成（2026-08-07，commit `5fb2ee5`）**，顺序与实现一致：

1. 修正 `HcReserveRequest`/`HcReleaseRequest` + `JobFeignClient`（对齐 B 契约；删 getHcFlow；新增 getJobForValidation + listCompanyJobs + HcConfirmRequest）
2. 新增 `JobHcOverviewDTO`/`CompanyJobDTO`（映射 B InternalJobValidationResponse / InternalCompanyJobVO）
3. 写 `HrOfferMapper` + XML（insert/selectById/条件分页/ForUpdate/乐观锁/urge/sync/expired/reconcile/offerRecord）
4. 新建 DTO/VO（OfferCreateDTO/OfferRejectDTO/OfferVO/OfferCreateResultVO/HcOverviewVO/OfferActionResultVO/OfferDetailVO）
5. 实现 `OfferNotifier`（候选人 OFFER_RECEIVED / HR OFFER_MANAGE）
6. 实现 `HrOfferService` + `HrOfferServiceImpl`
7. 实现 `HrOfferController`（8 接口，含候选人详情）
8. 实现 `OfferExpireScheduler` + `HcReconcileScheduler`
9. JDK 8 编译通过 + 提交推送
10. **反向同步补丁（2026-08-08，commit `8622eed`）**：`HrOfferMapper` 新增 `selectByApplicationIdForUpdate` → 新建 `InternalOfferAcceptDTO`/`InternalOfferRejectDTO` → `HrOfferService(+Impl)` 抽 `doAcceptOffer/doRejectOffer(offer, syncApplication)` + 新增 `acceptOfferByApplication/rejectOfferByApplication` → 新建 `InternalOfferController`（`POST /internal/offers/accept|reject`）→ JDK 8 编译通过

---

## 七·五、本地联调结果（2026-08-08）

> ✅ **Offer 管理全链路联调通过**。主链路（发起→列表→HC概览→催促→撤回→接受→拒绝→过期→对账）与 D 端候选人接口（详情/accept/reject）均验证通过；C 端反向同步链路（`/internal/offers/accept|reject`）D 侧已就绪，待成员 C 接入 `HrOfferFeignClient` 后联调（见 `Offer接受拒绝-反向同步-coding-plan.md`）。

---

## 八、验证（本地联调）

> ✅ **本地全链路联调通过（2026-08-08）**。下列场景已逐条验证勾选。

环境：IDEA 起 hr（重启加载新代码），MySQL/Redis/Nacos 已就绪。HR：16735263528（hr_1，company 1 HR_ADMIN）；测试投递 50113（陈静，OFFERABLE，**已有 SENT Offer 60102**，正好测 4205 重复发起）。

**前置数据准备：**
```sql
-- 造一个无 SENT Offer 的 OFFERABLE 投递：把 50113 的 SENT Offer 60102 改为 REJECTED 占位
UPDATE hr_offer SET status='REJECTED', rejected_at=NOW() WHERE id=60102;
-- 或：评估一场面试产出新的 OFFERABLE 投递（复用 Day 4-5 联调数据）
```

**接口链路（✅ = 联调通过）：**
- ✅ 1. 发起 Offer（50113，job 7，candidate 24）→ reserve 成功 + hr_offer 落库 SENT + 候选人 OFFER_RECEIVED 通知落库；**响应 offerId 为 JSON 字符串**
- ✅ 2. 重复发起（50113 再发起）→ **4205**；`GET /candidates/list?status=OFFERABLE` 该投递 `hasOfferRecord=true` + `lastOfferStatus=SENT`
- ✅ 3. Offer 列表 → 拼装候选人名/岗位名/statusDesc；status/jobId/**dateRange** 筛选生效；**offerId 字符串**
- ✅ 4. HC 概览（jobId=7）→ totalHc/reservedHc/confirmedHc/availableHc 与 B 侧一致；**不传 jobId → 公司级聚合（全公司四项求和）**
- ✅ 5. 催促 → SENT 正常 +1；把 last_urge_at 改到 23h 前并 urge_count=2 → 再催 → **4204**
- ✅ 6. 撤回（新发起一个 SENT）→ Offer WITHDRAWN + HC release + 通知
- ✅ 7. accept（候选人 token 调）→ Offer ACCEPTED + 投递 OFFERED→**OFFER_ACCEPTED** + confirm 成功
- ✅ 8. 详情（候选人 token 调 `GET /offers/{id}`）→ 返回 salary/entryDate/jobTitle/companyName/expiresAt；非本人 → 401
- ✅ 9. 重复 accept → 幂等直接成功
- ✅ 10. reject（候选人 token）→ Offer REJECTED + 投递 OFFERED→**OFFER_DECLINED** + release
- ✅ 11. 撤回/过期 → 投递回退 **OFFERABLE**
- ✅ 12. 过期：把某 SENT Offer expires_at 改到过去 → 手动触发 `expireOffers()` → Offer EXPIRED + HC release + 通知
- ✅ 13. 对账：把某 REJECTED Offer last_sync_time 置 NULL + 其 HC 流水改回 RESERVED → 触发 `reconcile()` → 流水 RELEASED + last_sync_time 更新
- ✅ 14. 权限：INTERVIEWER 调发起/列表 → 4011；非候选人调 accept → 401
- ✅ 15. 薪资软提示：salary 超岗位范围 → 响应 `salaryWarning.warn=true` 且不拦截
- ✅ 16. 重发验证（2026-08-07）：撤回 Offer（→WITHDRAWN）后对同投递**再次发起成功**（新 SENT 记录）；再撤回→EXPIRED 后再次发起成功；但 ACCEPTED/REJECTED 后再次发起 → **4205**

---

## 九、依赖 / 待确认（需在开工前确认）

| # | 项 | 类型 | 现状 | 决策 |
|---|----|------|------|------|
| 1 | **C 投递状态机扩展（新枚举）** | 依赖 | C 已更新 ApplicationStatus（OFFERED=待录用 + 新增 OFFER_ACCEPTED/OFFER_DECLINED） | **✅ 已落地**：C `ApplicationServiceImpl.java:82-84` TRANSITION_MAP 含 `OFFERABLE→{OFFERED,REJECTED}`、`OFFERED→{OFFER_ACCEPTED,OFFER_DECLINED,OFFERABLE}`（原 `OFFERABLE→WITHDRAWN` 需求作废） |
| 2 | **B 的 `hc/flow` 流水查询未实现** | 依赖（已绕） | D 的 getHcFlow 是死代码 | **✅ 已删除**：对账直接调幂等 release，本期不依赖；B 后续补则恢复 |
| 3 | **撤回 Offer 的投递状态 + 再次发起** | 确认 | 排期未写明 | **已确认（2026-08-06 + 08-07，已实现）**：撤回/过期投递**回退 `OFFERABLE`**（best-effort）；**再次发起**：2026-08-07 前端清单**推翻原「不可再发」**——删 `uk_application_id` 唯一索引，仅 `WITHDRAWN`/`EXPIRED` 可重发，`SENT`/`ACCEPTED`/`REJECTED` 拦 4205 |
| 4 | **催促/过期 HR 侧通知 type** | 确认 | ChatConstant 有 OFFER_MANAGE | 用 OFFER_MANAGE 归类 HR 侧 Offer 提醒 |
| 5 | **accept/reject 路径鉴权** | 确认 | 走 /api/v1/hr/**（A 网关已放行） | 候选人 token 可调；`userId==candidateId` 校验 |
| 6 | **offerId 字符串序列化**（前端 P0） | 确认 | 无全局 Long→String 先例 | **已确认（2026-08-07，已实现）**：`@JsonSerialize(ToStringSerializer)` 仅注解 OfferVO/OfferCreateResultVO/OfferDetailVO 的 offerId，不做全局 |
| 7 | **hc-overview jobId 可空**（前端 P0） | 确认 | B listCompanyJobs 已实现 | **已确认（2026-08-07）**：空=公司级聚合（listCompanyJobs→逐岗 getJobForValidation 求和） |
| 8 | **candidates 列表 hasOfferRecord + lastOfferStatus**（前端 P0） | 确认 | 涉及改 Day 3 代码 | **已确认（2026-08-07，已实现）**：CandidateVO 已有 `hasOfferRecord`（任意状态）+ 新增 `lastOfferStatus`（每投递最新 Offer 状态）；Mapper 新增 `selectLastOfferStatus` |
| 9 | **Offer 列表 dateRange**（前端 P0） | 确认 | 系分有 dateRange | **已确认（2026-08-07）**：补 TODAY/WEEK/MONTH，按 createdAt |
| 10 | **accept/reject 前端入口在 C 端投递追踪页**（跨端） | 联调提醒 | 前端成员C 页面触发 | **方案A 已确认（2026-08-07）**：通知跳转 + D 新增候选人版详情接口 `GET /offers/{id}`（接口数 7→8）；联调验收时拉前端 C |
| 11 | **C 端接受/拒绝 Offer 反向同步**（补丁） | 新增 | C 侧 `acceptOffer/declineOffer` 只改投递状态、**不改 hr_offer** | **已确认并实现（2026-08-08，commit `8622eed`）**：D 新增 `/internal/offers/accept|reject` 供 C 反向调用（`syncApplication=false` 避免行锁死锁），详见 `Offer接受拒绝-反向同步-coding-plan.md`；C 侧接入（`HrOfferFeignClient` + 改造 acceptOffer/declineOffer）由成员 C 落地 |

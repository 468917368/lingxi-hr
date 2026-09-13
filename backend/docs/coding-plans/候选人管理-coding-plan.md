# 候选人管理 - Coding Plan

> **所属模块：** lingxi-hr | **排期：** Day 3
> **技术栈：** Spring Boot 2.7 + MyBatis + Redis + RocketMQ + Feign
> **参考：** 成员D开发排期 Day 3、系分文档 5.5.2 / 5.6.3 / 5.7 节、`docs/schema.sql` resume_application 表、成员D后端系分 一
> **范围：** 候选人列表（筛选/排序/分页，跨服务拼装）+ Top5 高潜推荐 + 标记合适/不合适
> **跨服务依赖：** lingxi-resume（成员C）投递数据 —— **契约已落地**（`006249d`，2026-08-05 合并本地并重启生效）；lingxi-user（成员A，✅）候选人姓名/电话/头像
> **状态：** ✅ **已完成并联调通过（2026-08-05）**——提交 `53b4039`（16 文件 +893 行）；列表/total、Top5 排序、标记状态机直转、4302 拦截、通知落库 `sys_notification` 全链路验证通过

---

## 〇、核心决策摘要

1. **契约变更（✅ 已与成员C确认 2026-08-03，✅ 已落地 2026-08-05）**：现有 `ResumeFeignClient.getApplicationList` 返回 `Result<List<ApplicationDTO>>`，无法回传分页 `total`。改为返回 `Result<PageResult<ApplicationDTO>>`，并透传 `minMatchScore`/`keyword`/`sortBy`，保证筛选/排序/分页在数据源（C 侧）完成、total 正确。**C 侧已通过 `006249d` 落地**（InternalApplicationController 返回 PageResult + 透传参数；`updateApplicationStatus` + rejectFeedback；状态机 `SUBMITTED→{VIEWED,SCREENED,REJECTED,WITHDRAWN}`），联调已验证列表 total、标记状态机直转。
2. **标记落选反馈**：`PUT /internal/applications/{id}/status` 增加可选 `rejectFeedback` 参数（JSON：`{"reason":"原因","suggestions":["建议"]}`），对应 `resume_application.reject_feedback`。**C 侧已落地**，联调验证 JSON 落库。
3. **AI 落选反馈**：Day 6-7 才接入 DeepSeek/百宝箱。本期提供 `RejectFeedbackGenerator` 接口 + 模板降级实现（按匹配度生成原因+2-3条建议），标注 TODO 待接真 LLM。
4. **通知（方案变更：MQ → Feign 直调）**：~~topic `hr-notification`（系分 5.6.3）~~。**改为 Feign 直调 lingxi-chat `POST /internal/notifications`**（`CandidateNotifier` + `NotificationFeignClient`，type 用 lingxi-chat `ChatConstant.RESUME_VIEWED`）。原因：lingxi-chat 通知服务是直接调用式、`hr-notification` topic 无消费者、WebSocket session 对象仅 lingxi-chat 进程持有。Feign 失败 best-effort 记日志、`notificationSent=false`，不阻塞标记操作。
5. **权限**：列表/Top5 要求本企业 ACTIVE 成员；标记为筛选决策，要求本企业 **HR_ADMIN**（复用成员管理 `requireHrAdmin` 逻辑）。
6. **跨企业隔离**：投递详情须归属本企业（`ApplicationDTO.companyId` 校验），否则抛 4301。

---

## 一、涉及文件清单

### 已就绪（无需修改）
| 文件 | 说明 |
|------|------|
| `domain/entity/HrCompanyMember.java` | 企业成员实体（权限校验用 `selectByCompanyAndUser`） |
| `feign/UserFeignClient.java` | 已有：`getUserById` / `batchUsers`（拼装候选人姓名/电话/头像） |
| `feign/dto/SysUserDTO.java` | 用户信息 DTO（name/phone/avatar） |
| `mapper/HrCompanyMemberMapper.java` | 已有：`selectByCompanyAndUser` / `selectById` |
| `exception/HrErrorCode.java` | 已有 4300/4301，需补 4302 |
| `common/PageResult` / `Result` / `UserContext` | 分页/统一响应/上下文（lingxi-common） |

### 需要修改
| 文件 | 当前 | 修改 |
|------|:---:|------|
| `feign/ResumeFeignClient.java` | 3 个方法 | `getApplicationList` 返回 `Result<PageResult<ApplicationDTO>>` + 加 `minMatchScore`/`keyword`/`sortBy` 参数；`updateApplicationStatus` 加可选 `rejectFeedback` |
| `feign/dto/ApplicationDTO.java` | 已有字段 | 加 `companyId`（归属校验）、`advantages`/`risks`（Top5 用，C 可空返回） |
| `exception/HrErrorCode.java` | 已有 4300/4301 | 补 `CANDIDATE_ALREADY_PROCESSED(4302)` |

### 需要新建
| 文件 | 说明 |
|------|------|
| `domain/vo/CandidateVO.java` | 候选人列表项 |
| `domain/vo/TopCandidateVO.java` | Top5 高潜推荐项 |
| `domain/vo/MarkCandidateResultVO.java` | 标记结果出参 |
| `domain/dto/MarkCandidateDTO.java` | 标记入参（action 校验） |
| `domain/dto/RejectFeedbackDTO.java` | AI 落选反馈（reason + suggestions） |
| `service/HrCandidateService.java` | 候选人服务接口 |
| `service/impl/HrCandidateServiceImpl.java` | 业务实现 |
| `service/ai/RejectFeedbackGenerator.java` | 落选反馈生成器接口 |
| `service/ai/impl/TemplateRejectFeedbackGenerator.java` | 模板降级实现（待接 DeepSeek） |
| `service/notify/CandidateNotifier.java` | **候选人事件通知器（Feign 直调 lingxi-chat，替代原 MQ 生产者方案）** |
| `feign/NotificationFeignClient.java` | lingxi-chat 通知服务 Feign（`POST /internal/notifications`） |
| `feign/dto/CreateNotificationRequest.java` | 通知请求体（对齐 lingxi-chat 契约） |
| `controller/HrCandidateController.java` | 3 个接口 |

> ✅ **2026-08-05 全部交付**（提交 `53b4039`）。`CandidateNotifyProducer`（MQ）方案废弃未实现，通知改 Feign 直调（见决策 4）。

---

## 二、API 接口（3 个，系分 5.5.2）

### 1. 候选人列表
`GET /api/v1/hr/candidates/list`

| 参数 | 类型 | 必填 | 默认 | 描述 |
|------|------|:---:|:---:|------|
| jobId | Long | 否 | - | 按岗位筛选 |
| status | String | 否 | - | SUBMITTED/VIEWED/SCREENED/INTERVIEWING |
| minMatchScore | Integer | 否 | 0 | 最低匹配度筛选 |
| keyword | String | 否 | - | 搜索关键词（候选人姓名/技能标签，C 侧过滤） |
| sortBy | String | 否 | matchScore | matchScore(默认)/submittedAt/aiScore |
| page | Integer | 否 | 1 | 页码 |
| size | Integer | 否 | 20 | 每页条数（上限 100） |

**响应**：`Result<PageResult<CandidateVO>>`，字段：
```
id(=applicationId) candidateId candidateName phone avatar jobId jobTitle status matchScore aiScore appliedAt
```
- 拼装：投递分页来自 C；`candidateName`/`phone`/`avatar` 来自 A（批量优先 → 单查降级 → name="用户"+userId、avatar=null）
- 排序说明：`matchScore`/`aiScore` 为投递快照字段（A 的 Job Agent 投递时计算），`submittedAt` 为投递时间，均由 C 侧排序

### 2. Top5 高潜推荐
`GET /api/v1/hr/candidates/top5`

| 参数 | 类型 | 必填 | 描述 |
|------|------|:---:|------|
| jobId | Long | 否 | 不传返回本企业所有岗位 Top5 |

**响应**：`Result<List<TopCandidateVO>>`：
```
rank candidateId applicationId candidateName matchScore aiScore advantages risks
```
- 取数：C 按 `matchScore DESC, aiScore DESC` 返回前 5，不足 5 人返回实际数量
- `advantages`/`risks` 来自 `ApplicationDTO`（C 侧 AI/Job Agent 生成，未就绪前为 null）
- `applicationId` 透传 `ApplicationDTO.id`：前端 Top5 卡片「查看简历」直接调 `GET /candidates/{applicationId}/resume`，不再依赖列表匹配

### 3. 标记合适/不合适
`PUT /api/v1/hr/candidates/{applicationId}/mark`

**请求体**：`{"action": "SUITABLE" | "UNSUITABLE"}`（`@Pattern` 校验）

**响应**：`Result<MarkCandidateResultVO>`：
```
{ "applicationId": 5001, "newStatus": "SCREENED", "newStatusDesc": "筛选通过", "notificationSent": true }
```

---

## 三、Feign 契约调整（✅ 已与成员C确认，2026-08-03；C 实现未就绪前 mock 自测）

### ResumeFeignClient（3 处调整）
```java
// 1. 列表：返回分页对象 + 透传筛选/排序参数
@GetMapping("/applications/list")
Result<PageResult<ApplicationDTO>> getApplicationList(
        @RequestParam("companyId") Long companyId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "jobId", required = false) Long jobId,
        @RequestParam(value = "minMatchScore", required = false) Integer minMatchScore,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "sortBy", required = false) String sortBy,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "pageSize", required = false) Integer pageSize);

// 2. 详情：不变（用途：D 侧标记候选人时校验，非前端展示）
//    返回完整投递记录，其中 companyId 必须带（跨企业隔离校验，resume_application 无此列需 C 关联 job 表回填）
Result<ApplicationDTO> getApplication(@PathVariable("id") Long id);

// 3. 状态更新：加可选 rejectFeedback（JSON 字符串，仅 REJECTED 时传）
@PutMapping("/applications/{id}/status")
Result<Void> updateApplicationStatus(@PathVariable("id") Long id,
                                     @RequestParam("status") String status,
                                     @RequestParam(value = "rejectFeedback", required = false) String rejectFeedback);
```

> **投递详情用途说明**：`GET /internal/applications/{id}` 不是前端页面展示接口，是 D 侧「标记候选人合适/不合适」时的内部校验数据 ——
> ① 归属校验：读 `companyId` 确认投递属于本企业（否则 4301）；
> ② 状态机校验：读 `status` 判断是否可标记（仅 SUBMITTED/VIEWED，否则 4302）；
> ③ 通知拼装：读 `candidateId`/`jobTitle` 构造标记后的通知文案（Feign 直调 lingxi-chat）。
> 已与成员C确认该字段集合（id/jobId/jobTitle/candidateId/resumeId/companyId/status/matchScore/aiScore/appliedAt，advantages/risks 可空）。

### ApplicationDTO 追加字段
```java
private Long companyId;              // 投递归属企业（校验跨企业隔离）
private List<String> advantages;     // Top5 优势（C/Job Agent 生成，可空）
private List<String> risks;          // Top5 风险（同上，可空）
```

### 与成员C的对齐点（✅ 2026-08-03 已确认，无异议）
| 项 | 说明 | 状态 |
|------|------|:---:|
| `getApplicationList` 返回类型 | 需 C 返回 `Result<PageResult<ApplicationDTO>>`，否则拿不到 `total` | ✅ |
| 透传 `keyword`/`minMatchScore`/`sortBy` | keyword 匹配候选人姓名/技能标签，需 C 关联用户/简历数据 | ✅ |
| `ApplicationDTO.companyId` | `resume_application` 无 company_id 列，需 C 关联 job 表回填 | ✅ |
| `ApplicationDTO.advantages/risks` | `resume_application` 无此列，需 C 的 Job Agent/AI 生成（未就绪可空） | ✅ |
| `updateApplicationStatus` 的 `rejectFeedback` | 对应 `resume_application.reject_feedback` JSON 列 | ✅ |

---

## 四、Service 层

### HrCandidateService 接口
```java
PageResult<CandidateVO> listCandidates(Long companyId, CandidateQueryDTO query);
List<TopCandidateVO> topCandidates(Long companyId, Long jobId);
MarkCandidateResultVO markCandidate(Long companyId, Long applicationId, MarkCandidateDTO dto);
```

### listCandidates — 业务逻辑
```
1. companyId 为 null → 抛 401
2. 校验当前用户为本企业 ACTIVE 成员（selectByCompanyAndUser + status=ACTIVE），否则抛 4011
3. 参数归一：page 默认1(size<1→1)、size 默认20(>100→100)、minMatchScore 默认0、sortBy 默认 matchScore
4. Feign 调 C：getApplicationList(companyId, status, jobId, minMatchScore, keyword, sortBy, page, size)
   - 返回 Result<PageResult<ApplicationDTO>>，失败/降级：
     · 非分页返回（列表拿不到 total）→ 抛 500 "候选人列表查询失败，请稍后重试"
     · 调用异常 → 记日志，同样抛 500（列表不能降级为空，前端依赖分页结构）
5. 提取本页 candidateId 去重 → fetchUsers(userIds)（batch 优先 → 单查降级 → 兜底 name="用户"+id, phone="***", avatar=null）
6. 组装 List<CandidateVO>，返回 PageResult.of(list, total, page, size)
```

### topCandidates — 业务逻辑
```
1. companyId 为 null → 抛 401；校验 ACTIVE 成员 → 否则 4011
2. 调 C：getApplicationList(companyId, status=null, jobId, minMatchScore=null, keyword=null, sortBy="matchScore", page=1, pageSize=5)
   - 排序契约：C 侧 matchScore DESC + aiScore DESC 取前 5
   - 失败：记日志，返回空列表 []（Top5 为推荐性质，降级为空不阻塞）
3. fetchUsers 拼装 candidateName
4. 组装 TopCandidateVO（rank 从 1 起，applicationId=app.id），不足 5 人返回实际数量
```

> **说明**：系分 5.5.2 未要求匹配度 ≥70% 门槛；成员D后端系分 line 1116 有「匹配度 <70% 不进入 Top5」表述，与排期冲突，本期不实现该过滤，见遗留确认。

### markCandidate — 业务逻辑（核心）
```
1. companyId 为 null → 抛 401
2. 权限校验：requireHrAdmin(companyId)（mark 为筛选决策，仅 HR_ADMIN，复用成员管理逻辑）
3. Feign 调 C：getApplication(applicationId)
   - 返回 null / 非成功 → 抛 4300 候选人不存在
   - application.getCompanyId() 与 companyId 不等 → 抛 4301 无权查看该候选人
4. 状态机校验（resume_application 状态机：SUBMITTED→VIEWED→SCREENED→INTERVIEWING→OFFERABLE→OFFERED；终态 REJECTED/WITHDRAWN/OFFERED）：
   - 仅 SUBMITTED / VIEWED 允许标记
   - SCREENED / REJECTED（已标记过）→ 抛 4302 "该候选人已处理，请勿重复操作"
   - INTERVIEWING / OFFERABLE / OFFERED / WITHDRAWN → 抛 4302（已进入后续流程/终态，不可再标记）
5. action=SUITABLE：
   - Feign updateApplicationStatus(id, "SCREENED")，失败透传 C 错误码
   - Feign 直调 lingxi-chat 通知候选人（`CandidateNotifier.notifyScreenedPass`，type=RESUME_VIEWED，best-effort）
   - 返回 { applicationId, "SCREENED", "筛选通过", notificationSent }
6. action=UNSUITABLE：
   - feedback = rejectFeedbackGenerator.generate(application)（模板降级，TODO 接 DeepSeek）
   - Feign updateApplicationStatus(id, "REJECTED", JsonUtil.toJson(feedback))，失败透传
   - Feign 直调 lingxi-chat 通知候选人（`CandidateNotifier.notifyRejected`，type=RESUME_VIEWED + 落选反馈，best-effort）
   - 返回 { applicationId, "REJECTED", "已淘汰", notificationSent }
```

### 通知发送（CandidateNotifier，Feign 直调 lingxi-chat）
```
1. 构造请求：{ userId=candidateId, type="RESUME_VIEWED", title, content, targetType="application", targetId=applicationId }
2. notificationFeignClient.createNotification(request) → lingxi-chat POST /internal/notifications
3. lingxi-chat 内部写 sys_notification 表 + 用户在线时 WebSocket 实时推送（/ws/message）
4. 全程 try-catch：失败记 warn 日志，返回 false；调用方不阻塞标记主流程
```

---

## 五、Controller 层

```java
@Slf4j
@RestController
@RequestMapping("/api/v1/hr/candidates")
@RequiredArgsConstructor
@RequireLogin
public class HrCandidateController {

    @GetMapping("/list")
    public Result<PageResult<CandidateVO>> listCandidates(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "0") Integer minMatchScore,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "matchScore") String sortBy,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size);

    @GetMapping("/top5")
    public Result<List<TopCandidateVO>> topCandidates(@RequestParam(required = false) Long jobId);

    @PutMapping("/{applicationId}/mark")
    public Result<MarkCandidateResultVO> markCandidate(@PathVariable Long applicationId,
                                                       @Validated @RequestBody MarkCandidateDTO dto);
}
```

---

## 六、边界场景

| 场景 | 处理 |
|------|------|
| 未登录 / 无企业 | companyId 为 null → 401 |
| 非本企业成员访问列表 | `selectByCompanyAndUser` 空 或 status!=ACTIVE → 4011 |
| 非 HR_ADMIN 标记 | 标记仅 HR_ADMIN → 4011 |
| 投递记录不存在 | `getApplication` 空/失败 → 4300 |
| 跨企业投递 | application.companyId ≠ companyId → 4301 |
| 已标记过（SCREENED/REJECTED） | 4302 该候选人已处理，请勿重复操作 |
| 已进入面试/Offer/撤回（INTERVIEWING/OFFERABLE/OFFERED/WITHDRAWN） | 4302 不可再标记 |
| Feign 获取投递分页失败 | 抛 500 候选列表查询失败（前端依赖分页结构，不降级为空） |
| 用户信息 Feign 不可用 | 列表不阻塞：name="用户"+userId、phone="***"、avatar=null |
| Top5 依赖不可用 | 返回空列表 []（推荐性质，不阻塞） |
| 投递状态更新失败 | 透传 C 的错误码/消息（如 3202 投递状态不允许此操作） |
| MQ 未就绪/发送失败 | best-effort 记日志，`notificationSent=false`，标记操作成功 |
| action 非法 | DTO `@Pattern` 校验，400 |

---

## 七、错误码（补充 HrErrorCode 4302）

> 4007 保留空号不占用；4302 为 4300-4399 段空闲码。

| code | 说明 | 触发场景 |
|------|------|------|
| 4300 | 候选人不存在 | 投递记录查询为空 |
| 4301 | 无权查看该候选人 | 投递不属于本企业 |
| 4302 | 该候选人已处理，请勿重复操作 | 已标记过 / 已进入后续流程 / 终态 |

---

## 八、遗留确认（需与前端 / 成员C / 公共模块对齐）

| 项 | 现状 | 建议 |
|------|------|------|
| ResumeFeignClient 契约 | ✅ C 侧已落地（`006249d`，2026-08-05），联调通过 | 无 |
| 列表字段名 `id` vs `applicationId` | 系分 5.5.2 用 `id`（=applicationId），成员D系分用 `applicationId` | 本期按 5.5.2 输出 `id`（前端已确认） |
| Top5 匹配度门槛 | 系分 5.5.2 无门槛；成员D系分 line 1116「<70% 不进 Top5」 | 与排期冲突，本期不实现，需产品确认 |
| `advantages`/`risks` 数据来源 | `resume_application` 无此列，C 侧未生成 | **未落地**，联调返回 null（Top5 不阻塞） |
| 候选人通知方案 | ✅ **已定：Feign 直调 lingxi-chat** `POST /internal/notifications`，type=`RESUME_VIEWED`（`CandidateNotifier`） | MQ 方案废弃（lingxi-chat 通知服务直接调用式，topic 无消费者） |
| 标记权限 | 本期要求 HR_ADMIN，后端 4011 兜底 | 前端统一显示按钮，非 HR_ADMIN 点击由后端拦截提示 |

## 九、开发顺序

1. 调整 `ResumeFeignClient` + `ApplicationDTO`（契约）✅
2. 补充 `HrErrorCode.CANDIDATE_ALREADY_PROCESSED(4302)` ✅
3. 新建 DTO/VO：`CandidateVO`/`TopCandidateVO`/`MarkCandidateResultVO`/`MarkCandidateDTO`/`RejectFeedbackDTO` ✅
4. 实现 `RejectFeedbackGenerator` 接口 + `TemplateRejectFeedbackGenerator`（模板降级）✅
5. 实现 `CandidateNotifier` + `NotificationFeignClient`（Feign 直调 lingxi-chat）✅
6. 实现 `HrCandidateService` + `HrCandidateServiceImpl` ✅
7. 实现 `HrCandidateController` ✅
8. 自测 ✅：本地全链路联调通过（列表 total / Top5 排序 / 标记状态机直转 / 4302 拦截 / 通知落库 `sys_notification` / 落选反馈 JSON 落库）

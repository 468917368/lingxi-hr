# HR 端通知不同步修复 - Coding Plan

> **所属模块：** lingxi-hr（成员D）+ lingxi-resume（成员C 对接）
> **类型：** Bug 修复（通知链路缺失）
> **日期：** 2026-08-10
> **技术栈：** Spring Boot 2.7 + MyBatis + Feign + RocketMQ（仅 C 侧生产）
> **参考：** `后端总体系分文档.md` §5.5.12 内部接口、`成员D开发排期.md` Day 9「通知走 Feign 直调 lingxi-chat」决策、`模块四-HR服务-测分文档.md` 3.1.9 消息通知
> **状态：** ✅ **已实施 + 编译通过（2026-08-10）**

---

## 〇、Bug 概述

**一句话：** HR 端两类业务通知无上游生成方——① 求职者新投递（应发 `NEW_APPLICATION`）、② Offer 被候选人接受/拒绝（应发 `OFFER_MANAGE`），导致 HR 端通知列表与未读数 `newApplicationCount` / `offerManageCount` 恒为空。

**影响：** HR 无法及时感知新投递与 Offer 结果，招聘跟进滞后。

---

## 一、复现步骤与现象

1. 候选人一键投递职位（`lingxi-resume` `POST /api/v1/applications`）。
2. 候选人接受/拒绝 Offer（HR 端 `POST /api/v1/hr/offers/{id}/accept|reject` 或 C 端投递页 `PUT /api/v1/applications/{id}/offer/accept|decline`）。
3. 查询 HR 端通知列表 `GET /api/v1/notifications` / 未读数 `GET /api/v1/notifications/unread-count`：
   - 无「投递通知」（`NEW_APPLICATION`）
   - 无「Offer管理」（`OFFER_MANAGE`）接受/拒绝记录

**串行正确结果：** 新投递后本企业 HR 收到 `NEW_APPLICATION`；候选人接受/拒绝 Offer 后本企业 HR 收到 `OFFER_MANAGE`。

---

## 二、根因分析

**底层能力已就绪，缺上游触发。** lingxi-chat 已支持 B 端通知类型（`ChatConstant.NOTIFICATION_TYPE_NEW_APPLICATION` / `OFFER_MANAGE`），`NotificationServiceImpl.getUnreadCount` 已统计 `newApplicationCount` / `offerManageCount`，但**没有任何服务实际生成这两类通知**。

### ① 新投递无通知
- 投递在 `lingxi-resume/ApplicationServiceImpl.submit` 创建，仅发 MQ `application-event:NEW_APPLICATION`（`ApplicationEventProducer.java:34`）。
- 该事件唯一消费者是 `lingxi-user/MatchAnalysisListener`（AI 匹配分析），**无 HR 通知**。
- lingxi-hr 的 `listener` 包为空，无消费方；也无供 C 回调的内部接口。
- 企业 HR 成员表 `hr_company_member` 归属 lingxi-hr，C 侧无法自行确定通知对象 → 必须经 D。

### ② Offer 接受/拒绝无通知
- `HrOfferServiceImpl.doAcceptOffer`（`:432-455`）/ `doRejectOffer`（`:462-483`）状态流转成功后**均未调用任何 HR 通知**。两条入口（HR 端接口 + C 端反向同步 `/internal/offers/accept|reject`）都缺。
- 现有 HR 侧 Offer 通知仅 `notifyHrExpired`（过期提醒，`:833`）一处。

---

## 三、修复方案

**对齐 2026-08-08「D 通知走 Feign 直调 lingxi-chat」决策，不引入 MQ 消费。**

### Part 1：新投递 → HR 通知（C→D 内部接口）
投递创建在 C，通知对象在 D → C 投递成功后回调 D 新增内部接口，D 查本企业 `HR_ADMIN` 后 Feign 直调 lingxi-chat 发 `NEW_APPLICATION`。

```
lingxi-resume submit/reapply
  └─(事务提交后 best-effort) Feign ──> lingxi-hr POST /internal/applications/{id}/notify-hr
        ├─ ResumeFeignClient.getApplication 取 companyId/jobTitle/candidateId
        ├─ hr_company_member 查本企业 HR_ADMIN
        ├─ UserFeignClient.batchUsers 取候选人姓名
        └─ ApplicationNotifier ──Feign──> lingxi-chat /internal/notifications（NEW_APPLICATION）
```

### Part 2：Offer 接受/拒绝 → HR 通知（纯 D 侧）
`doAcceptOffer` / `doRejectOffer` 状态流转成功后，复用 `OfferNotifier.notifyHrManage`（`OFFER_MANAGE`，targetType=offer）通知本企业 `HR_ADMIN`。

**接收人策略：** 通知本企业**全部 `HR_ADMIN`**（2026-08-10 用户确认；既有 Offer 过期提醒 `notifyHrExpired`、待定复面通知 `notifyHrForFollowUp` 同步统一为全员）。

---

## 四、涉及文件清单

### lingxi-hr（成员D）

| 文件 | 类型 | 说明 |
|------|:---:|------|
| `service/notify/ApplicationNotifier.java` | 新增 | 发 `NEW_APPLICATION` 通知（best-effort，同 CandidateNotifier 模式） |
| `service/HrNotificationService.java` | 新增 | 通知编排接口 |
| `service/impl/HrNotificationServiceImpl.java` | 新增 | 拉投递→查 HR_ADMIN→拼候选人名→通知 |
| `controller/InternalApplicationNotifyController.java` | 新增 | `POST /internal/applications/{id}/notify-hr` |
| `service/impl/HrOfferServiceImpl.java` | 修改 | `doAcceptOffer/doRejectOffer` 加 `notifyHrOfferResult` |

### lingxi-resume（成员C 对接）

| 文件 | 类型 | 说明 |
|------|:---:|------|
| `feign/HrNotifyFeignClient.java` | 新增 | Feign 直调 lingxi-hr（name=`lingxi-hr`, path=`/internal`） |
| `feign/HrNotifyFeignFallback.java` | 新增 | 降级返回 success（通知 best-effort） |
| `service/impl/ApplicationServiceImpl.java` | 修改 | 新投递/重新投递两条路径事务提交后回调 |

---

## 五、具体改动

### 1. lingxi-hr `service/notify/ApplicationNotifier.java`

```java
private static final String TYPE_NEW_APPLICATION = "NEW_APPLICATION";

/** HR：新投递通知（type=NEW_APPLICATION，targetType=application） */
public boolean notifyHrNewApplication(Long hrUserId, Long applicationId, String jobTitle, String candidateName) {
    String candidate = candidateName != null && !candidateName.isEmpty() ? candidateName : "候选人";
    String title = "新投递";
    String content = "候选人「" + candidate + "」投递了「" + jobTitle + "」，请及时处理";
    return send(hrUserId, title, content, applicationId);
}
// send()：CreateNotificationRequest{type=NEW_APPLICATION, targetType=application, targetId=applicationId}
// Feign 直调 lingxi-chat /internal/notifications，失败记 warn 返回 false
```

### 2. lingxi-hr `service/impl/HrNotificationServiceImpl.java`（核心编排）

```java
public void notifyHrNewApplication(Long applicationId) {
    try {
        ApplicationDTO application = fetchApplication(applicationId);   // resume Feign
        if (application == null || application.getCompanyId() == null) { log.warn(...); return; }
        List<HrCompanyMember> hrAdmins =
                hrCompanyMemberMapper.selectByCompanyId(application.getCompanyId(), "HR_ADMIN", null);
        if (hrAdmins == null || hrAdmins.isEmpty()) { log.warn(...); return; }
        String candidateName = "候选人" + application.getCandidateId();
        SysUserDTO candidate = fetchUsers(Collections.singletonList(application.getCandidateId()))
                .get(application.getCandidateId());
        if (candidate != null && candidate.getName() != null) candidateName = candidate.getName();
        for (HrCompanyMember admin : hrAdmins) {   // 本企业全部 HR_ADMIN
            applicationNotifier.notifyHrNewApplication(
                    admin.getUserId(), applicationId, application.getJobTitle(), candidateName);
        }
    } catch (Exception e) {
        log.warn("新投递通知 HR 异常（不阻塞）: applicationId={}, error={}", applicationId, e.getMessage());
    }
}
```

### 3. lingxi-hr `controller/InternalApplicationNotifyController.java`

```java
@RestController
@RequestMapping("/internal/applications")
public class InternalApplicationNotifyController {
    @PostMapping("/{id}/notify-hr")
    public Result<Void> notifyHrNewApplication(@PathVariable("id") Long applicationId) {
        hrNotificationService.notifyHrNewApplication(applicationId);
        return Result.success();
    }
}
```

### 4. lingxi-hr `service/impl/HrOfferServiceImpl.java`

`doAcceptOffer` 状态变更后：`notifyHrOfferResult(offer, "接受");`
`doRejectOffer` 状态变更后：`notifyHrOfferResult(offer, "拒绝");`

```java
/** 候选人接受/拒绝 Offer 后通知本企业 HR_ADMIN（best-effort，不阻塞） */
private void notifyHrOfferResult(HrOffer offer, String action) {
    try {
        List<HrCompanyMember> hrAdmins =
                hrCompanyMemberMapper.selectByCompanyId(offer.getCompanyId(), ROLE_HR_ADMIN, null);
        if (hrAdmins == null || hrAdmins.isEmpty()) { log.warn(...); return; }
        String candidateName = "候选人" + offer.getCandidateId();
        SysUserDTO candidate = fetchUsers(Collections.singletonList(offer.getCandidateId()))
                .get(offer.getCandidateId());
        if (candidate != null && candidate.getName() != null) candidateName = candidate.getName();
        String jobTitle = queryJobTitleBestEffort(offer.getJobId());
        String title = "候选人已" + action + " Offer";
        String content = "候选人「" + candidateName + "」已" + action + "「" + jobTitle + "」的 Offer，请及时跟进";
        for (HrCompanyMember admin : hrAdmins) {   // 本企业全部 HR_ADMIN
            offerNotifier.notifyHrManage(admin.getUserId(), offer.getId(), jobTitle, title, content);
        }
    } catch (Exception e) {
        log.warn("Offer{} HR提醒失败: offerId={}, error={}", action, offer.getId(), e.getMessage());
    }
}
```

### 5. lingxi-resume `feign/HrNotifyFeignClient.java` + `HrNotifyFeignFallback.java`

```java
@FeignClient(name = "lingxi-hr", path = "/internal", fallback = HrNotifyFeignFallback.class)
public interface HrNotifyFeignClient {
    @PostMapping("/applications/{id}/notify-hr")
    Result<Void> notifyHrNewApplication(@PathVariable("id") Long applicationId);
}
```

### 6. lingxi-resume `service/impl/ApplicationServiceImpl.java`

新增依赖 `HrNotifyFeignClient`；`submit` 两条路径事务提交后回调（best-effort）：

```java
// 新投递路径
registerAfterCommit(() -> eventProducer.sendNewApplication(applicationId, candidateId, dto.getJobId()));
registerAfterCommit(() -> notifyHrNewApplication(applicationId));
// 重新投递路径
registerAfterCommit(() -> eventProducer.sendNewApplication(existing.getId(), candidateId, dto.getJobId()));
registerAfterCommit(() -> notifyHrNewApplication(existing.getId()));

private void notifyHrNewApplication(Long applicationId) {
    try {
        Result<Void> result = hrNotifyFeignClient.notifyHrNewApplication(applicationId);
        if (result == null || !result.isSuccess()) { log.warn(...); }
    } catch (Exception e) {
        log.warn("通知HR新投递异常（不阻塞）: applicationId={}, error={}", applicationId, e.getMessage());
    }
}
```

---

## 六、验证方案

### 1. 新投递 → HR 通知
- 候选人一键投递新岗位（或重新投递）→ 查 `sys_notification`：本企业**全部 `HR_ADMIN`** 各收到一条 `type=NEW_APPLICATION`，`target_id=applicationId`。
- `GET /api/v1/notifications/unread-count`：`newApplicationCount` +1；`GET /api/v1/notifications?type=NEW_APPLICATION` 列表可见。

### 2. Offer 接受/拒绝 → HR 通知
- HR 端发起 Offer → 候选人接受 → 查 `sys_notification`：`type=OFFER_MANAGE`，「候选人已接受 Offer」。
- 候选人拒绝 → `OFFER_MANAGE`，「候选人已拒绝 Offer」。
- 覆盖两条入口：D 端 `POST /api/v1/hr/offers/{id}/accept|reject` 与 C 端反向同步 `POST /internal/offers/accept|reject`。

### 3. best-effort / 降级
- 停掉 lingxi-chat：投递成功、Offer 接受/拒绝主流程不受影响，仅记 warn。
- 停掉 lingxi-hr（C 调 D 通知失败）：投递闭环不受影响（fallback + try/catch 双保险）。

### 4. 回归
- 候选人通知（简历筛选/面试/Offer 发起）不受影响；`actionResult.notificationSent=false` 字段语义不变（HR 通知 best-effort，不参与该字段）。

---

## 七、风险与边界

| 项 | 说明 | 处置 |
|----|------|------|
| C→D 内部接口路径 | `/internal/applications/**` 网关路由指向 resume，但 Feign 按服务名直连绕过网关（与 `OfferFeignClient` 一致） | 无影响，已确认 |
| 通知接收人 | 本企业**全部 `HR_ADMIN`**（2026-08-10 用户确认）；无「岗位负责人」映射 | 全员广播，多 HR 企业通知量=N×HR_ADMIN 数 |
| 事务时序 | 通知在 `registerAfterCommit` 执行，投递已提交、applicationId 可被 D 查询 | 正确 |
| C 侧 sentinel 熔断 | `HrNotifyFeignFallback` 降级返回 success，通知不阻塞投递 | 已加 fallback |
| 跨模块契约 | lingxi-resume 新增 Feign 依赖 lingxi-hr 内部接口 | 与成员C 契约已确认（2026-08-10） |

---

## 八、参考

- `后端总体系分文档.md`：§5.5.12 内部接口、7453-7460 行「D 通知走 Feign 直调 lingxi-chat / D 不再使用 RocketMQ」
- `成员D开发排期.md`：Day 9「通知走 Feign 直调 lingxi-chat；HR 端通知列表/未读/已读复用 lingxi-chat」
- `模块四-HR服务-测分文档.md`：3.1.9 消息通知（NTF-01~07）
- `lingxi-chat/ChatConstant.java`：B 端 `NEW_APPLICATION` / `OFFER_MANAGE` 类型
- `lingxi-hr/service/notify/` 既有 Notifier 模式（CandidateNotifier / InterviewNotifier / OfferNotifier）

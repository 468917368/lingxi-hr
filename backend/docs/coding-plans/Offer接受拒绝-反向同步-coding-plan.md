# Offer 接受/拒绝 - C 端反向同步 Coding Plan

> **所属模块：** lingxi-hr（成员D）↔ lingxi-resume（成员C）| **排期：** Day 8 补丁（2026-08-08）
> **技术栈：** Spring Boot 2.7 + MyBatis + Feign
> **参考：** 成员D开发排期 Day 8、系分文档 5.5.4、`Offer管理-coding-plan.md`、C 侧 `ApplicationServiceImpl.java`（acceptOffer/declineOffer）
> **范围：** 修复「C 端投递页接受/拒绝 Offer 只改投递状态、不改 hr_offer 表」的数据一致性问题
> **跨服务依赖：** lingxi-resume（成员C）`PUT /api/v1/applications/{id}/offer/accept|decline`；lingxi-job（成员B）HC confirm/release
> **状态：** ✅ **D 侧已实现 + JDK 8 编译通过**（2026-08-08）；C 侧契约待成员C落地

---

## 〇、问题背景与核心决策

### 问题

C 侧 `ApplicationServiceImpl.acceptOffer/declineOffer`（入口 `PUT /api/v1/applications/{id}/offer/accept|decline`）只走本地状态机更新 `resume_application`（OFFERED→OFFER_ACCEPTED/OFFER_DECLINED），**不更新 `hr_offer` 表**（该表在 D 库）。

若 C 端前端直接调 C 接口，后果：

| 后果 | 影响 |
|------|------|
| `hr_offer` 永远停在 `SENT` | Offer 列表显示「待确认」，`hasOfferRecord`/`lastOfferStatus` 判断错误 |
| 过期扫描（每5分钟）把已处理的 Offer 判为 EXPIRED | 触发重复 release HC + 发过期通知 |
| 接受后 HC 不会 confirm | B 侧一直 reserved，占用名额 |
| `accepted_at`/`rejected_at` 不落库 | 数据对不上 |

### 决策：C 调 D 反向更新（2026-08-08 用户确认）

- D 侧新增内部接口 `POST /internal/offers/accept|reject`，供 C 反向调用，**只同步 hr_offer + confirm/release HC**，不更新投递状态（投递状态由 C 自己改）。
- **为何 D 不调 C 更新投递状态**：C 的 accept/decline 是 `@Transactional`，先 `transition()` 改投递（未提交）再调 D。若 D 内部再反向调 C 的 `/internal/applications/{id}/status`，会拿行锁等 C 事务提交，而 C 事务又等 D 返回 → **行锁死锁**。故内部路径 `syncApplication=false`。
- **调用顺序（C 侧）**：先 C 本地 `transition()`，后调 D。D 失败 → C 抛异常回滚本地 transition（候选人重试时 D 幂等自愈）。
- **免鉴权**：与现有 `D→C /internal/applications/{id}/status` 一致，gateway 白名单免 JWT，不加服务 token（不加 `X-Caller-Service` 拦截器）。

### 幂等与失败恢复

| 场景 | 行为 |
|------|------|
| C 调 D 后 D 成功、C 提交失败 | 投递回 OFFERED、hr_offer 已 ACCEPTED；候选人重试 → C 再 transition + 调 D → D 幂等（已 ACCEPTED 直接成功）自愈 |
| D 调 B confirm 成功、本地 updateAccept 失败 | D 抛 4201 → C 回滚；重试时 D 幂等（confirm 以 offerId 幂等） |
| D 调 B confirm 失败（名额不足） | D 抛 4203「名额不足，请联系HR」→ C 回滚，候选人看到明确提示 |
| Offer 已过期 | D `validateAcceptable` 抛 4202 → C 回滚，投递保持 OFFERED，前端提示过期 |

---

## 一、涉及文件清单（D 侧，已实现）

### 需要修改
| 文件 | 修改 |
|------|------|
| `mapper/HrOfferMapper.java` + `resources/mapper/HrOfferMapper.xml` | 新增 `selectByApplicationIdForUpdate`（按 application_id 取最新一条 + `FOR UPDATE` 行锁，供 C 反向 accept/reject 并发控制） |
| `service/HrOfferService.java` | 接口新增 `acceptOfferByApplication(Long applicationId)` / `rejectOfferByApplication(Long applicationId, String rejectReason)` |
| `service/impl/HrOfferServiceImpl.java` | 抽核心 `doAcceptOffer(offer, syncApplication)` / `doRejectOffer(offer, rejectReason, syncApplication)`；原 `acceptOffer/rejectOffer` 改调核心（syncApplication=true）；新增两个 `*ByApplication` 方法（syncApplication=false） |

### 需要新建
| 文件 | 说明 |
|------|------|
| `domain/dto/InternalOfferAcceptDTO.java` | `{applicationId @NotNull}`（C 仅持有投递ID） |
| `domain/dto/InternalOfferRejectDTO.java` | `{applicationId @NotNull, rejectReason 选填}` |
| `controller/InternalOfferController.java` | `POST /internal/offers/accept` / `POST /internal/offers/reject`，返回 `Result<Void>` |

### 无需改动
| 文件 | 说明 |
|------|------|
| `exception/HrErrorCode.java` | 复用 4200/4201/4202/4203 |
| gateway | 路由 `lingxi-internal-hr`（`/internal/offers/**`）已存在；AuthFilter `/internal/**` 白名单已存在 |
| `domain/vo/OfferActionResultVO.java` | 内部方法复用返回，无字段变化 |

---

## 二、内部接口定义（D 侧，C 契约）

### 1. 接受 Offer（C 端反向）
`POST /internal/offers/accept`

请求体：
```json
{ "applicationId": 5001 }
```

响应：`Result<Void>`，`code=200` 成功

### 2. 拒绝 Offer（C 端反向）
`POST /internal/offers/reject`

请求体：
```json
{ "applicationId": 5001, "rejectReason": "薪资未谈拢" }   // rejectReason 选填
```

响应：`Result<Void>`，`code=200` 成功

### 业务逻辑（`HrOfferServiceImpl.doAcceptOffer/doRejectOffer`，syncApplication=false）
1. `selectByApplicationIdForUpdate(applicationId)` 定位最新一条 Offer（行锁）→ 不存在抛 **4200**
2. 幂等：`status==ACCEPTED/REJECTED` → 直接返回成功
3. 状态校验：`status==SENT` 且 `expires_at > NOW()`（否则 **4201/4202**）
4. 接受：Feign `confirmHc`（失败 → **4203**「名额不足，请联系HR」，Offer 保持 SENT）→ `updateAccept`（SENT→ACCEPTED + accepted_at）
5. 拒绝：`updateReject`（SENT→REJECTED + rejected_at + reject_reason）→ Feign `releaseHc(reason=REJECTED)`（best-effort，失败记日志不阻塞，对账兜底）
6. **不更新投递状态**（C 已自己改，避免死锁）

### 错误码（C 侧透传即可）
| code | HTTP | 说明 |
|------|:---:|------|
| 4200 | 404 | Offer 不存在（投递无对应 Offer 记录） |
| 4201 | 409 | Offer 状态不允许此操作（非 SENT，如并发已被处理） |
| 4202 | 410 | Offer 已过期 |
| 4203 | 409 | HC 不足 / confirm 失败（消息「名额不足，请联系HR」） |

---

## 三、C 侧改造（成员 C 落地，非 D 改动）

### 1. 新建 FeignClient（lingxi-resume）
```java
@FeignClient(name = "lingxi-hr", path = "/internal/offers")
public interface HrOfferFeignClient {
    @PostMapping("/accept")
    Result<Void> acceptOffer(@RequestBody InternalOfferAcceptDTO dto);

    @PostMapping("/reject")
    Result<Void> rejectOffer(@RequestBody InternalOfferRejectDTO dto);
}
```
> gateway 路由 `/internal/offers/**` → lingxi-hr，免鉴权；无需 `X-Caller-Service` 头（与 lingxi-resume 自身内部接口一致）。

### 2. 改造 `ApplicationServiceImpl.acceptOffer`
```java
@Override
@Transactional
public void acceptOffer(Long id) {
    Long candidateId = getLoginUserId();
    ResumeApplication application = getOwnedApplication(id, candidateId);
    ApplicationStatus current = ApplicationStatus.fromCode(application.getStatus());
    if (current != ApplicationStatus.OFFERED) {
        throw new BusinessException(ResumeErrorCode.STATUS_NOT_ALLOWED);
    }
    transition(id, current.getCode(), ApplicationStatus.OFFER_ACCEPTED.getCode(),
            candidateId, "CANDIDATE", "候选人接受Offer");

    // ===== 新增：反向同步 D 侧 hr_offer + confirm HC =====
    // 失败抛异常 → 回滚上面的 transition（D 幂等，重试自愈）
    hrOfferFeignClient.acceptOffer(new InternalOfferAcceptDTO(id));

    log.info("接受Offer成功: applicationId={}, candidateId={}", id, candidateId);
}
```

### 3. 改造 `ApplicationServiceImpl.declineOffer`
```java
    transition(id, current.getCode(), ApplicationStatus.OFFER_DECLINED.getCode(),
            candidateId, "CANDIDATE", "候选人拒绝Offer");

    // ===== 新增：反向同步 D 侧 hr_offer + release HC =====
    hrOfferFeignClient.rejectOffer(new InternalOfferRejectDTO(id, rejectReason));
```

### C 侧关键约束
- **顺序固定**：先 `transition()` 改投递，后调 D。D 失败抛异常 → 整个事务回滚，不产生半程状态。
- **不要反向调 D 后 D 再调 C**：D 内部路径不更新投递状态，C 不要依赖 D 回写。
- **Feign 调用在事务内**：与 C 现有 `transition` 同事务，D 失败即回滚。注意超时配置（建议读超时 ≥5s，D 内部含一次 B 的 Feign 调用）。
- **rejectReason**：C 当前 decline 无原因参数，若前端需要传，需在 C 的 DTO/接口补字段后透传；本期 D 侧已支持可选。

---

## 四、边界场景

| 场景 | 处理 |
|------|------|
| C 调 D 时投递无对应 Offer 记录 | 4200，C 回滚（正常不会发生：投递 OFFERED 必有 SENT Offer） |
| C 调 D 时 Offer 已被并发处理（非 SENT） | 4201，C 回滚；候选人重试 |
| C 调 D 时 Offer 已过期 | 4202，C 回滚，前端提示「Offer 已过期」 |
| confirm HC 失败（名额不足/已释放） | 4203「名额不足，请联系HR」，C 回滚，Offer 保持 SENT |
| D 成功但 C 提交失败 | 投递 OFFERED、hr_offer ACCEPTED；重试 D 幂等自愈 |
| 过期扫描 vs C 反向 accept 并发 | 均行锁/条件更新互斥，一个成功一个 4201/4202，无重复释放 |
| release HC 失败 | best-effort 记日志，每小时 HC 补偿对账幂等兜底 |
| 内部接口被外部误调 | gateway `/internal/**` 白名单与现有内部接口一致，风险同 `D→C /internal/applications/{id}/status`（本期不加强鉴权，用户已确认） |

---

## 五、开发顺序

✅ **D 侧已完成（2026-08-08）**：
1. `HrOfferMapper` 新增 `selectByApplicationIdForUpdate`（接口 + XML）
2. 新建 `InternalOfferAcceptDTO` / `InternalOfferRejectDTO`
3. `HrOfferService` 接口新增 `acceptOfferByApplication` / `rejectOfferByApplication`
4. `HrOfferServiceImpl` 抽 `doAcceptOffer/doRejectOffer(offer, syncApplication)` + 实现两个内部方法
5. 新建 `InternalOfferController`
6. JDK 8 编译通过（`lingxi-common` + `lingxi-hr` 全量）

⏳ **C 侧待落地**（成员C）：
7. lingxi-resume 新建 `HrOfferFeignClient` + 两个内部 DTO
8. 改造 `ApplicationServiceImpl.acceptOffer/declineOffer` 追加 Feign 调用
9. 联调验证

---

## 六、验证（联调清单）

前置：C 侧接入后，本地 IDEA 起 hr + resume + job + gateway，测试投递 OFFERED 且 hr_offer=SENT。

1. **C 端接受**：候选人调 `PUT /api/v1/applications/{id}/offer/accept` → 投递 OFFER_ACCEPTED + `hr_offer` status=ACCEPTED + `accepted_at` 落库 + B confirm 成功
2. **C 端拒绝**：`PUT /api/v1/applications/{id}/offer/decline` → 投递 OFFER_DECLINED + `hr_offer` status=REJECTED + `rejected_at`/`reject_reason` 落库 + B release
3. **幂等重试**：对已 ACCEPTED 投递重调 → D 侧幂等成功
4. **D 侧独立链路不回退**：D 端候选人接口 `POST /api/v1/hr/offers/{id}/accept` 仍正常（联动投递 OFFERED→OFFER_ACCEPTED）
5. **过期保护**：把 Offer expires_at 改过去 → C 端接受 → 4202，投递保持 OFFERED，hr_offer 保持 SENT
6. **并发**：D 端 accept 与 C 端 accept 同时打同一 offer → 一个成功一个 4201，无重复 confirm
7. **Offer 列表/候选人列表**：接受后 `lastOfferStatus=ACCEPTED`、Offer 列表不再显示「待确认」

---

## 七、依赖 / 待确认

| # | 项 | 类型 | 现状 | 决策 |
|---|----|------|------|------|
| 1 | C 侧 `HrOfferFeignClient` 落地 | 依赖 | C 未接入 | 由成员 C 按 §三 契约实现，D 侧已就绪 |
| 2 | C 侧 decline 是否传 rejectReason | 确认 | C 当前 decline 无原因入参 | D 已支持可选 rejectReason；C 按前端需要决定是否补字段 |
| 3 | 内部接口鉴权 | 确认 | 不加服务 token（与现有 resume↔hr 内部调用一致） | 用户已确认（2026-08-08） |
| 4 | D 端独立 accept/reject 接口是否保留 | 确认 | 保留（方案A：通知跳转 + D 详情页） | 两条链路并存，D 侧逻辑已抽核心复用，无重复代码 |

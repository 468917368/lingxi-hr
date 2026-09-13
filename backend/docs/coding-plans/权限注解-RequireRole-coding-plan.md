# lingxi-hr 权限注解（@RequireRole("HR")）加注 - Coding Plan

> **所属模块：** lingxi-hr（成员D）| **类型：** 权限加固（纯增量注解，不改业务逻辑）
> **技术栈：** Spring Boot 2.7 + `lingxi-common`（`RequireRole` 注解 + `AuthInterceptor`）
> **背景：** 团队约定各模块自行把权限注解加上（`@RequireRole` 可加在 controller 类或方法上）。lingxi-hr 现有多个 controller 仅 `@RequireLogin`，任意登录角色（含 CANDIDATE）都可访问 HR 侧接口；部分接口被跨模块 Feign 调用（`/internal/**`）或面向候选人，**不能**加 HR 权限。
> **范围：** 3 个 controller 加注 `@RequireRole("HR")`（HrCompany 类级 1 处 + HrCompanyMember 方法级 3 处 + HrOffer 方法级 5 处）。
> **状态：** 🚧 待开发

---

## 〇、背景与决策

### 为什么是现在加
- `AuthInterceptor`（lingxi-common）已生效：`HrApplication` scan `com.lingxi.common`，`WebMvcConfig.addInterceptors` 拦截 `/**`，排除白名单（`/internal/**`、`/api/v1/auth/**` 等）。
- 角色来源：Gateway 注入 `X-User-Role` 或解析 `Authorization` token 的 `role` 字段。用户级角色：`HR` / `INTERVIEWER` / `CANDIDATE`（`UserRole` 枚举）。
- 服务层已有**二次防线**：Offer/成员管理写操作内部 `requireHrAdmin`（校验 `hr_company_member.role=HR_ADMIN`）。本次注解是第一道闸门，在 Controller 层直接挡掉非 HR 角色，减少无效请求打到服务层。

### 决策
1. **只加 `@RequireRole("HR")`**，不加 `{"HR","INTERVIEWER"}` 到本文改动范围——HR 端写/管理接口严格限 HR；HR+INTERVIEWER 共用的接口（account/candidates/interviews）已注解或保持现状，不在本次范围。
2. **`/internal/**` 一律不加**：lingxi-resume 的 `OfferFeignClient`/`HrNotifyFeignClient`、lingxi-admin 的 `HrFeignClient` 均以 `name="lingxi-hr", path="/internal"` 服务间直调，gateway 白名单免鉴权；加注解会直接打挂跨模块调用（违反"少加不能多加"）。
3. **候选人接口不加**：Offer 详情/接受/拒绝（服务层 `requireCandidateOwner`）、Mock 面试、HR 公开信息，均为 CANDIDATE 角色使用。
4. **方法级优先于类级**（`AuthInterceptor` 先查方法再查类）：Offer 用方法级，避免误伤候选人接口；Company 全 HR 端，用类级。

---

## 一、涉及文件清单

### 修改（3 个文件，均只加注解 + import）

| 文件 | 加注位置 | 注解 | 涉及接口 |
|------|---------|------|---------|
| `controller/HrCompanyController.java` | **类级**（`@RequireLogin` 下） | `@RequireRole("HR")` | 4 个全限：创建企业 / 查·改企业信息 / 提交认证 / 查认证状态 |
| `controller/HrCompanyMemberController.java` | 方法级 3 个 | `@RequireRole("HR")` | `refreshInviteCode` / `createMember` / `removeMember` |
| `controller/HrOfferController.java` | 方法级 5 个 | `@RequireRole("HR")` | `createOffer` / `listOffers` / `getHcOverview` / `urgeOffer` / `retractOffer` |

> 每个文件补一行 `import com.lingxi.common.annotation.RequireRole;`。不改任何业务代码、不改类上已有 `@RequireLogin`。

### 不改
| 文件 | 原因 |
|------|------|
| `controller/HrAuthController.java` | `/api/v1/hr/register` 公开注册，gateway 白名单 |
| `controller/HrPublicController.java` | 已 `@RequireRole({"CANDIDATE"})`，候选人看 HR 公开信息 |
| `controller/HrAccountController.java` | 已 `@RequireRole({"HR","INTERVIEWER"})` |
| `controller/HrCandidateController.java` | 已 `@RequireRole({"HR","INTERVIEWER"})` |
| `controller/HrInterviewController.java` | HR+INTERVIEWER 共用（面试官端「我的面试」），不能只放行 HR |
| `controller/InternalApplicationNotifyController.java` | `/internal/**`，lingxi-resume 回调 |
| `controller/InternalOfferController.java` | `/internal/**`，lingxi-resume 回调 |
| `agent/controller/MockInterviewController.java` | 候选人模拟面试 |

---

## 二、改动明细

### 2.1 HrCompanyController —— 类级 `@RequireRole("HR")`

```java
@Slf4j
@RestController
@RequestMapping("/api/v1/hr/company")
@RequiredArgsConstructor
@RequireLogin
@RequireRole("HR")            // ← 新增
public class HrCompanyController { ... }
```

| 接口 | 现状 | 加后 |
|------|------|------|
| `POST /info` 创建企业 | 任意登录 | HR |
| `GET /info` 查企业信息 | 任意登录 | HR |
| `PUT /info` 更新企业信息 | 任意登录 | HR |
| `POST /certification` 提交认证 | 任意登录 | HR |
| `GET /certification/status` 查认证状态 | 任意登录 | HR |

> 理由：企业入驻/认证是 HR 专属流程（注册即 role=HR，服务层 `HrCompanyServiceImpl` 建企业后写 `hr_company_member.role=HR_ADMIN`），无跨模块调用、无候选人调用。

### 2.2 HrCompanyMemberController —— 方法级 3 处

```java
@RequireLogin
public class HrCompanyMemberController {

    @PostMapping("/join-by-invite")            // 不加：仅需登录态（引导加入）
    public Result<HrJoinCompanyVO> joinByInvite(...) { ... }

    @RequireRole("HR")                        // ← 新增（服务层 requireHrAdmin）
    @PutMapping("/invite-code")
    public Result<String> refreshInviteCode() { ... }

    @GetMapping                             // 不加：待确认（见八）
    public Result<List<HrMemberVO>> listMembers(...) { ... }

    @RequireRole("HR")                        // ← 新增（服务层 requireHrAdmin）
    @PostMapping
    public Result<Void> createMember(...) { ... }

    @RequireRole("HR")                        // ← 新增（服务层 requireHrAdmin）
    @DeleteMapping("/{memberId}")
    public Result<Void> removeMember(...) { ... }
}
```

| 接口 | 加注 | 依据 |
|------|------|------|
| `POST /join-by-invite` | 不加 | 服务层仅校验登录态（`HrCompanyMemberServiceImpl.joinByInvite` 无 `requireHrAdmin`）；邀请码加入是引导动作 |
| `PUT /invite-code` | ✅ | 服务层 `requireHrAdmin` |
| `GET` 成员列表 | 待确认 | 服务层未校验 HR_ADMIN；仅公司管理页展示则加，否则保持 |
| `POST` 创建面试官 | ✅ | 服务层 `requireHrAdmin` |
| `DELETE /{memberId}` | ✅ | 服务层 `requireHrAdmin` |

### 2.3 HrOfferController —— 方法级 5 处

```java
@RequireLogin
public class HrOfferController {

    @RequireRole("HR")                        // ← 新增
    @PostMapping
    public Result<OfferCreateResultVO> createOffer(...) { ... }

    @RequireRole("HR")                        // ← 新增
    @GetMapping
    public Result<PageResult<OfferVO>> listOffers(...) { ... }

    @RequireRole("HR")                        // ← 新增
    @GetMapping("/hc-overview")
    public Result<HcOverviewVO> getHcOverview(...) { ... }

    @RequireRole("HR")                        // ← 新增
    @PostMapping("/{offerId}/urge")
    public Result<Void> urgeOffer(...) { ... }

    @RequireRole("HR")                        // ← 新增
    @PostMapping("/{offerId}/retract")
    public Result<Void> retractOffer(...) { ... }

    @GetMapping("/{offerId}")                 // 不加：候选人接口（requireCandidateOwner）
    @PostMapping("/{offerId}/accept")         // 不加：候选人接口
    @PostMapping("/{offerId}/reject")         // 不加：候选人接口
}
```

| 接口 | 加注 | 依据 |
|------|------|------|
| `POST /` 发起 Offer | ✅ | `HrOfferServiceImpl.createOffer` → `requireHrAdmin` |
| `GET /` 列表 | ✅ | `listOffers` → `requireHrAdmin` |
| `GET /hc-overview` | ✅ | `getHcOverview` → `requireHrAdmin` |
| `POST /{offerId}/urge` | ✅ | `urgeOffer` → `requireHrAdmin` |
| `POST /{offerId}/retract` | ✅ | `retractOffer` → `requireHrAdmin` |
| `GET /{offerId}` 详情 | 不加 | `getOfferDetail` → `requireCandidateOwner`（候选人本人） |
| `POST /{offerId}/accept` | 不加 | `acceptOffer` → `requireCandidateOwner` |
| `POST /{offerId}/reject` | 不加 | `rejectOffer` → `requireCandidateOwner` |

> 必须方法级：类级会误伤 3 个候选人接口。

---

## 三、不加清单与理由（防误伤）

| 路径 | 调用方 | 为什么不加 |
|------|--------|-----------|
| `/internal/offers/**` | lingxi-resume `OfferFeignClient` | 服务间直调，gateway 白名单；token 可能不带用户/角色 |
| `/internal/applications/**` | lingxi-resume `HrNotifyFeignClient` | 同上 |
| `/internal/**`（lingxi-admin `HrFeignClient`） | lingxi-admin | 同上 |
| `/api/v1/hr/register` | 前端（公开） | gateway 白名单，注册前置无登录态 |
| `/api/v1/hr/{hrId}/public` | 前端（CANDIDATE） | 已限 CANDIDATE，HR 公开信息给求职者看 |
| `/api/v1/hr/offers/{offerId}` 详情/接受/拒绝 | 前端（CANDIDATE 本人） | 候选人操作，加 HR 即阻断 |
| `/api/v1/mock-interview/**` | 前端（CANDIDATE） | 模拟面试面向候选人 |

---

## 四、权限模型说明

```
用户级 role（JWT / X-User-Role）：HR / INTERVIEWER / CANDIDATE
企业成员级 role（hr_company_member）：HR_ADMIN / INTERVIEWER

对应关系：
  注册 HR → 建企业/邀请加入 → 成员 HR_ADMIN，用户 role = HR
  创建面试官 → 成员 INTERVIEWER，用户 role = INTERVIEWER
```

- `@RequireRole("HR")` 拦截器只校验**用户级 role**，不做企业归属校验；企业级隔离仍由服务层 `requireHrAdmin`（按 `UserContext.companyId` 查成员表）兜底，本次注解只是前置粗粒度拦截。
- 已有 `@RequireRole({"HR","INTERVIEWER"})` 的 account/candidates 不动——它们本来就该两种角色共用。

---

## 五、边界场景与风险

| 场景 | 影响 | 处理 |
|------|------|------|
| HR 用户尚未建企业（companyId=null）访问 `/api/v1/hr/company/*` | 用户 role=HR → 放行到服务层，服务层按 userId 反查（`getCertificationStatus` 已支持） | 正常，不阻断入驻流程 |
| INTERVIEWER 访问 `/api/v1/hr/company/*` 或 Offer 管理 | 拦截器 403 | 符合预期（面试官本就不应管理企业/Offer） |
| CANDIDATE 访问 Offer 管理 | 拦截器 403（原 4011 由服务层抛） | 更早拦截，错误码变为 403，前端需兜底提示 |
| 跨模块 Feign 调 `/internal/**` | 白名单放行，不受影响 | 验证时重点回归 |
| 候选人接受/拒绝 Offer（3 个候选人接口） | 无 `@RequireRole`，仅 `@RequireLogin` + 服务层 `requireCandidateOwner` | 不受影响 |
| token 无 role 字段 | `@RequireRole` 校验 `allowedRole.equals(user.getRole())` 恒 false → 403；未登录 → 401 | 与现有 account/candidates 行为一致 |

> 注意：错误码变化——部分原 `4011 MEMBER_NO_PERMISSION`（服务层业务码）会被拦截器先拦截成 `403 FORBIDDEN`。若前端依赖 4011 判断，需评估是否接受（建议接受：语义更贴近"无权限"；详见「八、待确认」）。

---

## 六、开发顺序

1. `HrCompanyController`：类级加 `@RequireRole("HR")` + import
2. `HrCompanyMemberController`：3 个方法加注 + import（`listMembers` 按「八」决策后再定）
3. `HrOfferController`：5 个方法加注 + import
4. JDK 8 编译验证（`zulu-8`，参考 memory：默认 Maven JDK 26 会让 Lombok 失败）
5. 本地联调回归（见七）
6. 收尾：如确认 `listMembers` 加 HR、`markCandidate` 收紧，再补 1 处/1 处方法级

---

## 七、验证（本地联调）

环境：IDEA 起 hr + gateway + nacos + mysql（若验证跨模块需 lingxi-resume）。

| # | 场景 | 预期 |
|---|------|------|
| 1 | HR 登录（role=HR）调 `POST /api/v1/hr/company/certification` | 200 |
| 2 | INTERVIEWER 登录调 `POST /api/v1/hr/company/certification` | 403 |
| 3 | CANDIDATE 登录调 `GET /api/v1/hr/offers/hc-overview` | 403 |
| 4 | CANDIDATE 登录调 `POST /api/v1/hr/offers/{id}/accept`（本人 offer） | 200（候选人接口未误伤） |
| 5 | HR 调 Offer 5 个管理接口 | 200 |
| 6 | lingxi-resume 起服，走 `POST /internal/offers/accept` | 200（白名单未误伤） |
| 7 | HR 调 `GET /api/v1/hr/company/members`（listMembers，若未加 HR） | 200 |
| 8 | 未登录调任何 /api/v1/hr/company/* | 401 |

---

## 八、依赖 / 待确认

| # | 项 | 类型 | 现状 | 决策 |
|---|----|------|------|------|
| 1 | `listMembers` 是否加 `@RequireRole("HR")` | 确认 | 服务层未校验 HR_ADMIN；成员列表在"公司管理页"Tab | 若仅 HR 看 → 加；若面试官也要看 → 保持 `@RequireLogin` |
| 2 | `HrCandidateController.markCandidate` 是否收紧 | 确认 | 注释要求 HR_ADMIN，但类级已放开 INTERVIEWER | 若要收紧 → 方法级 `@RequireRole("HR")`（本次默认不加，保持现状） |
| 3 | 错误码 4011→403 变化 | 确认 | 拦截器先于服务层抛 `FORBIDDEN` | 前端是否依赖 4011；默认接受 403 |
| 4 | `GET /api/v1/hr/company/info` 是否 INTERVIEWER 也需要 | 确认 | 目前公司管理页仅 HR | 若面试官需要读企业信息 → 拆方法级或改 `{"HR","INTERVIEWER"}` |

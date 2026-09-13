# 个人中心 - Coding Plan

> **所属模块：** lingxi-hr（成员D）| **排期：** Day 9
> **技术栈：** Spring Boot 2.7 + MyBatis + Feign
> **参考：** 成员D开发排期 Day 9、系分文档 5.5.6
> **范围：** 账号信息查询/修改 + 密码修改 + 手机号/邮箱修改（**position 不展示**；**操作日志取消**）
> **跨服务依赖：** lingxi-user（成员A）：`GET /internal/users/{id}`（读）、`PUT /api/v1/user/info`（改 name/avatar）、`POST /api/v1/auth/change-password`（密码）、`POST /api/v1/user/phone/send-code`+`/phone`（手机号）、`POST /api/v1/user/email/send-code`+`/email/verify`（邮箱）
> **状态：** ✅ **已实现 + JDK 8 编译通过**（2026-08-08）；本地联调待执行

---

## 〇、需求裁剪与决策（2026-08-08 用户确认）

1. **`position` 不展示**：系分文档 5.5.6 profile 响应的 `position` **取消**（`hr_company_member` 无此列、lingxi-user 也无来源，不新增列）。
2. **操作日志取消**：前端个人中心**不展示操作日志**。D 不实现日志查询、不调 lingxi-admin 写 `admin_operation_log`（该表归成员E，本期 HR 端不接入）。
3. **展示字段**：name / avatar / email / phone（+ department / companyName / role）。
4. **手机号修改**：参考求职者端（lingxi-user 验证码流程）——`send-code` 发短信验证码到新手机号 → `change` 验证后修改。
5. **邮箱修改**：**发验证码到新邮箱确认**（走 A 的 `email/send-code` + `email/verify`，不是免验证码）。
6. **接口形态**：**D 封装成 `/api/v1/hr/account/*`**（前端统一走 `/api/v1/hr/**`，HR 与面试官共用），内部 Feign 薄转发 lingxi-user。

---

## 一、接口定义（7 个）

### 1. 账号信息查询
`GET /api/v1/hr/account/profile`（HR_ADMIN / INTERVIEWER 共用）

**响应** `Result<AccountProfileVO>`：`id / name / phone / email / avatar / department / companyName / role`

| 字段 | 来源 | 方式 |
|------|------|------|
| id / name / phone / email / avatar / role | lingxi-user `GET /internal/users/{id}` | Feign `UserFeignClient.getUserById`（已有） |
| department | `hr_company_member`（按 userId 查 ACTIVE 成员） | `selectActiveByUserId`（已有） |
| companyName | `hr_company`（按 companyId） | `selectById`（已有） |

**降级**（与 Day 3 候选人列表一致，try-catch 不阻塞）：A 失败 → `name="用户"+userId`、`avatar=null`、`phone="***"`、`email=null`；member 查不到 → `department=null`；company 查不到 → `companyName=null`。

### 2. 账号信息修改
`PUT /api/v1/hr/account/profile`

**入参** `UpdateAccountProfileDTO`（均选填，至少传一个）：`name`（1-32 字符）/ `avatar` / `department`

- `name`/`avatar` → Feign A `PUT /api/v1/user/info`（透传 token；A 校验 name 1-32 字符 + **每月仅一次修改限制**）
- `department` → 本地 `updateDepartment(companyId, userId, department)`
- 全空 → 400「请至少填写一项需要修改的信息」

### 3. 密码修改
`PUT /api/v1/hr/account/password`

**入参** `ChangePasswordDTO`：`oldPassword` / `newPassword` / `confirmPassword`

- D 校验 `confirmPassword == newPassword`（不一致 → 400「两次输入的新密码不一致」）
- Feign A `POST /api/v1/auth/change-password`（A 校验旧密码 + 新密码强度 8-20 位含大小写字母数字特殊字符）
- 成功后 A 清 token → 重新登录

### 4. 发送手机号验证码
`POST /api/v1/hr/account/phone/send-code`

**入参** `SendPhoneCodeDTO`：`newPhone`（@Phone）

- Feign A `POST /api/v1/user/phone/send-code`（验证码发到新手机号）

### 5. 验证并修改手机号
`POST /api/v1/hr/account/phone`

**入参** `ChangePhoneDTO`：`newPhone`（@Phone）/ `code`（6 位）

- Feign A `POST /api/v1/user/phone`（A 校验验证码 + 改 sys_user.phone）
- 成功后 A 清 token → 重新登录

### 6. 发送邮箱验证码
`POST /api/v1/hr/account/email/send-code`

**入参** `SendEmailCodeDTO`：`email`（@Email）

- Feign A `POST /api/v1/user/email/send-code`（验证码发到新邮箱）

### 7. 验证并更新邮箱
`POST /api/v1/hr/account/email`

**入参** `VerifyEmailDTO`：`email`（@Email）/ `code`（6 位）

- Feign A `POST /api/v1/user/email/verify`（A 校验验证码 + 改 sys_user.email）

---

## 二、涉及文件清单

### 需要新建
| 文件 | 说明 |
|------|------|
| `controller/HrAccountController.java` | 7 个接口（GET/PUT profile + PUT password + 2×phone + 2×email） |
| `domain/vo/AccountProfileVO.java` | id/name/phone/email/avatar/department/companyName/role |
| `domain/dto/UpdateAccountProfileDTO.java` | name/avatar/department |
| `domain/dto/ChangePasswordDTO.java` | oldPassword/newPassword/confirmPassword |
| `domain/dto/SendPhoneCodeDTO.java` | newPhone |
| `domain/dto/ChangePhoneDTO.java` | newPhone + code |
| `domain/dto/SendEmailCodeDTO.java` | email |
| `domain/dto/VerifyEmailDTO.java` | email + code |
| `service/HrAccountService.java` + `service/impl/HrAccountServiceImpl.java` | 7 方法业务实现 |
| `feign/AccountFeignClient.java` | A 用户接口：updateUserInfo / changePassword / sendPhoneCode / changePhone / sendEmailCode / verifyEmail（6 个） |
| `feign/dto/UpdateUserInfoFeignDTO.java` / `ChangePasswordFeignDTO.java` | 改 name/avatar、改密码 |
| `feign/dto/SendPhoneCodeFeignDTO.java` / `ChangePhoneFeignDTO.java` | 手机号 |
| `feign/dto/SendEmailCodeFeignDTO.java` / `VerifyEmailFeignDTO.java` | 邮箱 |

### 需要修改
| 文件 | 修改 |
|------|------|
| `mapper/HrCompanyMemberMapper.java` + `resources/mapper/HrCompanyMemberMapper.xml` | 新增 `updateDepartment`（UPDATE ... SET department WHERE company_id+user_id+ACTIVE） |

### 复用（不改）
| 文件 | 说明 |
|------|------|
| `feign/UserFeignClient.java` | `getUserById` 读用户 |
| `mapper/HrCompanyMapper.java` | `selectById` 查企业名 |
| `mapper/HrCompanyMemberMapper.java` | `selectActiveByUserId` 查部门 |
| `exception/HrErrorCode.java` | 不新增错误码（复用 common 401/BAD_REQUEST + 4011 MEMBER_NO_PERMISSION） |
| `FeignConfig.tokenRelayInterceptor`（lingxi-common） | Feign 透传 Authorization，`AccountFeignClient` 免配置 |

> **网关改动（成员A 模块，2026-08-08）**：`lingxi-gateway` 的 `lingxi-hr` 路由 Path 从「candidates/interviews/offers/dashboard/company/register/mock-interview 白名单」改为 **`/api/v1/hr/**, /api/v1/mock-interview/**`** 通配（account/notifications 等后续前缀无需再加；`/api/v1/hr/jobs/**`、`/api/v1/hr/questions/**` 由上方 lingxi-job 路由先匹配不受影响）。**需同步成员A/生产**。

---

## 三、Service 层

### HrAccountService 接口
```java
public interface HrAccountService {
    AccountProfileVO getProfile(Long userId, Long companyId);
    void updateProfile(Long userId, Long companyId, UpdateAccountProfileDTO dto);
    void changePassword(Long userId, Long companyId, ChangePasswordDTO dto);
    void sendPhoneCode(Long userId, Long companyId, SendPhoneCodeDTO dto);
    void changePhone(Long userId, Long companyId, ChangePhoneDTO dto);
    void sendEmailCode(Long userId, Long companyId, SendEmailCodeDTO dto);
    void verifyEmail(Long userId, Long companyId, VerifyEmailDTO dto);
}
```

### 关键实现说明
- 7 个方法统一 `requireActiveMember(companyId)`（本企业 ACTIVE 成员，HR_ADMIN/INTERVIEWER 均可，非成员 4011）
- 手机号/邮箱/密码/name/avatar 均**薄转发** A（`AccountFeignClient`），`ensureSuccess(Result, userId, failMsg)` 统一检查：失败透传 A 的错误码/消息抛 `BusinessException`
- `getProfile` 中 A 调用 try-catch 降级（不阻塞），与 Day 3 候选人列表一致
- Feign 透传 HR token：`FeignConfig.tokenRelayInterceptor` 全局生效，`AccountFeignClient` 无需额外配置

---

## 四、边界场景

| 场景 | 处理 |
|------|------|
| A 用户信息查询失败 | name="用户"+id、avatar=null、phone="***"、email=null 降级，不阻塞 |
| 本企业无成员记录 / companyId 无企业 | department=null / companyName=null |
| updateProfile 全空 | 400「请至少填写一项需要修改的信息」 |
| name 超长 / 每月修改超频 | A 侧 400 透传（`NAME_UPDATE_LIMIT`） |
| confirmPassword 不一致 | 400「两次输入的新密码不一致」 |
| 新密码强度不足 / 旧密码错误 | A 侧 400 透传 |
| 手机号格式不对 | D 侧 @Phone 校验 400 |
| 验证码格式不对（非 6 位数字） | D 侧 @Pattern 校验 400 |
| 手机号验证码错误 / 邮箱验证码错误 | A 侧错误透传 |
| 新手机号已注册 | A 侧错误透传 |
| 非本企业成员调个人中心 | 4011（requireActiveMember） |

---

## 五、开发顺序

✅ **已完成（2026-08-08）**，顺序与实现一致：
1. 新增 `AccountFeignClient`（6 方法）+ 6 个 Feign DTO
2. `HrCompanyMemberMapper` + XML 新增 `updateDepartment`
3. 新建 DTO/VO（AccountProfileVO + 7 个 DTO）
4. 实现 `HrAccountService` + `HrAccountServiceImpl`（7 方法，`ensureSuccess` 统一检查）
5. 实现 `HrAccountController`（7 接口）
6. 网关路由 `lingxi-hr` 改为 `/api/v1/hr/**` 通配（**需同步成员A**）
7. JDK 8 编译通过

---

## 六、验证（本地联调）

环境：IDEA 起 hr（重启加载新代码）+ user + gateway（重启加载新路由）。HR：16735263528（hr_1，company 1 HR_ADMIN）。

1. `GET /api/v1/hr/account/profile` → name/phone/email/avatar/department/companyName/role 正常；**响应不含 position**
2. `PUT /api/v1/hr/account/profile` 改 name+avatar → A `sys_user` 更新；改 department → `hr_company_member` 更新
3. `PUT /api/v1/hr/account/password`：旧密码错 / 两次不一致 / 强度不足 → 报错；正确 → 成功，token 失效
4. 修改手机号：`POST /phone/send-code`（newPhone）→ 新手机号收验证码 → `POST /phone`（newPhone+code）→ A `sys_user.phone` 更新，token 失效重新登录
5. 修改邮箱：`POST /email/send-code`（email）→ 新邮箱收验证码 → `POST /email`（email+code）→ A `sys_user.email` 更新
6. 降级：停 lingxi-user → `GET profile` 返回降级字段，接口不 500
7. 权限：非本企业成员 token → 4011；面试官（INTERVIEWER）token → 正常共用
8. 网关：`GET /api/v1/hr/account/profile` 不再 404（路由通配生效）

---

## 七、依赖 / 待确认

| # | 项 | 类型 | 现状 | 决策 |
|---|----|------|------|------|
| 1 | A `PUT /api/v1/user/info` 对 name 每月一次修改限制 | 确认 | `UserInfoVO.nameUpdatedAt` + `NAME_UPDATE_LIMIT` | D 透传错误，前端提示 |
| 2 | 手机号/邮箱修改复用 A 验证码流程 | 确认 | A 已有 send-code + change/verify | **已确认（2026-08-08）**：D 封装薄转发，前端走 `/api/v1/hr/**` |
| 3 | 网关路由 `/api/v1/hr/**` 通配 | 依赖 | 原白名单无 account | **已改（2026-08-08）**：`lingxi-hr` 路由通配，**需同步成员A/生产** |
| 4 | 操作日志 / position | 已裁剪 | - | **前端不展示**，D 不实现（2026-08-08 用户确认） |

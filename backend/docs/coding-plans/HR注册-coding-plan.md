# HR 注册 - Coding Plan

> **所属模块：** lingxi-hr | **排期：** Day 1-2  
> **技术栈：** Spring Boot 2.7 + MyBatis + Redis + Feign  
> **参考：** 成员D开发排期 Day 1-2、系分文档 5.5 节  
> **设计决策：** 账号注册与创建企业**分离**——注册走本接口（内部复用 lingxi-user 注册逻辑），企业创建/认证走 `POST /api/v1/hr/company/certification`，注册后 `companyId=null`，待建企业后绑定

---

## 一、涉及文件清单

### 新增代码

| 文件 | 说明 |
|------|------|
| `controller/HrAuthController.java` | 注册接口 |
| `domain/dto/HrRegisterRequestDTO.java` | 注册入参（校验） |
| `service/HrAuthService.java` | 服务接口 |
| `service/impl/HrAuthServiceImpl.java` | 服务实现（透传 lingxi-user） |
| `feign/AuthFeignClient.java` | 调 lingxi-user `/api/v1/auth/register` |
| `feign/dto/RegisterRequestDTO.java` | 透传给 lingxi-user 的注册请求体 |
| `feign/dto/LoginResponseDTO.java` | 登录/注册响应（token + user） |
| `feign/dto/UserInfoDTO.java` | 用户信息（仅取 HR 注册所需字段） |

### 修改代码

| 文件 | 改动 |
|------|------|
| `lingxi-gateway/src/main/resources/application.yml` | lingxi-hr 路由 predicates 追加 `/api/v1/hr/register**` |

### 无新增的依赖
> 不新依赖表/Mapper。注册不写本服务任何表，账号创建完全由 lingxi-user 完成。

---

## 二、API 接口（1 个）

| 方法 | 路径 | 请求 | 响应 |
|------|------|------|------|
| `POST` | `/api/v1/hr/register` | `HrRegisterRequestDTO` (JSON) | `Result<LoginResponseDTO>` |

### 响应体（注册即登录，返回 Token）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "expiresIn": 7200,
    "user": {
      "id": 1001,
      "phone": "13812345678",
      "name": "张三",
      "role": "HR",
      "avatar": null,
      "email": null,
      "companyId": null
    }
  }
}
```

> `companyId=null`：注册阶段未建企业，前端拿到 token 后可调用认证接口创建企业。

---

## 三、请求校验（HrRegisterRequestDTO）

| 字段 | 校验规则 | 说明 |
|------|------|------|
| `phone` | `@NotBlank` + `@Phone` | 11 位手机号 |
| `code` | `@NotBlank` + `@Pattern(^\d{6}$)` | 6 位短信验证码 |
| `password` | `@NotBlank` + `@Size(8-20)` + 正则（大小写+数字+特殊字符） | 密码强度 |
| `name` | `@NotBlank` + `@Size(1-32)` | 真实姓名 |

---

## 四、Service 层

### HrAuthService 接口

```java
LoginResponseDTO registerHr(HrRegisterRequestDTO request);
```

### registerHr — 业务逻辑

```
1. 接收 HrRegisterRequestDTO（参数校验由 @Validated 拦截）
2. 构建 RegisterRequestDTO 透传字段：phone/code/password/name，role 固定 "HR"
3. Feign 调 authFeignClient.register(registerRequest)
4. 响应校验：
   - result == null 或 !result.isSuccess() → 抛 BusinessException(result.code, result.message)
     （失败场景：验证码错误/过期、手机号已注册、账号禁用等，原样透传 lingxi-user 错误码）
5. 成功 → 返回 LoginResponseDTO（token + user，companyId=null）
6. 记 INFO 日志：userId + phone
```

---

## 五、Controller 层

```java
@RestController
@RequestMapping("/api/v1/hr")
public class HrAuthController {

    @PostMapping("/register")
    public Result<LoginResponseDTO> register(@Validated @RequestBody HrRegisterRequestDTO request);
}
```

---

## 六、依赖：AuthFeignClient（调 lingxi-user）

```java
@FeignClient(name = "lingxi-user", contextId = "authFeignClient", path = "/api/v1/auth")
public interface AuthFeignClient {

    @PostMapping("/register")
    Result<LoginResponseDTO> register(@RequestBody RegisterRequestDTO request);
}
```

### 依赖 lingxi-user 的接口契约

| 项 | 内容 |
|------|------|
| 接口 | `POST /api/v1/auth/register`（lingxi-user `AuthController#register`） |
| 入参 | `phone/code/password/name/role` |
| 出参 | `Result<LoginResponse>`：`{accessToken, refreshToken, expiresIn, user:{id, phone, name, role, avatar, email, companyId}}` |
| 行为 | lingxi-user 内部完成：短信验证码校验 → 创建用户 → BCrypt 存密码 → 签发 Token |
| 错误透传 | lingxi-user 的验证码错误(1001)/过期(1002)/账号禁用(1004)等错误码原样透传给前端 |

### 需要成员A 确认
- `role="HR"` 是否被 lingxi-user 接受（其 RegisterRequest 的 role 枚举是否含 HR）
- `LoginResponse.user.companyId` 字段是否存在于 lingxi-user 的 `LoginResponse` 中（本服务 DTO 用 `@JsonIgnoreProperties(ignoreUnknown=true)` 兜底，缺失不报错）

---

## 七、边界场景

| 场景 | 处理 |
|------|------|
| 验证码错误/过期 | lingxi-user 返回错误，Feign 透传错误码给前端 |
| 手机号已注册 | lingxi-user 返回重复注册错误，透传 |
| 密码强度不足 | `@Pattern` 校验拦截，返回参数校验错误 |
| lingxi-user 服务不可用 | `result == null` → 抛 `ErrorCode.SYSTEM_ERROR` "注册服务异常，请重试" |
| Feign 超时/熔断 | 全局 Feign 异常处理 → 降级为系统错误提示 |
| 注册成功后未建企业 | `companyId=null`，前端引导创建企业（调用认证接口） |
| 重复调用注册 | 由 lingxi-user 幂等/唯一约束兜底（同一手机号不能重复注册） |

---

## 八、开发顺序

1. 建 `feign/dto/` 三个 DTO（RegisterRequestDTO / LoginResponseDTO / UserInfoDTO）
2. 建 `feign/AuthFeignClient.java`
3. 建 `domain/dto/HrRegisterRequestDTO.java`
4. 建 `service/HrAuthService.java` + `service/impl/HrAuthServiceImpl.java`
5. 建 `controller/HrAuthController.java`
6. 网关追加 `/api/v1/hr/register**` 路由
7. 自测（需 lingxi-user 起服 + 真实验证码，或 Feign mock）

---

## 九、与现有模块的关联

| 关联 | 说明 |
|------|------|
| 企业认证 | 注册成功后前端继续调 `POST /api/v1/hr/company/certification`（上传营业执照）创建企业，两步均需带 token |
| 成员管理 | HR 注册建企业后成为 HR_ADMIN，后续可创建面试官 |
| lingxi-user | 账号/验证码/Token 全部复用，本服务零存储 |

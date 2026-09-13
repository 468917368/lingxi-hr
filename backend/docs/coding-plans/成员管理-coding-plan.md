# 成员管理 - Coding Plan

> **所属模块：** lingxi-hr | **排期：** Day 1-2  
> **技术栈：** Spring Boot 2.7 + MyBatis + Redis + Feign  
> **参考：** 成员D开发排期 Day 1-2、系分文档 5.4 节 / 5.5.7 节 / 2.2.6 节 / 2.5.3 节 / 2.7.3 节  
> **范围：** 邀请码加入企业 + 刷新邀请码 + 成员管理（列表/添加/移除面试官），对应 B 端公司管理页「公司信息/成员管理」Tab
> **角色区分（勿混淆）：**
> - `sys_user.role`（全局账号角色）：CANDIDATE=求职者 / HR=HR / INTERVIEWER=面试官 / ADMIN=管理员
> - `hr_company_member.role`（企业内成员角色）：**HR_ADMIN**=HR管理员 / INTERVIEWER=面试官
> - 本文档成员判断均指 `hr_company_member.role`，即企业管理员用 `HR_ADMIN`、面试官用 `INTERVIEWER`

---

## 一、涉及文件清单

### 已就绪（无需修改）
| 文件 | 说明 |
|------|------|
| `entity/HrCompanyMember.java` | 成员实体 |
| `vo/HrMemberVO.java` | 成员列表出参 |
| `feign/dto/SysUserDTO.java` | 用户信息 DTO |
| `feign/AuthFeignClient.java` | 已有（HR注册复用）：`POST /api/v1/auth/register`；如需经 hr 代理发验证码可加 `sendCode`（可选） |
| `feign/dto/RegisterRequestDTO.java` | 已有：phone/code/password/name/role |
| `feign/dto/LoginResponseDTO.java` | 已有：token + user（取 user.id 作 userId） |
| `exception/HrErrorCode.java` | 已有 4001-4005，需补 4006、4008-4012（4007 废弃） |

### 需要编写代码
| 文件 | 当前 | 需要做 |
|------|:---:|------|
| `feign/UserFeignClient.java` | 已有 by-phone / create | 仅保留并补 A 已确认接口（`batch?ids=` / `{id}` / `{id}/profile`）；删 getUserByPhone / createUser（创建面试官改用 AuthFeignClient.register） |
| `dto/HrMemberDTO.java` | 已有 phone/name/department/techDirection/email | 加 password + code（注册表单入参：手机号/验证码/密码/姓名） |
| `mapper/HrCompanyMemberMapper.java` | 已有 insert / selectByCompanyAndUser / selectActiveByUserId | 加 selectByCompanyId / selectById / deleteById |
| `resources/mapper/HrCompanyMemberMapper.xml` | 已有 insert / selectByCompanyAndUser / selectActiveByUserId | 补上述方法 SQL |
| `mapper/HrCompanyMapper.java` | 已有 selectByInviteCode / selectById | 加 updateInviteCode（刷新邀请码用） |
| `resources/mapper/HrCompanyMapper.xml` | 已有 insert/selectById/selectByInviteCode/updateById | 补 updateInviteCode SQL |
| `service/HrCompanyMemberService.java` | 空 | 加方法声明 |
| `service/impl/HrCompanyMemberServiceImpl.java` | 空 | 写业务逻辑 |
| `controller/HrCompanyMemberController.java` | 空 | 写接口（邀请码加入 + 刷新邀请码 + 列表/添加/移除面试官） |
| `dto/HrJoinByInviteDTO.java` | - | 新增：邀请码加入入参（inviteCode） |
| `vo/HrJoinCompanyVO.java` | - | 新增：邀请码加入出参（companyId/companyName/role/status） |

### 需确认的依赖
| 依赖 | 接口 | 状态 | 用途 |
|------|------|:---:|------|
| 成员A | `GET /internal/users/batch?ids=1,2,3` | ✅ 已确认 | 批量获取用户信息（成员列表拼装 name/phone/avatar） |
| 成员A | `GET /internal/users/{id}` | ✅ 已确认 | 根据用户ID查询用户信息（含画像，批量不可用时循环单查） |
| 成员A | `GET /internal/users/{id}/profile` | ✅ 已确认 | 获取用户画像（头像等，列表可选补充） |
| 成员A | `POST /api/v1/auth/register`（AuthFeignClient 已接入） | ✅ 已确认* | 创建面试官账号（role=INTERVIEWER，表单含短信验证码） |
| 成员A | `POST /api/v1/auth/send-code` | ✅ 已就绪 | B端表单「获取验证码」发送短信验证码（lingxi-user 已有） |
| `SmsUtil`（common 模块） | `send(phone, templateCode, params)` | ✅ 已就绪 | 发送邀请短信（建号成功后通知面试官） |

> *register 需成员A 放开 role=INTERVIEWER（已沟通，后续放开），我们不改 A 的代码。
> **已废弃**：`GET /internal/users/by-phone`、`POST /internal/users`（createUser）——`createMember` 复用 `AuthFeignClient.register` 建号，手机号唯一性由 lingxi-user 兜底（重复注册返回错误透传）。

---

## 二、API 接口（5 个）

| 方法 | 路径 | 请求 | 响应 |
|------|------|------|------|
| `POST` | `/api/v1/hr/company/members/join-by-invite` | `HrJoinByInviteDTO` (JSON) | `Result<HrJoinCompanyVO>` |
| `PUT` | `/api/v1/hr/company/members/invite-code` | 无 | `Result<String>`（新邀请码） |
| `GET` | `/api/v1/hr/company/members` | `?role=&status=` | `Result<List<HrMemberVO>>` |
| `POST` | `/api/v1/hr/company/members` | `HrMemberDTO` (JSON) | `Result<Void>` |
| `DELETE` | `/api/v1/hr/company/members/{memberId}` | 无 | `Result<Void>` |

> 路径统一为 `/api/v1/hr/company/members`，需与前端确认。
> **邀请码加入路径**：系分文档 2.5.3 为 `POST /api/v1/company/join-by-invite`，此处收敛到 lingxi-hr 的 `/api/v1/hr/company/members/join-by-invite`（网关已配置 `/api/v1/hr/company/**` 路由，无需改网关）。
> **刷新邀请码路径**：prototype 公司信息Tab 的「刷新邀请码」按钮；备选路径 `/api/v1/hr/company/invite-code`（HrCompanyController），以前端确认为准。
> **移除范围**：仅支持移除面试官（role=INTERVIEWER）；HR_ADMIN 不可移除。

---

## 三、Mapper 层

### HrCompanyMemberMapper 方法（补充）

```java
// 已有（企业认证 part）
int insert(HrCompanyMember member);
HrCompanyMember selectByCompanyAndUser(companyId, userId);
HrCompanyMember selectActiveByUserId(@Param("userId") Long userId);

// 成员管理新增
List<HrCompanyMember> selectByCompanyId(@Param("companyId") Long companyId,
                                        @Param("role") String role,
                                        @Param("status") String status);
HrCompanyMember selectById(@Param("id") Long id);
int deleteById(@Param("id") Long id);     // 硬删除
```

### XML 新增 SQL

| 方法 | SQL |
|------|-----|
| `selectByCompanyId` | WHERE company_id=? AND role=? (optional) AND status=? (optional)，ORDER BY created_at DESC |
| `selectById` | WHERE id=? |
| `deleteById` | `DELETE FROM hr_company_member WHERE id=?`（硬删除，保留历史会话不受影响——`hr_interview.interviewer_id` 存的是 user_id 而非成员ID） |

### HrCompanyMapper 补充（刷新邀请码）

```java
int updateInviteCode(@Param("id") Long id, @Param("inviteCode") String inviteCode);
```

| 方法 | SQL |
|------|-----|
| `updateInviteCode` | `UPDATE hr_company SET invite_code = #{inviteCode} WHERE id = #{id}`（现有 updateById 不含 invite_code 字段，需独立方法） |

---

## 四、Service 层

### HrCompanyMemberService 接口

```java
HrJoinCompanyVO joinByInvite(Long userId, HrJoinByInviteDTO dto);
String refreshInviteCode(Long companyId);
void createMember(Long companyId, HrMemberDTO dto);
List<HrMemberVO> listMembers(Long companyId, String role, String status);
void removeMember(Long companyId, Long memberId);
```

### joinByInvite — 业务逻辑（邀请码加入企业，系分文档 2.2.6 / 2.5.3）

```
1. UserContext.getUserId() → null 抛 401
2. 校验 inviteCode 格式（6位大写字母+数字），不符抛 4006
3. hrCompanyMapper.selectByInviteCode(inviteCode) → null 抛 4006 "邀请码不存在"
4. 企业状态校验：
   - company.status != ACTIVE → 抛 4006（企业已禁用，按邀请码无效处理）
   - company.cert_status != APPROVED → 抛 4009 "该企业尚未通过认证，无法加入"
   - 邀请码不过期（已与成员A/产品确认，不校验过期）
5. hrCompanyMemberMapper.selectByCompanyAndUser(company.getId(), userId)
   != null → 抛 4008 "您已是该企业成员，无需重复加入"
6. 构建 HrCompanyMember（companyId/userId/role=HR_ADMIN/department=默认值/status=ACTIVE）
   注意：hr_company_member.department 为 NOT NULL，加入时无部门信息，默认填 "待定"
7. hrCompanyMemberMapper.insert(member)
8. 返回 HrJoinCompanyVO（companyId/companyName/role=HR_ADMIN/status）
```

### createMember — 业务逻辑（创建面试官：HR 填写注册表单，复用 register 建号）

```
1. UserContext.getCompanyId() → null 抛 401
2. 权限校验：当前用户须为本企业 HR_ADMIN（selectByCompanyAndUser + role=HR_ADMIN），否则抛 4011
3. Feign: authFeignClient.register(RegisterRequestDTO) —— 复用注册接口创建面试官账号
            入参：phone/code/password/name/role="INTERVIEWER"（HR 填写注册表单；验证码由 B端表单
            先调 /auth/send-code 发送到该手机号）
            响应 Result<LoginResponseDTO>，从 data.user.id 取 userId
            失败（验证码错误/过期、手机号已注册、角色不支持等）→ 透传 lingxi-user 错误码
4. 查 hr_company_member selectByCompanyAndUser(companyId, userId)
   != null → 抛 HrErrorCode.MEMBER_ALREADY_EXISTS(4002)
5. 构建 HrCompanyMember（companyId/userId/role=INTERVIEWER/department/techDirection/status=ACTIVE）
6. hrCompanyMemberMapper.insert(member)
7. 发送邀请短信（SmsUtil.send），失败只记日志不阻塞
```

### listMembers — 业务逻辑

```
1. UserContext.getCompanyId() → null 抛 401
2. hrCompanyMemberMapper.selectByCompanyId(companyId, role, status != null ? status : "ACTIVE")
3. 提取所有 userId，去重
4. 批量查用户信息：
   - 优先：GET /internal/users/batch?ids=1,2,3（成员A 已确认）
   - 降级：循环调 GET /internal/users/{id}（逐个查 userId）
   - 兜底：Feign 调用失败时 name="用户"+userId, phone="***"
5. 组装 List<HrMemberVO>：memberId/userId/name/phone/role/department/techDirection/interviewCount/status/createdAt
6. 返回
```

### removeMember — 业务逻辑（移除面试官）

```
1. UserContext.getCompanyId() → null 抛 401
2. 权限校验：当前用户为本企业 HR_ADMIN，否则抛 4011
3. hrCompanyMemberMapper.selectById(memberId) → null 抛 4010 "成员不存在"
4. 校验 member.getCompanyId().equals(companyId) → 否抛 4010（跨企业成员不可见）
5. 范围校验：member.getRole() == HR_ADMIN → 抛 4012 "不能移除企业管理员"（仅面试官可移除）
6. hrCompanyMemberMapper.deleteById(memberId)（硬删除，已确认）
   - 只删成员关系，不删 sys_user 账号（账号全局复用，其他企业可能仍关联）
```

### refreshInviteCode — 业务逻辑（刷新邀请码，prototype 公司信息Tab）

```
1. UserContext.getCompanyId() → null 抛 401
2. 权限校验：当前用户为本企业 HR_ADMIN，否则抛 4011
3. hrCompanyMapper.selectById(companyId) → null 抛 404（ErrorCode.NOT_FOUND）
4. 生成新的 6 位唯一邀请码（大写字母+数字，循环查重，与 joinByInvite 同一生成逻辑）
5. hrCompanyMapper.updateInviteCode(companyId, newCode)
6. 返回新邀请码（旧邀请码立即失效，已加入成员不受影响）
```

---

## 五、Controller 层

```java
@RestController
@RequestMapping("/api/v1/hr/company/members")
@RequireLogin
public class HrCompanyMemberController {

    /** 邀请码加入企业 */
    @PostMapping("/join-by-invite")
    public Result<HrJoinCompanyVO> joinByInvite(@Validated @RequestBody HrJoinByInviteDTO dto);

    /** 刷新邀请码 */
    @PutMapping("/invite-code")
    public Result<String> refreshInviteCode();

    /** 成员列表 */
    @GetMapping
    public Result<List<HrMemberVO>> listMembers(
        @RequestParam(required = false) String role,
        @RequestParam(required = false) String status);

    /** 添加成员（创建面试官） */
    @PostMapping
    public Result<Void> createMember(@Validated @RequestBody HrMemberDTO dto);

    /** 移除面试官 */
    @DeleteMapping("/{memberId}")
    public Result<Void> removeMember(@PathVariable Long memberId);
}
```

---

## 六、边界场景

| 场景 | 处理 |
|------|------|
| 手机号已注册 | `register` 返回 `PHONE_ALREADY_REGISTERED`，透传给前端（手机号唯一性由 lingxi-user 兜底） |
| 验证码错误 / 过期 | `register` 返回 CODE_INVALID / CODE_EXPIRED，透传给前端 |
| 面试官已在该企业 | `uk_company_user` 冲突 → 抛 4002 |
| 邀请短信发送失败 | 记录 error 日志，成员关系仍创建成功，响应中无提示 |
| 成员列表为空 | 返回空数组 `[]`（企业创建者 HR_ADMIN 已保障至少一条） |
| 批量查用户接口不可用 | 降级为循环单查，单查也失败则 name 显示 userId、phone 显示 `***` |
| 创建成员接口重复调用 | `uk_company_user` 拦截，返回 4002 |
| 邀请码格式不符 / 不存在 | 格式用 `@Pattern` 校验；查库为空抛 4006 |
| 邀请码对应企业未通过认证 | `company.cert_status != APPROVED` → 抛 4009 |
| 邀请码对应企业已禁用 | `company.status != ACTIVE` → 抛 4006 |
| 已在该企业（重复加入） | `selectByCompanyAndUser` 非空 → 抛 4008 |
| 加入者 department 为空 | `hr_company_member.department` NOT NULL，默认填 "待定" |
| 刷新邀请码企业不存在 | `selectById` 为空 → 抛 404 |
| 刷新邀请码后旧码失效 | 新码写入 `hr_company.invite_code`，旧码立即不可用；已加入成员不受影响 |
| 非 HR_ADMIN 刷新邀请码 | 权限不足 → 抛 4011 |
| 移除成员不存在 | `selectById` 为空 → 抛 4010 |
| 移除跨企业成员 | member.companyId ≠ 当前 companyId → 抛 4010（不可见） |
| 非 HR_ADMIN 操作成员 | 添加/移除均需 role=HR_ADMIN，否则抛 4011 |
| 移除 HR_ADMIN | 仅面试官可移除，role=HR_ADMIN → 抛 4012 |
| 被移除账号在其他企业 | 只删本企业成员关系，不影响 sys_user 账号及其他企业关系 |

---

## 七、错误码（补充 HrErrorCode 4006、4008-4012）

> 系分文档 2.5.3 邀请码加入用 2010-2013（归属模块A错误码段），此处统一归入 lingxi-hr 的 4001-4999 段。
> **4007 废弃**：系分文档 2011「邀请码已过期」— 已确认邀请码不过期，不占用 4007。

| code | 说明 | 触发场景 |
|------|------|------|
| 4006 | 邀请码不存在（或格式不正确） | 查库为空 / 企业 status=DISABLED |
| 4008 | 您已是该企业成员，无需重复加入 | 重复加入 |
| 4009 | 该企业尚未通过认证，无法加入 | cert_status != APPROVED |
| 4010 | 成员不存在 | 移除目标不存在或跨企业 |
| 4011 | 无权限操作该成员 | 非本企业 HR_ADMIN |
| 4012 | 不能移除企业管理员 | 移除 HR_ADMIN |

---

## 八、遗留确认（需与前端 / 成员A / 成员E 对齐）

> 已确认：邀请码不过期；移除面试官为硬删除（DELETE 行），仅面试官可移除。

| 项 | 现状 | 建议 |
|------|------|------|
| 创建面试官依赖 | lingxi-user register 现仅接受 role=CANDIDATE/HR（`AuthServiceImpl:148`），需放开 INTERVIEWER | 成员A 已确认后续放开，我们不改其代码；验证码由注册表单提供（复用 `/auth/send-code`），无需免验证码逻辑 |
| 邀请码加入角色 | 系分文档 2.2.6 约定加入者 `role=INTERVIEWER`；本期实现为 **HR_ADMIN** | 与系分文档有差异，需与产品/成员E确认是否接受「凭邀请码即成为管理员」；若否改回 INTERVIEWER |
| 非HR_ADMIN 操作成员 | 本期要求 role=HR_ADMIN 才能添加/移除/刷新邀请码 | 若面试官也需要部分操作权限需重新授权 |
| 刷新邀请码路径 | 本期放 HrCompanyMemberController `PUT /members/invite-code` | 备选 `/api/v1/hr/company/invite-code`（HrCompanyController），以前端确认为准 |

---

## 九、开发顺序

1. 补充 `HrErrorCode`（4006、4008-4012）
2. 补充 `HrCompanyMemberMapper` 接口（`selectByCompanyId`/`selectById`/`deleteById`）+ XML
3. 补充 `HrCompanyMapper.updateInviteCode` + XML
4. 新建 `HrJoinByInviteDTO` / `HrJoinCompanyVO`
5. 更新 `UserFeignClient` 补 A 已确认接口（`batch?ids=` / `{id}` / `{id}/profile`）；`createMember` 复用 `AuthFeignClient.register`（HR注册已就绪），mock 数据用于自测
6. 写 `HrCompanyMemberService` + `HrCompanyMemberServiceImpl`
7. 写 `HrCompanyMemberController`
8. 自测 5 个接口（邀请码加入 + 刷新邀请码 + 列表/添加/移除面试官）

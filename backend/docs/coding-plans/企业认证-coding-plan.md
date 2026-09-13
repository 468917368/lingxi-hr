# 企业认证 - Coding Plan

> **所属模块：** lingxi-hr | **排期：** Day 1-2  
> **技术栈：** Spring Boot 2.7 + MyBatis + Redis + Feign  
> **参考：** 成员D开发排期 Day 1-2、系分文档 5.4 节 / 5.5.12 节 \
> **todo：** 邀请码加入，成员管理，后端接口待实现


---

## 一、涉及文件清单

### 已就绪（无需修改）
| 文件 | 说明 |
|------|------|
| `entity/HrCompany.java` | 企业实体 |
| `entity/HrCompanyMember.java` | 成员实体 |
| `entity/HrCompanyCertification.java` | 认证申请实体 |
| `dto/HrCompanyDTO.java` | 企业信息入参 |
| `vo/HrCompanyVO.java` | 企业信息出参 |
| `exception/HrErrorCode.java` | 错误码 4001-4005 |

### 需要编写代码
| 文件 | 当前 | 需要做 |
|------|:---:|------|
| `mapper/HrCompanyMapper.java` | 空 | 加方法声明 |
| `mapper/HrCompanyCertificationMapper.java` | 空 | 加方法声明 |
| `mapper/HrCompanyMemberMapper.java` | 空 | 加方法声明 |
| `resources/mapper/HrCompanyMapper.xml` | 空 | 写 SQL |
| `resources/mapper/HrCompanyCertificationMapper.xml` | 空 | 写 SQL |
| `resources/mapper/HrCompanyMemberMapper.xml` | 空 | 写 SQL |
| `service/HrCompanyService.java` | 空 | 加方法声明 |
| `service/impl/HrCompanyServiceImpl.java` | 空 | 写业务逻辑 |
| `controller/HrCompanyController.java` | 空 | 写接口 |
| `vo/HrCertificationStatusVO.java` | - | 新增：认证状态出参（certStatus/certRejectReason/submittedAt） |

### 需确认的依赖
| 依赖 | 接口 | 用途 |
|------|------|------|
| `MinioUtil`（common 模块） | `upload(MultipartFile)` | 上传营业执照到 MinIO |

---

## 二、API 接口（5 个）

| 方法 | 路径 | 请求 | 响应 |
|------|------|------|------|
| `POST` | `/api/v1/hr/company/info` | `HrCompanyDTO` (JSON) | `Result<HrCompanyVO>` |
| `GET` | `/api/v1/hr/company/info` | 无 | `Result<HrCompanyVO>` |
| `PUT` | `/api/v1/hr/company/info` | `HrCompanyDTO` (JSON) | `Result<Void>` |
| `POST` | `/api/v1/hr/company/certification` | `HrCompanyDTO` + `businessLicense`(选填) + `certMaterial`(选填) | `Result<Void>` |
| `GET` | `/api/v1/hr/company/certification/status` | 无 | `Result<HrCertificationStatusVO>` |

---

## 三、Mapper 层

### HrCompanyMapper 方法

```java
int insert(HrCompany company);
HrCompany selectById(@Param("id") Long id);
HrCompany selectByInviteCode(@Param("inviteCode") String inviteCode);
int updateById(HrCompany company);
```

### HrCompanyMemberMapper 方法（已有 insert、selectByCompanyAndUser）

```java
int insert(HrCompanyMember member);
HrCompanyMember selectByCompanyAndUser(@Param("companyId") Long companyId,
                                       @Param("userId") Long userId);

// 新增：查用户有效的企业成员关系（提交认证前校验用户是否已有企业）
HrCompanyMember selectActiveByUserId(@Param("userId") Long userId);
```

### HrCompanyCertificationMapper 方法

```java
int insert(HrCompanyCertification certification);
HrCompanyCertification selectPendingByCompanyId(@Param("companyId") Long companyId);
```

### XML SQL 清单

| Mapper | SQL |
|--------|-----|
| `HrCompanyMapper.xml` | `insert`（13 列）、`selectById`、`selectByInviteCode`、`updateById`（动态 set，name 不更新） |
| `HrCompanyMemberMapper.xml` | `insert`（7 列）、`selectByCompanyAndUser`、`selectActiveByUserId`（WHERE user_id=? AND status='ACTIVE'） |
| `HrCompanyCertificationMapper.xml` | `insert`（5 列）、`selectPendingByCompanyId`（WHERE company_id=? AND status='PENDING'） |

---

## 四、Service 层

### HrCompanyService 接口

```java
HrCompanyVO createCompany(HrCompanyDTO dto);
HrCompanyVO getCompanyInfo(Long companyId);
void updateCompanyInfo(Long companyId, HrCompanyDTO dto);
void submitCertification(HrCompanyDTO dto, MultipartFile businessLicense, MultipartFile certMaterial);
HrCertificationStatusVO getCertificationStatus(Long userId);
```

### createCompany — 业务逻辑

```
1. UserContext.getUserId() → null 抛 401
2. 校验用户是否已有企业：查 hr_company_member WHERE user_id=? AND role='HR_ADMIN'
   已存在 → 抛 HrErrorCode.COMPANY_NAME_DUPLICATE(4001) "您已创建企业"
3. 生成 6 位邀请码（大写字母+数字），循环直到 selectByInviteCode == null
4. 构建 HrCompany（name/shortName/description/industry/scale/logoUrl/address/website/inviteCode/certStatus=PENDING/status=ACTIVE）
5. hrCompanyMapper.insert(company) → 获取自增 ID
6. 构建 HrCompanyMember（companyId/role=HR_ADMIN/status=ACTIVE）
7. hrCompanyMemberMapper.insert(member)
8. 以上 5-7 在同一事务 @Transactional
9. BeanUtils 转 HrCompanyVO 返回
```

### getCompanyInfo — 业务逻辑

```
1. UserContext.getCompanyId() → null 抛 401
2. hrCompanyMapper.selectById(companyId) → null 抛 404
3. BeanUtils 转 VO 返回
```

### updateCompanyInfo — 业务逻辑

```
1. UserContext.getCompanyId()
2. hrCompanyMapper.selectById(companyId) → null 抛 404
3. cert_status == "APPROVED" → dto.setName(null)（不更新 name）
4. 仅更新非 null 字段：shortName/description/logoUrl/address/website
5. hrCompanyMapper.updateById(company)
```

### submitCertification — 业务逻辑

> 企业认证 = HR 首次入驻表单：一次完成「创建企业 + 企业成员 + 认证申请」，企业信息随表单提交，营业执照/证明材料选填（对齐系分文档 2.2.4）

```
1. UserContext.getUserId() → null 抛 401
2. 校验该 HR 账号是否已有企业：hrCompanyMemberMapper.selectActiveByUserId(userId)
   != null → 抛 HrErrorCode.COMPANY_NAME_DUPLICATE(4001) "您已创建企业"（有则拒绝，防止重复入驻）
   == null → 继续创建（无企业）
3. businessLicense != null → 校验文件格式（jpg/png/pdf）和大小（≤5MB），不通过抛 4004，通过则上传 → businessLicenseUrl
4. certMaterial != null → 校验同上，上传 → certMaterialUrl
5. 生成 6 位邀请码（大写字母+数字），循环直到 selectByInviteCode == null
6. 构建 HrCompany（dto 企业信息 + inviteCode + businessLicenseUrl + certStatus=PENDING + status=ACTIVE）
7. hrCompanyMapper.insert(company) → 获取 companyId
8. 构建 HrCompanyMember（companyId/userId/role=HR_ADMIN/status=ACTIVE）
9. hrCompanyMemberMapper.insert(member)
10. 构建 HrCompanyCertification（companyId/applicantId=userId/businessLicenseUrl/certMaterialUrl/status=PENDING）
11. hrCompanyCertificationMapper.insert(certification)
12. 步骤 7/9/11 在同一事务 @Transactional
```

### getCertificationStatus — 业务逻辑

> pending 审核页依赖：展示认证状态、拒绝原因、提交时间
> **注意**：注册当次 token 无 companyId claim，网关不注入 X-Company-Id，故不能依赖 `UserContext.getCompanyId()`，改为按 userId 反查成员关系定位企业

```
1. UserContext.getUserId() → null 抛 401
2. hrCompanyMemberMapper.selectActiveByUserId(userId) → null 抛 404（无有效成员关系）
3. hrCompanyMapper.selectById(member.getCompanyId()) → null 抛 404
4. 构建 HrCertificationStatusVO（certStatus=company.certStatus、certRejectReason=company.certRejectReason、submittedAt=company.createdAt）返回
```

---

## 五、Controller 层

```java
@RestController
@RequestMapping("/api/v1/hr/company")
public class HrCompanyController {

    @PostMapping("/info")
    public Result<HrCompanyVO> createCompany(@Validated @RequestBody HrCompanyDTO dto);

    @GetMapping("/info")
    public Result<HrCompanyVO> getCompanyInfo();

    @PutMapping("/info")
    public Result<Void> updateCompanyInfo(@Validated @RequestBody HrCompanyDTO dto);

    @PostMapping("/certification")
    public Result<Void> submitCertification(
        HrCompanyDTO dto,   // multipart 表单字段绑定：name/industry/scale/address/website 等
        @RequestParam(value = "businessLicense", required = false) MultipartFile businessLicense,
        @RequestParam(value = "certMaterial", required = false) MultipartFile certMaterial);

    @GetMapping("/certification/status")
    public Result<HrCertificationStatusVO> getCertificationStatus();
}
```

---

## 六、边界场景

| 场景 | 处理 |
|------|------|
| 用户未登录 | `UserContext.getUserId()` 为 null，返回 401 |
| 用户已有企业（submitCertification） | 查 `hr_company_member` status=ACTIVE，存在则抛 4001（有则拒绝，防止重复入驻） |
| 用户已创建过企业（createCompany） | 查 `hr_company_member` role=HR_ADMIN，存在则抛 4001 |
| 邀请码生成冲突 | 循环重试直到唯一 |
| `uk_name` 冲突 | 捕获 `DuplicateKeyException`，转为 4001 |
| cert_status=APPROVED 时改 name | 忽略 name，不抛异常 |
| 认证文件格式错误 | 营业执照/证明材料提供时校验 jpg/png/pdf，不通过抛 4004 |
| 认证文件过大 | 营业执照/证明材料提供时校验 >5MB 抛 4004 |
| 认证状态查询（getCertificationStatus） | userId 为 null → 401；无有效成员关系/企业不存在 → 404 |
| MinIO 上传失败 | 抛系统异常，前端提示重试 |

---

## 七、开发顺序

1. 写 `HrCompanyMapper` + XML（insert, selectById, selectByInviteCode, updateById）
2. 补充 `HrCompanyMemberMapper` XML（insert, selectByCompanyAndUser）
3. 写 `HrCompanyCertificationMapper` + XML（insert, selectPendingByCompanyId）
4. 写 `HrCompanyService` + `HrCompanyServiceImpl`
5. 写 `HrCompanyController`
6. 自测 5 个接口

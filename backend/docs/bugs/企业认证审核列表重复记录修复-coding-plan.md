# 企业认证审核列表重复记录修复 - Coding Plan

> **所属模块：** lingxi-admin（成员E 侧 Mapper）
> **类型：** Bug 修复（JOIN 一对多行放大 → 列表重复）
> **日期：** 2026-08-11
> **技术栈：** MyBatis + MySQL(InnoDB)
> **参考：** `docs/schema.sql` hr_company / hr_company_certification / hr_company_member / sys_user；`后端总体系分文档.md` 企业认证审核
> **状态：** ✅ **已实施 + lingxi-admin 编译通过 + XML 校验通过（2026-08-11）；待部署生效**

---

## 〇、Bug 概述

**一句话：** 企业认证审核列表出现重复记录——同一企业「test1」出现两次，`id` 相同、行业/规模/提交时间完全相同，仅申请人姓名/电话不同；列表 total=5，实际唯一企业仅 4。根因是查询**双重 1:N JOIN 行放大**，非数据重复入库。

**影响：** 审核页总数虚高、同一企业重复展示，干扰审核操作。

---

## 一、复现步骤与现象

1. 访问企业认证审核列表接口（`lingxi-admin`，`selectCertifications`）。
2. 返回 5 条，其中两条：
   ```json
   {"id": 1, "companyName": "test1", "industry": "企业服务/SaaS", "scale": "50-99人",
    "applyTime": "2026-08-03T11:52:37", "applicantName": "hr_1",  "contactPhone": "16735263528"}
   {"id": 1, "companyName": "test1", "industry": "企业服务/SaaS", "scale": "50-99人",
    "applyTime": "2026-08-03T11:52:37", "applicantName": "as",    "contactPhone": "16735263520"}
   ```
3. 期望：每个企业仅 1 行（total=4），申请人 = 提交认证的那个人。

---

## 二、根因分析

`lingxi-admin/HrCompanyMapper.xml` `selectCertifications` 的 JOIN：

```sql
FROM hr_company c
LEFT JOIN hr_company_certification cert ON cert.company_id = c.id       -- ① 1:N（无 company_id 唯一约束，schema.sql:531 仅 idx）
LEFT JOIN hr_company_member m ON m.company_id = c.id AND m.role='HR_ADMIN' -- ② 1:N（企业可有多个 HR_ADMIN）
LEFT JOIN sys_user u ON u.id = m.user_id
```

两个 1:N 表 JOIN 产生**笛卡尔放大**：
- 企业「test1」（id=1）有 **2 个 HR_ADMIN** 成员 → ② 把同一认证记录放大为 2 行，`applicantName/contactPhone` 分别取到两个成员。
- 若某企业提交过多次认证，① 同样会放大。

**语义错误：** 申请人/联系人本应是**认证记录的 `applicant_id`**（`hr_company_certification.applicant_id`，schema.sql:521 必填），而不是 JOIN 全量 HR_ADMIN 成员。

**同源隐患：** `selectById`（详情）也 join 了 `hr_company_member`，靠 `LIMIT 1` 兜底，会**随机**取一个 HR_ADMIN 当联系人，非申请人。

---

## 三、修复方案

1. **去掉 `hr_company_member` JOIN**，申请人改关联 `cert.applicant_id`（`sys_user u ON u.id = cert.applicant_id`）——每行恰好一个申请人。
2. **每企业只取最新一条认证申请**（`MAX(created_at)` 子查询），消除认证记录 1:N 放大。
3. `selectById` 同样去成员 JOIN、改取 `cert.applicant_id`，消除随机联系人隐患。

---

## 四、涉及文件清单

| 文件 | 类型 | 说明 |
|------|:---:|------|
| `lingxi-admin/.../resources/mapper/HrCompanyMapper.xml` | 修改 | `selectCertifications` / `selectById` 两处 SQL |

（`HrCompanyMapper.java` 接口签名不变；`AdminCertificationServiceImpl` 无需改动。）

---

## 五、具体改动

### `selectCertifications`（列表）

```sql
SELECT
    c.id, c.name AS companyName, c.industry, c.scale, c.address,
    c.cert_status AS certStatus,
    c.business_license_url AS licenseUrl,
    cert.cert_material_url AS certMaterialUrl,
    cert.created_at AS applyTime,
    u.name AS applicantName,
    u.phone AS contactPhone
FROM hr_company c
LEFT JOIN (
    SELECT cc.company_id, cc.applicant_id, cc.cert_material_url, cc.created_at
    FROM hr_company_certification cc
    INNER JOIN (
        SELECT company_id, MAX(created_at) AS max_created_at
        FROM hr_company_certification
        GROUP BY company_id
    ) latest
    ON latest.company_id = cc.company_id AND latest.max_created_at = cc.created_at
) cert ON cert.company_id = c.id
LEFT JOIN sys_user u ON u.id = cert.applicant_id
<where>
    AND c.cert_status = #{certStatus}   -- 原条件不变
    AND c.name LIKE CONCAT('%', #{keyword}, '%')
</where>
ORDER BY cert.created_at DESC
```

### `selectById`（详情）

```sql
FROM hr_company c
LEFT JOIN hr_company_certification cert ON cert.company_id = c.id
LEFT JOIN sys_user u ON u.id = cert.applicant_id   -- 原 m.user_id → cert.applicant_id
WHERE c.id = #{id}
ORDER BY cert.created_at DESC
LIMIT 1
```

---

## 六、验证方案

1. 部署 lingxi-admin 后重查列表：`test1` 仅 1 行，申请人显示提交认证者；`total` 由 5 → 4。
2. 多 HR_ADMIN 企业回归：不再放大。
3. 有多次认证提交历史的企业回归：仅显示最新一条。
4. 详情页回归：联系人 = 认证申请人；`approve/reject` 走 `selectById` 不受影响。
5. 空数据回归：无认证记录的企业 LEFT JOIN 仍返回（申请人/时间为 null），列表不丢行。

---

## 七、风险与边界

| 项 | 说明 | 处置 |
|----|------|------|
| created_at 同秒冲突 | 同企业两条认证 `created_at` 恰好相同仍可能出 2 行 | 概率极低，可接受；如要绝对唯一可改按 `MAX(id)` |
| 无认证记录企业 | 申请人/联系人为 null（LEFT JOIN） | 符合预期（认证列表本质是已提交认证的企业） |
| 部署 | 改动在 lingxi-admin Mapper XML，需重新部署该模块 | 部署后生效 |
| 详情联系人语义变化 | `contactPerson` 从「任意 HR_ADMIN」改为「认证申请人」 | 语义更正确 |

---

## 八、参考

- `lingxi-admin/src/main/resources/mapper/HrCompanyMapper.xml`（selectById / selectCertifications）
- `lingxi-admin/.../service/impl/AdminCertificationServiceImpl.java`（getCertifications/getCertificationDetail）
- `docs/schema.sql`：`hr_company_certification`（518-534 行，applicant_id 必填、company_id 仅普通索引）、`hr_company_member`（497-512 行）

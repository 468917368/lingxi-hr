# Offer 管理页 HC 统计卡片全 0 修复 - Coding Plan

> **所属模块：** lingxi-hr（成员D 排查）+ lingxi-job（成员B 侧权限配置）
> **类型：** Bug 修复（服务间权限矩阵缺项 → 公司级聚合拿不到数据）
> **日期：** 2026-08-11
> **技术栈：** Spring Boot 2.7 + Feign + Service-Auth（X-Caller-Service / X-Service-Token）
> **参考：** `后端开发规范文档.md` 服务身份认证（系分 B-05）；`后端总体系分文档.md` 5.5.4 Offer HC 概览
> **状态：** ✅ **已实施 + lingxi-job 编译通过（2026-08-11）；待部署生效**

---

## 〇、Bug 概述

**一句话：** Offer 管理页顶部 HC 统计卡片（总HC / 已锁定 / 已确认 / 可用）公司级聚合全部显示 0，因为 lingxi-hr 调 B 侧「企业岗位列表」内部接口 `GET /internal/jobs/company/{companyId}` 被服务权限矩阵拦截（403），聚合循环拿不到任何岗位。

**影响：** HR 无法看到企业整体 HC 使用情况；单岗概览（带 jobId）正常，仅公司级全 0。

---

## 一、复现步骤与现象

1. 访问 `GET /api/v1/hr/offers/hc-overview`（不传 jobId，公司级）。
2. 返回：
   ```json
   {"jobId": null, "jobTitle": "全公司", "totalHc": 0, "reservedHc": 0, "confirmedHc": 0, "availableHc": 0}
   ```
3. 期望：企业全部岗位 HC 求和（非 0，前提企业有岗位且配了 total_hc）。

---

## 二、根因分析

`HrOfferServiceImpl.getHcOverview`（`lingxi-hr`，`service/impl/HrOfferServiceImpl.java:262-300`）公司级聚合流程：

```
listCompanyJobsOrEmpty(companyId)          // Feign GET /internal/jobs/company/{companyId}
  → for 每个 jobId: queryJobBestEffort()   // Feign GET /internal/jobs/{jobId}，求和
```

**断点：第一步被拦。** lingxi-job 的 `ServicePermissionConfig.java:43-46` 给 `lingxi-hr` 的权限矩阵只有：

| 模式 | 用途 | lingxi-hr 是否有 |
|------|------|:---:|
| `^/internal/jobs/[0-9]+$` | 单岗校验/查 HC | ✅ |
| `^/internal/jobs/[0-9]+/requirements$` | 考察要点 | ✅ |
| `^/internal/jobs/[0-9]+/hc/.*$` | reserve/confirm/release | ✅ |
| `^/internal/jobs/company/[0-9]+$` | **企业岗位列表** | ❌ **缺失** |

`InternalServiceAuthInterceptor`（`lingxi-job/interceptor/InternalServiceAuthInterceptor.java:50-56`）对 `/internal/jobs/company/{companyId}` 判定 `canAccess("lingxi-hr", uri)=false` → **403/2002** → `listCompanyJobsOrEmpty` 捕获失败返回**空列表** → 聚合循环 0 次 → 四项全 0。

对照：`lingxi-user` 权限矩阵已含企业岗位列表模式（`ServicePermissionConfig.java:40`），说明 `listCompanyJobs` 接口本身正常，只是 `lingxi-hr` 未放行（2026-08-07 加 hc-overview 聚合时漏配）。

---

## 三、修复方案

**在 `lingxi-hr` 权限矩阵补一个 URI 模式**，仅放开「企业岗位列表」访问，不动其他权限边界：

```java
Pattern.compile("^/internal/jobs/company/[0-9]+$")
```

---

## 四、涉及文件清单

| 文件 | 类型 | 说明 |
|------|:---:|------|
| `lingxi-job/.../config/ServicePermissionConfig.java` | 修改 | `lingxi-hr` 权限矩阵补企业岗位列表模式 |

（lingxi-hr 侧代码无需改动——聚合逻辑本身正确，只是拿不到数据源。）

---

## 五、具体改动

`lingxi-job/src/main/java/com/lingxi/job/config/ServicePermissionConfig.java` `lingxi-hr` 条目：

```java
PERMISSION_MATRIX.put("lingxi-hr", Arrays.asList(
        Pattern.compile("^/internal/jobs/[0-9]+$"),
        Pattern.compile("^/internal/jobs/[0-9]+/requirements$"),
        Pattern.compile("^/internal/jobs/company/[0-9]+$"),   // 新增：企业岗位列表（hc-overview 公司级聚合）
        Pattern.compile("^/internal/jobs/[0-9]+/hc/.*$")));
```

---

## 六、验证方案

1. 部署 lingxi-job 后：`GET /api/v1/hr/offers/hc-overview`（不传 jobId）→ 企业岗位 HC 求和正确显示（totalHc/reservedHc/confirmedHc/availableHc）。
2. 单岗概览（带 jobId）回归：仍正常。
3. 权限回归：lingxi-hr 访问其他未授权内部接口（如 `/internal/jobs/statistics/**`）仍被 403；lingxi-user/resume/admin 不受影响。

---

## 七、风险与边界

| 项 | 说明 | 处置 |
|----|------|------|
| 权限边界 | 仅补 `lingxi-hr` 访问本企业岗位列表；与 `lingxi-user` 既有授权一致 | 无放宽 |
| 部署 | B 侧改动，需重新打包部署 lingxi-job；`lingxi-hr` 的 `service-auth.token` 与 B 侧 `tokens.lingxi-hr` 一致即可（当前均为 `hr-token-dev`） | 部署后生效 |
| 数据前提 | 企业需有未删除岗位且配置了 `total_hc` 才显示非 0；无岗位时全 0 属正常 | 非本次缺陷 |

---

## 八、参考

- `lingxi-job/.../config/ServicePermissionConfig.java`（权限矩阵）
- `lingxi-job/.../interceptor/InternalServiceAuthInterceptor.java`（401/403 拦截）
- `lingxi-hr/.../service/impl/HrOfferServiceImpl.java:262-300`（getHcOverview 聚合）
- `lingxi-hr/.../feign/JobFeignClient.java`（listCompanyJobs Feign 契约）

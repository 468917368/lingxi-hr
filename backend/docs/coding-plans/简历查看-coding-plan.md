# 简历查看 - Coding Plan

> **所属模块：** lingxi-hr | **关联：** Day 3 候选人管理扩展（需求单 v1.1 → v1.2）
> **技术栈：** Spring Boot 2.7 + Feign（纯读接口，无建表/MQ/定时任务）
> **参考：** `docs/coding-plans/简历查看-后端需求单.md`、`候选人管理-coding-plan.md`、系分文档 4.4.2 / 5.5.2
> **范围：** HR 人才库「查看简历」按钮接真实接口：`GET /api/v1/hr/candidates/{applicationId}/resume`
> **跨服务依赖：** lingxi-resume（成员C）内部简历详情 `GET /internal/resumes/{id}/detail`（**全量**，免归属校验）——✅ C 已确认落地（2026-08-06）；lingxi-hr 内部已有 `getApplication`（拿 resumeId + 归属校验）
> **状态：** ✅ **lingxi-hr 侧已实现并编译通过（2026-08-06）**；C 侧 `/internal/resumes/{id}/detail` 已落地（`fe150947` 2026-08-05），**本地全链路联调通过**

---

## 〇、核心决策摘要

1. **数据链路（纯 Feign 透传）**：D 侧 `fetchApplication`（4300/4301 校验）拿 `resumeId` → Feign 调 C 内部简历详情 **`GET /internal/resumes/{id}/detail`** → 透传 C 的 `ResumeDetailVO`。**不经过公开 `GET /api/v1/resumes/{id}`**（有归属校验，HR 调用返回 RESUME_NOT_FOUND）。
2. **3.1 归属成员C**：`GET /internal/resumes/{id}/detail`（全量）由 C 实现（免归属校验，复用 `toDetailVO`），D 只做 Feign 调用。**2026-08-06 路径变更**：全量挪至 `/detail`，`/internal/resumes/{id}` 改为精简版（InternalResumeVO，供 lingxi-job 出题）。✅ C 已落地。
3. **DTO 命名**：lingxi-hr 侧新建 `feign/dto/ResumeDetailDTO`（**全量**字段镜像 C 的 `ResumeDetailVO`）。⚠️ 注意与 agent 包 `agent.feign.ResumeDetailDTO`（出题用精简 6 字段版）**同名不同包**，导入时勿混用。
4. **权限**：查看简历为**查看操作**，与 list/top5 一致 —— 本企业 **ACTIVE 成员**（非 HR_ADMIN），非成员 4011。
5. **简历不存在错误码**：新增 `CANDIDATE_RESUME_NOT_FOUND(4303, "候选人简历不存在")` —— applicationId 存在但 `resumeId` 为空，或 C 返回 RESUME_NOT_FOUND 时使用（**2026-08-05 用户确认**，前端按独立码明确提示）。
6. **Feign 降级**：C 内部接口网络异常 → 抛 500「简历获取失败，请稍后重试」；C 返回 RESUME_NOT_FOUND → 转 4303。**不降级为空**（简历内容为展示核心）。

---

## 一、涉及文件清单

### 已就绪（无需修改）
| 文件 | 说明 |
|------|------|
| `feign/ResumeFeignClient.java` | 已有 `getApplication`（拿 `resumeId` + `companyId` 归属校验） |
| `service/impl/HrCandidateServiceImpl.java` | 已有私有 `fetchApplication`（4300/4301）与 `requireActiveMember`（4011），直接复用 |
| `domain/vo/CandidateVO.java` | 列表项 `id`(=applicationId)，前端用 `record.id` 调本接口 |
| `exception/HrErrorCode.java` | 已有 4300/4301（需补 4303） |

### 需要修改（✅ 2026-08-06 全部完成）
| 文件 | 当前 | 修改 |
|------|:---:|------|
| `feign/ResumeFeignClient.java` | 3 个方法 | 加 `getInternalResume(id)` → `Result<ResumeDetailDTO>`，`@GetMapping("/resumes/{id}/detail")`（2026-08-06 全量路径调整） |
| `exception/HrErrorCode.java` | 4300/4301/4302 | 补 `CANDIDATE_RESUME_NOT_FOUND(4303, "候选人简历不存在")` |
| `service/HrCandidateService.java` | 3 个方法 | 加 `ResumeDetailDTO getCandidateResume(Long companyId, Long applicationId)` |
| `service/impl/HrCandidateServiceImpl.java` | 已有 | 实现 `getCandidateResume`（复用 `fetchApplication` + `requireActiveMember`） |
| `controller/HrCandidateController.java` | 3 个接口 | 加 `GET /{applicationId}/resume` |

### 需要新建（✅ 2026-08-06 已完成）
| 文件 | 说明 |
|------|------|
| `feign/dto/ResumeDetailDTO.java` | 简历详情 DTO（**全量**镜像 lingxi-resume `ResumeDetailVO`，15 字段含 `facePhotoUrl`），`@JsonIgnoreProperties(ignoreUnknown=true)` |

---

## 二、API 接口（1 个）

### HR 侧查看候选人简历
`GET /api/v1/hr/candidates/{applicationId}/resume`

| 参数 | 类型 | 必填 | 描述 |
|------|------|:---:|------|
| applicationId | Long（path） | 是 | 投递记录 ID（= 前端候选人列表 `record.id`） |

**鉴权**：`@RequireLogin` + 本企业 ACTIVE 成员（复用 `requireActiveMember`，非成员 4011）

**响应**：`Result<ResumeDetailDTO>`，字段对齐 lingxi-resume `ResumeDetailVO`：
```
id fileName fileFormat fileSize isDefault parseStatus resumeMdUrl
facePhotoUrl cardStructure candidateName phone email wechat createdAt updatedAt
```
- `cardStructure`：JSON 对象（DB 存 JSON 字符串，C 已反序列化返回，D 透传），前端直接渲染
- 前端渲染二选一：`resumeMdUrl`（解析 Markdown 预览）或 `cardStructure`（卡片渲染）

---

## 三、Feign 契约（成员C 3.1，✅ C 已落地 2026-08-06）

### lingxi-resume 内部简历详情接口（全量）
```java
// 路径：/internal/resumes/{id}/detail（网关路由 /internal/resumes/** ✅ 已存在，白名单免鉴权）
@GetMapping("/resumes/{id}/detail")
Result<ResumeDetailVO> getInternalResume(@PathVariable("id") Long id);
```
- **2026-08-06 路径变更**：全量简历挪至 `/detail`；`/internal/resumes/{id}` 为精简版（InternalResumeVO，供 lingxi-job 出题）。C 侧 `InternalResumeController` 已确认存在该端点。
- **免归属校验**：不调 `getOwnedResume`，仅 `selectById` 判空，null → 抛 C 侧 `RESUME_NOT_FOUND`
- 实现建议：C 在 `ResumeService`/`ResumeServiceImpl` 加 `getInternalResumeDetail(id)`，复用现有 `toDetailVO`（当前 private，需在同实现类内新增方法或抽公共方法）

**C 契约响应字段（v1.1 14 字段 + facePhotoUrl，全量 15 字段）：**
```
id / fileName / fileFormat / fileSize / isDefault / parseStatus / resumeMdUrl / facePhotoUrl
/ cardStructure / candidateName / phone / email / wechat / createdAt / updatedAt
```
> D 侧 DTO **按全量 15 字段建**（含 `facePhotoUrl`，`@JsonIgnoreProperties(ignoreUnknown=true)` 兜底——C 契约若真不返回该字段则透传 null，不影响）。

### 与成员C的对齐点
| 项 | 说明 | 状态 |
|------|------|:---:|
| 接口路径 | `GET /internal/resumes/{id}/detail`（全量；`/{id}` 为精简版供 lingxi-job） | ✅ C 已落地 |
| 免归属校验 | 仅供 lingxi-hr 内部 Feign 调用 | ✅ |
| 响应 DTO | C 已有 `ResumeDetailVO`（15 字段，含 `facePhotoUrl`） | ✅ 已存在 |
| 不存在语义 | 返回 C 侧 `RESUME_NOT_FOUND(3101)`，D 转 4303 | ✅ |

---

## 四、Service 层

### HrCandidateService 接口追加
```java
ResumeDetailDTO getCandidateResume(Long companyId, Long applicationId);
```

### getCandidateResume — 业务逻辑
```
1. companyId 为 null → 抛 401
2. requireActiveMember(companyId)（与 list/top5 一致，非 ACTIVE → 4011）
3. application = fetchApplication(applicationId, companyId)   // 复用私有方法
   - 投递不存在/查询失败 → 4300
   - 跨企业 → 4301
4. resumeId = application.getResumeId()
   - resumeId == null → 抛 4303「候选人简历不存在」（候选人未上传简历）
5. Feign 调 C：resumeFeignClient.getInternalResume(resumeId)
   - 网络异常 → 抛 500「简历获取失败，请稍后重试」（不降级为空）
   - Result 非成功：
     · C 返回 RESUME_NOT_FOUND(code=3101) → 抛 4303
     · 其他错误码 → 透传 C code/message
   - 成功但 data 为 null → 抛 4303
   - 成功 → 返回 result.getData()
6. 记日志：companyId/applicationId/resumeId
```

---

## 五、Controller 层

```java
/**
 * 查看候选人简历（HR 人才库「查看简历」按钮，需求单 v1.1）
 */
@GetMapping("/{applicationId}/resume")
public Result<ResumeDetailDTO> candidateResume(@PathVariable Long applicationId) {
    return Result.success(hrCandidateService.getCandidateResume(
            UserContext.getCompanyId(), applicationId));
}
```

---

## 六、边界场景

| 场景 | 处理 |
|------|------|
| 未登录 / 无企业 | companyId 为 null → 401 |
| 非本企业成员 / 非 ACTIVE | `requireActiveMember` → 4011 |
| applicationId 不存在 | `fetchApplication` → 4300 |
| 跨企业候选人 | `fetchApplication` companyId 不等 → 4301 |
| 投递存在但 resumeId 为空（未上传简历） | 4303 候选人简历不存在 |
| C 内部接口返回 RESUME_NOT_FOUND | 4303 |
| C 内部接口网络异常 / 超时 | 500 简历获取失败，请稍后重试（不降级为空） |
| C 返回其他错误码 | 透传 C code/message |
| cardStructure 为 null | 透传 null（前端兜底用 resumeMdUrl） |
| 候选侧 `GET /api/v1/resumes/{id}` | 归属校验不受影响（本人仍可看） |

---

## 七、错误码（补充 HrErrorCode 4303）

| code | 说明 | 触发场景 |
|------|------|------|
| 4011 | 无权限操作该成员 | 非本企业 / 非 ACTIVE 成员 |
| 4300 | 候选人不存在 | 投递记录不存在 / 查询失败 |
| 4301 | 无权查看该候选人 | 投递不属于本企业 |
| 4303（新增） | 候选人简历不存在 | resumeId 为空 / C 返回 RESUME_NOT_FOUND |

---

## 八、已确认决策 + 遗留确认

### 已确认（2026-08-05 拍板 + 2026-08-06 落地）
| 项 | 结论 |
|------|------|
| 简历不存在错误码 | **新增 4303**「候选人简历不存在」（前端明确提示） |
| DTO 命名/字段 | 新建 `feign/dto/ResumeDetailDTO`，**全量 15 字段含 `facePhotoUrl`**，导入勿与 agent 包精简版混用 |
| C 契约响应字段 | 以用户确认的 v1.1 14 字段为准；D 侧 DTO 仍含 `facePhotoUrl`（实际代码有，ignoreUnknown 兜底） |
| C 侧 3.1 路径 | **2026-08-06 变更**：全量挪至 `GET /internal/resumes/{id}/detail`；`/internal/resumes/{id}` 为精简版（InternalResumeVO，供 lingxi-job 出题）——✅ C 已落地 |

### 遗留确认
| 项 | 现状 | 建议 |
|------|------|------|
| 本地联调 | C 侧 `/internal/resumes/{id}/detail` 已落地（`fe150947`），D 侧实现已编译通过 | ✅ **联调通过（2026-08-06）**：正常链路 + 4303 分支 |

---

## 九、开发顺序（✅ 2026-08-06 全部完成；C 侧依赖已落地 `fe150947`，**本地全链路联调通过**）

1. （C 侧）lingxi-resume 落地 `GET /internal/resumes/{id}/detail` ✅
2. 新建 `feign/dto/ResumeDetailDTO`（全量字段）✅
3. `ResumeFeignClient` 加 `getInternalResume`（路径 `/resumes/{id}/detail`）✅
4. `HrErrorCode` 补 `CANDIDATE_RESUME_NOT_FOUND(4303)` ✅
5. `HrCandidateService` + `HrCandidateServiceImpl` 加 `getCandidateResume` ✅
6. `HrCandidateController` 加 `GET /{applicationId}/resume` ✅
7. 编译验证通过（zulu-8）✅
8. ⏳ 本地联调（依赖 C 3.1 本地可调）

---

## 十、联调验证（⏳ 待执行，C 3.1 已落地）

- **正常链路**：HR 对候选人点「查看简历」→ 返回 `resumeMdUrl` + `cardStructure`，前端可渲染简历
- **异常分支**：跨企业 → 4301；applicationId 不存在 → 4300；候选人未上传简历 / C 返回 RESUME_NOT_FOUND → 4303
- **回归**：候选侧 `GET /api/v1/resumes/{id}` 本人仍可查看；Day 3 候选人列表/Top5/标记不受影响

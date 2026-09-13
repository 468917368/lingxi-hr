# 简历上传与解析修复 - Coding Plan

> **所属模块：** lingxi-resume（成员D 侧）
> **类型：** Bug 修复（上传空白校验缺失 + 头像提取 NPE + 百宝箱平台 ~90s 切断致解析 FAILED + 并发会话串扰）
> **日期：** 2026-08-12
> **技术栈：** Spring Boot 2.7 / PDFBox 2.0 / Apache POI 5.2 / hutool / 百宝箱 API（SSE）
> **参考：** `docs/百宝箱agentSDK.md`；`ResumeErrorCode.java`；`RealBaibaoxiangClient.java`
> **状态：** ✅ 已实施 + zulu-8 编译通过；待重启 lingxi-resume 验证

---

## 〇、Bug 概述

简历「上传 → 解析」链路上暴露的 3 个独立问题（同一批排查中一并修复）：

**Bug 1 — 空白文件拦截失效（上传侧）**
`fileSize == 0` 只拦得住 0 字节文件；空白 PDF 是合法结构但无内容，实测 1.2KB~12KB，能通过上传校验入库，直到解析阶段才因"文本过少"FAILED，OSS 留下孤儿原件。且空文件分支错误码误用 `FILE_TOO_LARGE(3002)`，code 与 message 语义矛盾（code=3002 但提示"文件不能为空"）。

**Bug 2 — FacePhotoExtractor 空指针（解析侧，连带）**
空白 PDF 页无 resources：`page.getResources()` 返回 null，`resources.getXObjectNames()` 抛 NPE。被外层 catch 兜住只影响头像，但日志刷异常，属明确缺陷。

**Bug 3 — 百宝箱解析 FAILED（核心）**
百宝箱平台网关对 API 响应有 **约 90 秒上限**。简历文本约 1500 字触发的生成耗时约 91s：
- 流式：SSE 连接 92.6s/91.2s 被服务端切断，只收到 header（sessionId）0 chunk → "未返回有效 chunk" → FAILED；
- 非流式：91.8s 返回 **HTTP 502**；
- 但生成**实际完成**并缓存在会话中（平台控制台可见完整结果）；
- 重试重发完整简历文本 → 又触发 ~91s 生成 → 再被切断，重试必败。
- 附加：`lastConversationId` 为单例实例字段，并发解析/诊断互相覆盖会话上下文。

---

## 一、复现步骤与现象

1. 上传约 1500 字简历（袁权测试简历，textLen=1650）。
2. 日志现象（2026-08-12 本地）：
   ```
   发起百宝箱请求: ... query长度=1766, 文本长度=1650
   [~93s 后] 捕获百宝箱会话ID: 20260812sOHR42190656
   [1ms 后]   百宝箱未返回有效 chunk
   Agent 解析失败，重试 1 次 → 仍失败 → 置 FAILED（保留记录，可手动删除）
   ```
3. 平台侧控制台可见完整解析结果（生成成功，响应被网关切断）。
4. 直测接口（同 appId `202608APTjYV21059259`）：

   | 请求 | 结果 |
   |------|------|
   | 最短 query "你好" | 2.6s 正常返回 |
   | 全量 prompt + ~100字简历 | 44s 返回完整 JSON |
   | **全量 prompt + ~1500字简历（流式）** | **92.6s / 91.2s `IncompleteRead`（仅 359 字节 header），0 chunk** |
   | **全量 prompt + ~1500字简历（非流式）** | **91.8s HTTP 502** |
   | **短查询 + 失败会话 conversationId（取回）** | **39.7s 返回完整结果（229 chunk / 53KB）** |

---

## 二、根因分析

- **Bug 1**：空白判断只做了字节级（size==0），未做内容级（页数 / 文本 / 图片）。注释虽写"空文件拒绝 + ≤10MB上限"，但 `size==0` 对空白 PDF 无效。
- **Bug 3**：百宝箱平台网关对单次响应有 ~90s 上限，长简历生成耗时超过该值即被切断；生成结果缓存在会话（sessionId）中，**带 conversationId 的短查询可在 <90s 内取回**，是可靠的绕过路径。
- **并发串扰**：`lastConversationId` / 早期 `lastResumeText` 均用单实例字段，多请求并发互相覆盖。

---

## 三、修复方案

1. **上传预检（内容级空白判定）**：新增 `FILE_EMPTY(3005)`；PDF 用 PDFBox（页数=0 或 无文本且无图 → 拒收），Word 用 POI（docx→XWPF、doc→HWPF，无文本且无图 → 拒收）。扫描件（无文本但有图）放行，交给解析链路 OCR。预检异常按非空白放行，不阻断上传。
2. **空文件分支错误码修正**：`size==0` 抛 `FILE_EMPTY`（原误用 `FILE_TOO_LARGE`）。
3. **FacePhotoExtractor**：`page.getResources()` 加 null 判断。
4. **两段式取回（绕过 90s 切断）**：`readSseIntoQueue` 中首请求 0 chunk 但已捕获会话 ID → 自动用短查询 `RETRIEVAL_QUERY` + conversationId 取回已生成结果。
5. **会话 ID 并发隔离**：`lastConversationId` 单字段 → `Map<String,String> lastConversationIds`（按 contextId）；`handleSseData` / `buildQuery` / `chatA2A` / `chatText` 全部按 context 存取。
6. **pom 显式声明**：`poi-ooxml` + `poi-scratchpad`（版本对齐 Tika 传递的 5.2.3）。

---

## 四、涉及文件清单

| 文件 | 类型 | 说明 |
|------|:---:|------|
| `lingxi-resume/pom.xml` | 修改 | 显式声明 `poi-ooxml` / `poi-scratchpad` 5.2.3 |
| `lingxi-resume/.../exception/ResumeErrorCode.java` | 修改 | 新增 `FILE_EMPTY(3005)` |
| `lingxi-resume/.../service/impl/ResumeServiceImpl.java` | 修改 | 空文件分支改用 `FILE_EMPTY`；新增 ③.6 空白 PDF/Word 预检 `rejectBlankPdf` / `rejectBlankWord` / `hasPdfImages` |
| `lingxi-resume/.../agent/FacePhotoExtractor.java` | 修改 | `resources == null` 判断，修 NPE |
| `lingxi-resume/.../agent/impl/RealBaibaoxiangClient.java` | 修改 | 两段式取回 + conversationId 按 contextId 隔离 |

（`BaibaoxiangClient` 接口无需改动；`getLastConversationId()`/`setConversationId()` 全仓无调用方，移除 override 走接口默认实现。）

---

## 五、具体改动

### 5.1 `ResumeErrorCode.java`
```java
CAREER_EMPTY(3004, "职业名为空"),
FILE_EMPTY(3005, "文件内容为空，请上传有效简历"),   // 新增
STATUS_NOT_ALLOWED(3006, "当前状态不可操作"),
```

### 5.2 `ResumeServiceImpl.java` — 上传预检
```java
// ③ 大小校验：空文件拒绝（原误用 FILE_TOO_LARGE）
if (fileSize == 0) {
    throw new BusinessException(ResumeErrorCode.FILE_EMPTY);
}

// ③.6 空白文档预检：无文本且无图 → 拒收，避免入库后解析失败留孤儿文件
if ("pdf".equals(fileFormat)) {
    rejectBlankPdf(file, candidateId, fileName);
} else if ("docx".equals(fileFormat) || "doc".equals(fileFormat)) {
    rejectBlankWord(file, fileFormat, candidateId, fileName);
}
```
判定逻辑：PDF 用 `PDDocument.getNumberOfPages()==0` 或 `PDFTextStripper` 文本为空且无图片；Word 用 `XWPFWordExtractor`/`WordExtractor` 文本为空且 `getAllPictures()`/`getPicturesTable()` 无图。含图（扫描件）放行；预检异常按非空白放行。

### 5.3 `FacePhotoExtractor.java`
```java
PDResources resources = page.getResources();
if (resources != null) {          // 空白 PDF 页可能无 resources
    for (COSName name : resources.getXObjectNames()) { ... }
}
```

### 5.4 `RealBaibaoxiangClient.java` — 两段式取回
```java
private static final String RETRIEVAL_QUERY =
        "请把上一轮对那份简历生成的完整解析 JSON 结果原样完整输出，不要省略任何字段。";

// readSseIntoQueue 内：
int firstResult = readSseStream(httpRequest, chunkBuffer, doneTitles, queue, cancelled, contextId);
String convId = lastConversationIds.get(contextId);
if (chunkBuffer.length() == 0 && firstResult == 0 && !cancelled.get()
        && convId != null && !convId.isEmpty()) {
    log.warn("百宝箱首请求未返回内容，尝试取回已生成结果: contextId={}, conversationId={}",
            contextId, convId);
    readSseStream(buildRetrieveRequest(convId), chunkBuffer, doneTitles, queue, cancelled, contextId);
}
```
`readSseStream` 抽出为公共方法（返回值：1 有内容 / 0 无内容 / -1 HTTP非200 / -2 取消）；`buildRetrieveRequest` 构造短查询 + conversationId 请求。

### 5.5 `RealBaibaoxiangClient.java` — 会话 ID 并发隔离
```java
private final Map<String, String> lastConversationIds = new ConcurrentHashMap<>();
// handleSseData 增加 contextId 参数，捕获时 lastConversationIds.put(contextId, convId)
// chatA2A / chatText / buildQuery(MODE_SCORE) 均改为 lastConversationIds.get(contextId)
```

---

## 六、验证方案

1. **空白 PDF / Word 上传**：直接返回 `3005 文件内容为空，请上传有效简历`，不入库、不传 OSS。
2. **空白 PDF 不再 NPE**：日志不再出现 `FacePhotoExtractor` NullPointerException。
3. **长简历解析**：重启 lingxi-resume，上传袁权简历，日志应出现：
   ```
   百宝箱首请求未返回内容，尝试取回已生成结果: contextId=..., conversationId=...
   百宝箱产出完成: contextId=..., 长度=4569, 总耗时=...
   ```
   简历置 COMPLETED。
4. **短简历回归**：解析路径不受影响（首请求直接成功，不走取回）。
5. **并发解析**：两用户同时解析，会话 ID 互不覆盖（`lastConversationIds` 按 contextId 隔离）。
6. **扫描件 PDF**：无文本但有图 → 上传放行，解析链路 OCR 兜底（不误拒）。

---

## 七、风险与边界

| 项 | 说明 | 处置 |
|----|------|------|
| 取回查询依赖平台缓存 | 若首请求生成未完成（非超时类失败），取回可能仍为空 | 取回失败落到原有 FAILED/规则引擎兜底，不影响更坏 |
| 取回请求偶发 >90s | 平台波动时取回也可能超时 | 外层 catch 兜底，语义不劣于现状 |
| 空白判定漏网 | 含少量文字但无实际内容的文档仍可能通过 | 解析阶段 `MIN_TEXT_LENGTH=100` 质量校验兜底 |
| Word 空白预检成本 | 上传时多一次 POI 解析（秒级） | 可接受；10MB 上限约束 |
| `lastConversationIds` 不清理 | key = 历史简历/诊断 ID，量级小 | 与 `lastResumeTexts` 同策略，可接受 |
| 平台 90s 上限 | 属平台侧配置，未根治（仅绕过） | 建议同步在百宝箱控制台排查工作流生成超时/精简节点 |

---

## 八、参考

- `lingxi-resume/src/main/java/com/lingxi/resume/agent/impl/RealBaibaoxiangClient.java`
- `lingxi-resume/src/main/java/com/lingxi/resume/service/impl/ResumeServiceImpl.java`（upload / rejectBlankPdf / rejectBlankWord）
- `lingxi-resume/src/main/java/com/lingxi/resume/agent/FacePhotoExtractor.java`（extractFromPdf）
- `lingxi-resume/src/main/java/com/lingxi/resume/exception/ResumeErrorCode.java`
- `docs/百宝箱agentSDK.md`（SSE 事件格式：header/chunk/thinking/meta；sessionId 即 conversationId）
- 直测结论：流式 92.6s/91.2s 切断、非流式 91.8s 502、短查询取回 39.7s 成功（2026-08-12 实测）

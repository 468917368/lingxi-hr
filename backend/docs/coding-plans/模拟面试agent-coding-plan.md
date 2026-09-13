# Mock Interview Agent - Coding Plan（v2.1）

> **所属模块：** lingxi-hr | **排期：** Day 6-7
> **技术栈：** Spring Boot + MyBatis + Feign + SSE + **百宝箱 tboxsdk**
> **参考：** 成员D开发排期 Day 6-7、系分文档 5.2.3/5.5.9/5.6.2/5.6.4/5.7、`docs/schema.sql` mock_ 三表、`docs/百宝箱开放平台 ✖️ Java SDK.md`
> **设计决策（v2.1 更新）：**
> 1. **出题不依赖数据库题库**：AI 基于【岗位 JD/考察要点】+【求职者简历】动态出题，不走 B 侧 `job_question` 搜题接口（B 搜题接口也未落地）。
> 1b. **简历输入以能力模型为主、简历详情为辅**：C 侧 `GET /api/v1/resumes/{id}/ability-model` 已实现，返回 5 维能力分 + `subDimensions`（技能/年限/评分依据）——作为 AI 判断候选人水平、动态调节出题难度的主输入；`ResumeDetailVO.cardStructure/resumeMdUrl` 作为项目/经历细节补充。
> 2. **LLM 接入用百宝箱 tboxsdk**（`cn.tbox:tboxsdk`，SDK 文档 v0.0.10），封装 `TboxLlmClient`，**不参考 lingxi-resume 的自研 A2A 实现**。`hr.agent.mock=true` 时用 `MockTboxLlmClient` 返回预设 JSON（仅联调降级）。
> 3. **数据获取走 Feign**：岗位信息调 B `/internal/jobs/{jobId}/requirements`（已就绪，含 interviewFocus/coreSkills）；简历调 C 用户接口（**Feign token 透传已实现**，见"前置任务"）。

---

## 〇、前置调研结论（2026-08-04 核对）

| 依赖项 | 状态 | 说明 |
|------|:---:|------|
| mock_session/mock_answer/mock_report DDL | ✅ | `schema.sql` 727-789 已落地，代码无实体 |
| B 岗位考察要点 `GET /internal/jobs/{jobId}/requirements` | ✅ | 返回 `JobRequirementResponse`：jobType/coreSkills/softSkills/industryExperience/**interviewFocus** |
| B 岗位 JD 原文 | ⚠️ | `/internal/jobs/{id}` 明确**不返回 jd_text**；JD 原文仅在 C 端接口 `GET /api/v1/jobs/{jobId}` 的 `JobDetailVO.jdText/jdSummary`，需求职者身份 + token 透传 |
| C 简历详情 `GET /api/v1/resumes/{id}` | ⚠️ | 用户接口（需登录，只能查自己的），返回 `ResumeDetailVO`（含 cardStructure/resumeMdUrl/candidateName）；**系分 7.1 的内部接口 `/internal/resumes/{id}` 未落地** |
| C 能力模型 `GET /api/v1/resumes/{id}/ability-model` | ✅ 已实现 | 用户接口；**owner 校验**（只能查自己的简历）→ 正好走 Feign token 透传；返回 5 维能力分（professionalSkill/workExperience/industryKnowledge/comprehensiveQuality/learningGrowth）+ subDimensions（JSON，技能/年限/评分依据）。数据由简历解析 Agent 写入 `resume_ability_model`（schema.sql:374，`uk_resume_id` 唯一） |
| **Feign token 透传拦截器** | ✅ **已实现** | `lingxi-common/FeignConfig` 新增 `tokenRelayInterceptor`（2026-08-04），透传当前线程 `Authorization`；异步线程/已手动设置时跳过。JDK21 编译通过 |
| 百宝箱 SDK | 📄 | `cn.tbox:tboxsdk:0.0.10` + okhttp 4.10.0 + okhttp-sse 4.10.0；`TboxClient(new HttpClientConfig(token))`，对话型 `chat` / 生成型 `completion`，均返回 `Iterable<Map<String,Object>>` 流式 |
| 网关路由 `/api/v1/mock-interview/**` | ✅ | 排期确认 A 已就绪 |

---

## 〇.五、前置任务（必须，blocking）

### 1. Feign 请求透传 Authorization token（lingxi-common）✅ 已完成（2026-08-04）

`lingxi-common/src/main/java/com/lingxi/common/config/FeignConfig.java` 已新增 `tokenRelayInterceptor` Bean（RequestInterceptor）：

```java
@Bean
public RequestInterceptor tokenRelayInterceptor() {
    return template -> {
        // 已手动设置过 Authorization，则不覆盖（大小写不敏感）
        boolean hasAuth = template.headers().keySet().stream()
                .anyMatch(h -> h.equalsIgnoreCase(HttpHeaders.AUTHORIZATION));
        if (hasAuth) return;
        // 无请求上下文（MQ 消费者/定时任务/@Async）跳过
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return;
        String auth = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && !auth.isEmpty()) {
            template.header(HttpHeaders.AUTHORIZATION, auth);
        }
    };
}
```

> 影响面：仅 6 个 web 服务（user/job/resume/hr/chat/admin）通过 `scanBasePackages` 加载此配置；**gateway（WebFlux）不扫描 `com.lingxi.common`，不受影响**。内部接口（`/internal/**`）无 `@RequireLogin`，多带 header 不影响放行。

### 2. C 侧简历查询通道（二选一，优先 A）

| 方案 | 说明 |
|------|------|
| **方案 A（推荐）**：D 用求职者 token 调 C 用户接口 `GET /api/v1/resumes/{id}` / `GET /api/v1/resumes` / `GET /api/v1/resumes/{id}/ability-model`（依赖前置任务 1 的 token 透传） | 无需 C 改代码；简历 ID 由前端在 generate 请求传，或 D 取 isDefault=1 |
| **方案 B**：请 C 落地内部接口 `GET /internal/resumes/{id}` + `GET /internal/resumes/by-user/{userId}`（系分 7.1 契约） | 更干净，但依赖 C 排期 |

> v2.1 默认走**方案 A**（token 透传已实现，可执行）；若 C 内部接口先落地则无缝切换。

---

## 一、涉及文件清单

> **包结构约定**：模拟面试专属代码（实体/Mapper/DTO/Feign/Controller/Agent/配置）**全部收拢到 `com.lingxi.hr.agent` 包下**，与 HR 常规分层（`domain/`、`mapper/`、`controller/`）隔离，便于整体复用/移除。Mapper XML 放 `resources/mapper/agent/`，namespace 指向 `agent.mapper` 接口。

### 新增代码（统一位于 `com.lingxi.hr.agent` 下）

| 子包 | 文件 | 说明 |
|------|------|------|
| `agent/entity` | `MockSession.java` / `MockAnswer.java` / `MockReport.java` | 三表实体（对齐 schema.sql） |
| `agent/mapper` | `MockSessionMapper` / `MockAnswerMapper` / `MockReportMapper` | 基础 CRUD；XML 放 `resources/mapper/agent/`（`mapper-locations: classpath:mapper/**/*.xml` 自动覆盖子目录） |
| `agent/dto` | `MockGenerateRequest.java` / `MockAnswerRequest.java` / `MockQuestionVO.java` / `MockAnswerResultVO.java` / `MockReportVO.java` | 入参/出参 |
| `agent/feign` | `ResumeApiFeignClient.java`（`path="/api/v1"`：简历列表/详情/能力模型）+ `JobRequirementDTO.java` / `ResumeDetailDTO.java` / `AbilityModelDTO.java` | 出题数据 Feign + 响应 DTO |
| `agent/config` | `HrAgentProperties.java` / `BaibaoxiangProperties.java` / `MockInterviewProperties.java` / `TboxClientConfig.java` | 配置绑定（agent/百宝箱/每日次数限制）+ `@Bean TboxClient` |
| `agent` | `LlmClient.java` | LLM 客户端接口：`String generate(String systemPrompt, Map<String,Object> inputs, String userId, long timeoutMs)` |
| `agent/impl` | `TboxLlmClient.java` / `MockTboxLlmClient.java` | tboxsdk 实现（mock=false）/ 预设 JSON 实现（mock=true），均 `@ConditionalOnProperty` |
| `agent` | `MockInterviewAgent.java` | 三大场景编排（出题/评分/报告），数据获取 + LLM 生成 + SSE 事件转发 |
| `agent/tools` | `FetchJobRequirementsTool.java` / `FetchCandidateResumeTool.java` | 岗位考察要点 / 简历能力画像获取 |
| `agent` | `MockPromptBuilder.java` | 三大场景 systemPrompt + inputs 组装（含出题策略/评分维度提示词） |
| `agent/controller` | `MockInterviewController.java` | 5 个接口（3 个 SSE） |

### 修改代码

| 文件 | 改动 |
|------|------|
| `exception/HrErrorCode.java` | 追加 Mock Interview 错误码（枚举是 HR 模块公共类，**保留原位置**，仅追加值） |
| `feign/JobFeignClient.java` | 追加 `GET /internal/jobs/{jobId}/requirements` → `Result<JobRequirementDTO>`（HR 公共 Feign，保留原位置，mock 复用） |
| `application.yml` / `application-dev.yml` | ① 追加 `hr.agent.mock` + `baibaoxiang.*`（三个 appId 已就绪，见下方配置参考）；② **`mybatis.type-aliases-package` 改为 `com.lingxi.hr.domain.entity,com.lingxi.hr.agent.entity`**（实体已移入 agent 包）；③ 追加 `hr.mock-interview.quota`（每日每求职者次数限制，默认 3） |
| `lingxi-hr/pom.xml` | 新增 `cn.tbox:tboxsdk:0.0.10` + `com.squareup.okhttp3:okhttp:4.10.0` + `com.squareup.okhttp3:okhttp-sse:4.10.0` |
| `lingxi-common/.../FeignConfig.java` | ✅ 已实现（2026-08-04）：新增 `tokenRelayInterceptor`（token 透传，见〇.五） |

**配置参考（appId + token 已就绪，2026-08-04）：**

```yaml
hr:
  agent:
    mock: true            # 联调用 true；百宝箱工作流联调验证通过后置 false
  mock-interview:
    quota: ${HR_MOCK_INTERVIEW_QUOTA:3}   # 每日每求职者模拟面试次数上限，0=不限（已实现 2026-08-05）
baibaoxiang:
  api:
    key: ${TBOX_API_KEY:your-tbox-api-key}   # 百宝箱访问令牌（共用，一个账号一个）
  agent:
    generate-app-id: ${TBOX_GENERATE_APP_ID:202608APE9vh20999665}   # 出题工作流
    score-app-id: ${TBOX_SCORE_APP_ID:202608APlpZR21033809}         # 评分工作流
    report-app-id: ${TBOX_REPORT_APP_ID:202608AP7lse21015615}       # 报告工作流
```

---

## 二、API 接口（5 个，系分 5.5.9）

| 方法 | 路径 | 请求 | 响应 | 类型 |
|------|------|------|------|:---:|
| `POST` | `/api/v1/mock-interview/generate` | `MockGenerateRequest` | SSE | 出题 |
| `GET` | `/api/v1/mock-interview/{sessionId}/questions` | - | `Result<List<MockQuestionVO>>` | 获取题目 |
| `POST` | `/api/v1/mock-interview/{sessionId}/answer` | `MockAnswerRequest` | SSE | 评分 |
| `POST` | `/api/v1/mock-interview/{sessionId}/skip` | - | `Result<Void>` | 跳过 |
| `GET` | `/api/v1/mock-interview/{sessionId}/report` | - | SSE | 报告 |

**请求体：**

```json
// generate（v2 增加 resumeId，选填；不传则取默认简历）
{ "jobId": 101, "jobTitle": "高级前端工程师", "questionCount": 5, "resumeId": 3001 }

// answer
{ "questionNumber": 1, "answer": "React虚拟DOM是..." }
```

**SSE 事件协议**（对齐系分 5.5.9 示例；心跳沿用 `heartbeat`）：

```
// generate：progress → result → done（失败发 error）
event: progress
data: {"sequence":1,"code":"FETCH_JOB","message":"正在获取岗位考察重点..."}

event: progress
data: {"sequence":2,"code":"FETCH_RESUME","message":"正在读取简历能力画像..."}

event: progress
data: {"sequence":3,"code":"GENERATING","message":"AI正在基于岗位与简历定制题目..."}

event: result
data: {"sessionId":"mock-20260804-001","questions":[{"questionNumber":1,"questionType":"PROJECT","dimension":"项目深挖","content":"..."}]}

event: done
data: {"timestamp":"2026-08-04T10:00:10.000Z"}

// answer：progress → result（三维权分+点评）
event: result
data: {"questionNumber":1,"techAccuracyScore":82,"expressionScore":75,"knowledgeDepthScore":68,"overallScore":76.5,"aiComment":"..."}

// report：progress → result（完整报告）
event: result
data: {"sessionId":"mock-...","overallScore":78.5,"overallLevel":"GOOD","techAccuracyScore":...,"expressionScore":...,"knowledgeDepthScore":...,"projectScore":...,"highlights":[],"weaknesses":[],"improvementPlan":[],"answeredCount":4,"skippedCount":1,"totalDurationSec":600}
```

---

## 三、数据库（对齐 `schema.sql` 727-789）

### mock_session
| 字段 | 说明 |
|------|------|
| session_id | 唯一；格式 `mock-YYYYMMDD-NNN`，NNN 用 **Redis INCR 当日计数器**（key=`mock:session:seq:{date}`） |
| candidate_id | `UserContext.getUserId()` |
| job_id / job_title | job_title 冗余，岗位下线不丢历史 |
| total_questions | 5/8/10 |
| overall_score | 完成后写 |
| status | IN_PROGRESS / COMPLETED |
| started_at / completed_at | - |

### mock_answer
- `session_id + question_number` 联合唯一逻辑（同题重复提交 = UPDATE 覆盖，系分 5.7）
- question_content / question_dimension / question_type：题目快照
- candidate_answer / is_skipped / 三维分 / overall_score / ai_comment / answered_at

### mock_report
- session_id 唯一；overall_score/overall_level；维度分；highlights/weaknesses/improvement_plan（JSON）；统计字段

> **不建表**：DDL 已由 `schema.sql` 提供，本次只建实体 + Mapper + XML。

---

## 四、Agent 编排设计

### 4.1 LlmClient 接口（封装百宝箱 tboxsdk）

```java
public interface LlmClient {
    /** 单次生成，返回 LLM 原始文本（期望 JSON 字符串）。超时抛异常。 */
    String generate(String systemPrompt, Map<String,Object> inputs, String userId, long timeoutMs);
}
```

- **`TboxLlmClient`**（mock=false 激活）：
  ```java
  TboxClient client = new TboxClient(new HttpClientConfig(token)); // @Bean 注入
  Iterable<Map<String,Object>> chunks = client.completion(appId, inputs, userId);
  // 拼接 chunks 中的文本片段 → 完整 JSON 返回
  ```
  - `inputs`：生成型智能体的自定义参数 Map（SDK 文档 §3），key 与百宝箱工作流参数名对齐：出题 `jobInfo`/`resumeInfo`/`questionCount`；评分 `questionContent`/`questionType`/`referenceAnswer`/`candidateAnswer`；报告 `jobInfo`/`questions`/`overallScore`（见 `模拟面试-百宝箱工作流设计.md`）
  - `userId`：传 `candidateId.toString()`（会话上下文）
  - 超时：整体 60s（系分 5.6.2）；单次调用用 Future/阻塞等待实现
- **`MockTboxLlmClient`**（mock=true 激活）：按场景标记返回预设 JSON（题目/评分/报告），模拟流式拼接结果。

> 不参考 lingxi-resume 的 A2A 自研客户端；纯 tboxsdk 官方调用。百宝箱 3 个工作流已在平台侧建立（2026-08-04），返回 JSON Schema 见 4.3 与 `模拟面试-百宝箱工作流设计.md`。

### 4.2 三大场景数据流（MockInterviewAgent）

**场景 A：出题（generate）**
```
1. 建 mock_session(status=IN_PROGRESS)（session_id 由 Redis INCR 生成）
2. progress(FETCH_JOB) → FetchJobRequirementsTool → Feign B /internal/jobs/{jobId}/requirements
   - 成功 → 岗位输入 = {jobType, coreSkills[], interviewFocus[], softSkills[], industryExperience}
   - 失败 → 岗位输入 = {jobTitle}（仅凭岗位名出题，不阻塞）
3. progress(FETCH_RESUME) → FetchCandidateResumeTool → Feign C（ResumeApiFeignClient）
   - resumeId 缺省 → 先调 `GET /resumes` 取 isDefault=1 的简历
   - 成功 → 简历输入 = **能力模型为主**（5 维分 + subDimensions 技能/年限）+ **简历详情为辅**（cardStructure 技能/项目要点，文本化截断至 ~2000 字）
   - 能力模型 null（简历未解析）→ 降级为仅简历详情；都没有 → 简历输入 = 空，标注"无简历，按通用岗位要求出题"（`resumeUsed:false`）
4. progress(GENERATING) → MockPromptBuilder 组装出题 prompt → LlmClient.generate
   - 出题策略（系分 5.6.2）：BASIC 30% / PROJECT 40% / BOUNDARY 20% / COMPREHENSIVE 10%，由浅入深
   - total_questions 5/8/10，AI 返回题目 JSON 数组
   - LLM 不可用 → 走 QuestionBankFallback 内置通用模板（5 道，按题型模板），SSE 标记降级
5. 逐题写 mock_answer（question_content 快照；无评分）
6. SSE：progress → result(questions) → done
```

**场景 B：单题评分（answer）**
```
1. 校验 session IN_PROGRESS（COMPLETED → 40014），session_id 不存在 → 4007
2. 组装评分 prompt：题目(question_content) + 用户答案 + 参考要点（岗位维度，可无）
   → LlmClient.generate → 返回 {techAccuracy, expression, knowledgeDepth, aiComment}
3. 权重（系分 5.6.4）：overall = tech×0.5 + expr×0.3 + depth×0.2
4. 超时(>15s) → overall=0，aiComment="评分服务繁忙，本题未评分"，不阻塞
5. UPDATE mock_answer（同题覆盖）
6. SSE：progress → result(评分)
```

**场景 C：生成报告（report）**
```
1. 校验 session IN_PROGRESS；无有效答题记录 → 40015
2. 聚合 mock_answer 各题三维分 → 维度均分 + 总均分（未评分题剔除）
3. LlmClient.generate 生成 highlights/weaknesses/improvementPlan（LLM 不可用 → 按分数阈值规则生成）
4. 写 mock_report + mock_session(status=COMPLETED, overall_score, completed_at)
5. SSE：progress → result(报告) → done
```

### 4.3 LLM 输入/输出 JSON Schema（百宝箱生成型智能体约定）

**出题输出：**
```json
{ "questions": [
    {"questionType":"BASIC","difficulty":"MEDIUM","dimension":"基础验证","content":"..."},
    {"questionType":"PROJECT","difficulty":"HARD","dimension":"项目深挖","content":"..."}
]}
```

**评分输出：**
```json
{ "techAccuracyScore":82, "expressionScore":75, "knowledgeDepthScore":68, "aiComment":"..." }
```

**报告输出：**
```json
{ "highlights":["..."], "weaknesses":["..."], "improvementPlan":["..."] }
```

---

## 五、错误码（HrErrorCode 追加段）

| code | HTTP | 说明 |
|------|:---:|------|
| 4007 | 404 | session_id 不存在（排期错误码表 4006/4008 之间预留空号） |
| 40014 | 409 | 面试已完成（系分 5.7） |
| 40015 | 400 | 暂无答题记录，无法生成报告（系分 5.7） |
| 40016 | 400 | 简历不存在或无权限（C 简历查询失败） |
| 40017 | 500 | 出题服务繁忙，请稍后重试（LLM + 岗位数据均不可用） |
| 40018 | 429 | 今日模拟面试次数已达上限（`hr.mock-interview.quota`，按当天已建会话计数） |

> 4007/40014-40018 与既有 4001-4006、4008-4012、4100+ 不冲突；若团队码段约定有变，以最新约定为准。

---

## 六、边界与降级（系分 5.7 八、C端模拟面试）

| 场景 | 处理 |
|------|------|
| 岗位要求 Feign 失败 | 岗位输入降级为仅 jobTitle，正常出题 |
| 简历 Feign 失败/无简历 | 简历输入为空，按通用岗位要求出题；前端 result 里带 `resumeUsed:false` 提示 |
| 简历有但能力模型 null（未解析完） | 降级为仅用简历详情出题；能力模型缺失不影响主流程 |
| 能力模型 5 维分过低/过高 | 由 AI 依据 5 维分动态调节题目难度（如专业分低 → 多基础题；项目分高 → 深挖题），属正常出题逻辑非异常 |
| LLM 不可用（tboxsdk 调用异常/超时） | 出题走内置模板；评分/报告走规则兜底；SSE 发 error 事件提示降级 |
| 求职者中途退出 | session 保持 IN_PROGRESS，再次进入可选"继续面试/重新开始"（重新开始=新建 session） |
| 作答超 5 分钟 | 前端倒计时结束自动提交（is_skipped=1，answer 空），评分跳过该题 |
| 作答 < 20 字 | 不拦截，AI 点评标注"回答过短" |
| 面试完成后再提交 | 校验 session.status != COMPLETED，违例 40014 |
| 同一题多次提交 | UPDATE 覆盖（session_id + question_number） |
| LLM 评分超时 | 本题 overall=0 + "评分服务繁忙"，不阻塞其他题 |
| 报告时无答题记录 | 40015 |
| 每日次数达到上限 | `generate` 前校验当天已建会话数（`hr.mock-interview.quota`，0=不限），超限抛 40018，**不创建会话**，SSE 发 error 事件 |
| 岗位已下线 | 不校验岗位状态（job_title 已冗余），可完成面试 |
| AI 输出非 JSON | 解析失败重试 1 次；仍失败走兜底模板/规则 |

---

## 七、开发顺序

### Day 6 上午：实体 + Mapper + 公共能力
1. `agent/entity` 三实体（对齐 schema.sql）
2. `agent/mapper` 三 Mapper + `resources/mapper/agent/` 3 个 XML
3. `application.yml`/`application-dev.yml`：`hr.agent.mock` + `baibaoxiang.*` + `mybatis.type-aliases-package` 追加 `com.lingxi.hr.agent.entity`
4. `HrErrorCode` 追加错误码
5. ~~**前置任务 1**：`FeignConfig` 加 token 透传 RequestInterceptor（lingxi-common）~~（✅ 已完成 2026-08-04）
6. `pom.xml` 加 tboxsdk + okhttp + okhttp-sse

### Day 6 下午：Agent 框架 + 出题
7. `LlmClient` 接口 + `TboxLlmClient`（tboxsdk）+ `MockTboxLlmClient` + `agent/config/TboxClientConfig`
8. `JobFeignClient` 追加 requirements；新增 `agent/feign/ResumeApiFeignClient`（简历列表/详情/能力模型，token 透传）
9. `agent/tools` `FetchJobRequirementsTool` / `FetchCandidateResumeTool` + `MockPromptBuilder`
10. `MockInterviewAgent` 出题流程 + `agent/controller` `generate` SSE 接口（含内置模板兜底）

### Day 7 上午：答题 + 评分
11. `answer` SSE 接口（评分 prompt + 权重 + UPDATE 覆盖）
12. `skip` 接口（is_skipped=1）

### Day 7 下午：报告 + 收尾
13. `report` SSE 接口（聚合 + LLM 报告 + session COMPLETED）
14. `questions` 查询接口（支持"继续面试"）
15. 自测：generate → answer → skip → answer → report 全链路（mock 模式）
16. 更新 `成员D开发排期.md` 进度

### 补充（2026-08-05）
17. ✅ **每日次数限制**：新增 `MockInterviewProperties`（`hr.mock-interview.quota`，默认 3，0=不限）+ `MockSessionMapper.countByCandidateIdAndStartAt`（按当天已建会话计数）+ `generate` 前置 `checkQuota`（超限抛 40018，不建会话）

---

## 八、依赖与待确认

| 项 | 说明 |
|------|------|
| 🟢 **Feign token 透传** | ✅ 已实现（2026-08-04，lingxi-common `FeignConfig.tokenRelayInterceptor`）；D 以求职者身份调 B/C 用户接口的通道已打通 |
| 🟡 **简历数据通道** | 已确认**方案 A**：token 透传后调 C 用户接口（新增 `ResumeApiFeignClient` path=`/api/v1`），简历输入 = 能力模型为主 + 简历详情为辅；若 C 的 `/internal/resumes/{id}` 先落地可切方案 B |
| 🟢 **百宝箱工作流** | 3 个工作流已在平台侧建立（2026-08-04）：出题 `202608APE9vh20999665` / 评分 `202608APlpZR21033809` / 报告 `202608AP7lse21015615`，见 `模拟面试-百宝箱工作流设计.md`；D 侧实现完成后联调验证，通过后置 `hr.agent.mock=false` |
| 🟡 **JD 原文** | MVP 岗位输入用 requirements（interviewFocus/coreSkills，已就绪）；如需 jdText 原文，靠 token 透传调 B C 端 `GET /api/v1/jobs/{jobId}`，D 侧预留 `JobDetailFeignClient` |
| 🟢 网关路由 | `/api/v1/mock-interview/**` 已就绪；Controller 加 `@RequireLogin`，`UserContext.getUserId()` 即 candidate_id |
| 🟢 C 端次数限制 | ✅ 已实现（2026-08-05）：配置项 `hr.mock-interview.quota`（`application.yml` 默认 3，0=不限），`generate` 按当天已建 `mock_session` 数计数，超限抛 40018；`MockSessionMapper.countByCandidateIdAndStartAt` |

---

## 九、接口统计

| 项 | 数量 |
|------|:---:|
| SSE 接口 | 3（generate / answer / report） |
| 普通接口 | 2（questions / skip） |
| 新增实体 / Mapper+XML | 3 / 3（`agent/entity` + `agent/mapper` + `resources/mapper/agent/`） |
| 新增类（`com.lingxi.hr.agent` 包） | entity×3 + mapper×3 + dto×5 + feign×4 + config×2 + LlmClient + TboxLlmClient + MockTboxLlmClient + MockInterviewAgent + tools×2 + MockPromptBuilder + Controller = **22 个** |
| 修改 Feign | JobFeignClient 追加 requirements 方法（HR 公共 Feign） |
| 公共能力 | FeignConfig token 透传（lingxi-common） |

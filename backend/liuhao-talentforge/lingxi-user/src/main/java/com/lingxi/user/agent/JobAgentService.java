package com.lingxi.user.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.user.agent.config.BaibaoxiangProperties;
import com.lingxi.user.agent.config.JobAgentProperties;
import com.lingxi.user.agent.impl.MockJobLlmClient;
import com.lingxi.user.agent.tools.*;
import com.lingxi.user.domain.entity.SysAgentConversation;
import com.lingxi.user.feign.ResumeFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Job Agent 核心服务（混合架构：后端控工具调用，LLM 负责格式化回答）
 * <p>
 * 架构设计：
 * ① 关键词匹配为快速路径（80%场景，确定性高，0延迟）
 * ② LLM 兜底覆盖模糊场景（20%场景，如"我想看看前端的机会"）
 * ③ 工具调用由后端控制（不依赖 LLM function calling，不会调错工具）
 * ④ LLM 只负责把工具结果格式化成人话
 * </p>
 * <p>
 * 意图识别流程：
 * 用户消息 → detectIntent() 关键词匹配 → 命中则直接调工具
 * → 未命中（返回"chat"）→ classifyAndExtract() LLM 分类+提取参数
 * → LLM 返回有效意图 → 用 LLM 参数调工具
 * → LLM 也认为是闲聊 → 不调工具，LLM 直接回答
 * </p>
 * <p>
 * 支持的意图：
 * - search: 搜索岗位（"找前端工作"、"有什么测试岗位"）
 * - detail: 查看岗位详情（"看看岗位1的详情"）
 * - recommend: 推荐岗位（"推荐适合我的岗位"）
 * - match: 匹配度分析（"我和这个岗位匹配吗"）
 * - company: 查看公司岗位（"灵犀有什么岗位"）
 * - chat: 闲聊（不调工具，LLM 直接回答）
 * </p>
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobAgentService {

    private final JobAgentLlmClient llmClient;
    private final BaibaoxiangProperties baibaoxiangProperties;
    private final JobAgentProperties jobAgentProperties;
    private final AgentConversationManager conversationManager;
    private final JobPromptBuilder promptBuilder;

    // 工具
    private final SearchJobsTool searchJobsTool;
    private final GetJobDetailTool getJobDetailTool;
    private final GetMyResumeTool getMyResumeTool;
    private final CalculateMatchTool calculateMatchTool;
    private final GetCompanyJobsTool getCompanyJobsTool;

    // 缓存 + 线程池
    private final AgentCacheService cacheService;
    private final ResumeFeignClient resumeFeignClient;
    private final ObjectMapper objectMapper;

    @Autowired
    @Qualifier("agentMatchExecutor")
    private Executor matchExecutor;

    // 预编译正则
    private static final Pattern JOB_EXTRACT_PATTERN = Pattern.compile("找(.{2,10}?)(?:工作|岗位|职位)");
    private static final Pattern JOB_ID_HISTORY_PATTERN = Pattern.compile("\"jobId\"\\s*[:：]\\s*\"?(\\d+)\"?");
    private static final Pattern JOB_ID_MSG_PATTERN = Pattern.compile("(?:岗位|职位)\\s*(\\d+)");

    /**
     * Agent 对话入口（SSE 流式输出）
     * <p>
     * 完整流程：
     * ① 加载对话历史（用于上下文补全）
     * ② 意图识别（关键词匹配 → LLM 兜底）
     * ③ 根据意图调用对应工具（搜索/详情/推荐/匹配/公司）
     * ④ 拼装 Prompt（工具结果 + 用户问题）发给 LLM
     * ⑤ LLM 流式生成回答，通过 listener 实时推送给前端
     * ⑥ 保存对话历史
     * </p>
     *
     * @param userId     用户ID
     * @param sessionId  会话ID（一个会话多轮对话）
     * @param message    用户消息
     * @param listener   SSE 事件监听器（progress/chunk/result/done/error）
     */
    public void chat(Long userId, String sessionId, String message,
                     AgentEventListener listener) {
        try {
            // ① 加载对话历史（最近10条，用于上下文补全参数）
            List<SysAgentConversation> history = conversationManager.getHistory(userId, sessionId);
            String historyText = formatHistory(history);

            // ② 意图识别 + 工具调用（后端控制，不依赖 LLM function calling）
            notify(listener, "progress", progress("intent", "正在理解您的需求..."));
            String toolName = null;
            String toolResult = null;

            // 第一层：关键词快速匹配（确定性高，0延迟）
            String intent = detectIntent(message, history);
            log.info("Agent 意图识别: userId={}, intent={}, message={}", userId, intent, message);

            // 第二层：关键词未命中 → LLM 兜底分类+参数提取（5s超时）
            Map<String, Object> llmParams = null;
            if ("chat".equals(intent)) {
                try {
                    LlmClassification lc = classifyAndExtract(message);
                    if (lc != null && !"chat".equals(lc.intent)) {
                        log.info("LLM 兜底意图识别: userId={}, llmIntent={}, message={}", userId, lc.intent, message);
                        intent = lc.intent;
                        llmParams = lc.toSearchParams();
                    }
                } catch (Exception e) {
                    log.warn("LLM 意图分类失败，降级为闲聊: {}", e.getMessage());
                }
            }

            // 根据意图调用对应工具
            switch (intent) {
                case "search":
                    // 搜索岗位：提取 keywords/jobType/company 参数 → 调 Feign 搜索
                    notify(listener, "progress", progress("search", "正在搜索岗位..."));
                    toolName = "search_jobs";
                    if (llmParams != null) {
                        // LLM 兜底已提取参数，直接用
                        toolResult = searchJobsTool.execute(llmParams);
                    } else {
                        Map<String, Object> searchParams = extractSearchParams(message, history);
                        // 第三层：关键词和公司名都没提取到 → LLM 补参数
                        List<String> kw = (List<String>) searchParams.get("keywords");
                        String co = (String) searchParams.get("company");
                        if ((kw == null || kw.isEmpty()) && (co == null || co.isEmpty())) {
                            try {
                                LlmClassification lc = classifyAndExtract(message);
                                if (lc != null && !"chat".equals(lc.intent)) {
                                    log.info("LLM 补充搜索参数: keywords={}, company={}", lc.keywords, lc.company);
                                    searchParams = lc.toSearchParams();
                                }
                            } catch (Exception e) {
                                log.warn("LLM 参数补充失败: {}", e.getMessage());
                            }
                        }
                        toolResult = searchJobsTool.execute(searchParams);
                    }
                    break;

                case "detail":
                    notify(listener, "progress", progress("detail", "正在获取岗位详情..."));
                    toolName = "get_job_detail";
                    Long jobId = extractJobId(message, history);
                    if (jobId != null) {
                        Map<String, Object> detailParams = new HashMap<>();
                        detailParams.put("jobId", jobId);
                        toolResult = getJobDetailTool.execute(detailParams);
                    }
                    break;

                case "recommend":
                    notify(listener, "progress", progress("recommend", "正在获取您的简历..."));
                    notify(listener, "progress", progress("search", "正在搜索匹配岗位..."));
                    toolName = "recommend";
                    toolResult = executeRecommendWithMatch(userId, message, history, listener);
                    break;

                case "match":
                    notify(listener, "progress", progress("match", "正在计算匹配度..."));
                    toolName = "calculate_match";
                    Long matchJobId = extractJobId(message, history);
                    if (matchJobId != null) {
                        toolResult = calculateMatchTool.execute(matchJobId, userId);
                    } else {
                        toolResult = "{\"error\": \"请先搜索岗位，再查询匹配度\"}";
                    }
                    break;

                case "company":
                    notify(listener, "progress", progress("company", "正在查询企业岗位..."));
                    toolName = "get_company_jobs";
                    Map<String, Object> companyParams = extractCompanyParams(message);
                    // 公司名没提取到 → LLM 兜底
                    if ("".equals(companyParams.get("company"))) {
                        try {
                            LlmClassification lc = classifyAndExtract(message);
                            if (lc != null && lc.company != null && !lc.company.isEmpty()) {
                                log.info("LLM 补充公司名: company={}", lc.company);
                                companyParams.put("company", lc.company);
                            }
                        } catch (Exception e) {
                            log.warn("LLM 公司名提取失败: {}", e.getMessage());
                        }
                    }
                    toolResult = getCompanyJobsTool.execute(companyParams);
                    break;

                default:
                    // 闲聊，不调工具
                    break;
            }

            // ③ 拼装 Prompt（只发工具结果 + 用户问题，不加工具定义）
            String query = promptBuilder.buildChatQuery(message, toolName, toolResult, historyText);

            // ④ 调 LLM 生成回答（流式输出）
            notify(listener, "progress", progress("generating", "正在生成回答..."));
            String appId = baibaoxiangProperties.getAgent().getAppId();
            long timeout = jobAgentProperties.getLlm().getTimeoutMs();
            String answer;
            try {
                StringBuilder fullAnswer = new StringBuilder();
                answer = llmClient.generateStream(appId, query, userId.toString(), timeout,
                        chunk -> {
                            fullAnswer.append(chunk);
                            notify(listener, "chunk", singletonMap("content", chunk));
                        });
                if (answer == null || answer.isEmpty()) {
                    answer = fullAnswer.toString();
                }
            } catch (Exception llmEx) {
                log.warn("LLM 调用失败，降级为 mock: {}", llmEx.getMessage());
                notify(listener, "progress", progress("fallback", "AI服务暂时不可用，使用智能分析..."));
                MockJobLlmClient mockClient = new MockJobLlmClient();
                answer = mockClient.generate(appId, query, userId.toString(), timeout);
                notify(listener, "chunk", singletonMap("content", answer));
            }

            // ⑤ 保存对话历史
            conversationManager.saveMessage(userId, sessionId, "user", message);
            conversationManager.saveMessage(userId, sessionId, "assistant", answer);

            // ⑥ 返回结果
            notify(listener, "result", singletonMap("content", answer));
            notify(listener, "done", new HashMap<>());

        } catch (Exception e) {
            log.error("Agent 对话异常: userId={}", userId, e);
            notify(listener, "error", singletonMap("message", "抱歉，处理出错了，请稍后再试"));
        }
    }

    // ==================== 意图识别（第一层：关键词快速匹配）====================

    /**
     * 意图识别（带上下文补全）
     * <p>
     * 优先级从高到低：
     * 1. 明确意图词（"推荐"/"匹配"/"详情"/"公司"）→ 直接返回
     * 2. 搜索词（"找"/"搜"/"岗位"/"前端"/"java"等）→ 返回 search
     * 3. 单独城市名（"广州"）→ 结合上文判断是否为搜索
     * 4. 肯定回复（"是的"/"好的"）→ 结合上文判断
     * 5. 都没命中 → 返回 "chat"（交给 LLM 兜底分类）
     * </p>
     *
     * @param message 用户消息
     * @param history 对话历史（用于上下文判断）
     * @return 意图：search/detail/recommend/match/company/chat
     */
    private String detectIntent(String message, List<SysAgentConversation> history) {
        String lower = message.toLowerCase();

        // 1. 明确的意图关键词
        if (containsAny(lower, "推荐", "适合我", "根据简历", "我适合")) return "recommend";
        if (containsAny(lower, "匹配", "够格", "匹配度", "匹配分析")) return "match";
        if (containsAny(lower, "详情", "具体要求", "jd", "详细介绍")) return "detail";
        if (containsAny(lower, "公司", "企业", "字节", "腾讯", "阿里", "美团")) return "company";

        // 2. 搜索：岗位相关词 + 技能/岗位名称
        if (containsAny(lower, "找", "搜", "岗位", "工作", "招聘", "职位",
                "开发", "工程师", "后端", "前端", "测试", "运维", "设计", "产品",
                "java", "python", "go", "c++", "react", "vue", "android", "ios")) return "search";

        // 3. 单独城市名 → 结合上下文判断是否为搜索
        String city = extractCity(message);
        if (!city.isEmpty() && message.trim().length() <= city.length() + 2) {
            // 用户只发了城市名（如"广州"），检查上文是否有搜索意图
            if (hasSearchContext(history)) return "search";
        }

        // 4. 肯定回复 → 结合上文判断
        if (containsAny(lower, "是的", "对", "好的", "嗯", "是", "行", "可以", "搜吧", "帮我搜")) {
            if (hasSearchContext(history)) return "search";
            if (hasRecommendContext(history)) return "recommend";
        }

        return "chat";
    }

    /**
     * 检查历史中是否有搜索相关的上下文
     */
    private boolean hasSearchContext(List<SysAgentConversation> history) {
        if (history == null || history.isEmpty()) return false;
        // 检查最近3条assistant消息
        int count = 0;
        for (int i = history.size() - 1; i >= 0 && count < 3; i--) {
            SysAgentConversation msg = history.get(i);
            if ("assistant".equals(msg.getRole())) {
                count++;
                String content = msg.getContent();
                if (content != null && (content.contains("搜索") || content.contains("岗位")
                        || content.contains("后端") || content.contains("开发")
                        || content.contains("技术栈") || content.contains("关键词"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检查历史中是否有推荐相关的上下文
     */
    private boolean hasRecommendContext(List<SysAgentConversation> history) {
        if (history == null || history.isEmpty()) return false;
        int count = 0;
        for (int i = history.size() - 1; i >= 0 && count < 3; i--) {
            SysAgentConversation msg = history.get(i);
            if ("assistant".equals(msg.getRole())) {
                count++;
                String content = msg.getContent();
                if (content != null && (content.contains("推荐") || content.contains("简历")
                        || content.contains("匹配度"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    // ==================== 参数提取（从用户消息中解析结构化参数）====================

    /** 常见公司名列表（用于从用户消息中提取公司名，支持模糊匹配） */
    private static final String[] COMPANY_NAMES = {
            "灵犀科技", "灵犀",
            "字节跳动", "腾讯", "阿里巴巴", "美团", "百度", "京东", "华为", "小米",
            "网易", "拼多多", "快手", "滴滴", "蚂蚁集团", "微软", "谷歌", "Apple",
            "亚马逊", "阿里", "头条", "抖音", "微信", "支付宝"
    };

    /**
     * 提取搜索参数（带上下文补全）
     * <p>
     * 参数提取优先级：
     * 1. 当前消息提取 keywords/city/company
     * 2. 当前消息提取不到 → 从对话历史补全
     * 3. 都提取不到 → 返回空参数，由调用方决定是否 LLM 兜底
     * </p>
     * <p>
     * jobType 策略：有扩展 keywords 时不传 jobType（避免枚举值不匹配漏结果）
     * </p>
     *
     * @param message 用户消息
     * @param history 对话历史
     * @return 搜索参数 Map（keyword/keywords/city/jobType/company/page/size）
     */
    private Map<String, Object> extractSearchParams(String message, List<SysAgentConversation> history) {
        Map<String, Object> params = new LinkedHashMap<>();
        String city = extractCity(message);

        // 提取公司名
        String company = extractCompany(message);

        // 扩展搜索关键词（"前端" → ["前端","frontend","react","vue",...]，用于 title LIKE OR 搜索）
        List<String> keywords = calculateMatchTool.extractSearchKeywords(message);
        if (keywords.isEmpty() && history != null) {
            for (int i = history.size() - 1; i >= 0; i--) {
                SysAgentConversation msg = history.get(i);
                if ("user".equals(msg.getRole()) && msg.getContent() != null) {
                    keywords = calculateMatchTool.extractSearchKeywords(msg.getContent());
                    if (!keywords.isEmpty()) break;
                }
            }
        }

        // jobType 精确过滤：仅在没有扩展关键词时使用（避免枚举值不匹配导致漏结果）
        // 有扩展关键词时，title LIKE OR 已足够过滤，不需要 jobType 再卡一道
        String jobType = keywords.isEmpty() ? calculateMatchTool.extractJobTypeFromMessage(message) : "";

        // keywords 非空时 SQL 走 keywords OR 分支，keyword 不需要；为空时保留空值
        params.put("keyword", null);
        params.put("keywords", keywords);
        params.put("city", city);
        params.put("jobType", jobType);
        params.put("company", company);
        params.put("page", 1);
        params.put("size", 10);
        return params;
    }

    /**
     * 从用户消息中提取公司名
     */
    private String extractCompany(String message) {
        for (String name : COMPANY_NAMES) {
            if (message.contains(name)) {
                return name;
            }
        }
        return "";
    }

    /**
     * 从历史消息中提取关键词
     */
    private String extractKeywordFromHistory(List<SysAgentConversation> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            SysAgentConversation msg = history.get(i);
            if ("user".equals(msg.getRole()) && msg.getContent() != null) {
                String kw = extractKeyword(msg.getContent());
                if (!kw.isEmpty()) return kw;
            }
        }
        return "";
    }

    private String extractKeyword(String message) {
        String lower = message.toLowerCase();

        // 技能关键词映射（同义词 → 标准词）
        String[][] skillKeywords = {
                // 编程语言
                {"java", "Java"}, {"python", "Python"}, {"go", "Go"}, {"golang", "Go"},
                {"c++", "C++"}, {"c语言", "C++"}, {"javascript", "前端"}, {"typescript", "前端"},
                {"php", "PHP"}, {"ruby", "Ruby"}, {"rust", "Rust"}, {"scala", "Scala"},
                // 前端
                {"前端", "前端"}, {"frontend", "前端"}, {"react", "前端"}, {"vue", "前端"},
                {"angular", "前端"}, {"web前端", "前端"}, {"h5", "前端"}, {"小程序", "前端"},
                // 后端
                {"后端", "后端"}, {"backend", "后端"}, {"服务端", "后端"}, {"server", "后端"},
                {"后端开发", "后端"}, {"服务端开发", "后端"}, {"java后端", "后端"},
                // 移动端
                {"android", "Android"}, {"安卓", "Android"}, {"ios", "iOS"},
                {"移动端", "移动端"}, {"手机开发", "移动端"}, {"app开发", "移动端"},
                // 数据
                {"数据分析", "数据分析"}, {"数据开发", "数据开发"}, {"大数据", "大数据"},
                {"数据仓库", "数据仓库"}, {"etl", "数据开发"}, {"bi", "数据分析"},
                // AI/算法
                {"算法", "算法"}, {"机器学习", "算法"}, {"深度学习", "算法"},
                {"人工智能", "算法"}, {"ai", "算法"}, {"nlp", "算法"}, {"cv", "算法"},
                // 测试
                {"测试", "测试"}, {"qa", "测试"}, {"质量", "测试"}, {"自动化测试", "测试"},
                // 运维/DevOps
                {"运维", "运维"}, {"devops", "运维"}, {"sre", "运维"}, {"运维开发", "运维"},
                // 产品/设计
                {"产品经理", "产品经理"}, {"产品", "产品经理"}, {"pm", "产品经理"},
                {"ui", "UI"}, {"ue", "UI"}, {"交互", "UI"}, {"设计", "设计"},
                {"视觉", "设计"}, {"平面", "设计"}, {"ui设计", "UI"},
                // 其他
                {"全栈", "全栈"}, {"架构", "架构"}, {"项目经理", "项目经理"},
                {"安全", "安全"}, {"网�ite", "安全"}, {"区块链", "区块链"},
        };

        for (String[] pair : skillKeywords) {
            if (lower.contains(pair[0])) return pair[1];
        }

        // 尝试从"找XX工作"模式提取
        Matcher m = JOB_EXTRACT_PATTERN.matcher(message);
        if (m.find()) return m.group(1);

        return "";
    }

    private String extractCity(String message) {
        // 城市列表（含别名）
        String[][] cityAliases = {
                {"北京", "北京"}, {"京城", "北京"}, {"帝都", "北京"},
                {"上海", "上海"}, {"魔都", "上海"}, {"沪", "上海"},
                {"广州", "广州"}, {"羊城", "广州"}, {"穗", "广州"},
                {"深圳", "深圳"}, {"鹏城", "深圳"},
                {"杭州", "杭州"}, {"杭", "杭州"},
                {"成都", "成都"}, {"蓉城", "成都"},
                {"武汉", "武汉"}, {"江城", "武汉"},
                {"南京", "南京"}, {"金陵", "南京"}, {"宁", "南京"},
                {"西安", "西安"}, {"长安", "西安"},
                {"长沙", "长沙"}, {"星城", "长沙"},
                {"重庆", "重庆"}, {"山城", "重庆"}, {"渝", "重庆"},
                {"天津", "天津"}, {"津", "天津"},
                {"苏州", "苏州"}, {"无锡", "无锡"},
                {"厦门", "厦门"}, {"福州", "福州"},
                {"青岛", "青岛"}, {"大连", "大连"},
                {"郑州", "郑州"}, {"合肥", "合肥"},
                {"珠海", "珠海"}, {"佛山", "佛山"}, {"东莞", "东莞"},
        };

        for (String[] pair : cityAliases) {
            if (message.contains(pair[0])) return pair[1];
        }
        return "";
    }

    private Long extractJobId(String message, List<SysAgentConversation> history) {
        // 从对话历史中提取 jobId
        for (SysAgentConversation msg : history) {
            if (msg.getContent() != null && msg.getContent().contains("jobId")) {
                Matcher m = JOB_ID_HISTORY_PATTERN.matcher(msg.getContent());
                if (m.find()) return Long.parseLong(m.group(1));
            }
        }
        // 从消息中提取数字
        Matcher m = JOB_ID_MSG_PATTERN.matcher(message);
        if (m.find()) return Long.parseLong(m.group(1));
        // 默认返回第一个岗位
        return 1L;
    }

    private Map<String, Object> extractCompanyParams(String message) {
        Map<String, Object> params = new LinkedHashMap<>();
        String company = extractCompany(message);
        params.put("company", company);
        params.put("page", 1);
        params.put("size", 10);
        return params;
    }

    // ==================== 推荐+匹配度计算 ====================

    /**
     * 执行推荐流程（搜索 + 并行匹配度计算 + 排序）
     * <p>
     * 流程：
     * ① 获取用户简历（带缓存）
     * ② 搜索岗位（keywords/jobType/company 过滤，最多100条）
     * ③ 并行计算每个岗位的匹配度（6维度：技能/经验/学历/岗位/薪资/城市）
     * ④ 按匹配度排序，取 top 10
     * ⑤ 组装结果发给 LLM 格式化输出
     * </p>
     *
     * @param userId   用户ID（用于获取简历和计算匹配度）
     * @param message  用户消息（用于提取搜索关键词）
     * @param history  对话历史
     * @param listener SSE 事件监听器
     * @return 格式化的推荐结果文本
     */
    private String executeRecommendWithMatch(Long userId, String message,
                                              List<SysAgentConversation> history,
                                              AgentEventListener listener) {
        try {
            // ① 获取简历（带缓存）
            Map<String, Object> resumeData = cacheService.getUserResume(userId,
                    () -> {
                        try {
                            com.lingxi.common.domain.Result<Map<String, Object>> result =
                                    resumeFeignClient.getUserResume(userId);
                            return result.getData();
                        } catch (Exception e) {
                            log.warn("Feign获取简历失败: userId={}", userId, e);
                            return Collections.emptyMap();
                        }
                    });
            String resumeInfo = objectMapper.writeValueAsString(resumeData);

            // ② 搜索岗位（根据用户消息提取岗位方向过滤 + 公司名过滤）
            List<String> keywords = calculateMatchTool.extractSearchKeywords(message);
            String company = extractCompany(message);

            // 关键词和公司名都没提取到 → LLM 兜底补参数
            if (keywords.isEmpty() && company.isEmpty()) {
                try {
                    LlmClassification lc = classifyAndExtract(message);
                    if (lc != null) {
                        if (lc.keywords != null && !lc.keywords.isEmpty()) keywords = lc.keywords;
                        if (lc.company != null && !lc.company.isEmpty()) company = lc.company;
                        log.info("LLM 补充推荐参数: keywords={}, company={}", keywords, company);
                    }
                } catch (Exception e) {
                    log.warn("LLM 参数补充失败: {}", e.getMessage());
                }
            }

            Map<String, Object> searchParams = new LinkedHashMap<>();
            searchParams.put("keyword", null);
            searchParams.put("keywords", keywords);
            searchParams.put("city", extractCity(message));
            searchParams.put("jobType", keywords.isEmpty() ? calculateMatchTool.extractJobTypeFromMessage(message) : "");
            searchParams.put("company", company);
            searchParams.put("page", 1);
            searchParams.put("size", 100);
            String jobListJson = searchJobsTool.execute(searchParams);

            // ③ 并行计算匹配度
            List<Map<String, Object>> rankedJobs = calculateMatchForJobs(userId, jobListJson, listener);

            // ④ 只取 top 10 岗位
            List<Map<String, Object>> top10Jobs = rankedJobs.stream()
                    .limit(10)
                    .collect(Collectors.toList());
            log.info("推荐岗位: 总共{}个岗位，取top10: {}", rankedJobs.size(), top10Jobs.size());

            // ⑤ 组装结果（带指令）
            StringBuilder sb = new StringBuilder();
            sb.append("简历信息：\n").append(resumeInfo).append("\n\n");
            sb.append("搜索结果（已按匹配度排序，共").append(rankedJobs.size()).append("个岗位，取top10）：\n");
            sb.append(objectMapper.writeValueAsString(top10Jobs));
            sb.append("\n\n【指令】请根据以上数据，为用户推荐匹配度最高的10个岗位。\n");
            sb.append("每个岗位显示：职位名称、公司、薪资、城市、匹配度（必须使用数据中的matchScore字段值，格式为百分比）。\n");
            sb.append("按matchScore从高到低排序，使用🥇🥈🥉等图标。\n");
            sb.append("【重要】匹配度必须使用数据中已计算好的matchScore字段，不要自己重新计算！");

            return sb.toString();
        } catch (Exception e) {
            log.error("推荐计算失败", e);
            String resumeInfo = getMyResumeTool.execute(userId);
            String jobList = searchJobsTool.execute(extractSearchParams(message, history));
            return "简历信息：\n" + resumeInfo + "\n\n搜索结果：\n" + jobList;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> calculateMatchForJobs(Long userId, String jobListJson,
                                                             AgentEventListener listener) {
        List<Map<String, Object>> rankedJobs = new ArrayList<>();
        try {
            log.info("开始计算匹配度: userId={}, jobListJson长度={}", userId, jobListJson != null ? jobListJson.length() : 0);
            JsonNode root = objectMapper.readTree(jobListJson);
            JsonNode listNode = root.get("list");
            if (listNode == null || !listNode.isArray()) {
                log.warn("jobListJson中没有list字段或不是数组: {}", jobListJson.substring(0, Math.min(200, jobListJson.length())));
                return rankedJobs;
            }

            // 提取所有岗位
            List<Map<String, Object>> jobs = new ArrayList<>();
            for (JsonNode jobNode : listNode) {
                Map<String, Object> job = objectMapper.convertValue(jobNode, Map.class);
                if (job.get("jobId") != null) {
                    jobs.add(job);
                }
            }
            log.info("提取到{}个岗位，开始并行计算匹配度", jobs.size());

            notify(listener, "progress", progress("match",
                    "正在并行计算" + jobs.size() + "个岗位匹配度..."));

            // 并行计算匹配度
            List<CompletableFuture<Void>> futures = jobs.stream()
                    .map(job -> CompletableFuture.runAsync(() -> {
                        try {
                            Long jobId = Long.valueOf(job.get("jobId").toString());
                            log.info("计算岗位匹配度: jobId={}", jobId);
                            String matchResult = calculateMatchTool.execute(jobId, userId);
                            int matchScore = 0;
                            try {
                                JsonNode matchNode = objectMapper.readTree(matchResult);
                                if (matchNode.has("matchScore")) {
                                    matchScore = matchNode.get("matchScore").asInt();
                                    log.info("岗位{}匹配度计算结果: {}", jobId, matchScore);
                                } else {
                                    log.warn("matchResult中没有matchScore字段: {}", matchResult);
                                }
                            } catch (Exception e) {
                                log.warn("解析matchResult失败: {}", matchResult, e);
                            }
                            job.put("matchScore", matchScore);
                        } catch (Exception e) {
                            log.warn("并行匹配计算异常: jobId={}", job.get("jobId"), e);
                            job.put("matchScore", 0);
                        }
                    }, matchExecutor))
                    .collect(Collectors.toList());

            // 等待全部完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 按匹配度排序
            rankedJobs.addAll(jobs);
            rankedJobs.sort((a, b) -> Integer.compare(
                    (int) b.getOrDefault("matchScore", 0),
                    (int) a.getOrDefault("matchScore", 0)));
            log.info("匹配度计算完成，共{}个岗位", rankedJobs.size());

        } catch (Exception e) {
            log.error("匹配度计算异常", e);
        }
        return rankedJobs;
    }

    // ==================== 工具方法 ====================

    /** 最大历史消息数（5轮对话 = 10条消息） */
    private static final int MAX_HISTORY_MESSAGES = 10;
    /** 单条消息最大长度 */
    private static final int MAX_MESSAGE_LENGTH = 300;

    /**
     * 格式化对话历史（压缩优化）
     */
    private String formatHistory(List<SysAgentConversation> history) {
        if (history == null || history.isEmpty()) return "";

        // 过滤系统消息
        List<SysAgentConversation> userMessages = new ArrayList<>();
        for (SysAgentConversation msg : history) {
            if (!"system".equals(msg.getRole())) {
                userMessages.add(msg);
            }
        }

        // 只保留最近N条消息
        if (userMessages.size() > MAX_HISTORY_MESSAGES) {
            userMessages = userMessages.subList(
                    userMessages.size() - MAX_HISTORY_MESSAGES, userMessages.size());
        }

        StringBuilder sb = new StringBuilder();
        for (SysAgentConversation msg : userMessages) {
            String content = msg.getContent();
            if (content == null) continue;

            // 截断过长的消息
            if (content.length() > MAX_MESSAGE_LENGTH) {
                content = content.substring(0, MAX_MESSAGE_LENGTH) + "...";
            }

            // 简化角色标识
            String role = "user".equals(msg.getRole()) ? "用户" : "助手";
            sb.append(role).append(": ").append(content).append("\n");
        }

        return sb.toString();
    }

    private Map<String, Object> progress(String step, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("step", step);
        map.put("message", message);
        return map;
    }

    private void notify(AgentEventListener listener, String event, Object data) {
        if (listener != null) {
            try {
                listener.onEvent(event, data);
            } catch (Exception e) {
                log.debug("Agent 事件推送失败: event={}", event);
            }
        }
    }

    private Map<String, Object> singletonMap(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    // ==================== LLM 兜底意图分类（第二层：模糊场景覆盖）====================

    /** LLM 分类超时（ms），比主回答（60s）短很多，快速失败 */
    private static final long CLASSIFY_TIMEOUT_MS = 5000;

    /**
     * LLM 兜底：意图分类 + 参数提取（关键词匹配失败时调用）
     * <p>
     * 一次 LLM 调用同时完成两件事：
     * 1. 意图分类（search/detail/recommend/match/company/chat）
     * 2. 参数提取（keywords/city/company/jobType）
     * </p>
     * <p>
     * 调用时机：
     * - detectIntent 返回 "chat" 时（关键词未命中任何意图）
     * - search/recommend 意图但参数提取为空时（补充 keywords/company）
     * </p>
     * <p>
     * 降级策略：LLM 调用失败/超时 → 返回 null → 降级为闲聊
     * </p>
     *
     * @param message 用户消息
     * @return 分类结果（intent + 参数），失败返回 null
     */
    private LlmClassification classifyAndExtract(String message) {
        String appId = baibaoxiangProperties.getAgent().getAppId();
        String classifyPrompt = promptBuilder.buildClassifyPrompt(message);

        String llmResult;
        try {
            llmResult = llmClient.generate(appId, classifyPrompt, "classify", CLASSIFY_TIMEOUT_MS);
        } catch (Exception e) {
            log.warn("LLM 意图分类调用失败: {}", e.getMessage());
            return null;
        }

        if (llmResult == null || llmResult.isEmpty()) return null;

        // 从 LLM 返回中提取 JSON
        return parseLlmClassification(llmResult, message);
    }

    /**
     * 解析 LLM 返回的分类 JSON
     */
    private LlmClassification parseLlmClassification(String llmResult, String originalMessage) {
        try {
            // 提取 JSON 部分（LLM 可能返回额外文本）
            int jsonStart = llmResult.indexOf("{");
            int jsonEnd = llmResult.lastIndexOf("}");
            if (jsonStart < 0 || jsonEnd < 0 || jsonEnd <= jsonStart) {
                log.warn("LLM 分类结果无有效JSON: {}", llmResult);
                return null;
            }
            String json = llmResult.substring(jsonStart, jsonEnd + 1);
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);

            LlmClassification lc = new LlmClassification();
            lc.intent = node.has("intent") ? node.get("intent").asText("chat") : "chat";

            // keywords
            if (node.has("keywords") && node.get("keywords").isArray()) {
                lc.keywords = new ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode kw : node.get("keywords")) {
                    lc.keywords.add(kw.asText());
                }
            }
            if (lc.keywords == null || lc.keywords.isEmpty()) {
                // LLM 没返回 keywords，用 SEARCH_KEYWORD_EXPANSION 兜底
                lc.keywords = calculateMatchTool.extractSearchKeywords(originalMessage);
            }

            lc.city = node.has("city") ? node.get("city").asText("") : "";
            lc.company = node.has("company") ? node.get("company").asText("") : "";
            lc.jobType = node.has("jobType") ? node.get("jobType").asText("") : "";

            log.info("LLM 分类结果: intent={}, keywords={}, city={}, company={}, jobType={}",
                    lc.intent, lc.keywords, lc.city, lc.company, lc.jobType);
            return lc;
        } catch (Exception e) {
            log.warn("解析 LLM 分类结果失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * LLM 分类结果内部类
     * <p>
     * 封装 LLM 返回的意图分类和提取的搜索参数，
     * 通过 toSearchParams() 转换为 SearchJobsTool 需要的参数 Map。
     * </p>
     */
    private static class LlmClassification {
        /** 意图类型：search/detail/recommend/match/company/chat */
        String intent;
        /** 搜索关键词列表（如["前端","frontend","react"]） */
        List<String> keywords;
        /** 城市名（如"北京"） */
        String city;
        /** 公司名（如"灵犀科技"） */
        String company;
        /** 岗位类型枚举（如"FRONTEND"、"JAVA_BACKEND"） */
        String jobType;

        /**
         * 转换为搜索参数 Map（供 SearchJobsTool.execute() 使用）
         * <p>
         * 有扩展 keywords 时不传 jobType，避免枚举值不匹配漏结果
         * </p>
         */
        Map<String, Object> toSearchParams() {
            Map<String, Object> params = new LinkedHashMap<>();
            List<String> kw = keywords != null ? keywords : Collections.emptyList();
            params.put("keyword", null);
            params.put("keywords", kw);
            params.put("city", city != null ? city : "");
            // 有扩展关键词时不传 jobType，避免枚举值不匹配漏结果
            params.put("jobType", kw.isEmpty() ? (jobType != null ? jobType : "") : "");
            params.put("company", company != null ? company : "");
            params.put("page", 1);
            params.put("size", 10);
            return params;
        }
    }

    @FunctionalInterface
    public interface AgentEventListener {
        void onEvent(String eventName, Object data);
    }
}

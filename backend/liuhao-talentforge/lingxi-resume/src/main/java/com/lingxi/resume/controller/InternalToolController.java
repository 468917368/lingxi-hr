package com.lingxi.resume.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.domain.Result;
import com.lingxi.common.util.JsonUtil;
import com.lingxi.resume.agent.ToolContext;
import com.lingxi.resume.agent.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部工具端点（百宝箱智能体工作流 HTTP 节点回调）
 *
 * <p>百宝箱工作流中的 HTTP 节点调用这些端点执行工具。
 * 端点路径为 {@code /internal/**}，Gateway AuthFilter 白名单放行（不校验 Token）。
 *
 * <p>接口契约：
 * <pre>
 * POST /internal/tools/parse_resume
 * Request:  {"tikaText": "简历全文", "sections": [{"title":"教育经历","lines":["2016-2020..."]}]}
 * Response: {"code":0, "data": {"card_structure":{...}, "ability_model":{...}, "resume_md":"..."}}
 * </pre>
 *
 * <p>{@code sections} 为空数组时工具自动走规则引擎分段兜底。
 *
 * @author 成员C
 * @since 2026-08-03
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalToolController {

    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    /**
     * parse_resume 工具 — 按 Agent 识别的章节边界组装 card_structure + 评分 + resume.md
     */
    @PostMapping("/tools/parse_resume")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> parseResume(@RequestBody Map<String, Object> request) {
        log.info("收到百宝箱工具回调: /internal/tools/parse_resume");
        String tikaText = (String) request.getOrDefault("tikaText", "");
        ToolContext context = ToolContext.builder()
                .resumeId(null)
                .tikaText(tikaText)
                .build();
        String resultJson = toolExecutor.execute(
                ToolExecutor.TOOL_PARSE_RESUME,
                JsonUtil.toJson(request),
                context);
        try {
            return Result.success(objectMapper.readValue(resultJson, Map.class));
        } catch (Exception e) {
            log.error("工具结果解析失败", e);
            return Result.error(3502, "工具执行结果格式异常");
        }
    }
}

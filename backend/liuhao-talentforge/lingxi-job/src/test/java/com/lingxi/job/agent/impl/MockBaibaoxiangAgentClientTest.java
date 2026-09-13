package com.lingxi.job.agent.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.job.agent.AgentStreamEvent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mock 百宝箱客户端「部分命中补题」输出测试（阶段6.3 二期缺陷修复）
 * <p>直接调用 Mock 客户端 streamInterview（纯单测，不依赖 Spring/生产配置），收集 CONTENT
 * 拼 result JSON，守护补题分支：不得返回提示词模板/占位文本；未知题型 fail-fast 抛异常
 * （不被 resultJson 的 catch(Exception) 吞掉）。</p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
class MockBaibaoxiangAgentClientTest {

    private final ObjectMapper om = new ObjectMapper();
    private final MockBaibaoxiangAgentClient client = new MockBaibaoxiangAgentClient(om);

    @Test
    void partialHit_noTemplateEcho() throws Exception {
        String prompt = "【本次出题控制】\nquestionCount：2\nrequiredTypes：BOUNDARY,COMPREHENSIVE\ndifficulty：HARD\n";
        StringBuilder content = new StringBuilder();
        client.streamInterview(prompt, "u1", new AtomicBoolean(false), ev -> {
            if (ev.getType() == AgentStreamEvent.Type.CONTENT) {
                content.append(ev.getContent());
            }
        });
        JsonNode result = om.readTree(content.toString());
        assertEquals(2, result.path("questions").size());
        for (JsonNode q : result.path("questions")) {
            String c = q.path("content").asText("");
            assertFalse(c.contains("请围绕"), "补题 content 不得含提示词模板: " + c);
            assertFalse(c.contains("回答一道高质量面试题"), "补题 content 不得为模板回显: " + c);
            assertFalse("考察要点".equals(q.path("keyPoints").asText("")), "keyPoints 不得为占位");
            assertFalse("参考答案".equals(q.path("referenceAnswer").asText("")), "referenceAnswer 不得为占位");
        }
        // 补题题型 = 缺失题型集合
        Set<String> types = new HashSet<>();
        for (JsonNode q : result.path("questions")) {
            types.add(q.path("type").asText());
        }
        assertEquals(new HashSet<>(Arrays.asList("BOUNDARY", "COMPREHENSIVE")), types);
    }

    @Test
    void unknownType_failsFast() {
        // 4 固定题型之外：mockQuestionForType 抛异常，且不被 resultJson 的 catch(Exception) 吞掉
        String prompt = "【本次出题控制】\nquestionCount：1\nrequiredTypes：XXX_UNKNOWN\n";
        assertThrows(IllegalArgumentException.class,
                () -> client.streamInterview(prompt, "u1", new AtomicBoolean(false), ev -> { }));
    }
}

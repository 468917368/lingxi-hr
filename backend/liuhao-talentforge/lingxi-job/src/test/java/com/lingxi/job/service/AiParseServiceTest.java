package com.lingxi.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.context.UserDTO;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.job.agent.AgentStreamEvent;
import com.lingxi.job.agent.BaibaoxiangAgentClient;
import com.lingxi.job.agent.JdPromptSanitizer;
import com.lingxi.job.agent.impl.MockBaibaoxiangUserIdProvider;
import com.lingxi.job.domain.dto.request.JdParseRequest;
import com.lingxi.job.domain.dto.response.JdParseResponse;
import com.lingxi.job.service.impl.AiParseServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JD 解析服务单测
 * <p>手动构造 Mock 百宝箱客户端，覆盖成功/超时2301/JSON重试/仍失败500/禁敏感信息400，
 * 以及请求线程捕获用户 ID 生成伪标识传给客户端。</p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
class AiParseServiceTest {

    private static final String VALID_JSON =
            "{\"jobType\":\"JAVA_BACKEND\","
                    + "\"coreSkills\":[{\"name\":\"Java\",\"level\":\"3\",\"required\":true}],"
                    + "\"softSkills\":[{\"name\":\"沟通\",\"importance\":\"3\"}],"
                    + "\"minExperienceYears\":3,\"educationRequirement\":\"BACHELOR\","
                    + "\"salary\":{\"currency\":\"CNY\",\"period\":\"MONTH\",\"minAmount\":100000,\"maxAmount\":200000},"
                    + "\"jdSummary\":\"负责后端开发\",\"warnings\":[]}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUser() {
        // 模拟网关注入的 HR 用户上下文（请求线程）
        UserDTO user = new UserDTO();
        user.setUserId(1001L);
        user.setRole("HR");
        user.setCompanyId(201L);
        UserContext.set(user);
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    private AiParseService buildService(BaibaoxiangAgentClient client, long timeoutSeconds) {
        AiParseServiceImpl service = new AiParseServiceImpl(client, objectMapper,
                new MockBaibaoxiangUserIdProvider(), new JdPromptSanitizer());
        ReflectionTestUtils.setField(service, "timeoutSeconds", timeoutSeconds);
        ReflectionTestUtils.setField(service, "jdParseAppId", "test-jd-app");
        return service;
    }

    /** 无 streamInterview 实现的空客户端（JD 解析测试不需要） */
    private BaibaoxiangAgentClient client(String... responses) {
        return new BaibaoxiangAgentClient() {
            private int call = 0;

            @Override
            public String parseJd(String prompt, String userId) {
                String resp = responses[Math.min(call, responses.length - 1)];
                call++;
                return resp;
            }

            @Override
            public void streamInterview(String prompt, String userId, AtomicBoolean cancelled,
                                        Consumer<AgentStreamEvent> consumer) {
            }
        };
    }

    @Test
    void parse_userIdProvidedByProvider() {
        // 客户端收到的 userId 应为 provider 生成的伪标识，而非原始内部用户 ID
        List<String> received = new ArrayList<>();
        BaibaoxiangAgentClient capturingClient = new BaibaoxiangAgentClient() {
            @Override
            public String parseJd(String prompt, String userId) {
                received.add(userId);
                return VALID_JSON;
            }

            @Override
            public void streamInterview(String prompt, String userId, AtomicBoolean cancelled,
                                        Consumer<AgentStreamEvent> consumer) {
            }
        };
        AiParseService service = buildService(capturingClient, 5);
        JdParseResponse resp = service.parseJd(request("负责后端开发，要求 Java 经验"));
        assertEquals("JAVA_BACKEND", resp.getJobType());
        assertEquals(1, received.size());
        assertEquals("local-mock-user", received.get(0), "客户端应收到 provider 生成的伪标识");
        assertFalse(received.get(0).contains("1001"), "不应回传原始内部用户 ID");
    }

    @Test
    void parse_missingUserContext_401() {
        UserContext.clear();
        BaibaoxiangAgentClient neverClient = new BaibaoxiangAgentClient() {
            @Override
            public String parseJd(String prompt, String userId) {
                throw new AssertionError("缺少用户上下文时不应调用客户端");
            }

            @Override
            public void streamInterview(String prompt, String userId, AtomicBoolean cancelled,
                                        Consumer<AgentStreamEvent> consumer) {
            }
        };
        AiParseService service = buildService(neverClient, 5);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.parseJd(request("x")));
        assertEquals(401, ex.getCode());
    }

    @Test
    void parse_success() {
        AiParseService service = buildService(client(VALID_JSON), 5);
        JdParseResponse resp = service.parseJd(request("负责后端开发，要求 Java 经验"));
        assertEquals("JAVA_BACKEND", resp.getJobType());
        assertEquals("BACHELOR", resp.getEducationRequirement());
        assertNotNull(resp.getSalary().getMinAmount());
    }

    @Test
    void parse_timeout2301() {
        BaibaoxiangAgentClient slowClient = new BaibaoxiangAgentClient() {
            @Override
            public String parseJd(String prompt, String userId) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return VALID_JSON;
            }

            @Override
            public void streamInterview(String prompt, String userId, AtomicBoolean cancelled,
                                        Consumer<AgentStreamEvent> consumer) {
            }
        };
        AiParseService service = buildService(slowClient, 1);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.parseJd(request("x")));
        assertEquals(2301, ex.getCode());
    }

    @Test
    void parse_invalidJson_retryThenSuccess() {
        // 第一次格式错误 → 重试 1 次后成功
        AiParseService service = buildService(client("not-json", VALID_JSON), 5);
        JdParseResponse resp = service.parseJd(request("x"));
        assertEquals("JAVA_BACKEND", resp.getJobType());
    }

    @Test
    void parse_stillInvalid500() {
        AiParseService service = buildService(client("not-json"), 5);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.parseJd(request("x")));
        assertEquals(500, ex.getCode());
    }

    @Test
    void parse_containsForbiddenInfo_reject400() {
        // 结果含"男"（性别歧视信息）→ 400 reject
        AiParseService service = buildService(client("{\"jobType\":\"JAVA_BACKEND\",\"jdSummary\":\"要求男性候选人\"}"), 5);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.parseJd(request("x")));
        assertEquals(400, ex.getCode());
    }

    @Test
    void parse_retrySalaryRawTextSanitized() {
        // 第一次格式错误触发重试；第二次薪资无法结构化识别 → rawText 取自脱敏 JD，不得含电话
        String salaryNullJson = "{\"jobType\":\"JAVA_BACKEND\","
                + "\"coreSkills\":[{\"name\":\"Java\",\"level\":\"3\",\"required\":true}],"
                + "\"softSkills\":[],\"minExperienceYears\":3,\"educationRequirement\":\"BACHELOR\","
                + "\"salary\":{\"currency\":\"CNY\",\"period\":\"MONTH\",\"minAmount\":null,\"maxAmount\":null},"
                + "\"jdSummary\":\"负责后端开发\",\"warnings\":[]}";
        AiParseService service = buildService(client("not-json", salaryNullJson), 5);
        JdParseResponse resp = service.parseJd(request("薪资面议，联系 13812345678，负责 Java 后端"));
        assertNotNull(resp.getSalary().getRawText());
        assertFalse(resp.getSalary().getRawText().contains("13812345678"),
                "重试路径薪资原文应取自脱敏 JD，不得含电话");
    }

    private JdParseRequest request(String jdText) {
        JdParseRequest r = new JdParseRequest();
        r.setJdText(jdText);
        return r;
    }
}

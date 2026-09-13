package com.lingxi.job.service;

import cn.hutool.crypto.SecureUtil;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.context.UserDTO;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.job.domain.dto.request.AgentQuestionSubmitRequest;
import com.lingxi.job.domain.dto.response.AgentQuestionSubmitResponse;
import com.lingxi.job.domain.entity.JobPost;
import com.lingxi.job.domain.entity.JobProfile;
import com.lingxi.job.domain.entity.JobQuestion;
import com.lingxi.job.exception.JobErrorCode;
import com.lingxi.job.mapper.JobPostMapper;
import com.lingxi.job.mapper.JobProfileMapper;
import com.lingxi.job.mapper.JobQuestionMapper;
import com.lingxi.job.service.impl.AgentQuestionSubmitServiceImpl;
import com.lingxi.job.agent.QuestionContentSanitizer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 面试官提交 AI 题目申请入库 Service 单元测试（阶段6.3 二期）
 * <p>Mock JobPostMapper/JobProfileMapper/JobQuestionMapper，反射直插 UserContext
 * （项目依赖 mockito-core 不支持 MockedStatic，参照 CandidateJobServiceImplTest），验证：
 * 强制 source/status/createdBy/companyId、二次脱敏、企业隔离、去重（2404）/软删恢复、伪造字段不生效。</p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
class AgentQuestionSubmitServiceImplTest {

    /** 测试专用企业/岗位/用户高位 ID（独占，不污染业务数据） */
    private static final long COMPANY_ID = 999901L;
    private static final long JOB_ID = 999910001L;
    private static final long INTERVIEWER_ID = 99990003L;
    private static final long DELETED_QUESTION_ID = 999900777L;

    private final JobPostMapper postMapper = mock(JobPostMapper.class);
    private final JobProfileMapper profileMapper = mock(JobProfileMapper.class);
    private final JobQuestionMapper questionMapper = mock(JobQuestionMapper.class);
    private final QuestionContentSanitizer sanitizer = new QuestionContentSanitizer();
    private final AgentQuestionSubmitServiceImpl service =
            new AgentQuestionSubmitServiceImpl(postMapper, profileMapper, questionMapper, sanitizer);

    @Test
    void submit_success_savesAiGeneratedPendingReview() {
        JobProfile profile = profile("IT", "[{\"name\":\"Java\"}]");
        when(postMapper.selectByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(new JobPost());
        when(profileMapper.selectByJobIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(profile);
        when(questionMapper.countActiveByCompanyAndContentHash(eq(COMPANY_ID), any(), eq(null))).thenReturn(0);
        when(questionMapper.selectDeletedByContentHash(eq(COMPANY_ID), any())).thenReturn(null);
        when(questionMapper.insert(any())).thenAnswer(inv -> {
            JobQuestion q = inv.getArgument(0);
            q.setId(999900001L);
            return 1;
        });

        setUser(interviewer());
        AgentQuestionSubmitResponse resp;
        try {
            resp = service.submitToLibrary(JOB_ID, request("BASIC", "HashMap 扩容机制"));
        } finally {
            clearUser();
        }

        Assertions.assertEquals(999900001L, resp.getQuestionId());
        Assertions.assertEquals("PENDING_REVIEW", resp.getStatus());
        ArgumentCaptor<JobQuestion> cap = ArgumentCaptor.forClass(JobQuestion.class);
        verify(questionMapper).insert(cap.capture());
        JobQuestion saved = cap.getValue();
        Assertions.assertEquals(COMPANY_ID, saved.getCompanyId());
        Assertions.assertEquals("AI_GENERATED", saved.getSource());
        Assertions.assertEquals("PENDING_REVIEW", saved.getStatus());
        Assertions.assertEquals(INTERVIEWER_ID, saved.getCreatedBy());
        Assertions.assertEquals("IT", saved.getJobType());
        Assertions.assertEquals("BASIC", saved.getQuestionType());
        Assertions.assertEquals("MEDIUM", saved.getDifficulty());
    }

    @Test
    void submit_jobCrossCompanyOrMissing_returns2101() {
        when(postMapper.selectByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(null);
        setUser(interviewer());
        try {
            BusinessException e = Assertions.assertThrows(BusinessException.class,
                    () -> service.submitToLibrary(JOB_ID, request("BASIC", "题目内容")));
            Assertions.assertEquals(JobErrorCode.JOB_NOT_FOUND.getErrorCode(), e.getCode());
            verify(questionMapper, never()).insert(any());
        } finally {
            clearUser();
        }
    }

    @Test
    void submit_duplicateContent_returns2404() {
        when(postMapper.selectByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(new JobPost());
        when(profileMapper.selectByJobIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(profile("IT", null));
        when(questionMapper.countActiveByCompanyAndContentHash(eq(COMPANY_ID), any(), eq(null))).thenReturn(1);

        setUser(interviewer());
        try {
            BusinessException e = Assertions.assertThrows(BusinessException.class,
                    () -> service.submitToLibrary(JOB_ID, request("BASIC", "重复题目")));
            Assertions.assertEquals(JobErrorCode.QUESTION_DUPLICATE_CONTENT.getErrorCode(), e.getCode());
            verify(questionMapper, never()).insert(any());
        } finally {
            clearUser();
        }
    }

    @Test
    void submit_deletedRowRestored_reusesIdAndRefreshesMeta() {
        when(postMapper.selectByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(new JobPost());
        when(profileMapper.selectByJobIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(profile("IT", null));
        when(questionMapper.countActiveByCompanyAndContentHash(eq(COMPANY_ID), any(), eq(null))).thenReturn(0);
        JobQuestion deleted = new JobQuestion();
        deleted.setId(DELETED_QUESTION_ID);
        when(questionMapper.selectDeletedByContentHash(eq(COMPANY_ID), any())).thenReturn(deleted);
        when(questionMapper.restoreDeleted(eq(DELETED_QUESTION_ID), eq(COMPANY_ID), any())).thenReturn(1);

        setUser(interviewer());
        AgentQuestionSubmitResponse resp;
        try {
            resp = service.submitToLibrary(JOB_ID, request("BASIC", "复用软删行"));
        } finally {
            clearUser();
        }

        Assertions.assertEquals(DELETED_QUESTION_ID, resp.getQuestionId());
        ArgumentCaptor<JobQuestion> cap = ArgumentCaptor.forClass(JobQuestion.class);
        verify(questionMapper).restoreDeleted(eq(DELETED_QUESTION_ID), eq(COMPANY_ID), cap.capture());
        Assertions.assertEquals("AI_GENERATED", cap.getValue().getSource());
        Assertions.assertEquals("PENDING_REVIEW", cap.getValue().getStatus());
        verify(questionMapper, never()).insert(any());
    }

    @Test
    void submit_forgedFieldsIgnored_backendTruthWins() {
        when(postMapper.selectByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(new JobPost());
        when(profileMapper.selectByJobIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(profile("IT", null));
        when(questionMapper.countActiveByCompanyAndContentHash(eq(COMPANY_ID), any(), eq(null))).thenReturn(0);
        when(questionMapper.selectDeletedByContentHash(eq(COMPANY_ID), any())).thenReturn(null);
        when(questionMapper.insert(any())).thenAnswer(inv -> {
            JobQuestion q = inv.getArgument(0);
            q.setId(999900001L);
            return 1;
        });

        // 请求对象只含业务字段（DTO 无 source/status/companyId/createdBy，伪造无从传入）
        AgentQuestionSubmitRequest req = request("BASIC", "伪造字段校验");
        setUser(interviewer());
        try {
            service.submitToLibrary(JOB_ID, req);
        } finally {
            clearUser();
        }

        ArgumentCaptor<JobQuestion> cap = ArgumentCaptor.forClass(JobQuestion.class);
        verify(questionMapper).insert(cap.capture());
        JobQuestion saved = cap.getValue();
        Assertions.assertEquals(COMPANY_ID, saved.getCompanyId());
        Assertions.assertEquals("AI_GENERATED", saved.getSource());
        Assertions.assertEquals("PENDING_REVIEW", saved.getStatus());
        Assertions.assertEquals(INTERVIEWER_ID, saved.getCreatedBy());
    }

    @Test
    void submit_sensitiveContent_desensitizedBeforePersist() {
        when(postMapper.selectByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(new JobPost());
        when(profileMapper.selectByJobIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(profile("IT", null));
        when(questionMapper.countActiveByCompanyAndContentHash(eq(COMPANY_ID), any(), eq(null))).thenReturn(0);
        when(questionMapper.selectDeletedByContentHash(eq(COMPANY_ID), any())).thenReturn(null);
        when(questionMapper.insert(any())).thenAnswer(inv -> {
            JobQuestion q = inv.getArgument(0);
            q.setId(999900001L);
            return 1;
        });

        String sensitive = "候选人张伟同学在腾讯科技有限公司负责智慧城市项目，联系方式 13812345678";
        setUser(interviewer());
        try {
            service.submitToLibrary(JOB_ID, request("BASIC", sensitive));
        } finally {
            clearUser();
        }

        ArgumentCaptor<JobQuestion> cap = ArgumentCaptor.forClass(JobQuestion.class);
        verify(questionMapper).insert(cap.capture());
        String content = cap.getValue().getContent();
        Assertions.assertFalse(content.contains("张伟"), "姓名应脱敏");
        Assertions.assertFalse(content.contains("腾讯科技有限公司"), "公司应脱敏");
        Assertions.assertFalse(content.contains("13812345678"), "手机号应脱敏");
        Assertions.assertTrue(content.contains("某公司"), "公司应替换为占位");
        Assertions.assertEquals(SecureUtil.sha256(content), cap.getValue().getContentSha256(),
                "content_sha256 应基于脱敏后内容");
    }

    @Test
    void submit_profileMissing_returns400() {
        when(postMapper.selectByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(new JobPost());
        when(profileMapper.selectByJobIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(null);

        setUser(interviewer());
        try {
            BusinessException e = Assertions.assertThrows(BusinessException.class,
                    () -> service.submitToLibrary(JOB_ID, request("BASIC", "题目内容")));
            Assertions.assertEquals(400, e.getCode());
        } finally {
            clearUser();
        }
    }

    @Test
    void submit_questionTypeInvalid_returns400() {
        when(postMapper.selectByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(new JobPost());
        when(profileMapper.selectByJobIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(profile("IT", null));

        setUser(interviewer());
        try {
            BusinessException e = Assertions.assertThrows(BusinessException.class,
                    () -> service.submitToLibrary(JOB_ID, request("WRONG_TYPE", "题目内容")));
            Assertions.assertEquals(400, e.getCode());
        } finally {
            clearUser();
        }
    }

    /** keyPoints 数组：trim/去空/逐条脱敏后按换行拼接落库 */
    @Test
    void submit_keyPointsList_joinedDesensitizedTrimDropped() {
        when(postMapper.selectByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(new JobPost());
        when(profileMapper.selectByJobIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(profile("IT", null));
        when(questionMapper.countActiveByCompanyAndContentHash(eq(COMPANY_ID), any(), eq(null))).thenReturn(0);
        when(questionMapper.selectDeletedByContentHash(eq(COMPANY_ID), any())).thenReturn(null);
        when(questionMapper.insert(any())).thenAnswer(inv -> {
            JobQuestion q = inv.getArgument(0);
            q.setId(999900001L);
            return 1;
        });

        AgentQuestionSubmitRequest req = request("BASIC", "HashMap 扩容机制");
        req.setKeyPoints(Arrays.asList("并发安全", "  哈希冲突  ", "联系方式13812345678", "", null));
        setUser(interviewer());
        try {
            service.submitToLibrary(JOB_ID, req);
        } finally {
            clearUser();
        }

        ArgumentCaptor<JobQuestion> cap = ArgumentCaptor.forClass(JobQuestion.class);
        verify(questionMapper).insert(cap.capture());
        String saved = cap.getValue().getKeyPoints();
        Assertions.assertEquals("并发安全\n哈希冲突\n联系方式[已脱敏]", saved,
                "数组 keyPoints 应 trim/去空/逐条脱敏并按换行拼接");
        Assertions.assertFalse(saved.contains("13812345678"), "手机号应脱敏");
    }

    /** keyPoints 全空（空白/null）→ 落库 null */
    @Test
    void submit_keyPointsAllBlank_savesNull() {
        when(postMapper.selectByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(new JobPost());
        when(profileMapper.selectByJobIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(profile("IT", null));
        when(questionMapper.countActiveByCompanyAndContentHash(eq(COMPANY_ID), any(), eq(null))).thenReturn(0);
        when(questionMapper.selectDeletedByContentHash(eq(COMPANY_ID), any())).thenReturn(null);
        when(questionMapper.insert(any())).thenReturn(1);

        AgentQuestionSubmitRequest req = request("BASIC", "HashMap 扩容机制");
        req.setKeyPoints(Arrays.asList("  ", "", null));
        setUser(interviewer());
        try {
            service.submitToLibrary(JOB_ID, req);
        } finally {
            clearUser();
        }

        ArgumentCaptor<JobQuestion> cap = ArgumentCaptor.forClass(JobQuestion.class);
        verify(questionMapper).insert(cap.capture());
        Assertions.assertNull(cap.getValue().getKeyPoints(), "全空 keyPoints 应落库为 null");
    }

    /** 单条考察要点超 500 → 400，不落库 */
    @Test
    void submit_keyPointsSingleItemTooLong_returns400() {
        when(postMapper.selectByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(new JobPost());
        when(profileMapper.selectByJobIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(profile("IT", null));

        AgentQuestionSubmitRequest req = request("BASIC", "HashMap 扩容机制");
        req.setKeyPoints(Collections.singletonList(pad("要", 501)));
        setUser(interviewer());
        try {
            BusinessException e = Assertions.assertThrows(BusinessException.class,
                    () -> service.submitToLibrary(JOB_ID, req));
            Assertions.assertEquals(400, e.getCode());
            verify(questionMapper, never()).insert(any());
        } finally {
            clearUser();
        }
    }

    /** 拼接后总长超 1000 → 400（validateOptionalText 兜底），不落库 */
    @Test
    void submit_keyPointsTotalTooLong_returns400() {
        when(postMapper.selectByIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(new JobPost());
        when(profileMapper.selectByJobIdAndCompanyId(JOB_ID, COMPANY_ID)).thenReturn(profile("IT", null));

        AgentQuestionSubmitRequest req = request("BASIC", "HashMap 扩容机制");
        // 3 条各 400 字，换行拼接后 1202 > 1000 → validateOptionalText 兜底 400
        req.setKeyPoints(Arrays.asList(pad("要", 400), pad("要", 400), pad("要", 400)));
        setUser(interviewer());
        try {
            BusinessException e = Assertions.assertThrows(BusinessException.class,
                    () -> service.submitToLibrary(JOB_ID, req));
            Assertions.assertEquals(400, e.getCode());
            verify(questionMapper, never()).insert(any());
        } finally {
            clearUser();
        }
    }

    private static String pad(String s, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    // ==================== 辅助 ====================

    private static AgentQuestionSubmitRequest request(String questionType, String content) {
        AgentQuestionSubmitRequest req = new AgentQuestionSubmitRequest();
        req.setQuestionType(questionType);
        req.setContent(content);
        return req;
    }

    private static JobProfile profile(String jobType, String coreSkills) {
        JobProfile p = new JobProfile();
        p.setJobType(jobType);
        p.setCoreSkills(coreSkills);
        return p;
    }

    private static UserDTO interviewer() {
        UserDTO user = new UserDTO();
        user.setUserId(INTERVIEWER_ID);
        user.setCompanyId(COMPANY_ID);
        user.setRole("INTERVIEWER");
        return user;
    }

    /** 反射直插 UserContext.USER_HOLDER ThreadLocal（mockito-core 无 inline mock maker，不支持 mockStatic） */
    private static void setUser(UserDTO user) {
        try {
            Field holder = UserContext.class.getDeclaredField("USER_HOLDER");
            holder.setAccessible(true);
            @SuppressWarnings("unchecked")
            ThreadLocal<UserDTO> threadLocal = (ThreadLocal<UserDTO>) holder.get(null);
            if (user == null) {
                threadLocal.remove();
            } else {
                threadLocal.set(user);
            }
        } catch (Exception e) {
            throw new IllegalStateException("注入 UserContext 失败", e);
        }
    }

    private static void clearUser() {
        setUser(null);
    }
}

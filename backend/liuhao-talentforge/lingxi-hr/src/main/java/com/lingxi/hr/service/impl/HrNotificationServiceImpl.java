package com.lingxi.hr.service.impl;

import com.lingxi.common.domain.Result;
import com.lingxi.hr.domain.entity.HrCompanyMember;
import com.lingxi.hr.feign.ResumeFeignClient;
import com.lingxi.hr.feign.UserFeignClient;
import com.lingxi.hr.feign.dto.ApplicationDTO;
import com.lingxi.hr.feign.dto.SysUserDTO;
import com.lingxi.hr.mapper.HrCompanyMemberMapper;
import com.lingxi.hr.service.HrNotificationService;
import com.lingxi.hr.service.notify.ApplicationNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 通知服务实现（HR 端事件通知编排）
 *
 * <p>新投递通知：取投递详情（companyId/jobTitle/candidateId）→ 查本企业 HR_ADMIN
 * → 拼候选人姓名 → {@link ApplicationNotifier} 直调 lingxi-chat。全程 best-effort：
 * 任何环节失败仅告警，不抛异常（通知不影响投递闭环）。
 *
 * @author 成员D
 * @since 2026-08-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrNotificationServiceImpl implements HrNotificationService {

    private static final String ROLE_HR_ADMIN = "HR_ADMIN";

    private final ResumeFeignClient resumeFeignClient;
    private final UserFeignClient userFeignClient;
    private final HrCompanyMemberMapper hrCompanyMemberMapper;
    private final ApplicationNotifier applicationNotifier;

    @Override
    public void notifyHrNewApplication(Long applicationId) {
        try {
            // ① 投递详情（公司归属/岗位/候选人）
            ApplicationDTO application = fetchApplication(applicationId);
            if (application == null) {
                log.warn("新投递通知: 投递不存在或获取失败, applicationId={}", applicationId);
                return;
            }
            Long companyId = application.getCompanyId();
            if (companyId == null) {
                log.warn("新投递通知: 投递无企业归属, applicationId={}", applicationId);
                return;
            }

            // ② 本企业 HR_ADMIN（招聘负责人）
            List<HrCompanyMember> hrAdmins = hrCompanyMemberMapper.selectByCompanyId(companyId, ROLE_HR_ADMIN, null);
            if (hrAdmins == null || hrAdmins.isEmpty()) {
                log.warn("新投递通知: 未找到企业 HR_ADMIN, companyId={}, applicationId={}", companyId, applicationId);
                return;
            }

            // ③ 候选人姓名（best-effort）
            String candidateName = "候选人" + application.getCandidateId();
            Map<Long, SysUserDTO> userMap = fetchUsers(Collections.singletonList(application.getCandidateId()));
            SysUserDTO candidate = userMap.get(application.getCandidateId());
            if (candidate != null && candidate.getName() != null) {
                candidateName = candidate.getName();
            }

            // ④ 通知本企业全部 HR_ADMIN
            for (HrCompanyMember admin : hrAdmins) {
                applicationNotifier.notifyHrNewApplication(
                        admin.getUserId(), applicationId, application.getJobTitle(), candidateName);
            }
        } catch (Exception e) {
            log.warn("新投递通知 HR 异常（不阻塞）: applicationId={}, error={}", applicationId, e.getMessage());
        }
    }

    private ApplicationDTO fetchApplication(Long applicationId) {
        try {
            Result<ApplicationDTO> result = resumeFeignClient.getApplication(applicationId);
            if (result != null && result.isSuccess() && result.getData() != null) {
                return result.getData();
            }
            log.warn("获取投递详情失败: applicationId={}, code={}, message={}",
                    applicationId, result != null ? result.getCode() : null,
                    result != null ? result.getMessage() : null);
        } catch (Exception e) {
            log.warn("获取投递详情异常: applicationId={}, error={}", applicationId, e.getMessage());
        }
        return null;
    }

    private Map<Long, SysUserDTO> fetchUsers(List<Long> userIds) {
        try {
            String ids = userIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            Result<List<SysUserDTO>> r = userFeignClient.batchUsers(ids);
            if (r != null && r.isSuccess() && r.getData() != null) {
                return r.getData().stream()
                        .filter(u -> u != null && u.getId() != null)
                        .collect(java.util.stream.Collectors.toMap(SysUserDTO::getId, u -> u));
            }
        } catch (Exception e) {
            log.warn("批量查询用户失败: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }
}

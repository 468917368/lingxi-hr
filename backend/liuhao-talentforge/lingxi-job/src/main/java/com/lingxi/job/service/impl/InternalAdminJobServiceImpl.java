package com.lingxi.job.service.impl;

import com.lingxi.common.enums.JobStatus;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.job.domain.dto.request.JobOfflineRequest;
import com.lingxi.job.domain.dto.response.JobStatusResponse;
import com.lingxi.job.domain.entity.JobPost;
import com.lingxi.job.domain.entity.JobStatusLog;
import com.lingxi.job.enums.CloseReasonEnum;
import com.lingxi.job.exception.JobErrorCode;
import com.lingxi.job.mapper.JobPostMapper;
import com.lingxi.job.mapper.JobStatusLogMapper;
import com.lingxi.job.service.InternalAdminJobService;
import com.lingxi.job.validator.JobStateValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员内部岗位服务实现
 *
 * @author lingxi-team
 * @since 2026-08-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalAdminJobServiceImpl implements InternalAdminJobService {

    private final JobPostMapper jobPostMapper;
    private final JobStatusLogMapper jobStatusLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobStatusResponse offlineJob(Long jobId, JobOfflineRequest request, Long operatorId) {
        // 0. 防御性判空（对齐 deleteJob 风格，HTTP 路径已被 @Valid 拦截，此处防直调 Service）+ 操作人负数拒绝
        if (request.getVersion() == null) {
            throw new BusinessException(400, "岗位版本不能为空");
        }
        // 操作人必填且非 0：0=SYSTEM 保留语义，管理员操作不得写 operator_role=ADMIN + operator_id=0 的矛盾审计行
        if (operatorId == null) {
            throw new BusinessException(400, "缺少操作人");
        }
        if (operatorId <= 0) {
            throw new BusinessException(400, "操作人ID非法");
        }
        // 1. 契约校验：reason 必须为 VIOLATION（先于 DB，畸形请求无论状态都 400）
        if (!CloseReasonEnum.VIOLATION.getCode().equals(request.getReason())) {
            throw new BusinessException(400, "下架原因仅支持 VIOLATION");
        }
        // 2. 管理员全局可见，无企业隔离；软删除视为不存在
        JobPost post = jobPostMapper.selectById(jobId);
        if (post == null) {
            throw new BusinessException(JobErrorCode.JOB_NOT_FOUND);
        }
        // 3. 幂等：CLOSED+VIOLATION 短路返回当前状态（不校验 version、不写库）
        if (JobStatus.CLOSED.getCode().equals(post.getStatus())
                && CloseReasonEnum.VIOLATION.getCode().equals(post.getCloseReason())) {
            return buildResponse(post);
        }
        // 4. 状态机：DRAFT / CLOSED(其他原因) → 2103；PUBLISHED/PAUSED 放行
        JobStateValidator.validateTransition(post.getStatus(), "OFFLINE", post.getCloseReason(), 0);
        // 5. 乐观锁下架（companyId 取自查出的岗位；SQL WHERE 含 status 断言兜底）
        JobPost op = new JobPost();
        op.setId(jobId);
        op.setCompanyId(post.getCompanyId());
        op.setVersion(request.getVersion());
        if (jobPostMapper.offline(op) == 0) {
            throw new BusinessException(JobErrorCode.JOB_VERSION_CONFLICT);
        }
        // 6. remark 消毒（防日志注入：@NotBlank/@Size 不拦截 \r\n\t）
        String safeRemark = sanitizeRemark(request.getRemark());
        log.info("管理员违规下架岗位: jobId={}, operatorId={}, remark={}",
                jobId, operatorId, safeRemark);
        // 7. 落审计日志（operator_role=ADMIN、reason_detail=remark；同事务，非 1 行抛异常回滚）
        writeStatusLog(post, operatorId, safeRemark);
        // 8. 成功后重新查库组装响应（成功与幂等两条路径的 version/closedAt 均来自 DB 真实值，消除双时钟差异）
        JobPost updated = jobPostMapper.selectById(jobId);
        return buildResponse(updated);
    }

    /**
     * remark 消毒：trim + 控制字符替换为空格，防伪造日志行（日志注入）
     */
    private String sanitizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        return remark.trim().replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    }

    /**
     * 写岗位状态变更审计日志（管理员违规下架，同事务）
     * <p>operator_role=ADMIN、operator_id 为真实管理员；reason_detail 存 remark（遗留 #15 收尾）。
     * insert 非 1 行抛异常回滚同事务（杜绝"已下架无审计"）。必须在重新查库前调用
     * （post 仍为变更前状态，fromStatus 正确）。</p>
     */
    private void writeStatusLog(JobPost post, Long operatorId, String safeRemark) {
        JobStatusLog statusLog = new JobStatusLog();
        statusLog.setCompanyId(post.getCompanyId());
        statusLog.setJobId(post.getId());
        statusLog.setFromStatus(post.getStatus());
        statusLog.setToStatus(JobStatus.CLOSED.getCode());
        statusLog.setReason(CloseReasonEnum.VIOLATION.getCode());
        statusLog.setReasonDetail(safeRemark);
        statusLog.setOperatorId(operatorId);
        statusLog.setOperatorRole("ADMIN");
        if (jobStatusLogMapper.insert(statusLog) != 1) {
            throw new IllegalStateException("岗位违规下架审计日志插入失败, jobId=" + post.getId());
        }
    }

    /**
     * 统一响应组装（成功路径与幂等短路共用，version/closedAt 均为 DB 真实值）
     */
    private JobStatusResponse buildResponse(JobPost post) {
        JobStatusResponse resp = new JobStatusResponse();
        resp.setJobId(post.getId());
        resp.setStatus(post.getStatus());
        resp.setVersion(post.getVersion());
        resp.setClosedAt(post.getClosedAt());
        resp.setCloseReason(post.getCloseReason());
        return resp;
    }
}

package com.lingxi.hr.mapper;

import com.lingxi.hr.domain.entity.HrOffer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Offer 记录 Mapper（Day 8）
 *
 * <p>hr_offer.id 为 Snowflake 应用侧生成（非自增），insert 显式带 id。
 * 状态流转均走乐观锁 {@code WHERE id=? AND status=?}（rows=0 → 状态冲突）。
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Mapper
public interface HrOfferMapper {

    /** 插入 Offer（Snowflake id 显式提供） */
    int insert(HrOffer offer);

    /** 按ID查询 */
    HrOffer selectById(@Param("id") Long id);

    /** 按投递记录ID查最新一条 Offer（重复发起校验；允许多条记录，取 id 最大） */
    HrOffer selectByApplicationId(@Param("applicationId") Long applicationId);

    /** 按投递记录ID查最新一条 Offer 并加行锁（C 端反向 accept/reject 并发控制，2026-08-08） */
    HrOffer selectByApplicationIdForUpdate(@Param("applicationId") Long applicationId);

    /** 按ID查询并加行锁（accept/reject 并发控制） */
    HrOffer selectByIdForUpdate(@Param("id") Long id);

    /** 列表总数（公司隔离 + status/dateRange/jobId 过滤） */
    long countPage(@Param("companyId") Long companyId,
                   @Param("status") String status,
                   @Param("dateRange") String dateRange,
                   @Param("jobId") Long jobId);

    /** 列表分页（按 created_at DESC） */
    List<HrOffer> selectPage(@Param("companyId") Long companyId,
                             @Param("status") String status,
                             @Param("dateRange") String dateRange,
                             @Param("jobId") Long jobId,
                             @Param("offset") int offset,
                             @Param("size") int size);

    /** 接受：SENT → ACCEPTED + accepted_at（乐观锁） */
    int updateAccept(@Param("id") Long id, @Param("fromStatus") String fromStatus);

    /** 拒绝：SENT → REJECTED + rejected_at + reject_reason（乐观锁） */
    int updateReject(@Param("id") Long id, @Param("fromStatus") String fromStatus,
                     @Param("rejectReason") String rejectReason);

    /** 过期：SENT → EXPIRED（乐观锁，条件更新，rows=0 跳过） */
    int updateExpire(@Param("id") Long id, @Param("fromStatus") String fromStatus);

    /** 撤回：SENT → WITHDRAWN（乐观锁） */
    int updateWithdraw(@Param("id") Long id, @Param("fromStatus") String fromStatus);

    /** 催促：urge_count+1 + last_urge_at=NOW()（仅 SENT 可催，WHERE 兜底） */
    int updateUrge(@Param("id") Long id);

    /** 更新最后一次 HC 补偿对账时间 */
    int updateSyncTime(@Param("id") Long id);

    /** 过期扫描：SENT 且已过期（条件更新前先查，LIMIT 防长事务） */
    List<HrOffer> selectExpiredSnt(@Param("limit") int limit);

    /** 对账扫描：终态（REJECTED/EXPIRED/WITHDRAWN）且未做过补偿对账 */
    List<HrOffer> selectTerminalForReconcile(@Param("limit") int limit);

    /** 批量查已存在 Offer 记录的投递ID集合（候选人列表 hasOfferRecord，任意状态含终态） */
    List<Long> selectOfferRecordAppIds(@Param("applicationIds") List<Long> applicationIds);

    /** 批量查每投递最新一条 Offer 的状态（候选人列表 lastOfferStatus，2026-08-07 前端清单；返回只填 applicationId/status） */
    List<HrOffer> selectLastOfferStatus(@Param("applicationIds") List<Long> applicationIds);
}

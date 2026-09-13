package com.lingxi.job.mapper;

import com.lingxi.job.domain.entity.JobHcReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * HC预冻结流水表 Mapper
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Mapper
public interface JobHcReservationMapper {

    /**
     * 按企业+Offer查询流水
     */
    JobHcReservation selectByCompanyAndOffer(@Param("companyId") Long companyId, @Param("offerId") Long offerId);

    /**
     * 按企业+Offer查询流水并加锁（幂等校验）
     */
    JobHcReservation selectByCompanyAndOfferForUpdate(@Param("companyId") Long companyId, @Param("offerId") Long offerId);

    /**
     * 按企业+岗位+Offer查询流水并加锁（防操作错岗位）
     */
    JobHcReservation selectByCompanyJobOfferForUpdate(@Param("companyId") Long companyId, @Param("jobId") Long jobId,
                                                      @Param("offerId") Long offerId);

    /**
     * 新增流水
     */
    int insert(JobHcReservation reservation);

    /**
     * 更新流水状态（乐观锁 version）
     */
    int updateStatus(JobHcReservation reservation);
}

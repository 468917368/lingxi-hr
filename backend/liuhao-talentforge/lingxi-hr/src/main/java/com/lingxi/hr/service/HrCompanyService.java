package com.lingxi.hr.service;

import com.lingxi.hr.domain.dto.HrCompanyDTO;
import com.lingxi.hr.domain.vo.HrCertificationStatusVO;
import com.lingxi.hr.domain.vo.HrCompanyVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 企业信息/认证服务
 *
 * @author 成员D
 * @since 2026-08-02
 */
public interface HrCompanyService {

    /**
     * 创建企业（独立入口，创建企业 + 创始人成员）
     */
    HrCompanyVO createCompany(HrCompanyDTO dto);

    /**
     * 查询企业信息
     */
    HrCompanyVO getCompanyInfo(Long companyId);

    /**
     * 更新企业信息（认证通过后不更新 name）
     */
    void updateCompanyInfo(Long companyId, HrCompanyDTO dto);

    /**
     * 提交企业认证（HR 首次入驻表单：创建企业 + 成员 + 认证申请）
     */
    void submitCertification(HrCompanyDTO dto, MultipartFile businessLicense, MultipartFile certMaterial);

    /**
     * 查询企业认证状态（pending 审核页使用，按当前用户反查企业）
     */
    HrCertificationStatusVO getCertificationStatus(Long userId);
}

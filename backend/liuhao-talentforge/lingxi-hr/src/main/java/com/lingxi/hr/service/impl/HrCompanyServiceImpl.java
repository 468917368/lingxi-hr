package com.lingxi.hr.service.impl;

import com.lingxi.common.context.UserContext;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.common.util.MinioUtil;
import com.lingxi.hr.domain.dto.HrCompanyDTO;
import com.lingxi.hr.domain.entity.HrCompany;
import com.lingxi.hr.domain.entity.HrCompanyCertification;
import com.lingxi.hr.domain.entity.HrCompanyMember;
import com.lingxi.hr.domain.vo.HrCertificationStatusVO;
import com.lingxi.hr.domain.vo.HrCompanyVO;
import com.lingxi.hr.exception.HrErrorCode;
import com.lingxi.hr.mapper.HrCompanyCertificationMapper;
import com.lingxi.hr.mapper.HrCompanyMapper;
import com.lingxi.hr.mapper.HrCompanyMemberMapper;
import com.lingxi.hr.service.HrCompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 企业信息/认证服务实现
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrCompanyServiceImpl implements HrCompanyService {

    private final HrCompanyMapper hrCompanyMapper;
    private final HrCompanyMemberMapper hrCompanyMemberMapper;
    private final HrCompanyCertificationMapper hrCompanyCertificationMapper;
    private final MinioUtil minioUtil;

    private static final String ROLE_HR_ADMIN = "HR_ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String CERT_STATUS_PENDING = "PENDING";
    private static final String CERT_STATUS_APPROVED = "APPROVED";
    /** hr_company_member.department 为 NOT NULL，创建者默认部门 */
    private static final String DEFAULT_DEPARTMENT = "总部";

    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 6;
    private static final int MAX_INVITE_CODE_ATTEMPTS = 10;

    /** 认证文件最大 5MB */
    private static final long MAX_CERT_FILE_SIZE = 5 * 1024 * 1024L;
    private static final List<String> ALLOWED_CERT_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".pdf");
    private static final List<String> ALLOWED_CERT_CONTENT_TYPES =
            Arrays.asList("image/jpeg", "image/png", "application/pdf");
    private static final String LICENSE_PATH_PREFIX = "cert/business-license/";
    private static final String MATERIAL_PATH_PREFIX = "cert/material/";

    @Override
    @Transactional
    public HrCompanyVO createCompany(HrCompanyDTO dto) {
        Long userId = requireLogin();

        // 校验用户是否已有企业（HR_ADMIN 即已创建过企业）
        HrCompanyMember existing = hrCompanyMemberMapper.selectActiveByUserId(userId);
        if (existing != null && ROLE_HR_ADMIN.equals(existing.getRole())) {
            throw new BusinessException(HrErrorCode.COMPANY_NAME_DUPLICATE.getErrorCode(), "您已创建企业");
        }

        // 生成唯一邀请码
        String inviteCode = generateUniqueInviteCode();

        // 写入企业
        HrCompany company = buildCompany(dto, inviteCode, null);
        try {
            hrCompanyMapper.insert(company);
        } catch (DuplicateKeyException e) {
            log.warn("企业名称冲突: name={}", dto.getName());
            throw new BusinessException(HrErrorCode.COMPANY_NAME_DUPLICATE);
        }

        // 写入创始人成员
        hrCompanyMemberMapper.insert(buildFounderMember(company.getId(), userId));

        return toVO(company);
    }

    @Override
    public HrCompanyVO getCompanyInfo(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        HrCompany company = hrCompanyMapper.selectById(companyId);
        if (company == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return toVO(company);
    }

    @Override
    public void updateCompanyInfo(Long companyId, HrCompanyDTO dto) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        HrCompany company = hrCompanyMapper.selectById(companyId);
        if (company == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 认证通过后 name 不可修改（updateById 本就不更新 name，此处防御）
        if (CERT_STATUS_APPROVED.equals(company.getCertStatus())) {
            dto.setName(null);
        }

        // 仅更新非 null 字段：shortName/description/logoUrl/address/website
        HrCompany update = new HrCompany();
        update.setId(companyId);
        // industry/scale补充
        if (dto.getIndustry() != null) update.setIndustry(dto.getIndustry());
        if (dto.getScale() != null) update.setScale(dto.getScale());
        if (dto.getShortName() != null) update.setShortName(dto.getShortName());
        if (dto.getDescription() != null) update.setDescription(dto.getDescription());
        if (dto.getLogoUrl() != null) update.setLogoUrl(dto.getLogoUrl());
        if (dto.getAddress() != null) update.setAddress(dto.getAddress());
        if (dto.getWebsite() != null) update.setWebsite(dto.getWebsite());
        hrCompanyMapper.updateById(update);
    }

    @Override
    @Transactional
    public void submitCertification(HrCompanyDTO dto, MultipartFile businessLicense, MultipartFile certMaterial) {
        Long userId = requireLogin();

        // 校验该 HR 账号是否已有企业：有则拒绝，无则继续创建
        HrCompanyMember existing = hrCompanyMemberMapper.selectActiveByUserId(userId);
        if (existing != null) {
            throw new BusinessException(HrErrorCode.COMPANY_NAME_DUPLICATE.getErrorCode(), "您已创建企业");
        }

        // 校验并上传营业执照（选填，提供时校验格式/大小）
        String businessLicenseUrl = null;
        if (businessLicense != null && !businessLicense.isEmpty()) {
            validateCertFile(businessLicense, "营业执照");
            businessLicenseUrl = uploadFile(LICENSE_PATH_PREFIX, userId, businessLicense);
        }

        // 上传其他证明材料（选填）
        String certMaterialUrl = null;
        if (certMaterial != null && !certMaterial.isEmpty()) {
            validateCertFile(certMaterial, "证明材料");
            certMaterialUrl = uploadFile(MATERIAL_PATH_PREFIX, userId, certMaterial);
        }

        // 生成邀请码并写入企业
        String inviteCode = generateUniqueInviteCode();
        HrCompany company = buildCompany(dto, inviteCode, businessLicenseUrl);
        try {
            hrCompanyMapper.insert(company);
        } catch (DuplicateKeyException e) {
            log.warn("企业名称冲突: name={}", dto.getName());
            throw new BusinessException(HrErrorCode.COMPANY_NAME_DUPLICATE);
        }

        // 写入创始人成员
        hrCompanyMemberMapper.insert(buildFounderMember(company.getId(), userId));

        // 写入认证申请
        HrCompanyCertification certification = new HrCompanyCertification();
        certification.setCompanyId(company.getId());
        certification.setApplicantId(userId);
        certification.setBusinessLicenseUrl(businessLicenseUrl);
        certification.setCertMaterialUrl(certMaterialUrl);
        certification.setStatus(CERT_STATUS_PENDING);
        hrCompanyCertificationMapper.insert(certification);
    }

    @Override
    public HrCertificationStatusVO getCertificationStatus(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        // 注册当次 token 不含 companyId，不能依赖 UserContext.getCompanyId()，改为按 userId 反查成员关系
        HrCompanyMember member = hrCompanyMemberMapper.selectActiveByUserId(userId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        HrCompany company = hrCompanyMapper.selectById(member.getCompanyId());
        if (company == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        HrCertificationStatusVO vo = new HrCertificationStatusVO();
        vo.setCertStatus(company.getCertStatus());
        vo.setCertRejectReason(company.getCertRejectReason());
        vo.setSubmittedAt(company.getCreatedAt());
        return vo;
    }

    // ==================== 私有方法 ====================

    private Long requireLogin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 生成 6 位唯一邀请码（大写字母+数字），循环查重
     */
    private String generateUniqueInviteCode() {
        Random random = new Random();
        for (int i = 0; i < MAX_INVITE_CODE_ATTEMPTS; i++) {
            StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
            for (int j = 0; j < INVITE_CODE_LENGTH; j++) {
                sb.append(INVITE_CODE_CHARS.charAt(random.nextInt(INVITE_CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (hrCompanyMapper.selectByInviteCode(code) == null) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR.getErrorCode(), "邀请码生成失败，请重试");
    }

    private HrCompany buildCompany(HrCompanyDTO dto, String inviteCode, String businessLicenseUrl) {
        HrCompany company = new HrCompany();
        company.setName(dto.getName());
        company.setShortName(dto.getShortName());
        company.setDescription(dto.getDescription());
        company.setIndustry(dto.getIndustry());
        company.setScale(dto.getScale());
        company.setLogoUrl(dto.getLogoUrl());
        company.setAddress(dto.getAddress());
        company.setWebsite(dto.getWebsite());
        company.setInviteCode(inviteCode);
        company.setBusinessLicenseUrl(businessLicenseUrl);
        company.setCertStatus(CERT_STATUS_PENDING);
        company.setStatus(STATUS_ACTIVE);
        return company;
    }

    private HrCompanyMember buildFounderMember(Long companyId, Long userId) {
        HrCompanyMember member = new HrCompanyMember();
        member.setCompanyId(companyId);
        member.setUserId(userId);
        member.setRole(ROLE_HR_ADMIN);
        member.setDepartment(DEFAULT_DEPARTMENT);
        member.setInterviewCount(0);
        member.setStatus(STATUS_ACTIVE);
        return member;
    }

    /**
     * 校验认证文件：非空、格式（jpg/png/pdf）、大小（≤5MB）
     */
    private void validateCertFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, fieldName + "不能为空");
        }
        if (file.getSize() > MAX_CERT_FILE_SIZE) {
            throw new BusinessException(HrErrorCode.CERT_FILE_INVALID);
        }
        String extension = getFileExtension(file.getOriginalFilename());
        if (extension == null || !ALLOWED_CERT_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(HrErrorCode.CERT_FILE_INVALID);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CERT_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(HrErrorCode.CERT_FILE_INVALID);
        }
    }

    private String uploadFile(String pathPrefix, Long userId, MultipartFile file) {
        String extension = getFileExtension(file.getOriginalFilename());
        String objectName = pathPrefix + userId + "/" + UUID.randomUUID() + extension;
        try (InputStream inputStream = file.getInputStream()) {
            return minioUtil.upload(objectName, inputStream, file.getContentType());
        } catch (Exception e) {
            log.error("文件上传失败: objectName={}", objectName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getErrorCode(), "文件上传失败，请重试");
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    private HrCompanyVO toVO(HrCompany company) {
        HrCompanyVO vo = new HrCompanyVO();
        BeanUtils.copyProperties(company, vo);
        return vo;
    }
}

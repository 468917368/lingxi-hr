package com.lingxi.resume.service;

import com.lingxi.common.domain.PageRequest;
import com.lingxi.common.domain.PageResult;
import com.lingxi.resume.domain.dto.UpdateResumeDTO;
import com.lingxi.resume.domain.vo.InternalResumeVO;
import com.lingxi.resume.domain.vo.ResumeDetailVO;
import com.lingxi.resume.domain.vo.ResumeVO;
import com.lingxi.resume.domain.vo.UploadResumeVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历服务
 *
 * @author 成员C
 * @since 2026-08-01
 */
public interface ResumeService {

    /**
     * 简历列表（分页，仅当前登录候选人数据）
     *
     * @param pageRequest 分页请求
     * @return 分页简历列表
     */
    PageResult<ResumeVO> listResumes(PageRequest pageRequest);

    /**
     * 上传简历（格式/大小/5份上限校验 → MinIO 存原件 → 入库 PENDING）
     *
     * @param file 上传文件（pdf/docx/doc/jpg/png，≤10MB）
     * @return 上传结果（resumeId/fileUrl/parseStatus）
     */
    UploadResumeVO upload(MultipartFile file);

    /**
     * 简历详情（仅当前候选人）
     *
     * @param id 简历ID
     * @return 简历详情（含 cardStructure）
     */
    ResumeDetailVO getResumeDetail(Long id);

    /**
     * 在线编辑保存（更新 cardStructure）
     *
     * @param id  简历ID
     * @param dto 更新内容
     */
    void updateResume(Long id, UpdateResumeDTO dto);

    /**
     * 删除简历（逻辑删除；若为默认简历同时清默认标记；清 MinIO 原件）
     *
     * @param id 简历ID
     */
    void deleteResume(Long id);

    /**
     * 设为默认简历（先取消其他默认，再置目标为默认，事务保证互斥）
     *
     * @param id 简历ID
     */
    void setDefault(Long id);

    /**
     * 上传/更换简历头像（JPG/PNG，≤2MB；覆盖 MinIO 原头像并更新 face_photo_url）
     *
     * @param id   简历ID
     * @param file 头像图片文件
     * @return 新的头像 presigned URL（可直接展示）
     */
    String uploadFacePhoto(Long id, MultipartFile file);

    /**
     * 简历详情（内部接口，免归属校验，供 lingxi-job / lingxi-hr Feign 调用）
     *
     * <p>仅返回非敏感字段：id / parseStatus / cardStructure / resumeMdUrl / candidateName，
     * 不含 phone / email / wechat。
     *
     * @param id 简历ID
     * @return 内部简历视图（精简，无联系方式）
     */
    InternalResumeVO getInternalResumeDetail(Long id);

    /**
     * 简历全量详情（内部接口，免归属校验，供 lingxi-hr HR端简历展示）
     *
     * <p>返回全量 ResumeDetailVO（含 phone/email/wechat 等联系方式），
     * HR 端查看候选人简历需完整信息。
     *
     * @param id 简历ID
     * @return 简历全量详情
     */
    ResumeDetailVO getInternalResumeFullDetail(Long id);

    /**
     * 按用户ID获取默认简历（内部接口，供 Agent 工具调用）
     *
     * @param userId 用户ID（候选人ID）
     * @return 精简简历视图，无简历时返回null
     */
    InternalResumeVO getInternalResumeByUserId(Long userId);
}

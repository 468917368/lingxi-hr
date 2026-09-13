package com.lingxi.resume.controller;

import com.lingxi.common.domain.PageRequest;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.resume.domain.dto.UpdateResumeDTO;
import com.lingxi.resume.domain.vo.ResumeDetailVO;
import com.lingxi.resume.domain.vo.ResumeVO;
import com.lingxi.resume.domain.vo.UploadResumeVO;
import com.lingxi.resume.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

/**
 * 简历管理控制器
 *
 * <p>对齐契约：《后端总体系分文档.md》4.4.2 简历管理接口。
 *
 * @author 成员C
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/api/v1/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    /**
     * 简历列表（分页）
     *
     * @param pageRequest 分页参数
     * @return 分页简历列表
     */
    @GetMapping
    public Result<PageResult<ResumeVO>> list(PageRequest pageRequest) {
        return Result.success(resumeService.listResumes(pageRequest));
    }

    /**
     * 上传简历
     *
     * @param file 简历文件（pdf/docx/doc/jpg/png，≤10MB）
     * @return 上传结果（resumeId/fileUrl/parseStatus）
     */
    @PostMapping("/upload")
    public Result<UploadResumeVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(resumeService.upload(file));
    }

    /**
     * 简历详情
     *
     * @param id 简历ID
     * @return 简历详情
     */
    @GetMapping("/{id}")
    public Result<ResumeDetailVO> detail(@PathVariable("id") Long id) {
        return Result.success(resumeService.getResumeDetail(id));
    }

    /**
     * 在线编辑保存（更新 cardStructure）
     *
     * @param id  简历ID
     * @param dto 更新内容
     * @return 无数据
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody UpdateResumeDTO dto) {
        resumeService.updateResume(id, dto);
        return Result.success();
    }

    /**
     * 删除简历（逻辑删除）
     *
     * @param id 简历ID
     * @return 无数据
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        resumeService.deleteResume(id);
        return Result.success();
    }

    /**
     * 设为默认简历
     *
     * @param id 简历ID
     * @return 无数据
     */
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable("id") Long id) {
        resumeService.setDefault(id);
        return Result.success();
    }

    /**
     * 上传/更换简历头像（JPG/PNG，≤2MB）
     *
     * @param id   简历ID
     * @param file 头像图片文件
     * @return 新的头像 presigned URL
     */
    @PutMapping("/{id}/face-photo")
    public Result<String> uploadFacePhoto(@PathVariable("id") Long id,
                                          @RequestParam("file") MultipartFile file) {
        return Result.success(resumeService.uploadFacePhoto(id, file));
    }
}

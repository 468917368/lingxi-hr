package com.lingxi.chat.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.domain.Result;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.common.util.MinioUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 消息控制器（文件上传）
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MinioUtil minioUtil;

    /**
     * 上传图片/文件
     * POST /api/v1/messages/upload
     *
     * @param file 文件
     * @return 文件信息
     */
    @RequireLogin
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        // 校验文件
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        // 校验文件大小（10MB）
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        // 生成文件名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileName = "messages/" + UUID.randomUUID().toString() + extension;

        try {
            // 上传到MinIO
            String fileUrl = minioUtil.upload(fileName, file.getInputStream(), file.getContentType());

            // 返回文件信息
            Map<String, Object> result = new HashMap<>();
            result.put("fileUrl", fileUrl);
            result.put("fileName", originalFilename);
            result.put("fileSize", file.getSize());

            log.info("文件上传成功: fileName={}, fileSize={}", originalFilename, file.getSize());
            return Result.success(result);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }
}

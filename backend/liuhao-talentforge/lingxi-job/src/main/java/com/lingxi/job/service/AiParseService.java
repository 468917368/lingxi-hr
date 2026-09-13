package com.lingxi.job.service;

import com.lingxi.job.domain.dto.request.JdParseRequest;
import com.lingxi.job.domain.dto.response.JdParseResponse;

/**
 * JD 解析服务（阶段4，F-09）
 *
 * @author 成员B
 * @since 2026-08-04
 */
public interface AiParseService {

    /**
     * 解析 JD 文本 → 岗位画像草稿
     *
     * @param request JD 解析请求
     * @return 画像草稿
     */
    JdParseResponse parseJd(JdParseRequest request);
}

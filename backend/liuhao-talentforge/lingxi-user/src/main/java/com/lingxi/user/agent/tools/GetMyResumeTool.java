package com.lingxi.user.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.domain.Result;
import com.lingxi.user.feign.ResumeFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 获取用户简历工具
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyResumeTool {

    private final ResumeFeignClient resumeFeignClient;
    private final ObjectMapper objectMapper;

    public String execute(Long userId) {
        try {
            Result<Map<String, Object>> result = resumeFeignClient.getUserResume(userId);
            return objectMapper.writeValueAsString(result.getData());
        } catch (Exception e) {
            log.error("获取简历失败: userId={}", userId, e);
            return "{\"error\": \"获取简历失败\"}";
        }
    }
}

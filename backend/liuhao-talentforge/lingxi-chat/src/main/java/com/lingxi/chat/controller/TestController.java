package com.lingxi.chat.controller;

import com.lingxi.chat.feign.UserFeignClient;
import com.lingxi.common.domain.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 测试控制器
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final UserFeignClient userFeignClient;

    /**
     * 测试Feign调用用户服务
     */
    @GetMapping("/user/{id}")
    public Result<Map<String, Object>> testGetUser(@PathVariable Long id) {
        log.info("测试Feign调用: userId={}", id);
        try {
            Result<Map<String, Object>> result = userFeignClient.getUserById(id);
            log.info("Feign调用成功: {}", result);
            return result;
        } catch (Exception e) {
            log.error("Feign调用失败", e);
            return Result.error(500, "Feign调用失败: " + e.getMessage());
        }
    }
}

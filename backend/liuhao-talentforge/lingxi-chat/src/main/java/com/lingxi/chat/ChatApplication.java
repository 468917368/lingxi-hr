package com.lingxi.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 消息服务启动类
 *
 * @author 成员A
 * @since 2026-08-03
 */
@SpringBootApplication(scanBasePackages = {"com.lingxi.chat", "com.lingxi.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.lingxi.chat.feign")
@EnableAsync
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}

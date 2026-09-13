package com.lingxi.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 用户服务启动类
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@SpringBootApplication(scanBasePackages = {"com.lingxi.user", "com.lingxi.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.lingxi.user.feign")
@MapperScan("com.lingxi.user.mapper")
@EnableAsync
@EnableScheduling
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}

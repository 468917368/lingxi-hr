package com.lingxi.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 管理服务启动类
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@SpringBootApplication(scanBasePackages = {"com.lingxi.admin", "com.lingxi.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.lingxi.admin.feign")
@MapperScan("com.lingxi.admin.mapper")
@EnableScheduling
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}

package com.lingxi.resume;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 简历服务启动类
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@SpringBootApplication(scanBasePackages = {"com.lingxi.resume", "com.lingxi.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.lingxi.resume.feign")
@MapperScan("com.lingxi.resume.mapper")
@EnableAsync
public class ResumeApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ResumeApplication.class);
        app.setAdditionalProfiles("dev");
        app.run(args);
    }
}

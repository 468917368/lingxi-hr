package com.lingxi.job;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 岗位服务启动类
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@SpringBootApplication(scanBasePackages = {"com.lingxi.job", "com.lingxi.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.lingxi.job.feign")
@MapperScan("com.lingxi.job.mapper")
@EnableScheduling
public class
JobApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobApplication.class, args);
    }
}

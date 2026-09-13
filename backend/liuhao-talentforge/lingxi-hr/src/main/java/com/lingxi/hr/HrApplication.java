package com.lingxi.hr;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * HR服务启动类
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@SpringBootApplication(scanBasePackages = {"com.lingxi.hr", "com.lingxi.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.lingxi.hr.feign", "com.lingxi.hr.agent.feign"})
@MapperScan({"com.lingxi.hr.mapper", "com.lingxi.hr.agent.mapper"})
@EnableScheduling
public class
HrApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrApplication.class, args);
    }
}

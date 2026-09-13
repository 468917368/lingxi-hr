package com.lingxi.gateway.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

/**
 * Redis 配置
 * <p>
 * 强制使用 RESP2 协议，避免 Redis 6+ 的 HELLO 命令认证问题
 * 配置从 application.yml 或 Nacos 读取
 * </p>
 *
 * @author 成员A
 * @since 2026-07-31
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory() {
        // 强制 RESP2 协议
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(5))
                .clientOptions(ClientOptions.builder()
                        .protocolVersion(ProtocolVersion.RESP2)
                        .build())
                .build();

        // 从配置文件读取（支持 Nacos 覆盖）
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
        serverConfig.setHostName(host);
        serverConfig.setPort(port);
        serverConfig.setDatabase(database);
        if (password != null && !password.isEmpty()) {
            serverConfig.setPassword(password);
        }

        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }
}

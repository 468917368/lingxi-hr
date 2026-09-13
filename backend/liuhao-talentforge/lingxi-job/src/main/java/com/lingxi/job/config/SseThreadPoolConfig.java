package com.lingxi.job.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Interview Agent SSE 线程池与并发信号量配置
 * <p>
 * 线程池：core/max/queue 走 {@code sse.thread-pool.*}（默认 10/10/10），{@code AbortPolicy}
 * （队列满直接抛 {@code RejectedExecutionException}，由调用方转 SSE error 2304）。
 * </p>
 * <p>
 * 信号量：全局并发上限走 {@code sse.concurrency}（默认 10），建流前 {@code tryAcquire()}，
 * 失败 → HTTP 503 + 2304。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Configuration
public class SseThreadPoolConfig {

    /** 核心线程数 */
    @Value("${sse.thread-pool.core:10}")
    private int corePoolSize;

    /** 最大线程数 */
    @Value("${sse.thread-pool.max:10}")
    private int maxPoolSize;

    /** 队列容量 */
    @Value("${sse.thread-pool.queue:10}")
    private int queueCapacity;

    /** Agent 并发上限 */
    @Value("${sse.concurrency:10}")
    private int concurrency;

    @Bean("agentSseExecutor")
    public ThreadPoolExecutor agentSseExecutor() {
        return new ThreadPoolExecutor(
                corePoolSize, maxPoolSize, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean("agentSseSemaphore")
    public Semaphore agentSseSemaphore() {
        return new Semaphore(concurrency);
    }
}

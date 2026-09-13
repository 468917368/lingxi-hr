package com.lingxi.resume.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简历诊断线程池配置
 *
 * <p>提供两个线程池：
 * <ul>
 *   <li>{@code diagnosisExecutor}——诊断任务执行线程池（方案C异步化：诊断任务与
 *       SSE 连接解耦，跳页不取消任务，后台继续跑完落库）</li>
 *   <li>{@code sseReaderExecutor}——百宝箱 SSE 流读取线程池（替代 {@code new Thread()}，
 *       受控线程管理，避免无限裸线程）</li>
 * </ul>
 *
 * @author 成员C
 * @since 2026-08-08
 */
@Slf4j
@Configuration
public class DiagnosisConfig {

    /**
     * 诊断任务线程池：core=2, max=4, queue=100，拒绝策略 CallerRuns（打满时由调用线程执行，不丢任务）
     *
     * <p>诊断比解析更慢（百宝箱工作流 2-5 分钟），shutdown 等待放宽到 30s。
     */
    @Bean("diagnosisExecutor")
    public ThreadPoolTaskExecutor diagnosisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("diagnosis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 百宝箱 SSE 读取线程池：core=2, max=10, 60s 回收, daemon 线程, CallerRuns
     *
     * <p>替代 RealBaibaoxiangClient.doStream 中的 {@code new Thread()}——每次诊断/解析
     * 调用一个裸线程且无上限，高并发下可创建数十线程；改为受控线程池后复用线程。
     */
    @Bean("sseReaderExecutor")
    public ExecutorService sseReaderExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 10, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                new ThreadFactory() {
                    private final AtomicInteger seq = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "baibaoxiang-sse-" + seq.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}

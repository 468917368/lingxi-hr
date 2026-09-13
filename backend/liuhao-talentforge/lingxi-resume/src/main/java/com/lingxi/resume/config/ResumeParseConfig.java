package com.lingxi.resume.config;

import com.lingxi.resume.agent.ResumeParseService;
import com.lingxi.resume.domain.entity.Resume;
import com.lingxi.resume.mapper.ResumeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 简历解析线程池 + 兜底扫描配置
 *
 * <p>提供 {@code resumeParseExecutor} 线程池供 {@code @Async} 解析任务使用；
 * 启动时与每 5 分钟对中断的解析记录（PENDING/PARSING 且 updated_at 早于 30 分钟）
 * 进行兜底重触发，保证服务重启后中断的解析能被恢复。
 *
 * @author 成员C
 * @since 2026-08-03
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ResumeParseConfig {

    /** 兜底扫描：视为中断的时长阈值（分钟） */
    private static final long STALE_THRESHOLD_MINUTES = 30;

    /** 兜底扫描周期（分钟） */
    private static final long SCAN_INTERVAL_MINUTES = 5;

    private final ResumeMapper resumeMapper;
    private final ResumeParseService resumeParseService;

    private ScheduledExecutorService scheduledExecutor;

    /**
     * 简历解析线程池：core=2, max=4, queue=100，拒绝策略 CallerRuns（打满时由调用线程执行，不丢任务）
     */
    @Bean("resumeParseExecutor")
    public ThreadPoolTaskExecutor resumeParseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("resume-parse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * 启动后延迟执行一次兜底扫描，恢复服务重启前中断的解析
     *
     * <p>注意：不能在此直接调用 {@link ResumeParseService#parseAsync}——
     * @Async 拦截器解析 executor 时会触发正在创建中的本 @Configuration（循环依赖）；
     * 延迟 2 秒到容器就绪后由调度线程执行。
     */
    @PostConstruct
    public void startRecoveryScan() {
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "resume-parse-recovery-scan");
            t.setDaemon(true);
            return t;
        });
        scheduledExecutor.schedule(this::safeRecoveryScan, 2, TimeUnit.SECONDS);
        scheduledExecutor.scheduleWithFixedDelay(this::safeRecoveryScan,
                SCAN_INTERVAL_MINUTES, SCAN_INTERVAL_MINUTES, TimeUnit.MINUTES);
        log.info("兜底扫描定时器已启动: 首扫延迟2s, 之后每{}分钟扫描一次", SCAN_INTERVAL_MINUTES);
    }

    @PreDestroy
    public void stopRecoveryScan() {
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdownNow();
        }
    }

    /**
     * 定时扫描包装：异常仅记录，不影响下一次调度
     */
    private void safeRecoveryScan() {
        try {
            recoveryScan();
        } catch (Exception e) {
            log.error("定时兜底扫描失败", e);
        }
    }

    /**
     * 兜底扫描：查询 PENDING/PARSING 且更新时间早于阈值的记录并重新触发解析
     */
    private void recoveryScan() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
        List<Resume> staleList = resumeMapper.selectStaleForParse(staleBefore);
        if (staleList.isEmpty()) {
            return;
        }
        log.info("兜底扫描发现 {} 条中断解析记录，重新触发解析", staleList.size());
        for (Resume resume : staleList) {
            try {
                resumeParseService.parseAsync(resume.getId());
            } catch (Exception e) {
                // 单个记录触发失败不影响其他记录（如线程池饱和）
                log.error("兜底重触发解析失败: resumeId={}", resume.getId(), e);
            }
        }
    }
}

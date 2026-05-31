package com.chatflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async infrastructure for the media processing pipeline (Phase 6+).
 *
 * <p>Thumbnail generation and the follow-up persistence/WebSocket push run on a
 * dedicated, bounded pool so heavy image/video work never starves request
 * threads. The pool is bounded and uses CallerRuns so a burst of uploads slows
 * down rather than exhausting memory with queued byte arrays.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "mediaProcessingExecutor", destroyMethod = "shutdown")
    public Executor mediaProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("media-proc-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

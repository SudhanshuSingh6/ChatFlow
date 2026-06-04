package com.chatflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService typingTimerExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "typing-timer");
            t.setDaemon(true);
            return t;
        });
    }
}
package com.miniESB.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Active l'exécution asynchrone (@Async) nécessaire pour le streaming SSE
 * des logs de build Docker sans bloquer le thread HTTP (tâche 5.4).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "buildSseExecutor")
    public Executor buildSseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("build-sse-");
        executor.initialize();
        return executor;
    }
}
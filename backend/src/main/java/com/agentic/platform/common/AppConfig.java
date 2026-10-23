package com.agentic.platform.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Core application beans: typed configuration, bounded async executors, and the
 * OpenAPI document definition.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    /**
     * Bounded executor for best-effort analytics persistence. A bounded queue with a
     * caller-runs policy prevents unbounded memory growth and retry storms.
     */
    @Bean(name = "analyticsExecutor")
    public Executor analyticsExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(500);
        ex.setThreadNamePrefix("analytics-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }

    /**
     * Bounded executor service used by the orchestration scheduler to run independent
     * tasks in parallel. A fixed pool caps concurrent task execution; a caller-runs
     * policy on saturation prevents unbounded queue growth.
     */
    @Bean(name = "orchestrationExecutor")
    public ExecutorService orchestrationExecutor(AppProperties props) {
        int max = Math.max(2, props.getOrchestration().getMaxParallelTasks());
        ThreadPoolExecutor ex = new ThreadPoolExecutor(
                max, max, 60L, java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("orch-" + t.threadId());
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        return ex;
    }

    @Bean
    public OpenAPI platformOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Agentic SDLC Platform API")
                .version("0.1.0")
                .description("URL Shortener + Agentic SDLC Orchestration Engine")
                .license(new License().name("Prototype").url("about:blank")));
    }
}

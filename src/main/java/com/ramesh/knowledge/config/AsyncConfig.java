package com.ramesh.knowledge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Streaming answers run on their own threads so the servlet thread can return
 * the SSE emitter immediately. Virtual threads keep this cheap: each answer
 * stream is mostly waiting on model output.
 */
@Configuration
public class AsyncConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService answerStreamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

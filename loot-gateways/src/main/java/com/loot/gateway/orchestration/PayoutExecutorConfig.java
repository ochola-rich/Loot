package com.loot.gateway.orchestration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class PayoutExecutorConfig {

    @Bean(destroyMethod = "close")
    public ExecutorService payoutExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

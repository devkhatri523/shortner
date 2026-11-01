package com.url.shortner.orchestrator;

import com.url.shortner.orchestrator.config.AgentSettings;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
@EnableConfigurationProperties(AgentSettings.class)
public class OrchestratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService workflowExecutor() {
        return Executors.newFixedThreadPool(2);
    }
}

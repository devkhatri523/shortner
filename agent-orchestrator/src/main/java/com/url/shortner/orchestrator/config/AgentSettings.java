package com.url.shortner.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "agent")
public record AgentSettings(
        Path sourceRoot,
        Path workspaceRoot,
        int maxFixAttempts,
        Duration testTimeout,
        String dockerImage,
        String dockerUser,
        Path mavenCache) {

    public AgentSettings {
        if (maxFixAttempts < 0 || maxFixAttempts > 10) {
            throw new IllegalArgumentException("agent.max-fix-attempts must be 0..10");
        }
        if (testTimeout == null || testTimeout.isNegative() || testTimeout.isZero()) {
            throw new IllegalArgumentException("agent.test-timeout must be positive");
        }
    }
}

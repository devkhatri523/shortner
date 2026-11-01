package com.url.shortner.config;

import com.url.shortner.dto.ShortenerProperties;
import com.url.shortner.exception.ShortCodeCollisionException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class CodeRetryConfiguration {
    @Bean
    RetryTemplate shortCodeRetryTemplate(ShortenerProperties properties) {
        return RetryTemplate.builder()
                .maxAttempts(properties.code().maxAttempts())
                .fixedBackoff(properties.code().retryBackoff().toMillis())
                .retryOn(ShortCodeCollisionException.class)
                .build();
    }
}

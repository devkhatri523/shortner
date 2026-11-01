package com.url.shortner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "shortener")
public record ShortenerProperties(
        @NotNull URI baseUrl,
        @NotNull @Valid Code code,
        @NotNull @Valid Cache cache) {

    public ShortenerProperties {
        if (baseUrl != null &&
                (!"http".equalsIgnoreCase(baseUrl.getScheme())
                    && !"https".equalsIgnoreCase(baseUrl.getScheme())
                 || baseUrl.getHost() == null
                 || baseUrl.getRawUserInfo() != null
                 || baseUrl.getRawQuery() != null
                 || baseUrl.getRawFragment() != null)) {
            throw new IllegalArgumentException("shortener.base-url must be an HTTP(S) base URL");
        }
    }

    public record Code(
            @Min(4) @Max(32) int length,
            @Min(1) int maxAttempts,
            @NotNull Duration retryBackoff) {
        public Code {
            if (retryBackoff != null && (retryBackoff.isNegative() || retryBackoff.isZero())) {
                throw new IllegalArgumentException("Retry backoff must be positive");
            }
        }
    }
    public record Cache(@NotNull Duration ttl) {
        public Cache {
            if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
                throw new IllegalArgumentException("Cache TTL must be positive");
            }
        }
    }
}

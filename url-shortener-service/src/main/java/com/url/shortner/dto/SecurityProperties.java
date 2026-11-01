package com.url.shortner.dto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "shortener.security")
public record SecurityProperties(
        @NotBlank String encryptionKey,
        @NotBlank String hmacKey) {
}

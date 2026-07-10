package com.church.operation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "church.password-reset")
public record PasswordResetProperties(
    String frontendBaseUrl,
    Duration tokenLifetime,
    String fromAddress
) {
}

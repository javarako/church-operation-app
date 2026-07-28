package com.church.operation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "church.runtime-email")
public record RuntimeEmailProperties(
    String host,
    int port,
    String username,
    String password,
    boolean auth,
    boolean startTls,
    String fromAddress,
    String encryptionKey,
    Duration verificationLifetime
) {
}

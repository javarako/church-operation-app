package com.church.operation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "church")
public record ChurchTimeZoneProperties(String timeZone) {
}

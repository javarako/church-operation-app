package com.church.operation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "church.fiscal-year")
public record FiscalYearDefaultsProperties(int startMonth) {
}

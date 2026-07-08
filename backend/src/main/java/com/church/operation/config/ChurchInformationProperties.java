package com.church.operation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "church")
public record ChurchInformationProperties(
    Information information,
    Branding branding
) {
    public record Information(String name, String address, String contactInfo, String treasurerName) {
    }

    public record Branding(String bannerPath, String logPath) {
    }
}

package com.church.operation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "church")
public record ChurchInformationProperties(
    Information information,
    Branding branding,
    Ui ui
) {
    public record Information(
        String name,
        String address,
        String contactInfo,
        String treasurerName,
        String charityRegistrationNumber,
        String receiptIssueLocation,
        String website
    ) {
    }

    public record Branding(String bannerPath, String logPath) {
    }

    public record Ui(int listPageSize) {
    }
}

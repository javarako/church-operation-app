package com.church.operation.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ChurchInformationPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfiguration.class)
        .withPropertyValues(
            "church.information.name=Grace Community Church",
            "church.information.address=123 Church Street, Toronto, ON",
            "church.information.contact-info=416-555-0100",
            "church.information.treasurer-name=Daniel Kim",
            "church.information.charity-registration-number=123456789RR0001",
            "church.information.receipt-issue-location=Toronto, Ontario",
            "church.information.website=https://grace.example.org",
            "church.branding.banner-path=/branding/banner.png",
            "church.branding.log-path=/branding/logo.png",
            "church.ui.list-page-size=20"
        );

    @Test
    void bindsOfficialReceiptChurchInformation() {
        contextRunner.run(context -> {
            ChurchInformationProperties.Information information = context
                .getBean(ChurchInformationProperties.class)
                .information();

            assertThat(information.charityRegistrationNumber()).isEqualTo("123456789RR0001");
            assertThat(information.receiptIssueLocation()).isEqualTo("Toronto, Ontario");
            assertThat(information.website()).isEqualTo("https://grace.example.org");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ChurchInformationProperties.class)
    static class TestConfiguration {
    }
}

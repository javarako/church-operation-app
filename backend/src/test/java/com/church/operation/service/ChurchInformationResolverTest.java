package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.entity.ChurchSettings;
import com.church.operation.repo.ChurchSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChurchInformationResolverTest {
    @Mock private ChurchSettingsRepository repository;

    private ChurchInformationProperties properties;
    private ChurchInformationResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new ChurchInformationProperties(
            new ChurchInformationProperties.Information(
                "Default Church",
                "Default Address",
                "Default Contact",
                "Default Treasurer",
                "Default Charity Number",
                "Default Issue Location",
                "https://default.example.org"
            ),
            new ChurchInformationProperties.Branding(
                "/branding/default-banner.png",
                "/branding/default-logo.png"
            ),
            new ChurchInformationProperties.Ui(20)
        );
        resolver = new ChurchInformationResolver(properties, repository);
    }

    @Test
    void returnsConfiguredDefaultsWhenNoSettingsAreSaved() {
        when(repository.findById(ChurchSettings.SINGLETON_ID)).thenReturn(Optional.empty());

        EffectiveChurchInformation result = resolver.resolve();

        assertThat(result.name()).isEqualTo("Default Church");
        assertThat(result.address()).isEqualTo("Default Address");
        assertThat(result.contactInfo()).isEqualTo("Default Contact");
        assertThat(result.treasurerName()).isEqualTo("Default Treasurer");
        assertThat(result.charityRegistrationNumber()).isEqualTo("Default Charity Number");
        assertThat(result.receiptIssueLocation()).isEqualTo("Default Issue Location");
        assertThat(result.website()).isEqualTo("https://default.example.org");
        assertThat(result.logoUrl()).isEqualTo("/branding/default-logo.png");
        assertThat(result.bannerUrl()).isEqualTo("/branding/default-banner.png");
        assertThat(result.updatedAt()).isNull();
    }

    @Test
    void savedValuesOverrideDefaultsAndBlankValuesFallBackByField() {
        ChurchSettings saved = new ChurchSettings();
        saved.setName("  Runtime Church  ");
        saved.setAddress(" ");
        saved.setContactInfo("Runtime Contact");
        saved.setTreasurerName("");
        saved.setCharityRegistrationNumber("Runtime Charity Number");
        saved.setReceiptIssueLocation("Runtime Issue Location");
        saved.setWebsite("https://runtime.example.org");
        saved.setUpdatedAt(Instant.parse("2026-07-26T16:00:00Z"));
        when(repository.findById(ChurchSettings.SINGLETON_ID)).thenReturn(Optional.of(saved));

        EffectiveChurchInformation result = resolver.resolve();

        assertThat(result.name()).isEqualTo("Runtime Church");
        assertThat(result.address()).isEqualTo("Default Address");
        assertThat(result.contactInfo()).isEqualTo("Runtime Contact");
        assertThat(result.treasurerName()).isEqualTo("Default Treasurer");
        assertThat(result.charityRegistrationNumber()).isEqualTo("Runtime Charity Number");
        assertThat(result.receiptIssueLocation()).isEqualTo("Runtime Issue Location");
        assertThat(result.website()).isEqualTo("https://runtime.example.org");
        assertThat(result.updatedAt()).isEqualTo(saved.getUpdatedAt());
    }

    @Test
    void savedBrandingUsesVersionedPublicUrls() {
        ChurchSettings saved = new ChurchSettings();
        saved.setLogoGridFsId("logo-id");
        saved.setBannerGridFsId("banner-id");
        saved.setUpdatedAt(Instant.parse("2026-07-26T16:00:00Z"));
        when(repository.findById(ChurchSettings.SINGLETON_ID)).thenReturn(Optional.of(saved));

        EffectiveChurchInformation result = resolver.resolve();

        assertThat(result.logoUrl()).isEqualTo("/api/church-information/logo?v=1785081600000");
        assertThat(result.bannerUrl()).isEqualTo("/api/church-information/banner?v=1785081600000");
        assertThat(resolver.savedSettings()).containsSame(saved);
    }
}

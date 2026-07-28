package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.entity.ChurchSettings;
import com.church.operation.repo.ChurchSettingsRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class ChurchInformationResolver {
    private static final String LOGO_ENDPOINT = "/api/church-information/logo";
    private static final String BANNER_ENDPOINT = "/api/church-information/banner";

    private final ChurchInformationProperties properties;
    private final ChurchSettingsRepository repository;

    public ChurchInformationResolver(
        ChurchInformationProperties properties,
        ChurchSettingsRepository repository
    ) {
        this.properties = properties;
        this.repository = repository;
    }

    public EffectiveChurchInformation resolve() {
        Optional<ChurchSettings> saved = savedSettings();
        ChurchSettings settings = saved.orElse(null);
        ChurchInformationProperties.Information defaults = properties.information();
        Instant updatedAt = settings == null ? null : settings.getUpdatedAt();

        return new EffectiveChurchInformation(
            effective(settings == null ? null : settings.getName(), defaults.name()),
            effective(settings == null ? null : settings.getAddress(), defaults.address()),
            effective(settings == null ? null : settings.getContactInfo(), defaults.contactInfo()),
            effective(settings == null ? null : settings.getTreasurerName(), defaults.treasurerName()),
            effective(settings == null ? null : settings.getCharityRegistrationNumber(), defaults.charityRegistrationNumber()),
            effective(settings == null ? null : settings.getReceiptIssueLocation(), defaults.receiptIssueLocation()),
            effective(settings == null ? null : settings.getWebsite(), defaults.website()),
            brandingUrl(settings == null ? null : settings.getLogoGridFsId(), LOGO_ENDPOINT,
                properties.branding().logPath(), updatedAt),
            brandingUrl(settings == null ? null : settings.getBannerGridFsId(), BANNER_ENDPOINT,
                properties.branding().bannerPath(), updatedAt),
            updatedAt
        );
    }

    public Optional<ChurchSettings> savedSettings() {
        return repository.findById(ChurchSettings.SINGLETON_ID);
    }

    private String effective(String savedValue, String defaultValue) {
        return savedValue == null || savedValue.isBlank() ? defaultValue : savedValue.trim();
    }

    private String brandingUrl(String gridFsId, String endpoint, String defaultPath, Instant updatedAt) {
        if (gridFsId == null || gridFsId.isBlank()) {
            return defaultPath;
        }
        long version = updatedAt == null ? 0 : updatedAt.toEpochMilli();
        return endpoint + "?v=" + version;
    }
}

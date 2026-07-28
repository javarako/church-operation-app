package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.config.ChurchTimeZoneDefaultsProperties;
import com.church.operation.config.DataManagementProperties;
import com.church.operation.config.FiscalYearDefaultsProperties;
import com.church.operation.entity.ChurchSettings;
import com.church.operation.repo.ChurchSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Optional;

@Service
public class RuntimeOperationalSettings {
    private final ChurchSettingsRepository repository;
    private final ZoneId defaultTimeZone;
    private final int defaultFiscalYearStartMonth;
    private final int defaultListPageSize;
    private final Duration defaultDataOperationExpiry;

    public RuntimeOperationalSettings(
        ChurchSettingsRepository repository,
        ChurchTimeZoneDefaultsProperties timeZoneProperties,
        FiscalYearDefaultsProperties fiscalYearProperties,
        ChurchInformationProperties informationProperties,
        DataManagementProperties dataManagementProperties
    ) {
        this.repository = repository;
        this.defaultTimeZone = ZoneId.of(timeZoneProperties.timeZone());
        this.defaultFiscalYearStartMonth = fiscalYearProperties.startMonth();
        this.defaultListPageSize = informationProperties.ui().listPageSize();
        this.defaultDataOperationExpiry = dataManagementProperties.operationExpiry();
    }

    public EffectiveSettings resolve() {
        Optional<ChurchSettings> saved = repository.findById(ChurchSettings.SINGLETON_ID);
        return new EffectiveSettings(
            saved.map(ChurchSettings::getTimeZone)
                .filter(StringUtils::hasText)
                .map(ZoneId::of)
                .orElse(defaultTimeZone),
            saved.map(ChurchSettings::getFiscalYearStartMonth)
                .orElse(defaultFiscalYearStartMonth),
            saved.map(ChurchSettings::getListPageSize)
                .orElse(defaultListPageSize),
            saved.map(ChurchSettings::getDataOperationExpiry)
                .orElse(defaultDataOperationExpiry)
        );
    }

    public ZoneId timeZone() {
        return resolve().timeZone();
    }

    public int fiscalYearStartMonth() {
        return resolve().fiscalYearStartMonth();
    }

    public int listPageSize() {
        return resolve().listPageSize();
    }

    public Duration dataOperationExpiry() {
        return resolve().dataOperationExpiry();
    }

    public record EffectiveSettings(
        ZoneId timeZone,
        int fiscalYearStartMonth,
        int listPageSize,
        Duration dataOperationExpiry
    ) {
    }
}

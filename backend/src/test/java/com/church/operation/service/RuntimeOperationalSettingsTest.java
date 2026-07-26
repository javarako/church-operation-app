package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.config.ChurchTimeZoneDefaultsProperties;
import com.church.operation.config.DataManagementProperties;
import com.church.operation.config.FiscalYearDefaultsProperties;
import com.church.operation.entity.ChurchSettings;
import com.church.operation.repo.ChurchSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeOperationalSettingsTest {
    @Mock private ChurchSettingsRepository repository;

    private RuntimeOperationalSettings resolver;

    @BeforeEach
    void setUp() {
        ChurchInformationProperties information = new ChurchInformationProperties(
            null,
            null,
            new ChurchInformationProperties.Ui(20)
        );
        DataManagementProperties dataManagement = new DataManagementProperties(
            Path.of("build/test-data"), Duration.ofMinutes(30), DataSize.ofGigabytes(2)
        );
        resolver = new RuntimeOperationalSettings(
            repository,
            new ChurchTimeZoneDefaultsProperties("America/Toronto"),
            new FiscalYearDefaultsProperties(1),
            information,
            dataManagement
        );
    }

    @Test
    void resolvesSavedOperationalOverridesWithoutRestart() {
        ChurchSettings saved = new ChurchSettings();
        saved.setTimeZone("America/Vancouver");
        saved.setFiscalYearStartMonth(4);
        saved.setListPageSize(50);
        saved.setDataOperationExpiry(Duration.ofMinutes(60));
        when(repository.findById(ChurchSettings.SINGLETON_ID)).thenReturn(Optional.of(saved));

        assertThat(resolver.timeZone()).isEqualTo(ZoneId.of("America/Vancouver"));
        assertThat(resolver.fiscalYearStartMonth()).isEqualTo(4);
        assertThat(resolver.listPageSize()).isEqualTo(50);
        assertThat(resolver.dataOperationExpiry()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void fallsBackPerFieldWhenSavedOverrideIsMissing() {
        ChurchSettings saved = new ChurchSettings();
        saved.setListPageSize(40);
        when(repository.findById(ChurchSettings.SINGLETON_ID)).thenReturn(Optional.of(saved));

        assertThat(resolver.timeZone()).isEqualTo(ZoneId.of("America/Toronto"));
        assertThat(resolver.fiscalYearStartMonth()).isEqualTo(1);
        assertThat(resolver.listPageSize()).isEqualTo(40);
        assertThat(resolver.dataOperationExpiry()).isEqualTo(Duration.ofMinutes(30));
    }
}

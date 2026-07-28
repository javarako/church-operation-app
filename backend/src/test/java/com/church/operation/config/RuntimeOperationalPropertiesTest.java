package com.church.operation.config;

import com.church.operation.service.RuntimeOperationalSettings;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeOperationalPropertiesTest {
    @Test
    void fiscalYearProviderReadsCurrentRuntimeValueOnEveryCall() {
        RuntimeOperationalSettings settings = mock(RuntimeOperationalSettings.class);
        when(settings.fiscalYearStartMonth()).thenReturn(1, 4);
        FiscalYearProperties properties = new FiscalYearProperties(settings);

        assertThat(properties.startMonth()).isEqualTo(1);
        assertThat(properties.startMonth()).isEqualTo(4);
    }

    @Test
    void timeZoneProviderReadsCurrentRuntimeValueOnEveryCall() {
        RuntimeOperationalSettings settings = mock(RuntimeOperationalSettings.class);
        when(settings.timeZone()).thenReturn(
            ZoneId.of("America/Toronto"), ZoneId.of("America/Vancouver")
        );
        ChurchTimeZoneProperties properties = new ChurchTimeZoneProperties(settings);

        assertThat(properties.zoneId()).isEqualTo(ZoneId.of("America/Toronto"));
        assertThat(properties.zoneId()).isEqualTo(ZoneId.of("America/Vancouver"));
    }
}

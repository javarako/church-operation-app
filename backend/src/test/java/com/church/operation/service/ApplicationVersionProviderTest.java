package com.church.operation.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationVersionProviderTest {
    @Test
    void returnsBuildVersionWhenMetadataIsAvailable() {
        @SuppressWarnings("unchecked")
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        Properties values = new Properties();
        values.setProperty("version", "1.2.3");
        when(provider.getIfAvailable()).thenReturn(new BuildProperties(values));

        assertThat(new ApplicationVersionProvider(provider, "development").version()).isEqualTo("1.2.3");
    }

    @Test
    void returnsConfiguredFallbackDuringDevelopment() {
        @SuppressWarnings("unchecked")
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        assertThat(new ApplicationVersionProvider(provider, "1.0.0-SNAPSHOT").version())
            .isEqualTo("1.0.0-SNAPSHOT");
    }
}

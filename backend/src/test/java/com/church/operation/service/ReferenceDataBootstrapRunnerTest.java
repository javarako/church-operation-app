package com.church.operation.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReferenceDataBootstrapRunnerTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class);

    @Test
    void createsRunnerWhenDefaultSeedingIsEnabled() {
        contextRunner
            .withPropertyValues("church.reference-data.seed-defaults=true")
            .run(context -> assertThat(context)
                .hasSingleBean(ReferenceDataBootstrapRunner.class));
    }

    @Test
    void doesNotCreateRunnerWhenDefaultSeedingIsDisabled() {
        contextRunner
            .withPropertyValues("church.reference-data.seed-defaults=false")
            .run(context -> assertThat(context)
                .doesNotHaveBean(ReferenceDataBootstrapRunner.class));
    }

    @Test
    void doesNotCreateRunnerWhenPropertyIsMissing() {
        contextRunner.run(context -> assertThat(context)
            .doesNotHaveBean(ReferenceDataBootstrapRunner.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import(ReferenceDataBootstrapRunner.class)
    static class TestConfig {
        @Bean
        ReferenceDataService referenceDataService() {
            return mock(ReferenceDataService.class);
        }
    }
}

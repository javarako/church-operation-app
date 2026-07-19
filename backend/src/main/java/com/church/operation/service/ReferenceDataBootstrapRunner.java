package com.church.operation.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@ConditionalOnProperty(
    prefix = "church.reference-data",
    name = "seed-defaults",
    havingValue = "true",
    matchIfMissing = false
)
public class ReferenceDataBootstrapRunner implements ApplicationRunner {
    private final ReferenceDataService referenceDataService;

    public ReferenceDataBootstrapRunner(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @Override
    public void run(ApplicationArguments args) {
        referenceDataService.seedDefaults();
    }
}

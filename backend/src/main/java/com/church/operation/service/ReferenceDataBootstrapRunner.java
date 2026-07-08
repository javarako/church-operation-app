package com.church.operation.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
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

package com.church.operation.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5)
public class OfferingHierarchyMigrationRunner implements ApplicationRunner {
    private final OfferingHierarchyMigrationService migrationService;

    public OfferingHierarchyMigrationRunner(OfferingHierarchyMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrationService.migrate();
    }
}

package com.church.operation.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MongoRestoreNamespacePolicyTest {
    private final MongoRestoreNamespacePolicy policy = new MongoRestoreNamespacePolicy("restore_db");

    @ParameterizedTest
    @ValueSource(strings = {
        "system.profile",
        "members.system.profile",
        MongoRestoreNamespacePolicy.RESTORE_STAGING_PREFIX + "pending",
        MongoRestoreNamespacePolicy.RESTORE_BACKUP_PREFIX + "previous"
    })
    void rejectsReservedRestoreNamespaces(String name) {
        assertThat(policy.isRestorable(name)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"members", "members.archive", "ordinary.dotted.name"})
    void acceptsOrdinaryNamespacesIncludingDottedNames(String name) {
        assertThat(policy.isRestorable(name)).isTrue();
    }
}

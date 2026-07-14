package com.church.operation.service;

import com.mongodb.client.MongoDatabase;
import org.bson.RawBsonDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoRestoreCatalogServiceTest {
    @Test
    void preservesPrimaryFailureAndKnownRecoveryNamesWhenEvidenceCatalogFails() {
        MongoDatabase database = mock(MongoDatabase.class);
        IllegalStateException primary = new IllegalStateException("cutover failed");
        IllegalStateException evidenceFailure = new IllegalStateException("catalog unavailable");
        when(database.listCollections(RawBsonDocument.class)).thenThrow(evidenceFailure);
        MongoRestoreCatalogService catalog = new MongoRestoreCatalogService(database);

        RestoreCutoverException failure = catalog.cutoverFailure(
            "maintenance required",
            primary,
            List.of("staging_members"),
            List.of("backup_members")
        );

        assertThat(failure.getCause()).isSameAs(primary);
        assertThat(failure.maintenanceRequired()).isTrue();
        assertThat(failure.stagingNamespaces()).containsExactly("staging_members");
        assertThat(failure.backupNamespaces()).containsExactly("backup_members");
        assertThat(primary.getSuppressed()).containsExactly(evidenceFailure);
    }
}

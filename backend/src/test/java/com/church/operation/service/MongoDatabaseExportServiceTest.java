package com.church.operation.service;

import com.church.operation.config.DataManagementProperties;
import com.mongodb.client.MongoDatabase;
import org.bson.RawBsonDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoDatabaseExportServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void preservesPrimaryExportFailureAndSuppressesWorkingDirectoryCleanupFailure() {
        MongoDatabase database = mock(MongoDatabase.class);
        IllegalStateException primary = new IllegalStateException("catalog failed");
        IOException cleanupFailure = new IOException("cleanup failed");
        when(database.listCollections(RawBsonDocument.class)).thenThrow(primary);
        DataManagementProperties properties = new DataManagementProperties(
            tempDirectory.resolve("operations"),
            Duration.ofMinutes(30),
            DataSize.ofMegabytes(10)
        );
        MongoDatabaseExportService exporter = new MongoDatabaseExportService(
            database,
            new ArchivePackageService(properties),
            properties,
            ignored -> { throw cleanupFailure; }
        );

        assertThatThrownBy(() -> exporter.exportFull(
            tempDirectory.resolve("backup.zip"), "password".toCharArray()
        )).isSameAs(primary)
            .satisfies(failure -> assertThat(failure.getSuppressed()).containsExactly(cleanupFailure));
    }
}

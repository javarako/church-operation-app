package com.church.operation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataOperationStoreTest {
    @TempDir Path tempDirectory;

    @Test
    void newOperationsUseCurrentExpiryWithoutChangingExistingDeadline() throws Exception {
        Instant now = Instant.parse("2026-07-26T18:00:00Z");
        RuntimeOperationalSettings settings = mock(RuntimeOperationalSettings.class);
        when(settings.dataOperationExpiry()).thenReturn(
            Duration.ofMinutes(30), Duration.ofMinutes(60)
        );
        DataOperationStore store = new DataOperationStore(
            settings, Clock.fixed(now, ZoneOffset.UTC)
        );

        Path firstArchive = Files.createFile(tempDirectory.resolve("first.zip"));
        DataOperationStore.Operation first = store.create(
            "admin", firstArchive, mock(DataManagementService.RestoreSession.class), 1, 2, 3
        );
        store.complete(first);
        Path secondArchive = Files.createFile(tempDirectory.resolve("second.zip"));
        DataOperationStore.Operation second = store.create(
            "admin", secondArchive, mock(DataManagementService.RestoreSession.class), 1, 2, 3
        );

        assertThat(first.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
        assertThat(second.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(60)));
    }
}

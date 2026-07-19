package com.church.operation.service;

import com.church.operation.config.DataManagementProperties;
import com.church.operation.dto.ArchiveManifest;
import com.church.operation.dto.DataOperationResponse;
import com.church.operation.entity.Member;
import com.church.operation.util.ArchiveType;
import com.church.operation.util.DataOperationStatus;
import com.church.operation.util.Role;
import com.church.operation.util.SystemAuditOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataManagementServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

    @TempDir
    Path tempDirectory;

    private final MongoDatabaseExportService exporter = mock(MongoDatabaseExportService.class);
    private final AuthTokenService authTokenService = mock(AuthTokenService.class);
    private final DataManagementService.RestoreSession restoreSession =
        mock(DataManagementService.RestoreSession.class);
    private final SystemAuditService audit = mock(SystemAuditService.class);
    private DataOperationStore store;
    private MaintenanceModeService maintenanceMode;
    private DataManagementService service;
    private Member admin;

    @BeforeEach
    void setUp() throws Exception {
        DataManagementProperties properties = new DataManagementProperties(
            tempDirectory, Duration.ofMinutes(30), DataSize.ofMegabytes(20)
        );
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        store = new DataOperationStore(properties, clock);
        maintenanceMode = new MaintenanceModeService();
        service = new DataManagementService(
            exporter, (archive, password) -> restoreSession,
            authTokenService, store, maintenanceMode, properties, audit, clock
        );
        admin = member("admin-1", Role.ADMIN);

        when(restoreSession.manifest()).thenReturn(new ArchiveManifest(1, ArchiveType.FULL_BACKUP, List.of()));
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(0), "backup");
            return new ArchiveManifest(1, ArchiveType.FULL_BACKUP, List.of());
        }).when(exporter).exportFull(any(Path.class), any(char[].class));
        when(restoreSession.replaceAll()).thenReturn(new RestoreVerification(4, 25, 3));
    }

    @Test
    void requiresSafetyBackupBeforeRestoreAndCompletesTheStagedFlow() throws Exception {
        Path upload = Files.writeString(tempDirectory.resolve("restore.zip"), "archive");
        char[] restorePassword = "restore-secret".toCharArray();

        DataOperationResponse validated = service.validateRestore(admin, upload, restorePassword);

        assertThat(validated.status()).isEqualTo(DataOperationStatus.VALIDATED);
        assertThat(restorePassword).containsOnly('\0');
        assertThatThrownBy(() -> service.executeRestore(admin, validated.id(), "RESTORE FULL DATABASE"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("safety backup");
        verify(restoreSession, never()).replaceAll();

        char[] safetyPassword = "safety-secret".toCharArray();
        try (DataManagementService.DownloadArtifact safety =
                 service.createSafetyBackup(admin, validated.id(), safetyPassword)) {
            assertThat(Files.readString(safety.path())).isEqualTo("backup");
        }
        assertThat(safetyPassword).containsOnly('\0');
        assertThat(service.status(admin, validated.id()).status())
            .isEqualTo(DataOperationStatus.SAFETY_BACKUP_DOWNLOADED);

        DataOperationResponse completed = service.executeRestore(
            admin, validated.id(), "RESTORE FULL DATABASE"
        );

        assertThat(completed.status()).isEqualTo(DataOperationStatus.COMPLETE);
        assertThat(completed.documentCount()).isEqualTo(25);
        assertThat(maintenanceMode.isActive()).isFalse();
        verify(authTokenService).revokeAll();
        verify(audit).recordSuccess(eq(admin), eq(SystemAuditOperation.FULL_RESTORE_VALIDATE), anyMap());
        verify(audit).recordSuccess(eq(admin), eq(SystemAuditOperation.FULL_RESTORE_SAFETY_BACKUP), anyMap());
        verify(audit).recordSuccess(eq(admin), eq(SystemAuditOperation.FULL_RESTORE_EXECUTE), anyMap());
    }

    @Test
    void rejectsWrongActorWrongPhraseAndConcurrentMutation() throws Exception {
        Path upload = Files.writeString(tempDirectory.resolve("restore.zip"), "archive");
        DataOperationResponse operation = service.validateRestore(admin, upload, "secret".toCharArray());
        Member otherAdmin = member("admin-2", Role.ADMIN);

        assertThatThrownBy(() -> service.status(otherAdmin, operation.id()))
            .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.executeRestore(admin, operation.id(), "restore"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("confirmation");
        assertThatThrownBy(() -> service.validateRestore(
            admin, Files.writeString(tempDirectory.resolve("second.zip"), "archive"), "secret".toCharArray()
        )).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already active");
    }

    @Test
    void failedReplacementKeepsMaintenanceModeActive() throws Exception {
        Path upload = Files.writeString(tempDirectory.resolve("restore.zip"), "archive");
        DataOperationResponse operation = service.validateRestore(admin, upload, "secret".toCharArray());
        try (DataManagementService.DownloadArtifact ignored =
                 service.createSafetyBackup(admin, operation.id(), "safety".toCharArray())) {
            // Mark the mandatory safety download complete.
        }
        when(restoreSession.replaceAll()).thenThrow(new IllegalStateException("cutover failed"));

        assertThatThrownBy(() -> service.executeRestore(admin, operation.id(), "RESTORE FULL DATABASE"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cutover failed");

        assertThat(maintenanceMode.isActive()).isTrue();
        assertThat(service.status(admin, operation.id()).status())
            .isEqualTo(DataOperationStatus.FAILED_MAINTENANCE);
        verify(authTokenService, never()).revokeAll();
        verify(audit).recordFailure(
            org.mockito.ArgumentMatchers.eq(admin),
            org.mockito.ArgumentMatchers.eq(SystemAuditOperation.FULL_RESTORE_EXECUTE),
            anyMap(),
            any(IllegalStateException.class)
        );
    }

    private Member member(String id, Role role) {
        Member member = new Member();
        member.setId(id);
        member.setPrimaryEmail(id + "@example.test");
        member.setRoles(Set.of(role));
        return member;
    }
}

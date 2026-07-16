package com.church.operation.service;

import com.church.operation.config.DataManagementProperties;
import com.church.operation.dto.ArchiveCollectionManifest;
import com.church.operation.dto.ArchiveManifest;
import com.church.operation.dto.DataOperationResponse;
import com.church.operation.entity.Member;
import com.church.operation.util.DataOperationStatus;
import com.church.operation.util.Role;
import com.church.operation.util.SystemAuditOperation;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

@Service
public class DataManagementService {
    public static final String RESTORE_CONFIRMATION = "RESTORE FULL DATABASE";

    private final MongoDatabaseExportService exporter;
    private final RestoreValidator restoreValidator;
    private final AuthTokenService authTokenService;
    private final DataOperationStore operationStore;
    private final MaintenanceModeService maintenanceMode;
    private final DataManagementProperties properties;
    private final SystemAuditService audit;
    private final Clock clock;

    @Autowired
    public DataManagementService(
        MongoDatabaseExportService exporter,
        MongoDatabaseImportService importer,
        AuthTokenService authTokenService,
        DataOperationStore operationStore,
        MaintenanceModeService maintenanceMode,
        DataManagementProperties properties,
        SystemAuditService audit
    ) {
        this(
            exporter,
            (archive, password) -> {
                MongoDatabaseImportService.ValidatedArchive validated = importer.validateFull(archive, password);
                return new RestoreSession() {
                    @Override
                    public ArchiveManifest manifest() {
                        return validated.manifest();
                    }

                    @Override
                    public RestoreVerification replaceAll() throws IOException {
                        return importer.replaceAll(validated);
                    }

                    @Override
                    public void close() throws IOException {
                        validated.close();
                    }
                };
            },
            authTokenService,
            operationStore,
            maintenanceMode,
            properties,
            audit,
            Clock.systemUTC()
        );
    }

    DataManagementService(
        MongoDatabaseExportService exporter,
        RestoreValidator restoreValidator,
        AuthTokenService authTokenService,
        DataOperationStore operationStore,
        MaintenanceModeService maintenanceMode,
        DataManagementProperties properties,
        SystemAuditService audit,
        Clock clock
    ) {
        this.exporter = exporter;
        this.restoreValidator = restoreValidator;
        this.authTokenService = authTokenService;
        this.operationStore = operationStore;
        this.maintenanceMode = maintenanceMode;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    public DataOperationResponse validateRestore(Member actor, Path uploadedArchive, char[] password) throws IOException {
        RestoreSession validated = null;
        try {
            requireAdmin(actor);
            requirePassword(password);
            Objects.requireNonNull(uploadedArchive, "Uploaded archive is required.");
            validated = restoreValidator.validate(uploadedArchive, password);
            ArchiveManifest manifest = validated.manifest();
            long collections = manifest.collections().stream()
                .map(ArchiveCollectionManifest::collection)
                .distinct()
                .count();
            long documents = manifest.collections().stream()
                .filter(entry -> entry.entryName().endsWith("/data.bson"))
                .mapToLong(ArchiveCollectionManifest::documentCount)
                .sum();
            long indexes = manifest.collections().stream()
                .filter(entry -> entry.entryName().endsWith("/indexes.bson"))
                .mapToLong(ArchiveCollectionManifest::documentCount)
                .sum();
            DataOperationResponse response = operationStore.create(
                actor.getId(), uploadedArchive, validated, collections, documents, indexes
            ).response();
            audit.recordSuccess(actor, SystemAuditOperation.FULL_RESTORE_VALIDATE, Map.of(
                "operationId", response.id(), "recordCount", documents
            ));
            return response;
        } catch (IOException | RuntimeException | Error exception) {
            if (validated != null) {
                try {
                    validated.close();
                } catch (IOException closeFailure) {
                    exception.addSuppressed(closeFailure);
                }
            }
            audit.recordFailure(actor, SystemAuditOperation.FULL_RESTORE_VALIDATE, Map.of(), exception);
            throw exception;
        } finally {
            clear(password);
        }
    }

    public DownloadArtifact createFullBackup(Member actor, char[] password) throws IOException {
        try {
            requireAdmin(actor);
            DownloadArtifact artifact = export(
                password, "church-full-backup-" + clock.instant().toString().replace(':', '-') + ".zip"
            );
            audit.recordSuccess(actor, SystemAuditOperation.FULL_BACKUP, Map.of());
            return artifact;
        } catch (IOException | RuntimeException | Error exception) {
            audit.recordFailure(actor, SystemAuditOperation.FULL_BACKUP, Map.of(), exception);
            throw exception;
        } finally {
            clear(password);
        }
    }

    public DownloadArtifact createSafetyBackup(Member actor, String operationId, char[] password) throws IOException {
        Map<String, ?> metadata = operationId == null ? Map.of() : Map.of("operationId", operationId);
        try {
            requireAdmin(actor);
            DataOperationStore.Operation operation = operationStore.require(operationId, actor.getId());
            if (operation.status() != DataOperationStatus.VALIDATED) {
                throw new IllegalStateException("Safety backup is not available in the current operation state.");
            }
            DownloadArtifact artifact = export(password, "pre-restore-safety-backup.zip");
            operation.status(DataOperationStatus.SAFETY_BACKUP_DOWNLOADED);
            operation.message("Safety backup generated. Verify the downloaded file before restoring.");
            audit.recordSuccess(actor, SystemAuditOperation.FULL_RESTORE_SAFETY_BACKUP, metadata);
            return artifact;
        } catch (IOException | RuntimeException | Error exception) {
            audit.recordFailure(actor, SystemAuditOperation.FULL_RESTORE_SAFETY_BACKUP, metadata, exception);
            throw exception;
        } finally {
            clear(password);
        }
    }

    public DataOperationResponse executeRestore(Member actor, String operationId, String confirmation) throws IOException {
        Map<String, ?> metadata = operationId == null ? Map.of() : Map.of("operationId", operationId);
        try {
            DataOperationResponse response = executeRestoreOperation(actor, operationId, confirmation);
            audit.recordSuccess(actor, SystemAuditOperation.FULL_RESTORE_EXECUTE, Map.of(
                "operationId", operationId, "recordCount", response.documentCount()
            ));
            return response;
        } catch (IOException | RuntimeException | Error exception) {
            audit.recordFailure(actor, SystemAuditOperation.FULL_RESTORE_EXECUTE, metadata, exception);
            throw exception;
        }
    }

    private DataOperationResponse executeRestoreOperation(
        Member actor,
        String operationId,
        String confirmation
    ) throws IOException {
        requireAdmin(actor);
        if (!RESTORE_CONFIRMATION.equals(confirmation)) {
            throw new IllegalArgumentException("The restore confirmation phrase does not match.");
        }
        DataOperationStore.Operation operation = operationStore.require(operationId, actor.getId());
        if (operation.status() != DataOperationStatus.SAFETY_BACKUP_DOWNLOADED) {
            throw new IllegalStateException("Download the safety backup before restoring the database.");
        }

        operation.status(DataOperationStatus.RESTORING);
        operation.message("Database restore is running.");
        maintenanceMode.enable();
        try {
            RestoreVerification verification = operation.validatedArchive().replaceAll();
            operation.verification(verification);
            authTokenService.revokeAll();
            operationStore.complete(operation);
            maintenanceMode.disable();
            return operation.response();
        } catch (IOException | RuntimeException | Error exception) {
            operationStore.failInMaintenance(operation, exception.getMessage());
            throw exception;
        }
    }

    public DataOperationResponse status(Member actor, String operationId) {
        requireAdmin(actor);
        return operationStore.require(operationId, actor.getId()).response();
    }

    private DownloadArtifact export(char[] password, String filename) throws IOException {
        requirePassword(password);
        Files.createDirectories(properties.tempDirectory());
        Path output = Files.createTempFile(properties.tempDirectory(), "download-", ".zip");
        try {
            exporter.exportFull(output, password);
            return new DownloadArtifact(output, filename);
        } catch (IOException | RuntimeException | Error exception) {
            Files.deleteIfExists(output);
            throw exception;
        } finally {
            clear(password);
        }
    }

    private void requireAdmin(Member actor) {
        if (actor == null || actor.getRoles() == null || !actor.getRoles().contains(Role.ADMIN)) {
            throw new SecurityException("Administrator access is required for data management.");
        }
    }

    private void requirePassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Backup password is required.");
        }
    }

    private void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    public record DownloadArtifact(Path path, String filename) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            Files.deleteIfExists(path);
        }
    }

    @FunctionalInterface
    interface RestoreValidator {
        RestoreSession validate(Path archive, char[] password) throws IOException;
    }

    interface RestoreSession extends AutoCloseable {
        ArchiveManifest manifest();
        RestoreVerification replaceAll() throws IOException;
        @Override
        void close() throws IOException;
    }
}

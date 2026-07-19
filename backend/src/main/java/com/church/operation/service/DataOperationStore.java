package com.church.operation.service;

import com.church.operation.config.DataManagementProperties;
import com.church.operation.dto.DataOperationResponse;
import com.church.operation.util.DataOperationStatus;
import com.church.operation.util.DataOperationType;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DataOperationStore {
    private final Map<String, Operation> operations = new ConcurrentHashMap<>();
    private final DataManagementProperties properties;
    private final Clock clock;
    private String activeMutationId;

    @Autowired
    public DataOperationStore(DataManagementProperties properties) {
        this(properties, Clock.systemUTC());
    }

    DataOperationStore(DataManagementProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized Operation create(
        String actorId,
        Path uploadedArchive,
        DataManagementService.RestoreSession validatedArchive,
        long collectionCount,
        long documentCount,
        long indexCount
    ) {
        expireOperations();
        if (activeMutationId != null) {
            throw new IllegalStateException("Another data-management operation is already active.");
        }
        String id = UUID.randomUUID().toString();
        Operation operation = new Operation(
            id,
            actorId,
            uploadedArchive,
            validatedArchive,
            clock.instant().plus(properties.operationExpiry()),
            collectionCount,
            documentCount,
            indexCount
        );
        operations.put(id, operation);
        activeMutationId = id;
        return operation;
    }

    public synchronized Operation require(String id, String actorId) {
        expireOperations();
        Operation operation = operations.get(id);
        if (operation == null) {
            throw new IllegalArgumentException("Data-management operation was not found or has expired.");
        }
        if (!operation.actorId().equals(actorId)) {
            throw new SecurityException("You do not have permission to access this operation.");
        }
        return operation;
    }

    public synchronized void complete(Operation operation) {
        operation.status(DataOperationStatus.COMPLETE);
        operation.message("Restore completed.");
        release(operation);
    }

    public synchronized void failInMaintenance(Operation operation, String message) {
        operation.status(DataOperationStatus.FAILED_MAINTENANCE);
        operation.message(message);
    }

    public synchronized void beginExternalMutation(String id) {
        expireOperations();
        if (activeMutationId != null && !activeMutationId.equals(id)) {
            throw new IllegalStateException("Another data-management operation is already active.");
        }
        activeMutationId = id;
    }

    public synchronized void endExternalMutation(String id) {
        if (id != null && id.equals(activeMutationId)) {
            activeMutationId = null;
        }
    }

    private void release(Operation operation) {
        if (operation.id().equals(activeMutationId)) {
            activeMutationId = null;
        }
        operation.closeResources();
    }

    private void expireOperations() {
        Instant now = clock.instant();
        operations.values().removeIf(operation -> {
            if (!now.isBefore(operation.expiresAt())) {
                if (operation.id().equals(activeMutationId)) {
                    activeMutationId = null;
                }
                operation.closeResources();
                return true;
            }
            return false;
        });
    }

    public static final class Operation {
        private final String id;
        private final String actorId;
        private final Path uploadedArchive;
        private DataManagementService.RestoreSession validatedArchive;
        private final Instant expiresAt;
        private final long collectionCount;
        private long documentCount;
        private long indexCount;
        private DataOperationStatus status = DataOperationStatus.VALIDATED;
        private String message = "Backup archive validated.";

        private Operation(
            String id,
            String actorId,
            Path uploadedArchive,
            DataManagementService.RestoreSession validatedArchive,
            Instant expiresAt,
            long collectionCount,
            long documentCount,
            long indexCount
        ) {
            this.id = id;
            this.actorId = actorId;
            this.uploadedArchive = uploadedArchive;
            this.validatedArchive = validatedArchive;
            this.expiresAt = expiresAt;
            this.collectionCount = collectionCount;
            this.documentCount = documentCount;
            this.indexCount = indexCount;
        }

        public String id() { return id; }
        public String actorId() { return actorId; }
        public Instant expiresAt() { return expiresAt; }
        public DataOperationStatus status() { return status; }
        public void status(DataOperationStatus value) { status = value; }
        public void message(String value) { message = value; }
        public DataManagementService.RestoreSession validatedArchive() {
            if (validatedArchive == null) {
                throw new IllegalStateException("Validated restore archive is no longer available.");
            }
            return validatedArchive;
        }
        public void verification(RestoreVerification verification) {
            documentCount = verification.documentCount();
            indexCount = verification.indexCount();
        }
        public DataOperationResponse response() {
            return new DataOperationResponse(
                id, DataOperationType.FULL_RESTORE, status, expiresAt,
                collectionCount, documentCount, indexCount, message
            );
        }
        private void closeResources() {
            if (validatedArchive != null) {
                try {
                    validatedArchive.close();
                } catch (IOException ignored) {
                    // Expired operation cleanup is best effort.
                }
                validatedArchive = null;
            }
            try {
                Files.deleteIfExists(uploadedArchive);
            } catch (IOException ignored) {
                // Expired operation cleanup is best effort.
            }
        }
    }
}

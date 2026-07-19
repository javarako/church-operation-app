package com.church.operation.entity;

import com.church.operation.util.SystemAuditOperation;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document("system_audit_events")
@CompoundIndexes({
    @CompoundIndex(name = "audit_actor_timestamp", def = "{'actorId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "audit_operation_timestamp", def = "{'operation': 1, 'timestamp': -1}")
})
public final class SystemAuditEvent {
    @Id
    private final String id;
    @Indexed
    private final String actorId;
    private final String actorEmail;
    @Indexed
    private final SystemAuditOperation operation;
    @Indexed
    private final Instant timestamp;
    private final String result;
    private final Map<String, String> metadata;
    private final String errorSummary;

    @PersistenceCreator
    public SystemAuditEvent(
        String id,
        String actorId,
        String actorEmail,
        SystemAuditOperation operation,
        Instant timestamp,
        String result,
        Map<String, String> metadata,
        String errorSummary
    ) {
        this.id = id;
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.operation = operation;
        this.timestamp = timestamp;
        this.result = result;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.errorSummary = errorSummary;
    }

    public String getId() { return id; }
    public String getActorId() { return actorId; }
    public String getActorEmail() { return actorEmail; }
    public SystemAuditOperation getOperation() { return operation; }
    public Instant getTimestamp() { return timestamp; }
    public String getResult() { return result; }
    public Map<String, String> getMetadata() { return metadata; }
    public String getErrorSummary() { return errorSummary; }
}

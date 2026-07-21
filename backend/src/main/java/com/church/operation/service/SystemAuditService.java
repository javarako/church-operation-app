package com.church.operation.service;

import com.church.operation.entity.Member;
import com.church.operation.entity.SystemAuditEvent;
import com.church.operation.repo.SystemAuditEventRepository;
import com.church.operation.util.SystemAuditOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SystemAuditService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemAuditService.class);
    private static final int MAX_ERROR_LENGTH = 300;
    private static final Set<String> ALLOWED_METADATA_KEYS = Set.of(
        "operationId", "archiveId", "fiscalYear", "taxYear", "receiptId", "receiptNumber",
        "memberId", "referenceDataId", "offeringNumber", "recordCount", "offeringCount",
        "linkedIncomeCount", "expenseCount", "budgetCount", "receiptCount", "reportType",
        "version", "closingId", "gridFsFileId", "checksum", "fileSize"
    );
    private static final Set<String> FORBIDDEN_KEY_PARTS = Set.of("password", "hash", "bytes", "content");
    private static final Pattern SENSITIVE_ERROR_VALUE = Pattern.compile(
        "(?i)(password|hash|bytes|content)(\\s*[:=]\\s*)\\S+"
    );

    private final SystemAuditEventRepository repository;
    private final Clock clock;

    @Autowired
    public SystemAuditService(SystemAuditEventRepository repository) {
        this(repository, Clock.systemUTC());
    }

    SystemAuditService(SystemAuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void recordSuccess(Member actor, SystemAuditOperation operation, Map<String, ?> metadata) {
        persist(actor, operation, "SUCCESS", metadata, null);
    }

    public void recordFailure(
        Member actor,
        SystemAuditOperation operation,
        Map<String, ?> metadata,
        Throwable failure
    ) {
        persist(actor, operation, "FAILURE", metadata, sanitizeFailure(failure));
    }

    private void persist(
        Member actor,
        SystemAuditOperation operation,
        String result,
        Map<String, ?> metadata,
        String errorSummary
    ) {
        Map<String, String> safeMetadata = sanitizeMetadata(metadata);
        SystemAuditEvent event = new SystemAuditEvent(
            null,
            actor == null ? null : actor.getId(),
            actor == null ? null : actor.getPrimaryEmail(),
            operation,
            clock.instant(),
            result,
            safeMetadata,
            errorSummary
        );
        try {
            repository.save(event);
        } catch (RuntimeException auditFailure) {
            LOGGER.error("Could not persist system audit event for {}.", operation, auditFailure);
        }
    }

    private Map<String, String> sanitizeMetadata(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (FORBIDDEN_KEY_PARTS.stream().anyMatch(normalized::contains)) {
                throw new IllegalArgumentException("Sensitive audit metadata keys are not allowed.");
            }
            if (!ALLOWED_METADATA_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unsupported audit metadata key: " + key);
            }
            sanitized.put(key, String.valueOf(value));
        });
        return Map.copyOf(sanitized);
    }

    private String sanitizeFailure(Throwable failure) {
        if (failure == null) {
            return "Unknown failure";
        }
        String message = failure.getMessage();
        String summary = failure.getClass().getSimpleName()
            + (message == null || message.isBlank() ? "" : ": " + message);
        summary = summary.replaceAll("\\s+", " ").trim();
        summary = SENSITIVE_ERROR_VALUE.matcher(summary).replaceAll("$1$2[REDACTED]");
        return summary.length() <= MAX_ERROR_LENGTH ? summary : summary.substring(0, MAX_ERROR_LENGTH);
    }
}

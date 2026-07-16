package com.church.operation.dto;

import com.church.operation.util.DataOperationStatus;
import com.church.operation.util.DataOperationType;

import java.time.Instant;

public record DataOperationResponse(
    String id,
    DataOperationType type,
    DataOperationStatus status,
    Instant expiresAt,
    long collectionCount,
    long documentCount,
    long indexCount,
    String message
) {
}

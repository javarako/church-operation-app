package com.church.operation.dto;

import com.church.operation.util.EmailSettingsSource;

import java.time.Instant;

public record EmailSettingsResponse(
    String host,
    int port,
    String username,
    String fromAddress,
    boolean passwordConfigured,
    EmailSettingsSource source,
    Instant updatedAt
) {
}

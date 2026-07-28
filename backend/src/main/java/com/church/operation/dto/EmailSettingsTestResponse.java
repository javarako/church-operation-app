package com.church.operation.dto;

import java.time.Instant;

public record EmailSettingsTestResponse(
    String verificationToken,
    Instant expiresAt,
    String message
) {
}

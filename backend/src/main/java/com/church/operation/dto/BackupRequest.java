package com.church.operation.dto;

import jakarta.validation.constraints.NotBlank;

public record BackupRequest(@NotBlank String password) {
}

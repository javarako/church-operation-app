package com.church.operation.dto;

import jakarta.validation.constraints.NotBlank;

public record RestoreExecuteRequest(@NotBlank String confirmation) {
}

package com.church.operation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record YearEndClosingRequest(
    @Min(2000) int fiscalYear,
    @NotBlank String currentPassword
) {
}

package com.church.operation.dto;

import com.church.operation.util.ReferenceDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReferenceDataRequest(
    @NotNull ReferenceDataType type,
    @NotBlank String code,
    @NotBlank String label,
    int sortOrder,
    boolean active,
    String parentCode
) {
}

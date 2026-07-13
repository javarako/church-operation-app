package com.church.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidTaxReceiptRequest(@NotBlank @Size(max = 500) String reason) {
}

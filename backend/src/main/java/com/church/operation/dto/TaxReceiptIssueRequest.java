package com.church.operation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaxReceiptIssueRequest(
    @Min(1900) @Max(2200) int taxYear,
    @NotBlank String offeringNumber,
    @Size(max = 500) String thankYouNote
) {
}

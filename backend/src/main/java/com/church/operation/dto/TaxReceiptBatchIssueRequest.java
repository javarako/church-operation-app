package com.church.operation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record TaxReceiptBatchIssueRequest(
    @Min(1900) @Max(2200) int taxYear,
    @Size(max = 500) String thankYouNote
) {
}

package com.church.operation.dto;

import java.util.List;

public record TaxReceiptValidationError(
    String offeringNumber,
    String donorName,
    List<String> errors
) {
}

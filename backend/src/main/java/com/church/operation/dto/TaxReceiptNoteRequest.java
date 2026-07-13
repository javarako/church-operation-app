package com.church.operation.dto;

import jakarta.validation.constraints.Size;

public record TaxReceiptNoteRequest(@Size(max = 500) String thankYouNote) {
}

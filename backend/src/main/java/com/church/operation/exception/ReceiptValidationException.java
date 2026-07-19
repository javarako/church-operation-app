package com.church.operation.exception;

import com.church.operation.dto.TaxReceiptValidationError;

import java.util.List;

public class ReceiptValidationException extends IllegalArgumentException {
    private final List<TaxReceiptValidationError> errors;

    public ReceiptValidationException(List<TaxReceiptValidationError> errors) {
        super("Receipt validation failed: " + errors.stream()
            .flatMap(error -> error.errors().stream())
            .distinct()
            .reduce((left, right) -> left + ", " + right)
            .orElse("unknown validation error"));
        this.errors = List.copyOf(errors);
    }

    public List<TaxReceiptValidationError> getErrors() {
        return errors;
    }
}

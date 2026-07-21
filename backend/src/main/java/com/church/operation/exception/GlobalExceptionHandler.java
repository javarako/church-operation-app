package com.church.operation.exception;

import com.church.operation.dto.ApiError;
import com.church.operation.dto.TaxReceiptValidationError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "Request validation failed."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(ReceiptValidationException.class)
    ResponseEntity<ReceiptValidationResponse> handleReceiptValidation(ReceiptValidationException ex) {
        return ResponseEntity.badRequest().body(new ReceiptValidationResponse(
            "RECEIPT_VALIDATION_ERROR",
            ex.getMessage(),
            ex.getErrors()
        ));
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<ApiError> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(MemberImageNotFoundException.class)
    ResponseEntity<ApiError> handleMemberImageNotFound(MemberImageNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(DeletionBlockedException.class)
    ResponseEntity<ApiError> handleDeletionBlocked(DeletionBlockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("DELETION_BLOCKED", ex.getMessage()));
    }

    @ExceptionHandler(YearEndClosingConflictException.class)
    ResponseEntity<ApiError> handleYearEndClosingConflict(YearEndClosingConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("YEAR_END_CLOSING_CONFLICT", ex.getMessage()));
    }

    record ReceiptValidationResponse(String code, String message, List<TaxReceiptValidationError> errors) {
    }
}

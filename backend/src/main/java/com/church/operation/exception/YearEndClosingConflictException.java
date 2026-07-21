package com.church.operation.exception;

public class YearEndClosingConflictException extends RuntimeException {
    public YearEndClosingConflictException(String message) {
        super(message);
    }

    public YearEndClosingConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

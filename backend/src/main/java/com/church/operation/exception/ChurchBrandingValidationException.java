package com.church.operation.exception;

public class ChurchBrandingValidationException extends IllegalArgumentException {
    public ChurchBrandingValidationException(String message) {
        super(message);
    }

    public ChurchBrandingValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

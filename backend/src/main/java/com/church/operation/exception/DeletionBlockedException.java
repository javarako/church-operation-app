package com.church.operation.exception;

public class DeletionBlockedException extends RuntimeException {
    public DeletionBlockedException(String message) {
        super(message);
    }
}

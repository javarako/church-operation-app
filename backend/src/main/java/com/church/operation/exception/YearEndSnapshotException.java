package com.church.operation.exception;

public class YearEndSnapshotException extends IllegalStateException {
    public YearEndSnapshotException(String message) {
        super(message);
    }

    public YearEndSnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}

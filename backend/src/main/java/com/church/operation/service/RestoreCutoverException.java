package com.church.operation.service;

import java.io.IOException;
import java.util.List;

public final class RestoreCutoverException extends IOException {
    private final List<String> stagingNamespaces;
    private final List<String> backupNamespaces;

    RestoreCutoverException(
        String message,
        Throwable cause,
        List<String> stagingNamespaces,
        List<String> backupNamespaces
    ) {
        super(message, cause);
        this.stagingNamespaces = List.copyOf(stagingNamespaces);
        this.backupNamespaces = List.copyOf(backupNamespaces);
    }

    public boolean maintenanceRequired() {
        return true;
    }

    public List<String> stagingNamespaces() {
        return stagingNamespaces;
    }

    public List<String> backupNamespaces() {
        return backupNamespaces;
    }
}

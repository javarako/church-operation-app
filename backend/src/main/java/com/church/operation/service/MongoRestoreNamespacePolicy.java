package com.church.operation.service;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class MongoRestoreNamespacePolicy {
    static final String RESTORE_STAGING_PREFIX = "__church_restore_staging__";
    static final String RESTORE_BACKUP_PREFIX = "__church_restore_backup__";

    private static final int MAX_NAMESPACE_BYTES = 255;

    private final String databaseName;

    MongoRestoreNamespacePolicy(String databaseName) {
        if (databaseName == null || databaseName.isBlank()) {
            throw new IllegalArgumentException("MongoDB database name is required for restore preflight.");
        }
        this.databaseName = databaseName;
    }

    boolean isRestorable(String name) {
        return name != null && !name.isBlank() && name.indexOf('$') < 0
            && !name.startsWith("system.") && !name.contains(".system.")
            && !name.startsWith(RESTORE_STAGING_PREFIX) && !name.startsWith(RESTORE_BACKUP_PREFIX)
            && name.codePoints().noneMatch(Character::isISOControl)
            && namespaceBytes(name) <= MAX_NAMESPACE_BYTES;
    }

    void validate(String name) {
        if (!isRestorable(name)) {
            throw new IllegalArgumentException("Backup collection name is invalid or reserved.");
        }
    }

    private int namespaceBytes(String name) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(databaseName + "." + name));
            return encoded.remaining();
        } catch (CharacterCodingException exception) {
            return MAX_NAMESPACE_BYTES + 1;
        }
    }
}

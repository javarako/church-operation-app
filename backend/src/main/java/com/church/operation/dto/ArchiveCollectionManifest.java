package com.church.operation.dto;

public record ArchiveCollectionManifest(
    String collection,
    String entryName,
    long documentCount,
    long sizeBytes,
    String sha256
) {
    public ArchiveCollectionManifest(String collection, String entryName) {
        this(collection, entryName, 0, 0, null);
    }
}

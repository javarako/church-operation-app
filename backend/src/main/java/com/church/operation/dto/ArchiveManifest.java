package com.church.operation.dto;

import com.church.operation.util.ArchiveType;

import java.util.List;

public record ArchiveManifest(
    int formatVersion,
    ArchiveType archiveType,
    List<ArchiveCollectionManifest> collections
) {
}

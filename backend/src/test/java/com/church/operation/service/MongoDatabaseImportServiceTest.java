package com.church.operation.service;

import com.church.operation.config.DataManagementProperties;
import com.church.operation.dto.ArchiveCollectionManifest;
import com.church.operation.dto.ArchiveManifest;
import com.church.operation.util.ArchiveType;
import com.mongodb.client.MongoDatabase;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.RawBsonDocument;
import org.bson.codecs.BsonDocumentCodec;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MongoDatabaseImportServiceTest {
    private static final char[] PASSWORD = "invalid namespace preflight".toCharArray();

    @TempDir
    Path tempDirectory;

    @ParameterizedTest
    @ValueSource(strings = {
        "$members",
        "members.system.profile",
        "system.profile",
        "__church_restore_staging__existing",
        "members\narchive"
    })
    void rejectsInvalidOriginalNamespaceBeforeAnyDatabaseAccessOrMutation(String namespace) throws Exception {
        DataManagementProperties properties = new DataManagementProperties(
            tempDirectory.resolve("operations"),
            Duration.ofMinutes(30),
            DataSize.ofMegabytes(10)
        );
        ArchivePackageService packages = new ArchivePackageService(properties);
        Path archive = createArchive(packages, namespace);
        MongoDatabase database = mock(MongoDatabase.class);
        when(database.getName()).thenReturn("restore_db");
        MongoDatabaseImportService importer = new MongoDatabaseImportService(database, packages);

        assertThatThrownBy(() -> importer.validateFull(archive, PASSWORD))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid or reserved");

        verify(database).getName();
        verifyNoMoreInteractions(database);
    }

    private Path createArchive(ArchivePackageService packages, String namespace) throws Exception {
        Map<String, Path> entries = new LinkedHashMap<>();
        String dataEntry = MongoDatabaseExportService.dataEntryName(namespace);
        String optionsEntry = MongoDatabaseExportService.optionsEntryName(namespace);
        String indexesEntry = MongoDatabaseExportService.indexesEntryName(namespace);
        entries.put(dataEntry, emptySidecar(dataEntry));
        entries.put(optionsEntry, createCommandSidecar(optionsEntry, namespace));
        entries.put(indexesEntry, emptySidecar(indexesEntry));
        ArchiveManifest manifest = new ArchiveManifest(
            ArchivePackageService.FORMAT_VERSION,
            ArchiveType.FULL_BACKUP,
            List.of(
                new ArchiveCollectionManifest(namespace, dataEntry),
                new ArchiveCollectionManifest(namespace, optionsEntry),
                new ArchiveCollectionManifest(namespace, indexesEntry)
            )
        );
        Path archive = tempDirectory.resolve("invalid-" + Integer.toHexString(namespace.hashCode()) + ".zip");
        packages.write(archive, PASSWORD, manifest, entries);
        return archive;
    }

    private Path emptySidecar(String entryName) throws Exception {
        Path path = tempDirectory.resolve("entries").resolve(entryName);
        Files.createDirectories(path.getParent());
        Files.createFile(path);
        return path;
    }

    private Path createCommandSidecar(String entryName, String namespace) throws Exception {
        Path path = tempDirectory.resolve("entries").resolve(entryName);
        Files.createDirectories(path.getParent());
        BsonDocument command = new BsonDocument("create", new BsonString(namespace));
        try (OutputStream output = Files.newOutputStream(path)) {
            new BsonStreamCodec().writeRaw(new RawBsonDocument(command, new BsonDocumentCodec()), output);
        }
        return path;
    }
}

package com.church.operation.service;

import com.church.operation.dto.ArchiveCollectionManifest;
import com.church.operation.dto.ArchiveManifest;
import com.church.operation.util.ArchiveType;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchivePackageServiceTest {
    private static final char[] PASSWORD = "correct horse battery staple".toCharArray();
    private static final String ENTRY_NAME = "collections/members.bson";

    @TempDir
    Path tempDir;

    private final BsonStreamCodec codec = new BsonStreamCodec();
    private final ArchivePackageService service = new ArchivePackageService();

    @Test
    void validatesEncryptedPackageWithCorrectPasswordAndCleansExtractionOnClose() throws Exception {
        Path archive = writeArchive(ArchiveType.FULL_BACKUP);
        Path extractionDirectory;
        Path extractedEntry;

        try (ArchivePackageService.ValidatedArchive validated = service.validate(
            archive, PASSWORD, ArchiveType.FULL_BACKUP
        )) {
            assertThat(validated.manifest().formatVersion()).isEqualTo(1);
            assertThat(validated.manifest().archiveType()).isEqualTo(ArchiveType.FULL_BACKUP);
            assertThat(validated.entries()).containsKey(ENTRY_NAME);
            extractionDirectory = validated.extractionDirectory();
            extractedEntry = validated.entries().get(ENTRY_NAME);
            assertThat(extractedEntry).exists();
        }

        assertThat(extractionDirectory).doesNotExist();
        assertThat(extractedEntry).doesNotExist();
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        Path archive = writeArchive(ArchiveType.FULL_BACKUP);

        assertThatThrownBy(() -> service.validate(
            archive, "not the password".toCharArray(), ArchiveType.FULL_BACKUP
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSameSizeShaMismatch() throws Exception {
        byte[] original = bsonBytes(new Document("member", "Ada"));
        byte[] changed = original.clone();
        changed[changed.length - 2] ^= 1;
        Path archive = writeManualArchive(
            "same-size-sha.zip",
            manifestJson(1, List.of(collection("members", ENTRY_NAME, 1, original.length, sha256(original)))),
            List.of(new RawEntry(ENTRY_NAME, changed, true, EncryptionMethod.AES))
        );

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("checksum");
    }

    @Test
    void rejectsDocumentCountMismatch() throws Exception {
        byte[] entry = bsonBytes(new Document("member", "Ada"));
        Path archive = writeManualArchive(
            "document-count.zip",
            manifestJson(1, List.of(collection("members", ENTRY_NAME, 2, entry.length, sha256(entry)))),
            List.of(new RawEntry(ENTRY_NAME, entry, true, EncryptionMethod.AES))
        );

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("document count");
    }

    @Test
    void rejectsWrongArchiveType() throws Exception {
        Path archive = writeArchive(ArchiveType.FULL_BACKUP);

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.RESTORE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("type");
    }

    @Test
    void writeRejectsUnsupportedFormatVersion() throws Exception {
        Path entry = writeEntry("unsupported-writer.bson", new Document("member", "Ada"));
        Path archive = tempDir.resolve("unsupported-writer.zip");
        ArchiveManifest manifest = new ArchiveManifest(
            2,
            ArchiveType.FULL_BACKUP,
            List.of(new ArchiveCollectionManifest("members", ENTRY_NAME))
        );

        assertThatThrownBy(() -> service.write(archive, PASSWORD, manifest, Map.of(ENTRY_NAME, entry)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("version");
        assertThat(archive).doesNotExist();
    }

    @Test
    void rejectsIndependentlyConstructedUnsupportedVersionArchive() throws Exception {
        byte[] entry = bsonBytes(new Document("member", "Ada"));
        Path archive = writeManualArchive(
            "unsupported-reader.zip",
            manifestJson(2, List.of(collection("members", ENTRY_NAME, 1, entry.length, sha256(entry)))),
            List.of(new RawEntry(ENTRY_NAME, entry, true, EncryptionMethod.AES))
        );

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("version");
    }

    @Test
    void rejectsFractionalFormatVersionInsteadOfTruncatingIt() throws Exception {
        byte[] entry = bsonBytes(new Document("member", "Ada"));
        Path archive = writeManualArchive(
            "fractional-version.zip",
            manifestJson("1.5", "1", Long.toString(entry.length), entry),
            List.of(new RawEntry(ENTRY_NAME, entry, true, EncryptionMethod.AES))
        );

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("number");
    }

    @Test
    void rejectsExactFractionalTokensBeforeFloatingPointRounding() throws Exception {
        byte[] entry = bsonBytes(new Document("member", "Ada"));
        String fractionalToken = "0.99999999999999999";
        List<String> manifests = List.of(
            manifestJson(fractionalToken, "1", Long.toString(entry.length), entry),
            manifestJson("1", fractionalToken, Long.toString(entry.length), entry),
            manifestJson("1", "1", fractionalToken, entry)
        );

        for (int index = 0; index < manifests.size(); index++) {
            Path archive = writeManualArchive(
                "exact-fractional-" + index + ".zip",
                manifests.get(index),
                List.of(new RawEntry(ENTRY_NAME, entry, true, EncryptionMethod.AES))
            );

            assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
                .as("numeric field index %s", index)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("number");
        }
    }

    @Test
    void rejectsFractionalNegativeAndOverflowingDocumentCounts() throws Exception {
        byte[] entry = bsonBytes(new Document("member", "Ada"));
        List<String> invalidCounts = List.of("1.5", "-1", "9223372036854775808");

        for (int index = 0; index < invalidCounts.size(); index++) {
            Path archive = writeManualArchive(
                "invalid-count-" + index + ".zip",
                manifestJson("1", invalidCounts.get(index), Long.toString(entry.length), entry),
                List.of(new RawEntry(ENTRY_NAME, entry, true, EncryptionMethod.AES))
            );

            assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
                .as("documentCount=%s", invalidCounts.get(index))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("number");
        }
    }

    @Test
    void rejectsFractionalNegativeAndOverflowingEntrySizes() throws Exception {
        byte[] entry = bsonBytes(new Document("member", "Ada"));
        List<String> invalidSizes = List.of("1.5", "-1", "9223372036854775808");

        for (int index = 0; index < invalidSizes.size(); index++) {
            Path archive = writeManualArchive(
                "invalid-size-" + index + ".zip",
                manifestJson("1", "1", invalidSizes.get(index), entry),
                List.of(new RawEntry(ENTRY_NAME, entry, true, EncryptionMethod.AES))
            );

            assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
                .as("sizeBytes=%s", invalidSizes.get(index))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("number");
        }
    }

    @Test
    void writeRejectsNegativeCountAndSizeValues() throws Exception {
        Path entry = writeEntry("negative-writer.bson", new Document("member", "Ada"));
        List<ArchiveCollectionManifest> invalidCollections = List.of(
            new ArchiveCollectionManifest("members", ENTRY_NAME, -1, 0, null),
            new ArchiveCollectionManifest("members", ENTRY_NAME, 0, -1, null)
        );

        for (int index = 0; index < invalidCollections.size(); index++) {
            ArchiveManifest manifest = new ArchiveManifest(
                1, ArchiveType.FULL_BACKUP, List.of(invalidCollections.get(index))
            );
            Path archive = tempDir.resolve("negative-writer-" + index + ".zip");

            assertThatThrownBy(() -> service.write(archive, PASSWORD, manifest, Map.of(ENTRY_NAME, entry)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("number");
        }
    }

    @Test
    void rejectsPathTraversalEntryNames() throws Exception {
        Path archive = writeArchive(ArchiveType.FULL_BACKUP);
        try (ZipFile zipFile = new ZipFile(archive.toFile(), PASSWORD)) {
            zipFile.addStream(new ByteArrayInputStream(new byte[] {1}), encryptedParameters("../escape.bson"));
        }

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("path");
    }

    @Test
    void rejectsDuplicateManifestEntries() throws Exception {
        byte[] entry = bsonBytes(new Document("member", "Ada"));
        Document declared = collection("members", ENTRY_NAME, 1, entry.length, sha256(entry));
        Path archive = writeManualArchive(
            "duplicate-manifest-entry.zip",
            manifestJson(1, List.of(declared, declared)),
            List.of(new RawEntry(ENTRY_NAME, entry, true, EncryptionMethod.AES))
        );

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate manifest entries");
    }

    @Test
    void acceptsMultipleSidecarEntriesForOneLogicalCollection() throws Exception {
        byte[] first = bsonBytes(new Document("member", "Ada"));
        byte[] second = bsonBytes(new Document("member", "Grace"));
        String secondEntry = "collections/other.bson";
        Path archive = writeManualArchive(
            "duplicate-logical-collection.zip",
            manifestJson(1, List.of(
                collection("members", ENTRY_NAME, 1, first.length, sha256(first)),
                collection("members", secondEntry, 1, second.length, sha256(second))
            )),
            List.of(
                new RawEntry(ENTRY_NAME, first, true, EncryptionMethod.AES),
                new RawEntry(secondEntry, second, true, EncryptionMethod.AES)
            )
        );

        try (ArchivePackageService.ValidatedArchive validated = service.validate(
            archive, PASSWORD, ArchiveType.FULL_BACKUP
        )) {
            assertThat(validated.manifest().collections()).hasSize(2);
            assertThat(validated.entries()).containsKeys(ENTRY_NAME, secondEntry);
        }
    }

    @Test
    void writeRejectsBlankAndNullLogicalCollectionNamesAndAcceptsSidecars() throws Exception {
        Path first = writeEntry("logical-first.bson", new Document("member", "Ada"));
        Path second = writeEntry("logical-second.bson", new Document("member", "Grace"));

        for (String invalidName : new String[] {null, "", "   "}) {
            ArchiveManifest manifest = new ArchiveManifest(
                1,
                ArchiveType.FULL_BACKUP,
                List.of(new ArchiveCollectionManifest(invalidName, ENTRY_NAME))
            );
            assertThatThrownBy(() -> service.write(
                tempDir.resolve("invalid-logical-" + String.valueOf(invalidName) + ".zip"),
                PASSWORD,
                manifest,
                Map.of(ENTRY_NAME, first)
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collection names");
        }

        String secondEntry = "collections/other.bson";
        ArchiveManifest duplicate = new ArchiveManifest(1, ArchiveType.FULL_BACKUP, List.of(
            new ArchiveCollectionManifest("members", ENTRY_NAME),
            new ArchiveCollectionManifest("members", secondEntry)
        ));
        service.write(
            tempDir.resolve("duplicate-logical-writer.zip"),
            PASSWORD,
            duplicate,
            Map.of(ENTRY_NAME, first, secondEntry, second)
        );
    }

    @Test
    void rejectsDirectoryEntries() throws Exception {
        Path archive = writeArchive(ArchiveType.FULL_BACKUP);
        Path directory = Files.createDirectory(tempDir.resolve("empty-directory"));
        ZipParameters parameters = encryptedParameters(null);
        parameters.setIncludeRootFolder(true);
        parameters.setRootFolderNameInZip("empty-directory");
        try (ZipFile zipFile = new ZipFile(archive.toFile(), PASSWORD)) {
            zipFile.addFolder(directory.toFile(), parameters);
            assertThat(zipFile.getFileHeaders()).anyMatch(FileHeader::isDirectory);
        }

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid entry");
    }

    @Test
    void rejectsUnencryptedEntries() throws Exception {
        byte[] entry = bsonBytes(new Document("member", "Ada"));
        Path archive = writeManualArchive(
            "unencrypted-entry.zip",
            manifestJson(1, List.of(collection("members", ENTRY_NAME, 1, entry.length, sha256(entry)))),
            List.of(new RawEntry(ENTRY_NAME, entry, false, EncryptionMethod.NONE))
        );

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("AES");
    }

    @Test
    void rejectsEncryptedNonAesEntries() throws Exception {
        byte[] entry = bsonBytes(new Document("member", "Ada"));
        Path archive = writeManualArchive(
            "zip-standard-entry.zip",
            manifestJson(1, List.of(collection("members", ENTRY_NAME, 1, entry.length, sha256(entry)))),
            List.of(new RawEntry(ENTRY_NAME, entry, true, EncryptionMethod.ZIP_STANDARD))
        );

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("AES");
    }

    @Test
    void rejectsUnlistedEntries() throws Exception {
        Path archive = writeArchive(ArchiveType.FULL_BACKUP);
        try (ZipFile zipFile = new ZipFile(archive.toFile(), PASSWORD)) {
            zipFile.addStream(new ByteArrayInputStream(new byte[] {1}), encryptedParameters("extra.bson"));
        }

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unlisted");
    }

    @Test
    void rejectsManifestLargerThanConfiguredBound() throws Exception {
        byte[] entry = bsonBytes(new Document("member", "Ada"));
        Path archive = writeManualArchive(
            "large-manifest.zip",
            manifestJson(1, List.of(collection("members", ENTRY_NAME, 1, entry.length, sha256(entry)))),
            List.of(new RawEntry(ENTRY_NAME, entry, true, EncryptionMethod.AES))
        );
        ArchivePackageService bounded = new ArchivePackageService(64, 1024);

        assertThatThrownBy(() -> bounded.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("manifest size");
    }

    @Test
    void writeRejectsManifestLargerThanConfiguredBound() throws Exception {
        Path entry = writeEntry("writer-large-manifest.bson", new Document("member", "Ada"));
        Path archive = tempDir.resolve("writer-large-manifest.zip");
        ArchivePackageService bounded = new ArchivePackageService(codec, 64, 1024);
        ArchiveManifest manifest = new ArchiveManifest(
            1,
            ArchiveType.FULL_BACKUP,
            List.of(new ArchiveCollectionManifest("members", ENTRY_NAME))
        );

        assertThatThrownBy(() -> bounded.write(archive, PASSWORD, manifest, Map.of(ENTRY_NAME, entry)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("manifest size");
        assertThat(archive).doesNotExist();
    }

    @Test
    void rejectsEntryHeaderLargerThanConfiguredBoundBeforeExtraction() throws Exception {
        byte[] entry = bsonBytes(new Document("member", "Ada"));
        Path archive = writeManualArchive(
            "large-entry.zip",
            manifestJson(1, List.of(collection("members", ENTRY_NAME, 1, entry.length, sha256(entry)))),
            List.of(new RawEntry(ENTRY_NAME, entry, true, EncryptionMethod.AES))
        );
        ArchivePackageService bounded = new ArchivePackageService(codec, 4096, entry.length - 1L);

        assertThatThrownBy(() -> bounded.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("entry size");
    }

    @Test
    void writeRejectsEntryLargerThanConfiguredBound() throws Exception {
        Path entry = writeEntry("writer-large-entry.bson", new Document("member", "Ada"));
        Path archive = tempDir.resolve("writer-large-entry.zip");
        ArchivePackageService bounded = new ArchivePackageService(codec, 4096, Files.size(entry) - 1);
        ArchiveManifest manifest = new ArchiveManifest(
            1,
            ArchiveType.FULL_BACKUP,
            List.of(new ArchiveCollectionManifest("members", ENTRY_NAME))
        );

        assertThatThrownBy(() -> bounded.write(archive, PASSWORD, manifest, Map.of(ENTRY_NAME, entry)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("entry size");
        assertThat(archive).doesNotExist();
    }

    @Test
    void rejectsEntryHeaderThatDisagreesWithDeclaredSize() throws Exception {
        byte[] entry = bsonBytes(new Document("member", "Ada"));
        Path archive = writeManualArchive(
            "declared-size.zip",
            manifestJson(1, List.of(collection("members", ENTRY_NAME, 1, entry.length - 1L, sha256(entry)))),
            List.of(new RawEntry(ENTRY_NAME, entry, true, EncryptionMethod.AES))
        );

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("entry size");
    }

    @Test
    void clearsValidationPasswordCopyWhenTempDirectoryCreationFails() {
        IOException creationFailure = new IOException("temp directory unavailable");
        class FailingTempDirectoryService extends ArchivePackageService {
            private char[] capturedPasswordCopy;

            @Override
            char[] copyPassword(char[] password) {
                capturedPasswordCopy = super.copyPassword(password);
                return capturedPasswordCopy;
            }

            @Override
            Path createExtractionDirectory() throws IOException {
                throw creationFailure;
            }
        }
        FailingTempDirectoryService failingService = new FailingTempDirectoryService();
        char[] suppliedPassword = "validation secret".toCharArray();

        assertThatThrownBy(() -> failingService.validate(
            tempDir.resolve("unread.zip"), suppliedPassword, ArchiveType.FULL_BACKUP
        )).isSameAs(creationFailure);

        assertThat(failingService.capturedPasswordCopy).containsOnly('\0');
        assertThat(suppliedPassword).containsExactly("validation secret".toCharArray());
    }

    @Test
    void closeReportsCleanupFailure() throws Exception {
        Path extractionDirectory = Files.createDirectory(tempDir.resolve("close-failure"));
        IOException cleanupFailure = new IOException("cleanup failed");
        ArchivePackageService.ValidatedArchive validated = new ArchivePackageService.ValidatedArchive(
            new ArchiveManifest(1, ArchiveType.FULL_BACKUP, List.of()),
            Map.of(),
            extractionDirectory,
            ignored -> { throw cleanupFailure; }
        );

        assertThatThrownBy(validated::close).isSameAs(cleanupFailure);
    }

    @Test
    void validationFailureRetainsCleanupFailureAsSuppressedEvidence() throws Exception {
        Path archive = writeArchive(ArchiveType.FULL_BACKUP);
        IOException cleanupFailure = new IOException("cleanup failed");
        ArchivePackageService failingCleanup = new ArchivePackageService(
            codec,
            4096,
            4096,
            ignored -> { throw cleanupFailure; }
        );

        assertThatThrownBy(() -> failingCleanup.validate(archive, PASSWORD, ArchiveType.RESTORE))
            .isInstanceOf(IllegalArgumentException.class)
            .satisfies(exception -> assertThat(exception.getSuppressed()).containsExactly(cleanupFailure));
    }

    private Path writeArchive(ArchiveType archiveType) throws Exception {
        Path entry = writeEntry("members.bson", new Document("member", "Ada"));
        Path archive = tempDir.resolve("archive-" + archiveType + ".zip");
        ArchiveManifest manifest = new ArchiveManifest(
            1,
            archiveType,
            List.of(new ArchiveCollectionManifest("members", ENTRY_NAME))
        );
        service.write(archive, PASSWORD, manifest, Map.of(ENTRY_NAME, entry));
        return archive;
    }

    private Path writeEntry(String fileName, Document... documents) throws Exception {
        Path entry = tempDir.resolve(fileName);
        Files.write(entry, bsonBytes(documents));
        return entry;
    }

    private byte[] bsonBytes(Document... documents) throws Exception {
        ByteArrayOutputStream bson = new ByteArrayOutputStream();
        codec.write(List.of(documents), bson);
        return bson.toByteArray();
    }

    private Path writeManualArchive(
        String fileName,
        String manifestJson,
        List<RawEntry> entries
    ) throws Exception {
        Path archive = tempDir.resolve(fileName);
        try (ZipFile zipFile = new ZipFile(archive.toFile(), PASSWORD)) {
            for (RawEntry entry : entries) {
                zipFile.addStream(new ByteArrayInputStream(entry.bytes()), parameters(entry));
            }
            zipFile.addStream(
                new ByteArrayInputStream(manifestJson.getBytes(StandardCharsets.UTF_8)),
                encryptedParameters("manifest.json")
            );
        }
        return archive;
    }

    private String manifestJson(Object formatVersion, List<Document> collections) {
        return new Document("formatVersion", formatVersion)
            .append("archiveType", ArchiveType.FULL_BACKUP.name())
            .append("collections", collections)
            .toJson();
    }

    private String manifestJson(String version, String count, String size, byte[] entry) throws Exception {
        return "{\"formatVersion\":" + version
            + ",\"archiveType\":\"FULL_BACKUP\",\"collections\":[{\"collection\":\"members\""
            + ",\"entryName\":\"" + ENTRY_NAME + "\",\"documentCount\":" + count
            + ",\"sizeBytes\":" + size + ",\"sha256\":\"" + sha256(entry) + "\"}]}";
    }

    private Document collection(String name, String entryName, long count, long size, String sha256) {
        return new Document("collection", name)
            .append("entryName", entryName)
            .append("documentCount", count)
            .append("sizeBytes", size)
            .append("sha256", sha256);
    }

    private ZipParameters parameters(RawEntry entry) {
        ZipParameters parameters = new ZipParameters();
        parameters.setFileNameInZip(entry.name());
        parameters.setCompressionMethod(CompressionMethod.DEFLATE);
        parameters.setEncryptFiles(entry.encrypted());
        if (entry.encrypted()) {
            parameters.setEncryptionMethod(entry.encryptionMethod());
        }
        return parameters;
    }

    private ZipParameters encryptedParameters(String fileName) {
        return parameters(new RawEntry(fileName, new byte[0], true, EncryptionMethod.AES));
    }

    private String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private record RawEntry(String name, byte[] bytes, boolean encrypted, EncryptionMethod encryptionMethod) {
    }
}

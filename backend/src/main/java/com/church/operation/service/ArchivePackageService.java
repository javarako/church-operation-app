package com.church.operation.service;

import com.church.operation.dto.ArchiveCollectionManifest;
import com.church.operation.dto.ArchiveManifest;
import com.church.operation.util.ArchiveType;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.bson.Document;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArchivePackageService {
    public static final int FORMAT_VERSION = 1;

    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final int DEFAULT_MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final long DEFAULT_MAX_ENTRY_BYTES = 2L * 1024 * 1024 * 1024;
    private static final ObjectReader MANIFEST_READER = new ObjectMapper().rebuild()
        .enable(
            DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
            DeserializationFeature.USE_BIG_INTEGER_FOR_INTS
        )
        .build()
        .reader();

    private final BsonStreamCodec bsonStreamCodec;
    private final int maxManifestBytes;
    private final long maxEntryBytes;
    private final DirectoryCleaner directoryCleaner;

    public ArchivePackageService() {
        this(new BsonStreamCodec(), DEFAULT_MAX_MANIFEST_BYTES, DEFAULT_MAX_ENTRY_BYTES);
    }

    public ArchivePackageService(int maxManifestBytes, long maxEntryBytes) {
        this(new BsonStreamCodec(), maxManifestBytes, maxEntryBytes);
    }

    ArchivePackageService(BsonStreamCodec bsonStreamCodec) {
        this(bsonStreamCodec, DEFAULT_MAX_MANIFEST_BYTES, DEFAULT_MAX_ENTRY_BYTES);
    }

    ArchivePackageService(BsonStreamCodec bsonStreamCodec, int maxManifestBytes, long maxEntryBytes) {
        this(bsonStreamCodec, maxManifestBytes, maxEntryBytes, ArchivePackageService::deleteDirectory);
    }

    ArchivePackageService(
        BsonStreamCodec bsonStreamCodec,
        int maxManifestBytes,
        long maxEntryBytes,
        DirectoryCleaner directoryCleaner
    ) {
        if (bsonStreamCodec == null || directoryCleaner == null || maxManifestBytes < 1 || maxEntryBytes < 1) {
            throw new IllegalArgumentException("Archive validation limits and dependencies are required.");
        }
        this.bsonStreamCodec = bsonStreamCodec;
        this.maxManifestBytes = maxManifestBytes;
        this.maxEntryBytes = maxEntryBytes;
        this.directoryCleaner = directoryCleaner;
    }

    public void write(Path output, char[] password, ArchiveManifest manifest, Map<String, Path> entries) throws IOException {
        validateWriteInput(manifest, entries);
        List<ArchiveCollectionManifest> collections = new ArrayList<>();
        for (ArchiveCollectionManifest collection : manifest.collections()) {
            Path source = entries.get(collection.entryName());
            collections.add(new ArchiveCollectionManifest(
                collection.collection(),
                collection.entryName(),
                documentCount(source),
                Files.size(source),
                sha256(source)
            ));
        }
        ArchiveManifest computedManifest = new ArchiveManifest(
            manifest.formatVersion(), manifest.archiveType(), List.copyOf(collections)
        );
        byte[] manifestBytes = manifestDocument(computedManifest).toJson().getBytes(StandardCharsets.UTF_8);
        if (manifestBytes.length > maxManifestBytes) {
            throw new IllegalArgumentException("Archive manifest size exceeds the limit.");
        }
        char[] passwordCopy = copyPassword(password);
        try {
            Files.deleteIfExists(output);
            try (ZipFile zipFile = new ZipFile(output.toFile(), passwordCopy)) {
                for (ArchiveCollectionManifest collection : manifest.collections()) {
                    Path source = entries.get(collection.entryName());
                    zipFile.addFile(source.toFile(), encryptedParameters(collection.entryName()));
                }
                zipFile.addStream(
                    new ByteArrayInputStream(manifestBytes),
                    encryptedParameters(MANIFEST_ENTRY)
                );
            }
        } catch (ZipException exception) {
            throw new IOException("Unable to write encrypted archive.", exception);
        } finally {
            Arrays.fill(passwordCopy, '\0');
        }
    }

    public ValidatedArchive validate(Path archive, char[] password, ArchiveType expected) throws IOException {
        char[] passwordCopy = copyPassword(password);
        Path extractionDirectory = null;
        try {
            extractionDirectory = createExtractionDirectory();
            try (ZipFile zipFile = new ZipFile(archive.toFile(), passwordCopy)) {
                Map<String, FileHeader> headers = validatedHeaders(zipFile);
                FileHeader manifestHeader = headers.get(MANIFEST_ENTRY);
                if (manifestHeader == null) {
                    throw new IllegalArgumentException("Archive manifest is missing.");
                }
                validateManifestHeader(manifestHeader);
                ArchiveManifest manifest;
                try (InputStream input = zipFile.getInputStream(manifestHeader)) {
                    byte[] bytes = readBounded(input, maxManifestBytes, "Archive manifest size exceeds the limit.");
                    manifest = parseManifest(new String(bytes, StandardCharsets.UTF_8));
                }
                validateManifest(manifest, expected);
                Set<String> declaredEntries = new HashSet<>();
                declaredEntries.add(MANIFEST_ENTRY);
                Map<String, Path> extractedEntries = new HashMap<>();
                for (ArchiveCollectionManifest collection : manifest.collections()) {
                    String entryName = normalizeEntryName(collection.entryName());
                    if (!declaredEntries.add(entryName)) {
                        throw new IllegalArgumentException("Archive contains duplicate manifest entries.");
                    }
                    FileHeader header = headers.get(entryName);
                    if (header == null) {
                        throw new IllegalArgumentException("Archive is missing a declared entry.");
                    }
                    validateEntryHeader(header, collection);
                    Path extracted = extractionDirectory.resolve(entryName).normalize();
                    if (!extracted.startsWith(extractionDirectory)) {
                        throw new IllegalArgumentException("Archive entry has an invalid path.");
                    }
                    Files.createDirectories(extracted.getParent());
                    try (InputStream input = zipFile.getInputStream(header)) {
                        writeAndVerify(input, extracted, collection);
                    }
                    extractedEntries.put(entryName, extracted);
                }
                if (!headers.keySet().equals(declaredEntries)) {
                    throw new IllegalArgumentException("Archive contains unlisted entries.");
                }
                return new ValidatedArchive(
                    manifest, Map.copyOf(extractedEntries), extractionDirectory, directoryCleaner
                );
            }
        } catch (ZipException exception) {
            IllegalArgumentException failure = new IllegalArgumentException("Unable to read encrypted archive.", exception);
            cleanupAfterFailure(extractionDirectory, failure);
            throw failure;
        } catch (IOException | RuntimeException exception) {
            cleanupAfterFailure(extractionDirectory, exception);
            throw exception;
        } finally {
            Arrays.fill(passwordCopy, '\0');
        }
    }

    private void validateWriteInput(ArchiveManifest manifest, Map<String, Path> entries) throws IOException {
        if (manifest == null || manifest.collections() == null || entries == null) {
            throw new IllegalArgumentException("Archive manifest and entries are required.");
        }
        if (manifest.formatVersion() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported archive format version.");
        }
        if (manifest.archiveType() == null) {
            throw new IllegalArgumentException("Archive type is required.");
        }
        Set<String> names = new HashSet<>();
        Set<String> collectionNames = new HashSet<>();
        for (ArchiveCollectionManifest collection : manifest.collections()) {
            if (collection == null || !names.add(normalizeEntryName(collection.entryName()))
                || !entries.containsKey(collection.entryName())) {
                throw new IllegalArgumentException("Archive entries must be unique and declared.");
            }
            if (collection.collection() == null || collection.collection().isBlank()
                || !collectionNames.add(collection.collection())) {
                throw new IllegalArgumentException("Archive collection names must be unique and nonblank.");
            }
            if (collection.documentCount() < 0 || collection.sizeBytes() < 0) {
                throw new IllegalArgumentException("Archive manifest contains an invalid number.");
            }
            if (Files.size(entries.get(collection.entryName())) > maxEntryBytes) {
                throw new IllegalArgumentException("Archive entry size exceeds the limit.");
            }
        }
        if (entries.size() != names.size() || entries.containsKey(MANIFEST_ENTRY)) {
            throw new IllegalArgumentException("Archive entries must match the manifest.");
        }
    }

    private Map<String, FileHeader> validatedHeaders(ZipFile zipFile) throws ZipException {
        Map<String, FileHeader> headers = new HashMap<>();
        for (FileHeader header : zipFile.getFileHeaders()) {
            if (header.isDirectory() || isLink(header)) {
                throw new IllegalArgumentException("Archive contains an invalid entry.");
            }
            if (!header.isEncrypted() || header.getEncryptionMethod() != EncryptionMethod.AES) {
                throw new IllegalArgumentException("Archive entries must use AES encryption.");
            }
            String entryName = normalizeEntryName(header.getFileName());
            if (headers.put(entryName, header) != null) {
                throw new IllegalArgumentException("Archive contains an invalid entry.");
            }
        }
        return headers;
    }

    private void validateManifest(ArchiveManifest manifest, ArchiveType expected) {
        if (manifest == null || manifest.formatVersion() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported archive format version.");
        }
        if (manifest.archiveType() != expected) {
            throw new IllegalArgumentException("Archive type does not match the requested operation.");
        }
        if (manifest.collections() == null) {
            throw new IllegalArgumentException("Archive manifest collections are missing.");
        }
        Set<String> entryNames = new HashSet<>();
        Set<String> collectionNames = new HashSet<>();
        for (ArchiveCollectionManifest collection : manifest.collections()) {
            if (collection == null || !entryNames.add(normalizeEntryName(collection.entryName()))) {
                throw new IllegalArgumentException("Archive contains duplicate manifest entries.");
            }
            if (collection.collection() == null || collection.collection().isBlank()
                || !collectionNames.add(collection.collection())) {
                throw new IllegalArgumentException("Archive collection names must be unique and nonblank.");
            }
        }
    }

    private Document manifestDocument(ArchiveManifest manifest) {
        List<Document> collections = manifest.collections().stream()
            .map(collection -> new Document("collection", collection.collection())
                .append("entryName", collection.entryName())
                .append("documentCount", collection.documentCount())
                .append("sizeBytes", collection.sizeBytes())
                .append("sha256", collection.sha256()))
            .toList();
        return new Document("formatVersion", manifest.formatVersion())
            .append("archiveType", manifest.archiveType().name())
            .append("collections", collections);
    }

    private ArchiveManifest parseManifest(String json) {
        JsonNode document;
        try {
            document = MANIFEST_READER.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Archive manifest is invalid.", exception);
        }
        JsonNode collectionDocuments = document == null ? null : document.get("collections");
        if (collectionDocuments == null || !collectionDocuments.isArray()) {
            throw new IllegalArgumentException("Archive manifest collections are missing.");
        }
        List<ArchiveCollectionManifest> collections = new ArrayList<>();
        for (JsonNode collection : collectionDocuments) {
            collections.add(new ArchiveCollectionManifest(
                textValue(collection, "collection"),
                textValue(collection, "entryName"),
                nonNegativeLong(collection, "documentCount"),
                nonNegativeLong(collection, "sizeBytes"),
                textValue(collection, "sha256")
            ));
        }
        return new ArchiveManifest(
            nonNegativeInt(document, "formatVersion"),
            ArchiveType.valueOf(textValue(document, "archiveType")),
            List.copyOf(collections)
        );
    }

    private String textValue(JsonNode document, String key) {
        JsonNode value = document == null ? null : document.get(key);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException("Archive manifest is invalid.");
        }
        return value.stringValue();
    }

    private long nonNegativeLong(JsonNode document, String key) {
        JsonNode value = document == null ? null : document.get(key);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException("Archive manifest contains an invalid number.");
        }
        try {
            long result = value.decimalValue().longValueExact();
            if (result < 0) {
                throw new ArithmeticException("negative");
            }
            return result;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Archive manifest contains an invalid number.", exception);
        }
    }

    private int nonNegativeInt(JsonNode document, String key) {
        long value = nonNegativeLong(document, key);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Archive manifest contains an invalid number.");
        }
        return (int) value;
    }

    private void validateManifestHeader(FileHeader header) {
        long size = header.getUncompressedSize();
        if (size < 0 || size > maxManifestBytes) {
            throw new IllegalArgumentException("Archive manifest size exceeds the limit.");
        }
    }

    private void validateEntryHeader(FileHeader header, ArchiveCollectionManifest collection) {
        long size = header.getUncompressedSize();
        if (size < 0 || size > maxEntryBytes || size != collection.sizeBytes()) {
            throw new IllegalArgumentException("Archive entry size does not match the manifest or exceeds the limit.");
        }
    }

    private byte[] readBounded(InputStream input, long limit, String message) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long size = 0;
        for (int read; (read = input.read(buffer)) >= 0;) {
            if (read == 0) {
                continue;
            }
            if (size > limit - read) {
                throw new IllegalArgumentException(message);
            }
            output.write(buffer, 0, read);
            size += read;
        }
        return output.toByteArray();
    }

    private void writeAndVerify(InputStream input, Path output, ArchiveCollectionManifest collection) throws IOException {
        MessageDigest digest = sha256Digest();
        long size = 0;
        try (var target = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) {
                if (read == 0) {
                    continue;
                }
                if (size > maxEntryBytes - read) {
                    throw new IllegalArgumentException("Archive entry size exceeds the limit.");
                }
                target.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                size += read;
            }
        }
        if (size != collection.sizeBytes()) {
            throw new IllegalArgumentException("Archive entry size does not match the manifest.");
        }
        if (!hex(digest.digest()).equals(collection.sha256())) {
            throw new IllegalArgumentException("Archive entry checksum does not match the manifest.");
        }
        if (documentCount(output) != collection.documentCount()) {
            throw new IllegalArgumentException("Archive entry document count does not match the manifest.");
        }
    }

    private String normalizeEntryName(String entryName) {
        if (entryName == null || entryName.isBlank() || entryName.startsWith("/") || entryName.startsWith("\\")
            || entryName.matches("^[A-Za-z]:.*") || entryName.contains("\\")) {
            throw new IllegalArgumentException("Archive entry has an invalid path.");
        }
        Path normalized = Path.of(entryName).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..") || !normalized.toString().replace('\\', '/').equals(entryName)) {
            throw new IllegalArgumentException("Archive entry has an invalid path.");
        }
        return normalized.toString().replace('\\', '/');
    }

    private boolean isLink(FileHeader header) {
        byte[] attributes = header.getExternalFileAttributes();
        if (attributes == null || attributes.length < 4) {
            return false;
        }
        int mode = ((attributes[3] & 0xff) << 8) | (attributes[2] & 0xff);
        return (mode & 0170000) == 0120000;
    }

    private ZipParameters encryptedParameters(String entryName) {
        ZipParameters parameters = new ZipParameters();
        parameters.setFileNameInZip(entryName);
        parameters.setCompressionMethod(CompressionMethod.DEFLATE);
        parameters.setEncryptFiles(true);
        parameters.setEncryptionMethod(EncryptionMethod.AES);
        return parameters;
    }

    char[] copyPassword(char[] password) {
        return Arrays.copyOf(password, password.length);
    }

    Path createExtractionDirectory() throws IOException {
        return Files.createTempDirectory("church-archive-");
    }

    private long documentCount(Path source) throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            return bsonStreamCodec.count(input);
        }
    }

    private String sha256(Path source) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream input = Files.newInputStream(source)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte current : value) {
            result.append(String.format("%02x", current));
        }
        return result.toString();
    }

    private void cleanupAfterFailure(Path directory, Throwable failure) {
        if (directory == null) {
            return;
        }
        try {
            directoryCleaner.delete(directory);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        List<Path> paths;
        try (var stream = Files.walk(directory)) {
            paths = stream.sorted(java.util.Comparator.reverseOrder()).toList();
        }
        IOException failure = null;
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @FunctionalInterface
    interface DirectoryCleaner {
        void delete(Path directory) throws IOException;
    }

    public static final class ValidatedArchive implements AutoCloseable {
        private final ArchiveManifest manifest;
        private final Map<String, Path> entries;
        private final Path extractionDirectory;
        private final DirectoryCleaner directoryCleaner;

        public ValidatedArchive(
            ArchiveManifest manifest,
            Map<String, Path> entries,
            Path extractionDirectory
        ) {
            this(manifest, entries, extractionDirectory, ArchivePackageService::deleteDirectory);
        }

        ValidatedArchive(
            ArchiveManifest manifest,
            Map<String, Path> entries,
            Path extractionDirectory,
            DirectoryCleaner directoryCleaner
        ) {
            this.manifest = manifest;
            this.entries = entries;
            this.extractionDirectory = extractionDirectory;
            this.directoryCleaner = directoryCleaner;
        }

        public ArchiveManifest manifest() {
            return manifest;
        }

        public Map<String, Path> entries() {
            return entries;
        }

        public Path extractionDirectory() {
            return extractionDirectory;
        }

        @Override
        public void close() throws IOException {
            directoryCleaner.delete(extractionDirectory);
        }
    }
}

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

    private final BsonStreamCodec bsonStreamCodec;

    public ArchivePackageService() {
        this(new BsonStreamCodec());
    }

    ArchivePackageService(BsonStreamCodec bsonStreamCodec) {
        this.bsonStreamCodec = bsonStreamCodec;
    }

    public void write(Path output, char[] password, ArchiveManifest manifest, Map<String, Path> entries) throws IOException {
        validateWriteInput(manifest, entries);
        char[] passwordCopy = Arrays.copyOf(password, password.length);
        try {
            Files.deleteIfExists(output);
            ZipFile zipFile = new ZipFile(output.toFile(), passwordCopy);
            List<ArchiveCollectionManifest> collections = new ArrayList<>();
            for (ArchiveCollectionManifest collection : manifest.collections()) {
                Path source = entries.get(collection.entryName());
                zipFile.addFile(source.toFile(), encryptedParameters(collection.entryName()));
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
            Path manifestFile = Files.createTempFile("church-archive-manifest-", ".json");
            try {
                Files.writeString(manifestFile, manifestDocument(computedManifest).toJson(), StandardCharsets.UTF_8);
                zipFile.addFile(manifestFile.toFile(), encryptedParameters(MANIFEST_ENTRY));
            } finally {
                Files.deleteIfExists(manifestFile);
            }
        } catch (ZipException exception) {
            throw new IOException("Unable to write encrypted archive.", exception);
        } finally {
            Arrays.fill(passwordCopy, '\0');
        }
    }

    public ValidatedArchive validate(Path archive, char[] password, ArchiveType expected) throws IOException {
        char[] passwordCopy = Arrays.copyOf(password, password.length);
        Path extractionDirectory = Files.createTempDirectory("church-archive-");
        try {
            ZipFile zipFile = new ZipFile(archive.toFile(), passwordCopy);
            Map<String, FileHeader> headers = validatedHeaders(zipFile);
            FileHeader manifestHeader = headers.get(MANIFEST_ENTRY);
            if (manifestHeader == null) {
                throw new IllegalArgumentException("Archive manifest is missing.");
            }
            ArchiveManifest manifest;
            try (InputStream input = zipFile.getInputStream(manifestHeader)) {
                manifest = parseManifest(new String(input.readAllBytes(), StandardCharsets.UTF_8));
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
            return new ValidatedArchive(manifest, Map.copyOf(extractedEntries), extractionDirectory);
        } catch (ZipException exception) {
            deleteDirectory(extractionDirectory);
            throw new IllegalArgumentException("Unable to read encrypted archive.", exception);
        } catch (IOException | RuntimeException exception) {
            deleteDirectory(extractionDirectory);
            throw exception;
        } finally {
            Arrays.fill(passwordCopy, '\0');
        }
    }

    private void validateWriteInput(ArchiveManifest manifest, Map<String, Path> entries) {
        if (manifest == null || manifest.collections() == null || entries == null) {
            throw new IllegalArgumentException("Archive manifest and entries are required.");
        }
        Set<String> names = new HashSet<>();
        for (ArchiveCollectionManifest collection : manifest.collections()) {
            if (collection == null || !names.add(normalizeEntryName(collection.entryName()))
                || !entries.containsKey(collection.entryName())) {
                throw new IllegalArgumentException("Archive entries must be unique and declared.");
            }
        }
        if (entries.size() != names.size() || entries.containsKey(MANIFEST_ENTRY)) {
            throw new IllegalArgumentException("Archive entries must match the manifest.");
        }
    }

    private Map<String, FileHeader> validatedHeaders(ZipFile zipFile) throws ZipException {
        Map<String, FileHeader> headers = new HashMap<>();
        for (FileHeader header : zipFile.getFileHeaders()) {
            String entryName = normalizeEntryName(header.getFileName());
            if (header.isDirectory() || isLink(header) || !header.isEncrypted() || headers.put(entryName, header) != null) {
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
        Document document = Document.parse(json);
        List<Document> collectionDocuments = document.getList("collections", Document.class);
        List<ArchiveCollectionManifest> collections = collectionDocuments.stream()
            .map(collection -> new ArchiveCollectionManifest(
                collection.getString("collection"),
                collection.getString("entryName"),
                number(collection, "documentCount").longValue(),
                number(collection, "sizeBytes").longValue(),
                collection.getString("sha256")
            ))
            .toList();
        return new ArchiveManifest(
            number(document, "formatVersion").intValue(),
            ArchiveType.valueOf(document.getString("archiveType")),
            collections
        );
    }

    private Number number(Document document, String key) {
        Object value = document.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Archive manifest contains an invalid number.");
        }
        return number;
    }

    private void writeAndVerify(InputStream input, Path output, ArchiveCollectionManifest collection) throws IOException {
        MessageDigest digest = sha256Digest();
        long size = 0;
        try (var target = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) {
                target.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                size += read;
            }
        }
        if (size != collection.sizeBytes() || !hex(digest.digest()).equals(collection.sha256())) {
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

    private long documentCount(Path source) throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            return bsonStreamCodec.read(input).size();
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

    private void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup after validation errors.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup after validation errors.
        }
    }

    public record ValidatedArchive(ArchiveManifest manifest, Map<String, Path> entries, Path extractionDirectory)
        implements AutoCloseable {
        @Override
        public void close() {
            if (extractionDirectory != null && Files.exists(extractionDirectory)) {
                try (var paths = Files.walk(extractionDirectory)) {
                    paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Best-effort cleanup for extracted archive data.
                        }
                    });
                } catch (IOException ignored) {
                    // Best-effort cleanup for extracted archive data.
                }
            }
        }
    }
}

package com.church.operation.service;

import com.church.operation.dto.ArchiveCollectionManifest;
import com.church.operation.dto.ArchiveManifest;
import com.church.operation.util.ArchiveType;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchivePackageServiceTest {
    private static final char[] PASSWORD = "correct horse battery staple".toCharArray();

    @TempDir
    Path tempDir;

    private final ArchivePackageService service = new ArchivePackageService();

    @Test
    void validatesEncryptedPackageWithCorrectPassword() throws Exception {
        Path archive = writeArchive(1, ArchiveType.FULL_BACKUP);

        ArchivePackageService.ValidatedArchive validated = service.validate(
            archive, PASSWORD, ArchiveType.FULL_BACKUP
        );

        assertThat(validated.manifest().formatVersion()).isEqualTo(1);
        assertThat(validated.manifest().archiveType()).isEqualTo(ArchiveType.FULL_BACKUP);
        assertThat(validated.entries()).containsKey("collections/members.bson");
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        Path archive = writeArchive(1, ArchiveType.FULL_BACKUP);

        assertThatThrownBy(() -> service.validate(
            archive, "not the password".toCharArray(), ArchiveType.FULL_BACKUP
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsChangedEntryBytes() throws Exception {
        Path archive = writeArchive(1, ArchiveType.FULL_BACKUP);
        ZipFile zipFile = new ZipFile(archive.toFile(), PASSWORD);
        zipFile.removeFile("collections/members.bson");
        ZipParameters parameters = encryptedParameters("collections/members.bson");
        zipFile.addStream(new ByteArrayInputStream(new byte[] {9, 9, 9}), parameters);

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("checksum");
    }

    @Test
    void rejectsWrongArchiveType() throws Exception {
        Path archive = writeArchive(1, ArchiveType.FULL_BACKUP);

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.RESTORE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("type");
    }

    @Test
    void rejectsUnsupportedFormatVersion() throws Exception {
        Path archive = writeArchive(2, ArchiveType.FULL_BACKUP);

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("version");
    }

    @Test
    void rejectsPathTraversalEntryNames() throws Exception {
        Path archive = writeArchive(1, ArchiveType.FULL_BACKUP);
        ZipFile zipFile = new ZipFile(archive.toFile(), PASSWORD);
        zipFile.addStream(new ByteArrayInputStream(new byte[] {1}), encryptedParameters("../escape.bson"));

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("path");
    }

    @Test
    void rejectsUnlistedEntries() throws Exception {
        Path archive = writeArchive(1, ArchiveType.FULL_BACKUP);
        ZipFile zipFile = new ZipFile(archive.toFile(), PASSWORD);
        zipFile.addStream(new ByteArrayInputStream(new byte[] {1}), encryptedParameters("extra.bson"));

        assertThatThrownBy(() -> service.validate(archive, PASSWORD, ArchiveType.FULL_BACKUP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unlisted");
    }

    private Path writeArchive(int formatVersion, ArchiveType archiveType) throws Exception {
        Path entry = tempDir.resolve("members.bson");
        ByteArrayOutputStream bson = new ByteArrayOutputStream();
        new BsonStreamCodec().write(List.of(new Document("member", "Ada")), bson);
        Files.write(entry, bson.toByteArray());
        Path archive = tempDir.resolve("archive-" + formatVersion + "-" + archiveType + ".zip");
        ArchiveManifest manifest = new ArchiveManifest(
            formatVersion,
            archiveType,
            List.of(new ArchiveCollectionManifest("members", "collections/members.bson"))
        );
        service.write(archive, PASSWORD, manifest, Map.of("collections/members.bson", entry));
        return archive;
    }

    private ZipParameters encryptedParameters(String fileName) {
        ZipParameters parameters = new ZipParameters();
        parameters.setFileNameInZip(fileName);
        parameters.setCompressionMethod(CompressionMethod.DEFLATE);
        parameters.setEncryptFiles(true);
        parameters.setEncryptionMethod(EncryptionMethod.AES);
        return parameters;
    }
}

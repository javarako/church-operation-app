package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.entity.ChurchSettings;
import com.church.operation.exception.ChurchBrandingValidationException;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.BsonObjectId;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.zip.CRC32;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChurchBrandingServiceTest {
    @Mock private GridFsTemplate gridFsTemplate;

    private ChurchBrandingService service;

    @BeforeEach
    void setUp() {
        ChurchInformationProperties properties = new ChurchInformationProperties(
            new ChurchInformationProperties.Information("Church", "", "", "", "", "", ""),
            new ChurchInformationProperties.Branding(
                "/branding/church-banner.png",
                "/branding/church_logo.png"
            ),
            new ChurchInformationProperties.Ui(20)
        );
        service = new ChurchBrandingService(gridFsTemplate, properties);
    }

    @Test
    void storesDecodedPngAndJpegUsingDetectedContentType() throws IOException {
        ObjectId pngId = new ObjectId("64b000000000000000000001");
        ObjectId jpegId = new ObjectId("64b000000000000000000002");
        when(gridFsTemplate.store(any(InputStream.class), any(String.class), eq("image/png"), any(Document.class)))
            .thenReturn(pngId);
        when(gridFsTemplate.store(any(InputStream.class), any(String.class), eq("image/jpeg"), any(Document.class)))
            .thenReturn(jpegId);

        ChurchBrandingService.StoredBranding png = service.store(file("logo.png", "image/png", image(2, 2, "png")), "logo");
        ChurchBrandingService.StoredBranding jpeg = service.store(file("banner.jpg", "image/jpeg", image(2, 2, "jpg")), "banner");

        assertThat(png).isEqualTo(new ChurchBrandingService.StoredBranding(pngId.toHexString(), "image/png"));
        assertThat(jpeg).isEqualTo(new ChurchBrandingService.StoredBranding(jpegId.toHexString(), "image/jpeg"));
    }

    @Test
    void rejectsSpoofedAndOversizedFilesBeforeGridFsWrite() {
        assertThatThrownBy(() -> service.store(
            file("fake.png", "image/png", "not-an-image".getBytes(UTF_8)), "logo"
        )).isInstanceOf(ChurchBrandingValidationException.class);

        assertThatThrownBy(() -> service.store(
            file("large.png", "image/png", new byte[5 * 1024 * 1024 + 1]), "logo"
        )).isInstanceOf(ChurchBrandingValidationException.class)
            .hasMessageContaining("5 MB");

        verifyNoInteractions(gridFsTemplate);
    }

    @Test
    void rejectsImagesBeyondDimensionAndPixelLimits() throws IOException {
        assertThatThrownBy(() -> service.store(
            file("wide.png", "image/png", pngWithDimensions(8001, 1)), "banner"
        )).isInstanceOf(ChurchBrandingValidationException.class)
            .hasMessageContaining("dimensions");

        assertThatThrownBy(() -> service.store(
            file("dense.png", "image/png", pngWithDimensions(8000, 5001)), "banner"
        )).isInstanceOf(ChurchBrandingValidationException.class)
            .hasMessageContaining("dimensions");
    }

    @Test
    void loadsStoredBrandingAndFallsBackToBundledLogo() throws IOException {
        String id = "64b000000000000000000003";
        byte[] storedBytes = image(2, 2, "png");
        GridFSFile storedFile = new GridFSFile(
            new BsonObjectId(new ObjectId(id)),
            "church-logo.png",
            storedBytes.length,
            255,
            new Date(),
            new Document("contentType", "image/png")
        );
        GridFsResource resource = org.mockito.Mockito.mock(GridFsResource.class);
        when(gridFsTemplate.findOne(any(Query.class))).thenReturn(storedFile);
        when(gridFsTemplate.getResource(storedFile)).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(storedBytes));

        ChurchBrandingService.BrandingContent content = service.databaseFile(id).orElseThrow();
        assertThat(content.bytes()).isEqualTo(storedBytes);
        assertThat(content.contentType()).isEqualTo("image/png");

        ChurchSettings defaults = new ChurchSettings();
        assertThat(service.effectiveLogoBytes(defaults)).isNotEmpty();
    }

    @Test
    void missingFilesReturnEmptyAndDeletionIsIdempotent() {
        when(gridFsTemplate.findOne(any(Query.class))).thenReturn(null);
        doThrow(new IllegalStateException("already gone")).when(gridFsTemplate).delete(any(Query.class));

        assertThat(service.databaseFile("64b000000000000000000004")).isEmpty();
        assertThat(service.databaseFile("not-an-object-id")).isEmpty();
        assertThatCode(() -> service.deleteQuietly("64b000000000000000000004")).doesNotThrowAnyException();

        verify(gridFsTemplate).delete(any(Query.class));
    }

    private MockMultipartFile file(String filename, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", filename, contentType, bytes);
    }

    private byte[] image(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private byte[] pngWithDimensions(int width, int height) throws IOException {
        byte[] bytes = image(1, 1, "png");
        writeInt(bytes, 16, width);
        writeInt(bytes, 20, height);
        CRC32 crc = new CRC32();
        crc.update(bytes, 12, 17);
        writeInt(bytes, 29, (int) crc.getValue());
        return bytes;
    }

    private void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}

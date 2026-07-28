package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.entity.ChurchSettings;
import com.church.operation.exception.ChurchBrandingValidationException;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChurchBrandingService {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_DIMENSION = 8_000;
    private static final long MAX_PIXELS = 40_000_000L;

    private final GridFsTemplate gridFsTemplate;
    private final ChurchInformationProperties properties;

    public ChurchBrandingService(
        GridFsTemplate gridFsTemplate,
        ChurchInformationProperties properties
    ) {
        this.gridFsTemplate = gridFsTemplate;
        this.properties = properties;
    }

    public StoredBranding store(MultipartFile file, String kind) {
        if (!"logo".equals(kind) && !"banner".equals(kind)) {
            throw new ChurchBrandingValidationException("Branding type must be logo or banner.");
        }
        ValidatedImage image = validate(file);
        String extension = "image/png".equals(image.contentType()) ? ".png" : ".jpg";
        String filename = "church-" + kind + "-" + UUID.randomUUID() + extension;
        Document metadata = new Document()
            .append("kind", kind)
            .append("contentType", image.contentType())
            .append("uploadedAt", Instant.now());
        ObjectId id = gridFsTemplate.store(
            new ByteArrayInputStream(image.bytes()), filename, image.contentType(), metadata
        );
        return new StoredBranding(id.toHexString(), image.contentType());
    }

    public Optional<BrandingContent> databaseFile(String id) {
        ObjectId objectId;
        try {
            objectId = new ObjectId(id);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }

        GridFSFile file = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(objectId)));
        if (file == null) {
            return Optional.empty();
        }
        GridFsResource resource = gridFsTemplate.getResource(file);
        try (InputStream input = resource.getInputStream()) {
            Document metadata = file.getMetadata();
            String contentType = metadata == null ? null : metadata.getString("contentType");
            return Optional.of(new BrandingContent(
                input.readAllBytes(),
                contentType == null ? "application/octet-stream" : contentType
            ));
        } catch (IOException exception) {
            throw new IllegalStateException("Church branding could not be read.", exception);
        }
    }

    public byte[] effectiveLogoBytes(ChurchSettings settings) {
        return effectiveLogo(settings).bytes();
    }

    public BrandingContent effectiveLogo(ChurchSettings settings) {
        return effective(
            settings == null ? null : settings.getLogoGridFsId(),
            properties.branding().logPath()
        );
    }

    public BrandingContent effectiveBanner(ChurchSettings settings) {
        return effective(
            settings == null ? null : settings.getBannerGridFsId(),
            properties.branding().bannerPath()
        );
    }

    public void deleteQuietly(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        try {
            ObjectId objectId = new ObjectId(id);
            gridFsTemplate.delete(Query.query(Criteria.where("_id").is(objectId)));
        } catch (RuntimeException ignored) {
            // Replacement and reset remain idempotent when the old file is already absent.
        }
    }

    private ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ChurchBrandingValidationException("Choose a PNG or JPEG image.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ChurchBrandingValidationException("Choose a PNG or JPEG image no larger than 5 MB.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ChurchBrandingValidationException("The image could not be read.", exception);
        }
        String detectedType = detectContentType(bytes);
        String declaredType = file.getContentType();
        if (detectedType == null || (declaredType != null && !declaredType.isBlank()
            && !detectedType.equals(declaredType.toLowerCase(Locale.ROOT)))) {
            throw new ChurchBrandingValidationException("The image must be a valid PNG or JPEG file.");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw invalidImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                    || (long) width * height > MAX_PIXELS) {
                    throw new ChurchBrandingValidationException(
                        "The image dimensions must not exceed 8,000 pixels or 40 megapixels."
                    );
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw invalidImage();
                }
            } finally {
                reader.dispose();
            }
        } catch (ChurchBrandingValidationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ChurchBrandingValidationException("The image must be a valid PNG or JPEG file.", exception);
        }
        return new ValidatedImage(bytes, detectedType);
    }

    private String detectContentType(byte[] bytes) {
        if (startsWith(bytes, new int[] {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
            return "image/png";
        }
        if (startsWith(bytes, new int[] {0xff, 0xd8, 0xff})) {
            return "image/jpeg";
        }
        return null;
    }

    private boolean startsWith(byte[] bytes, int[] signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((bytes[index] & 0xff) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private byte[] bundledResource(String publicPath) {
        String normalized = publicPath == null ? "" : publicPath.replaceFirst("^/+", "");
        ClassPathResource resource = new ClassPathResource("static/" + normalized);
        try (InputStream input = resource.getInputStream()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Default church branding could not be read.", exception);
        }
    }

    private BrandingContent effective(String gridFsId, String defaultPath) {
        if (gridFsId != null && !gridFsId.isBlank()) {
            Optional<BrandingContent> stored = databaseFile(gridFsId);
            if (stored.isPresent()) {
                return stored.get();
            }
        }
        byte[] bytes = bundledResource(defaultPath);
        String contentType = detectContentType(bytes);
        return new BrandingContent(bytes, contentType == null ? "application/octet-stream" : contentType);
    }

    private ChurchBrandingValidationException invalidImage() {
        return new ChurchBrandingValidationException("The image must be a valid PNG or JPEG file.");
    }

    public record StoredBranding(String id, String contentType) {
    }

    public record BrandingContent(byte[] bytes, String contentType) {
    }

    private record ValidatedImage(byte[] bytes, String contentType) {
    }
}

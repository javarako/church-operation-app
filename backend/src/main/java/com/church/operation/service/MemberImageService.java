package com.church.operation.service;

import com.church.operation.config.MemberImageProperties;
import com.church.operation.dto.MemberImageContent;
import com.church.operation.entity.Member;
import com.church.operation.repo.MemberRepository;
import com.church.operation.util.Role;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Service
public class MemberImageService {
    private final GridFsTemplate gridFsTemplate;
    private final MemberRepository memberRepository;
    private final MemberImageProperties properties;

    public MemberImageService(
        GridFsTemplate gridFsTemplate,
        MemberRepository memberRepository,
        MemberImageProperties properties
    ) {
        this.gridFsTemplate = gridFsTemplate;
        this.memberRepository = memberRepository;
        this.properties = properties;
    }

    public String store(Member actor, String memberId, MultipartFile file) {
        Member member = findAuthorized(actor, memberId);
        byte[] bytes = validateAndRead(file);
        String contentType = detectContentType(bytes);
        validateDeclaredType(file.getContentType(), contentType);
        validateExtension(file.getOriginalFilename(), contentType);

        String filename = file.getOriginalFilename();
        Document metadata = new Document()
            .append("memberId", memberId)
            .append("uploadedBy", actor.getId())
            .append("uploadedAt", Instant.now())
            .append("contentType", contentType);
        ObjectId newId = gridFsTemplate.store(
            new ByteArrayInputStream(bytes), filename, contentType, metadata
        );

        String oldId = member.getFaceImageAttachmentId();
        member.setFaceImageAttachmentId(newId.toHexString());
        try {
            memberRepository.save(member);
        } catch (RuntimeException exception) {
            member.setFaceImageAttachmentId(oldId);
            delete(newId.toHexString());
            throw exception;
        }

        if (oldId != null && !oldId.isBlank()) {
            delete(oldId);
        }
        return newId.toHexString();
    }

    public MemberImageContent load(Member actor, String memberId) {
        Member member = findAuthorized(actor, memberId);
        String imageId = member.getFaceImageAttachmentId();
        if (imageId == null || imageId.isBlank()) {
            throw new IllegalArgumentException("Member image was not found.");
        }

        GridFSFile file = gridFsTemplate.findOne(idQuery(imageId));
        if (file == null) {
            throw new IllegalArgumentException("Member image was not found.");
        }

        GridFsResource resource = gridFsTemplate.getResource(file);
        try {
            Document metadata = file.getMetadata();
            String contentType = metadata == null ? null : metadata.getString("contentType");
            return new MemberImageContent(
                resource.getInputStream().readAllBytes(),
                contentType == null ? "application/octet-stream" : contentType,
                file.getFilename()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Member image could not be read.", exception);
        }
    }

    public void remove(Member actor, String memberId) {
        Member member = findAuthorized(actor, memberId);
        String imageId = member.getFaceImageAttachmentId();
        if (imageId == null || imageId.isBlank()) {
            return;
        }

        member.setFaceImageAttachmentId(null);
        memberRepository.save(member);
        delete(imageId);
    }

    private Member findAuthorized(Member actor, String memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member was not found."));
        boolean manager = hasRole(actor, Role.ADMIN) || hasRole(actor, Role.MEMBERSHIP);
        boolean self = actor != null && Objects.equals(actor.getId(), memberId) && hasRole(actor, Role.MEMBER);
        if (!manager && !self) {
            throw new AccessDeniedException("You do not have permission to manage this member image.");
        }
        return member;
    }

    private byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Member image is required.");
        }
        if (file.getSize() > properties.maxSize().toBytes()) {
            throw new IllegalArgumentException("Member image must not exceed 5 MB.");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Member image could not be read.", exception);
        }
    }

    private String detectContentType(byte[] bytes) {
        if (startsWith(bytes, new int[] {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
            return "image/png";
        }
        if (startsWith(bytes, new int[] {0xff, 0xd8, 0xff})) {
            return "image/jpeg";
        }
        if (bytes.length >= 16
            && asciiEquals(bytes, 0, "RIFF")
            && asciiEquals(bytes, 8, "WEBP")
            && (asciiEquals(bytes, 12, "VP8 ")
                || asciiEquals(bytes, 12, "VP8L")
                || asciiEquals(bytes, 12, "VP8X"))) {
            if (asciiEquals(bytes, 12, "VP8X") && bytes.length > 20 && (bytes[20] & 0x02) != 0) {
                throw new IllegalArgumentException("Animated WebP images are not supported.");
            }
            return "image/webp";
        }
        throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are supported.");
    }

    private void validateDeclaredType(String declaredType, String detectedType) {
        if (!detectedType.equals(declaredType == null ? null : declaredType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Image content does not match " + declaredType + ".");
        }
    }

    private void validateExtension(String filename, String contentType) {
        String normalized = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        boolean valid = switch (contentType) {
            case "image/png" -> normalized.endsWith(".png");
            case "image/jpeg" -> normalized.endsWith(".jpg") || normalized.endsWith(".jpeg");
            case "image/webp" -> normalized.endsWith(".webp");
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("Image filename extension does not match its content.");
        }
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

    private boolean asciiEquals(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (bytes[offset + index] != (byte) value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasRole(Member member, Role role) {
        return member != null && member.getRoles() != null && member.getRoles().contains(role);
    }

    private void delete(String id) {
        gridFsTemplate.delete(idQuery(id));
    }

    private Query idQuery(String id) {
        try {
            return Query.query(Criteria.where("_id").is(new ObjectId(id)));
        } catch (IllegalArgumentException exception) {
            return Query.query(Criteria.where("_id").is(id));
        }
    }
}

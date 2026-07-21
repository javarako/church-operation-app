package com.church.operation.service;

import com.church.operation.entity.YearEndClosing;
import com.church.operation.util.YearEndReportType;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class YearEndSnapshotStore {
    public static final String EXCEL_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final GridFsTemplate gridFsTemplate;

    public YearEndSnapshotStore(GridFsTemplate gridFsTemplate) {
        this.gridFsTemplate = gridFsTemplate;
    }

    public StoredSnapshot store(
        byte[] bytes,
        String filename,
        int fiscalYear,
        YearEndReportType reportType,
        int version
    ) {
        String checksum = sha256(bytes);
        Document metadata = new Document()
            .append("fiscalYear", fiscalYear)
            .append("reportType", reportType.name())
            .append("version", version)
            .append("checksum", checksum)
            .append("contentType", EXCEL_CONTENT_TYPE)
            .append("storedAt", Instant.now());
        ObjectId id = gridFsTemplate.store(
            new ByteArrayInputStream(bytes),
            filename,
            EXCEL_CONTENT_TYPE,
            metadata
        );
        return new StoredSnapshot(id.toHexString(), bytes.length, checksum);
    }

    public byte[] load(YearEndClosing closing) {
        GridFSFile file = gridFsTemplate.findOne(idQuery(closing.getGridFsFileId()));
        if (file == null) {
            throw new IllegalStateException("Closed yearly workbook snapshot was not found.");
        }
        GridFsResource resource = gridFsTemplate.getResource(file);
        try {
            byte[] bytes = resource.getInputStream().readAllBytes();
            if (!MessageDigest.isEqual(
                closing.getChecksum().getBytes(StandardCharsets.UTF_8),
                sha256(bytes).getBytes(StandardCharsets.UTF_8)
            )) {
                throw new IllegalStateException("Closed yearly workbook checksum verification failed.");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("Closed yearly workbook snapshot could not be read.", exception);
        }
    }

    public void delete(String gridFsFileId) {
        gridFsTemplate.delete(idQuery(gridFsFileId));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private Query idQuery(String id) {
        try {
            return Query.query(Criteria.where("_id").is(new ObjectId(id)));
        } catch (IllegalArgumentException exception) {
            return Query.query(Criteria.where("_id").is(id));
        }
    }

    public record StoredSnapshot(String gridFsFileId, long fileSize, String checksum) {
    }
}

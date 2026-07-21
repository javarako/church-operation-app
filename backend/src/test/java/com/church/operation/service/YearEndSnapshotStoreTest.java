package com.church.operation.service;

import com.church.operation.entity.YearEndClosing;
import com.church.operation.util.YearEndReportType;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.BsonObjectId;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YearEndSnapshotStoreTest {
    private static final String EXCEL_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Mock private GridFsTemplate gridFsTemplate;
    @Mock private GridFsResource gridFsResource;

    private YearEndSnapshotStore store;

    @BeforeEach
    void setUp() {
        store = new YearEndSnapshotStore(gridFsTemplate);
    }

    @Test
    void storesWorkbookWithChecksumAndMetadata() {
        byte[] bytes = "closed-workbook".getBytes(StandardCharsets.UTF_8);
        ObjectId id = new ObjectId("64b000000000000000000001");
        when(gridFsTemplate.store(
            any(InputStream.class),
            eq("yearly-offerings-2026-closed-v1.xlsx"),
            eq(EXCEL_TYPE),
            any(Document.class)
        )).thenReturn(id);

        YearEndSnapshotStore.StoredSnapshot result = store.store(
            bytes,
            "yearly-offerings-2026-closed-v1.xlsx",
            2026,
            YearEndReportType.OFFERING,
            1
        );

        assertThat(result.gridFsFileId()).isEqualTo(id.toHexString());
        assertThat(result.fileSize()).isEqualTo(bytes.length);
        assertThat(result.checksum()).hasSize(64);

        ArgumentCaptor<Document> metadata = ArgumentCaptor.forClass(Document.class);
        verify(gridFsTemplate).store(
            any(InputStream.class),
            eq("yearly-offerings-2026-closed-v1.xlsx"),
            eq(EXCEL_TYPE),
            metadata.capture()
        );
        assertThat(metadata.getValue().getInteger("fiscalYear")).isEqualTo(2026);
        assertThat(metadata.getValue().getString("reportType")).isEqualTo("OFFERING");
        assertThat(metadata.getValue().getInteger("version")).isEqualTo(1);
        assertThat(metadata.getValue().getString("checksum")).isEqualTo(result.checksum());
    }

    @Test
    void loadsExactStoredBytesWhenChecksumMatches() throws Exception {
        byte[] bytes = "stored-workbook".getBytes(StandardCharsets.UTF_8);
        YearEndClosing closing = closing(sha256(bytes));
        GridFSFile gridFsFile = gridFsFile(bytes.length);
        when(gridFsTemplate.findOne(any(Query.class))).thenReturn(gridFsFile);
        when(gridFsTemplate.getResource(gridFsFile)).thenReturn(gridFsResource);
        when(gridFsResource.getInputStream()).thenReturn(new ByteArrayInputStream(bytes));

        assertThat(store.load(closing)).isEqualTo(bytes);
    }

    @Test
    void rejectsChecksumMismatch() throws Exception {
        YearEndClosing closing = closing(sha256("expected".getBytes(StandardCharsets.UTF_8)));
        GridFSFile gridFsFile = gridFsFile(7);
        when(gridFsTemplate.findOne(any(Query.class))).thenReturn(gridFsFile);
        when(gridFsTemplate.getResource(gridFsFile)).thenReturn(gridFsResource);
        when(gridFsResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("changed".getBytes(StandardCharsets.UTF_8))
        );

        assertThatThrownBy(() -> store.load(closing))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Closed yearly workbook checksum verification failed.");
    }

    @Test
    void rejectsMissingSnapshot() {
        YearEndClosing closing = closing(sha256("expected".getBytes(StandardCharsets.UTF_8)));
        when(gridFsTemplate.findOne(any(Query.class))).thenReturn(null);

        assertThatThrownBy(() -> store.load(closing))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Closed yearly workbook snapshot was not found.");
    }

    @Test
    void deletesSnapshotByGridFsId() {
        store.delete("64b000000000000000000001");

        verify(gridFsTemplate).delete(any(Query.class));
    }

    private YearEndClosing closing(String checksum) {
        YearEndClosing closing = new YearEndClosing();
        closing.setGridFsFileId("64b000000000000000000001");
        closing.setChecksum(checksum);
        return closing;
    }

    private String sha256(byte[] bytes) {
        return YearEndSnapshotStore.sha256(bytes);
    }

    private GridFSFile gridFsFile(long length) {
        return new GridFSFile(
            new BsonObjectId(new ObjectId("64b000000000000000000001")),
            "yearly-offerings-2026-closed-v1.xlsx",
            length,
            255,
            new Date(),
            new Document()
        );
    }
}

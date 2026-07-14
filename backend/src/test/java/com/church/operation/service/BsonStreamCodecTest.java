package com.church.operation.service;

import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BsonStreamCodecTest {
    private final BsonStreamCodec codec = new BsonStreamCodec();

    @Test
    void roundTripsConcatenatedDocumentsWithMongoAndNestedValues() throws Exception {
        Document document = new Document("_id", new ObjectId("64b000000000000000000001"))
            .append("amount", new Decimal128(new BigDecimal("1234.56")))
            .append("date", Date.from(LocalDate.of(2026, 7, 13).atStartOfDay(ZoneOffset.UTC).toInstant()))
            .append("active", true)
            .append("tags", List.of("backup", 7))
            .append("metadata", new Document("source", "test").append("sequence", 2))
            .append("optional", null)
            .append("attachment", new Binary(new byte[] {1, 2, 3, 4}));
        Document second = new Document("name", "second").append("value", false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        codec.write(List.of(document, second), output);

        assertThat(codec.read(new ByteArrayInputStream(output.toByteArray())))
            .containsExactly(document, second);
    }

    @Test
    void countsConcatenatedDocumentsWithoutMaterializingThem() throws Exception {
        byte[] emptyDocument = new byte[] {5, 0, 0, 0, 0};
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        input.write(emptyDocument);
        input.write(emptyDocument);
        input.write(emptyDocument);

        assertThat(codec.count(new ByteArrayInputStream(input.toByteArray()))).isEqualTo(3);
    }

    @Test
    void rejectsOversizedDocumentBeforeReadingItsPayload() {
        byte[] length = ByteBuffer.allocate(Integer.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(BsonStreamCodec.MAX_DOCUMENT_SIZE + 1)
            .array();

        assertThatThrownBy(() -> codec.count(new ByteArrayInputStream(length)))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("length");
    }

    @Test
    void rejectsTruncatedDocumentWhileStreamingCount() {
        byte[] truncated = new byte[] {8, 0, 0, 0, 1, 2};

        assertThatThrownBy(() -> codec.count(new ByteArrayInputStream(truncated)))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Truncated");
    }
}

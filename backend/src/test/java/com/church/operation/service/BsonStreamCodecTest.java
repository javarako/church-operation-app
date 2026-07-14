package com.church.operation.service;

import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}

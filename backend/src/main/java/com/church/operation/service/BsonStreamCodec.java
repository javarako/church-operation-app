package com.church.operation.service;

import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.Document;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class BsonStreamCodec {
    public static final int MAX_DOCUMENT_SIZE = 16 * 1024 * 1024;

    private final DocumentCodec documentCodec = new DocumentCodec();

    public void write(List<Document> documents, OutputStream output) throws IOException {
        for (Document document : documents) {
            try (BasicOutputBuffer buffer = new BasicOutputBuffer(); BsonBinaryWriter writer = new BsonBinaryWriter(buffer)) {
                documentCodec.encode(writer, document, EncoderContext.builder().build());
                output.write(buffer.toByteArray());
            }
        }
    }

    public List<Document> read(InputStream input) throws IOException {
        List<Document> documents = new ArrayList<>();
        byte[] lengthBytes = new byte[Integer.BYTES];
        while (readLength(input, lengthBytes)) {
            int length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (length < 5 || length > MAX_DOCUMENT_SIZE) {
                throw new IOException("Invalid BSON document length.");
            }
            byte[] bson = new byte[length];
            System.arraycopy(lengthBytes, 0, bson, 0, lengthBytes.length);
            readFully(input, bson, lengthBytes.length, length - lengthBytes.length);
            try (BsonBinaryReader reader = new BsonBinaryReader(ByteBuffer.wrap(bson))) {
                documents.add(documentCodec.decode(reader, DecoderContext.builder().build()));
            }
        }
        return documents;
    }

    private boolean readLength(InputStream input, byte[] lengthBytes) throws IOException {
        int first = input.read();
        if (first < 0) {
            return false;
        }
        lengthBytes[0] = (byte) first;
        readFully(input, lengthBytes, 1, lengthBytes.length - 1);
        return true;
    }

    private void readFully(InputStream input, byte[] buffer, int offset, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = input.read(buffer, offset + total, length - total);
            if (read < 0) {
                throw new EOFException("Truncated BSON document.");
            }
            total += read;
        }
    }
}

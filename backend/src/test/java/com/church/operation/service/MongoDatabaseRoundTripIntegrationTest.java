package com.church.operation.service;

import com.church.operation.config.DataManagementProperties;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MongoDatabaseRoundTripIntegrationTest {
    private static final char[] PASSWORD = "complete database round trip".toCharArray();

    @Container
    private static final MongoDBContainer MONGODB = new MongoDBContainer(
        DockerImageName.parse("mongo:7.0.17")
    );

    @TempDir
    Path tempDirectory;

    @Test
    void exportsAndRestoresEveryCollectionWithRawDocumentsOptionsIndexesAndGridFsBytes() throws Exception {
        try (MongoClient client = MongoClients.create(MONGODB.getConnectionString())) {
            MongoDatabase database = client.getDatabase("round_trip");
            seedDatabase(database);
            DatabaseSnapshot expected = snapshot(database);

            DataManagementProperties properties = new DataManagementProperties(
                tempDirectory.resolve("operations"),
                Duration.ofMinutes(30),
                DataSize.ofGigabytes(2)
            );
            ArchivePackageService archivePackageService = new ArchivePackageService(properties);
            MongoDatabaseExportService exporter = new MongoDatabaseExportService(
                database, archivePackageService, properties
            );
            MongoDatabaseImportService importer = new MongoDatabaseImportService(
                database, archivePackageService
            );
            Path archive = tempDirectory.resolve("full-backup.zip");

            var manifest = exporter.exportFull(archive, PASSWORD);
            dropAllCollections(database);
            database.getCollection("collection_created_after_backup")
                .insertOne(new Document("mustDisappear", true));

            try (ArchivePackageService.ValidatedArchive validated = importer.validateFull(archive, PASSWORD)) {
                var verification = importer.replaceAll(validated);
                assertThat(verification.collectionCount()).isEqualTo(expected.documents().size());
                assertThat(verification.documentCount()).isEqualTo(expected.totalDocumentCount());
                assertThat(verification.indexCount()).isEqualTo(expected.totalCustomIndexCount());
            }

            assertThat(manifest.collections()).hasSize(expected.documents().size() * 3);
            assertThat(snapshot(database)).usingRecursiveComparison().isEqualTo(expected);
            assertThat(downloadGridFs(database, expected.gridFsId())).isEqualTo(expected.gridFsBytes());
            assertThat(database.listCollectionNames())
                .doesNotContain("collection_created_after_backup")
                .allMatch(name -> !name.startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX));
        }
    }

    private void seedDatabase(MongoDatabase database) {
        database.runCommand(new Document("create", "members")
            .append("validator", new Document("status", new Document("$in", List.of("ACTIVE", "INACTIVE"))))
            .append("validationLevel", "moderate")
            .append("validationAction", "error")
            .append("collation", new Document("locale", "en").append("strength", 2)));

        MongoCollection<Document> members = database.getCollection("members");
        members.insertMany(List.of(
            new Document("_id", new ObjectId("65a000000000000000000001"))
                .append("name", "Ada Lovelace")
                .append("email", "ada@example.test")
                .append("status", "ACTIVE")
                .append("notes", "Choir and finance")
                .append("expiresAt", java.util.Date.from(Instant.parse("2030-01-01T00:00:00Z")))
                .append("location", new Document("type", "Point").append("coordinates", List.of(-79.38, 43.65)))
                .append("attributes", new Document("ministry", "music")),
            new Document("_id", new ObjectId("65a000000000000000000002"))
                .append("name", "Grace Hopper")
                .append("status", "INACTIVE")
                .append("notes", "Community outreach")
                .append("attributes", new Document("ministry", "care"))
        ));

        database.runCommand(new Document("createIndexes", "members").append("indexes", List.of(
            new Document("key", new Document("email", 1))
                .append("name", "email_unique_partial")
                .append("unique", true)
                .append("partialFilterExpression", new Document("email", new Document("$type", "string")))
                .append("collation", new Document("locale", "en").append("strength", 2)),
            new Document("key", new Document("expiresAt", 1))
                .append("name", "expires_ttl")
                .append("expireAfterSeconds", 3600L)
                .append("sparse", true),
            new Document("key", new Document("notes", "text"))
                .append("name", "notes_text")
                .append("weights", new Document("notes", 7))
                .append("default_language", "english")
                .append("language_override", "language")
                .append("collation", new Document("locale", "simple")),
            new Document("key", new Document("location", "2dsphere"))
                .append("name", "location_geo"),
            new Document("key", new Document("$**", 1))
                .append("name", "attributes_wildcard")
                .append("wildcardProjection", new Document("attributes.internal", 0))
                .append("hidden", true)
        )));

        database.getCollection("audit_events").insertOne(new Document("event", "member-created")
            .append("payload", new Document("memberId", new ObjectId("65a000000000000000000001"))));
        database.getCollection(MongoDatabaseExportService.RESTORE_STAGING_PREFIX + "leftover")
            .insertOne(new Document("excluded", true));

        byte[] gridFsBytes = new byte[8193];
        for (int index = 0; index < gridFsBytes.length; index++) {
            gridFsBytes[index] = (byte) (index * 31);
        }
        GridFSBuckets.create(database).uploadFromStream(
            "receipts/2029/ada.pdf",
            new ByteArrayInputStream(gridFsBytes)
        );
    }

    private DatabaseSnapshot snapshot(MongoDatabase database) {
        Map<String, List<byte[]>> documents = new LinkedHashMap<>();
        Map<String, BsonDocument> options = new LinkedHashMap<>();
        Map<String, List<BsonDocument>> indexes = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        database.listCollectionNames().into(names);
        names.stream()
            .filter(name -> !name.startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX))
            .sorted()
            .forEach(name -> {
                documents.put(name, rawDocuments(database, name));
                options.put(name, collectionOptions(database, name));
                indexes.put(name, customIndexSignatures(database, name));
            });

        RawBsonDocument gridFsFile = database.getCollection("fs.files", RawBsonDocument.class)
            .find().first();
        assertThat(gridFsFile).isNotNull();
        ObjectId gridFsId = gridFsFile.getObjectId("_id").getValue();
        return new DatabaseSnapshot(
            documents,
            options,
            indexes,
            gridFsId,
            downloadGridFs(database, gridFsId),
            documents.values().stream().mapToLong(List::size).sum(),
            indexes.values().stream().mapToLong(List::size).sum()
        );
    }

    private List<byte[]> rawDocuments(MongoDatabase database, String collectionName) {
        List<byte[]> result = new ArrayList<>();
        database.getCollection(collectionName, RawBsonDocument.class)
            .find()
            .sort(new Document("_id", 1))
            .forEach(document -> {
                var buffer = document.getByteBuffer().asNIO().duplicate();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                result.add(bytes);
            });
        return result;
    }

    private BsonDocument collectionOptions(MongoDatabase database, String collectionName) {
        RawBsonDocument catalog = database.listCollections(RawBsonDocument.class)
            .filter(new Document("name", collectionName))
            .first();
        assertThat(catalog).isNotNull();
        return catalog.getDocument("options");
    }

    private List<BsonDocument> customIndexSignatures(MongoDatabase database, String collectionName) {
        List<BsonDocument> signatures = new ArrayList<>();
        database.getCollection(collectionName, RawBsonDocument.class)
            .listIndexes(RawBsonDocument.class)
            .forEach(index -> {
                if (!"_id_".equals(index.getString("name").getValue())) {
                    BsonDocument signature = BsonDocument.parse(index.toJson());
                    signature.remove("v");
                    signature.remove("ns");
                    signatures.add(signature);
                }
            });
        signatures.sort(Comparator.comparing(index -> index.getString("name").getValue()));
        return signatures;
    }

    private byte[] downloadGridFs(MongoDatabase database, ObjectId id) {
        GridFSBucket bucket = GridFSBuckets.create(database);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bucket.downloadToStream(id, output);
        return output.toByteArray();
    }

    private void dropAllCollections(MongoDatabase database) {
        List<String> names = new ArrayList<>();
        database.listCollectionNames().into(names);
        names.forEach(name -> database.getCollection(name).drop());
    }

    private record DatabaseSnapshot(
        Map<String, List<byte[]>> documents,
        Map<String, BsonDocument> options,
        Map<String, List<BsonDocument>> indexes,
        ObjectId gridFsId,
        byte[] gridFsBytes,
        long totalDocumentCount,
        long totalCustomIndexCount
    ) {
    }
}

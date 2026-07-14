package com.church.operation.service;

import com.church.operation.config.DataManagementProperties;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandListener;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.BsonDocumentCodec;
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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

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
        RecordingCommandListener commandListener = new RecordingCommandListener();
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(MONGODB.getConnectionString()))
            .addCommandListener(commandListener)
            .build();
        try (MongoClient client = MongoClients.create(settings)) {
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
            assertArchivedIndexVersions(archivePackageService, archive);
            dropAllCollections(database);
            database.getCollection("collection_created_after_backup")
                .insertOne(new Document("mustDisappear", true));
            commandListener.clear();

            try (MongoDatabaseImportService.ValidatedArchive validated = importer.validateFull(archive, PASSWORD)) {
                var verification = importer.replaceAll(validated);
                assertThat(verification.collectionCount()).isEqualTo(expected.types().size());
                assertThat(verification.documentCount()).isEqualTo(expected.totalDocumentCount());
                assertThat(verification.indexCount()).isEqualTo(expected.totalCustomIndexCount());
            }

            assertThat(manifest.collections()).hasSize(expected.types().size() * 3);
            assertThat(manifest.collections())
                .filteredOn(entry -> entry.collection().equals("active_members"))
                .filteredOn(entry -> entry.entryName().endsWith("/data.bson"))
                .singleElement()
                .extracting(entry -> entry.documentCount())
                .isEqualTo(0L);
            assertThat(snapshot(database)).usingRecursiveComparison().isEqualTo(expected);
            assertThat(downloadGridFs(database, expected.gridFsId())).isEqualTo(expected.gridFsBytes());
            assertThat(commandListener.createIndexesCommands()).isNotEmpty();
            assertThat(commandListener.createIndexesCommands())
                .allSatisfy(command -> assertThat(command.getArray("indexes"))
                    .allSatisfy(value -> assertThat(value.asDocument()).containsKey("v")));
            assertThat(database.listCollectionNames())
                .doesNotContain("collection_created_after_backup")
                .allMatch(name -> !name.startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX))
                .allMatch(name -> !name.startsWith(MongoDatabaseExportService.RESTORE_BACKUP_PREFIX));
        }
    }

    @Test
    void rejectsMalformedSemanticSidecarDuringValidationWithoutDatabaseMutation() throws Exception {
        try (MongoClient client = MongoClients.create(MONGODB.getConnectionString())) {
            MongoDatabase database = client.getDatabase("semantic_preflight");
            database.getCollection("members").insertOne(new Document("name", "Ada"));
            DataManagementProperties properties = properties("semantic-operations");
            ArchivePackageService packages = new ArchivePackageService(properties);
            MongoDatabaseExportService exporter = new MongoDatabaseExportService(database, packages, properties);
            MongoDatabaseImportService importer = new MongoDatabaseImportService(database, packages);
            Path archive = tempDirectory.resolve("semantic-valid.zip");
            Path malformed = tempDirectory.resolve("semantic-malformed.zip");
            exporter.exportFull(archive, PASSWORD);
            appendSecondCreateCommand(packages, archive, malformed, "members");
            DatabaseSnapshot before = snapshot(database);

            assertThatThrownBy(() -> {
                try (MongoDatabaseImportService.ValidatedArchive ignored = importer.validateFull(malformed, PASSWORD)) {
                    // Validation must reject this archive before replaceAll is possible.
                }
            }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single create command");

            assertThat(snapshot(database)).usingRecursiveComparison().isEqualTo(before);
            assertThat(database.listCollectionNames())
                .allMatch(name -> !name.startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX))
                .allMatch(name -> !name.startsWith(MongoDatabaseExportService.RESTORE_BACKUP_PREFIX));
        }
    }

    @Test
    void rejectsUnsupportedArchivedIndexVersionDuringValidationWithoutDatabaseMutation() throws Exception {
        try (MongoClient client = MongoClients.create(MONGODB.getConnectionString())) {
            MongoDatabase database = client.getDatabase("index_version_preflight");
            database.getCollection("members").insertOne(new Document("email", "ada@example.test"));
            database.getCollection("members").createIndex(new Document("email", 1));
            DataManagementProperties properties = properties("index-version-operations");
            ArchivePackageService packages = new ArchivePackageService(properties);
            MongoDatabaseExportService exporter = new MongoDatabaseExportService(database, packages, properties);
            MongoDatabaseImportService importer = new MongoDatabaseImportService(database, packages);
            Path archive = tempDirectory.resolve("index-version-valid.zip");
            Path malformed = tempDirectory.resolve("index-version-malformed.zip");
            exporter.exportFull(archive, PASSWORD);
            replaceArchivedIndexVersion(packages, archive, malformed, "members", 0);
            DatabaseSnapshot before = snapshot(database);

            assertThatThrownBy(() -> {
                try (MongoDatabaseImportService.ValidatedArchive ignored = importer.validateFull(malformed, PASSWORD)) {
                    // Unsupported versions must never reach staging.
                }
            }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("index version");

            assertThat(snapshot(database)).usingRecursiveComparison().isEqualTo(before);
            assertThat(database.listCollectionNames())
                .allMatch(name -> !name.startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX));
        }
    }

    @Test
    void excludesEveryMongoSystemNamespaceFromRestorableExports() throws Exception {
        try (MongoClient client = MongoClients.create(MONGODB.getConnectionString())) {
            MongoDatabase database = client.getDatabase("system_namespace_policy");
            database.getCollection("members").insertOne(new Document("name", "Ada"));
            database.runCommand(new Document("profile", 2));
            database.getCollection("members").find().first();
            database.runCommand(new Document("profile", 0));
            assertThat(database.listCollectionNames()).anyMatch(name -> name.startsWith("system."));

            DataManagementProperties properties = properties("system-namespace-operations");
            ArchivePackageService packages = new ArchivePackageService(properties);
            MongoDatabaseExportService exporter = new MongoDatabaseExportService(database, packages, properties);
            MongoDatabaseImportService importer = new MongoDatabaseImportService(database, packages);
            Path archive = tempDirectory.resolve("system-namespace-policy.zip");

            var manifest = exporter.exportFull(archive, PASSWORD);

            assertThat(manifest.collections())
                .extracting(entry -> entry.collection())
                .contains("members")
                .allMatch(name -> !name.startsWith("system."));
            try (MongoDatabaseImportService.ValidatedArchive ignored = importer.validateFull(archive, PASSWORD)) {
                // Export policy must never produce an archive rejected by restore namespace policy.
            }
        }
    }

    @Test
    void restoresViewsWithLookupAndNestedPipelineUnionDependencies() throws Exception {
        RecordingCommandListener commandListener = new RecordingCommandListener();
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(MONGODB.getConnectionString()))
            .addCommandListener(commandListener)
            .build();
        try (MongoClient client = MongoClients.create(settings)) {
            MongoDatabase database = client.getDatabase("recursive_view_dependencies");
            database.getCollection("members").insertOne(
                new Document("_id", 1).append("name", "Ada")
            );
            database.getCollection("offerings").insertOne(
                new Document("memberId", 1).append("kind", "offering")
            );
            database.getCollection("audit_events").insertOne(
                new Document("memberId", 1).append("kind", "audit")
            );
            database.getCollection("member_links").insertOne(
                new Document("memberId", 1).append("label", "choir")
            );
            database.getCollection("archived_members").insertOne(
                new Document("_id", 2).append("name", "Grace")
            );
            database.runCommand(new Document("create", "member_activity")
                .append("viewOn", "members")
                .append("pipeline", List.of(
                    new Document("$lookup", new Document("from", "offerings")
                        .append("localField", "_id")
                        .append("foreignField", "memberId")
                        .append("pipeline", List.of(
                            new Document("$unionWith", new Document("coll", "audit_events")
                                .append("pipeline", List.of(
                                    new Document("$lookup", new Document("from", "member_links")
                                        .append("localField", "memberId")
                                        .append("foreignField", "memberId")
                                        .append("as", "links"))
                                )))
                        ))
                        .append("as", "activity")),
                    new Document("$unionWith", "archived_members")
                )));
            List<BsonDocument> expected = semanticDocuments(database, "member_activity");

            DataManagementProperties properties = properties("recursive-view-operations");
            ArchivePackageService packages = new ArchivePackageService(properties);
            MongoDatabaseExportService exporter = new MongoDatabaseExportService(database, packages, properties);
            MongoDatabaseImportService importer = new MongoDatabaseImportService(database, packages);
            Path archive = tempDirectory.resolve("recursive-view-dependencies.zip");
            exporter.exportFull(archive, PASSWORD);
            dropAllCollections(database);
            commandListener.clear();

            try (MongoDatabaseImportService.ValidatedArchive validated = importer.validateFull(archive, PASSWORD)) {
                importer.replaceAll(validated);
            }

            assertThat(semanticDocuments(database, "member_activity")).isEqualTo(expected);
            BsonDocument stagedViewCreate = commandListener.viewCreateCommands().stream()
                .filter(command -> command.getString("create").getValue().startsWith(
                    MongoDatabaseExportService.RESTORE_STAGING_PREFIX
                ))
                .findFirst()
                .orElseThrow();
            assertThat(new MongoRestorePreflightPlanner(database.getName()).viewDependencies(stagedViewCreate))
                .allMatch(name -> name.startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX));
        }
    }

    @Test
    void forcedCutoverFailureRestoresOrdinaryOriginalsAndRetainsStagingEvidence() throws Exception {
        try (MongoClient client = MongoClients.create(MONGODB.getConnectionString())) {
            MongoDatabase database = client.getDatabase("forced_cutover_failure");
            database.getCollection("members").insertOne(new Document("version", "archive"));
            DataManagementProperties properties = properties("cutover-operations");
            ArchivePackageService packages = new ArchivePackageService(properties);
            MongoDatabaseExportService exporter = new MongoDatabaseExportService(database, packages, properties);
            Path archive = tempDirectory.resolve("cutover.zip");
            exporter.exportFull(archive, PASSWORD);

            database.getCollection("members").drop();
            database.getCollection("members").insertOne(new Document("version", "original-at-cutover"));
            DatabaseSnapshot before = snapshot(database);
            IllegalStateException forced = new IllegalStateException("forced cutover failure");
            MongoDatabaseImportService importer = new MongoDatabaseImportService(
                database,
                packages,
                () -> { throw forced; }
            );

            assertThatThrownBy(() -> {
                try (MongoDatabaseImportService.ValidatedArchive validated = importer.validateFull(archive, PASSWORD)) {
                    importer.replaceAll(validated);
                }
            }).isInstanceOf(RestoreCutoverException.class)
                .hasCause(forced)
                .satisfies(failure -> {
                    RestoreCutoverException cutoverFailure = (RestoreCutoverException) failure;
                    assertThat(cutoverFailure.maintenanceRequired()).isTrue();
                    assertThat(cutoverFailure.stagingNamespaces()).isNotEmpty();
                });

            assertThat(snapshot(database)).usingRecursiveComparison().isEqualTo(before);
            assertThat(database.listCollectionNames())
                .anyMatch(name -> name.startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX));
        }
    }

    @Test
    void catalogFailureCannotMaskInjectedCutoverFailureOrRecoveryEvidence() throws Exception {
        try (MongoClient client = MongoClients.create(MONGODB.getConnectionString())) {
            MongoDatabase database = client.getDatabase("catalog_failure_during_cutover");
            database.getCollection("members").insertOne(new Document("version", "archive"));
            DataManagementProperties properties = properties("catalog-failure-operations");
            ArchivePackageService packages = new ArchivePackageService(properties);
            MongoDatabaseExportService exporter = new MongoDatabaseExportService(database, packages, properties);
            Path archive = tempDirectory.resolve("catalog-failure-cutover.zip");
            exporter.exportFull(archive, PASSWORD);

            database.getCollection("members").drop();
            database.getCollection("members").insertOne(new Document("version", "live"));
            AtomicBoolean failCatalog = new AtomicBoolean();
            IllegalStateException catalogFailure = new IllegalStateException("catalog unavailable");
            MongoDatabase failingCatalogDatabase = mock(MongoDatabase.class, delegatesTo(database));
            doAnswer(invocation -> {
                if (failCatalog.get()) {
                    throw catalogFailure;
                }
                return database.listCollections(RawBsonDocument.class);
            }).when(failingCatalogDatabase).listCollections(RawBsonDocument.class);
            IllegalStateException forced = new IllegalStateException("injected cutover failure");
            MongoDatabaseImportService importer = new MongoDatabaseImportService(
                failingCatalogDatabase,
                packages,
                () -> {
                    failCatalog.set(true);
                    throw forced;
                }
            );

            assertThatThrownBy(() -> {
                try (MongoDatabaseImportService.ValidatedArchive validated = importer.validateFull(archive, PASSWORD)) {
                    importer.replaceAll(validated);
                }
            }).isInstanceOf(RestoreCutoverException.class)
                .hasCause(forced)
                .satisfies(failure -> {
                    RestoreCutoverException cutoverFailure = (RestoreCutoverException) failure;
                    assertThat(cutoverFailure.maintenanceRequired()).isTrue();
                    assertThat(cutoverFailure.stagingNamespaces()).isNotEmpty();
                    assertThat(cutoverFailure.backupNamespaces()).isNotEmpty();
                    assertThat(forced.getSuppressed()).contains(catalogFailure);
                });
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

        database.runCommand(new Document("create", "attendance_series")
            .append("timeseries", new Document("timeField", "recordedAt")
                .append("metaField", "ministry")
                .append("granularity", "minutes"))
            .append("expireAfterSeconds", 31_536_000L));
        database.getCollection("attendance_series").insertMany(List.of(
            new Document("recordedAt", java.util.Date.from(Instant.parse("2029-01-07T15:00:00Z")))
                .append("ministry", "children")
                .append("count", 24),
            new Document("recordedAt", java.util.Date.from(Instant.parse("2029-01-14T15:00:00Z")))
                .append("ministry", "children")
                .append("count", 27)
        ));
        database.runCommand(new Document("createIndexes", "attendance_series").append("indexes", List.of(
            new Document("key", new Document("ministry", 1).append("recordedAt", -1))
                .append("name", "ministry_recorded_at")
                .append("v", 2)
        )));

        database.runCommand(new Document("create", "active_members")
            .append("viewOn", "members")
            .append("pipeline", List.of(
                new Document("$match", new Document("status", "ACTIVE")),
                new Document("$project", new Document("name", 1).append("status", 1))
            )));
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
        Map<String, List<BsonDocument>> documents = new LinkedHashMap<>();
        Map<String, List<byte[]>> rawCollectionDocuments = new LinkedHashMap<>();
        Map<String, BsonDocument> options = new LinkedHashMap<>();
        Map<String, List<BsonDocument>> indexes = new LinkedHashMap<>();
        Map<String, String> types = namespaceTypes(database);
        types.forEach((name, type) -> {
                documents.put(name, semanticDocuments(database, name));
                if ("collection".equals(type)) {
                    rawCollectionDocuments.put(name, rawDocuments(database, name));
                }
                options.put(name, collectionOptions(database, name));
                indexes.put(name, "view".equals(type) ? List.of() : customIndexSignatures(database, name));
            });

        RawBsonDocument gridFsFile = database.getCollection("fs.files", RawBsonDocument.class)
            .find().first();
        ObjectId gridFsId = gridFsFile == null ? null : gridFsFile.getObjectId("_id").getValue();
        return new DatabaseSnapshot(
            types,
            documents,
            rawCollectionDocuments,
            options,
            indexes,
            gridFsId,
            gridFsId == null ? new byte[0] : downloadGridFs(database, gridFsId),
            types.entrySet().stream()
                .filter(entry -> !"view".equals(entry.getValue()))
                .mapToLong(entry -> documents.get(entry.getKey()).size())
                .sum(),
            indexes.values().stream().mapToLong(List::size).sum()
        );
    }

    private List<BsonDocument> semanticDocuments(MongoDatabase database, String collectionName) {
        List<BsonDocument> result = new ArrayList<>();
        database.getCollection(collectionName, BsonDocument.class)
            .find()
            .forEach(document -> result.add(document.clone()));
        result.sort(Comparator.comparing(BsonDocument::toJson));
        return result;
    }

    private Map<String, String> namespaceTypes(MongoDatabase database) {
        List<RawBsonDocument> catalog = new ArrayList<>();
        database.listCollections(RawBsonDocument.class).into(catalog);
        Map<String, String> result = new LinkedHashMap<>();
        catalog.stream()
            .filter(entry -> isApplicationNamespace(entry.getString("name").getValue()))
            .sorted(Comparator.comparing(entry -> entry.getString("name").getValue()))
            .forEach(entry -> result.put(
                entry.getString("name").getValue(),
                entry.getString("type").getValue()
            ));
        return result;
    }

    private boolean isApplicationNamespace(String name) {
        return !name.startsWith("system.")
            && !name.startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX)
            && !name.startsWith(MongoDatabaseExportService.RESTORE_BACKUP_PREFIX);
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
                    signature.remove("ns");
                    signatures.add(signature);
                }
            });
        signatures.sort(Comparator.comparing(index -> index.getString("name").getValue()));
        return signatures;
    }

    private byte[] downloadGridFs(MongoDatabase database, ObjectId id) {
        if (id == null) {
            return new byte[0];
        }
        GridFSBucket bucket = GridFSBuckets.create(database);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bucket.downloadToStream(id, output);
        return output.toByteArray();
    }

    private DataManagementProperties properties(String directory) {
        return new DataManagementProperties(
            tempDirectory.resolve(directory),
            Duration.ofMinutes(30),
            DataSize.ofGigabytes(2)
        );
    }

    private void assertArchivedIndexVersions(ArchivePackageService packages, Path archive) throws Exception {
        BsonStreamCodec codec = new BsonStreamCodec();
        try (ArchivePackageService.ValidatedArchive validated = packages.validate(
            archive, PASSWORD, com.church.operation.util.ArchiveType.FULL_BACKUP
        )) {
            List<Path> indexSidecars = validated.manifest().collections().stream()
                .filter(entry -> entry.entryName().endsWith("/indexes.bson"))
                .map(entry -> validated.entries().get(entry.entryName()))
                .toList();
            List<BsonDocument> specifications = new ArrayList<>();
            for (Path sidecar : indexSidecars) {
                try (var input = Files.newInputStream(sidecar)) {
                    List<RawBsonDocument> commands = codec.readRawBatch(input, 2);
                    if (!commands.isEmpty()) {
                        BsonArray indexes = commands.getFirst().getArray("indexes");
                        indexes.forEach(value -> specifications.add(value.asDocument()));
                    }
                }
            }
            assertThat(specifications).isNotEmpty().allSatisfy(index -> assertThat(index).containsKey("v"));
        }
    }

    private void appendSecondCreateCommand(
        ArchivePackageService packages,
        Path sourceArchive,
        Path outputArchive,
        String collectionName
    ) throws Exception {
        BsonStreamCodec codec = new BsonStreamCodec();
        Map<String, Path> copiedEntries = new HashMap<>();
        try (ArchivePackageService.ValidatedArchive validated = packages.validate(
            sourceArchive, PASSWORD, com.church.operation.util.ArchiveType.FULL_BACKUP
        )) {
            for (var entry : validated.entries().entrySet()) {
                Path copy = tempDirectory.resolve("semantic-copy").resolve(entry.getKey());
                Files.createDirectories(copy.getParent());
                Files.copy(entry.getValue(), copy);
                copiedEntries.put(entry.getKey(), copy);
            }
            Path options = copiedEntries.get(MongoDatabaseExportService.optionsEntryName(collectionName));
            try (OutputStream output = Files.newOutputStream(options, StandardOpenOption.APPEND)) {
                codec.write(List.of(new Document("create", collectionName)), output);
            }
            packages.write(outputArchive, PASSWORD, validated.manifest(), copiedEntries);
        }
    }

    private void replaceArchivedIndexVersion(
        ArchivePackageService packages,
        Path sourceArchive,
        Path outputArchive,
        String collectionName,
        int version
    ) throws Exception {
        BsonStreamCodec codec = new BsonStreamCodec();
        Map<String, Path> copiedEntries = new HashMap<>();
        try (ArchivePackageService.ValidatedArchive validated = packages.validate(
            sourceArchive, PASSWORD, com.church.operation.util.ArchiveType.FULL_BACKUP
        )) {
            for (var entry : validated.entries().entrySet()) {
                Path copy = tempDirectory.resolve("index-version-copy").resolve(entry.getKey());
                Files.createDirectories(copy.getParent());
                Files.copy(entry.getValue(), copy);
                copiedEntries.put(entry.getKey(), copy);
            }
            Path indexes = copiedEntries.get(MongoDatabaseExportService.indexesEntryName(collectionName));
            BsonDocument command;
            try (var input = Files.newInputStream(indexes)) {
                command = BsonDocument.parse(codec.readRaw(input).toJson());
            }
            command.getArray("indexes").getFirst().asDocument().put("v", new BsonInt32(version));
            try (OutputStream output = Files.newOutputStream(indexes)) {
                codec.writeRaw(new RawBsonDocument(command, new BsonDocumentCodec()), output);
            }
            packages.write(outputArchive, PASSWORD, validated.manifest(), copiedEntries);
        }
    }

    private void dropAllCollections(MongoDatabase database) {
        List<RawBsonDocument> catalog = new ArrayList<>();
        database.listCollections(RawBsonDocument.class).into(catalog);
        catalog.stream()
            .filter(entry -> !"system.views".equals(entry.getString("name").getValue()))
            .filter(entry -> !entry.getString("name").getValue().startsWith("system.buckets."))
            .sorted(Comparator.comparing(entry -> "view".equals(entry.getString("type").getValue()) ? 0 : 1))
            .map(entry -> entry.getString("name").getValue())
            .forEach(name -> database.getCollection(name).drop());
    }

    private record DatabaseSnapshot(
        Map<String, String> types,
        Map<String, List<BsonDocument>> documents,
        Map<String, List<byte[]>> rawCollectionDocuments,
        Map<String, BsonDocument> options,
        Map<String, List<BsonDocument>> indexes,
        ObjectId gridFsId,
        byte[] gridFsBytes,
        long totalDocumentCount,
        long totalCustomIndexCount
    ) {
    }

    private static final class RecordingCommandListener implements CommandListener {
        private final List<BsonDocument> createIndexesCommands = new CopyOnWriteArrayList<>();
        private final List<BsonDocument> viewCreateCommands = new CopyOnWriteArrayList<>();

        @Override
        public void commandStarted(CommandStartedEvent event) {
            if ("createIndexes".equals(event.getCommandName())) {
                createIndexesCommands.add(event.getCommand().clone());
            } else if ("create".equals(event.getCommandName()) && event.getCommand().containsKey("viewOn")) {
                viewCreateCommands.add(event.getCommand().clone());
            }
        }

        private List<BsonDocument> createIndexesCommands() {
            return List.copyOf(createIndexesCommands);
        }

        private List<BsonDocument> viewCreateCommands() {
            return List.copyOf(viewCreateCommands);
        }

        private void clear() {
            createIndexesCommands.clear();
            viewCreateCommands.clear();
        }
    }
}

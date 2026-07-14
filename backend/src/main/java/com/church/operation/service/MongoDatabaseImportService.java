package com.church.operation.service;

import com.church.operation.dto.ArchiveCollectionManifest;
import com.church.operation.util.ArchiveType;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.RenameCollectionOptions;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.RawBsonDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MongoDatabaseImportService {
    private static final int INSERT_BATCH_SIZE = 500;

    private final MongoDatabase database;
    private final ArchivePackageService archivePackageService;
    private final BsonStreamCodec bsonStreamCodec;

    @Autowired
    public MongoDatabaseImportService(
        MongoTemplate mongoTemplate,
        ArchivePackageService archivePackageService
    ) {
        this(mongoTemplate.getDb(), archivePackageService);
    }

    MongoDatabaseImportService(
        MongoDatabase database,
        ArchivePackageService archivePackageService
    ) {
        this.database = database;
        this.archivePackageService = archivePackageService;
        this.bsonStreamCodec = new BsonStreamCodec();
    }

    public ArchivePackageService.ValidatedArchive validateFull(Path archive, char[] password) throws IOException {
        return archivePackageService.validate(archive, password, ArchiveType.FULL_BACKUP);
    }

    public RestoreVerification replaceAll(ArchivePackageService.ValidatedArchive archive) throws IOException {
        Map<String, CollectionEntries> entriesByCollection = collectionEntries(archive);
        dropStagingCollections();

        Map<String, String> stagedCollections = new LinkedHashMap<>();
        long documentCount = 0;
        long indexCount = 0;
        try {
            int ordinal = 0;
            for (Map.Entry<String, CollectionEntries> entry : entriesByCollection.entrySet()) {
                String collectionName = entry.getKey();
                String stagingName = MongoDatabaseExportService.RESTORE_STAGING_PREFIX
                    + UUID.randomUUID().toString().replace("-", "") + "_" + ordinal++;
                stagedCollections.put(collectionName, stagingName);
                CollectionVerification verification = stageCollection(
                    collectionName, stagingName, entry.getValue()
                );
                documentCount += verification.documentCount();
                indexCount += verification.indexCount();
            }

            dropLiveCollections();
            for (Map.Entry<String, String> entry : stagedCollections.entrySet()) {
                database.getCollection(entry.getValue()).renameCollection(
                    new MongoNamespace(database.getName(), entry.getKey()),
                    new RenameCollectionOptions().dropTarget(true)
                );
            }
            return new RestoreVerification(entriesByCollection.size(), documentCount, indexCount);
        } catch (IOException | RuntimeException exception) {
            dropStagingCollections();
            throw exception;
        }
    }

    private CollectionVerification stageCollection(
        String collectionName,
        String stagingName,
        CollectionEntries entries
    ) throws IOException {
        BsonDocument createCommand = readSingleCommand(entries.optionsPath(), true);
        requireTarget(createCommand, "create", collectionName);
        BsonDocument expectedOptions = createCommand.clone();
        expectedOptions.remove("create");
        createCommand.put("create", new BsonString(stagingName));
        database.runCommand(createCommand);

        long inserted = insertDocuments(stagingName, entries.dataPath());
        if (inserted != entries.dataManifest().documentCount()) {
            throw new IllegalArgumentException("Restored document count does not match the manifest.");
        }

        BsonDocument indexesCommand = readSingleCommand(entries.indexesPath(), false);
        List<BsonDocument> expectedIndexes = List.of();
        if (indexesCommand != null) {
            requireTarget(indexesCommand, "createIndexes", collectionName);
            expectedIndexes = indexSpecifications(indexesCommand);
            indexesCommand.put("createIndexes", new BsonString(stagingName));
            database.runCommand(indexesCommand);
        }

        verifyCollection(stagingName, inserted, expectedOptions, expectedIndexes);
        return new CollectionVerification(inserted, expectedIndexes.size());
    }

    private long insertDocuments(String collectionName, Path dataPath) throws IOException {
        long inserted = 0;
        try (InputStream input = Files.newInputStream(dataPath)) {
            List<RawBsonDocument> batch;
            while (!(batch = bsonStreamCodec.readRawBatch(input, INSERT_BATCH_SIZE)).isEmpty()) {
                database.getCollection(collectionName, RawBsonDocument.class).insertMany(batch);
                inserted += batch.size();
            }
        }
        return inserted;
    }

    private void verifyCollection(
        String collectionName,
        long expectedDocumentCount,
        BsonDocument expectedOptions,
        List<BsonDocument> expectedIndexes
    ) {
        long actualDocumentCount = database.getCollection(collectionName).countDocuments();
        if (actualDocumentCount != expectedDocumentCount) {
            throw new IllegalStateException("Restored collection verification failed for document count.");
        }

        RawBsonDocument catalog = database.listCollections(RawBsonDocument.class)
            .filter(new BsonDocument("name", new BsonString(collectionName)))
            .first();
        if (catalog == null || !catalog.getDocument("options").equals(expectedOptions)) {
            throw new IllegalStateException("Restored collection verification failed for options.");
        }

        List<BsonDocument> actualIndexes = new ArrayList<>();
        database.getCollection(collectionName, RawBsonDocument.class)
            .listIndexes(RawBsonDocument.class)
            .forEach(index -> {
                if (!"_id_".equals(index.getString("name").getValue())) {
                    actualIndexes.add(catalogIndexSignature(index));
                }
            });
        actualIndexes.sort(Comparator.comparing(index -> index.getString("name").getValue()));
        List<BsonDocument> expectedCatalogIndexes = expectedIndexes.stream()
            .map(this::catalogIndexSignature)
            .sorted(Comparator.comparing(index -> index.getString("name").getValue()))
            .toList();
        if (!actualIndexes.equals(expectedCatalogIndexes)) {
            throw new IllegalStateException("Restored collection verification failed for indexes.");
        }
    }

    private BsonDocument readSingleCommand(Path path, boolean required) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            List<RawBsonDocument> commands = bsonStreamCodec.readRawBatch(input, 2);
            if (commands.isEmpty()) {
                if (required) {
                    throw new IllegalArgumentException("Backup sidecar command is missing.");
                }
                return null;
            }
            if (commands.size() != 1) {
                throw new IllegalArgumentException("Backup sidecar contains multiple commands.");
            }
            return mutableCopy(commands.getFirst());
        }
    }

    private void requireTarget(BsonDocument command, String commandName, String collectionName) {
        if (!command.containsKey(commandName)
            || !command.get(commandName).isString()
            || !collectionName.equals(command.getString(commandName).getValue())) {
            throw new IllegalArgumentException("Backup sidecar command target is invalid.");
        }
    }

    private BsonDocument mutableCopy(BsonDocument source) {
        BsonDocument copy = new BsonDocument();
        source.forEach(copy::append);
        return copy;
    }

    private BsonDocument catalogIndexSignature(BsonDocument source) {
        BsonDocument signature = mutableCopy(source);
        signature.remove("v");
        signature.remove("ns");
        if (isTextIndex(signature) && signature.containsKey("collation")) {
            BsonDocument collation = signature.getDocument("collation");
            if (collation.size() == 1 && "simple".equals(collation.getString("locale").getValue())) {
                signature.remove("collation");
            }
        }
        return signature;
    }

    private boolean isTextIndex(BsonDocument index) {
        return index.getDocument("key").values().stream()
            .anyMatch(value -> value.isString() && "text".equals(value.asString().getValue()));
    }

    private List<BsonDocument> indexSpecifications(BsonDocument command) {
        if (!command.containsKey("indexes") || !command.get("indexes").isArray()) {
            throw new IllegalArgumentException("Backup createIndexes command is invalid.");
        }
        BsonArray indexes = command.getArray("indexes");
        List<BsonDocument> specifications = new ArrayList<>(indexes.size());
        indexes.forEach(value -> {
            if (!value.isDocument()) {
                throw new IllegalArgumentException("Backup index specification is invalid.");
            }
            BsonDocument specification = value.asDocument();
            specification.remove("v");
            specification.remove("ns");
            specifications.add(specification);
        });
        specifications.sort(Comparator.comparing(index -> index.getString("name").getValue()));
        return List.copyOf(specifications);
    }

    private Map<String, CollectionEntries> collectionEntries(
        ArchivePackageService.ValidatedArchive archive
    ) {
        Map<String, MutableCollectionEntries> grouped = new LinkedHashMap<>();
        for (ArchiveCollectionManifest manifestEntry : archive.manifest().collections()) {
            MutableCollectionEntries entries = grouped.computeIfAbsent(
                manifestEntry.collection(), ignored -> new MutableCollectionEntries()
            );
            Path path = archive.entries().get(manifestEntry.entryName());
            if (manifestEntry.entryName().equals(MongoDatabaseExportService.dataEntryName(manifestEntry.collection()))) {
                entries.setData(path, manifestEntry);
            } else if (manifestEntry.entryName().equals(
                MongoDatabaseExportService.optionsEntryName(manifestEntry.collection())
            )) {
                entries.setOptions(path);
            } else if (manifestEntry.entryName().equals(
                MongoDatabaseExportService.indexesEntryName(manifestEntry.collection())
            )) {
                entries.setIndexes(path);
            } else {
                throw new IllegalArgumentException("Backup contains an unrecognized collection sidecar.");
            }
        }

        Map<String, CollectionEntries> complete = new LinkedHashMap<>();
        grouped.forEach((collection, entries) -> complete.put(collection, entries.complete()));
        return complete;
    }

    private void dropStagingCollections() {
        collectionNames().stream()
            .filter(name -> name.startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX))
            .forEach(name -> database.getCollection(name).drop());
    }

    private void dropLiveCollections() {
        collectionNames().stream()
            .filter(name -> !name.startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX))
            .forEach(name -> database.getCollection(name).drop());
    }

    private List<String> collectionNames() {
        List<String> names = new ArrayList<>();
        database.listCollectionNames().into(names);
        return names;
    }

    private record CollectionEntries(
        Path dataPath,
        ArchiveCollectionManifest dataManifest,
        Path optionsPath,
        Path indexesPath
    ) {
    }

    private record CollectionVerification(long documentCount, long indexCount) {
    }

    private static final class MutableCollectionEntries {
        private Path dataPath;
        private ArchiveCollectionManifest dataManifest;
        private Path optionsPath;
        private Path indexesPath;

        private void setData(Path path, ArchiveCollectionManifest manifest) {
            if (dataPath != null) {
                throw new IllegalArgumentException("Backup contains duplicate data sidecars.");
            }
            dataPath = path;
            dataManifest = manifest;
        }

        private void setOptions(Path path) {
            if (optionsPath != null) {
                throw new IllegalArgumentException("Backup contains duplicate options sidecars.");
            }
            optionsPath = path;
        }

        private void setIndexes(Path path) {
            if (indexesPath != null) {
                throw new IllegalArgumentException("Backup contains duplicate index sidecars.");
            }
            indexesPath = path;
        }

        private CollectionEntries complete() {
            if (dataPath == null || optionsPath == null || indexesPath == null) {
                throw new IllegalArgumentException("Backup collection sidecars are incomplete.");
            }
            return new CollectionEntries(dataPath, dataManifest, optionsPath, indexesPath);
        }
    }
}

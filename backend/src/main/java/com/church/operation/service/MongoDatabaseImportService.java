package com.church.operation.service;

import com.church.operation.dto.ArchiveCollectionManifest;
import com.church.operation.dto.ArchiveManifest;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MongoDatabaseImportService {
    private static final int INSERT_BATCH_SIZE = 500;
    private static final int INSERT_BATCH_BYTES = 16 * 1024 * 1024;
    private static final String COLLECTION_TYPE = "collection";
    private static final String VIEW_TYPE = "view";
    private static final String TIMESERIES_TYPE = "timeseries";
    private static final Set<String> CREATE_COMMAND_FIELDS = Set.of(
        "create",
        "capped",
        "size",
        "max",
        "validator",
        "validationLevel",
        "validationAction",
        "storageEngine",
        "collation",
        "viewOn",
        "pipeline",
        "timeseries",
        "expireAfterSeconds",
        "clusteredIndex",
        "changeStreamPreAndPostImages",
        "recordIdsReplicated",
        "autoIndexId",
        "indexOptionDefaults",
        "encryptedFields"
    );
    private static final Set<String> INDEX_COMMAND_FIELDS = Set.of("createIndexes", "indexes");
    private static final Set<String> INDEX_SPECIFICATION_FIELDS = Set.of(
        "key",
        "name",
        "v",
        "unique",
        "sparse",
        "expireAfterSeconds",
        "partialFilterExpression",
        "collation",
        "hidden",
        "weights",
        "default_language",
        "language_override",
        "textIndexVersion",
        "2dsphereIndexVersion",
        "bits",
        "min",
        "max",
        "bucketSize",
        "storageEngine",
        "wildcardProjection",
        "background",
        "prepareUnique"
    );

    private final MongoDatabase database;
    private final ArchivePackageService archivePackageService;
    private final BsonStreamCodec bsonStreamCodec;
    private final CutoverHook cutoverHook;

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
        this(database, archivePackageService, () -> { });
    }

    MongoDatabaseImportService(
        MongoDatabase database,
        ArchivePackageService archivePackageService,
        CutoverHook cutoverHook
    ) {
        if (database == null || archivePackageService == null || cutoverHook == null) {
            throw new IllegalArgumentException("MongoDB restore dependencies are required.");
        }
        this.database = database;
        this.archivePackageService = archivePackageService;
        this.bsonStreamCodec = new BsonStreamCodec();
        this.cutoverHook = cutoverHook;
    }

    public ValidatedArchive validateFull(Path archive, char[] password) throws IOException {
        ArchivePackageService.ValidatedArchive packageArchive = archivePackageService.validate(
            archive, password, ArchiveType.FULL_BACKUP
        );
        try {
            RestorePlan restorePlan = preflight(packageArchive);
            return new ValidatedArchive(this, packageArchive, restorePlan);
        } catch (IOException | RuntimeException | Error exception) {
            closeAfterFailure(packageArchive, exception);
            throw exception;
        }
    }

    public RestoreVerification replaceAll(ValidatedArchive archive) throws IOException {
        RestorePlan restorePlan = archive.requirePlan(this);
        ensureNoRecoveryNamespaces();

        String operationId = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> stagingNames = stagingNames(restorePlan, operationId);
        try {
            stageAll(restorePlan, stagingNames);
        } catch (IOException | RuntimeException | Error exception) {
            cleanupPreCutoverStaging(stagingNames.values(), exception);
            throw exception;
        }
        try {
            return cutover(restorePlan, stagingNames, operationId);
        } catch (RestoreCutoverException exception) {
            throw exception;
        } catch (IOException | RuntimeException | Error exception) {
            cleanupPreCutoverStaging(stagingNames.values(), exception);
            throw exception;
        }
    }

    private RestorePlan preflight(ArchivePackageService.ValidatedArchive archive) throws IOException {
        Map<String, MutableCollectionEntries> grouped = new LinkedHashMap<>();
        for (ArchiveCollectionManifest manifestEntry : archive.manifest().collections()) {
            validateCollectionName(manifestEntry.collection());
            MutableCollectionEntries entries = grouped.computeIfAbsent(
                manifestEntry.collection(), ignored -> new MutableCollectionEntries()
            );
            Path path = archive.entries().get(manifestEntry.entryName());
            if (path == null) {
                throw new IllegalArgumentException("Backup manifest entry has no validated extracted file.");
            }
            String collection = manifestEntry.collection();
            if (manifestEntry.entryName().equals(MongoDatabaseExportService.dataEntryName(collection))) {
                entries.setData(path, manifestEntry);
            } else if (manifestEntry.entryName().equals(MongoDatabaseExportService.optionsEntryName(collection))) {
                entries.setOptions(path);
            } else if (manifestEntry.entryName().equals(MongoDatabaseExportService.indexesEntryName(collection))) {
                entries.setIndexes(path);
            } else {
                throw new IllegalArgumentException("Backup contains an unrecognized collection sidecar.");
            }
        }

        List<NamespacePlan> namespaces = new ArrayList<>();
        for (Map.Entry<String, MutableCollectionEntries> entry : grouped.entrySet()) {
            CollectionEntries files = entry.getValue().complete();
            namespaces.add(preflightNamespace(entry.getKey(), files));
        }
        namespaces.sort(Comparator.comparing(NamespacePlan::name));
        validateViewRelationships(namespaces);
        return new RestorePlan(List.copyOf(namespaces));
    }

    private NamespacePlan preflightNamespace(String collectionName, CollectionEntries entries) throws IOException {
        long dataCount;
        try (InputStream input = Files.newInputStream(entries.dataPath())) {
            dataCount = bsonStreamCodec.count(input);
        }
        if (dataCount != entries.dataManifest().documentCount()) {
            throw new IllegalArgumentException("Backup data sidecar count does not match its manifest entry.");
        }

        BsonDocument createCommand = readCreateCommand(entries.optionsPath());
        validateCommandFields(createCommand, CREATE_COMMAND_FIELDS, "create");
        requireTarget(createCommand, "create", collectionName);
        NamespaceType type = namespaceType(createCommand);
        validateCreateCommand(type, createCommand);

        BsonDocument indexesCommand = readIndexesCommand(entries.indexesPath());
        List<BsonDocument> indexes = validateIndexCommand(collectionName, indexesCommand);
        if (type == NamespaceType.VIEW) {
            if (dataCount != 0) {
                throw new IllegalArgumentException("Backup view data sidecar must be empty.");
            }
            if (!indexes.isEmpty()) {
                throw new IllegalArgumentException("Backup view index sidecar must be empty.");
            }
        }
        BsonDocument expectedOptions = createCommand.clone();
        expectedOptions.remove("create");
        return new NamespacePlan(
            collectionName,
            type,
            entries,
            createCommand,
            expectedOptions,
            indexesCommand,
            indexes
        );
    }

    private BsonDocument readCreateCommand(Path path) throws IOException {
        List<RawBsonDocument> commands = readCommands(path);
        if (commands.size() != 1) {
            throw new IllegalArgumentException("Backup options sidecar must contain a single create command.");
        }
        return mutableCopy(commands.getFirst());
    }

    private BsonDocument readIndexesCommand(Path path) throws IOException {
        List<RawBsonDocument> commands = readCommands(path);
        if (commands.isEmpty()) {
            return null;
        }
        if (commands.size() != 1) {
            throw new IllegalArgumentException("Backup index sidecar must contain at most one createIndexes command.");
        }
        return mutableCopy(commands.getFirst());
    }

    private List<RawBsonDocument> readCommands(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return bsonStreamCodec.readRawBatch(input, 2);
        }
    }

    private void validateCommandFields(BsonDocument command, Set<String> allowed, String commandName) {
        if (!allowed.containsAll(command.keySet())) {
            throw new IllegalArgumentException("Backup " + commandName + " command contains unsupported fields.");
        }
    }

    private NamespaceType namespaceType(BsonDocument command) {
        boolean view = command.containsKey("viewOn") || command.containsKey("pipeline");
        boolean timeseries = command.containsKey("timeseries");
        if (view && timeseries) {
            throw new IllegalArgumentException("Backup create command has conflicting namespace types.");
        }
        if (view) {
            return NamespaceType.VIEW;
        }
        if (timeseries) {
            return NamespaceType.TIMESERIES;
        }
        return NamespaceType.COLLECTION;
    }

    private void validateCreateCommand(NamespaceType type, BsonDocument command) {
        validateCreateOptionShapes(command);
        if (type == NamespaceType.VIEW) {
            if (!Set.of("create", "viewOn", "pipeline", "collation").containsAll(command.keySet())) {
                throw new IllegalArgumentException("Backup view create command contains invalid options.");
            }
            if (!command.containsKey("viewOn") || !command.get("viewOn").isString()
                || command.getString("viewOn").getValue().isBlank()
                || !command.containsKey("pipeline") || !command.get("pipeline").isArray()) {
                throw new IllegalArgumentException("Backup view create command is invalid.");
            }
            validateCollectionName(command.getString("viewOn").getValue());
            command.getArray("pipeline").forEach(stage -> {
                if (!stage.isDocument() || stage.asDocument().containsKey("$out")
                    || stage.asDocument().containsKey("$merge")) {
                    throw new IllegalArgumentException("Backup view pipeline is invalid.");
                }
            });
        } else if (command.containsKey("viewOn") || command.containsKey("pipeline")) {
            throw new IllegalArgumentException("Backup create command has invalid view options.");
        }

        if (type == NamespaceType.TIMESERIES) {
            if (!command.get("timeseries").isDocument()) {
                throw new IllegalArgumentException("Backup time-series options are invalid.");
            }
            BsonDocument timeseries = command.getDocument("timeseries");
            if (!Set.of(
                "timeField", "metaField", "granularity", "bucketMaxSpanSeconds", "bucketRoundingSeconds"
            ).containsAll(timeseries.keySet())) {
                throw new IllegalArgumentException("Backup time-series options are invalid.");
            }
            if (!timeseries.containsKey("timeField") || !timeseries.get("timeField").isString()
                || timeseries.getString("timeField").getValue().isBlank()) {
                throw new IllegalArgumentException("Backup time-series options are invalid.");
            }
            if (timeseries.containsKey("metaField") && (!timeseries.get("metaField").isString()
                || timeseries.getString("metaField").getValue().isBlank())) {
                throw new IllegalArgumentException("Backup time-series options are invalid.");
            }
            if (timeseries.containsKey("granularity") && (!timeseries.get("granularity").isString()
                || !Set.of("seconds", "minutes", "hours").contains(
                    timeseries.getString("granularity").getValue()
                ))) {
                throw new IllegalArgumentException("Backup time-series options are invalid.");
            }
            for (String field : List.of("bucketMaxSpanSeconds", "bucketRoundingSeconds")) {
                if (timeseries.containsKey(field) && !isNumber(timeseries.get(field))) {
                    throw new IllegalArgumentException("Backup time-series options are invalid.");
                }
            }
        } else if (command.containsKey("expireAfterSeconds")) {
            throw new IllegalArgumentException("Backup create command has time-series-only options.");
        }
        if (command.containsKey("capped") && command.getBoolean("capped").getValue()
            && !command.containsKey("size")) {
            throw new IllegalArgumentException("Backup capped collection size is missing.");
        }
    }

    private void validateCreateOptionShapes(BsonDocument command) {
        for (String field : List.of("capped", "autoIndexId", "recordIdsReplicated")) {
            if (command.containsKey(field) && !command.get(field).isBoolean()) {
                throw new IllegalArgumentException("Backup create command option type is invalid.");
            }
        }
        for (String field : List.of("size", "max", "expireAfterSeconds")) {
            if (command.containsKey(field) && !isNumber(command.get(field))) {
                throw new IllegalArgumentException("Backup create command option type is invalid.");
            }
        }
        for (String field : List.of(
            "validator",
            "storageEngine",
            "collation",
            "clusteredIndex",
            "changeStreamPreAndPostImages",
            "indexOptionDefaults",
            "encryptedFields"
        )) {
            if (command.containsKey(field) && !command.get(field).isDocument()) {
                throw new IllegalArgumentException("Backup create command option type is invalid.");
            }
        }
        if (command.containsKey("validationLevel") && (!command.get("validationLevel").isString()
            || !Set.of("off", "strict", "moderate").contains(
                command.getString("validationLevel").getValue()
            ))) {
            throw new IllegalArgumentException("Backup validation level is invalid.");
        }
        if (command.containsKey("validationAction") && (!command.get("validationAction").isString()
            || !Set.of("error", "warn").contains(command.getString("validationAction").getValue()))) {
            throw new IllegalArgumentException("Backup validation action is invalid.");
        }
        if (command.containsKey("collation")) {
            BsonDocument collation = command.getDocument("collation");
            if (!collation.containsKey("locale") || !collation.get("locale").isString()) {
                throw new IllegalArgumentException("Backup collation is invalid.");
            }
        }
    }

    private List<BsonDocument> validateIndexCommand(String collectionName, BsonDocument command) {
        if (command == null) {
            return List.of();
        }
        validateCommandFields(command, INDEX_COMMAND_FIELDS, "createIndexes");
        requireTarget(command, "createIndexes", collectionName);
        if (!command.containsKey("indexes") || !command.get("indexes").isArray()
            || command.getArray("indexes").isEmpty()) {
            throw new IllegalArgumentException("Backup createIndexes command is invalid.");
        }

        Set<String> names = new HashSet<>();
        List<BsonDocument> specifications = new ArrayList<>();
        for (var value : command.getArray("indexes")) {
            if (!value.isDocument()) {
                throw new IllegalArgumentException("Backup index specification is invalid.");
            }
            BsonDocument specification = value.asDocument();
            if (!INDEX_SPECIFICATION_FIELDS.containsAll(specification.keySet())
                || specification.containsKey("ns")
                || !specification.containsKey("key") || !specification.get("key").isDocument()
                || specification.getDocument("key").isEmpty()
                || !specification.containsKey("name") || !specification.get("name").isString()
                || specification.getString("name").getValue().isBlank()
                || "_id_".equals(specification.getString("name").getValue())
                || !names.add(specification.getString("name").getValue())) {
                throw new IllegalArgumentException("Backup index specification is invalid.");
            }
            if (specification.containsKey("v")) {
                if (!specification.get("v").isInt32() && !specification.get("v").isInt64()) {
                    throw new IllegalArgumentException("Backup index version is invalid.");
                }
                long version = specification.get("v").isInt32()
                    ? specification.getInt32("v").getValue()
                    : specification.getInt64("v").getValue();
                if (version != 1 && version != 2) {
                    throw new IllegalArgumentException("Backup index version is invalid.");
                }
            }
            validateIndexSpecificationShapes(specification);
            specifications.add(specification.clone());
        }
        specifications.sort(Comparator.comparing(index -> index.getString("name").getValue()));
        return List.copyOf(specifications);
    }

    private void validateIndexSpecificationShapes(BsonDocument specification) {
        if (specification.getDocument("key").values().stream()
            .anyMatch(value -> !value.isString() && !isNumber(value))) {
            throw new IllegalArgumentException("Backup index key specification is invalid.");
        }
        for (String field : List.of("unique", "sparse", "hidden", "background", "prepareUnique")) {
            if (specification.containsKey(field) && !specification.get(field).isBoolean()) {
                throw new IllegalArgumentException("Backup index option type is invalid.");
            }
        }
        for (String field : List.of(
            "partialFilterExpression", "collation", "weights", "storageEngine", "wildcardProjection"
        )) {
            if (specification.containsKey(field) && !specification.get(field).isDocument()) {
                throw new IllegalArgumentException("Backup index option type is invalid.");
            }
        }
        for (String field : List.of(
            "expireAfterSeconds", "textIndexVersion", "2dsphereIndexVersion", "bits", "min", "max", "bucketSize"
        )) {
            if (specification.containsKey(field) && !isNumber(specification.get(field))) {
                throw new IllegalArgumentException("Backup index option type is invalid.");
            }
        }
        for (String field : List.of("default_language", "language_override")) {
            if (specification.containsKey(field) && !specification.get(field).isString()) {
                throw new IllegalArgumentException("Backup index option type is invalid.");
            }
        }
    }

    private boolean isNumber(org.bson.BsonValue value) {
        return value.isInt32() || value.isInt64() || value.isDouble() || value.isDecimal128();
    }

    private void validateViewRelationships(List<NamespacePlan> namespaces) {
        Map<String, NamespacePlan> byName = new LinkedHashMap<>();
        namespaces.forEach(namespace -> byName.put(namespace.name(), namespace));
        for (NamespacePlan namespace : namespaces) {
            if (namespace.type() == NamespaceType.VIEW) {
                String source = namespace.createCommand().getString("viewOn").getValue();
                if (!byName.containsKey(source)) {
                    throw new IllegalArgumentException("Backup view source is missing from the archive.");
                }
            }
        }
        orderedViews(namespaces);
    }

    private List<NamespacePlan> orderedViews(List<NamespacePlan> namespaces) {
        Map<String, NamespacePlan> remaining = new LinkedHashMap<>();
        namespaces.stream()
            .filter(namespace -> namespace.type() == NamespaceType.VIEW)
            .forEach(namespace -> remaining.put(namespace.name(), namespace));
        List<NamespacePlan> ordered = new ArrayList<>();
        while (!remaining.isEmpty()) {
            List<NamespacePlan> ready = remaining.values().stream()
                .filter(view -> !remaining.containsKey(view.createCommand().getString("viewOn").getValue()))
                .toList();
            if (ready.isEmpty()) {
                throw new IllegalArgumentException("Backup view dependencies contain a cycle.");
            }
            ready.forEach(view -> {
                ordered.add(view);
                remaining.remove(view.name());
            });
        }
        return ordered;
    }

    private void validateCollectionName(String name) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0
            || name.startsWith("system.")
            || name.startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX)
            || name.startsWith(MongoDatabaseExportService.RESTORE_BACKUP_PREFIX)
            || database.getName().getBytes(StandardCharsets.UTF_8).length + 1
                + name.getBytes(StandardCharsets.UTF_8).length > 255) {
            throw new IllegalArgumentException("Backup collection name is invalid or reserved.");
        }
    }

    private void requireTarget(BsonDocument command, String commandName, String collectionName) {
        if (!command.containsKey(commandName)
            || !command.get(commandName).isString()
            || !collectionName.equals(command.getString(commandName).getValue())) {
            throw new IllegalArgumentException("Backup sidecar command target is invalid.");
        }
    }

    private Map<String, String> stagingNames(RestorePlan restorePlan, String operationId) {
        Map<String, String> names = new LinkedHashMap<>();
        int ordinal = 0;
        for (NamespacePlan namespace : restorePlan.namespaces()) {
            names.put(
                namespace.name(),
                MongoDatabaseExportService.RESTORE_STAGING_PREFIX + operationId + "_" + ordinal++
            );
        }
        return names;
    }

    private void stageAll(RestorePlan restorePlan, Map<String, String> stagingNames) throws IOException {
        List<NamespacePlan> stagingOrder = new ArrayList<>();
        restorePlan.namespaces().stream()
            .filter(namespace -> namespace.type() == NamespaceType.COLLECTION)
            .forEach(stagingOrder::add);
        restorePlan.namespaces().stream()
            .filter(namespace -> namespace.type() == NamespaceType.TIMESERIES)
            .forEach(stagingOrder::add);
        stagingOrder.addAll(orderedViews(restorePlan.namespaces()));

        for (NamespacePlan namespace : stagingOrder) {
            stageNamespace(namespace, stagingNames.get(namespace.name()), stagingNames);
        }
    }

    private void stageNamespace(
        NamespacePlan namespace,
        String stagingName,
        Map<String, String> stagingNames
    ) throws IOException {
        BsonDocument createCommand = namespace.createCommand().clone();
        createCommand.put("create", new BsonString(stagingName));
        if (namespace.type() == NamespaceType.VIEW) {
            String source = createCommand.getString("viewOn").getValue();
            createCommand.put("viewOn", new BsonString(stagingNames.get(source)));
        }
        database.runCommand(createCommand);

        long inserted = 0;
        if (namespace.type() != NamespaceType.VIEW) {
            inserted = insertDocuments(stagingName, namespace.entries().dataPath());
            runIndexes(namespace, stagingName);
        }
        BsonDocument stagedOptions = createCommand.clone();
        stagedOptions.remove("create");
        verifyNamespace(namespace, stagingName, inserted, stagedOptions);
    }

    private void runIndexes(NamespacePlan namespace, String target) {
        if (namespace.indexesCommand() == null) {
            return;
        }
        BsonDocument command = namespace.indexesCommand().clone();
        command.put("createIndexes", new BsonString(target));
        database.runCommand(command);
    }

    private long insertDocuments(String collectionName, Path dataPath) throws IOException {
        long inserted = 0;
        try (InputStream input = Files.newInputStream(dataPath)) {
            BsonStreamCodec.RawBatchReader reader = bsonStreamCodec.rawBatchReader(
                input, INSERT_BATCH_SIZE, INSERT_BATCH_BYTES
            );
            List<RawBsonDocument> batch;
            while (!(batch = reader.readBatch()).isEmpty()) {
                database.getCollection(collectionName, RawBsonDocument.class).insertMany(batch);
                inserted += batch.size();
            }
        }
        return inserted;
    }

    private void verifyNamespace(
        NamespacePlan namespace,
        String actualName,
        long actualDocumentCount,
        BsonDocument expectedOptions
    ) {
        if (namespace.type() != NamespaceType.VIEW
            && actualDocumentCount != namespace.entries().dataManifest().documentCount()) {
            throw new IllegalStateException("Restored namespace verification failed for document count.");
        }

        RawBsonDocument catalog = namespaceCatalog(actualName);
        if (catalog == null
            || !namespace.type().catalogType.equals(catalog.getString("type").getValue())
            || !catalog.getDocument("options").equals(expectedOptions)) {
            throw new IllegalStateException("Restored namespace verification failed for type or options.");
        }

        if (namespace.type() == NamespaceType.VIEW) {
            return;
        }
        List<BsonDocument> actualIndexes = customIndexSignatures(actualName);
        List<BsonDocument> expectedIndexes = namespace.indexes().stream()
            .map(this::catalogIndexSignature)
            .sorted(Comparator.comparing(index -> index.getString("name").getValue()))
            .toList();
        if (!actualIndexes.equals(expectedIndexes)) {
            throw new IllegalStateException("Restored namespace verification failed for indexes.");
        }
    }

    private RestoreVerification cutover(
        RestorePlan restorePlan,
        Map<String, String> stagingNames,
        String operationId
    ) throws IOException {
        List<NamespaceCatalog> liveNamespaces = liveNamespaces();
        Map<String, String> backupNames = backupNames(liveNamespaces, operationId);
        List<Map.Entry<String, String>> movedBackups = new ArrayList<>();
        List<Map.Entry<String, String>> promotions = new ArrayList<>();
        try {
            for (NamespaceCatalog live : liveNamespaces) {
                if (live.type() == NamespaceType.COLLECTION) {
                    String backup = backupNames.get(live.name());
                    rename(live.name(), backup, false);
                    movedBackups.add(Map.entry(live.name(), backup));
                }
            }
            dropNonRenamableLive(liveNamespaces, NamespaceType.VIEW);
            dropNonRenamableLive(liveNamespaces, NamespaceType.TIMESERIES);

            boolean hookInvoked = false;
            for (NamespacePlan namespace : restorePlan.namespaces()) {
                if (namespace.type() == NamespaceType.COLLECTION) {
                    String staging = stagingNames.get(namespace.name());
                    rename(staging, namespace.name(), false);
                    promotions.add(Map.entry(namespace.name(), staging));
                    if (!hookInvoked) {
                        hookInvoked = true;
                        cutoverHook.afterFirstPromotion();
                    }
                }
            }
            for (NamespacePlan namespace : restorePlan.namespaces()) {
                if (namespace.type() == NamespaceType.TIMESERIES) {
                    replayNamespace(namespace);
                }
            }
            for (NamespacePlan namespace : orderedViews(restorePlan.namespaces())) {
                replayNamespace(namespace);
            }
            verifyLiveRestore(restorePlan);
        } catch (IOException | RuntimeException | Error exception) {
            rollbackOrdinaryCollections(promotions, movedBackups, exception);
            throw cutoverFailure("Database restore cutover failed; maintenance recovery is required.", exception);
        }

        try {
            dropKnownNamespaces(backupNames.values());
            dropKnownNamespaces(stagingNames.values());
        } catch (RuntimeException cleanupFailure) {
            throw cutoverFailure(
                "Database restore completed but recovery namespace cleanup failed; maintenance is required.",
                cleanupFailure
            );
        }
        return new RestoreVerification(
            restorePlan.namespaces().size(),
            restorePlan.namespaces().stream()
                .mapToLong(namespace -> namespace.entries().dataManifest().documentCount())
                .sum(),
            restorePlan.namespaces().stream().mapToLong(namespace -> namespace.indexes().size()).sum()
        );
    }

    private void replayNamespace(NamespacePlan namespace) throws IOException {
        database.runCommand(namespace.createCommand());
        long inserted = 0;
        if (namespace.type() != NamespaceType.VIEW) {
            inserted = insertDocuments(namespace.name(), namespace.entries().dataPath());
            runIndexes(namespace, namespace.name());
        }
        verifyNamespace(namespace, namespace.name(), inserted, namespace.expectedOptions());
    }

    private void verifyLiveRestore(RestorePlan restorePlan) {
        Set<String> expectedNames = restorePlan.namespaces().stream()
            .map(NamespacePlan::name)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> actualNames = liveNamespaces().stream()
            .map(NamespaceCatalog::name)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!actualNames.equals(expectedNames)) {
            throw new IllegalStateException("Restored database namespace set does not match the archive.");
        }
        for (NamespacePlan namespace : restorePlan.namespaces()) {
            long count = namespace.type() == NamespaceType.VIEW
                ? 0
                : database.getCollection(namespace.name()).countDocuments();
            verifyNamespace(namespace, namespace.name(), count, namespace.expectedOptions());
        }
    }

    private Map<String, String> backupNames(List<NamespaceCatalog> liveNamespaces, String operationId) {
        Map<String, String> names = new LinkedHashMap<>();
        int ordinal = 0;
        for (NamespaceCatalog namespace : liveNamespaces) {
            if (namespace.type() == NamespaceType.COLLECTION) {
                names.put(
                    namespace.name(),
                    MongoDatabaseExportService.RESTORE_BACKUP_PREFIX + operationId + "_" + ordinal++
                );
            }
        }
        return names;
    }

    private void dropNonRenamableLive(List<NamespaceCatalog> liveNamespaces, NamespaceType type) {
        liveNamespaces.stream()
            .filter(namespace -> namespace.type() == type)
            .forEach(namespace -> database.getCollection(namespace.name()).drop());
    }

    private void rollbackOrdinaryCollections(
        List<Map.Entry<String, String>> promotions,
        List<Map.Entry<String, String>> movedBackups,
        Throwable primary
    ) {
        List<Map.Entry<String, String>> reversePromotions = new ArrayList<>(promotions);
        Collections.reverse(reversePromotions);
        for (Map.Entry<String, String> promotion : reversePromotions) {
            try {
                if (namespaceCatalog(promotion.getKey()) != null
                    && namespaceCatalog(promotion.getValue()) == null) {
                    rename(promotion.getKey(), promotion.getValue(), false);
                }
            } catch (RuntimeException rollbackFailure) {
                primary.addSuppressed(rollbackFailure);
            }
        }

        List<Map.Entry<String, String>> reverseBackups = new ArrayList<>(movedBackups);
        Collections.reverse(reverseBackups);
        for (Map.Entry<String, String> backup : reverseBackups) {
            try {
                if (namespaceCatalog(backup.getValue()) != null
                    && namespaceCatalog(backup.getKey()) == null) {
                    rename(backup.getValue(), backup.getKey(), false);
                }
            } catch (RuntimeException rollbackFailure) {
                primary.addSuppressed(rollbackFailure);
            }
        }
    }

    private RestoreCutoverException cutoverFailure(String message, Throwable cause) {
        List<String> staging = new ArrayList<>();
        List<String> backups = new ArrayList<>();
        for (NamespaceCatalog namespace : allNamespaces()) {
            if (namespace.name().startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX)) {
                staging.add(namespace.name());
            } else if (namespace.name().startsWith(MongoDatabaseExportService.RESTORE_BACKUP_PREFIX)) {
                backups.add(namespace.name());
            }
        }
        return new RestoreCutoverException(message, cause, staging, backups);
    }

    private void ensureNoRecoveryNamespaces() throws RestoreCutoverException {
        List<String> existing = allNamespaces().stream()
            .map(NamespaceCatalog::name)
            .filter(name -> name.startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX)
                || name.startsWith(MongoDatabaseExportService.RESTORE_BACKUP_PREFIX))
            .toList();
        if (!existing.isEmpty()) {
            throw new RestoreCutoverException(
                "Restore recovery namespaces already exist; maintenance recovery is required.",
                null,
                existing.stream().filter(name -> name.startsWith(
                    MongoDatabaseExportService.RESTORE_STAGING_PREFIX
                )).toList(),
                existing.stream().filter(name -> name.startsWith(
                    MongoDatabaseExportService.RESTORE_BACKUP_PREFIX
                )).toList()
            );
        }
    }

    private void cleanupPreCutoverStaging(Iterable<String> stagingNames, Throwable primary) {
        try {
            dropKnownNamespaces(stagingNames);
        } catch (RuntimeException cleanupFailure) {
            primary.addSuppressed(cleanupFailure);
        }
    }

    private void dropKnownNamespaces(Iterable<String> names) {
        Set<String> requested = new LinkedHashSet<>();
        names.forEach(requested::add);
        allNamespaces().stream()
            .filter(namespace -> requested.contains(namespace.name()))
            .sorted(Comparator.comparingInt(namespace -> dropOrder(namespace.type())))
            .forEach(namespace -> database.getCollection(namespace.name()).drop());
    }

    private int dropOrder(NamespaceType type) {
        return switch (type) {
            case VIEW -> 0;
            case TIMESERIES -> 1;
            case COLLECTION -> 2;
        };
    }

    private void rename(String source, String target, boolean dropTarget) {
        database.getCollection(source).renameCollection(
            new MongoNamespace(database.getName(), target),
            new RenameCollectionOptions().dropTarget(dropTarget)
        );
    }

    private List<NamespaceCatalog> liveNamespaces() {
        return allNamespaces().stream()
            .filter(namespace -> !namespace.name().startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX))
            .filter(namespace -> !namespace.name().startsWith(MongoDatabaseExportService.RESTORE_BACKUP_PREFIX))
            .toList();
    }

    private List<NamespaceCatalog> allNamespaces() {
        List<RawBsonDocument> catalog = new ArrayList<>();
        database.listCollections(RawBsonDocument.class).into(catalog);
        return catalog.stream()
            .filter(entry -> !isMongoOwnedCompanion(entry.getString("name").getValue()))
            .map(entry -> new NamespaceCatalog(
                entry.getString("name").getValue(),
                NamespaceType.fromCatalog(entry.getString("type").getValue())
            ))
            .sorted(Comparator.comparing(NamespaceCatalog::name))
            .toList();
    }

    private RawBsonDocument namespaceCatalog(String name) {
        return database.listCollections(RawBsonDocument.class)
            .filter(new BsonDocument("name", new BsonString(name)))
            .first();
    }

    private boolean isMongoOwnedCompanion(String name) {
        return "system.views".equals(name) || name.startsWith("system.buckets.");
    }

    private List<BsonDocument> customIndexSignatures(String collectionName) {
        List<BsonDocument> signatures = new ArrayList<>();
        database.getCollection(collectionName, RawBsonDocument.class)
            .listIndexes(RawBsonDocument.class)
            .forEach(index -> {
                if (!"_id_".equals(index.getString("name").getValue())) {
                    signatures.add(catalogIndexSignature(index));
                }
            });
        signatures.sort(Comparator.comparing(index -> index.getString("name").getValue()));
        return signatures;
    }

    private BsonDocument catalogIndexSignature(BsonDocument source) {
        BsonDocument signature = mutableCopy(source);
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

    private BsonDocument mutableCopy(BsonDocument source) {
        BsonDocument copy = new BsonDocument();
        source.forEach(copy::append);
        return copy;
    }

    private void closeAfterFailure(AutoCloseable closeable, Throwable primary) {
        try {
            closeable.close();
        } catch (Exception cleanupFailure) {
            primary.addSuppressed(cleanupFailure);
        }
    }

    @FunctionalInterface
    interface CutoverHook {
        void afterFirstPromotion();
    }

    public static final class ValidatedArchive implements AutoCloseable {
        private final MongoDatabaseImportService owner;
        private final ArchivePackageService.ValidatedArchive packageArchive;
        private final RestorePlan restorePlan;
        private boolean closed;

        private ValidatedArchive(
            MongoDatabaseImportService owner,
            ArchivePackageService.ValidatedArchive packageArchive,
            RestorePlan restorePlan
        ) {
            this.owner = owner;
            this.packageArchive = packageArchive;
            this.restorePlan = restorePlan;
        }

        public ArchiveManifest manifest() {
            return packageArchive.manifest();
        }

        private RestorePlan requirePlan(MongoDatabaseImportService expectedOwner) {
            if (closed || owner != expectedOwner) {
                throw new IllegalArgumentException("Validated restore archive is closed or belongs to another importer.");
            }
            return restorePlan;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                packageArchive.close();
            }
        }
    }

    private enum NamespaceType {
        COLLECTION(COLLECTION_TYPE),
        VIEW(VIEW_TYPE),
        TIMESERIES(TIMESERIES_TYPE);

        private final String catalogType;

        NamespaceType(String catalogType) {
            this.catalogType = catalogType;
        }

        private static NamespaceType fromCatalog(String type) {
            return switch (type) {
                case COLLECTION_TYPE -> COLLECTION;
                case VIEW_TYPE -> VIEW;
                case TIMESERIES_TYPE -> TIMESERIES;
                default -> throw new IllegalStateException("MongoDB returned an unsupported namespace type: " + type);
            };
        }
    }

    private record NamespaceCatalog(String name, NamespaceType type) {
    }

    private record RestorePlan(List<NamespacePlan> namespaces) {
    }

    private record NamespacePlan(
        String name,
        NamespaceType type,
        CollectionEntries entries,
        BsonDocument createCommand,
        BsonDocument expectedOptions,
        BsonDocument indexesCommand,
        List<BsonDocument> indexes
    ) {
    }

    private record CollectionEntries(
        Path dataPath,
        ArchiveCollectionManifest dataManifest,
        Path optionsPath,
        Path indexesPath
    ) {
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

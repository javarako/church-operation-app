package com.church.operation.service;

import com.church.operation.dto.ArchiveCollectionManifest;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.RawBsonDocument;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MongoRestorePreflightPlanner {
    private static final int MAX_NAMESPACE_BYTES = 255;
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

    private final String databaseName;
    private final BsonStreamCodec bsonStreamCodec;

    MongoRestorePreflightPlanner(String databaseName) {
        if (databaseName == null || databaseName.isBlank()) {
            throw new IllegalArgumentException("MongoDB database name is required for restore preflight.");
        }
        this.databaseName = databaseName;
        this.bsonStreamCodec = new BsonStreamCodec();
    }

    RestorePlan preflight(ArchivePackageService.ValidatedArchive archive) throws IOException {
        Map<String, MutableCollectionEntries> grouped = new LinkedHashMap<>();
        for (ArchiveCollectionManifest manifestEntry : archive.manifest().collections()) {
            validateNamespaceName(manifestEntry.collection());
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
            namespaces.add(preflightNamespace(entry.getKey(), entry.getValue().complete()));
        }
        namespaces.sort(java.util.Comparator.comparing(NamespacePlan::name));
        orderedViews(namespaces);
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
            validateNamespaceName(command.getString("viewOn").getValue());
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
        for (BsonValue value : command.getArray("indexes")) {
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
        specifications.sort(java.util.Comparator.comparing(index -> index.getString("name").getValue()));
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

    private boolean isNumber(BsonValue value) {
        return value.isInt32() || value.isInt64() || value.isDouble() || value.isDecimal128();
    }

    private void requireTarget(BsonDocument command, String commandName, String collectionName) {
        if (!command.containsKey(commandName)
            || !command.get(commandName).isString()
            || !collectionName.equals(command.getString(commandName).getValue())) {
            throw new IllegalArgumentException("Backup sidecar command target is invalid.");
        }
    }

    void validateNamespaceName(String name) {
        if (name == null || name.isBlank() || name.indexOf('$') >= 0
            || name.startsWith("system.") || name.contains(".system.")
            || name.startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX)
            || name.startsWith(MongoDatabaseExportService.RESTORE_BACKUP_PREFIX)
            || name.codePoints().anyMatch(Character::isISOControl)
            || namespaceBytes(name) > MAX_NAMESPACE_BYTES) {
            throw new IllegalArgumentException("Backup collection name is invalid or reserved.");
        }
    }

    Set<String> viewDependencies(BsonDocument createCommand) {
        LinkedHashSet<String> dependencies = new LinkedHashSet<>();
        if (!createCommand.containsKey("viewOn") || !createCommand.get("viewOn").isString()) {
            throw new IllegalArgumentException("Backup view create command is invalid.");
        }
        dependencies.add(createCommand.getString("viewOn").getValue());
        if (createCommand.containsKey("pipeline")) {
            if (!createCommand.get("pipeline").isArray()) {
                throw new IllegalArgumentException("Backup view pipeline is invalid.");
            }
            transform(createCommand.getArray("pipeline"), dependencies, null);
        }
        return Set.copyOf(dependencies);
    }

    List<String> orderedViewNames(
        Map<String, BsonDocument> views,
        Set<String> archiveNamespaces
    ) {
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        views.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                Set<String> viewDependencies = viewDependencies(entry.getValue());
                for (String dependency : viewDependencies) {
                    validateNamespaceName(dependency);
                    if (!archiveNamespaces.contains(dependency)) {
                        throw new IllegalArgumentException("Backup view source is missing from the archive.");
                    }
                }
                dependencies.put(entry.getKey(), viewDependencies);
            });

        List<String> ordered = new ArrayList<>();
        Set<String> remaining = new LinkedHashSet<>(dependencies.keySet());
        while (!remaining.isEmpty()) {
            List<String> ready = remaining.stream()
                .filter(view -> dependencies.get(view).stream().noneMatch(remaining::contains))
                .sorted()
                .toList();
            if (ready.isEmpty()) {
                throw new IllegalArgumentException("Backup view dependencies contain a cycle.");
            }
            ordered.addAll(ready);
            remaining.removeAll(ready);
        }
        return List.copyOf(ordered);
    }

    BsonDocument stagingCreateCommand(
        BsonDocument source,
        String stagingName,
        Map<String, String> stagingNames
    ) {
        BsonDocument staged = source.clone();
        staged.put("create", new BsonString(stagingName));
        staged.put("viewOn", new BsonString(stagingDependency(
            staged.getString("viewOn").getValue(), stagingNames
        )));
        if (staged.containsKey("pipeline")) {
            staged.put("pipeline", transform(staged.getArray("pipeline"), new LinkedHashSet<>(), stagingNames));
        }
        return staged;
    }

    private BsonValue transform(
        BsonValue source,
        Set<String> dependencies,
        Map<String, String> stagingNames
    ) {
        if (source.isArray()) {
            BsonArray transformed = new BsonArray();
            source.asArray().forEach(value -> transformed.add(transform(value, dependencies, stagingNames)));
            return transformed;
        }
        if (!source.isDocument()) {
            return source;
        }

        BsonDocument transformed = new BsonDocument();
        source.asDocument().forEach((key, value) -> transformed.append(
            key,
            switch (key) {
                case "$lookup", "$graphLookup" -> transformFromOperator(value, dependencies, stagingNames);
                case "$unionWith" -> transformUnionWith(value, dependencies, stagingNames);
                default -> transform(value, dependencies, stagingNames);
            }
        ));
        return transformed;
    }

    private BsonValue transformFromOperator(
        BsonValue source,
        Set<String> dependencies,
        Map<String, String> stagingNames
    ) {
        if (!source.isDocument()) {
            throw new IllegalArgumentException("Backup view pipeline namespace dependency is invalid.");
        }
        BsonDocument transformed = new BsonDocument();
        source.asDocument().forEach((key, value) -> {
            if ("from".equals(key)) {
                transformed.append(key, transformedDependency(value, dependencies, stagingNames));
            } else {
                transformed.append(key, transform(value, dependencies, stagingNames));
            }
        });
        return transformed;
    }

    private BsonValue transformUnionWith(
        BsonValue source,
        Set<String> dependencies,
        Map<String, String> stagingNames
    ) {
        if (source.isString()) {
            return transformedDependency(source, dependencies, stagingNames);
        }
        if (!source.isDocument()) {
            throw new IllegalArgumentException("Backup view pipeline namespace dependency is invalid.");
        }
        BsonDocument transformed = new BsonDocument();
        source.asDocument().forEach((key, value) -> {
            if ("coll".equals(key)) {
                transformed.append(key, transformedDependency(value, dependencies, stagingNames));
            } else {
                transformed.append(key, transform(value, dependencies, stagingNames));
            }
        });
        return transformed;
    }

    private BsonString transformedDependency(
        BsonValue source,
        Set<String> dependencies,
        Map<String, String> stagingNames
    ) {
        if (!source.isString() || source.asString().getValue().isBlank()) {
            throw new IllegalArgumentException("Backup view pipeline namespace dependency is invalid.");
        }
        String dependency = source.asString().getValue();
        dependencies.add(dependency);
        return new BsonString(stagingNames == null ? dependency : stagingDependency(dependency, stagingNames));
    }

    private String stagingDependency(String dependency, Map<String, String> stagingNames) {
        String stagingName = stagingNames.get(dependency);
        if (stagingName == null) {
            throw new IllegalArgumentException("Backup view source is missing from the archive.");
        }
        return stagingName;
    }

    List<NamespacePlan> orderedViews(List<NamespacePlan> namespaces) {
        Map<String, BsonDocument> views = new LinkedHashMap<>();
        Set<String> available = new LinkedHashSet<>();
        Map<String, NamespacePlan> byName = new LinkedHashMap<>();
        for (NamespacePlan namespace : namespaces) {
            available.add(namespace.name());
            byName.put(namespace.name(), namespace);
            if (namespace.type() == NamespaceType.VIEW) {
                views.put(namespace.name(), namespace.createCommand());
            }
        }
        return orderedViewNames(views, available).stream().map(byName::get).toList();
    }

    private BsonDocument mutableCopy(BsonDocument source) {
        BsonDocument copy = new BsonDocument();
        source.forEach(copy::append);
        return copy;
    }

    private int namespaceBytes(String name) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(databaseName + "." + name));
            return encoded.remaining();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Backup collection name is invalid or reserved.", exception);
        }
    }

    enum NamespaceType {
        COLLECTION("collection"),
        VIEW("view"),
        TIMESERIES("timeseries");

        final String catalogType;

        NamespaceType(String catalogType) {
            this.catalogType = catalogType;
        }

        static NamespaceType fromCatalog(String type) {
            return switch (type) {
                case "collection" -> COLLECTION;
                case "view" -> VIEW;
                case "timeseries" -> TIMESERIES;
                default -> throw new IllegalStateException("MongoDB returned an unsupported namespace type: " + type);
            };
        }
    }

    record RestorePlan(List<NamespacePlan> namespaces) {
    }

    record NamespacePlan(
        String name,
        NamespaceType type,
        CollectionEntries entries,
        BsonDocument createCommand,
        BsonDocument expectedOptions,
        BsonDocument indexesCommand,
        List<BsonDocument> indexes
    ) {
    }

    record CollectionEntries(
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

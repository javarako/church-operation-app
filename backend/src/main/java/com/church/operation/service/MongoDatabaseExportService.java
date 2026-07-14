package com.church.operation.service;

import com.church.operation.config.DataManagementProperties;
import com.church.operation.dto.ArchiveCollectionManifest;
import com.church.operation.dto.ArchiveManifest;
import com.church.operation.util.ArchiveType;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.RawBsonDocument;
import org.bson.codecs.BsonDocumentCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Stores each collection under collections/&lt;UTF-8-name-as-hex&gt;/{data,options,indexes}.bson.
 */
@Service
public class MongoDatabaseExportService {
    public static final String RESTORE_STAGING_PREFIX = "__church_restore_staging__";
    public static final String RESTORE_BACKUP_PREFIX = "__church_restore_backup__";

    private static final String COLLECTION_TYPE = "collection";
    private static final String VIEW_TYPE = "view";
    private static final String TIMESERIES_TYPE = "timeseries";
    private static final String DATA_SUFFIX = "/data.bson";
    private static final String OPTIONS_SUFFIX = "/options.bson";
    private static final String INDEXES_SUFFIX = "/indexes.bson";
    private static final BsonDocumentCodec BSON_DOCUMENT_CODEC = new BsonDocumentCodec();

    private final MongoDatabase database;
    private final ArchivePackageService archivePackageService;
    private final DataManagementProperties properties;
    private final BsonStreamCodec bsonStreamCodec;
    private final DirectoryCleaner directoryCleaner;

    @Autowired
    public MongoDatabaseExportService(
        MongoTemplate mongoTemplate,
        ArchivePackageService archivePackageService,
        DataManagementProperties properties
    ) {
        this(mongoTemplate.getDb(), archivePackageService, properties);
    }

    MongoDatabaseExportService(
        MongoDatabase database,
        ArchivePackageService archivePackageService,
        DataManagementProperties properties
    ) {
        this(database, archivePackageService, properties, MongoDatabaseExportService::deleteDirectory);
    }

    MongoDatabaseExportService(
        MongoDatabase database,
        ArchivePackageService archivePackageService,
        DataManagementProperties properties,
        DirectoryCleaner directoryCleaner
    ) {
        this.database = database;
        this.archivePackageService = archivePackageService;
        this.properties = properties;
        this.bsonStreamCodec = new BsonStreamCodec();
        this.directoryCleaner = directoryCleaner;
    }

    public ArchiveManifest exportFull(Path output, char[] password) throws IOException {
        Files.createDirectories(properties.tempDirectory());
        Path workingDirectory = Files.createTempDirectory(properties.tempDirectory(), "mongo-export-");
        Throwable failure = null;
        try {
            List<NamespaceCatalog> namespaces = discoverNamespaces();

            List<ArchiveCollectionManifest> manifestEntries = new ArrayList<>();
            Map<String, Path> packageEntries = new HashMap<>();
            for (NamespaceCatalog namespace : namespaces) {
                addCollectionEntries(
                    namespace, workingDirectory, manifestEntries, packageEntries
                );
            }

            ArchiveManifest manifest = new ArchiveManifest(
                ArchivePackageService.FORMAT_VERSION,
                ArchiveType.FULL_BACKUP,
                List.copyOf(manifestEntries)
            );
            archivePackageService.write(output, password, manifest, Map.copyOf(packageEntries));
            try (ArchivePackageService.ValidatedArchive validated = archivePackageService.validate(
                output, password, ArchiveType.FULL_BACKUP
            )) {
                return validated.manifest();
            }
        } catch (IOException | RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            try {
                directoryCleaner.delete(workingDirectory);
            } catch (IOException cleanupFailure) {
                if (failure != null) {
                    failure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private List<NamespaceCatalog> discoverNamespaces() {
        List<RawBsonDocument> catalogEntries = new ArrayList<>();
        database.listCollections(RawBsonDocument.class).into(catalogEntries);
        return catalogEntries.stream()
            .map(this::namespaceCatalog)
            .filter(namespace -> !isMongoOwnedCompanion(namespace.name()))
            .filter(namespace -> !namespace.name().startsWith(RESTORE_STAGING_PREFIX))
            .filter(namespace -> !namespace.name().startsWith(RESTORE_BACKUP_PREFIX))
            .sorted(Comparator.comparing(NamespaceCatalog::name))
            .toList();
    }

    private NamespaceCatalog namespaceCatalog(RawBsonDocument catalog) {
        if (!catalog.containsKey("name") || !catalog.get("name").isString()
            || !catalog.containsKey("type") || !catalog.get("type").isString()
            || !catalog.containsKey("options") || !catalog.get("options").isDocument()) {
            throw new IllegalStateException("MongoDB returned an invalid collection catalog entry.");
        }
        String name = catalog.getString("name").getValue();
        String type = catalog.getString("type").getValue();
        if (!COLLECTION_TYPE.equals(type) && !VIEW_TYPE.equals(type) && !TIMESERIES_TYPE.equals(type)) {
            throw new IllegalStateException("MongoDB returned an unsupported namespace type: " + type);
        }
        return new NamespaceCatalog(name, type, catalog.getDocument("options").clone());
    }

    private boolean isMongoOwnedCompanion(String name) {
        return "system.views".equals(name) || name.startsWith("system.buckets.");
    }

    private void addCollectionEntries(
        NamespaceCatalog namespace,
        Path workingDirectory,
        List<ArchiveCollectionManifest> manifestEntries,
        Map<String, Path> packageEntries
    ) throws IOException {
        String collectionName = namespace.name();
        String dataEntry = dataEntryName(collectionName);
        String optionsEntry = optionsEntryName(collectionName);
        String indexesEntry = indexesEntryName(collectionName);
        Path dataPath = workingDirectory.resolve(dataEntry);
        Path optionsPath = workingDirectory.resolve(optionsEntry);
        Path indexesPath = workingDirectory.resolve(indexesEntry);
        Files.createDirectories(dataPath.getParent());

        writeCollectionData(namespace, dataPath);
        writeCreateCommand(namespace, optionsPath);
        writeCreateIndexesCommand(namespace, indexesPath);

        addPackageEntry(collectionName, dataEntry, dataPath, manifestEntries, packageEntries);
        addPackageEntry(collectionName, optionsEntry, optionsPath, manifestEntries, packageEntries);
        addPackageEntry(collectionName, indexesEntry, indexesPath, manifestEntries, packageEntries);
    }

    private void addPackageEntry(
        String collectionName,
        String entryName,
        Path path,
        List<ArchiveCollectionManifest> manifestEntries,
        Map<String, Path> packageEntries
    ) {
        manifestEntries.add(new ArchiveCollectionManifest(collectionName, entryName));
        packageEntries.put(entryName, path);
    }

    private void writeCollectionData(NamespaceCatalog namespace, Path output) throws IOException {
        if (VIEW_TYPE.equals(namespace.type())) {
            Files.createFile(output);
            return;
        }
        try (
            OutputStream stream = Files.newOutputStream(output);
            MongoCursor<RawBsonDocument> cursor = database
                .getCollection(namespace.name(), RawBsonDocument.class)
                .find()
                .iterator()
        ) {
            while (cursor.hasNext()) {
                bsonStreamCodec.writeRaw(cursor.next(), stream);
            }
        }
    }

    private void writeCreateCommand(NamespaceCatalog namespace, Path output) throws IOException {
        BsonDocument command = new BsonDocument("create", new BsonString(namespace.name()));
        namespace.options().forEach(command::append);
        writeCommand(command, output);
    }

    private void writeCreateIndexesCommand(NamespaceCatalog namespace, Path output) throws IOException {
        if (VIEW_TYPE.equals(namespace.type())) {
            Files.createFile(output);
            return;
        }
        BsonArray indexes = new BsonArray();
        database.getCollection(namespace.name(), RawBsonDocument.class)
            .listIndexes(RawBsonDocument.class)
            .forEach(index -> {
                if (!"_id_".equals(index.getString("name").getValue())) {
                    BsonDocument specification = mutableCopy(index);
                    specification.remove("ns");
                    addRequiredTextCollation(specification, namespace.options());
                    indexes.add(specification);
                }
            });
        if (indexes.isEmpty()) {
            Files.createFile(output);
            return;
        }
        writeCommand(new BsonDocument("createIndexes", new BsonString(namespace.name()))
            .append("indexes", indexes), output);
    }

    private void addRequiredTextCollation(BsonDocument index, BsonDocument collectionOptions) {
        if (isTextIndex(index) && !index.containsKey("collation") && collectionOptions.containsKey("collation")) {
            index.append("collation", new BsonDocument("locale", new BsonString("simple")));
        }
    }

    private boolean isTextIndex(BsonDocument index) {
        return index.getDocument("key").values().stream()
            .anyMatch(value -> value.isString() && "text".equals(value.asString().getValue()));
    }

    private void writeCommand(BsonDocument command, Path output) throws IOException {
        try (OutputStream stream = Files.newOutputStream(output)) {
            bsonStreamCodec.writeRaw(new RawBsonDocument(command, BSON_DOCUMENT_CODEC), stream);
        }
    }

    private BsonDocument mutableCopy(BsonDocument source) {
        BsonDocument copy = new BsonDocument();
        source.forEach(copy::append);
        return copy;
    }

    static String dataEntryName(String collectionName) {
        return collectionEntryPrefix(collectionName) + DATA_SUFFIX;
    }

    static String optionsEntryName(String collectionName) {
        return collectionEntryPrefix(collectionName) + OPTIONS_SUFFIX;
    }

    static String indexesEntryName(String collectionName) {
        return collectionEntryPrefix(collectionName) + INDEXES_SUFFIX;
    }

    private static String collectionEntryPrefix(String collectionName) {
        return "collections/" + HexFormat.of().formatHex(collectionName.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        List<Path> paths;
        try (var stream = Files.walk(directory)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }
        IOException failure = null;
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private record NamespaceCatalog(String name, String type, BsonDocument options) {
    }

    @FunctionalInterface
    interface DirectoryCleaner {
        void delete(Path directory) throws IOException;
    }
}

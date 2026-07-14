package com.church.operation.service;

import com.church.operation.dto.ArchiveManifest;
import com.church.operation.service.MongoRestoreCatalogService.NamespaceCatalog;
import com.church.operation.service.MongoRestorePreflightPlanner.NamespacePlan;
import com.church.operation.service.MongoRestorePreflightPlanner.NamespaceType;
import com.church.operation.service.MongoRestorePreflightPlanner.RestorePlan;
import com.church.operation.util.ArchiveType;
import com.mongodb.client.MongoDatabase;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MongoDatabaseImportService {
    private static final int INSERT_BATCH_SIZE = 500;
    private static final int INSERT_BATCH_BYTES = 16 * 1024 * 1024;

    private final MongoDatabase database;
    private final ArchivePackageService archivePackageService;
    private final BsonStreamCodec bsonStreamCodec;
    private final MongoRestorePreflightPlanner preflightPlanner;
    private final MongoRestoreCatalogService catalogService;
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
        this.preflightPlanner = new MongoRestorePreflightPlanner(database.getName());
        this.catalogService = new MongoRestoreCatalogService(database);
        this.cutoverHook = cutoverHook;
    }

    public ValidatedArchive validateFull(Path archive, char[] password) throws IOException {
        ArchivePackageService.ValidatedArchive packageArchive = archivePackageService.validate(
            archive, password, ArchiveType.FULL_BACKUP
        );
        try {
            RestorePlan restorePlan = preflightPlanner.preflight(packageArchive);
            return new ValidatedArchive(this, packageArchive, restorePlan);
        } catch (IOException | RuntimeException | Error exception) {
            closeAfterFailure(packageArchive, exception);
            throw exception;
        }
    }

    public RestoreVerification replaceAll(ValidatedArchive archive) throws IOException {
        RestorePlan restorePlan = archive.requirePlan(this);
        catalogService.ensureNoRecoveryNamespaces();

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
        stagingOrder.addAll(preflightPlanner.orderedViews(restorePlan.namespaces()));

        for (NamespacePlan namespace : stagingOrder) {
            stageNamespace(namespace, stagingNames.get(namespace.name()), stagingNames);
        }
    }

    private void stageNamespace(
        NamespacePlan namespace,
        String stagingName,
        Map<String, String> stagingNames
    ) throws IOException {
        BsonDocument createCommand;
        if (namespace.type() == NamespaceType.VIEW) {
            createCommand = preflightPlanner.stagingCreateCommand(
                namespace.createCommand(), stagingName, stagingNames
            );
        } else {
            createCommand = namespace.createCommand().clone();
            createCommand.put("create", new BsonString(stagingName));
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

        RawBsonDocument catalog = catalogService.namespaceCatalog(actualName);
        if (catalog == null
            || !namespace.type().catalogType.equals(catalog.getString("type").getValue())
            || !catalog.getDocument("options").equals(expectedOptions)) {
            throw new IllegalStateException("Restored namespace verification failed for type or options.");
        }

        if (namespace.type() == NamespaceType.VIEW) {
            return;
        }
        List<BsonDocument> actualIndexes = catalogService.customIndexSignatures(actualName);
        List<BsonDocument> expectedIndexes = namespace.indexes().stream()
            .map(catalogService::catalogIndexSignature)
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
        List<NamespaceCatalog> liveNamespaces = catalogService.liveNamespaces();
        Map<String, String> backupNames = catalogService.backupNames(liveNamespaces, operationId);
        List<Map.Entry<String, String>> movedBackups = new ArrayList<>();
        List<Map.Entry<String, String>> promotions = new ArrayList<>();
        try {
            for (NamespaceCatalog live : liveNamespaces) {
                if (live.type() == NamespaceType.COLLECTION) {
                    String backup = backupNames.get(live.name());
                    catalogService.rename(live.name(), backup);
                    movedBackups.add(Map.entry(live.name(), backup));
                }
            }
            catalogService.dropNonRenamableLive(liveNamespaces, NamespaceType.VIEW);
            catalogService.dropNonRenamableLive(liveNamespaces, NamespaceType.TIMESERIES);

            boolean hookInvoked = false;
            for (NamespacePlan namespace : restorePlan.namespaces()) {
                if (namespace.type() == NamespaceType.COLLECTION) {
                    String staging = stagingNames.get(namespace.name());
                    catalogService.rename(staging, namespace.name());
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
            for (NamespacePlan namespace : preflightPlanner.orderedViews(restorePlan.namespaces())) {
                replayNamespace(namespace);
            }
            verifyLiveRestore(restorePlan);
        } catch (IOException | RuntimeException | Error exception) {
            catalogService.rollbackOrdinaryCollections(promotions, movedBackups, exception);
            throw catalogService.cutoverFailure(
                "Database restore cutover failed; maintenance recovery is required.",
                exception,
                stagingNames.values(),
                backupNames.values()
            );
        }

        try {
            catalogService.dropKnownNamespaces(backupNames.values());
            catalogService.dropKnownNamespaces(stagingNames.values());
        } catch (RuntimeException | Error cleanupFailure) {
            throw catalogService.cutoverFailure(
                "Database restore completed but recovery namespace cleanup failed; maintenance is required.",
                cleanupFailure,
                stagingNames.values(),
                backupNames.values()
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
        Set<String> actualNames = catalogService.liveNamespaces().stream()
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

    private void cleanupPreCutoverStaging(Iterable<String> stagingNames, Throwable primary) {
        try {
            catalogService.dropKnownNamespaces(stagingNames);
        } catch (RuntimeException | Error cleanupFailure) {
            MongoRestoreCatalogService.addSuppressedSafely(primary, cleanupFailure);
        }
    }

    private void closeAfterFailure(AutoCloseable closeable, Throwable primary) {
        try {
            closeable.close();
        } catch (Exception cleanupFailure) {
            MongoRestoreCatalogService.addSuppressedSafely(primary, cleanupFailure);
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
}

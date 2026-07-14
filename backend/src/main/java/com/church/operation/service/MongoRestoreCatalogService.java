package com.church.operation.service;

import com.church.operation.service.MongoRestorePreflightPlanner.NamespaceType;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.RenameCollectionOptions;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.RawBsonDocument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MongoRestoreCatalogService {
    private final MongoDatabase database;

    MongoRestoreCatalogService(MongoDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("MongoDB catalog dependency is required.");
        }
        this.database = database;
    }

    void ensureNoRecoveryNamespaces() throws RestoreCutoverException {
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

    List<NamespaceCatalog> liveNamespaces() {
        return allNamespaces().stream()
            .filter(namespace -> !namespace.name().startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX))
            .filter(namespace -> !namespace.name().startsWith(MongoDatabaseExportService.RESTORE_BACKUP_PREFIX))
            .toList();
    }

    List<NamespaceCatalog> allNamespaces() {
        List<RawBsonDocument> catalog = new ArrayList<>();
        database.listCollections(RawBsonDocument.class).into(catalog);
        return catalog.stream()
            .filter(entry -> !entry.getString("name").getValue().startsWith("system."))
            .map(entry -> new NamespaceCatalog(
                entry.getString("name").getValue(),
                NamespaceType.fromCatalog(entry.getString("type").getValue())
            ))
            .sorted(java.util.Comparator.comparing(NamespaceCatalog::name))
            .toList();
    }

    RawBsonDocument namespaceCatalog(String name) {
        return database.listCollections(RawBsonDocument.class)
            .filter(new BsonDocument("name", new BsonString(name)))
            .first();
    }

    Map<String, String> backupNames(List<NamespaceCatalog> liveNamespaces, String operationId) {
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

    void rename(String source, String target) {
        database.getCollection(source).renameCollection(
            new MongoNamespace(database.getName(), target),
            new RenameCollectionOptions().dropTarget(false)
        );
    }

    void dropNonRenamableLive(List<NamespaceCatalog> liveNamespaces, NamespaceType type) {
        liveNamespaces.stream()
            .filter(namespace -> namespace.type() == type)
            .forEach(namespace -> database.getCollection(namespace.name()).drop());
    }

    void rollbackOrdinaryCollections(
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
                    rename(promotion.getKey(), promotion.getValue());
                }
            } catch (RuntimeException | Error rollbackFailure) {
                addSuppressedSafely(primary, rollbackFailure);
            }
        }

        List<Map.Entry<String, String>> reverseBackups = new ArrayList<>(movedBackups);
        Collections.reverse(reverseBackups);
        for (Map.Entry<String, String> backup : reverseBackups) {
            try {
                if (namespaceCatalog(backup.getValue()) != null
                    && namespaceCatalog(backup.getKey()) == null) {
                    rename(backup.getValue(), backup.getKey());
                }
            } catch (RuntimeException | Error rollbackFailure) {
                addSuppressedSafely(primary, rollbackFailure);
            }
        }
    }

    void dropKnownNamespaces(Iterable<String> names) {
        Set<String> requested = new LinkedHashSet<>();
        names.forEach(requested::add);
        allNamespaces().stream()
            .filter(namespace -> requested.contains(namespace.name()))
            .sorted(java.util.Comparator.comparingInt(namespace -> dropOrder(namespace.type())))
            .forEach(namespace -> database.getCollection(namespace.name()).drop());
    }

    List<BsonDocument> customIndexSignatures(String collectionName) {
        List<BsonDocument> signatures = new ArrayList<>();
        database.getCollection(collectionName, RawBsonDocument.class)
            .listIndexes(RawBsonDocument.class)
            .forEach(index -> {
                if (!"_id_".equals(index.getString("name").getValue())) {
                    signatures.add(catalogIndexSignature(index));
                }
            });
        signatures.sort(java.util.Comparator.comparing(index -> index.getString("name").getValue()));
        return signatures;
    }

    BsonDocument catalogIndexSignature(BsonDocument source) {
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

    RestoreCutoverException cutoverFailure(
        String message,
        Throwable cause,
        Collection<String> knownStaging,
        Collection<String> knownBackups
    ) {
        Set<String> staging = new LinkedHashSet<>(knownStaging);
        Set<String> backups = new LinkedHashSet<>(knownBackups);
        Throwable evidenceFailure = null;
        try {
            List<RawBsonDocument> catalog = new ArrayList<>();
            database.listCollections(RawBsonDocument.class).into(catalog);
            for (RawBsonDocument namespace : catalog) {
                String name = namespace.getString("name").getValue();
                if (name.startsWith(MongoDatabaseExportService.RESTORE_STAGING_PREFIX)) {
                    staging.add(name);
                } else if (name.startsWith(MongoDatabaseExportService.RESTORE_BACKUP_PREFIX)) {
                    backups.add(name);
                }
            }
        } catch (RuntimeException | Error exception) {
            evidenceFailure = exception;
            addSuppressedSafely(cause, exception);
        }

        RestoreCutoverException failure = new RestoreCutoverException(
            message,
            cause,
            staging.stream().sorted().toList(),
            backups.stream().sorted().toList()
        );
        if (cause == null && evidenceFailure != null) {
            addSuppressedSafely(failure, evidenceFailure);
        }
        return failure;
    }

    static void addSuppressedSafely(Throwable primary, Throwable secondary) {
        if (primary != null && secondary != null && primary != secondary
            && Arrays.stream(primary.getSuppressed()).noneMatch(existing -> existing == secondary)) {
            primary.addSuppressed(secondary);
        }
    }

    private int dropOrder(NamespaceType type) {
        return switch (type) {
            case VIEW -> 0;
            case TIMESERIES -> 1;
            case COLLECTION -> 2;
        };
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

    record NamespaceCatalog(String name, NamespaceType type) {
    }
}

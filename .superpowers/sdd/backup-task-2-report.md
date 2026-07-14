# Backup/Restore Task 2 Report

## Status

DONE_WITH_CONCERNS

## RED Evidence

Command:

```text
cd backend && mvn -Dtest=MongoDatabaseRoundTripIntegrationTest test
```

The first valid test compile failed as intended because Task 2 did not exist:

```text
cannot find symbol: class DataManagementProperties
cannot find symbol: class MongoDatabaseExportService
cannot find symbol: class MongoDatabaseImportService
BUILD FAILURE
```

The initial test harness also exposed the Testcontainers 2 API change from a container-owned client to `getConnectionString()`. After correcting the harness, production code compiled and the unsandboxed Docker test exercised MongoDB 7.0.17. Subsequent RED runs caught an immutable `RawBsonDocument` mutation and MongoDB's omitted simple-collation requirement for replaying text indexes on collections with non-simple default collation.

## GREEN Evidence

Focused integration test:

```text
mvn -Dtest=MongoDatabaseRoundTripIntegrationTest test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Task 1 contract regression tests:

```text
mvn -Dtest=BsonStreamCodecTest,ArchivePackageServiceTest test
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Complete backend suite:

```text
mvn test
Tests run: 135, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Dependency resolution:

```text
org.testcontainers:testcontainers-mongodb:2.0.2:test
org.testcontainers:testcontainers-junit-jupiter:2.0.2:test
```

## Files

Created:

- `.superpowers/sdd/backup-task-2-report.md`
- `backend/src/main/java/com/church/operation/config/DataManagementProperties.java`
- `backend/src/main/java/com/church/operation/service/MongoDatabaseExportService.java`
- `backend/src/main/java/com/church/operation/service/MongoDatabaseImportService.java`
- `backend/src/main/java/com/church/operation/service/RestoreVerification.java`
- `backend/src/test/java/com/church/operation/service/MongoDatabaseRoundTripIntegrationTest.java`

Modified:

- `backend/pom.xml`
- `backend/src/main/java/com/church/operation/ChurchOperationApplication.java`
- `backend/src/main/java/com/church/operation/service/ArchivePackageService.java`
- `backend/src/main/java/com/church/operation/service/BsonStreamCodec.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/church/operation/service/ArchivePackageServiceTest.java`

No unrelated receipt, UI, generated, target, progress, Docker Compose, or temporary files are included.

## Design Decisions

- Format version 1 remains unchanged. A collection may now own multiple uniquely named manifest entries.
- Sidecars use `collections/<UTF-8 collection name as lowercase hex>/data.bson`, `options.bson`, and `indexes.bson`.
- Data is exported and imported as exact `RawBsonDocument` bytes. Imports use batches of at most 500 documents.
- The options sidecar contains one raw `create` command. The indexes sidecar contains zero or one raw `createIndexes` command.
- Index export removes only server-owned `v` and `ns`. All other catalog fields remain BSON, including key, name, unique, sparse, TTL, partial filter, collation, hidden, text, geo, and wildcard options.
- MongoDB omits the required explicit simple collation from catalogued text indexes when the collection has a non-simple default. Export adds that replay requirement without removing any catalogued field; verification normalizes this server omission.
- Export discovers and sorts `listCollectionNames()` at runtime, excluding only `__church_restore_staging__` names.
- Restore creates and verifies all collections under unique reserved staging names, then drops current non-staging collections and renames staged collections into place.
- `ArchivePackageService` is an injectable service whose entry limit comes from `church.data-management.max-upload-size`.
- `DataManagementProperties` is registered in the application's explicit `@EnableConfigurationProperties` list.

## Self-Review

- Confirmed ordinary, audit/future-style, GridFS files, and GridFS chunks collections round-trip without hard-coded catalog names.
- Confirmed raw documents and GridFS download bytes are identical after restore.
- Confirmed collection validator, validation level/action, and default collation are identical after restore.
- Confirmed unique, sparse, TTL, partial-filter, collation, hidden, text, 2dsphere, and wildcard index signatures are identical after restore.
- Confirmed collections created after backup are removed and reserved staging collections are neither exported nor left behind.
- Confirmed archive sidecar checksums/counts still validate and Task 1 permits repeated logical collection names only through unique entries.
- Confirmed `git diff --check` is clean and the complete backend suite passes.

## Commit

- Base: `ef50c2a`
- Message: `feat: round-trip complete MongoDB backups`
- Final hash is supplied in the task handoff because a commit cannot embed its own final hash.

## Concerns

- The brief names `org.testcontainers:junit-jupiter`, but Spring Boot 4.0.0's imported Testcontainers 2.0.2 BOM does not manage that coordinate. The managed 2.0.2 module is `org.testcontainers:testcontainers-junit-jupiter`, so that artifact is used with no explicit version. The MongoDB module matches the brief exactly.

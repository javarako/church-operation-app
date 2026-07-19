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

## Review Remediation - 2026-07-13

### Status

DONE_WITH_CONCERNS

### RED Evidence

Initial archive, batch, and integration boundary run:

```text
mvn -Dtest=ArchivePackageServiceTest,BsonStreamCodecTest,MongoDatabaseRoundTripIntegrationTest test
Tests run: 37, Failures: 1, Errors: 4, Skipped: 0
BUILD FAILURE
```

Relevant failures before implementation:

```text
BsonStreamCodec.RawBatchReader cannot be resolved to a type
The method rawBatchReader(ByteArrayInputStream, int, int) is undefined
ArchivePackageServiceTest.rejectsUploadedArchiveLargerThanConfiguredBoundBeforeExtraction:
Expecting code to raise a throwable.
```

The same run also confirmed validation extraction did not use the configured data-management directory. Its Docker-backed class was blocked in the sandbox; all Docker runs below were repeated with approved Docker access.

Index-version semantic preflight RED:

```text
mvn -Dtest=MongoDatabaseRoundTripIntegrationTest#rejectsUnsupportedArchivedIndexVersionDuringValidationWithoutDatabaseMutation test
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
Expecting code to raise a throwable.
BUILD FAILURE
```

Primary-exception preservation RED:

```text
mvn -Dtest=MongoDatabaseExportServiceTest test
COMPILATION ERROR
no suitable constructor found for MongoDatabaseExportService(..., DirectoryCleaner)
BUILD FAILURE
```

These tests were added before their production APIs and behavior. The integration additions also covered view/time-series catalog replay, malformed sidecars, and forced cutover failure before the importer implementation was replaced.

### GREEN Evidence

Focused Task 2 plus archive-contract suite:

```text
mvn -Dtest=MongoDatabaseRoundTripIntegrationTest,ArchivePackageServiceTest,BsonStreamCodecTest,MongoDatabaseExportServiceTest test
Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Exact Task 1 regressions:

```text
mvn -Dtest=BsonStreamCodecTest,ArchivePackageServiceTest test
Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Complete backend suite:

```text
mvn test
Tests run: 144, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Diff hygiene:

```text
git diff --check
(no output)
```

### Review Fixes

- Export now discovers full catalog records through `listCollections`, preserves ordinary collection, view, and time-series options, and omits only MongoDB-owned `system.views` and `system.buckets.*` companions plus reserved recovery namespaces.
- Views export empty data/index sidecars and replay from validated `viewOn`/pipeline definitions. Time-series collections stage with their options, data, and indexes, then use an explicit live replay path because MongoDB does not support renaming them.
- `validateFull` now returns an importer-owned `ValidatedArchive` with a private constructor after canonical sidecar relationships, counts, BSON, names, command shapes/targets, namespace types/options, view dependencies, and index specs have all passed semantic preflight. The package-level archive handle also has no public constructor.
- Every namespace is staged and verified before live mutation. Ordinary live collections move to operation-specific backup names, promoted ordinary collections roll back to staging on failure, and originals roll back from backup names where possible.
- Cutover failure raises `RestoreCutoverException` with `maintenanceRequired() == true` and retained staging/backup namespace evidence. Existing recovery namespaces block a new restore instead of being silently deleted.
- Upload validation enforces the compressed archive size, each entry size, declared aggregate extraction size, and actual cumulative bytes written. Export and validation extraction both use `church.data-management.temp-directory`.
- Index `v` is retained in sidecars, signatures, staging commands, and live replay. Only namespace-owned `ns` is removed. MongoDB 7 integration command capture confirms replayed index specs contain `v`.
- Import batches are bounded at 500 documents and 16 MiB of BSON, with a stateful reader that retains the next document when the byte boundary is reached.
- Export and validation cleanup failures are attached as suppressed exceptions. Cutover failures retain recovery evidence instead of running destructive staging cleanup.

### Self-Review

- Confirmed ordinary collection raw BSON, options, custom indexes, GridFS metadata/chunks, and GridFS bytes still round-trip exactly.
- Confirmed time-series documents round-trip semantically; raw field layout is not asserted because MongoDB rebuckets measurements during replay.
- Confirmed view/time-series catalog types and options are identical after restore and MongoDB-owned companion namespaces are not archived independently.
- Confirmed malformed options sidecars and unsupported index versions fail in `validateFull` before any database mutation.
- Confirmed a forced cutover failure restores the original ordinary collection, reports the original exception as its cause, and leaves staging namespaces present.
- Confirmed archive upload, per-entry, cumulative extraction, configured temp-directory, controlled-construction, BSON batch, and suppressed-cleanup tests pass.
- Confirmed only Task 2 and minimal Task 1 archive-contract source/tests plus this report are selected for commit. Receipt, UI, generated, target, progress, Docker Compose, and temporary files remain unstaged.

### Commit

- Message: `fix: harden complete database restore`
- Final hash is supplied in the task handoff because a commit cannot include its own hash.

### Concerns

- Standalone MongoDB cannot make this multi-namespace cutover atomic. Ordinary collections have backup/rollback protection; views and time-series namespaces use a validated replay path and retain staging/recovery evidence on failure. Task 3 must take the mandatory safety backup first and map `maintenanceRequired()` failures to `FAILED_MAINTENANCE`.
- `operationExpiry` remains intentionally unused until Task 3 and is not a Task 2 defect.

## Remaining Findings Remediation - 2026-07-13

### Status

DONE

### RED Evidence

Generated-manifest aggregate accounting:

```text
mvn -Dtest=ArchivePackageServiceTest#writeIncludesGeneratedManifestInAggregateSizeLimit test
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
Expecting code to raise a throwable.
BUILD FAILURE
```

System namespace export symmetry:

```text
mvn -Dtest=MongoDatabaseRoundTripIntegrationTest#excludesEveryMongoSystemNamespaceFromRestorableExports test
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
system.profile was present in all three exported sidecars.
BUILD FAILURE
```

Preflight planner extraction and recursive dependency contracts:

```text
mvn -Dtest=MongoRestorePreflightPlannerTest test
cannot find symbol: class MongoRestorePreflightPlanner
BUILD FAILURE
```

After the namespace validator existed, the recursive view contract remained RED because `viewDependencies`, `stagingCreateCommand`, and `orderedViewNames` did not exist. The catalog service contract likewise failed to compile before `MongoRestoreCatalogService` was created.

Primary cutover failure preservation:

```text
mvn -Dtest=MongoDatabaseRoundTripIntegrationTest#catalogFailureCannotMaskInjectedCutoverFailureOrRecoveryEvidence test
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
Expected RestoreCutoverException but got IllegalArgumentException: Self-suppression not permitted
BUILD FAILURE
```

The failure showed the injected cutover exception was first masked by catalog evidence collection, then the same catalog throwable was suppressed onto itself during fallback cleanup.

### GREEN Evidence

Focused restore/archive suite:

```text
mvn -Dtest=MongoDatabaseRoundTripIntegrationTest,ArchivePackageServiceTest,BsonStreamCodecTest,MongoDatabaseExportServiceTest,MongoDatabaseImportServiceTest,MongoRestorePreflightPlannerTest,MongoRestoreCatalogServiceTest test
Tests run: 71, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Complete backend suite:

```text
mvn test
Tests run: 173, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Files

Created:

- `backend/src/main/java/com/church/operation/service/MongoRestorePreflightPlanner.java`
- `backend/src/main/java/com/church/operation/service/MongoRestoreCatalogService.java`
- `backend/src/test/java/com/church/operation/service/MongoRestorePreflightPlannerTest.java`
- `backend/src/test/java/com/church/operation/service/MongoRestoreCatalogServiceTest.java`
- `backend/src/test/java/com/church/operation/service/MongoDatabaseImportServiceTest.java`

Modified:

- `.superpowers/sdd/backup-task-2-report.md`
- `backend/src/main/java/com/church/operation/service/ArchivePackageService.java`
- `backend/src/main/java/com/church/operation/service/MongoDatabaseExportService.java`
- `backend/src/main/java/com/church/operation/service/MongoDatabaseImportService.java`
- `backend/src/test/java/com/church/operation/service/ArchivePackageServiceTest.java`
- `backend/src/test/java/com/church/operation/service/MongoDatabaseRoundTripIntegrationTest.java`

Receipt, UI, generated, target, progress, Docker Compose, and temporary files remain outside this change.

### Decomposition Rationale

- `MongoDatabaseImportService` fell from 1,020 to 371 lines and now coordinates validation ownership, staging data transfer, verification, and cutover flow.
- `MongoRestorePreflightPlanner` owns archive sidecar parsing, semantic option/index validation, complete namespace-name validation, restore plan records, recursive view dependency analysis, topological ordering, cycle/missing-source checks, and staging command rewrites. It has no live database mutation capability.
- `MongoRestoreCatalogService` owns live/recovery catalog discovery, backup naming, rename/drop ordering, rollback, catalog/index signatures, and failure evidence. Its `cutoverFailure` starts with known operation namespaces, catches evidence failures, safely suppresses them onto the primary, and always returns a maintenance-required `RestoreCutoverException`.
- These boundaries are exercised directly by focused unit tests and through the MongoDB round-trip integration suite; the split removes independent responsibilities rather than redistributing methods cosmetically.

### Self-Review

- Confirmed every `IOException`, `RuntimeException`, or `Error` after live mutation is converted to `RestoreCutoverException`; rollback, cleanup, and evidence failures cannot replace or self-suppress the primary.
- Confirmed original and referenced namespaces reject blanks, `$`, `system.*`, embedded `.system.`, restore prefixes, null/C0/C1 controls, malformed Unicode, and namespace lengths over 255 UTF-8 bytes before database access or staging. The exact byte boundary remains accepted.
- Confirmed view dependency discovery and staging rewrite use the same recursive BSON walk for `viewOn`, `$lookup.from`, `$graphLookup.from`, both `$unionWith` forms, nested pipelines, and facets. Missing dependencies and indirect cycles fail preflight.
- Confirmed a MongoDB integration view with lookup, nested pipeline lookup/union, and string union produces identical restored results and all captured staging references use staging names.
- Confirmed export omits every MongoDB-reserved `system.*` namespace, including `system.profile`; restore applies the same policy, so export cannot emit internally generated archives that semantic preflight rejects.
- Confirmed generated manifest bytes count toward writer aggregate limits before the archive output is touched.
- Confirmed prior raw BSON, GridFS, views, time-series, index `v`/options, upload/entry/aggregate/temp bounds, byte-bounded batches, semantic preflight, rollback, recovery evidence, and cleanup suppression coverage remains green.
- Confirmed focused diff checks contain no whitespace errors and only the listed Task 2/minimal archive files will be staged.

### Commit

- Message: `fix: preserve restore recovery state`
- Final hash is supplied in the task handoff because a commit cannot include its own final hash.

### Concerns

- None beyond the already documented standalone MongoDB non-atomic cutover constraint, which is now consistently surfaced as maintenance-required recovery state.

## Final Two Findings Remediation - 2026-07-14

### Status

DONE

### RED Evidence

The initial shared-policy/planner run failed because the canonical policy did not yet exist:

```text
mvn -Dtest=MongoRestoreNamespacePolicyTest,MongoRestorePreflightPlannerTest test
COMPILATION ERROR
cannot find symbol: class MongoRestoreNamespacePolicy
BUILD FAILURE
```

After introducing only the policy and wiring preflight, the literal regression remained red:

```text
mvn -Dtest=MongoRestoreNamespacePolicyTest,MongoRestorePreflightPlannerTest test
Tests run: 27, Failures: 1, Errors: 0, Skipped: 0
literal_lookup and literal_union were incorrectly discovered as view dependencies.
BUILD FAILURE
```

The exporter regression then proved that a catalog entry preflight would reject still reached data export:

```text
mvn -Dtest=MongoDatabaseExportServiceTest#excludesCatalogNamespacesThatRestorePreflightWouldReject test
Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
NullPointerException in writeCollectionData for members.system.profile
BUILD FAILURE
```

### GREEN Evidence

Focused policy, exporter, and planner tests:

```text
mvn -Dtest=MongoRestoreNamespacePolicyTest,MongoDatabaseExportServiceTest,MongoRestorePreflightPlannerTest test
Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Docker-backed literal/rewrite regression:

```text
mvn -Dtest=MongoDatabaseRoundTripIntegrationTest#restoresViewsWithNestedDependenciesWithoutRewritingLiteralLookupData test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Focused Task 2 planner, export, restore, and archive suite:

```text
mvn -Dtest=MongoDatabaseRoundTripIntegrationTest,ArchivePackageServiceTest,BsonStreamCodecTest,MongoDatabaseExportServiceTest,MongoDatabaseImportServiceTest,MongoRestoreNamespacePolicyTest,MongoRestorePreflightPlannerTest,MongoRestoreCatalogServiceTest test
Tests run: 80, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Complete backend suite:

```text
mvn test
Tests run: 182, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Self-Review

- `MongoRestoreNamespacePolicy` is the single database-aware source for reserved restore prefixes, `system.`/`.system.` rules, controls, malformed Unicode, and MongoDB's 255-byte namespace limit. Export filters catalog records through it and preflight delegates validation to it.
- Tests cover `system.*`, embedded `.system.`, both restore prefixes, and permitted ordinary dotted names. MongoDB rejects physical `.system.` names itself, so the exporter case supplies that catalog entry through a mocked catalog and verifies no sidecar export is attempted.
- View dependency discovery and staging rewrites now inspect only stage-level `$lookup`, `$graphLookup`, `$unionWith`, and `$facet` operators. Recursion occurs only in `$lookup.pipeline`, `$unionWith.pipeline`, and each `$facet` branch; expression data such as `$literal` remains untouched.
- The integration regression confirms a real view's nested sources are staged, its `$literal: { $lookup: ... }` BSON is unchanged in the captured staging create command, and the restored view returns the same semantic results.
- `git diff --check` is clean. Only Task 2 source/tests and this report are candidates for staging; pre-existing receipt, UI, generated, progress, Docker Compose, and temporary changes remain unstaged.

### Concerns

- The existing standalone-MongoDB cutover atomicity limitation remains unchanged and is outside these two findings.

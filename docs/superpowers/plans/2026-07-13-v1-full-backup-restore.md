# v1.0 Full Backup And Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Admin download an AES-encrypted complete MongoDB backup and safely validate, safety-backup, and replace-restore the whole database through System Administration.

**Architecture:** `ArchivePackageService` writes versioned raw-BSON packages with checksums and AES encryption. `DataManagementService` stages expiring operations, coordinates maintenance mode, and restores every collection/index/GridFS entry after mandatory validation and safety-backup download.

**Tech Stack:** Java 21, Spring Boot 4, MongoDB Java Driver/Spring Data MongoDB, Zip4j 2.11.5, Jackson, JUnit 5, Testcontainers MongoDB, Vue 3, Vitest, Vue Testing Library.

## Global Constraints

- Back up the entire database, including future collections, GridFS, indexes, receipt data, archive registries, and audit events.
- Package data as raw BSON with a versioned manifest and SHA-256 checksums.
- Encrypt database ZIPs with Admin-supplied AES passwords that are never stored or logged.
- Do not retain completed backups on the server.
- Validate fully before mutation and require a downloaded encrypted safety backup.
- Current MongoDB is standalone; failure must leave explicit maintenance/recovery state.
- Only Admin may access any endpoint in this plan.

---

### Task 1: Build The Versioned Encrypted Archive Codec

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/church/operation/util/ArchiveType.java`
- Create: `backend/src/main/java/com/church/operation/dto/ArchiveManifest.java`
- Create: `backend/src/main/java/com/church/operation/dto/ArchiveCollectionManifest.java`
- Create: `backend/src/main/java/com/church/operation/service/BsonStreamCodec.java`
- Create: `backend/src/main/java/com/church/operation/service/ArchivePackageService.java`
- Test: `backend/src/test/java/com/church/operation/service/BsonStreamCodecTest.java`
- Test: `backend/src/test/java/com/church/operation/service/ArchivePackageServiceTest.java`

**Interfaces:**
- Produces: `write(Path output, char[] password, ArchiveManifest manifest, Map<String,Path> entries): void` and `validate(Path archive, char[] password, ArchiveType expected): ValidatedArchive`.

- [ ] **Step 1: Add failing BSON round-trip tests**

Round-trip documents containing `ObjectId`, `Decimal128`, `LocalDate`-mapped dates, booleans, arrays, nested documents, nulls, and binary data through concatenated BSON documents.

```java
codec.write(List.of(document), outputStream);
assertThat(codec.read(inputStream)).containsExactly(document);
```

- [ ] **Step 2: Add failing encrypted-package tests**

Test correct password, wrong password, changed entry bytes, wrong archive type, unsupported format version, and path-traversal entry names.

- [ ] **Step 3: Run and verify failure**

Run: `cd backend && mvn -Dtest=BsonStreamCodecTest,ArchivePackageServiceTest test`

Expected: compilation failures.

- [ ] **Step 4: Add Zip4j dependency**

```xml
<dependency>
  <groupId>net.lingala.zip4j</groupId>
  <artifactId>zip4j</artifactId>
  <version>2.11.5</version>
</dependency>
```

- [ ] **Step 5: Implement BSON stream framing**

BSON documents already start with a little-endian byte length. Read exactly that many bytes, reject lengths below 5 or above the configured entry limit, and decode with the MongoDB driver's `DocumentCodec`.

- [ ] **Step 6: Implement encrypted package format**

Use AES encryption and ZIP_DEFLATE for every entry. Write data entries first, compute SHA-256/count/size, then write `manifest.json`. During validation, normalize each name and reject absolute paths, `..`, links, duplicate entries, unlisted entries, and checksum/count mismatches.

- [ ] **Step 7: Run tests and commit**

Run: `cd backend && mvn -Dtest=BsonStreamCodecTest,ArchivePackageServiceTest test`

Expected: pass.

```bash
git add backend/pom.xml backend/src/main/java/com/church/operation/util/ArchiveType.java backend/src/main/java/com/church/operation/dto/ArchiveManifest.java backend/src/main/java/com/church/operation/dto/ArchiveCollectionManifest.java backend/src/main/java/com/church/operation/service/BsonStreamCodec.java backend/src/main/java/com/church/operation/service/ArchivePackageService.java backend/src/test/java/com/church/operation/service/BsonStreamCodecTest.java backend/src/test/java/com/church/operation/service/ArchivePackageServiceTest.java
git commit -m "feat: add encrypted BSON archive format"
```

### Task 2: Export And Import The Complete MongoDB Database

**Files:**
- Create: `backend/src/main/java/com/church/operation/config/DataManagementProperties.java`
- Create: `backend/src/main/java/com/church/operation/service/MongoDatabaseExportService.java`
- Create: `backend/src/main/java/com/church/operation/service/MongoDatabaseImportService.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/pom.xml`
- Test: `backend/src/test/java/com/church/operation/service/MongoDatabaseRoundTripIntegrationTest.java`

**Interfaces:**
- Consumes: package codec from Task 1.
- Produces: `exportFull(Path,char[]): ArchiveManifest`, `validateFull(Path,char[]): ValidatedArchive`, `replaceAll(ValidatedArchive): RestoreVerification`.

- [ ] **Step 1: Add Testcontainers dependencies and failing round-trip test**

Add test-scoped `org.testcontainers:mongodb` and `org.testcontainers:junit-jupiter`, using the versions managed by Spring Boot's BOM.

Seed ordinary collections, custom indexes, `fs.files`, and `fs.chunks`; export; clear; import; then assert byte-for-byte documents, index keys/options, and GridFS bytes.

- [ ] **Step 2: Run and verify failure**

Run: `cd backend && mvn -Dtest=MongoDatabaseRoundTripIntegrationTest test`

Expected: missing export/import service compilation failure.

- [ ] **Step 3: Add bounded temp/upload properties**

```yaml
church:
  data-management:
    temp-directory: ${CHURCH_DATA_TEMP_DIRECTORY:${java.io.tmpdir}/church-operation-data}
    operation-expiry: ${CHURCH_DATA_OPERATION_EXPIRY:30m}
    max-upload-size: ${CHURCH_DATA_MAX_UPLOAD_SIZE:2GB}
```

- [ ] **Step 4: Implement complete export**

Use `MongoDatabase.listCollectionNames()`, collection options, `listIndexes()`, and raw documents. Export all collections, including GridFS collections, without hard-coded names. Exclude temporary restore-staging collections by a reserved prefix.

- [ ] **Step 5: Implement replacement import**

Create collections with options, insert raw documents in bounded batches, and recreate non-`_id` indexes. Verification compares manifest counts and expected index signatures.

- [ ] **Step 6: Run integration test and commit**

Run: `cd backend && mvn -Dtest=MongoDatabaseRoundTripIntegrationTest test`

Expected: pass when Docker is available; otherwise report Testcontainers as the explicit blocker rather than skipping silently.

```bash
git add backend/pom.xml backend/src/main/resources/application.yml backend/src/main/java/com/church/operation/config/DataManagementProperties.java backend/src/main/java/com/church/operation/service/MongoDatabaseExportService.java backend/src/main/java/com/church/operation/service/MongoDatabaseImportService.java backend/src/test/java/com/church/operation/service/MongoDatabaseRoundTripIntegrationTest.java
git commit -m "feat: round-trip complete MongoDB backups"
```

### Task 3: Add Staged Restore Operations And Maintenance Mode

**Files:**
- Create: `backend/src/main/java/com/church/operation/util/DataOperationType.java`
- Create: `backend/src/main/java/com/church/operation/util/DataOperationStatus.java`
- Create: `backend/src/main/java/com/church/operation/dto/DataOperationResponse.java`
- Create: `backend/src/main/java/com/church/operation/service/DataOperationStore.java`
- Create: `backend/src/main/java/com/church/operation/service/MaintenanceModeService.java`
- Create: `backend/src/main/java/com/church/operation/filter/MaintenanceModeFilter.java`
- Create: `backend/src/main/java/com/church/operation/service/DataManagementService.java`
- Modify: `backend/src/main/java/com/church/operation/service/AuthTokenService.java`
- Test: `backend/src/test/java/com/church/operation/service/DataManagementServiceTest.java`
- Test: `backend/src/test/java/com/church/operation/filter/MaintenanceModeFilterTest.java`

**Interfaces:**
- Produces: validate/safety-backup/execute/status service workflow and `AuthTokenService.revokeAll()`.

- [ ] **Step 1: Write failing staged-flow tests**

Cover state transitions:

```text
UPLOADED -> VALIDATED -> SAFETY_BACKUP_DOWNLOADED -> RESTORING -> COMPLETE
                                                    -> FAILED_MAINTENANCE
```

Reject execute before safety download, expired IDs, second concurrent mutation, wrong actor, and wrong confirmation phrase.

- [ ] **Step 2: Write failing filter tests**

When maintenance is active, GET health and restore-status remain available, while POST/PUT/PATCH/DELETE outside restore endpoints return 503 JSON. Normal requests pass through when inactive.

- [ ] **Step 3: Run and verify failure**

Run: `cd backend && mvn -Dtest=DataManagementServiceTest,MaintenanceModeFilterTest test`

Expected: compilation failures.

- [ ] **Step 4: Implement operation store and secure cleanup**

Store only operation metadata, actor ID, staged paths, validation summary, and expiry. Keep passwords in request-local `char[]`, overwrite with `Arrays.fill(password, '\0')` in `finally`, and never retain them in operation objects.

- [ ] **Step 5: Implement restore orchestration**

On execute: acquire single-operation lock, enable maintenance, call replacement import, verify, call `authTokenService.revokeAll()`, then disable maintenance only on success. On failure keep maintenance active and operation status `FAILED_MAINTENANCE`.

- [ ] **Step 6: Run tests and commit**

Run: `cd backend && mvn -Dtest=DataManagementServiceTest,MaintenanceModeFilterTest test`

Expected: pass.

```bash
git add backend/src/main/java/com/church/operation/util/DataOperationType.java backend/src/main/java/com/church/operation/util/DataOperationStatus.java backend/src/main/java/com/church/operation/dto/DataOperationResponse.java backend/src/main/java/com/church/operation/service/DataOperationStore.java backend/src/main/java/com/church/operation/service/MaintenanceModeService.java backend/src/main/java/com/church/operation/filter/MaintenanceModeFilter.java backend/src/main/java/com/church/operation/service/DataManagementService.java backend/src/main/java/com/church/operation/service/AuthTokenService.java backend/src/test/java/com/church/operation/service/DataManagementServiceTest.java backend/src/test/java/com/church/operation/filter/MaintenanceModeFilterTest.java
git commit -m "feat: stage safe full database restores"
```

### Task 4: Expose Admin Backup And Restore APIs

**Files:**
- Create: `backend/src/main/java/com/church/operation/rest/DataManagementController.java`
- Create: `backend/src/main/java/com/church/operation/dto/BackupRequest.java`
- Create: `backend/src/main/java/com/church/operation/dto/RestoreExecuteRequest.java`
- Create: `backend/src/test/java/com/church/operation/rest/DataManagementControllerTest.java`

**Interfaces:**
- Produces: full-backup, restore validate, safety-backup, execute, and status endpoints.

- [ ] **Step 1: Write failing controller/security tests**

Test Admin success, non-Admin forbidden, password absence, multipart upload, attachment headers, explicit operation ID path variables, confirmation mismatch, and status ownership.

- [ ] **Step 2: Run and verify failure**

Run: `cd backend && mvn -Dtest=DataManagementControllerTest test`

Expected: 404 failures.

- [ ] **Step 3: Implement endpoint contract**

Use request-body passwords for generated downloads and multipart fields for restore validation. Never accept passwords in URLs. Return `application/zip` attachments and JSON operation summaries.

- [ ] **Step 4: Run tests and commit**

Run: `cd backend && mvn -Dtest=DataManagementControllerTest test`

Expected: pass.

```bash
git add backend/src/main/java/com/church/operation/rest/DataManagementController.java backend/src/main/java/com/church/operation/dto/BackupRequest.java backend/src/main/java/com/church/operation/dto/RestoreExecuteRequest.java backend/src/test/java/com/church/operation/rest/DataManagementControllerTest.java
git commit -m "feat: expose admin backup and restore APIs"
```

### Task 5: Build System Administration Full-Database UI

**Files:**
- Create: `frontend/src/api/dataManagement.ts`
- Modify: `frontend/src/api/http.ts`
- Rewrite: `frontend/src/views/SystemAdministrationView.vue`
- Create: `frontend/src/views/SystemAdministrationView.test.ts`
- Modify: `frontend/src/styles/main.css`

**Interfaces:**
- Consumes: Task 4 APIs.
- Produces: Full Backup and Full Restore staged UI.

- [ ] **Step 1: Write failing view tests**

Test password/confirmation matching, backup download, restore validation summary, disabled execute before safety backup, typed phrase, progress/error states, password clearing, and redirect to Login after complete restore.

- [ ] **Step 2: Run and verify failure**

Run: `cd frontend && npm test -- SystemAdministrationView.test.ts`

Expected: placeholder view assertions fail.

- [ ] **Step 3: Add multipart/password API helpers**

Implement explicit functions rather than a generic password logger. Ensure error responses from Blob endpoints are parsed as JSON when content type indicates JSON.

- [ ] **Step 4: Implement full-width staged sections**

Use separate Backup and Restore tabs/sections, password reveal toggles, file chooser, validation counts, mandatory safety-download action, typed confirmation, and operation polling. Do not nest cards.

- [ ] **Step 5: Run tests and build**

Run: `cd frontend && npm test -- SystemAdministrationView.test.ts`

Run: `cd frontend && npm run build`

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/dataManagement.ts frontend/src/api/http.ts frontend/src/views/SystemAdministrationView.vue frontend/src/views/SystemAdministrationView.test.ts frontend/src/styles/main.css
git commit -m "feat: add full database administration UI"
```

### Task 6: Verify Full Backup And Restore Slice

**Files:**
- Modify only for scoped verification fixes.

**Interfaces:**
- Produces: trusted package/restore foundation for fiscal archives.

- [ ] **Step 1: Run all suites including Testcontainers**

Run: `cd backend && mvn test`

Run: `cd frontend && npm test && npm run build`

Expected: all pass.

- [ ] **Step 2: Perform destructive disposable round trip**

In local Docker only: seed members, offerings, transactions, budgets, receipts, indexes, and images; download backup; alter/delete data; validate; download safety backup; restore; verify exact counts and image bytes; verify old tokens fail and restored credentials work.

- [ ] **Step 3: Test failure modes**

Wrong password, changed ZIP byte, wrong archive type, expired operation, restore without safety download, and forced import failure must not reopen a partial database.

- [ ] **Step 4: Capture System Administration screenshots and close verification cleanly**

Verify desktop/mobile text fit and destructive-action hierarchy. Run `git diff --check`; expected output is empty. Route any discovered defect back to its owning task with a regression test.

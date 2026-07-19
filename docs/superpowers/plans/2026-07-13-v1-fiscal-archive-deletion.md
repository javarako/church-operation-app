# v1.0 Fiscal Archive And Protected Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Download and clean one configured fiscal year, safely merge-restore it later, and enable hard deletion only for members/reference values with no live, receipt, or archive dependencies.

**Architecture:** Fiscal archives reuse the encrypted BSON package foundation but contain selected offerings, linked income, expenses, and budgets. A compact `FiscalArchiveRegistry` protects member/reference relationships after downloaded records are removed.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data MongoDB, Zip4j, Vue 3, JUnit 5, Mockito, Testcontainers MongoDB, Vitest, Vue Testing Library.

## Global Constraints

- Fiscal boundaries use `church.fiscal-year.start-month` inclusively.
- Include soft-deleted fiscal records.
- Keep members, reference data, tax receipts, audit events, and GridFS live.
- Download must complete before cleanup confirmation is enabled.
- Restore preflight blocks every duplicate/conflict before insertion.
- Member/reference deletion is hard delete only when unused.
- Bootstrap Admin and current actor can never be deleted.

---

### Task 1: Persist Fiscal Archive Registry Metadata

**Files:**
- Create: `backend/src/main/java/com/church/operation/entity/FiscalArchiveRegistry.java`
- Create: `backend/src/main/java/com/church/operation/util/FiscalArchiveStatus.java`
- Create: `backend/src/main/java/com/church/operation/repo/FiscalArchiveRegistryRepository.java`
- Create: `backend/src/main/java/com/church/operation/dto/FiscalArchivePreview.java`
- Test: `backend/src/test/java/com/church/operation/service/FiscalArchiveServiceTest.java`

**Interfaces:**
- Produces: registry model containing archive ID/checksum, year/range, counts, member IDs, reference-code usage, status, actor, and timestamps.

- [ ] **Step 1: Write failing fiscal-boundary and preview tests**

Test January and non-January starts. For April start, fiscal 2026 must be `2026-04-01` through `2027-03-31`.

Assert preview counts include deleted records and linked income transactions by offering source ID.

- [ ] **Step 2: Run and verify failure**

Run: `cd backend && mvn -Dtest=FiscalArchiveServiceTest test`

Expected: missing service/model compilation failure.

- [ ] **Step 3: Define registry and indexes**

Use a unique `archiveId`, index fiscal year/status/checksum, and store sets for member IDs and each reference-data type. Do not store offering amounts or full financial documents in registry metadata.

- [ ] **Step 4: Commit model/test scaffold after compilation**

Run: `cd backend && mvn -DskipTests compile`

Expected: success after adding required model/repository types.

```bash
git add backend/src/main/java/com/church/operation/entity/FiscalArchiveRegistry.java backend/src/main/java/com/church/operation/util/FiscalArchiveStatus.java backend/src/main/java/com/church/operation/repo/FiscalArchiveRegistryRepository.java backend/src/main/java/com/church/operation/dto/FiscalArchivePreview.java backend/src/test/java/com/church/operation/service/FiscalArchiveServiceTest.java
git commit -m "feat: add fiscal archive registry"
```

### Task 2: Generate, Download, And Clean Fiscal Archives

**Files:**
- Create: `backend/src/main/java/com/church/operation/service/FiscalArchiveService.java`
- Modify: `backend/src/main/java/com/church/operation/repo/OfferingRepository.java`
- Modify: `backend/src/main/java/com/church/operation/repo/FinancialTransactionRepository.java`
- Modify: `backend/src/main/java/com/church/operation/repo/BudgetRepository.java`
- Modify: `backend/src/main/java/com/church/operation/rest/DataManagementController.java`
- Test: `backend/src/test/java/com/church/operation/service/FiscalArchiveServiceTest.java`
- Test: `backend/src/test/java/com/church/operation/rest/DataManagementControllerTest.java`

**Interfaces:**
- Consumes: encrypted package codec and operation store from full-backup plan.
- Produces: `preview`, `download`, and idempotent `clean` operations.

- [ ] **Step 1: Complete failing archive-content tests**

Assert archive includes:

- Offerings by `offeringDate` in range, including deleted.
- Linked offering-income transactions by source ID.
- Manual expenses by `transactionDate` in range, including deleted.
- Budgets by exact fiscal year, including deleted.
- Member/reference usage sets in manifest and registry.

- [ ] **Step 2: Add repository methods that do not filter deleted records**

```java
List<Offering> findByOfferingDateBetween(LocalDate start, LocalDate end);

@Query("{ 'sourceType': 'OFFERING', 'sourceId': { $in: ?0 } }")
List<FinancialTransaction> findOfferingTransactionsBySourceIds(List<String> ids);
```

Add manual expense and all-budget fiscal queries with explicit `@Query` definitions.

- [ ] **Step 3: Implement archive generation**

Create a `FISCAL` manifest using the same raw BSON/AES package. Save a staged operation only after package validation succeeds. Mark download completion when the attachment response finishes.

- [ ] **Step 4: Implement idempotent cleanup**

Delete exactly manifest IDs in dependency order: linked transactions, offerings/manual expenses, budgets. Recount all IDs. Save/update registry only after absence verification. Retrying repeats safe `deleteAllById` calls.

- [ ] **Step 5: Run service/controller tests**

Run: `cd backend && mvn -Dtest=FiscalArchiveServiceTest,DataManagementControllerTest test`

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/church/operation/service/FiscalArchiveService.java backend/src/main/java/com/church/operation/repo/OfferingRepository.java backend/src/main/java/com/church/operation/repo/FinancialTransactionRepository.java backend/src/main/java/com/church/operation/repo/BudgetRepository.java backend/src/main/java/com/church/operation/rest/DataManagementController.java backend/src/test/java/com/church/operation/service/FiscalArchiveServiceTest.java backend/src/test/java/com/church/operation/rest/DataManagementControllerTest.java
git commit -m "feat: archive and clean fiscal records"
```

### Task 3: Validate And Merge-Restore Fiscal Archives

**Files:**
- Modify: `backend/src/main/java/com/church/operation/service/FiscalArchiveService.java`
- Modify: `backend/src/main/java/com/church/operation/rest/DataManagementController.java`
- Test: `backend/src/test/java/com/church/operation/service/FiscalArchiveRestoreIntegrationTest.java`

**Interfaces:**
- Produces: staged validation and execute flow for `FISCAL` packages.

- [ ] **Step 1: Write failing integration tests**

Cover successful merge, duplicate offering ID, duplicate budget key, missing member, missing reference code, checksum mismatch, wrong archive type, and forced insertion failure with compensating deletion.

- [ ] **Step 2: Run and verify failure**

Run: `cd backend && mvn -Dtest=FiscalArchiveRestoreIntegrationTest test`

Expected: restore methods missing/failing.

- [ ] **Step 3: Implement complete preflight**

Before insertion, verify every document ID is absent, every member/reference dependency exists, budget uniqueness is clear, linked source IDs resolve inside the package, and registry checksum/year match.

- [ ] **Step 4: Implement insertion and compensation**

Track inserted IDs by collection. On unexpected failure, delete only IDs inserted by this operation in reverse dependency order. Mark operation failed and registry unchanged. On success verify counts and mark registry `RESTORED`.

- [ ] **Step 5: Run integration tests and commit**

Run: `cd backend && mvn -Dtest=FiscalArchiveRestoreIntegrationTest test`

Expected: pass.

```bash
git add backend/src/main/java/com/church/operation/service/FiscalArchiveService.java backend/src/main/java/com/church/operation/rest/DataManagementController.java backend/src/test/java/com/church/operation/service/FiscalArchiveRestoreIntegrationTest.java
git commit -m "feat: restore fiscal archives safely"
```

### Task 4: Add Protected Member Deletion

**Files:**
- Modify: `backend/src/main/java/com/church/operation/repo/OfferingRepository.java`
- Modify: `backend/src/main/java/com/church/operation/repo/PasswordResetTokenRepository.java`
- Modify: `backend/src/main/java/com/church/operation/repo/TaxReceiptRepository.java`
- Modify: `backend/src/main/java/com/church/operation/service/MemberService.java`
- Modify: `backend/src/main/java/com/church/operation/rest/MemberController.java`
- Create: `backend/src/main/java/com/church/operation/dto/DeletionDependency.java`
- Create: `backend/src/main/java/com/church/operation/exception/DeletionBlockedException.java`
- Test: `backend/src/test/java/com/church/operation/service/MemberServiceTest.java`

**Interfaces:**
- Produces: `MemberService.deleteMember(Member actor, String id): void` and `DELETE /api/members/{id}`.

- [ ] **Step 1: Write failing deletion tests**

Test success plus blocks for bootstrap Admin, self, live/deleted offerings, archive registry, and all tax receipt statuses. Assert successful deletion removes image and password-reset tokens.

- [ ] **Step 2: Run and verify failure**

Run: `cd backend && mvn -Dtest=MemberServiceTest test`

Expected: missing delete method failure.

- [ ] **Step 3: Implement dependency collection**

Return all blocker types in one structured error rather than stopping at the first. Do not include sensitive offering amounts in error details.

- [ ] **Step 4: Implement hard delete and endpoint**

After all checks: remove GridFS image through `MemberImageService`, delete reset tokens, then delete member. Use explicit `@PathVariable("id")`.

- [ ] **Step 5: Run tests and commit**

Run: `cd backend && mvn -Dtest=MemberServiceTest test`

Expected: pass.

```bash
git add backend/src/main/java/com/church/operation/repo/OfferingRepository.java backend/src/main/java/com/church/operation/repo/PasswordResetTokenRepository.java backend/src/main/java/com/church/operation/repo/TaxReceiptRepository.java backend/src/main/java/com/church/operation/service/MemberService.java backend/src/main/java/com/church/operation/rest/MemberController.java backend/src/main/java/com/church/operation/dto/DeletionDependency.java backend/src/main/java/com/church/operation/exception/DeletionBlockedException.java backend/src/test/java/com/church/operation/service/MemberServiceTest.java
git commit -m "feat: protect member deletion"
```

### Task 5: Add Protected Reference-Data Deletion

**Files:**
- Modify: `backend/src/main/java/com/church/operation/service/ReferenceDataService.java`
- Modify: `backend/src/main/java/com/church/operation/rest/ReferenceDataController.java`
- Modify: `backend/src/main/java/com/church/operation/repo/ReferenceDataRepository.java`
- Test: `backend/src/test/java/com/church/operation/service/ReferenceDataServiceTest.java`

**Interfaces:**
- Produces: `ReferenceDataService.delete(Member actor, String id): void` and `DELETE /api/reference-data/{id}`.

- [ ] **Step 1: Write failing type-specific dependency tests**

Test each reference type against members, offerings including deleted, transactions including deleted, budgets, archive registry, and child sub-categories. Test unused seeded default deletion succeeds.

- [ ] **Step 2: Run and verify failure**

Run: `cd backend && mvn -Dtest=ReferenceDataServiceTest test`

Expected: missing delete method failure.

- [ ] **Step 3: Implement type-specific checks**

Use `MongoTemplate.exists(Query, Entity.class)` for exact fields where repository method proliferation would obscure the mapping. Parent category deletion must also check `ReferenceData.parentCode`.

- [ ] **Step 4: Add endpoint and run tests**

Run: `cd backend && mvn -Dtest=ReferenceDataServiceTest test`

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/church/operation/service/ReferenceDataService.java backend/src/main/java/com/church/operation/rest/ReferenceDataController.java backend/src/main/java/com/church/operation/repo/ReferenceDataRepository.java backend/src/test/java/com/church/operation/service/ReferenceDataServiceTest.java
git commit -m "feat: protect reference data deletion"
```

### Task 6: Add Fiscal And Delete UI Workflows

**Files:**
- Modify: `frontend/src/api/dataManagement.ts`
- Modify: `frontend/src/api/members.ts`
- Modify: `frontend/src/api/referenceData.ts`
- Modify: `frontend/src/views/SystemAdministrationView.vue`
- Modify: `frontend/src/views/SystemAdministrationView.test.ts`
- Modify: `frontend/src/views/MembersView.vue`
- Modify: `frontend/src/views/ReferenceDataView.vue`
- Modify: `frontend/src/views/MembersView.test.ts`
- Create: `frontend/src/views/ReferenceDataView.test.ts`
- Modify: `frontend/src/styles/main.css`

**Interfaces:**
- Consumes: archive and delete endpoints.
- Produces: complete approved Admin/archive and trash-icon workflows.

- [ ] **Step 1: Write failing frontend tests**

Test fiscal preview counts, password confirmation, archive download, disabled clean before download, typed confirmation, restore validation/conflict errors, member trash icon, reference trash icon, blocked-deletion message, and successful row removal.

- [ ] **Step 2: Run and verify failure**

Run: `cd frontend && npm test -- SystemAdministrationView.test.ts MembersView.test.ts ReferenceDataView.test.ts`

Expected: missing workflow/action failures.

- [ ] **Step 3: Implement fiscal System Administration sections**

Use the configured fiscal-year label returned by preview. Keep full Backup/Restore controls intact. Clear passwords after requests and revoke download object URLs.

- [ ] **Step 4: Add trash actions matching existing UI**

Use Lucide `Trash2`, icon-only buttons with title/aria-label, and confirmation dialogs. On 409 dependency error, show all returned blocker messages and recommend inactive/locked for members.

- [ ] **Step 5: Run tests and build**

Run: `cd frontend && npm test -- SystemAdministrationView.test.ts MembersView.test.ts ReferenceDataView.test.ts`

Run: `cd frontend && npm run build`

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/dataManagement.ts frontend/src/api/members.ts frontend/src/api/referenceData.ts frontend/src/views/SystemAdministrationView.vue frontend/src/views/SystemAdministrationView.test.ts frontend/src/views/MembersView.vue frontend/src/views/ReferenceDataView.vue frontend/src/views/MembersView.test.ts frontend/src/views/ReferenceDataView.test.ts frontend/src/styles/main.css
git commit -m "feat: add fiscal archive and protected deletion UI"
```

### Task 7: Verify Fiscal Archive And Deletion Slice

**Files:**
- Modify only for scoped verification fixes.

**Interfaces:**
- Produces: completed core v1.0 feature set for integration plan.

- [ ] **Step 1: Run all suites**

Run: `cd backend && mvn test`

Run: `cd frontend && npm test && npm run build`

Expected: all pass.

- [ ] **Step 2: Disposable fiscal round trip**

Seed records immediately before, inside, and after a non-January fiscal range, including deleted records and images. Archive/clean the selected year; verify retained records; restore; verify IDs/counts/totals.

- [ ] **Step 3: Deletion matrix smoke test**

Verify unreferenced member/reference deletion succeeds and live, receipt, archive, and child-reference dependencies block with correct explanations.

- [ ] **Step 4: Capture responsive screenshots and close verification cleanly**

Capture System Administration, Members, and Reference Data desktop/mobile states. Run `git diff --check`; expected output is empty. Route defects back to their owning tasks with regression tests.

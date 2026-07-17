# Reference Data And Offering Hierarchy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make reference-data maintenance ADMIN-only with immutable identities, add Committee Code, split offering Fund and Category, migrate legacy data safely, and fix stale parent-category options.

**Architecture:** Keep the generic `ReferenceData` document and add explicit `OFFERING_FUND`, `OFFERING_CATEGORY`, and `COMMITTEE_CODE` types. Separate the active operational read endpoint from the ADMIN maintenance endpoint. Add an ordered, idempotent startup migration that maps legacy combined offering values to `GENERAL -> existing category`, then update every offering-dependent workflow to use the hierarchy.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, Spring Data MongoDB, JUnit 5, Mockito, Testcontainers, Vue 3, Vue Router, Vitest, Vue Testing Library.

## Global Constraints

- Package all Java code under `com.church.operation` using the existing `config`, `dto`, `entity`, `exception`, `filter`, `repo`, `rest`, `service`, and `util` structure.
- Reference-data Type and Code are immutable after creation.
- Only ADMIN can list maintenance data or create, update, and delete reference data.
- Authenticated operational users retain read access to active reference values.
- Offering hierarchy is Fund -> Category.
- Existing combined offering values migrate under `GENERAL` without losing records.
- Existing full backups and fiscal archives remain recoverable.
- Follow TDD for every behavior change.
- Leave changes uncommitted for the user to commit.

---

## File Structure

### Backend

- Modify `util/ReferenceDataType.java`: add new types and retain the legacy type for migration.
- Modify `service/ReferenceDataService.java`: immutable identity, parent validation, ADMIN maintenance, defaults.
- Modify `rest/ReferenceDataController.java`: split active reads from maintenance reads.
- Modify `service/ReferenceDataDeletionService.java`: ADMIN-only deletion and hierarchy dependencies.
- Create `service/OfferingHierarchyMigrationService.java`: idempotent reference and record migration.
- Create `service/OfferingHierarchyMigrationRunner.java`: ordered startup invocation before reference bootstrap so legacy labels and statuses are preserved before defaults are seeded.
- Modify `service/ReferenceDataBootstrapRunner.java`: explicit ordering.
- Modify offering entities, DTOs, service, reports, budgets, archive registry, and archive service to carry Fund and Category.
- Update the corresponding unit and integration tests.

### Frontend

- Modify `api/referenceData.ts`: new types and separate maintenance-list API.
- Modify `views/ReferenceDataView.vue`: immutable edit controls, ADMIN maintenance API, parent cache refresh.
- Modify router and app layout: ADMIN-only route and menu.
- Modify offering, budget, finance, and report APIs/views to use Fund and Category.
- Expand Vue tests for permissions, immediate refresh, filtering, and payloads.

---

### Task 1: Reference-Data Identity, Authorization, Committee Code, And Refresh Defect

**Files:**
- Modify: `backend/src/main/java/com/church/operation/util/ReferenceDataType.java`
- Modify: `backend/src/main/java/com/church/operation/service/ReferenceDataService.java`
- Modify: `backend/src/main/java/com/church/operation/service/ReferenceDataDeletionService.java`
- Modify: `backend/src/main/java/com/church/operation/rest/ReferenceDataController.java`
- Test: `backend/src/test/java/com/church/operation/service/ReferenceDataServiceTest.java`
- Test: `backend/src/test/java/com/church/operation/service/ReferenceDataDeletionServiceTest.java`
- Create: `backend/src/test/java/com/church/operation/rest/ReferenceDataControllerTest.java`
- Modify: `frontend/src/api/referenceData.ts`
- Modify: `frontend/src/views/ReferenceDataView.vue`
- Modify: `frontend/src/views/ReferenceDataView.test.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/layouts/AppLayout.vue`
- Modify: `frontend/src/layouts/AppLayout.test.ts`
- Modify: `frontend/src/App.test.ts`

**Interfaces:**
- `ReferenceDataService.listActive(ReferenceDataType, String)` returns active operational options without maintenance privileges.
- `ReferenceDataService.listAll(Member, ReferenceDataType, String)` requires ADMIN.
- `GET /api/reference-data/{type}` returns active values.
- `GET /api/reference-data/maintenance/{type}` returns all values for ADMIN.
- `listReferenceData(type, parentCode?)` calls the active endpoint.
- `listAllReferenceData(type, parentCode?)` calls the maintenance endpoint.

- [ ] **Step 1: Write backend failing tests for immutable identity and ADMIN-only maintenance**

Add tests proving:

```java
assertThatThrownBy(() -> service.update(admin, "ref-id",
    request(ReferenceDataType.GROUP_CODE, "NEW_CODE", "Choir", null)))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("Type and code cannot be changed");

assertThatThrownBy(() -> service.listAll(treasurer, ReferenceDataType.GROUP_CODE, null))
    .isInstanceOf(SecurityException.class);

assertThat(service.listActive(ReferenceDataType.GROUP_CODE, null))
    .containsExactly(activeGroup);
```

Also verify MEMBERSHIP and TREASURER cannot create or update, and only ADMIN can delete.

- [ ] **Step 2: Run focused backend tests and verify RED**

Run:

```bash
cd backend
mvn -Dtest=ReferenceDataServiceTest,ReferenceDataDeletionServiceTest,ReferenceDataControllerTest test
```

Expected: failures showing mutable Code/Type, old role permissions, and the missing maintenance endpoint.

- [ ] **Step 3: Implement the backend reference-data contract**

Add enum values:

```java
COMMITTEE_CODE,
OFFERING_FUND,
OFFERING_CATEGORY
```

Retain `OFFERING_FUND_CATEGORY` as a legacy migration type.

In `update`, load the stored record and reject when either identity field differs:

```java
if (referenceData.getType() != request.type()
    || !referenceData.getCode().equals(normalizeCode(request.code()))) {
    throw new IllegalArgumentException("Reference data type and code cannot be changed after creation.");
}
```

Change maintenance authorization to ADMIN only. Keep active reads role-neutral at the service layer because the controller is already authenticated.

Add the active and maintenance controller endpoints exactly as defined above.

Update default seeding for new databases:

```text
OFFERING_FUND: GENERAL / General Fund
OFFERING_CATEGORY under GENERAL: TITHE, THANKSGIVING, MISSION, BUILDING
```

Do not seed Committee Code values. Existing legacy
`OFFERING_FUND_CATEGORY` records are handled only by the migration task.

- [ ] **Step 4: Run focused backend tests and verify GREEN**

Run the Step 2 command.

Expected: all focused tests pass.

- [ ] **Step 5: Write frontend failing tests**

Add tests proving:

- Reference Data appears only for ADMIN.
- TREASURER and MEMBERSHIP redirect from `/reference-data` to `/`.
- Committee Code appears in the type selector.
- Type and Code are disabled after selecting an existing row.
- Creating a Financial Category causes a second `FINANCIAL_CATEGORY` maintenance fetch.
- The newly returned Financial Category appears immediately in the Financial Sub-category parent selector.

Mock `createReferenceData` to return the new record and return different category lists on successive maintenance calls.

- [ ] **Step 6: Run focused frontend tests and verify RED**

Run:

```bash
cd frontend
npm test -- src/views/ReferenceDataView.test.ts src/layouts/AppLayout.test.ts src/App.test.ts
```

Expected: failures for current shared access, editable identity controls, missing Committee Code, and stale parent options.

- [ ] **Step 7: Implement frontend reference-data behavior**

- Route `/reference-data` with `adminRoles`.
- Show the menu item only for ADMIN.
- Use `listAllReferenceData` in the maintenance view.
- Add Committee Code, Offering Fund, and Offering Category type options.
- Disable Type and Code when `selectedOption` is non-null.
- Show Parent Fund for `OFFERING_CATEGORY`.
- After every create, update, or delete, reload the selected list, Offering Funds, and Financial Categories with one `Promise.all`.

- [ ] **Step 8: Run focused frontend tests and verify GREEN**

Run the Step 6 command.

Expected: all focused tests pass.

---

### Task 2: Idempotent Legacy Offering Hierarchy Migration

**Files:**
- Modify: `backend/src/main/java/com/church/operation/entity/Offering.java`
- Modify: `backend/src/main/java/com/church/operation/repo/ReferenceDataRepository.java`
- Modify: `backend/src/main/java/com/church/operation/repo/OfferingRepository.java`
- Modify: `backend/src/main/java/com/church/operation/repo/FinancialTransactionRepository.java`
- Modify: `backend/src/main/java/com/church/operation/repo/BudgetRepository.java`
- Modify: `backend/src/main/java/com/church/operation/service/ReferenceDataBootstrapRunner.java`
- Create: `backend/src/main/java/com/church/operation/service/OfferingHierarchyMigrationService.java`
- Create: `backend/src/main/java/com/church/operation/service/OfferingHierarchyMigrationRunner.java`
- Create: `backend/src/test/java/com/church/operation/service/OfferingHierarchyMigrationServiceTest.java`
- Modify: `backend/src/test/java/com/church/operation/V1WorkflowIntegrationTest.java`

**Interfaces:**
- `Offering` adds `fundCode` and `categoryCode`; legacy `fundCategory` remains temporarily readable.
- `OfferingHierarchyMigrationService.migrate()` is safe to run repeatedly.
- Migration runs at order 5; bootstrap runs at order 10.

- [ ] **Step 1: Write failing migration tests**

Cover:

1. `GENERAL` is created if absent.
2. Legacy `TITHE` becomes `OFFERING_CATEGORY/TITHE` with parent `GENERAL`.
3. Offering `fundCategory=TITHE` becomes `fundCode=GENERAL`, `categoryCode=TITHE`.
4. Linked income becomes `category=GENERAL`, `subCategory=TITHE`.
5. Offering-income budget becomes `category=GENERAL`, `subCategory=TITHE`.
6. Running `migrate()` twice creates no duplicates and does not overwrite nonblank new fields.
7. Deleted offerings and budgets are migrated because archive/restore and historic reads still depend on them.

- [ ] **Step 2: Run migration tests and verify RED**

Run:

```bash
cd backend
mvn -Dtest=OfferingHierarchyMigrationServiceTest test
```

Expected: compilation or assertion failure because the migration and new fields do not exist.

- [ ] **Step 3: Implement model fields and migration**

Implement a repository-backed migration:

```java
public void migrate() {
    ReferenceData general = ensureGeneralFund();
    migrateLegacyReferences(general.getCode());
    migrateOfferings(general.getCode());
    migrateLinkedIncome(general.getCode());
    migrateOfferingBudgets(general.getCode());
}
```

Only migrate records where the new hierarchy is incomplete and the legacy value is nonblank. Log record IDs for conflicts and leave conflicting legacy data intact.

- [ ] **Step 4: Run migration tests and verify GREEN**

Run the Step 2 command.

Expected: all migration tests pass.

- [ ] **Step 5: Extend the lifecycle integration test**

Seed a legacy combined reference, offering, linked income, and budget; invoke migration; then complete backup, fiscal archive/restore, and full restore. Verify the hierarchy and legacy record count remain stable.

- [ ] **Step 6: Run the integration test**

Run:

```bash
cd backend
mvn -Dtest=V1WorkflowIntegrationTest test
```

Expected: pass with Testcontainers MongoDB.

---

### Task 3: Offering Entry And Linked Income Hierarchy

**Files:**
- Modify: `backend/src/main/java/com/church/operation/dto/OfferingRequest.java`
- Modify: `backend/src/main/java/com/church/operation/dto/OfferingResponse.java`
- Modify: `backend/src/main/java/com/church/operation/service/OfferingService.java`
- Modify: `backend/src/test/java/com/church/operation/service/OfferingServiceTest.java`
- Modify: `frontend/src/api/offerings.ts`
- Modify: `frontend/src/views/OfferingsView.vue`
- Create: `frontend/src/views/OfferingsView.test.ts`

**Interfaces:**
- `OfferingRequest(..., String fundCode, String categoryCode, BigDecimal amount, ...)`
- `OfferingResponse` exposes `fundCode` and `categoryCode`.
- Linked income stores Fund in `category` and Offering Category in `subCategory`.

- [ ] **Step 1: Write backend failing offering tests**

Test create and update with:

```java
when(references.findByTypeAndCode(OFFERING_FUND, "GENERAL"))
    .thenReturn(Optional.of(activeFund));
when(references.findByTypeAndCode(OFFERING_CATEGORY, "TITHE"))
    .thenReturn(Optional.of(activeCategoryWithParentGeneral));
```

Assert the offering and linked transaction both persist `GENERAL/TITHE`. Add mismatch, inactive parent, inactive child, and missing category failures.

- [ ] **Step 2: Run backend offering tests and verify RED**

Run:

```bash
cd backend
mvn -Dtest=OfferingServiceTest test
```

Expected: failures because the request and service still use `fundCategory`.

- [ ] **Step 3: Implement backend offering hierarchy**

Normalize Fund and Category independently. Validate the Category's `parentCode`
equals the normalized Fund. Update create, edit, response mapping, and linked
income synchronization.

- [ ] **Step 4: Run backend offering tests and verify GREEN**

Run the Step 2 command.

- [ ] **Step 5: Write frontend offering tests and verify RED**

Test:

- Funds load from `OFFERING_FUND`.
- Selecting `GENERAL` loads `OFFERING_CATEGORY?parentCode=GENERAL`.
- Changing Fund clears Category.
- Save payload contains `fundCode` and `categoryCode`.
- List and filter distinguish Fund from Category.

Run:

```bash
cd frontend
npm test -- src/views/OfferingsView.test.ts
```

Expected: fail against the combined field.

- [ ] **Step 6: Implement the offering UI and API**

Add separate Fund and Category selects, dependent loading, two list columns, and
two filters. Preserve the existing edit/delete UI pattern and pagination.

- [ ] **Step 7: Run focused offering tests and verify GREEN**

Run the Step 5 command.

---

### Task 4: Offering Budgets And Finance Summaries

**Files:**
- Modify: `backend/src/main/java/com/church/operation/service/BudgetService.java`
- Modify: `backend/src/test/java/com/church/operation/service/BudgetServiceTest.java`
- Modify: `frontend/src/api/budgets.ts`
- Modify: `frontend/src/views/BudgetsView.vue`
- Modify: `frontend/src/views/BudgetsView.test.ts`
- Modify: `frontend/src/views/FinanceView.vue`
- Create: `frontend/src/views/FinanceView.test.ts`

**Interfaces:**
- Offering-income budget `category` is Fund and `subCategory` is Offering Category.
- Expense budget semantics remain unchanged.
- Daily offering income summary key is date + Fund + Category.

- [ ] **Step 1: Write backend failing budget tests**

Require active `OFFERING_FUND` and active `OFFERING_CATEGORY` with the selected
Fund as parent. Verify both fields are required for `OFFERING_INCOME`; preserve
existing expense and carry-over behavior.

- [ ] **Step 2: Run backend budget tests and verify RED**

Run:

```bash
cd backend
mvn -Dtest=BudgetServiceTest test
```

Expected: failures because offering budgets ignore sub-category.

- [ ] **Step 3: Implement budget hierarchy validation**

Split validation by budget type:

```java
case OFFERING_INCOME -> validateOfferingHierarchy(category, subCategory);
case EXPENSE -> validateFinancialHierarchy(category, subCategory);
case CARRY_OVER -> clearCategoryFields();
```

- [ ] **Step 4: Run backend budget tests and verify GREEN**

Run the Step 2 command.

- [ ] **Step 5: Write frontend budget and finance failing tests**

Test filtered Offering Category options and payload fields in Budgets. Test that
Finance creates separate daily summary rows when date and Fund match but
Offering Category differs.

- [ ] **Step 6: Run focused frontend tests and verify RED**

Run:

```bash
cd frontend
npm test -- src/views/BudgetsView.test.ts src/views/FinanceView.test.ts
```

- [ ] **Step 7: Implement budget and finance UI behavior**

For offering budgets, label `category` as Fund and `subCategory` as Category,
and load `OFFERING_CATEGORY` by selected Fund. Change the finance summary key to:

```ts
`${transaction.transactionDate}:${transaction.category}:${transaction.subCategory ?? ''}`
```

- [ ] **Step 8: Run focused frontend tests and verify GREEN**

Run the Step 6 command.

---

### Task 5: Offering Reports

**Files:**
- Modify: `backend/src/main/java/com/church/operation/dto/WeeklyOfferingReportRow.java`
- Modify: `backend/src/main/java/com/church/operation/dto/MemberOfferingSummaryReportRow.java`
- Modify: `backend/src/main/java/com/church/operation/service/ReportService.java`
- Modify: `backend/src/main/java/com/church/operation/rest/ReportController.java`
- Modify: `backend/src/test/java/com/church/operation/service/ReportServiceTest.java`
- Modify: `backend/src/test/java/com/church/operation/rest/ReportControllerTest.java`
- Modify: `frontend/src/api/reports.ts`
- Modify: `frontend/src/views/ReportsView.vue`
- Modify: `frontend/src/views/ReportsView.test.ts`

**Interfaces:**
- Weekly and member-summary requests accept optional `fundCode` and `categoryCode`.
- Rows and CSV exports include both fields.
- Existing inclusive end-date and offering-number ordering remain unchanged.

- [ ] **Step 1: Write failing report tests**

Verify independent Fund and Category filters, hierarchy fields in grouping keys,
and deterministic sorting by existing keys followed by Fund and Category.
Verify the controller accepts both query parameters.

- [ ] **Step 2: Run backend report tests and verify RED**

Run:

```bash
cd backend
mvn -Dtest=ReportServiceTest,ReportControllerTest test
```

- [ ] **Step 3: Implement backend report hierarchy**

Replace combined-field matching and grouping with Fund and Category. Keep tax
receipt aggregation unchanged.

- [ ] **Step 4: Run backend report tests and verify GREEN**

Run the Step 2 command.

- [ ] **Step 5: Write frontend report tests and verify RED**

Test two dependent filters, two table/CSV columns, and request parameters.

- [ ] **Step 6: Implement frontend reports and verify GREEN**

Run:

```bash
cd frontend
npm test -- src/views/ReportsView.test.ts
```

Expected: all report tests pass.

---

### Task 6: Fiscal Archive And Reference Deletion Compatibility

**Files:**
- Modify: `backend/src/main/java/com/church/operation/entity/FiscalArchiveRegistry.java`
- Modify: `backend/src/main/java/com/church/operation/service/FiscalArchiveService.java`
- Modify: `backend/src/main/java/com/church/operation/service/ReferenceDataDeletionService.java`
- Modify: `backend/src/test/java/com/church/operation/service/FiscalArchiveServiceTest.java`
- Modify: `backend/src/test/java/com/church/operation/service/FiscalArchiveRoundTripIntegrationTest.java`
- Modify: `backend/src/test/java/com/church/operation/service/ReferenceDataDeletionServiceTest.java`

**Interfaces:**
- Registry adds `offeringFunds` and `offeringCategories`.
- Legacy `fundCategories` remains readable for old archive registries.
- Restore maps legacy archive offerings to `GENERAL/<legacy value>`.

- [ ] **Step 1: Write failing archive and deletion tests**

Verify:

- archive preview captures both hierarchy levels;
- cleanup registry stores both sets;
- restore validates Fund and parent-matching Category;
- a legacy archive restores beneath `GENERAL`;
- Fund deletion is blocked by child categories and domain records;
- Category deletion is blocked by offerings, income, budgets, and cleaned archives;
- Committee Code deletion has no false domain dependency.

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```bash
cd backend
mvn -Dtest=FiscalArchiveServiceTest,FiscalArchiveRoundTripIntegrationTest,ReferenceDataDeletionServiceTest test
```

- [ ] **Step 3: Implement archive registry and validation changes**

Collect Fund and Category separately. During legacy restore, populate missing
new fields before validation and merge. Preserve the old registry field for
backward compatibility.

- [ ] **Step 4: Implement deletion dependencies**

Map:

```text
OFFERING_FUND -> offering.fundCode, transaction.category, budget.category,
                 offering-category.parentCode, archive.offeringFunds
OFFERING_CATEGORY -> offering.categoryCode, transaction.subCategory,
                     budget.subCategory, archive.offeringCategories
```

Require ADMIN in every deletion path.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the Step 2 command.

---

### Task 7: Cross-Feature Verification And Documentation

**Files:**
- Modify: `backend/src/test/java/com/church/operation/V1WorkflowIntegrationTest.java`
- Modify: `docs/superpowers/specs/2026-07-07-church-operations-app-design.md`
- Modify: `docs/design-docs/church-app-ui-spec.md`
- Modify: `.superpowers/sdd/progress.md`

**Interfaces:**
- The lifecycle test proves migration, offering creation, linked income, budget,
  report, fiscal archive/restore, and full backup/restore in one MongoDB flow.

- [ ] **Step 1: Complete the lifecycle regression scenario**

Assert:

1. legacy combined data migrates to `GENERAL/TITHE`;
2. ADMIN can maintain Committee Code and immutable identity is enforced;
3. TREASURER can read active values but cannot maintain them;
4. a new offering and linked income store the hierarchy;
5. an offering budget stores the hierarchy;
6. reports expose Fund and Category;
7. fiscal archive/restore preserves the hierarchy;
8. full backup/restore preserves exact collection and GridFS state.

- [ ] **Step 2: Run all backend tests**

Run:

```bash
cd backend
mvn test
```

Expected: zero failures and zero errors.

- [ ] **Step 3: Run all frontend tests**

Run:

```bash
cd frontend
npm test
```

Expected: zero failed test files and zero failed tests.

- [ ] **Step 4: Build production artifacts**

Run:

```bash
cd frontend
npm run build
cd ..
docker compose config --quiet
docker compose build
```

Expected: all commands exit zero.

- [ ] **Step 5: Start and probe the Docker stack**

Run:

```bash
docker compose up -d
docker compose ps
curl -fsS http://localhost:5173/
curl -fsS http://localhost:8080/api/church-information
```

Expected: MongoDB, backend, and frontend are Up; both HTTP requests return 200.

- [ ] **Step 6: Update specifications and progress**

Replace old combined `OFFERING_FUND_CATEGORY` behavior in the master design and
UI specification. Record this enhancement, exact test counts, production build,
Docker build, and runtime probes in `.superpowers/sdd/progress.md`.

- [ ] **Step 7: Final change review**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors; all intended changes remain uncommitted.

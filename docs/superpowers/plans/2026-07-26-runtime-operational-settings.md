# Runtime Operational Church Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an ADMIN change the church time zone, fiscal-year start month, list page size, and data-operation expiry from Church Settings with no server restart.

**Architecture:** Extend the existing singleton `ChurchSettings` document and API, then add a `RuntimeOperationalSettings` resolver that combines saved overrides with existing server defaults on every operation. Backend date, fiscal-period, pagination, and restore-session consumers read that resolver; the Vue shared church-information store makes public UI values reactive while administrative expiry remains private.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, Spring Data MongoDB, Vue 3, TypeScript, Vue Router, JUnit 5, Mockito, Vitest, Vue Testing Library

## Global Constraints

- Changes take effect without restarting the application.
- Only `ADMIN` may view, save, or reset Church Settings.
- `.env` and `application.yml` values remain field-by-field defaults.
- Time zones must be valid IANA `ZoneId` values.
- Fiscal-year start month must be from 1 through 12.
- List page size must be from 5 through 100.
- Data-operation expiry must be positive and no longer than two hours.
- Existing prepared data operations retain their assigned expiration timestamp.
- Closed yearly reports, issued tax receipts, and existing fiscal archives remain immutable.
- Work directly on `develop` and execute inline; do not create a worktree or dispatch subagents.

---

### Task 1: Persist And Resolve Runtime Operational Settings

**Files:**
- Modify: `backend/src/main/java/com/church/operation/entity/ChurchSettings.java`
- Create: `backend/src/main/java/com/church/operation/service/RuntimeOperationalSettings.java`
- Modify: `backend/src/main/java/com/church/operation/config/ChurchInformationProperties.java`
- Modify: `backend/src/main/java/com/church/operation/config/FiscalYearProperties.java`
- Create: `backend/src/main/java/com/church/operation/config/ChurchTimeZoneProperties.java`
- Modify: `backend/src/main/java/com/church/operation/ChurchOperationApplication.java`
- Test: `backend/src/test/java/com/church/operation/service/RuntimeOperationalSettingsTest.java`

**Interfaces:**
- Consumes: `ChurchSettingsRepository.findById(ChurchSettings.SINGLETON_ID)`, `ChurchInformationProperties.ui().listPageSize()`, `FiscalYearProperties.startMonth()`, `DataManagementProperties.operationExpiry()`.
- Produces: `RuntimeOperationalSettings.EffectiveSettings(ZoneId timeZone, int fiscalYearStartMonth, int listPageSize, Duration dataOperationExpiry)` and convenience methods `timeZone()`, `fiscalYearStartMonth()`, `listPageSize()`, and `dataOperationExpiry()`.

- [ ] **Step 1: Write resolver tests that prove saved values override defaults field by field**

```java
@Test
void resolvesSavedOperationalOverridesWithoutRestart() {
    ChurchSettings saved = new ChurchSettings();
    saved.setTimeZone("America/Vancouver");
    saved.setFiscalYearStartMonth(4);
    saved.setListPageSize(50);
    saved.setDataOperationExpiry(Duration.ofMinutes(60));
    when(repository.findById(ChurchSettings.SINGLETON_ID)).thenReturn(Optional.of(saved));

    assertThat(resolver.timeZone()).isEqualTo(ZoneId.of("America/Vancouver"));
    assertThat(resolver.fiscalYearStartMonth()).isEqualTo(4);
    assertThat(resolver.listPageSize()).isEqualTo(50);
    assertThat(resolver.dataOperationExpiry()).isEqualTo(Duration.ofMinutes(60));
}

@Test
void fallsBackPerFieldWhenSavedOverrideIsMissing() {
    ChurchSettings saved = new ChurchSettings();
    saved.setListPageSize(40);
    when(repository.findById(ChurchSettings.SINGLETON_ID)).thenReturn(Optional.of(saved));

    assertThat(resolver.timeZone()).isEqualTo(ZoneId.of("America/Toronto"));
    assertThat(resolver.fiscalYearStartMonth()).isEqualTo(1);
    assertThat(resolver.listPageSize()).isEqualTo(40);
    assertThat(resolver.dataOperationExpiry()).isEqualTo(Duration.ofMinutes(30));
}
```

- [ ] **Step 2: Run the resolver tests and confirm they fail because the fields and resolver do not exist**

Run: `cd backend && mvn -Dtest=RuntimeOperationalSettingsTest test`

Expected: compilation fails for `RuntimeOperationalSettings` and the new `ChurchSettings` accessors.

- [ ] **Step 3: Add nullable persisted fields and implement field-by-field runtime resolution**

```java
@Service
public class RuntimeOperationalSettings {
    private final ChurchSettingsRepository repository;
    private final ZoneId defaultTimeZone;
    private final int defaultFiscalYearStartMonth;
    private final int defaultListPageSize;
    private final Duration defaultDataOperationExpiry;

    public ZoneId timeZone() {
        return saved().map(ChurchSettings::getTimeZone).filter(StringUtils::hasText)
            .map(ZoneId::of).orElse(defaultTimeZone);
    }

    public int fiscalYearStartMonth() {
        return saved().map(ChurchSettings::getFiscalYearStartMonth)
            .orElse(defaultFiscalYearStartMonth);
    }

    public int listPageSize() {
        return saved().map(ChurchSettings::getListPageSize).orElse(defaultListPageSize);
    }

    public Duration dataOperationExpiry() {
        return saved().map(ChurchSettings::getDataOperationExpiry)
            .orElse(defaultDataOperationExpiry);
    }
}
```

Add `church.time-zone: ${CHURCH_TIME_ZONE:America/Toronto}` to `application.yml`, bind it through `ChurchTimeZoneProperties(String value)`, and register that configuration record in `ChurchOperationApplication`. Keep `TZ` in Docker Compose as the container default, but do not depend on JVM default-zone mutation.

- [ ] **Step 4: Run the resolver and existing configuration tests**

Run: `cd backend && mvn -Dtest=RuntimeOperationalSettingsTest,ChurchInformationPropertiesTest test`

Expected: all selected tests pass.

- [ ] **Step 5: Commit the persistence and resolver unit**

```bash
git add backend/src/main/java/com/church/operation/entity/ChurchSettings.java backend/src/main/java/com/church/operation/service/RuntimeOperationalSettings.java backend/src/main/java/com/church/operation/config/ChurchTimeZoneProperties.java backend/src/main/java/com/church/operation/config/ChurchInformationProperties.java backend/src/main/java/com/church/operation/config/FiscalYearProperties.java backend/src/main/java/com/church/operation/ChurchOperationApplication.java backend/src/main/resources/application.yml backend/src/test/java/com/church/operation/service/RuntimeOperationalSettingsTest.java
git commit -m "feat: resolve runtime operational settings"
```

### Task 2: Extend Church Settings Validation, API, And Audit

**Files:**
- Modify: `backend/src/main/java/com/church/operation/dto/ChurchSettingsSaveRequest.java`
- Modify: `backend/src/main/java/com/church/operation/dto/ChurchSettingsAdminResponse.java`
- Modify: `backend/src/main/java/com/church/operation/service/ChurchSettingsService.java`
- Modify: `backend/src/main/java/com/church/operation/service/SystemAuditService.java`
- Test: `backend/src/test/java/com/church/operation/service/ChurchSettingsServiceTest.java`
- Test: `backend/src/test/java/com/church/operation/rest/ChurchSettingsControllerTest.java`
- Test: `backend/src/test/java/com/church/operation/service/SystemAuditServiceTest.java`

**Interfaces:**
- Consumes: Task 1 persisted fields and `RuntimeOperationalSettings` effective values.
- Produces: request/response fields `timeZone`, `fiscalYearStartMonth`, `listPageSize`, and `dataOperationExpiryMinutes`.

- [ ] **Step 1: Add failing service and controller tests for valid values and each validation boundary**

```java
@Test
void savesValidatedOperationalSettings() {
    ChurchSettingsSaveRequest request = requestWithOperations(
        "America/Vancouver", 4, 50, 60
    );

    ChurchSettingsAdminResponse result = service.save(actor, request, null, null);

    assertThat(result.timeZone()).isEqualTo("America/Vancouver");
    assertThat(result.fiscalYearStartMonth()).isEqualTo(4);
    assertThat(result.listPageSize()).isEqualTo(50);
    assertThat(result.dataOperationExpiryMinutes()).isEqualTo(60);
}

@ParameterizedTest
@CsvSource({
    "Not/AZone,1,20,30",
    "America/Toronto,0,20,30",
    "America/Toronto,1,4,30",
    "America/Toronto,1,20,121"
})
void rejectsInvalidOperationalSettings(String zone, int month, int pageSize, long expiry) {
    assertThatThrownBy(() -> service.save(
        actor, requestWithOperations(zone, month, pageSize, expiry), null, null
    )).isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: Run focused tests and verify they fail**

Run: `cd backend && mvn -Dtest=ChurchSettingsServiceTest,ChurchSettingsControllerTest,SystemAuditServiceTest test`

Expected: compilation or assertion failures for missing operational fields.

- [ ] **Step 3: Extend DTOs and make save atomic after complete validation**

```java
private OperationalValues validateOperations(ChurchSettingsSaveRequest request) {
    ZoneId.of(request.timeZone());
    if (request.fiscalYearStartMonth() < 1 || request.fiscalYearStartMonth() > 12) {
        throw new IllegalArgumentException("Fiscal year start month must be between 1 and 12.");
    }
    if (request.listPageSize() < 5 || request.listPageSize() > 100) {
        throw new IllegalArgumentException("List page size must be between 5 and 100.");
    }
    if (request.dataOperationExpiryMinutes() < 1 || request.dataOperationExpiryMinutes() > 120) {
        throw new IllegalArgumentException("Data operation expiry must be between 1 and 120 minutes.");
    }
    return new OperationalValues(
        request.timeZone(), request.fiscalYearStartMonth(), request.listPageSize(),
        Duration.ofMinutes(request.dataOperationExpiryMinutes())
    );
}
```

Validate text, branding, and operational values before saving the document or replacing GridFS files. Reset continues deleting the singleton document so all four values return to server defaults.

- [ ] **Step 4: Add safe audit change flags**

Record booleans such as `timeZoneChanged`, `fiscalYearStartMonthChanged`, `listPageSizeChanged`, and `dataOperationExpiryChanged`; do not write the whole settings object into audit metadata.

- [ ] **Step 5: Run focused API, service, authorization, and audit tests**

Run: `cd backend && mvn -Dtest=ChurchSettingsServiceTest,ChurchSettingsControllerTest,SystemAuditServiceTest test`

Expected: all selected tests pass, including existing ADMIN-only controller tests.

- [ ] **Step 6: Commit the validated API unit**

```bash
git add backend/src/main/java/com/church/operation/dto backend/src/main/java/com/church/operation/service/ChurchSettingsService.java backend/src/main/java/com/church/operation/service/SystemAuditService.java backend/src/test/java/com/church/operation/service/ChurchSettingsServiceTest.java backend/src/test/java/com/church/operation/rest/ChurchSettingsControllerTest.java backend/src/test/java/com/church/operation/service/SystemAuditServiceTest.java
git commit -m "feat: manage runtime operational settings"
```

### Task 3: Apply Dynamic Time Zone And Fiscal-Year Boundaries

**Files:**
- Modify: `backend/src/main/java/com/church/operation/service/DashboardService.java`
- Modify: `backend/src/main/java/com/church/operation/service/ReportService.java`
- Modify: `backend/src/main/java/com/church/operation/service/QuarterlyOfferingReportService.java`
- Modify: `backend/src/main/java/com/church/operation/service/QuarterlyExpenditureReportService.java`
- Modify: `backend/src/main/java/com/church/operation/service/YearlyOfferingReportService.java`
- Modify: `backend/src/main/java/com/church/operation/service/YearlyExpenditureReportService.java`
- Modify: `backend/src/main/java/com/church/operation/service/YearlyFinancialExcelService.java`
- Modify: `backend/src/main/java/com/church/operation/service/FiscalArchiveService.java`
- Modify: `backend/src/main/java/com/church/operation/service/YearEndClosingService.java`
- Modify: `backend/src/main/java/com/church/operation/service/TaxReceiptService.java`
- Test: corresponding service tests under `backend/src/test/java/com/church/operation/service/`

**Interfaces:**
- Consumes: `RuntimeOperationalSettings.timeZone()` and `fiscalYearStartMonth()` from Task 1.
- Produces: all live calculations resolve settings at method invocation time rather than constructor time.

- [ ] **Step 1: Add failing tests that change the saved settings between two calls on the same service instance**

```java
@Test
void recalculatesFiscalPeriodAfterRuntimeSettingChanges() {
    when(runtimeSettings.fiscalYearStartMonth()).thenReturn(1, 4);

    DashboardResponse januaryStart = service.dashboard(actor);
    DashboardResponse aprilStart = service.dashboard(actor);

    assertThat(januaryStart.fiscalStart()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(aprilStart.fiscalStart()).isEqualTo(LocalDate.of(2026, 4, 1));
}

@Test
void convertsMemberRegistrationDateUsingRuntimeZone() {
    when(runtimeSettings.timeZone()).thenReturn(
        ZoneId.of("America/Toronto"), ZoneId.of("America/Vancouver")
    );
    // Use an Instant near midnight so the two zones produce different local dates.
}
```

Add equivalent boundary assertions for yearly reports, quarterly reports, archive preview, and closing eligibility. Existing closed-report download tests must still assert that saved bytes are returned unchanged.

- [ ] **Step 2: Run the affected service tests and verify failures show constructor-bound settings**

Run: `cd backend && mvn -Dtest=DashboardServiceTest,ReportServiceTest,QuarterlyOfferingReportServiceTest,QuarterlyExpenditureReportServiceTest,YearlyOfferingReportServiceTest,YearlyExpenditureReportServiceTest,FiscalArchiveServiceTest,YearEndClosingServiceTest,TaxReceiptServiceTest test`

Expected: new sequential-call assertions fail because the services still use immutable configuration or system-default time zones.

- [ ] **Step 3: Replace static fiscal and default-zone reads with the runtime resolver**

```java
int startMonth = runtimeSettings.fiscalYearStartMonth();
ZoneId zone = runtimeSettings.timeZone();
LocalDate localDate = persistedInstant.atZone(zone).toLocalDate();
```

Read each effective value once near the start of a public operation and pass it into private helpers. Do not query MongoDB repeatedly inside loops. Preserve saved closing workbooks, issued receipt snapshots, and archive bytes.

- [ ] **Step 4: Run all date-boundary and immutable-history tests**

Run: `cd backend && mvn -Dtest=DashboardServiceTest,ReportServiceTest,QuarterlyOfferingReportServiceTest,QuarterlyExpenditureReportServiceTest,YearlyOfferingReportServiceTest,YearlyExpenditureReportServiceTest,YearlyFinancialExcelServiceTest,FiscalArchiveServiceTest,YearEndClosingServiceTest,TaxReceiptServiceTest test`

Expected: all selected tests pass.

- [ ] **Step 5: Commit the dynamic period calculations**

```bash
git add backend/src/main/java/com/church/operation/service/DashboardService.java backend/src/main/java/com/church/operation/service/ReportService.java backend/src/main/java/com/church/operation/service/QuarterlyOfferingReportService.java backend/src/main/java/com/church/operation/service/QuarterlyExpenditureReportService.java backend/src/main/java/com/church/operation/service/YearlyOfferingReportService.java backend/src/main/java/com/church/operation/service/YearlyExpenditureReportService.java backend/src/main/java/com/church/operation/service/YearlyFinancialExcelService.java backend/src/main/java/com/church/operation/service/FiscalArchiveService.java backend/src/main/java/com/church/operation/service/YearEndClosingService.java backend/src/main/java/com/church/operation/service/TaxReceiptService.java backend/src/test/java/com/church/operation/service/DashboardServiceTest.java backend/src/test/java/com/church/operation/service/ReportServiceTest.java backend/src/test/java/com/church/operation/service/QuarterlyOfferingReportServiceTest.java backend/src/test/java/com/church/operation/service/QuarterlyExpenditureReportServiceTest.java backend/src/test/java/com/church/operation/service/YearlyOfferingReportServiceTest.java backend/src/test/java/com/church/operation/service/YearlyExpenditureReportServiceTest.java backend/src/test/java/com/church/operation/service/YearlyFinancialExcelServiceTest.java backend/src/test/java/com/church/operation/service/FiscalArchiveServiceTest.java backend/src/test/java/com/church/operation/service/YearEndClosingServiceTest.java backend/src/test/java/com/church/operation/service/TaxReceiptServiceTest.java
git commit -m "feat: apply runtime fiscal and time-zone settings"
```

### Task 4: Apply Dynamic Data-Operation Expiry And Public Information

**Files:**
- Modify: `backend/src/main/java/com/church/operation/service/DataOperationStore.java`
- Modify: `backend/src/main/java/com/church/operation/rest/ChurchInformationController.java`
- Create: `backend/src/test/java/com/church/operation/service/DataOperationStoreTest.java`
- Test: `backend/src/test/java/com/church/operation/rest/ChurchInformationControllerTest.java`

**Interfaces:**
- Consumes: `RuntimeOperationalSettings.dataOperationExpiry()`, `timeZone()`, `fiscalYearStartMonth()`, and `listPageSize()`.
- Produces: public church-information fields `timeZone`, `fiscalYearStartMonth`, and effective `listPageSize`.

- [ ] **Step 1: Write failing expiry and public-response tests**

```java
@Test
void newOperationsUseCurrentExpiryWhileExistingOperationKeepsItsTimestamp() {
    when(runtimeSettings.dataOperationExpiry()).thenReturn(
        Duration.ofMinutes(30), Duration.ofMinutes(60)
    );

    Operation first = store.create(actorId, archiveOne, sessionOne, 1, 2, 3);
    store.complete(first);
    Operation second = store.create(actorId, archiveTwo, sessionTwo, 1, 2, 3);

    assertThat(first.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
    assertThat(second.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(60)));
}
```

Controller assertions must verify the effective zone, month, and page size come from the runtime resolver.

- [ ] **Step 2: Run focused tests and verify they fail**

Run: `cd backend && mvn -Dtest=DataOperationStoreTest,ChurchInformationControllerTest test`

Expected: assertions fail because expiry and public values still come from immutable properties.

- [ ] **Step 3: Resolve expiry only when creating an operation**

```java
Instant expiresAt = clock.instant().plus(runtimeSettings.dataOperationExpiry());
```

Keep `expiresAt` stored on each in-memory operation. Never recompute it in `require()` or `expireOperations()`.

- [ ] **Step 4: Return effective public operational values**

Extend the church-information response with runtime values while keeping data-operation expiry absent from this public API.

- [ ] **Step 5: Run focused tests**

Run: `cd backend && mvn -Dtest=DataOperationStoreTest,ChurchInformationControllerTest test`

Expected: all selected tests pass.

- [ ] **Step 6: Commit runtime expiry and public response**

```bash
git add backend/src/main/java/com/church/operation/service/DataOperationStore.java backend/src/main/java/com/church/operation/rest/ChurchInformationController.java backend/src/test/java/com/church/operation/service/DataOperationStoreTest.java backend/src/test/java/com/church/operation/rest/ChurchInformationControllerTest.java
git commit -m "feat: expose effective operational settings"
```

### Task 5: Add Operational Controls And Reactive Pagination

**Files:**
- Modify: `frontend/src/api/churchSettings.ts`
- Modify: `frontend/src/api/churchInformation.ts`
- Modify: `frontend/src/views/SystemAdministrationView.vue`
- Modify: `frontend/src/composables/usePagination.ts`
- Test: `frontend/src/views/SystemAdministrationView.test.ts`
- Test: `frontend/src/stores/churchInformationStore.test.ts`
- Test: list view tests under `frontend/src/views/`

**Interfaces:**
- Consumes: Task 2 ADMIN fields and Task 4 public response fields.
- Produces: reactive settings controls and pagination that follows `churchInformationState.value.listPageSize`.

- [ ] **Step 1: Add failing frontend tests for controls, save payload, reset, and live pagination**

```ts
it('saves operational church settings and refreshes shared information', async () => {
  await openChurchSettings();
  await user.selectOptions(screen.getByLabelText('Church time zone'), 'America/Vancouver');
  await user.selectOptions(screen.getByLabelText('Fiscal year starts'), '4');
  await user.clear(screen.getByLabelText('List page size'));
  await user.type(screen.getByLabelText('List page size'), '50');
  await user.selectOptions(screen.getByLabelText('Data operation expiry'), '60');
  await user.click(screen.getByRole('button', { name: 'Save church settings' }));

  expect(saveChurchSettings).toHaveBeenCalledWith(
    expect.objectContaining({
      timeZone: 'America/Vancouver',
      fiscalYearStartMonth: 4,
      listPageSize: 50,
      dataOperationExpiryMinutes: 60,
    }),
    undefined,
    undefined,
  );
});
```

Add a pagination test that begins on page 2, updates shared `listPageSize`, and asserts page 1 and the new row range immediately.

- [ ] **Step 2: Run focused frontend tests and verify they fail**

Run: `cd frontend && npm test -- --run src/views/SystemAdministrationView.test.ts src/stores/churchInformationStore.test.ts src/views/MembersView.test.ts`

Expected: missing controls/types and non-reactive pagination assertions fail.

- [ ] **Step 3: Extend TypeScript contracts and Church Settings form**

```ts
export interface ChurchSettingsDraft {
  // Existing church information fields remain here.
  timeZone: string;
  fiscalYearStartMonth: number;
  listPageSize: number;
  dataOperationExpiryMinutes: number;
}
```

Use a curated IANA selector including Canadian zones, a January-to-December selector, a numeric page-size input with `min="5"` and `max="100"`, and expiry options 10, 20, 30, 60, and 120 minutes. Place them in an `Operational Settings` subsection without creating nested cards.

- [ ] **Step 4: Make pagination watch shared church information**

```ts
watch(
  () => churchInformationState.value?.listPageSize,
  (value) => {
    if (value && value >= 5 && value <= 100) {
      pageSize.value = value;
      currentPage.value = 1;
    }
  },
  { immediate: true },
);
```

Replace direct `getChurchInformation()` calls in `usePagination` with the shared store load so all currently mounted lists react to a successful settings save.

- [ ] **Step 5: Run System Administration, store, and all paginated view tests**

Run: `cd frontend && npm test -- --run src/views/SystemAdministrationView.test.ts src/stores/churchInformationStore.test.ts src/views/MembersView.test.ts src/views/OfferingsView.test.ts src/views/FinanceView.test.ts src/views/BudgetsView.test.ts src/views/ReferenceDataView.test.ts src/views/ReportsView.test.ts`

Expected: all selected tests pass.

- [ ] **Step 6: Commit the frontend operational settings unit**

```bash
git add frontend/src/api/churchSettings.ts frontend/src/api/churchInformation.ts frontend/src/views/SystemAdministrationView.vue frontend/src/composables/usePagination.ts frontend/src/views/SystemAdministrationView.test.ts frontend/src/stores/churchInformationStore.test.ts frontend/src/views/MembersView.test.ts frontend/src/views/OfferingsView.test.ts frontend/src/views/FinanceView.test.ts frontend/src/views/BudgetsView.test.ts frontend/src/views/ReferenceDataView.test.ts frontend/src/views/ReportsView.test.ts
git commit -m "feat: configure operational church settings"
```

### Task 6: Verify Backup Compatibility, Documentation, And Complete Builds

**Files:**
- Modify: `backend/src/test/java/com/church/operation/service/MongoDatabaseRoundTripIntegrationTest.java`
- Modify: `docs/user-guide/build_user_guides.py`
- Modify: `docs/user-guide/build_korean_pdf.py`
- Modify: `docs/user-guide/Church Operations User Guide.docx`
- Modify: `docs/user-guide/Church Operations User Guide.pdf`
- Modify: `docs/user-guide/교회운영 메뉴얼.docx`
- Modify: `docs/user-guide/교회운영 메뉴얼.pdf`
- Modify: `docs/feature-inventory.md`
- Modify: `docs/feature-inventory-ko.md`

**Interfaces:**
- Consumes: completed runtime operational settings behavior from Tasks 1 through 5.
- Produces: verified backup round trip, current manuals, and release-ready builds.

- [ ] **Step 1: Extend the database round-trip test with all four persisted fields**

```java
Document settings = new Document("_id", ChurchSettings.SINGLETON_ID)
    .append("timeZone", "America/Vancouver")
    .append("fiscalYearStartMonth", 4)
    .append("listPageSize", 50)
    .append("dataOperationExpiry", "PT1H");
```

After restore, assert all four values match the exported values and server defaults are not substituted.

- [ ] **Step 2: Run backend integration and full test suites**

Run: `cd backend && mvn -Dtest=MongoDatabaseRoundTripIntegrationTest test`

Expected: Docker-backed round-trip test passes.

Run: `cd backend && mvn test`

Expected: the complete backend suite passes with zero failures and zero errors.

- [ ] **Step 3: Run complete frontend tests and production build**

Run: `cd frontend && npm test -- --run`

Expected: the complete frontend suite passes.

Run: `cd frontend && npm run build`

Expected: TypeScript checking and Vite production build succeed.

- [ ] **Step 4: Update both manuals and feature inventories**

Document ADMIN access, each control, immediate behavior, server-default reset, historical-document immutability, and the rule that existing prepared restore operations keep their original expiry.

- [ ] **Step 5: Regenerate and visually verify English and Korean documents**

Run: `python docs/user-guide/build_user_guides.py`

Run: `python docs/user-guide/build_korean_pdf.py`

Render both DOCX/PDF outputs with the existing document and PDF verification workflow. Inspect changed System Administration pages at full size and contact sheets for every page. Confirm there is no clipped text, overlap, blank page, or missing Korean glyph.

- [ ] **Step 6: Check the complete feature diff**

Run: `git diff --check HEAD~5..HEAD -- backend/src frontend/src docs/superpowers docs/feature-inventory.md docs/feature-inventory-ko.md docs/user-guide/build_user_guides.py docs/user-guide/build_korean_pdf.py`

Expected: no whitespace errors in source or documentation.

- [ ] **Step 7: Commit documentation and verification updates**

```bash
git add backend/src/test/java/com/church/operation/service/MongoDatabaseRoundTripIntegrationTest.java docs/user-guide docs/feature-inventory.md docs/feature-inventory-ko.md
git commit -m "docs: explain runtime operational settings"
```

### Task 7: Runtime Smoke Test

**Files:**
- No source changes expected.

**Interfaces:**
- Consumes: completed application images and the existing local MongoDB volume.
- Produces: runtime evidence that settings change without restarting app containers.

- [ ] **Step 1: Rebuild only backend and frontend containers**

Run: `docker compose up --build -d backend frontend`

Expected: backend and frontend become healthy while the existing MongoDB container and volume remain untouched.

- [ ] **Step 2: Verify health and effective public settings**

Run: `curl -fsS http://localhost:8080/actuator/health`

Expected: `{"status":"UP"}`.

Run: `curl -fsS http://localhost:8080/api/church-information`

Expected: response includes valid `timeZone`, `fiscalYearStartMonth`, and `listPageSize` values.

- [ ] **Step 3: Verify ADMIN Church Settings in the browser**

Log in as an ADMIN, open System Administration > Church Settings, change the page size, save, and confirm an already open list immediately returns to page 1 with the new number of rows. Change it back to its original value before ending the smoke test.

- [ ] **Step 4: Verify runtime fiscal and time-zone refresh without restart**

Change the fiscal month and time zone, revisit Dashboard and Reports without logging out, and confirm displayed fiscal ranges use the new values. Restore the original values before ending the smoke test.

- [ ] **Step 5: Confirm no MongoDB upgrade occurred**

Run: `docker compose ps`

Expected: MongoDB remains on its existing version. The separate MongoDB 6-to-8 migration requires explicit approval and is not part of this plan.

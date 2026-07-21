# Year-End Closing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add independent, password-confirmed year-end closing and reopening for yearly Offering and Expenditure workbooks, with immutable MongoDB GridFS snapshots and closing-aware downloads.

**Architecture:** A `YearEndClosingService` coordinates authorization, password verification, fiscal-date rules, workbook generation, GridFS storage, metadata versioning, reopening, auditing, and download selection. `YearlyFinancialExcelService` remains the renderer but accepts explicit lifecycle metadata. Existing yearly download URLs delegate to the coordinator, while new status/close/reopen endpoints drive the Vue UI.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, Spring Data MongoDB/GridFS, Apache POI, MongoDB 6+, JUnit 5, Mockito, Vue 3, TypeScript, Vitest, Vue Testing Library.

## Global Constraints

- Keep Java at version 21 in Maven and Docker.
- Close `OFFERING` and `EXPENDITURE` independently.
- Only `ADMIN` and `TREASURER` may close or reopen.
- Keep yearly download access for `ADMIN`, `TREASURER`, `PASTOR`, and `VIEWER`.
- Verify the signed-in user's own current password on every close and reopen.
- Allow closing only after the configured fiscal end date has passed.
- Generate open/reopened downloads live; return only GridFS bytes while closed.
- Reopening retains metadata, GridFS bytes, and audit history.
- Closed Korean titles remove `안`; draft/reopened titles retain `예산안`.
- Do not modify Official Tax Receipt behavior.
- Never persist or log plaintext passwords.

## File Map

**Create backend:**

- `util/YearEndReportType.java`, `util/YearEndClosingStatus.java`
- `entity/YearEndClosing.java`, `repo/YearEndClosingRepository.java`
- `dto/YearEndClosingRequest.java`, `dto/YearEndClosingReportStatus.java`
- `dto/YearEndClosingStatusResponse.java`, `dto/YearlyWorkbookDownload.java`
- `exception/YearEndClosingConflictException.java`
- `service/YearlyWorkbookLifecycle.java`, `service/YearEndSnapshotStore.java`
- `service/YearEndClosingService.java`
- `service/YearEndSnapshotStoreTest.java`, `service/YearEndClosingServiceTest.java`

**Modify backend:**

- `service/YearlyFinancialExcelService.java`
- `rest/ReportController.java`, `exception/GlobalExceptionHandler.java`
- `util/SystemAuditOperation.java`, `service/SystemAuditService.java`
- `service/YearlyFinancialExcelServiceTest.java`, `service/SystemAuditServiceTest.java`
- `rest/ReportControllerTest.java`

**Modify frontend:**

- `frontend/src/api/http.ts`, `frontend/src/api/http.test.ts`
- `frontend/src/api/reports.ts`
- `frontend/src/views/ReportsView.vue`, `frontend/src/views/ReportsView.test.ts`

---

### Task 1: Render Workbook Lifecycle Identity

**Files:**
- Create: `backend/src/main/java/com/church/operation/util/YearEndClosingStatus.java`
- Create: `backend/src/main/java/com/church/operation/service/YearlyWorkbookLifecycle.java`
- Modify: `backend/src/main/java/com/church/operation/service/YearlyFinancialExcelService.java`
- Test: `backend/src/test/java/com/church/operation/service/YearlyFinancialExcelServiceTest.java`

**Interfaces:**
- Produces: `render(YearlyFinancialReport, YearlyWorkbookLifecycle)`.
- Preserves: `render(YearlyFinancialReport)` as a not-closed overload.

- [ ] **Step 1: Write the failing renderer tests**

```java
@Test
void rendersNeverClosedWorkbookAsDraft() throws Exception {
    byte[] bytes = service("/branding/church_logo.png")
        .render(offeringReport(), YearlyWorkbookLifecycle.notClosed());
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
        XSSFSheet sheet = workbook.getSheet("Offering income");
        assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
            .isEqualTo("2026년도 수입 결산 및 예산안");
        assertThat(sheet.getRow(2).getCell(0).getStringCellValue())
            .isEqualTo("DRAFT - Year-end closing not completed");
    }
}

@Test
void rendersClosedWorkbookWithFinalTitleAndVersion() throws Exception {
    byte[] bytes = service("/branding/church_logo.png").render(
        offeringReport(),
        YearlyWorkbookLifecycle.closed(Instant.parse("2026-07-21T19:42:00Z"), 2)
    );
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
        XSSFSheet sheet = workbook.getSheet("Offering income");
        assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
            .isEqualTo("2026년도 수입 결산 및 예산");
        assertThat(sheet.getRow(2).getCell(0).getStringCellValue())
            .contains("YEAR-END CLOSED").contains("Version 2");
        assertThat(sheet.getMergedRegions()).contains(new CellRangeAddress(2, 2, 0, 7));
    }
}

@Test
void rendersReopenedWorkbookAsDraftWithTimestamp() throws Exception {
    byte[] bytes = service("/branding/church_logo.png").render(
        expenditureReport(),
        YearlyWorkbookLifecycle.reopened(Instant.parse("2026-07-22T13:15:00Z"))
    );
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
        XSSFSheet sheet = workbook.getSheet("Expenditure");
        assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
            .isEqualTo("2026년도 지출 결산 및 예산안");
        assertThat(sheet.getRow(2).getCell(0).getStringCellValue())
            .contains("DRAFT - Reopened");
    }
}
```

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -Dtest=YearlyFinancialExcelServiceTest test`

Expected: compilation fails because the lifecycle types and overload are absent.

- [ ] **Step 3: Add lifecycle types**

```java
public enum YearEndClosingStatus { NOT_CLOSED, CLOSED, REOPENED }

public record YearlyWorkbookLifecycle(
    YearEndClosingStatus status,
    Instant eventAt,
    Integer version
) {
    public static YearlyWorkbookLifecycle notClosed() {
        return new YearlyWorkbookLifecycle(YearEndClosingStatus.NOT_CLOSED, null, null);
    }
    public static YearlyWorkbookLifecycle reopened(Instant at) {
        return new YearlyWorkbookLifecycle(YearEndClosingStatus.REOPENED, at, null);
    }
    public static YearlyWorkbookLifecycle closed(Instant at, int version) {
        return new YearlyWorkbookLifecycle(YearEndClosingStatus.CLOSED, at, version);
    }
    public boolean finalized() { return status == YearEndClosingStatus.CLOSED; }
}
```

- [ ] **Step 4: Implement lifecycle-aware rendering**

Delegate the old `render` overload to `notClosed()`. Pass lifecycle into
`createTopRows`, remove only the final `안` for closed titles, and use row 3:

```java
String suffix = lifecycle.finalized()
    ? report.titleSuffix().replaceFirst("안$", "")
    : report.titleSuffix();
titleRow.getCell(0).setCellValue(report.fiscalYear() + "년도 " + suffix);

Row statusRow = sheet.createRow(2);
createCells(statusRow, styles.status());
statusRow.getCell(0).setCellValue(statusText(lifecycle));
sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 7));
```

Format timestamps with `DateTimeFormatter.ofPattern("MMM d, uuuu h:mm a",
Locale.ENGLISH).withZone(ZoneId.systemDefault())`. Do not alter formulas, table
rows, logo, print area, or repeated rows.

- [ ] **Step 5: Verify GREEN**

Run: `cd backend && mvn -Dtest=YearlyFinancialExcelServiceTest test`

Expected: all renderer tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/church/operation/util/YearEndClosingStatus.java \
  backend/src/main/java/com/church/operation/service/YearlyWorkbookLifecycle.java \
  backend/src/main/java/com/church/operation/service/YearlyFinancialExcelService.java \
  backend/src/test/java/com/church/operation/service/YearlyFinancialExcelServiceTest.java
git commit -m "feat: mark yearly workbook closing state"
```

---

### Task 2: Persist Closing Metadata And GridFS Snapshots

**Files:**
- Create: `backend/src/main/java/com/church/operation/util/YearEndReportType.java`
- Create: `backend/src/main/java/com/church/operation/entity/YearEndClosing.java`
- Create: `backend/src/main/java/com/church/operation/repo/YearEndClosingRepository.java`
- Create: `backend/src/main/java/com/church/operation/service/YearEndSnapshotStore.java`
- Test: `backend/src/test/java/com/church/operation/service/YearEndSnapshotStoreTest.java`

**Interfaces:**
- Produces: `store`, checksum-verifying `load`, and compensating `delete`.
- Produces: active/latest repository queries for Task 3.

- [ ] **Step 1: Write failing GridFS tests**

```java
@Test
void storesWorkbookWithChecksumAndMetadata() {
    byte[] bytes = "closed-workbook".getBytes(StandardCharsets.UTF_8);
    ObjectId id = new ObjectId();
    when(gridFsTemplate.store(any(InputStream.class), anyString(), eq(EXCEL_TYPE), any(Document.class)))
        .thenReturn(id);

    StoredSnapshot result = store.store(
        bytes, "yearly-offerings-2026-closed-v1.xlsx", 2026, OFFERING, 1
    );

    assertThat(result.gridFsFileId()).isEqualTo(id.toHexString());
    assertThat(result.fileSize()).isEqualTo(bytes.length);
    assertThat(result.checksum()).hasSize(64);
}

@Test
void rejectsChecksumMismatch() {
    YearEndClosing closing = closingWithChecksum("incorrect");
    stubGridFsBytes("changed".getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> store.load(closing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Closed yearly workbook checksum verification failed.");
}
```

Also test missing file, exact-byte load, and deletion by ObjectId.

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -Dtest=YearEndSnapshotStoreTest test`

Expected: compilation fails because the model/store do not exist.

- [ ] **Step 3: Add persistence types**

```java
public enum YearEndReportType { OFFERING, EXPENDITURE }
```

Create `YearEndClosing` as `@Document("year_end_closings")` with all fields from
the approved specification. Add a unique compound index on `fiscalYear`,
`reportType`, and `version`. Mark `activeKey` unique+sparse and construct it as:

```java
public static String activeKey(int year, YearEndReportType type) {
    return year + ":" + type.name();
}
```

- [ ] **Step 4: Add repository queries**

```java
Optional<YearEndClosing> findByFiscalYearAndReportTypeAndActiveTrue(int year, YearEndReportType type);
Optional<YearEndClosing> findFirstByFiscalYearAndReportTypeOrderByVersionDesc(int year, YearEndReportType type);
```

- [ ] **Step 5: Implement GridFS storage**

Follow `MemberImageService`'s ObjectId query pattern. Return:

```java
public record StoredSnapshot(String gridFsFileId, long fileSize, String checksum) {}
```

Store fiscal year, report type, version, checksum, content type, and timestamp
as GridFS metadata. `load` must find the exact ID, read all bytes, recalculate
SHA-256, compare using `MessageDigest.isEqual`, and fail without fallback if
missing or corrupt.

- [ ] **Step 6: Verify GREEN**

Run: `cd backend && mvn -Dtest=YearEndSnapshotStoreTest test`

Expected: all snapshot tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/church/operation/util/YearEndReportType.java \
  backend/src/main/java/com/church/operation/entity/YearEndClosing.java \
  backend/src/main/java/com/church/operation/repo/YearEndClosingRepository.java \
  backend/src/main/java/com/church/operation/service/YearEndSnapshotStore.java \
  backend/src/test/java/com/church/operation/service/YearEndSnapshotStoreTest.java
git commit -m "feat: store year-end workbook snapshots"
```

---

### Task 3: Implement Closing, Reopening, And Download Selection

**Files:**
- Create: `backend/src/main/java/com/church/operation/dto/YearEndClosingRequest.java`
- Create: `backend/src/main/java/com/church/operation/dto/YearEndClosingReportStatus.java`
- Create: `backend/src/main/java/com/church/operation/dto/YearEndClosingStatusResponse.java`
- Create: `backend/src/main/java/com/church/operation/dto/YearlyWorkbookDownload.java`
- Create: `backend/src/main/java/com/church/operation/exception/YearEndClosingConflictException.java`
- Create: `backend/src/main/java/com/church/operation/service/YearEndClosingService.java`
- Modify: `backend/src/main/java/com/church/operation/exception/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/church/operation/util/SystemAuditOperation.java`
- Modify: `backend/src/main/java/com/church/operation/service/SystemAuditService.java`
- Test: `backend/src/test/java/com/church/operation/service/YearEndClosingServiceTest.java`
- Test: `backend/src/test/java/com/church/operation/service/SystemAuditServiceTest.java`

**Interfaces:**
- Produces: `status(Member, int)`, `close(Member, type, request)`,
  `reopen(Member, type, request)`, and `download(Member, type, int)`.

- [ ] **Step 1: Write failing security and fiscal-date tests**

```java
@Test
void rejectsWrongCurrentPasswordBeforeGenerating() {
    when(memberRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
    when(passwordEncoder.matches("wrong", admin.getPasswordHash())).thenReturn(false);

    assertThatThrownBy(() -> service.close(
        admin, OFFERING, new YearEndClosingRequest(2025, "wrong")
    )).isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Current password is incorrect.");
    verifyNoInteractions(yearlyOfferingReportService, excelService, snapshotStore);
}
```

Cover Admin/Treasurer success; Pastor/Viewer/Membership/Member rejection;
freshly loaded inactive/locked account rejection; invalid year; and
`closeEligible == LocalDate.now(clock).isAfter(fiscalEnd)`.

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -Dtest=YearEndClosingServiceTest test`

Expected: compilation fails because lifecycle service/DTOs do not exist.

- [ ] **Step 3: Add exact DTO contracts**

```java
public record YearEndClosingRequest(@Min(2000) int fiscalYear, @NotBlank String currentPassword) {}

public record YearEndClosingReportStatus(
    YearEndReportType reportType, YearEndClosingStatus status, Integer version, Instant eventAt
) {}

public record YearEndClosingStatusResponse(
    int fiscalYear, LocalDate fiscalEndDate, boolean closeEligible,
    YearEndClosingReportStatus offering, YearEndClosingReportStatus expenditure
) {}

public record YearlyWorkbookDownload(byte[] content, String filename) {}
```

- [ ] **Step 4: Implement status and fresh-account password validation**

Use `YearlyFinancialPeriod.from(year, fiscalYearProperties.startMonth())`.
Status allows existing report roles. Mutations reload actor by ID, then verify
active, unlocked, Admin/Treasurer role, and `passwordEncoder.matches`.

- [ ] **Step 5: Write failing close/version/cleanup tests**

Test version 1, finalized renderer metadata, snapshot metadata, active key,
actor/time fields, audit success, already-closed conflict, and this cleanup:

```java
when(snapshotStore.store(any(), anyString(), eq(2025), eq(OFFERING), eq(1)))
    .thenReturn(new StoredSnapshot("orphan", 123, "checksum"));
when(repository.save(any())).thenThrow(new DuplicateKeyException("active"));

assertThatThrownBy(() -> service.close(admin, OFFERING, request))
    .isInstanceOf(YearEndClosingConflictException.class);
verify(snapshotStore).delete("orphan");
```

- [ ] **Step 6: Implement closing**

Calculate `version = latest.map(v -> v.getVersion() + 1).orElse(1)`. Render
once with `YearlyWorkbookLifecycle.closed(now, version)`, store GridFS, then save
active metadata. On save failure, delete only the newly stored GridFS file,
audit failure, and map duplicate state to a conflict.

- [ ] **Step 7: Write failing reopen/history tests**

Assert reopening requires an active record and valid password, sets status
`REOPENED`, `active=false`, `activeKey=null`, actor/time, retains file metadata,
never deletes GridFS, audits the action, and allows a later version 2 closing.

- [ ] **Step 8: Implement reopening**

Update only lifecycle fields on the active record. Do not alter or delete the
snapshot fields.

- [ ] **Step 9: Write failing download-selection tests**

Test:

- No history: live `notClosed()` rendering and `-draft.xlsx`.
- Latest reopened: live `reopened(at)` rendering and `-draft.xlsx`.
- Active closed: `snapshotStore.load` only and `-closed-vN.xlsx`.
- Missing/corrupt closed snapshot: explicit failure with no live generation.

- [ ] **Step 10: Implement download selection**

Enforce report access before closed loads. For live downloads, use the existing
Offering/Expenditure builders and renderer. Return authoritative filenames in
`YearlyWorkbookDownload`.

- [ ] **Step 11: Add conflict and audit support**

Add `YEAR_END_CLOSE` and `YEAR_END_REOPEN`. Allow audit metadata keys
`reportType`, `version`, `closingId`, `gridFsFileId`, `checksum`, and `fileSize`.
Continue rejecting keys containing password/hash/bytes/content. Map
`YearEndClosingConflictException` to HTTP 409 code `YEAR_END_CLOSING_CONFLICT`.

- [ ] **Step 12: Verify GREEN**

Run: `cd backend && mvn -Dtest=YearEndClosingServiceTest,SystemAuditServiceTest test`

Expected: all lifecycle and audit tests pass.

- [ ] **Step 13: Commit**

```bash
git add backend/src/main/java/com/church/operation/dto/YearEndClosingRequest.java \
  backend/src/main/java/com/church/operation/dto/YearEndClosingReportStatus.java \
  backend/src/main/java/com/church/operation/dto/YearEndClosingStatusResponse.java \
  backend/src/main/java/com/church/operation/dto/YearlyWorkbookDownload.java \
  backend/src/main/java/com/church/operation/exception/YearEndClosingConflictException.java \
  backend/src/main/java/com/church/operation/exception/GlobalExceptionHandler.java \
  backend/src/main/java/com/church/operation/service/YearEndClosingService.java \
  backend/src/main/java/com/church/operation/service/SystemAuditService.java \
  backend/src/main/java/com/church/operation/util/SystemAuditOperation.java \
  backend/src/test/java/com/church/operation/service/YearEndClosingServiceTest.java \
  backend/src/test/java/com/church/operation/service/SystemAuditServiceTest.java
git commit -m "feat: add year-end closing lifecycle"
```

---

### Task 4: Expose Closing APIs And Preserve Download URLs

**Files:**
- Modify: `backend/src/main/java/com/church/operation/rest/ReportController.java`
- Test: `backend/src/test/java/com/church/operation/rest/ReportControllerTest.java`

**Interfaces:**
- Consumes: Task 3 lifecycle service.
- Produces: status, close, reopen, and closing-aware downloads.

- [ ] **Step 1: Write failing controller tests**

Cover:

```text
GET  /api/reports/yearly-closing-status?fiscalYear=2025
POST /api/reports/yearly-closing/OFFERING/close
POST /api/reports/yearly-closing/OFFERING/reopen
POST /api/reports/yearly-closing/EXPENDITURE/close
POST /api/reports/yearly-closing/EXPENDITURE/reopen
```

Post body: `{"fiscalYear":2025,"currentPassword":"secret-value"}`.
Assert authenticated principal delegation, validation 400, conflict 409, JSON
status shape, dynamic filename, Excel content type, and exact bytes.

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -Dtest=ReportControllerTest test`

Expected: endpoint tests fail.

- [ ] **Step 3: Implement endpoints**

Inject `YearEndClosingService`. Keep existing yearly download URLs, but replace
their direct build/render logic:

```java
YearlyWorkbookDownload download = yearEndClosingService.download(
    actor(authentication), YearEndReportType.OFFERING, fiscalYear
);
return excelAttachment(download.content(), download.filename());
```

Use `@Valid @RequestBody YearEndClosingRequest` for close/reopen.

- [ ] **Step 4: Verify GREEN**

Run: `cd backend && mvn -Dtest=ReportControllerTest,YearEndClosingServiceTest test`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/church/operation/rest/ReportController.java \
  backend/src/test/java/com/church/operation/rest/ReportControllerTest.java
git commit -m "feat: expose year-end closing APIs"
```

---

### Task 5: Add Frontend API Contracts And Server Filenames

**Files:**
- Modify: `frontend/src/api/http.ts`
- Modify: `frontend/src/api/http.test.ts`
- Modify: `frontend/src/api/reports.ts`

**Interfaces:**
- Produces: `getBlobResponse`, status/close/reopen calls, and filename-aware downloads.

- [ ] **Step 1: Write a failing HTTP helper test**

```typescript
it('returns headers for filename-aware downloads', async () => {
  fetchMock.mockResolvedValue(new Response('xlsx', {
    status: 200,
    headers: { 'Content-Disposition': 'attachment; filename=yearly-offerings-2025-closed-v1.xlsx' },
  }));
  const response = await getBlobResponse('/api/reports/yearly-offerings.xlsx?fiscalYear=2025');
  expect(response.headers.get('Content-Disposition')).toContain('closed-v1.xlsx');
});
```

- [ ] **Step 2: Verify RED**

Run: `cd frontend && npm test -- --run src/api/http.test.ts`

Expected: `getBlobResponse` is missing.

- [ ] **Step 3: Export response-preserving GET**

```typescript
export function getBlobResponse(path: string): Promise<Response> {
  return request(path);
}
```

- [ ] **Step 4: Add reports API contracts**

```typescript
export type YearEndReportType = 'OFFERING' | 'EXPENDITURE';
export type YearEndClosingStatus = 'NOT_CLOSED' | 'CLOSED' | 'REOPENED';
export interface YearEndClosingReportStatus {
  reportType: YearEndReportType;
  status: YearEndClosingStatus;
  version?: number;
  eventAt?: string;
}
export interface YearEndClosingStatusResponse {
  fiscalYear: number;
  fiscalEndDate: string;
  closeEligible: boolean;
  offering: YearEndClosingReportStatus;
  expenditure: YearEndClosingReportStatus;
}
export interface YearlyWorkbookDownload { blob: Blob; filename: string; }
```

Add `getYearEndClosingStatus`, `closeYearEndReport`, and
`reopenYearEndReport`. Change only yearly download calls to return
`YearlyWorkbookDownload`, parsing quoted/unquoted `Content-Disposition` and
using a neutral fallback only when absent.

- [ ] **Step 5: Add filename parsing tests and verify GREEN**

Run: `cd frontend && npm test -- --run src/api/http.test.ts`

Expected: helper tests pass for quoted, unquoted, and missing filenames.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/http.ts frontend/src/api/http.test.ts frontend/src/api/reports.ts
git commit -m "feat: add year-end closing client API"
```

---

### Task 6: Build Closing Controls And Password Dialog

**Files:**
- Modify: `frontend/src/views/ReportsView.vue`
- Modify: `frontend/src/views/ReportsView.test.ts`

**Interfaces:**
- Consumes: Task 5 client API.
- Produces: independent statuses/actions and password confirmation UI.

- [ ] **Step 1: Write failing status and role tests**

Mock one open and one closed state. Assert Admin/Treasurer see lifecycle actions,
Viewer sees statuses/downloads but no lifecycle actions, datetime is beside the
report name, and premature closing is disabled with fiscal-end explanation.

- [ ] **Step 2: Verify RED**

Run: `cd frontend && npm test -- --run src/views/ReportsView.test.ts`

Expected: lifecycle UI assertions fail.

- [ ] **Step 3: Load and display status**

Load status when the yearly tab activates and after valid fiscal-year changes.
Use a sequence guard so stale responses cannot overwrite a newer year. Format
`eventAt` with `Intl.DateTimeFormat`; display `Not closed`, `Closed <datetime>`,
or `Reopened <datetime>` beside each report name.

- [ ] **Step 4: Write failing dialog tests**

Assert clicking either lifecycle action opens `role="dialog"` with action,
report type, fiscal year, password input, Confirm, and Cancel. Cancel makes no
request. Confirm sends the password to the correct action and clears it after
success or failure.

- [ ] **Step 5: Implement dialog and independent busy states**

Use an in-page modal, not `window.prompt`:

```typescript
const closingDialog = reactive({
  open: false,
  reportType: 'OFFERING' as YearEndReportType,
  action: 'close' as 'close' | 'reopen',
  password: '',
  busy: false,
});
```

On success, close and clear the dialog, then refresh both statuses. On failure,
keep the dialog available for correction, clear password, and show the backend
message. Disable only the affected report row while processing.

- [ ] **Step 6: Update download tests**

Mock `{ blob, filename }` and assert the server-provided draft/closed filename
is used. Preserve independent Offering/Expenditure download busy states.

- [ ] **Step 7: Verify GREEN**

Run: `cd frontend && npm test -- --run src/views/ReportsView.test.ts src/api/http.test.ts`

Expected: all focused frontend tests pass.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/views/ReportsView.vue frontend/src/views/ReportsView.test.ts
git commit -m "feat: add year-end closing report controls"
```

---

### Task 7: Full Verification And Progress Update

**Files:**
- Modify: `.superpowers/sdd/progress.md`

**Interfaces:**
- Consumes: Tasks 1-6.
- Produces: verified completion evidence.

- [ ] **Step 1: Run all backend tests**

Run: `cd backend && mvn test`

Expected: zero failures and zero errors.

- [ ] **Step 2: Run all frontend tests**

Run: `cd frontend && npm test -- --run`

Expected: all Vitest suites pass.

- [ ] **Step 3: Build production artifacts**

Run: `cd frontend && npm run build`

Expected: Vue/TypeScript build exits 0.

Run: `docker compose build backend`

Expected: the Java 21 backend image builds successfully.

- [ ] **Step 4: Inspect workbook variants**

Render Offering and Expenditure in draft, closed, and reopened states. Verify
titles, row-3 status, version, logo, formulas, merges, columns, and print setup.

- [ ] **Step 5: Exercise the local MongoDB lifecycle**

1. Close Offering for a completed fiscal year.
2. Confirm Expenditure remains open.
3. Download Offering twice and verify identical SHA-256 bytes.
4. Change source data and verify closed Offering bytes remain identical.
5. Reopen and verify the next Offering download is a live draft.
6. Close again and verify version 2.
7. Repeat for Expenditure.
8. Confirm all GridFS versions remain stored and audit events contain no password.

- [ ] **Step 6: Update progress**

Record the completed lifecycle, GridFS snapshots, roles, password checks,
workbook identity, UI, and verification outcomes in `.superpowers/sdd/progress.md`.

- [ ] **Step 7: Run repository checks**

Run: `git diff --check`

Expected: no whitespace errors.

Run: `git status --short`

Expected: only the intentional progress update remains.

- [ ] **Step 8: Commit**

```bash
git add .superpowers/sdd/progress.md
git commit -m "docs: record year-end closing completion"
```

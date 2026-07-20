# Yearly Financial Excel Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add separately downloadable yearly Offering and Expenditure Excel workbooks containing selected-year budget, fiscal-year actual, and following-year budget in the approved sample-matching layout.

**Architecture:** Add yearly-specific DTOs and aggregation services so missing-next-budget semantics remain explicit and quarterly behavior stays stable. A yearly Apache POI renderer owns the A:H layout, formulas, and formatting; a small shared support class centralizes logo and print behavior for yearly and quarterly renderers. Existing Report controller and Vue Reports page expose both downloads under a Yearly Financial Report tab.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data MongoDB, Apache POI 5.5.1, JUnit 5, Mockito, Spring MockMvc, Vue 3.5, TypeScript, Vitest, Vue Testing Library, `@oai/artifact-tool`.

## Global Constraints

- Preserve packages under `java/com.church.operation`.
- Permit exactly `ADMIN`, `TREASURER`, `PASTOR`, and `VIEWER`.
- Interpret the selected year with `church.fiscal-year.start-month`.
- Offering actuals use `offeringSunday`; deleted offerings stay excluded.
- Expenditure actuals include only non-deleted `EXPENSE` transactions.
- Offering isolates `CARRY_OVER`; Expenditure isolates `CONTINGENCY`.
- Missing next-year budget records display `-` in both F and G; numeric zero is not missing.
- Offering row 4 uses `수입결산` and `수입대비`; Expenditure uses `지출결산` and `지출대비`.
- Use Letter landscape, Adjust to 100%, approved margins/footer, print area through H, and repeating rows 1:4.
- Do not alter quarterly calculations, layout, filenames, or endpoints.

## File Map

Create yearly DTOs `YearlyFinancialRow`, `YearlyFinancialGroup`, and `YearlyFinancialReport` in `backend/src/main/java/com/church/operation/dto/`. Create `YearlyFinancialPeriod`, two aggregation services, `FinancialExcelLayoutSupport`, and `YearlyFinancialExcelService` in `backend/src/main/java/com/church/operation/service/`. Add one focused test class per new service. Modify `QuarterlyFinancialExcelService`, `ReportController`, `frontend/src/api/reports.ts`, `ReportsView.vue`, and their existing tests.

---

### Task 1: Yearly Model, Fiscal Period, And Offering Aggregation

**Files:**
- Create: `backend/src/main/java/com/church/operation/dto/YearlyFinancialRow.java`
- Create: `backend/src/main/java/com/church/operation/dto/YearlyFinancialGroup.java`
- Create: `backend/src/main/java/com/church/operation/dto/YearlyFinancialReport.java`
- Create: `backend/src/main/java/com/church/operation/service/YearlyFinancialPeriod.java`
- Create: `backend/src/main/java/com/church/operation/service/YearlyOfferingReportService.java`
- Test: `backend/src/test/java/com/church/operation/service/YearlyOfferingReportServiceTest.java`

**Interfaces:**
- Consumes: current/next `BudgetRepository.findActiveByFiscalYear`, fiscal-range Offering query, Offering Fund/Category references, and `FiscalYearProperties`.
- Produces: `YearlyOfferingReportService.build(Member actor, int fiscalYear): YearlyFinancialReport`.
- DTO signatures:

```java
public record YearlyFinancialRow(
    String groupCode, String groupLabel, String itemCode, String itemLabel,
    BigDecimal currentBudget, BigDecimal actual,
    BigDecimal nextBudget, boolean nextBudgetPresent
) {}

public record YearlyFinancialGroup(
    int sequence, String groupCode, String groupLabel,
    List<YearlyFinancialRow> rows
) {}

public record YearlyFinancialReport(
    int fiscalYear, LocalDate fiscalStart, LocalDate fiscalEnd,
    List<YearlyFinancialGroup> groups,
    BigDecimal specialCurrentBudget, BigDecimal specialActual,
    BigDecimal specialNextBudget, boolean specialNextBudgetPresent,
    String sheetName, String titleSuffix,
    String actualHeader, String actualRatioHeader, String specialRowLabel
) {}
```

- [ ] **Step 1: Write failing Offering service tests**

Create tests for a July fiscal year, both budget years, actual aggregation, next-only rows, and carry-over separation:

```java
YearlyFinancialReport report = service(7).build(member(Role.TREASURER), 2026);
assertThat(report.fiscalStart()).isEqualTo(LocalDate.of(2026, 7, 1));
assertThat(report.fiscalEnd()).isEqualTo(LocalDate.of(2027, 6, 30));
YearlyFinancialRow tithe = findRow(report, "TITHE");
assertThat(tithe.currentBudget()).isEqualByComparingTo("12000");
assertThat(tithe.actual()).isEqualByComparingTo("900");
assertThat(tithe.nextBudget()).isEqualByComparingTo("13000");
assertThat(tithe.nextBudgetPresent()).isTrue();
assertThat(report.specialCurrentBudget()).isEqualByComparingTo("2500");
assertThat(report.specialActual()).isEqualByComparingTo("500");
```

Add a separate test where `TITHE` has no next-year record and `MISSIONS` has an entered zero record:

```java
assertThat(findRow(report, "TITHE").nextBudgetPresent()).isFalse();
assertThat(findRow(report, "MISSIONS").nextBudgetPresent()).isTrue();
assertThat(findRow(report, "MISSIONS").nextBudget()).isEqualByComparingTo(BigDecimal.ZERO);
```

Also cover all four authorized roles, rejected member roles, year below 2000, inactive reference labels/order, code fallback, malformed keys, null/non-positive actuals, and case-insensitive `CARRY_OVER`.

- [ ] **Step 2: Run the focused test and verify RED**

Run `cd backend && ./mvnw -Dtest=YearlyOfferingReportServiceTest test`.

Expected: compilation fails because the yearly records and service do not exist.

- [ ] **Step 3: Implement the model and Offering service**

Implement the period exactly:

```java
LocalDate start = LocalDate.of(fiscalYear, startMonth, 1);
LocalDate end = start.plusYears(1).minusDays(1);
```

Reject invalid year/month with `IllegalArgumentException("A valid fiscal year is required.")`. Aggregate with:

```java
Map<RowKey, BigDecimal> currentBudgets;
Map<RowKey, BigDecimal> actuals;
Map<RowKey, BigDecimal> nextBudgets;
Set<RowKey> nextBudgetKeys;
```

Filter `OFFERING_INCOME`, aggregate current and next `CARRY_OVER` separately, query actuals once for the inclusive range, union all normal keys, and sort with the quarterly service's deterministic reference comparator. Return metadata:

```java
"Offering income", "수입 결산 및 예산안", "수입결산", "수입대비", "전년도 이월금"
```

- [ ] **Step 4: Verify GREEN**

Run `cd backend && ./mvnw -Dtest=YearlyOfferingReportServiceTest test`.

Expected: all focused tests pass with zero failures/errors.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/church/operation/dto/YearlyFinancial*.java \
  backend/src/main/java/com/church/operation/service/YearlyFinancialPeriod.java \
  backend/src/main/java/com/church/operation/service/YearlyOfferingReportService.java \
  backend/src/test/java/com/church/operation/service/YearlyOfferingReportServiceTest.java
git commit -m "feat: aggregate yearly offering reports"
```

---

### Task 2: Expenditure Aggregation

**Files:**
- Create: `backend/src/main/java/com/church/operation/service/YearlyExpenditureReportService.java`
- Test: `backend/src/test/java/com/church/operation/service/YearlyExpenditureReportServiceTest.java`

**Interfaces:**
- Consumes: Task 1 model/period, both budget years, fiscal-range transaction query, Financial Category/Sub-category references.
- Produces: `YearlyExpenditureReportService.build(Member actor, int fiscalYear): YearlyFinancialReport`.

- [ ] **Step 1: Write failing Expenditure tests**

```java
YearlyFinancialReport report = service(1).build(member(Role.ADMIN), 2026);
YearlyFinancialRow office = findRow(report, "OFFICE");
assertThat(office.currentBudget()).isEqualByComparingTo("12000");
assertThat(office.actual()).isEqualByComparingTo("600");
assertThat(office.nextBudget()).isEqualByComparingTo("12500");
assertThat(report.specialActual()).isEqualByComparingTo("150");
assertThat(report.specialNextBudgetPresent()).isFalse();
assertThat(report.actualHeader()).isEqualTo("지출결산");
assertThat(report.actualRatioHeader()).isEqualTo("지출대비");
```

Cover `INCOME` exclusion, repository-deleted contract, null/non-positive records, current-only/actual-only/next-only rows, inactive references, code fallback, roles, invalid year, and case-insensitive `CONTINGENCY` combined across children.

- [ ] **Step 2: Verify RED**

Run `cd backend && ./mvnw -Dtest=YearlyExpenditureReportServiceTest test`.

Expected: compilation fails because the service does not exist.

- [ ] **Step 3: Implement the service**

Use the Task 1 map/presence model, filter `BudgetType.EXPENSE` and `FinancialTransactionType.EXPENSE`, and aggregate `CONTINGENCY` separately. Resolve the special label from all Financial Category references before normal-group filtering. Return:

```java
"Expenditure", "지출 결산 및 예산안", "지출결산", "지출대비", contingencyLabel
```

- [ ] **Step 4: Verify GREEN**

Run `cd backend && ./mvnw -Dtest=YearlyOfferingReportServiceTest,YearlyExpenditureReportServiceTest test`.

Expected: both aggregation suites pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/church/operation/service/YearlyExpenditureReportService.java \
  backend/src/test/java/com/church/operation/service/YearlyExpenditureReportServiceTest.java
git commit -m "feat: aggregate yearly expenditure reports"
```

---

### Task 3: Excel Renderer And Shared Layout Support

**Files:**
- Create: `backend/src/main/java/com/church/operation/service/FinancialExcelLayoutSupport.java`
- Create: `backend/src/main/java/com/church/operation/service/YearlyFinancialExcelService.java`
- Create: `backend/src/test/java/com/church/operation/service/YearlyFinancialExcelServiceTest.java`
- Modify: `backend/src/main/java/com/church/operation/service/QuarterlyFinancialExcelService.java:48-68,215-295`
- Modify: `backend/src/test/java/com/church/operation/service/QuarterlyFinancialExcelServiceTest.java`

**Interfaces:**
- Produces: `YearlyFinancialExcelService.render(YearlyFinancialReport): byte[]`.
- Shared support signatures:

```java
static void addLogo(XSSFWorkbook workbook, XSSFSheet sheet,
    ChurchInformationProperties properties, int firstColumn, int lastColumnExclusive);
static void configurePrint(XSSFWorkbook workbook, XSSFSheet sheet,
    int lastColumn, int finalRow);
```

- [ ] **Step 1: Write failing workbook tests**

Assert exact titles and headers:

```java
assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
    .isEqualTo("2026년도 수입 결산 및 예산안");
assertThat(rowValues(sheet.getRow(3))).containsExactly(
    "구 분", "항 목", "2026 예산", "수입결산", "수입대비",
    "2027 예산", "예산대비", "비고");
```

Assert detail formulas and missing data:

```java
assertThat(sheet.getRow(4).getCell(4).getCellFormula())
    .isEqualTo("IF(C5=0,\"-\",D5/C5)");
assertThat(sheet.getRow(4).getCell(6).getCellFormula())
    .isEqualTo("IF(OR(C5=0,NOT(ISNUMBER(F5))),\"-\",F5/C5)");
assertThat(sheet.getRow(missingRow).getCell(5).getStringCellValue()).isEqualTo("-");
assertThat(sheet.getRow(zeroEnteredRow).getCell(5).getNumericCellValue()).isZero();
```

Repeat title/header assertions for Expenditure. Assert A:H merges, subtotals, combined/special/final totals, formula evaluation, logo, print area, repeating rows, margins/footer, scale 100, fit false, and sample widths:

```java
double[] widths = {7.83203125, 28.83203125, 12.83203125, 12.83203125,
    8.83203125, 12.83203125, 8.83203125, 32.83203125};
```

Write representative files to `backend/target/yearly-offerings-preview.xlsx` and `backend/target/yearly-expenditures-preview.xlsx`.

- [ ] **Step 2: Verify RED**

Run `cd backend && ./mvnw -Dtest=YearlyFinancialExcelServiceTest test`.

Expected: compilation fails because renderer/support are absent.

- [ ] **Step 3: Extract shared support**

Move the quarterly aspect-ratio logo loader and Adjust-to-100 print setup into `FinancialExcelLayoutSupport`. Quarterly delegates with `(7, 10)` for logo and last print column `9`; yearly uses `(6, 8)` and last print column `7`. Preserve quarterly tests unchanged.

- [ ] **Step 4: Implement the yearly renderer**

Create eight cells per row, merge `A2:H2`, use the approved title/header metadata, sample dimensions and styles. For each detail:

```java
numeric(C, item.currentBudget());
numeric(D, item.actual());
formula(E, "IF(C" + r + "=0,\"-\",D" + r + "/C" + r + ")");
if (item.nextBudgetPresent()) numeric(F, item.nextBudget()); else text(F, "-");
formula(G, "IF(OR(C" + r + "=0,NOT(ISNUMBER(F" + r + "))),\"-\",F" + r + "/C" + r + ")");
```

For subtotal/combined/final F, use `SUM(...)` only when at least one contributing record is present; otherwise write `-`. G always uses the same type-safe formula. Use formulas for C/D totals, merge A:B on summary rows, wrap B, apply sample borders/fonts/underline/currency/percentage formats, and configure print A:H through the final row.

- [ ] **Step 5: Verify yearly and quarterly renderers**

Run `cd backend && ./mvnw -Dtest=YearlyFinancialExcelServiceTest,QuarterlyFinancialExcelServiceTest test`.

Expected: both suites pass and both preview files are non-empty.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/church/operation/service/FinancialExcelLayoutSupport.java \
  backend/src/main/java/com/church/operation/service/YearlyFinancialExcelService.java \
  backend/src/main/java/com/church/operation/service/QuarterlyFinancialExcelService.java \
  backend/src/test/java/com/church/operation/service/YearlyFinancialExcelServiceTest.java \
  backend/src/test/java/com/church/operation/service/QuarterlyFinancialExcelServiceTest.java
git commit -m "feat: render yearly financial Excel reports"
```

---

### Task 4: Download Endpoints

**Files:**
- Modify: `backend/src/main/java/com/church/operation/rest/ReportController.java:40-202`
- Modify: `backend/src/test/java/com/church/operation/rest/ReportControllerTest.java`

**Interfaces:**
- Produces `GET /api/reports/yearly-offerings.xlsx?fiscalYear=<year>`.
- Produces `GET /api/reports/yearly-expenditures.xlsx?fiscalYear=<year>`.

- [ ] **Step 1: Write failing controller tests**

Add yearly mocks and test status, XLSX content type, bytes, service calls, and filenames `yearly-offerings-2026.xlsx` and `yearly-expenditures-2026.xlsx`. Add a 400 test when an aggregation service rejects year 1999.

```java
mockMvc.perform(get("/api/reports/yearly-offerings.xlsx")
        .param("fiscalYear", "2026").principal(authentication(viewer)))
    .andExpect(status().isOk())
    .andExpect(header().string("Content-Disposition",
        "attachment; filename=yearly-offerings-2026.xlsx"))
    .andExpect(content().bytes(workbook));
```

- [ ] **Step 2: Verify RED**

Run `cd backend && ./mvnw -Dtest=ReportControllerTest test`.

Expected: setup or requests fail because yearly dependencies/endpoints are missing.

- [ ] **Step 3: Implement endpoints**

Add all three yearly services to the constructor. Each endpoint builds the report, renders it, and returns the existing XLSX content type with approved filename. Extract a private `excel(String filename, byte[] bytes)` helper only if quarterly tests remain byte-for-byte equivalent.

- [ ] **Step 4: Verify GREEN**

Run `cd backend && ./mvnw -Dtest=ReportControllerTest,YearlyOfferingReportServiceTest,YearlyExpenditureReportServiceTest test`.

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/church/operation/rest/ReportController.java \
  backend/src/test/java/com/church/operation/rest/ReportControllerTest.java
git commit -m "feat: expose yearly financial workbook downloads"
```

---

### Task 5: Reports API And UI

**Files:**
- Modify: `frontend/src/api/reports.ts:69-162`
- Modify: `frontend/src/views/ReportsView.vue:10-208,410-718,993-1024`
- Modify: `frontend/src/views/ReportsView.test.ts:1-205`

**Interfaces:**

```ts
export interface YearlyFinancialReportFilters { fiscalYear: number }
export function downloadYearlyOfferingReport(filters: YearlyFinancialReportFilters): Promise<Blob>;
export function downloadYearlyExpenditureReport(filters: YearlyFinancialReportFilters): Promise<Blob>;
```

- [ ] **Step 1: Write failing view tests**

Mock both API methods. Select `Yearly Financial Report`, set fiscal year 2026, download each workbook, and assert request objects and filenames. Test year validation, active styling, Viewer visibility, independent busy states, error display, and absence of Run report/Export CSV/summary/table.

```ts
await fireEvent.click(screen.getByRole('tab', { name: /yearly financial report/i }));
await fireEvent.update(screen.getByLabelText('Fiscal year'), '2026');
await fireEvent.click(screen.getByRole('button', { name: /download yearly offering excel/i }));
expect(yearlyOfferingMock).toHaveBeenCalledWith({ fiscalYear: 2026 });
expect(downloadedFilename).toBe('yearly-offerings-2026.xlsx');
```

- [ ] **Step 2: Verify RED**

Run `cd frontend && npm test -- ReportsView.test.ts`.

Expected: tests fail because yearly API/tab/actions are absent.

- [ ] **Step 3: Add API methods**

Use `getBlob(withQuery('/api/reports/yearly-offerings.xlsx', {...filters}))` and the matching expenditure endpoint.

- [ ] **Step 4: Add the yearly UI**

Extend the report ID union and add:

```ts
{
  id: 'yearly-financial',
  label: 'Yearly Financial Report',
  title: 'Yearly Financial Report',
  description: 'Download the fiscal-year offering and expenditure workbooks.',
}
```

Add shared fiscal-year state, separate busy states, two stacked download rows, validation `fiscalYear >= 2000`, approved filenames, and existing error handling. Introduce:

```ts
const isWorkbookDownloadReport = computed(() =>
  activeVisibleReportId.value === 'quarterly-financial'
    || activeVisibleReportId.value === 'yearly-financial');
```

Use it to hide Export CSV, Run report, summary, and table on both workbook tabs. Rename quarterly-specific download CSS classes to neutral financial-download names without visual change.

- [ ] **Step 5: Verify frontend**

Run `cd frontend && npm test -- ReportsView.test.ts && npm run build`.

Expected: focused tests pass and production build exits 0.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/reports.ts frontend/src/views/ReportsView.vue frontend/src/views/ReportsView.test.ts
git commit -m "feat: add yearly financial report downloads"
```

---

### Task 6: Full Verification And Visual Workbook QA

**Files:**
- Verify: `backend/target/yearly-offerings-preview.xlsx`
- Verify: `backend/target/yearly-expenditures-preview.xlsx`

- [ ] **Step 1: Run the complete backend suite**

Run `cd backend && ./mvnw test`.

Expected: Maven exits 0 with zero failures/errors.

- [ ] **Step 2: Run the complete frontend suite and build**

Run `cd frontend && npm test && npm run build`.

Expected: all Vitest tests pass and Vite build exits 0.

- [ ] **Step 3: Inspect both workbooks with artifact-tool**

Using the bundled Node runtime and `@oai/artifact-tool`, import each preview. Inspect `A1:H<final-row>` for values/formulas and scan for `#REF!|#DIV/0!|#VALUE!|#NAME?|#N/A`. Confirm titles, exact row-4 headers, numeric values, missing F dashes, G formulas, subtotal/final formulas, and sheet names. Keep the QA script temporary and uncommitted.

- [ ] **Step 4: Render and inspect both sheets**

Render every used range at scale 2. Verify visible configured logo, title underline, Korean glyphs, merged parent labels, unclipped wrapped text, aligned currency/percentages, special row, final total, and all content within A:H. For any defect, add a failing assertion where practical, apply the smallest fix, rerun focused tests, and rerender both sheets.

- [ ] **Step 5: Check repository hygiene**

```bash
git diff --check
git status --short
```

Expected: no whitespace errors and no unrelated files staged or modified by this work.

- [ ] **Step 6: Request final review and reverify**

Invoke `superpowers:requesting-code-review`. Resolve correctness findings, rerun affected focused tests, then repeat full backend tests, frontend tests/build, and both workbook renders before reporting completion.


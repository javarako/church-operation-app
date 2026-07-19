# Quarterly Financial Excel Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a quarterly expenditure Excel report beside the existing quarterly offering download, while sharing workbook layout, formulas, branding, and print behavior.

**Architecture:** Replace the offering-specific workbook DTOs and renderer with a neutral quarterly financial model and one shared Apache POI renderer. Keep offering and expenditure aggregation in separate services because their repositories, reference types, and special-row rules differ. The Reports UI shares calendar year and quarter inputs while exposing independent offering and expenditure download actions.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, Spring Data MongoDB, Apache POI 5.5.1, Vue 3, TypeScript, Vitest, Vue Testing Library, JUnit 5, Mockito.

## Global Constraints

- Calendar quarters remain Q1 January-March, Q2 April-June, Q3 July-September, and Q4 October-December.
- Fiscal budget year is derived from the fiscal period containing the selected quarter end.
- Report access is limited to `ADMIN`, `TREASURER`, `PASTOR`, and `VIEWER`.
- Expenditure uses only active `EXPENSE` budgets and non-deleted `EXPENSE` transactions.
- Expense category code `CONTINGENCY` is case-insensitive and combines all sub-categories into the special row.
- Offering fund code `CARRY_OVER` remains case-insensitive and combines into its special row.
- Zero values in C:H display blank; I displays `-` only when C is zero.
- Both workbooks retain the existing one-page landscape print layout.
- Both workbooks must visibly embed the configured `church.branding.log-path` image in H:J.
- Do not commit. The user will review and commit the combined uncommitted work.

---

### Task 1: Introduce The Shared Quarterly Financial Model

**Files:**
- Create: `backend/src/main/java/com/church/operation/dto/QuarterlyFinancialRow.java`
- Create: `backend/src/main/java/com/church/operation/dto/QuarterlyFinancialGroup.java`
- Create: `backend/src/main/java/com/church/operation/dto/QuarterlyFinancialReport.java`
- Create: `backend/src/main/java/com/church/operation/service/QuarterlyFinancialPeriod.java`
- Modify: `backend/src/main/java/com/church/operation/service/QuarterlyOfferingReportService.java`
- Modify: `backend/src/test/java/com/church/operation/service/QuarterlyOfferingReportServiceTest.java`
- Delete after migration: `backend/src/main/java/com/church/operation/dto/QuarterlyOfferingCategoryRow.java`
- Delete after migration: `backend/src/main/java/com/church/operation/dto/QuarterlyOfferingFundGroup.java`
- Delete after migration: `backend/src/main/java/com/church/operation/dto/QuarterlyOfferingReport.java`

**Interfaces:**
- Produces: `QuarterlyFinancialPeriod.from(int year, int quarter, int fiscalStartMonth)`.
- Produces: `QuarterlyFinancialReport` consumed by both aggregation services and the shared renderer.
- Changes: `QuarterlyOfferingReportService.build(Member, int, int)` returns `QuarterlyFinancialReport`.

- [ ] **Step 1: Add failing shared-model assertions to the offering tests**

Update the existing tests to require workbook metadata and generic group/row names:

```java
QuarterlyFinancialReport report = service(7).build(member(Role.VIEWER), 2026, 2);

assertThat(report.sheetName()).isEqualTo("Offering income");
assertThat(report.titleSuffix()).isEqualTo("수입");
assertThat(report.specialRowLabel()).isEqualTo("전년도 이월금");
assertThat(report.groups()).extracting(QuarterlyFinancialGroup::groupCode)
    .containsExactly("BUILDING", "GENERAL");
assertThat(report.specialBudget()).isEqualByComparingTo("2500");
assertThat(report.specialMonthlyActuals()).containsExactly(
    BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("999")
);
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run:

```bash
cd backend && mvn -Dtest=QuarterlyOfferingReportServiceTest test
```

Expected: compilation or assertion failure because the generic records and metadata do not exist.

- [ ] **Step 3: Implement the shared records and period calculation**

Create:

```java
public record QuarterlyFinancialRow(
    String groupCode,
    String groupLabel,
    String itemCode,
    String itemLabel,
    BigDecimal budget,
    List<BigDecimal> monthlyActuals,
    BigDecimal cumulativeActual
) {}
```

```java
public record QuarterlyFinancialGroup(
    int sequence,
    String groupCode,
    String groupLabel,
    List<QuarterlyFinancialRow> rows
) {}
```

```java
public record QuarterlyFinancialReport(
    int calendarYear,
    int quarter,
    LocalDate quarterStart,
    LocalDate quarterEnd,
    int fiscalBudgetYear,
    LocalDate fiscalStart,
    List<YearMonth> months,
    List<QuarterlyFinancialGroup> groups,
    BigDecimal specialBudget,
    List<BigDecimal> specialMonthlyActuals,
    BigDecimal specialCumulativeActual,
    String sheetName,
    String titleSuffix,
    String specialRowLabel
) {}
```

Implement `QuarterlyFinancialPeriod.from` with:

```java
public record QuarterlyFinancialPeriod(
    int calendarYear,
    int quarter,
    LocalDate quarterStart,
    LocalDate quarterEnd,
    int fiscalBudgetYear,
    LocalDate fiscalStart,
    List<YearMonth> months
) {
    public static QuarterlyFinancialPeriod from(int year, int quarter, int fiscalStartMonth) {
        if (year < 2000 || quarter < 1 || quarter > 4
            || fiscalStartMonth < 1 || fiscalStartMonth > 12) {
            throw new IllegalArgumentException("A valid calendar year and quarter are required.");
        }
        int firstMonth = ((quarter - 1) * 3) + 1;
        LocalDate quarterStart = LocalDate.of(year, firstMonth, 1);
        LocalDate quarterEnd = quarterStart.plusMonths(3).minusDays(1);
        int fiscalBudgetYear = quarterEnd.getMonthValue() >= fiscalStartMonth
            ? quarterEnd.getYear()
            : quarterEnd.getYear() - 1;
        LocalDate fiscalStart = LocalDate.of(fiscalBudgetYear, fiscalStartMonth, 1);
        return new QuarterlyFinancialPeriod(
            year,
            quarter,
            quarterStart,
            quarterEnd,
            fiscalBudgetYear,
            fiscalStart,
            List.of(
                YearMonth.from(quarterStart),
                YearMonth.from(quarterStart.plusMonths(1)),
                YearMonth.from(quarterStart.plusMonths(2))
            )
        );
    }
}
```

Validate `year >= 2000`, `quarter` from 1 through 4, and `fiscalStartMonth` from 1 through 12.

- [ ] **Step 4: Migrate offering aggregation to the generic model**

Preserve all current behavior, but map:

```java
new QuarterlyFinancialReport(
    period.calendarYear(),
    period.quarter(),
    period.quarterStart(),
    period.quarterEnd(),
    period.fiscalBudgetYear(),
    period.fiscalStart(),
    period.months(),
    groups,
    carryOverBudget,
    carryOverMonthlyActuals,
    carryOverCumulativeActual,
    "Offering income",
    "수입",
    "전년도 이월금"
);
```

Replace fund/category accessor names in tests with group/item accessors. Remove the old offering-specific records only after all references migrate.

- [ ] **Step 5: Run offering service tests and confirm GREEN**

Run:

```bash
cd backend && mvn -Dtest=QuarterlyOfferingReportServiceTest test
```

Expected: all offering aggregation, authorization, period, carry-over, and ordering tests pass.

---

### Task 2: Add Quarterly Expenditure Aggregation

**Files:**
- Create: `backend/src/main/java/com/church/operation/service/QuarterlyExpenditureReportService.java`
- Create: `backend/src/test/java/com/church/operation/service/QuarterlyExpenditureReportServiceTest.java`

**Interfaces:**
- Consumes: `BudgetRepository.findActiveByFiscalYear(int)`.
- Consumes: `FinancialTransactionRepository.findActiveByTransactionDateBetween(LocalDate, LocalDate)`.
- Consumes: `ReferenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType)`.
- Produces: `QuarterlyFinancialReport build(Member actor, int year, int quarter)`.

- [ ] **Step 1: Write failing period, role, and validation tests**

Assert:

```java
QuarterlyFinancialReport report = service(7).build(member(Role.VIEWER), 2026, 2);
assertThat(report.quarterStart()).isEqualTo(LocalDate.of(2026, 4, 1));
assertThat(report.quarterEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
assertThat(report.fiscalStart()).isEqualTo(LocalDate.of(2025, 7, 1));
assertThat(report.fiscalBudgetYear()).isEqualTo(2025);
assertThat(report.sheetName()).isEqualTo("Expenditure");
assertThat(report.titleSuffix()).isEqualTo("지출");
assertThat(report.specialRowLabel()).isEqualTo("CONTINGENCY");
```

Test all four report roles and rejection of `MEMBER`, year 1999, quarter 0, and quarter 5.

- [ ] **Step 2: Run the new test and confirm RED**

Run:

```bash
cd backend && mvn -Dtest=QuarterlyExpenditureReportServiceTest test
```

Expected: compilation failure because `QuarterlyExpenditureReportService` does not exist.

- [ ] **Step 3: Add failing aggregation and contingency tests**

Use:

```java
budget(EXPENSE, "ADMIN", "OFFICE", "12000");
budget(EXPENSE, "MISSION", "SUPPORT", "2400");
budget(EXPENSE, "CONTINGENCY", "GENERAL", "2500");
expense("2025-08-03", "ADMIN", "OFFICE", "300");
expense("2026-04-05", "ADMIN", "OFFICE", "100");
expense("2026-05-03", "ADMIN", "OFFICE", "200");
expense("2026-06-07", "PROPERTY", "REPAIR", "50");
expense("2026-06-14", "Contingency", "EMERGENCY", "999");
```

Assert:

```java
assertThat(report.groups()).extracting(QuarterlyFinancialGroup::groupCode)
    .containsExactly("PROPERTY", "ADMIN", "MISSION");
assertThat(report.groups()).noneMatch(group ->
    group.groupCode().equalsIgnoreCase("CONTINGENCY")
);
assertThat(report.specialBudget()).isEqualByComparingTo("2500");
assertThat(report.specialMonthlyActuals()).containsExactly(
    BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("999")
);
assertThat(report.specialCumulativeActual()).isEqualByComparingTo("999");
```

Also assert:

- Only `BudgetType.EXPENSE` budgets enter.
- Only `FinancialTransactionType.EXPENSE` transactions enter.
- Repository query range is fiscal start through quarter end.
- Null/non-positive values are ignored.
- Budget-only and transaction-only rows remain.
- Inactive references provide labels.
- Unknown codes fall back to stored codes.
- Category and sub-category order follows `sortOrder`, label, then code.

- [ ] **Step 4: Implement expenditure aggregation**

Follow the offering aggregation shape with:

```java
if (budget.getBudgetType() != BudgetType.EXPENSE) continue;
if (transaction.getType() != FinancialTransactionType.EXPENSE) continue;
```

Identify contingency before requiring a sub-category:

```java
if (isContingency(categoryCode)) {
    contingencyBudget = contingencyBudget.add(amountOrZero(budget.getBudget()));
    continue;
}
```

For transactions, collect the special actual before requiring a sub-category:

```java
if (isContingency(categoryCode)) {
    contingencyCumulative = contingencyCumulative.add(amount);
    if (!transactionDate.isBefore(period.quarterStart())) {
        int monthIndex = period.months().indexOf(YearMonth.from(transactionDate));
        if (monthIndex >= 0) {
            contingencyMonthly[monthIndex] = contingencyMonthly[monthIndex].add(amount);
        }
    }
    continue;
}
```

Use `FINANCIAL_CATEGORY` and `FINANCIAL_SUB_CATEGORY` references. Combine all
contingency sub-categories into the special row.

- [ ] **Step 5: Run expenditure tests and confirm GREEN**

Run:

```bash
cd backend && mvn -Dtest=QuarterlyExpenditureReportServiceTest test
```

Expected: all expenditure period, role, filtering, contingency, label, ordering, and aggregation tests pass.

---

### Task 3: Extract The Shared Workbook Renderer And Fix Logo Placement

**Files:**
- Create: `backend/src/main/java/com/church/operation/service/QuarterlyFinancialExcelService.java`
- Create: `backend/src/test/java/com/church/operation/service/QuarterlyFinancialExcelServiceTest.java`
- Delete after migration: `backend/src/main/java/com/church/operation/service/QuarterlyOfferingExcelService.java`
- Delete after migration: `backend/src/test/java/com/church/operation/service/QuarterlyOfferingExcelServiceTest.java`

**Interfaces:**
- Consumes: `QuarterlyFinancialReport`.
- Produces: `byte[] render(QuarterlyFinancialReport report)`.

- [ ] **Step 1: Write failing shared-renderer tests**

Create offering and expenditure fixtures and assert:

```java
byte[] offeringBytes = service().render(offeringReport());
byte[] expenseBytes = service().render(expenditureReport());

try (XSSFWorkbook workbook = workbook(expenseBytes)) {
    Sheet sheet = workbook.getSheet("Expenditure");
    assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
        .isEqualTo("2026 년도 2분기 지출");
    assertThat(sheet.getRow(contingencyRow).getCell(0).getStringCellValue())
        .isEqualTo("CONTINGENCY");
    assertThat(sheet.getRow(finalRow).getCell(7).getCellFormula())
        .isEqualTo("H" + normalTotalExcelRow + "+H" + contingencyExcelRow);
}
```

Retain assertions for:

- Korean headers and month labels.
- Subtotal and total formulas.
- Blank zero values in C:H.
- `-` in I only when C is zero.
- Merged group and summary ranges.
- Column widths and Arial fonts.
- Letter, landscape, one-page scaling, margins, footer, print area, and repeated rows.

- [ ] **Step 2: Run renderer tests and confirm RED**

Run:

```bash
cd backend && mvn -Dtest=QuarterlyFinancialExcelServiceTest test
```

Expected: compilation failure because the shared renderer does not exist.

- [ ] **Step 3: Add failing logo-anchor assertions**

Inspect the sheet drawing and assert:

```java
XSSFPicture picture = (XSSFPicture) sheet.getDrawingPatriarch().getShapes().getFirst();
XSSFClientAnchor anchor = picture.getClientAnchor();
assertThat(anchor.getCol1()).isEqualTo(7);
assertThat(anchor.getCol2()).isLessThanOrEqualTo(10);
assertThat(anchor.getRow1()).isEqualTo(0);
assertThat(anchor.getRow2()).isGreaterThan(anchor.getRow1());
assertThat(anchor.getDx2()).isGreaterThan(anchor.getDx1());
```

Verify this for both offering and expenditure workbooks. Keep the missing-logo test: report generation succeeds and contains no picture.

- [ ] **Step 4: Implement the shared renderer**

Move the existing layout, styles, formulas, and print configuration into
`QuarterlyFinancialExcelService`. Read sheet name, title suffix, and special-row
label from the report metadata.

Generate:

```java
report.calendarYear() + " 년도 " + report.quarter() + "분기 " + report.titleSuffix()
```

Render groups from `report.groups()`, the special row from the `special*`
fields, and final C:H as normal total plus special row.

- [ ] **Step 5: Implement explicit visible logo anchoring**

Load `properties.branding().logPath()` from either direct classpath or
`static/` classpath form. Decode width and height, calculate a scale preserving
aspect ratio, and set both sides of the anchor within H:J and row 1. Do not rely
on an anchor containing only `col1` and `row1`.

Keep:

```java
anchor.setCol1(7);
anchor.setRow1(0);
```

Set `col2`, `row2`, and EMU offsets from the scaled pixel dimensions so the
picture has a non-zero visible rectangle and remains inside column J.

- [ ] **Step 6: Run renderer tests and confirm GREEN**

Run:

```bash
cd backend && mvn -Dtest=QuarterlyFinancialExcelServiceTest test
```

Expected: offering and expenditure workbook, formula, formatting, print, and logo tests pass.

---

### Task 4: Add The Expenditure Download Endpoint

**Files:**
- Modify: `backend/src/main/java/com/church/operation/rest/ReportController.java`
- Modify: `backend/src/test/java/com/church/operation/rest/ReportControllerTest.java`

**Interfaces:**
- Injects: `QuarterlyOfferingReportService`.
- Injects: `QuarterlyExpenditureReportService`.
- Injects: `QuarterlyFinancialExcelService`.
- Adds: `GET /api/reports/quarterly-expenditures.xlsx`.

- [ ] **Step 1: Write failing controller tests**

Add a mocked expenditure service and shared renderer. Assert:

```java
mockMvc.perform(get("/api/reports/quarterly-expenditures.xlsx")
        .param("year", "2026")
        .param("quarter", "2")
        .principal(authentication(viewer)))
    .andExpect(status().isOk())
    .andExpect(content().contentType(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    ))
    .andExpect(header().string(
        "Content-Disposition",
        "attachment; filename=quarterly-expenditures-2026-q2.xlsx"
    ))
    .andExpect(content().bytes(workbook));
```

Verify `build(viewer, 2026, 2)` and `render(report)`. Retain the offering endpoint test using the shared renderer. Add invalid expenditure quarter coverage.

- [ ] **Step 2: Run controller tests and confirm RED**

Run:

```bash
cd backend && mvn -Dtest=ReportControllerTest test
```

Expected: failure because the expenditure endpoint and dependencies do not exist.

- [ ] **Step 3: Implement both endpoints with the shared renderer**

Retain `/quarterly-offerings.xlsx` and add:

```java
@GetMapping("/quarterly-expenditures.xlsx")
ResponseEntity<byte[]> downloadQuarterlyExpenditures(
    Authentication authentication,
    @RequestParam("year") int year,
    @RequestParam("quarter") int quarter
) {
    byte[] workbook = quarterlyFinancialExcelService.render(
        quarterlyExpenditureReportService.build(actor(authentication), year, quarter)
    );
    return excel(
        workbook,
        "quarterly-expenditures-" + year + "-q" + quarter + ".xlsx"
    );
}
```

Extract only a small private `excel(byte[], String)` response helper to avoid
duplicating content type and attachment headers.

- [ ] **Step 4: Run controller and quarterly backend tests**

Run:

```bash
cd backend && mvn -Dtest=ReportControllerTest,QuarterlyOfferingReportServiceTest,QuarterlyExpenditureReportServiceTest,QuarterlyFinancialExcelServiceTest test
```

Expected: all focused backend tests pass.

---

### Task 5: Build The Quarterly Financial Report UI

**Files:**
- Modify: `frontend/src/api/reports.ts`
- Modify: `frontend/src/views/ReportsView.vue`
- Modify: `frontend/src/views/ReportsView.test.ts`

**Interfaces:**
- Retains: `downloadQuarterlyOfferingReport(filters)`.
- Adds: `downloadQuarterlyExpenditureReport(filters)`.
- Uses one `QuarterlyFinancialReportFilters` type for both downloads.

- [ ] **Step 1: Write failing frontend tests**

Mock both API functions and assert:

```typescript
const tab = screen.getByRole('tab', { name: /quarterly financial report/i });
await fireEvent.click(tab);

expect(screen.getByText('Quarterly Offering Excel')).toBeTruthy();
expect(screen.getByText('Quarterly Expenditure Excel')).toBeTruthy();

await fireEvent.update(screen.getByLabelText('Calendar year'), '2026');
await fireEvent.update(screen.getByLabelText('Quarter'), '2');
await fireEvent.click(screen.getByRole('button', { name: /download offering excel/i }));
expect(offeringQuarterlyMock).toHaveBeenCalledWith({ year: 2026, quarter: 2 });
expect(downloadedFilename).toBe('quarterly-offerings-2026-q2.xlsx');

await fireEvent.click(screen.getByRole('button', { name: /download expenditure excel/i }));
expect(expenditureQuarterlyMock).toHaveBeenCalledWith({ year: 2026, quarter: 2 });
expect(downloadedFilename).toBe('quarterly-expenditures-2026-q2.xlsx');
```

Add tests that one busy action disables only its own button and that each API
failure appears in the existing error area.

- [ ] **Step 2: Run the view test and confirm RED**

Run:

```bash
cd frontend && npm test -- ReportsView.test.ts
```

Expected: failures because the renamed tab, expenditure API, and second action do not exist.

- [ ] **Step 3: Add the expenditure API**

Rename the filter type:

```typescript
export interface QuarterlyFinancialReportFilters {
  year: number;
  quarter: 1 | 2 | 3 | 4;
}
```

Add:

```typescript
export function downloadQuarterlyExpenditureReport(filters: QuarterlyFinancialReportFilters) {
  return getBlob(withQuery('/api/reports/quarterly-expenditures.xlsx', { ...filters }));
}
```

Use the same type for the existing offering function.

- [ ] **Step 4: Implement the renamed tab and stacked actions**

Rename the tab ID to `quarterly-financial`, label it `Quarterly Financial Report`,
and title it `Quarterly Financial Report`.

Keep one Year and Quarter control block. Below it render two unframed stacked
action rows:

```html
<div class="quarterly-downloads">
  <section class="quarterly-download">
    <h4>Quarterly Offering Excel</h4>
    <button type="button" :disabled="quarterlyOfferingBusy" @click="downloadQuarterlyOfferingWorkbook">
      Download Offering Excel
    </button>
  </section>
  <section class="quarterly-download">
    <h4>Quarterly Expenditure Excel</h4>
    <button type="button" :disabled="quarterlyExpenditureBusy" @click="downloadQuarterlyExpenditureWorkbook">
      Download Expenditure Excel
    </button>
  </section>
</div>
```

Do not show the normal submit action, summary strip, table, or CSV button for
this tab. Validate year and quarter in one helper shared by both download
functions. Preserve the existing report-tab highlight behavior.

- [ ] **Step 5: Run frontend tests and build**

Run:

```bash
cd frontend && npm test -- ReportsView.test.ts
cd frontend && npm test
cd frontend && npm run build
```

Expected: the focused test, full frontend suite, type-check, and Vite build pass.

---

### Task 6: Documentation And End-To-End Verification

**Files:**
- Modify: `docs/superpowers/specs/2026-07-18-quarterly-offering-excel-report-design.md`
- Modify: `docs/superpowers/plans/2026-07-18-quarterly-offering-excel-report.md`
- Modify: `.superpowers/sdd/progress.md`

**Interfaces:**
- Documents the final shared model, expenditure report, UI names, logo rule, test counts, and visual verification.

- [ ] **Step 1: Update the existing offering documents**

Record that the offering report now uses the shared quarterly financial model
and renderer. Preserve all corrected carry-over and zero-display rules.

- [ ] **Step 2: Update project progress**

Add one concise Quarterly Financial Excel Report entry covering:

- Shared offering/expenditure renderer.
- `CARRY_OVER` and `CONTINGENCY` special rows.
- Renamed Reports tab and two downloads.
- Visible logo in both workbooks.
- Final backend/frontend test counts and build status.

- [ ] **Step 3: Run the full backend suite**

Run with Docker access because Testcontainers uses MongoDB:

```bash
cd backend && mvn test
```

Expected: zero failures and zero errors.

- [ ] **Step 4: Generate representative workbooks**

Have the renderer test write:

```text
backend/target/quarterly-offerings-preview.xlsx
backend/target/quarterly-expenditures-preview.xlsx
```

The files are generated verification artifacts and remain ignored.

- [ ] **Step 5: Inspect and render both workbooks**

Use the bundled `@oai/artifact-tool` runtime to:

- Inspect A1:J through each final row for values and formulas.
- Scan for `#REF!`, `#DIV/0!`, `#VALUE!`, `#NAME?`, and `#N/A`.
- Inspect image drawings and anchors.
- Render each used range to PNG.

Confirm visually:

- Offering title and carry-over row.
- Expenditure title and contingency row.
- Final totals include special rows.
- Zero display rules.
- Church logo visibly occupies the top-right H:J area in both reports.
- No text, logo, or table clipping.

- [ ] **Step 6: Run final repository checks**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors. Preserve unrelated `.DS_Store` and all existing
user changes. Leave all work uncommitted.

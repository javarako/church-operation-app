# Quarterly Offering Excel Report Implementation Plan

> **Final implementation:** The offering-specific model and renderer described
> here were migrated to the shared model and renderer in
> `2026-07-18-quarterly-financial-excel-report.md`. The original offering
> aggregation, `CARRY_OVER`, zero-display, formulas, and print-layout rules
> remain in force.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a calendar-quarter offering-income Excel download that reproduces the supplied Korean workbook layout using current budgets, offerings, reference labels, and church branding.

**Architecture:** A focused aggregation service derives calendar and fiscal dates, joins budgets and offerings, and returns an immutable report model. A separate Apache POI renderer converts that model into the formatted workbook; the existing report controller exposes the file and the Vue Reports page initiates the download.

**Tech Stack:** Java 21, Spring Boot 4.0.7, Spring Security, Spring Data MongoDB, Apache POI OOXML 5.5.1, JUnit 5, Mockito, Vue 3, TypeScript, Vitest, Vue Testing Library.

## Global Constraints

- Calendar quarters are Q1 January-March, Q2 April-June, Q3 July-September, and Q4 October-December.
- Select the fiscal budget year whose configured fiscal period contains the calendar quarter end date.
- Aggregate non-deleted offerings by `offeringSunday`, never `offeringDate`.
- Treat the active `OFFERING_INCOME` budget with fund code `CARRY_OVER` as the dedicated carry-over value; do not use legacy `BudgetType.CARRY_OVER`.
- Keep budget-only and offering-only fund/category rows.
- Use all Offering Fund and Offering Category references, including inactive records, for historical labels and ordering.
- Match the sample’s Korean labels, column widths, formulas, logo placement, landscape print settings, margins, repeating rows, print area, and page footer.
- Permit the same roles as existing general reports: `ADMIN`, `TREASURER`, `PASTOR`, and `VIEWER`.
- Leave generated workbooks in memory and do not persist them on the server.
- Preserve unrelated working-tree changes and leave this feature uncommitted unless the user requests a commit.

---

### Task 1: Quarterly Report Domain Model And Period Calculation

**Files:**
- Create: `backend/src/main/java/com/church/operation/dto/QuarterlyOfferingReport.java`
- Create: `backend/src/main/java/com/church/operation/dto/QuarterlyOfferingFundGroup.java`
- Create: `backend/src/main/java/com/church/operation/dto/QuarterlyOfferingCategoryRow.java`
- Create: `backend/src/main/java/com/church/operation/service/QuarterlyOfferingReportService.java`
- Create: `backend/src/test/java/com/church/operation/service/QuarterlyOfferingReportServiceTest.java`

**Interfaces:**
- Consumes: `BudgetRepository`, `OfferingRepository`, `ReferenceDataRepository`, `FiscalYearProperties`, `Member`.
- Produces: `QuarterlyOfferingReportService.build(Member actor, int year, int quarter)`.
- Produces immutable records:

```java
public record QuarterlyOfferingCategoryRow(
    String fundCode,
    String fundLabel,
    String categoryCode,
    String categoryLabel,
    BigDecimal budget,
    List<BigDecimal> monthlyActuals,
    BigDecimal cumulativeActual
) {}

public record QuarterlyOfferingFundGroup(
    int sequence,
    String fundCode,
    String fundLabel,
    List<QuarterlyOfferingCategoryRow> categories
) {}

public record QuarterlyOfferingReport(
    int calendarYear,
    int quarter,
    LocalDate quarterStart,
    LocalDate quarterEnd,
    int fiscalBudgetYear,
    LocalDate fiscalStart,
    List<YearMonth> months,
    List<QuarterlyOfferingFundGroup> funds,
    BigDecimal carryOverBudget,
    List<BigDecimal> carryOverMonthlyActuals,
    BigDecimal carryOverCumulativeActual
) {}
```

- [ ] **Step 1: Write failing period, authorization, and validation tests**

Create tests that construct the service with mocked repositories and assert:

```java
assertThat(service.build(viewer(), 2026, 2))
    .extracting(QuarterlyOfferingReport::quarterStart, QuarterlyOfferingReport::quarterEnd)
    .containsExactly(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));

when(fiscalYearProperties.startMonth()).thenReturn(7);
QuarterlyOfferingReport report = service.build(viewer(), 2026, 1);
assertThat(report.fiscalBudgetYear()).isEqualTo(2025);
assertThat(report.fiscalStart()).isEqualTo(LocalDate.of(2025, 7, 1));

assertThatThrownBy(() -> service.build(memberOnly(), 2026, 1))
    .isInstanceOf(SecurityException.class);
assertThatThrownBy(() -> service.build(viewer(), 2026, 0))
    .isInstanceOf(IllegalArgumentException.class);
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd backend && ./mvnw -Dtest=QuarterlyOfferingReportServiceTest test
```

Expected: compilation fails because the quarterly report records and service do not exist.

- [ ] **Step 3: Implement the immutable records and period calculation**

Implement `build` with these exact calculations:

```java
int firstMonth = ((quarter - 1) * 3) + 1;
LocalDate quarterStart = LocalDate.of(year, firstMonth, 1);
LocalDate quarterEnd = quarterStart.plusMonths(3).minusDays(1);
int fiscalStartMonth = fiscalYearProperties.startMonth();
int fiscalBudgetYear = quarterEnd.getMonthValue() >= fiscalStartMonth
    ? quarterEnd.getYear()
    : quarterEnd.getYear() - 1;
LocalDate fiscalStart = LocalDate.of(fiscalBudgetYear, fiscalStartMonth, 1);
List<YearMonth> months = List.of(
    YearMonth.from(quarterStart),
    YearMonth.from(quarterStart.plusMonths(1)),
    YearMonth.from(quarterStart.plusMonths(2))
);
```

Validate `year >= 2000`, quarter `1..4`, and the same four roles used by `ReportService`.

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```bash
cd backend && ./mvnw -Dtest=QuarterlyOfferingReportServiceTest test
```

Expected: all period, validation, and role tests pass.

### Task 2: Budget, Offering, Carry-Over, Label, And Ordering Aggregation

**Files:**
- Modify: `backend/src/main/java/com/church/operation/service/QuarterlyOfferingReportService.java`
- Modify: `backend/src/test/java/com/church/operation/service/QuarterlyOfferingReportServiceTest.java`

**Interfaces:**
- Consumes: `BudgetRepository.findActiveByFiscalYear(int)`.
- Consumes: `OfferingRepository.findByDeletedFalseAndOfferingSundayBetweenOrderByOfferingSundayAscFundCategoryAscPaymentMethodAsc(LocalDate, LocalDate)`.
- Consumes: `ReferenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType)`.
- Produces a fully populated `QuarterlyOfferingReport`.

- [ ] **Step 1: Write failing aggregation tests**

Use budgets and offerings that cover:

```java
Budget generalTithe = budget(2025, OFFERING_INCOME, "GENERAL", "TITHE", "12000");
Budget carryOver = budget(2025, OFFERING_INCOME, "CARRY_OVER", null, "2500");
Offering april = offering("2026-04-05", "GENERAL", "TITHE", "100");
Offering may = offering("2026-05-03", "GENERAL", "TITHE", "200");
Offering priorFiscal = offering("2025-08-03", "GENERAL", "TITHE", "300");
Offering carryOverActual = offering("2026-06-14", "CARRY_OVER", null, "999");
```

Assert:

```java
QuarterlyOfferingCategoryRow row = report.funds().getFirst().categories().getFirst();
assertThat(row.budget()).isEqualByComparingTo("12000");
assertThat(row.monthlyActuals()).containsExactly(
    new BigDecimal("100"), new BigDecimal("200"), BigDecimal.ZERO
);
assertThat(row.cumulativeActual()).isEqualByComparingTo("600");
assertThat(report.carryOverBudget()).isEqualByComparingTo("2500");
assertThat(report.carryOverMonthlyActuals()).containsExactly(
    BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("999")
);
assertThat(report.carryOverCumulativeActual()).isEqualByComparingTo("999");
assertThat(report.funds()).noneMatch(fund -> fund.fundCode().equals("CARRY_OVER"));
```

Add separate assertions that:

- The offering query starts at fiscal start and ends at quarter end.
- Deleted offerings cannot enter because the repository method is the non-deleted query.
- A budget-only category and an offering-only category both appear.
- Inactive references still supply labels.
- Unknown codes display as their stored codes.
- Funds and categories follow `sortOrder`, label, then code.
- Null amounts become zero and non-positive/invalid records do not break totals.

- [ ] **Step 2: Run the focused test and verify the new cases fail**

Run:

```bash
cd backend && ./mvnw -Dtest=QuarterlyOfferingReportServiceTest test
```

Expected: aggregation assertions fail because `build` does not yet populate rows.

- [ ] **Step 3: Implement aggregation**

Use a normalized key:

```java
private record RowKey(String fundCode, String categoryCode) {}
```

Filter active fiscal budgets to `BudgetType.OFFERING_INCOME`. Extract `CARRY_OVER` by case-insensitive normalized fund code and exclude it from ordinary keys. Merge duplicate carry-over values defensively with `BigDecimal::add`.

Load offerings once for `fiscalStart..quarterEnd` and skip null dates and null/non-positive amounts. Exclude `CARRY_OVER` from normal keys, but aggregate its three quarter months and fiscal cumulative amount into the dedicated carry-over fields. Aggregate normal offerings by fund/category:

```java
monthly.computeIfAbsent(key, ignored -> new BigDecimal[] {
    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
});
if (!offeringSunday.isBefore(quarterStart)) {
    int monthIndex = YearMonth.from(offeringSunday).getMonthValue()
        - YearMonth.from(quarterStart).getMonthValue();
    monthly.get(key)[monthIndex] = monthly.get(key)[monthIndex].add(amount);
}
cumulative.merge(key, amount, BigDecimal::add);
```

Build the row-key union from ordinary budgets and offerings. Resolve labels and ordering from `OFFERING_FUND` and `OFFERING_CATEGORY`, checking category `parentCode` when present. Group ordered rows into `QuarterlyOfferingFundGroup` records and assign one-based sequences.

- [ ] **Step 4: Run aggregation tests**

Run:

```bash
cd backend && ./mvnw -Dtest=QuarterlyOfferingReportServiceTest test
```

Expected: all quarterly aggregation tests pass.

### Task 3: Apache POI Workbook Renderer

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/church/operation/service/QuarterlyOfferingExcelService.java`
- Create: `backend/src/test/java/com/church/operation/service/QuarterlyOfferingExcelServiceTest.java`

**Interfaces:**
- Consumes: `QuarterlyOfferingReport`, `ChurchInformationProperties.branding().logPath()`.
- Produces: `byte[] QuarterlyOfferingExcelService.render(QuarterlyOfferingReport report)`.

- [ ] **Step 1: Add Apache POI and write the failing workbook structure test**

Add:

```xml
<dependency>
  <groupId>org.apache.poi</groupId>
  <artifactId>poi-ooxml</artifactId>
  <version>5.5.1</version>
</dependency>
```

Create a representative report with two funds, multiple categories, and carry over. Parse the result with `XSSFWorkbook` and assert:

```java
assertThat(workbook.getSheetName(0)).isEqualTo("Offering income");
assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("2026 년도 2분기 수입");
assertThat(sheet.getRow(3).getCell(3).getStringCellValue()).isEqualTo("4월");
assertThat(sheet.getRow(4).getCell(6).getCellFormula()).isEqualTo("SUM(D5:F5)");
assertThat(sheet.getRow(4).getCell(8).getCellFormula()).isEqualTo("IFERROR(H5/C5,0)");
```

- [ ] **Step 2: Run the renderer test and verify it fails**

Run:

```bash
cd backend && ./mvnw -Dtest=QuarterlyOfferingExcelServiceTest test
```

Expected: compilation fails because the renderer does not exist.

- [ ] **Step 3: Implement workbook rows, formulas, and styles**

Create `XSSFWorkbook` and `Offering income`. Build reusable styles for title, header, detail labels, fund labels, currency, percentage, subtotal, combined total, carry over, and final total.

Set widths using POI’s 1/256 units:

```java
sheet.setColumnWidth(0, (int) (6.1640625 * 256));
sheet.setColumnWidth(1, 27 * 256);
for (int column = 2; column <= 8; column++) {
    sheet.setColumnWidth(column, (int) (10.83203125 * 256));
}
sheet.setColumnWidth(9, (int) (16.5 * 256));
```

Use the exact row plan from the design. Keep zero amounts numeric but apply a zero-blank currency format to C:H:

```java
"$"#,##0;[Red]("$"#,##0);""
```

Use `IF(Cn=0,"-",Hn/Cn)` in column I so only a zero budget displays a dash. Populate the carry-over row from its dedicated budget, monthly actual, quarter-total, and cumulative fields, and include C:H from that row in the final totals. Generate formulas with one-based Excel row numbers. Merge each fund’s A cells through its subtotal and merge A:B for combined, carry-over, and final rows. Use `FormulaEvaluator.evaluateAll()` before writing so preview applications have cached results.

- [ ] **Step 4: Add failing image and print-metadata assertions**

Assert:

```java
assertThat(workbook.getAllPictures()).hasSize(1);
assertThat(sheet.getPrintSetup().getLandscape()).isTrue();
assertThat(sheet.getPrintSetup().getFitWidth()).isEqualTo((short) 1);
assertThat(sheet.getPrintSetup().getFitHeight()).isEqualTo((short) 1);
assertThat(sheet.getHorizontallyCenter()).isTrue();
assertThat(sheet.getFooter().getCenter()).isEqualTo("Page &P");
assertThat(workbook.getPrintArea(0)).isEqualTo("'Offering income'!$A$1:$J$" + finalRow);
```

Also assert 0.5 top/bottom margins, 0.25 left/right margins, repeating rows 1:4, title underline, sample column widths, and expected merged ranges.

- [ ] **Step 5: Implement logo and print metadata**

Normalize the configured classpath path as the tax receipt service does:

```java
String path = properties.branding().logPath();
String resourcePath = path.startsWith("/") ? path.substring(1) : path;
```

Detect PNG/JPEG, add the picture, anchor it within H1:J1, and preserve aspect ratio with `picture.resize()` constrained to the target area. Missing or unreadable optional branding must leave a valid workbook without an image.

Configure:

```java
sheet.getPrintSetup().setLandscape(true);
sheet.getPrintSetup().setFitWidth((short) 1);
sheet.getPrintSetup().setFitHeight((short) 1);
sheet.setFitToPage(true);
sheet.setHorizontallyCenter(true);
sheet.setMargin(Sheet.TopMargin, 0.5);
sheet.setMargin(Sheet.BottomMargin, 0.5);
sheet.setMargin(Sheet.LeftMargin, 0.25);
sheet.setMargin(Sheet.RightMargin, 0.25);
sheet.getFooter().setCenter("Page &P");
workbook.setPrintArea(0, 0, 9, 0, finalRowIndex);
sheet.setRepeatingRows(CellRangeAddress.valueOf("1:4"));
```

- [ ] **Step 6: Run renderer tests**

Run:

```bash
cd backend && ./mvnw -Dtest=QuarterlyOfferingExcelServiceTest test
```

Expected: all workbook content, formula, logo, style, and print-setting tests pass.

### Task 4: Secured Download Endpoint

**Files:**
- Modify: `backend/src/main/java/com/church/operation/rest/ReportController.java`
- Modify: `backend/src/test/java/com/church/operation/rest/ReportControllerTest.java`

**Interfaces:**
- Consumes: `QuarterlyOfferingReportService.build(Member, int, int)`.
- Consumes: `QuarterlyOfferingExcelService.render(QuarterlyOfferingReport)`.
- Produces: `GET /api/reports/quarterly-offerings.xlsx?year=<year>&quarter=<quarter>`.

- [ ] **Step 1: Write the failing controller test**

Mock the report-data and renderer services, then assert:

```java
mockMvc.perform(get("/api/reports/quarterly-offerings.xlsx")
        .param("year", "2026")
        .param("quarter", "2")
        .with(authentication(authentication(viewer()))))
    .andExpect(status().isOk())
    .andExpect(header().string(
        HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=quarterly-offerings-2026-q2.xlsx"
    ))
    .andExpect(content().contentType(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    ))
    .andExpect(content().bytes(new byte[] {1, 2, 3}));
```

Add invalid quarter and unauthorized member-role coverage.

- [ ] **Step 2: Run the controller test and verify it fails**

Run:

```bash
cd backend && ./mvnw -Dtest=ReportControllerTest test
```

Expected: `404` for the missing endpoint or constructor compilation failure after adding mocks.

- [ ] **Step 3: Implement the controller endpoint**

Inject both quarterly services and add:

```java
@GetMapping("/quarterly-offerings.xlsx")
ResponseEntity<byte[]> downloadQuarterlyOfferings(
    Authentication authentication,
    @RequestParam("year") int year,
    @RequestParam("quarter") int quarter
) {
    byte[] workbook = quarterlyOfferingExcelService.render(
        quarterlyOfferingReportService.build(actor(authentication), year, quarter)
    );
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=quarterly-offerings-" + year + "-q" + quarter + ".xlsx"
        )
        .body(workbook);
}
```

- [ ] **Step 4: Run backend report tests**

Run:

```bash
cd backend && ./mvnw -Dtest=QuarterlyOfferingReportServiceTest,QuarterlyOfferingExcelServiceTest,ReportControllerTest test
```

Expected: all quarterly and existing controller tests pass.

### Task 5: Vue Report Tab And Excel Download

**Files:**
- Modify: `frontend/src/api/reports.ts`
- Modify: `frontend/src/views/ReportsView.vue`
- Modify: `frontend/src/views/ReportsView.test.ts`

**Interfaces:**
- Produces frontend API:

```ts
export interface QuarterlyOfferingReportFilters {
  year: number;
  quarter: 1 | 2 | 3 | 4;
}

export function downloadQuarterlyOfferingReport(filters: QuarterlyOfferingReportFilters) {
  return getBlob(withQuery('/api/reports/quarterly-offerings.xlsx', filters));
}
```

- [ ] **Step 1: Write failing UI and download tests**

Mock `downloadQuarterlyOfferingReport`, sign in as `VIEWER`, and assert:

```ts
expect(screen.getByRole('tab', { name: /quarterly offerings/i })).toBeTruthy();
await fireEvent.click(screen.getByRole('tab', { name: /quarterly offerings/i }));
await fireEvent.update(screen.getByLabelText('Calendar year'), '2026');
await fireEvent.update(screen.getByLabelText('Quarter'), '2');
await fireEvent.click(screen.getByRole('button', { name: /download excel/i }));
expect(quarterlyMock).toHaveBeenCalledWith({ year: 2026, quarter: 2 });
expect(anchor.download).toBe('quarterly-offerings-2026-q2.xlsx');
```

Also assert that download failures render the existing error message area and that the selected tab keeps `active-report-tab`.

- [ ] **Step 2: Run the frontend test and verify it fails**

Run:

```bash
cd frontend && npm test -- --run src/views/ReportsView.test.ts
```

Expected: the quarterly tab and API export are absent.

- [ ] **Step 3: Add the API function and quarterly tab**

Extend `ReportTab['id']` with `'quarterly-offerings'`, add:

```ts
{
  id: 'quarterly-offerings',
  label: 'Quarterly offerings',
  title: 'Quarterly Offering Excel',
  description: 'Download the calendar-quarter offering income workbook.',
}
```

Add reactive defaults:

```ts
const quarterlyFilters = reactive({
  year: now.getFullYear(),
  quarter: (Math.floor(now.getMonth() / 3) + 1) as 1 | 2 | 3 | 4,
});
const quarterlyBusy = ref(false);
```

Render a year number input, quarter select, and `Download Excel` button only for the active quarterly tab. Do not run a table report on tab selection. Download using the existing `downloadBlob` helper with the agreed filename.

- [ ] **Step 4: Run the frontend tests**

Run:

```bash
cd frontend && npm test -- --run src/views/ReportsView.test.ts
```

Expected: all report-page tests pass.

### Task 6: Workbook Visual Verification And Full Regression

**Files:**
- Modify: `backend/src/test/java/com/church/operation/service/QuarterlyOfferingExcelServiceTest.java`

**Interfaces:**
- Verifies the complete feature; produces no new runtime interface.

- [ ] **Step 1: Generate a representative workbook**

Add `writesRepresentativeWorkbookForVisualInspection` to `QuarterlyOfferingExcelServiceTest`. It writes `target/quarterly-offerings-preview.xlsx` and asserts that the file is non-empty. Its report fixture contains:

- Two normal funds.
- At least two categories in the first fund.
- A budget-only row.
- An offering-only row.
- Values in all three quarter months.
- Prior-fiscal-period cumulative offerings.
- A `CARRY_OVER` offering-income budget.
- The configured church logo.

Expected: a valid `.xlsx` is available under `backend/target/` for inspection.

- [ ] **Step 2: Inspect the generated workbook programmatically**

Use the bundled spreadsheet runtime to render and inspect the workbook. Verify:

- No formula errors.
- No clipped Korean title/header text.
- Logo remains within H:J row 1 and preserves aspect ratio.
- Fund merges stop at each subtotal.
- Currency, percentage, and zero-blank display formats match the sample.
- Print preview metadata is landscape, 1 page wide by 1 tall, centered, and repeats rows 1:4.

- [ ] **Step 3: Run the full backend suite**

Run:

```bash
cd backend && ./mvnw test
```

Expected: all backend tests pass.

- [ ] **Step 4: Run the full frontend suite and build**

Run:

```bash
cd frontend && npm test -- --run
cd frontend && npm run build
```

Expected: all Vitest tests pass and the production build succeeds.

- [ ] **Step 5: Review the working tree**

Run:

```bash
git status --short
git diff --check
```

Expected: no whitespace errors; only the quarterly report feature plus pre-existing user changes are present. Do not stage or commit without the user’s request.

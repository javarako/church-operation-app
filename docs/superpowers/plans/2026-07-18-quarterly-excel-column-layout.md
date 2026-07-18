# Quarterly Excel Column Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the requested column widths and column-B wrapping to both quarterly financial Excel workbooks.

**Architecture:** Modify the existing shared Apache POI renderer so Offering and Expenditure receive identical layout changes. Preserve source cell styles by applying cached wrapped style copies only to column B.

**Tech Stack:** Java 21, Apache POI XSSF, JUnit 5, AssertJ

## Global Constraints

- Column widths are A `7`, B `28`, I `8.5`, and J `16`.
- Every styled cell in column B wraps text.
- Columns C through H and all existing workbook behavior remain unchanged.
- Both Offering and Expenditure workbooks must be covered by tests.

---

### Task 1: Quarterly Workbook Column Layout

**Files:**
- Modify: `backend/src/test/java/com/church/operation/service/QuarterlyFinancialExcelServiceTest.java`
- Modify: `backend/src/main/java/com/church/operation/service/QuarterlyFinancialExcelService.java`
- Modify: `docs/superpowers/specs/2026-07-18-quarterly-offering-excel-report-design.md`
- Modify: `docs/superpowers/specs/2026-07-18-quarterly-financial-excel-report-design.md`

**Interfaces:**
- Consumes: `QuarterlyFinancialExcelService.render(QuarterlyFinancialReport)`
- Produces: Offering and Expenditure `.xlsx` bytes with the requested widths and wrapping

- [x] **Step 1: Write failing layout assertions**

Add a helper that asserts:

```java
assertThat(sheet.getColumnWidth(0)).isEqualTo(7 * 256);
assertThat(sheet.getColumnWidth(1)).isEqualTo(28 * 256);
assertThat(sheet.getColumnWidth(8)).isEqualTo((int) (8.5 * 256));
assertThat(sheet.getColumnWidth(9)).isEqualTo(16 * 256);
for (Row row : sheet) {
    Cell cell = row.getCell(1);
    if (cell != null) {
        assertThat(cell.getCellStyle().getWrapText()).isTrue();
    }
}
```

Invoke the helper for both Offering and Expenditure workbook tests.

- [x] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd backend
mvn -Dtest=QuarterlyFinancialExcelServiceTest test
```

Expected: failures report the old widths and non-wrapped column-B styles.

- [x] **Step 3: Implement the shared renderer change**

Update `configureColumns`:

```java
sheet.setColumnWidth(0, 7 * 256);
sheet.setColumnWidth(1, 28 * 256);
sheet.setColumnWidth(8, (int) (8.5 * 256));
sheet.setColumnWidth(9, 16 * 256);
```

After report rows are created, call a method that iterates column B and applies
cached cloned styles with `setWrapText(true)`. Use each source style index as
the cache key so all other style properties remain intact without style
explosion.

- [x] **Step 4: Run focused and full backend tests**

Run:

```bash
cd backend
mvn -Dtest=QuarterlyFinancialExcelServiceTest test
mvn test
```

Expected: all tests pass.

- [x] **Step 5: Update the existing report specifications**

Replace the old width values with A `7`, B `28`, I `8.5`, and J `16`, and state
that every column-B cell uses wrap text.

- [x] **Step 6: Verify generated workbooks**

Inspect both preview workbook XML files for the four widths and render both
workbooks to PDF/PNG. Confirm the sheets remain Letter landscape, Adjust to
100%, readable, and free of clipped column-B labels.

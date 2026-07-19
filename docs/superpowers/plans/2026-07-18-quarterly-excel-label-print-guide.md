# Quarterly Excel Label, Print Setup, And Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display the configured CONTINGENCY category label, set both quarterly workbooks to Adjust to 100% print scaling, and publish the updated English and Korean manuals.

**Architecture:** Resolve the expenditure special-row label in the expenditure aggregation service using its existing all-reference index. Change print behavior once in the shared Apache POI renderer. Update both Markdown guide sources and regenerate the four published DOCX/PDF artifacts through the existing guide builders and document rendering workflow.

**Tech Stack:** Java 21, Spring Boot, Apache POI, JUnit 5, Python, python-docx, LibreOffice/Poppler document rendering

## Global Constraints

- Resolve Financial Category code `CONTINGENCY` case-insensitively.
- Use its configured label, falling back to `CONTINGENCY`.
- Apply Adjust to 100% to both Offering and Expenditure workbooks.
- Preserve Letter landscape orientation, margins, horizontal centering, print area, repeated rows, and footer.
- Update and regenerate both English and Korean guides.
- Leave all changes uncommitted.

---

### Task 1: Resolve The Expenditure Special-Row Label

**Files:**
- Modify: `backend/src/test/java/com/church/operation/service/QuarterlyExpenditureReportServiceTest.java`
- Modify: `backend/src/main/java/com/church/operation/service/QuarterlyExpenditureReportService.java`

**Interfaces:**
- Consumes: `ReferenceIndex.categoryLabel(String code)`.
- Produces: `QuarterlyFinancialReport.specialRowLabel()` containing the configured label or `CONTINGENCY`.

- [ ] **Step 1: Write failing label tests**

Add a configured category:

```java
reference(
    ReferenceDataType.FINANCIAL_CATEGORY,
    "CONTINGENCY",
    "Emergency Reserve",
    null,
    99,
    true
)
```

Assert:

```java
assertThat(report.specialRowLabel()).isEqualTo("Emergency Reserve");
```

Add a separate missing-reference case and assert:

```java
assertThat(report.specialRowLabel()).isEqualTo("CONTINGENCY");
```

- [ ] **Step 2: Run the focused service test and verify RED**

Run:

```bash
cd backend
mvn -Dtest=QuarterlyExpenditureReportServiceTest test
```

Expected: the configured-label assertion fails because the report contains the
literal code.

- [ ] **Step 3: Implement label resolution**

Load the reference index before constructing `QuarterlyFinancialReport`, pass it
through aggregation, and set:

```java
references.categoryLabel("CONTINGENCY")
```

as `specialRowLabel`. Ensure `ReferenceIndex.categoryLabel` falls back to
`CONTINGENCY` for a missing or blank label.

- [ ] **Step 4: Run the focused service test and verify GREEN**

Run the focused Maven command again. Expected: all expenditure report tests
pass.

---

### Task 2: Change Shared Workbook Print Scaling

**Files:**
- Modify: `backend/src/test/java/com/church/operation/service/QuarterlyFinancialExcelServiceTest.java`
- Modify: `backend/src/main/java/com/church/operation/service/QuarterlyFinancialExcelService.java`

**Interfaces:**
- Consumes: both Offering and Expenditure `QuarterlyFinancialReport` fixtures.
- Produces: worksheet page setup with scale `100` and no fit dimensions.

- [ ] **Step 1: Replace fit-to-page assertions with failing 100% assertions**

For both sheet types assert:

```java
assertThat(sheet.getPrintSetup().getScale()).isEqualTo((short) 100);
assertThat(sheet.getCTWorksheet().getSheetPr().getPageSetUpPr().getFitToPage()).isFalse();
assertThat(sheet.getCTWorksheet().getPageSetup().isSetFitToWidth()).isFalse();
assertThat(sheet.getCTWorksheet().getPageSetup().isSetFitToHeight()).isFalse();
```

Retain assertions for orientation, paper size, margins, centering, footer, print
area, and repeated rows.

- [ ] **Step 2: Run the renderer test and verify RED**

Run:

```bash
cd backend
mvn -Dtest=QuarterlyFinancialExcelServiceTest test
```

Expected: the new scale/fit assertions fail against the current Fit-to-page
settings.

- [ ] **Step 3: Implement Adjust to 100%**

In `configurePrint`:

```java
sheet.setFitToPage(false);
sheet.getPrintSetup().setScale((short) 100);
sheet.getCTWorksheet().getPageSetup().unsetFitToWidth();
sheet.getCTWorksheet().getPageSetup().unsetFitToHeight();
```

Remove the existing `setFitWidth`, `setFitHeight`, and `setFitToPage(true)`
calls. Guard XML unsets with `isSetFitToWidth()` and `isSetFitToHeight()`.

- [ ] **Step 4: Run focused quarterly tests**

Run:

```bash
cd backend
mvn -Dtest=QuarterlyExpenditureReportServiceTest,QuarterlyOfferingReportServiceTest,QuarterlyFinancialExcelServiceTest test
```

Expected: all focused tests pass.

---

### Task 3: Update And Regenerate Both User Guides

**Files:**
- Modify: `docs/user-guide/user-guide-content.md`
- Modify: `docs/user-guide/user-guide-content-ko.md`
- Regenerate: `docs/user-guide/Church Operations User Guide.docx`
- Regenerate: `docs/user-guide/Church Operations User Guide.pdf`
- Regenerate: `docs/user-guide/교회운영 메뉴얼.docx`
- Regenerate: `docs/user-guide/교회운영 메뉴얼.pdf`

**Interfaces:**
- Consumes: existing guide builder scripts and current report screenshot.
- Produces: synchronized English and Korean guide sources and published files.

- [ ] **Step 1: Add quarterly report instructions in English**

After Financial Actual vs Budget, add:

```markdown
### Quarterly Financial Report

1. Select **Quarterly Financial Report**.
2. Select the calendar year and quarter.
3. Select the Offering download for the quarterly offering workbook or the
   Expenditure download for the quarterly expense workbook.
4. Review the configured carry-over row in Offering and the configured
   `CONTINGENCY` Financial Category label in Expenditure.

Both workbooks use Letter landscape print settings with scaling set to
**Adjust to 100%**.
```

- [ ] **Step 2: Add the equivalent Korean instructions**

Use:

```markdown
### 분기 재정 보고서

1. **Quarterly Financial Report**를 선택합니다.
2. 달력 연도와 분기를 선택합니다.
3. 분기 헌금 통합 문서는 Offering 다운로드를, 분기 지출 통합 문서는
   Expenditure 다운로드를 선택합니다.
4. 헌금 통합 문서의 이월금 행과 지출 통합 문서의 `CONTINGENCY`
   재정 카테고리 표시 이름을 확인합니다.

두 통합 문서는 Letter 가로 방향이며 인쇄 배율은 **100%로 조정**으로
설정됩니다.
```

- [ ] **Step 3: Rebuild the DOCX files**

Using the workspace Python runtime:

```bash
python docs/user-guide/build_user_guide.py
python docs/user-guide/build_user_guide_ko.py
```

Expected: both DOCX files are regenerated without errors.

- [ ] **Step 4: Render DOCX and emit PDFs**

Use the Documents skill `render_docx.py` workflow for each DOCX with
`--emit_pdf`. Copy the visually approved emitted PDFs to the existing English
and Korean PDF paths.

- [ ] **Step 5: Visually inspect all rendered pages**

Verify:

- The Quarterly Financial Report subsection appears in both languages.
- No heading is orphaned.
- No text or screenshot is clipped.
- Page numbers, headers, and footers remain intact.
- English and Korean PDF page counts match their corresponding DOCX renders.

---

### Task 4: Final Verification And Review

**Files:**
- Verify all files modified above.

- [ ] **Step 1: Run complete backend tests**

Run:

```bash
cd backend
mvn test
```

Expected: all backend tests pass.

- [ ] **Step 2: Inspect generated workbook XML and previews**

Regenerate the representative workbooks through
`QuarterlyFinancialExcelServiceTest`, then confirm:

```text
pageSetup scale="100"
no fitToWidth
no fitToHeight
CONTINGENCY reference label appears in the expenditure special row
```

Render both workbooks to PDF/PNG and visually inspect the special rows.

- [ ] **Step 3: Review workspace**

Run:

```bash
git diff --check
git status --short
```

Confirm unrelated user files are untouched and all feature changes remain
uncommitted.

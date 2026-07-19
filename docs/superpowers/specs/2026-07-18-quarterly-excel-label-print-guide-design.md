# Quarterly Excel Label, Print Setup, And Guide Design

## Goal

Correct the quarterly expenditure special-row label, change quarterly workbook
print scaling to Excel's Adjust to 100% mode, and document the quarterly
financial downloads in both published user-guide languages.

## Root Causes

The expenditure aggregation service currently sets the special-row label to the
literal code `CONTINGENCY`. Although the workbook cell is populated, it cannot
show the church-configured Financial Category label.

The shared workbook renderer explicitly enables Fit-to-page and sets both fit
width and fit height to one page. Excel therefore opens the workbooks in Fit to
page mode instead of Adjust to 100% mode.

## Expenditure Special-Row Label

`QuarterlyExpenditureReportService` will resolve the label of Financial Category
code `CONTINGENCY` from the complete reference-data collection already loaded by
the service. The lookup is case-insensitive.

The resolved label is passed as `QuarterlyFinancialReport.specialRowLabel`.
When the reference record is absent or its label is blank, the fallback is the
literal code `CONTINGENCY`.

The category remains excluded from normal expenditure groups and continues to
combine all of its sub-category budgets and transactions in the dedicated
special row.

## Print Setup

`QuarterlyFinancialExcelService` is shared by Offering and Expenditure, so the
print change applies to both workbooks.

Each generated sheet will:

- Retain Letter paper and landscape orientation.
- Retain the existing margins, horizontal centering, print area, repeated title
  rows, and page footer.
- Disable Fit-to-page mode.
- Remove `fitToWidth` and `fitToHeight` from the worksheet page setup.
- Set print scale to `100`.

The resulting Excel Page Setup selection is Adjust to 100% normal size.

## User Guides

Update:

- `docs/user-guide/user-guide-content.md`
- `docs/user-guide/user-guide-content-ko.md`

The Reports section in each language will explain:

- Open `Reports` and select `Quarterly Financial Report`.
- Select a calendar year and quarter.
- Download the separate Quarterly Offering Excel or Quarterly Expenditure Excel
  workbook.
- Offering uses the configured carry-over row.
- Expenditure uses the configured Financial Category label for
  `CONTINGENCY`, falling back to the code.
- Both workbooks open with print scaling set to Adjust to 100%.

Regenerate:

- `docs/user-guide/Church Operations User Guide.docx`
- `docs/user-guide/Church Operations User Guide.pdf`
- `docs/user-guide/교회운영 메뉴얼.docx`
- `docs/user-guide/교회운영 메뉴얼.pdf`

The generated DOCX and PDF files will be rendered and visually inspected for
page overflow, clipped text, missing screenshots, and broken headings.

## Testing

Backend tests will verify:

- A configured `CONTINGENCY` label becomes the report special-row label.
- Missing or blank reference labels fall back to `CONTINGENCY`.
- Offering and Expenditure sheets use print scale `100`.
- Fit-to-page is disabled.
- `fitToWidth` and `fitToHeight` are absent.
- Existing page layout settings remain unchanged.

Run the focused quarterly service and renderer tests, then the complete backend
test suite. Rebuild both manuals and render their pages for visual verification.

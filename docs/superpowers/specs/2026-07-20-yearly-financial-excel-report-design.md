# Yearly Financial Excel Report Design

## Goal

Add separate yearly Offering and Expenditure Excel downloads that follow the
layout and visual language of `Yearly report sample.xlsx` and reuse the proven
concepts from the quarterly financial reports.

## Scope

This change includes:

- A Yearly Financial Report tab on the Reports page.
- A fiscal-year selector shared by two download actions.
- A yearly Offering workbook.
- A yearly Expenditure workbook.
- Current-year budget, current-year actual, and next-year budget aggregation.
- Dedicated `CARRY_OVER` and `CONTINGENCY` rows.
- Configured church-logo placement and sample-matching workbook formatting.
- Backend, frontend, workbook-structure, formula, and visual regression tests.

It does not combine Offering and Expenditure into one workbook, change the
quarterly reports, or change the existing on-screen Financial Actual vs Budget
report.

## Access

The yearly financial downloads are available to the existing general report
roles:

- `ADMIN`
- `TREASURER`
- `PASTOR`
- `VIEWER`

`MEMBERSHIP` and `MEMBER` do not receive access.

## Fiscal Period

The user selects a fiscal year. For selected fiscal year `2026`:

- The current budget is loaded from fiscal-year 2026 budget records.
- Actual activity covers the configured 2026 fiscal period.
- If the configured fiscal start month is January, the actual period is
  January 1, 2026 through December 31, 2026.
- For any other configured start month, the period begins on the first day of
  that month in 2026 and ends one day before the same date in 2027.
- The proposed next-year budget is loaded from fiscal-year 2027 budget records.

The report rejects fiscal years below 2000.

## Offering Data

### Budgets

Load active budget records for the selected fiscal year and the following
fiscal year.

- Include only `BudgetType.OFFERING_INCOME`.
- `category` is the Offering Fund code.
- `subCategory` is the Offering Category code.
- A case-insensitive fund code of `CARRY_OVER` is excluded from normal groups
  and aggregated into the dedicated carry-over row for each budget year.
- Preserve whether a next-year budget record exists separately from its amount.

### Actual Offerings

Load offerings whose `offeringSunday` is within the selected fiscal period.

- Include only non-deleted offering records.
- Group normal offerings by fund and category.
- Aggregate one annual actual amount per row.
- Exclude case-insensitive `CARRY_OVER` offerings from normal groups and
  aggregate them into the dedicated carry-over row.
- Ignore null dates, null amounts, and non-positive amounts.

## Expenditure Data

### Budgets

Load active budget records for the selected fiscal year and the following
fiscal year.

- Include only `BudgetType.EXPENSE`.
- `category` is the Financial Category code.
- `subCategory` is the Financial Sub-category code.
- A case-insensitive category code of `CONTINGENCY` is excluded from normal
  groups and aggregated into the dedicated contingency row for each budget
  year.
- All contingency sub-categories are combined.
- Preserve whether a next-year budget record exists separately from its amount.

### Actual Expenditures

Load financial transactions within the selected fiscal period.

- Include only non-deleted `FinancialTransactionType.EXPENSE` records.
- Group normal transactions by category and sub-category.
- Aggregate one annual actual amount per row.
- Exclude case-insensitive `CONTINGENCY` transactions from normal groups and
  aggregate them into the dedicated contingency row.
- Ignore null dates, null amounts, and non-positive amounts.

## Reference Labels And Ordering

Use Offering Fund and Offering Category references for the Offering workbook.
Use Financial Category and Financial Sub-category references for the
Expenditure workbook. Include inactive reference values so historical records
retain their labels.

- Parent groups sort by `sortOrder`, then label, then code.
- Child categories sort by `sortOrder`, then label, then code within the parent.
- Unknown values fall back to the stored code.
- Normal detail rows are the union of current-year budget keys, actual activity
  keys, and next-year budget keys.

## Yearly Workbook Model

Offering and Expenditure aggregation services produce a shared yearly workbook
model containing:

- Selected fiscal year, fiscal start date, and fiscal end date.
- Ordered parent groups and child rows.
- Current-year budget amount.
- Annual actual amount.
- Next-year budget amount and an explicit record-present flag.
- Current-year and next-year special-row values.
- Workbook metadata: sheet name, Korean labels, and special-row label.

Offering maps `CARRY_OVER` to the special row. Expenditure maps
`CONTINGENCY` to the special row.

## Workbook Layout

The Offering workbook contains one sheet named `Offering income`.

The Expenditure workbook contains one sheet named `Expenditure`.

Both workbooks follow the sample structure:

1. Configured church logo in the top-right area of row 1.
2. Merged, underlined title in A2:H2.
3. Blank row 3.
4. Korean headers in row 4.
5. Dynamic detail and summary rows beginning at row 5.

The Offering title is:

`<year>년도 수입 결산 및 예산안`

The Expenditure title is:

`<year>년도 지출 결산 및 예산안`

### Offering Headers

| Column | Header | Content |
|---|---|---|
| A | `구 분` | Offering Fund grouping |
| B | `항 목` | Offering Category |
| C | `<year> 예산` | Selected fiscal-year budget |
| D | `수입결산` | Selected fiscal-year actual offering total |
| E | `수입대비` | Actual offering divided by current budget |
| F | `<year + 1> 예산` | Following fiscal-year budget |
| G | `예산대비` | Next-year budget divided by current budget |
| H | `비고` | Notes |

### Expenditure Headers

| Column | Header | Content |
|---|---|---|
| A | `구 분` | Financial Category grouping |
| B | `항 목` | Financial Sub-category |
| C | `<year> 예산` | Selected fiscal-year budget |
| D | `지출결산` | Selected fiscal-year actual expenditure total |
| E | `지출대비` | Actual expenditure divided by current budget |
| F | `<year + 1> 예산` | Following fiscal-year budget |
| G | `예산대비` | Next-year budget divided by current budget |
| H | `비고` | Notes |

Match the sample workbook's column widths, row heights, fonts, borders,
alignment, wrapping, currency formats, percentage formats, merged regions, and
title treatment. Keep row 1 at the sample logo height and row 2 at the sample
title height.

For each normal group:

- Create one detail row per child category.
- Add a `(<sequence>) 소 계` subtotal row.
- Merge the group label in column A through its subtotal row.

After all normal groups:

- Add the combined normal total row.
- Add the special row.
- Add the final total row.

The Offering special-row label is `전년도 이월금`. The Expenditure special-row
label resolves the Financial Category label for code `CONTINGENCY`, falling
back to `CONTINGENCY` when no usable label exists.

## Calculations And Missing Budgets

Use formulas for derived workbook cells and totals.

- Detail E: `IF(C=0,"-",D/C)`.
- Detail G: `IF(next-year budget is missing,"-",IF(C=0,"-",F/C))`.
- Group subtotal C and D: sum the group's detail rows.
- Group subtotal F: sum entered next-year budgets; display `-` when no
  next-year budget record exists anywhere in that group.
- Group subtotal E and G: calculate from the subtotal values, with the same
  zero-current-budget and missing-next-year-budget rules.
- Normal total C and D: sum all group subtotal rows.
- Normal total F: sum entered next-year budgets; display `-` when no normal
  next-year budget records exist.
- Special-row E and G: use the same ratio and missing-budget rules.
- Final C and D: normal total plus special row.
- Final F: sum available normal and special next-year budgets; display `-` only
  when no next-year budget record exists in either scope.
- Final E and G: calculate from final values with the same denominator and
  missing-budget rules.

Missing next-year budget data means no matching active budget record exists.
Both F and G display `-` for that missing scope. An explicitly entered zero is
numeric data and is not treated as missing. Amounts and percentages otherwise
remain numeric and auditable.

## Logo And Branding

Load the configured `church.branding.log-path` resource using the same
classpath resolution behavior as the quarterly reports.

- Preserve the logo aspect ratio.
- Place the logo in the top-right area of row 1 without covering the title.
- Treat a missing or invalid logo as optional so report generation still works.
- Verify a configured logo has a visible, non-zero anchor in both workbooks.

## Print Settings

Both workbooks use the sample and quarterly financial report conventions:

- Letter paper.
- Landscape orientation.
- Adjust to 100% scaling with fit-to-page disabled.
- Horizontally centered.
- Top and bottom margins: 0.5 inches.
- Left and right margins: 0.25 inches.
- Center footer: `Page &P`.
- Print area through column H and the final total row.
- Repeat rows 1 through 4 on each printed page.

## API

Add:

`GET /api/reports/yearly-offerings.xlsx?fiscalYear=<year>`

`GET /api/reports/yearly-expenditures.xlsx?fiscalYear=<year>`

Both responses use content type:

`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`

Download filenames are:

- `yearly-offerings-<year>.xlsx`
- `yearly-expenditures-<year>.xlsx`

## Reports UI

Add a `Yearly Financial Report` report button.

The selected report shows one `Fiscal year` field followed by two stacked
download sections:

1. `Yearly Offering Excel`
2. `Yearly Expenditure Excel`

Each section has its own download button and busy state. Both use the same
validated fiscal year. Errors remain visible in the existing Reports error
area. Selecting the report button retains the existing selected-button
highlight behavior.

## Error Handling

- Reject fiscal years below 2000.
- Reject unauthorized roles before querying data.
- Skip malformed budget, offering, or transaction records rather than
  corrupting totals.
- Return a usable workbook when the logo resource is missing or invalid.
- Surface backend download failures through the existing Reports error area.

## Testing And Verification

Backend aggregation tests cover:

- Configured fiscal start and end dates.
- Role authorization and invalid fiscal years.
- Current and next fiscal-year budget selection.
- Offering-income and expense filtering.
- Deleted, wrong-type, invalid, and non-positive actual-record exclusion.
- Current-budget-only, actual-only, and next-budget-only rows.
- Reference labels and deterministic ordering.
- Case-insensitive `CARRY_OVER` and `CONTINGENCY` aggregation.
- Explicit distinction between missing and zero next-year budget records.
- Detail, subtotal, normal total, special-row, and final totals.

Workbook tests cover:

- Sheet names and exact Korean titles.
- Exact Offering and Expenditure row-4 headers.
- Sample column widths, row heights, wrapping, merged cells, styles, formulas,
  and number formats.
- Missing next-year budget dashes in F and G at detail and summary levels.
- Landscape Letter print settings at Adjust to 100%.
- Church-logo presence and visible top-right anchors.
- Formula evaluation without spreadsheet errors.

Controller tests cover authorization, validation, content type, filenames, and
workbook bytes for both endpoints.

Frontend tests cover:

- Yearly Financial Report visibility and selected state.
- Fiscal-year validation.
- Both stacked download actions.
- Correct query parameters and filenames.
- Independent busy states and error handling.

Final verification includes the focused tests, the complete backend and
frontend suites, the frontend production build, artifact-tool value/formula
inspection, formula-error scans, and visual rendering of both generated sheets.

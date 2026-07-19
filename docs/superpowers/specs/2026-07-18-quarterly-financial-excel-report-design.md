# Quarterly Financial Excel Report Design

## Goal

Extend the existing quarterly offering Excel feature with a matching quarterly
expenditure workbook, present both downloads under one Quarterly Financial
Report tab, and make the workbook layout and church-logo behavior consistent.

## Scope

This change includes:

- A quarterly expenditure data model and aggregation service.
- A quarterly expenditure Excel download.
- A shared quarterly workbook renderer used by offering and expenditure.
- Reliable top-right church-logo placement in both workbooks.
- A renamed Reports tab with separate offering and expenditure download actions.
- Backend, frontend, workbook-structure, formula, and visual regression tests.

It does not combine offering and expenditure into one workbook or change the
existing CSV and on-screen financial budget report.

## Access

The quarterly financial downloads are available to the same report roles:

- `ADMIN`
- `TREASURER`
- `PASTOR`
- `VIEWER`

`MEMBERSHIP` and `MEMBER` do not receive access.

## Period Rules

The user selects a calendar year and calendar quarter.

- Q1: January 1 through March 31.
- Q2: April 1 through June 30.
- Q3: July 1 through September 30.
- Q4: October 1 through December 31.

The configured fiscal start month determines the fiscal budget year and
cumulative start date. The fiscal period containing the selected quarter end is
used, matching the quarterly offering report.

## Expenditure Data

### Budgets

Load active budgets for the derived fiscal budget year.

- Include only `BudgetType.EXPENSE`.
- `category` is the Financial Category code.
- `subCategory` is the Financial Sub-category code.
- A case-insensitive category code of `CONTINGENCY` is excluded from normal
  groups and aggregated into the dedicated contingency row.
- All contingency sub-categories are combined.

### Transactions

Load non-deleted financial transactions from fiscal start through quarter end.

- Include only `FinancialTransactionType.EXPENSE`.
- Group normal transactions by category and sub-category.
- D, E, and F contain transaction totals for the three quarter months.
- G contains the quarter total.
- H contains the fiscal-start-through-quarter-end cumulative total.
- A case-insensitive category code of `CONTINGENCY` is excluded from normal
  groups and aggregated into the dedicated contingency row.
- Ignore null dates, null amounts, and non-positive amounts.

### Reference Labels And Ordering

Use Financial Category and Financial Sub-category reference data, including
inactive values, so historical records retain labels.

- Categories sort by `sortOrder`, then label, then code.
- Sub-categories sort by `sortOrder`, then label, then code within the parent.
- Unknown values fall back to the stored code.
- The normal detail rows are the union of budget keys and transaction keys.

## Shared Workbook Model

Offering and expenditure aggregation services produce the same internal
quarterly workbook model:

- Calendar year and quarter.
- Quarter start and end.
- Fiscal budget year and fiscal start.
- Three `YearMonth` values.
- Ordered groups with category rows.
- Each category row contains budget, three monthly actuals, and cumulative actual.
- One special row containing budget, three monthly actuals, and cumulative actual.
- Workbook metadata: sheet name, Korean title suffix, and special-row label.

Offering maps `CARRY_OVER` to the special row. Expenditure maps `CONTINGENCY`.

## Workbook Layout

The expenditure workbook contains one sheet named `Expenditure`.

The offering workbook retains its existing sheet name `Offering income`.

Both workbooks use the same structure:

1. Configured church logo in the top-right H:J area.
2. Merged, underlined title in A2:J2.
3. Blank row.
4. Korean headers.
5. Dynamic detail and summary rows.

The expenditure title is:

`<year> 년도 <quarter>분기 지출`

The offering title remains:

`<year> 년도 <quarter>분기 수입`

The columns remain:

| Column | Content |
|---|---|
| A | Category grouping |
| B | Sub-category |
| C | Annual budget |
| D | Quarter month 1 |
| E | Quarter month 2 |
| F | Quarter month 3 |
| G | Quarterly total |
| H | Fiscal cumulative total |
| I | Cumulative actual divided by budget |
| J | Notes |

Both workbooks use the sample workbook's exact raw OOXML column widths:

- A: `7.83203125`
- B: `28.83203125`
- C through H: `10.83203125`
- I: `9.33203125`
- J: `16.83203125`

Every populated cell in column B uses wrap text, including the header, detail,
subtotal, special, and total rows.

For each normal group:

- Create one detail row per sub-category.
- Add a `(<sequence>) 소 계` subtotal row.
- Merge the group label in column A through its subtotal row.

After all normal groups:

- Add the combined normal total row.
- Add the special row.
- Add the final total row.

The expenditure special-row label uses the configured Financial Category label
for code `CONTINGENCY`, resolved case-insensitively, and falls back to
`CONTINGENCY` when the reference is missing or blank. The offering special-row
label remains `전년도 이월금`.

## Calculations

Use formulas for derived cells:

- Detail G: `SUM(D:F)`.
- Detail I: `IF(C=0,"-",H/C)`.
- Group subtotal C:H: sum the group detail rows.
- Group subtotal I: `IF(C=0,"-",H/C)`.
- Normal total C:H: sum all group subtotal rows.
- Normal total I: `IF(C=0,"-",H/C)`.
- Special G: `SUM(D:F)`.
- Special I: `IF(C=0,"-",H/C)`.
- Final C:H: normal total plus special row.
- Final I: `IF(C=0,"-",H/C)`.

Amounts remain numeric. Zero values in C:H display as blank. Column I displays
`-` only when column C is zero; positive budgets with zero actual display
`0.00%`.

## Logo And Branding

The shared renderer loads the configured `church.branding.log-path` resource.

- Resolve supported classpath forms used by the existing application.
- Preserve the image aspect ratio.
- Anchor the image inside the H:J region of row 1.
- Size it to remain visibly present without clipping or covering the title.
- Treat a missing or invalid image as optional so report generation still works.
- Tests must verify the image exists and has a non-zero visible anchor.
- Visual verification must confirm the logo appears in both rendered workbooks.

## Print Settings

Both workbooks retain the existing quarterly offering settings:

- Letter paper.
- Landscape.
- Adjust to 100% scaling with fit-to-page disabled.
- Horizontally centered.
- Top and bottom margins: 0.5 inches.
- Left and right margins: 0.25 inches.
- Center footer: `Page &P`.
- Print area through column J and the final total row.
- Repeat rows 1 through 4 on each printed page.

## API

Retain:

`GET /api/reports/quarterly-offerings.xlsx?year=<year>&quarter=<1..4>`

Add:

`GET /api/reports/quarterly-expenditures.xlsx?year=<year>&quarter=<1..4>`

The expenditure response:

- Content type:
  `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- Filename:
  `quarterly-expenditures-<year>-q<quarter>.xlsx`

Invalid years or quarters return the same validation behavior as the offering
endpoint.

## Reports UI

Rename the tab:

- From: `Quarterly offerings`
- To: `Quarterly Financial Report`

The selected tab shows one Year field and one Quarter field, followed by two
stacked download sections:

1. `Quarterly Offering Excel`
2. `Quarterly Expenditure Excel`

Each section has its own download button and busy state. Both use the same
validated year and quarter. Errors remain visible in the report error area.

## Error Handling

- Reject years below 2000 and quarters outside 1 through 4.
- Reject unauthorized roles before querying data.
- Skip malformed budget or transaction records rather than corrupting totals.
- Return a usable workbook when the logo resource is missing or invalid.
- Surface backend download errors through the existing Reports error message.

## Testing And Verification

Backend tests cover:

- Calendar-quarter and fiscal-period calculations.
- Role authorization and invalid selections.
- Expense-only budget and transaction filtering.
- Deleted, income, invalid, and non-positive transaction exclusion.
- Budget-only and transaction-only rows.
- Reference labels and deterministic ordering.
- Case-insensitive `CONTINGENCY` aggregation across sub-categories.
- Monthly, quarter, cumulative, subtotal, normal total, contingency, and final totals.
- Endpoint headers, filename, and workbook bytes.
- Offering regression behavior after shared-renderer extraction.

Workbook tests cover:

- Sheet names and Korean titles.
- Shared column widths, column-B wrapping, merged cells, styles, formulas, and
  zero display.
- Landscape one-page print settings.
- Church logo presence and visible top-right anchor for both workbooks.
- Formula evaluation without spreadsheet errors.

Frontend tests cover:

- Renamed Quarterly Financial Report tab.
- Both stacked download actions.
- Shared year and quarter parameters.
- Correct filenames.
- Independent busy states and error handling.

Final verification includes:

- Full backend tests.
- Full frontend tests and production build.
- Artifact-tool inspection of values and formulas.
- Formula-error scans.
- Visual rendering of both sheets, including visible logos.

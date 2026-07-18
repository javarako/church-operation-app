# Quarterly Offering Excel Report Design

> **Final architecture:** This offering-only design is retained as the original
> workbook specification. It is extended by
> `2026-07-18-quarterly-financial-excel-report-design.md`, which adds the
> matching expenditure workbook, shared quarterly financial records and
> renderer, explicit H:J logo placement, and the combined Quarterly Financial
> Report UI.

## Goal

Add a downloadable quarterly offering workbook to the Reports page. The workbook must reproduce the structure, formatting, branding, and print behavior of `Quarterly report sample.xlsx` while using current church budgets, offerings, reference labels, and branding.

## User Interface

Add a `Quarterly offerings` tab to the Reports page for the same roles that can use general reports: `ADMIN`, `TREASURER`, `PASTOR`, and `VIEWER`.

The tab contains:

- Calendar year input, defaulting to the current year.
- Quarter selector with values 1 through 4.
- `Download Excel` command.

The download filename is `quarterly-offerings-<year>-q<quarter>.xlsx`.

## Endpoint

Add:

```text
GET /api/reports/quarterly-offerings.xlsx?year=<calendar-year>&quarter=<1-4>
```

The response content type is:

```text
application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

The endpoint uses the existing report authorization rule. Invalid years or quarters return a validation error.

## Calendar And Fiscal Periods

Calendar quarters are fixed:

- Q1: January 1 through March 31.
- Q2: April 1 through June 30.
- Q3: July 1 through September 30.
- Q4: October 1 through December 31.

The cumulative period starts at the configured fiscal-year start date belonging to the fiscal period that contains the quarter end date. The selected budget year is the year in which that fiscal period starts.

Example: with a July fiscal start, Q1 2026 belongs to the fiscal period July 1, 2025 through June 30, 2026 and uses fiscal-year budget records tagged `2025`.

## Data Sources

### Budget

Load active budgets for the derived fiscal budget year.

- Include `OFFERING_INCOME` budgets.
- For offering-income budgets, `category` is the Offering Fund code and `subCategory` is the Offering Category code.
- The offering-income budget whose fund code is `CARRY_OVER` is excluded from normal detail and used for the dedicated carry-over row.
- Do not use the legacy `CARRY_OVER` budget type for this report.

### Offerings

Load non-deleted offerings by `offeringSunday` from the fiscal start date through the calendar quarter end date.

- D, E, and F contain monthly totals for the three quarter months.
- G contains the quarter total.
- H contains the fiscal-start-through-quarter-end cumulative total.
- Offerings whose fund code is `CARRY_OVER` are excluded from normal fund detail and aggregated into the dedicated carry-over row.

### Reference Data

Use all Offering Fund and Offering Category reference records, including inactive references, to resolve historical labels and sort order.

- Funds sort by `sortOrder`, then label, then code.
- Categories sort by `sortOrder`, then label, then code within their parent fund.
- Unknown references fall back to the stored code.
- The detail row set is the union of budget keys and offering keys, so budget-only and offering-only categories remain visible.

## Workbook Structure

Create one worksheet named `Offering income`.

### Columns

| Column | Content |
|---|---|
| A | Fund grouping |
| B | Offering category |
| C | Annual offering budget |
| D | Quarter month 1 |
| E | Quarter month 2 |
| F | Quarter month 3 |
| G | Quarterly total |
| H | Fiscal cumulative total through the quarter end |
| I | Cumulative actual divided by budget (`H / C`) |
| J | Notes |

Column widths match the sample:

- A: `6.1640625`
- B: `27`
- C through I: `10.83203125`
- J: `16.5`

### Rows

1. Configured church logo, right-aligned in the H:J area, preserving its aspect ratio. Row height is 45 points.
2. Merged `A2:J2` title: `<year> 년도 <quarter>분기 수입`. Arial 18-point bold, underlined, horizontally centered. Row height is 23 points.
3. Blank.
4. Korean headers from the sample:
   - `구 분`
   - `항 목`
   - `예산`
   - `<month>월`
   - `<month>월`
   - `<month>월`
   - `분기 합계`
   - `누적`
   - `예산대비`
   - `비고`
5. Dynamic detail and summary rows.

For each normal fund:

- Create one row per category.
- Add a subtotal row with `(<sequence>) 소 계` in column B.
- Merge the fund label in column A from the first category row through the subtotal row.
- Use the reference-data label for the fund and category.

After all funds:

- Add a combined income total row, merging A:B. For two funds, the label is `(1) + (2) 합 계`; for other counts, list all sequence numbers in the same pattern.
- Add a carry-over row, merging A:B, labeled `전년도 이월금`.
- Add a final row, merging A:B, labeled `총 합 계`.

If there are no normal funds, the combined income total is still present with zero values. The carry-over and final rows are always present.

## Calculations

Use Excel formulas for derived workbook values:

- Detail G: `SUM(D:F)`.
- Detail I: `IF(C=0,"-",H/C)`.
- Fund subtotal C:H: `SUM` over the fund detail rows.
- Fund subtotal I: `IF(C=0,"-",H/C)`.
- Combined income total C:H: sum the fund subtotal rows.
- Combined income total I: `IF(C=0,"-",H/C)`.
- Carry-over C: the `CARRY_OVER` offering-fund budget amount.
- Carry-over D:F: monthly `CARRY_OVER` offering totals for the quarter.
- Carry-over G: `SUM(D:F)`.
- Carry-over H: fiscal-start-through-quarter-end cumulative `CARRY_OVER` offering total.
- Carry-over I: `IF(C=0,"-",H/C)`.
- Final C: combined income budget plus carry-over budget.
- Final D:H: combined income actual values plus carry-over actual values.
- Final I: `IF(C=0,"-",H/C)`.

Budget and offering values are numeric cells, not formatted strings.

## Formatting

Match the sample workbook:

- Arial throughout.
- Body font size 12.
- Gray header fill.
- Thin black table borders.
- Fund labels centered and wrapped.
- Category labels left-aligned.
- Subtotal and total numeric cells bold.
- Currency format displays zero values as blank.
- Percentage format: `0.00%`.
- Zero values in C:H display as blank.
- Column I displays `-` only when column C has a zero budget; a positive budget with zero actual displays `0.00%`.
- Gridlines remain visible as in the sample.

Rows 4 through the final total use the same compact row-height and border pattern as the sample.

## Print Settings

- Landscape orientation.
- Fit to one page wide by one page tall.
- Margins:
  - Top: 0.5 inches.
  - Bottom: 0.5 inches.
  - Left: 0.25 inches.
  - Right: 0.25 inches.
- Center horizontally on the page.
- Footer: `Page &P`.
- Print area: `A1:J<final-row>`.
- Print-title rows: `$1:$4`.

## Implementation Boundaries

- Add Apache POI OOXML support to the backend.
- Keep data aggregation separate from workbook rendering:
  - A report-data service calculates dates, rows, labels, and totals.
  - An Excel renderer owns workbook styles, merges, images, formulas, and print settings.
- Load the configured `church.information.branding.log-path` from the classpath using the same path normalization pattern as the tax receipt renderer.
- Do not store generated workbooks on the server.

## Testing

Backend tests cover:

- Calendar-quarter boundaries.
- Fiscal period and budget-year derivation.
- Monthly and cumulative aggregation by `offeringSunday`.
- Deleted offerings excluded.
- Budget-only and offering-only categories included.
- `CARRY_OVER` fund budget excluded from detail and returned separately.
- Reference labels and ordering.
- Authorization and input validation.
- Workbook sheet name, title, headers, column widths, row merges, formulas, number formats, configured logo, print area, repeating rows, margins, orientation, scaling, horizontal centering, and footer.

Frontend tests cover:

- Quarterly report tab visibility.
- Year and quarter controls.
- Download request parameters and filename.

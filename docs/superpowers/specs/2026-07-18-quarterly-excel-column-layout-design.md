# Quarterly Excel Column Layout Design

## Goal

Update the shared quarterly financial Excel layout for both Offering and
Expenditure workbooks.

## Required Layout

- Column A width: `7`
- Column B width: `28`
- Column B wrap text: enabled for every styled cell, including header, detail,
  subtotal, special, and total rows
- Column I width: `8.5`
- Column J width: `16`

Columns C through H retain their current widths. All formulas, merged cells,
borders, alignment, number formats, logo placement, and Adjust to 100% print
settings remain unchanged.

## Implementation

Keep the change in `QuarterlyFinancialExcelService`, which renders both
workbook types. Set the four requested widths in `configureColumns`.

Column B shares styles with other columns, so changing the existing shared
styles would wrap unrelated columns. After report rows are created, iterate
through column B cells and apply cached wrapped copies of their existing
styles. Cache by source style index to avoid creating one style per cell.

## Verification

Automated tests must assert the requested widths and column-B wrapping in both
Offering and Expenditure workbooks. Representative workbooks must be rendered
and visually inspected to confirm text is legible and the print layout remains
Letter landscape at Adjust to 100%.

# Quarterly Excel Column Layout Design

## Goal

Update the shared quarterly financial Excel layout for both Offering and
Expenditure workbooks.

## Required Layout

- Column A raw OOXML width: `7.83203125`
- Column B raw OOXML width: `28.83203125`
- Column B wrap text: enabled for every styled cell, including header, detail,
  subtotal, special, and total rows
- Column I raw OOXML width: `9.33203125`
- Column J raw OOXML width: `16.83203125`

Columns C through H retain their current widths. All formulas, merged cells,
borders, alignment, number formats, logo placement, and Adjust to 100% print
settings remain unchanged.

These stored values reproduce the requested displayed Excel widths from the
reference workbook. Writing the displayed numbers directly produces narrower
columns because Excel includes character-width padding in the stored value.

## Implementation

Keep the change in `QuarterlyFinancialExcelService`, which renders both
workbook types. Set the four reference workbook widths in `configureColumns`.

Column B shares styles with other columns, so changing the existing shared
styles would wrap unrelated columns. After report rows are created, iterate
through column B cells and apply cached wrapped copies of their existing
styles. Cache by source style index to avoid creating one style per cell.

## Verification

Automated tests must assert the requested widths and column-B wrapping in both
Offering and Expenditure workbooks. Representative workbooks must be rendered
and visually inspected to confirm text is legible and the print layout remains
Letter landscape at Adjust to 100%.

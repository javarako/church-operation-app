# Official Tax Receipt Amount Layout Design

## Scope

Simplify only the generated official tax receipt PDF. Receipt generation, report filters, stored receipt snapshots, tax calculations, and audit fields remain unchanged.

## PDF Content

Each duplicated half-page receipt removes these visible fields:

- Offering number
- Amount of gift
- Advantage amount
- Advantage description
- Eligible amount

The donation section displays one prominent value labeled `Amount`, using the receipt's total gift amount.

## Layout

Use the space released by the removed fields to improve readability. Increase every visible PDF text size by exactly 2 points while preserving the existing half-letter duplication, church identity, receipt metadata, thank-you note, authorized signature, and CRA information. Both receipt copies must remain identical and must fit within their half page.

## Verification

PDF extraction tests must confirm that `Amount` appears twice and that every removed label is absent. Existing tests continue to verify the duplicated receipt content and one-page letter layout.

# Reference Parent Label Design

## Goal

Display the parent reference label instead of its raw code in the Reference Data list.

## Behavior

- Offering Category rows resolve `parentCode` from Offering Fund reference data.
- Financial Sub-category rows resolve `parentCode` from Financial Category reference data.
- Rows without a parent display `-`.
- Unknown parent codes fall back to the raw code.
- Stored and API `parentCode` values remain unchanged.

## Verification

- Verify an Offering Category with parent code `GENERAL` displays its parent label.
- Verify a Financial Sub-category with parent code `ADMIN` displays its parent label.
- Run the complete frontend test suite and production build.

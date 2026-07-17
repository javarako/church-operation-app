# Budget Income Label Design

## Goal

Use the label "Income" consistently on the Budgets page.

## Scope

- Change the Budgets filter option from "Offering income" to "Income".
- Change the budget form option from "Offering income" to "Income".
- Change the budget table display label from "Offering income" to "Income".
- Preserve the internal and API value `OFFERING_INCOME`.

## Verification

- Verify both budget-type selectors display "Income" with value `OFFERING_INCOME`.
- Verify an income budget row displays "Income".
- Run the complete frontend test suite and production build.

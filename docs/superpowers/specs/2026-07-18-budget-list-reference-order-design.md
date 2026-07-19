# Budget List Reference Order Design

## Goal

Sort the Budgets page list in a predictable business order:

1. Income budgets.
2. Expense budgets.
3. Parent reference-data `sortOrder` within each type.
4. Child reference-data `sortOrder` within each parent.

The ordering must be applied before pagination so every page follows the same
sequence.

## Scope

This change affects only the Budgets page list. It does not change the budget
API, MongoDB query order, budget forms, filters, or reference-data maintenance.

## Ordering Rules

Income budgets use:

- `OFFERING_FUND` as the parent reference.
- `OFFERING_CATEGORY` as the child reference.

Expense budgets use:

- `FINANCIAL_CATEGORY` as the parent reference.
- `FINANCIAL_SUB_CATEGORY` as the child reference.

Rows are sorted by:

1. Budget type: `OFFERING_INCOME`, then `EXPENSE`.
2. Parent reference `sortOrder`.
3. Parent reference label, then code, as deterministic tie-breakers.
4. Child reference `sortOrder`.
5. Child reference label, then code, as deterministic tie-breakers.
6. Budget ID as the final stable tie-breaker.

Reference codes missing from the currently loaded reference data remain visible.
They sort after configured references and use their code as the display and
ordering fallback. The standard reference-data endpoint intentionally returns
active records only, so inactive historical codes follow the same fallback
behavior. This preserves Treasurer access without depending on the ADMIN-only
maintenance endpoint.

## Frontend Design

`BudgetsView.vue` will derive a sorted list from the loaded budget and reference
data collections. The existing type filter will run before sorting, and
pagination will consume the sorted result.

No new API calls or backend changes are required because the page already loads
the four reference-data collections needed to calculate the order.

## Testing

`BudgetsView.test.ts` will verify:

- Income rows appear before expense rows regardless of API response order.
- Income rows follow offering fund and offering category `sortOrder`.
- Expense rows follow financial category and sub-category `sortOrder`.
- Unknown reference codes remain visible and sort after configured references.
- Sorting is applied before a configured pagination boundary.
- The existing type filter continues to work with the sorted list.

The focused Budgets view tests and the complete frontend test suite will be run,
followed by a production frontend build.

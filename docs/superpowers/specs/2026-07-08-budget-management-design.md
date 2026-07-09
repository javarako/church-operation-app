# Budget Management Design

## Purpose

Add fiscal-year budget maintenance so Admin and Treasurer users can enter planned offering income and expense budgets before a new fiscal year starts. This gives the app the budget data needed for yearly actual-versus-budget financial reporting.

## Scope

This slice includes:

- Budget list API and Vue screen.
- Fiscal year selector.
- One carry-over amount row from the previous fiscal year.
- Offering income budgets by offering fund/category.
- Expense budgets by financial category and optional financial sub-category.
- Budget field named `budget`.
- Optional memo/notes field.
- Same list/detail editing pattern as Members, Offerings, and Finance: clicking a row loads it into the form and highlights it.
- Trash-bin icon for budget delete.
- Reference-data validation for offering fund/category, financial category, and financial sub-category.
- Duplicate protection for fiscal year, budget type, category, and sub-category, including only one carry-over row per fiscal year.

This slice does not include:

- Actual-versus-budget report calculations.
- Budget approval workflow.
- Budget import/export.
- Budget version history.
- Audit persistence.

## Roles And Access

- `ADMIN`: can list, create, edit, and delete budgets.
- `TREASURER`: can list, create, edit, and delete budgets.
- `VIEWER`, `PASTOR`, `MEMBERSHIP`, and `MEMBER`: no budget maintenance access in this slice.

The backend enforces role access. The frontend guards `/budgets` with existing finance roles.

## Fiscal Year

The app uses the configurable fiscal-year start month:

```yaml
church:
  fiscal-year:
    start-month: ${CHURCH_FISCAL_YEAR_START_MONTH:1}
```

The default is January (`1`). Budget records store a fiscal-year number, such as `2026`. The Budget screen lets users select the fiscal year directly rather than calculating it from dates in the first slice. Reports can later use the configured fiscal-year start month to calculate actual date ranges.

## Data Model

Add a `Budget` document.

Fields:

- `id`
- `fiscalYear`
- `budgetType`: `CARRY_OVER`, `OFFERING_INCOME`, or `EXPENSE`
- `category`
- `subCategory`
- `budget`
- `memo`
- `createdBy`
- `createdAt`
- `updatedBy`
- `updatedAt`
- `deleted`
- `deletedBy`
- `deletedAt`

Validation:

- Fiscal year is required and must be reasonable for planning, using `2000` to `2100`.
- Budget type is required.
- Budget must be zero or greater.
- For `CARRY_OVER`, category and sub-category are ignored. Only one active carry-over row is allowed per fiscal year.
- For `OFFERING_INCOME` and `EXPENSE`, category is required.
- For `OFFERING_INCOME`, category must match active `OFFERING_FUND_CATEGORY` reference data, and sub-category is ignored.
- For `EXPENSE`, category must match active `FINANCIAL_CATEGORY` reference data.
- For `EXPENSE`, sub-category is optional, but when present it must match active `FINANCIAL_SUB_CATEGORY` with `parentCode` equal to the selected category.
- Active offering and expense budgets must be unique by fiscal year, budget type, category, and sub-category.

## Backend Flow

`BudgetService` owns budget workflows:

1. `listBudgets(actor, fiscalYear)` returns non-deleted rows for the selected fiscal year sorted by budget type, category, and sub-category.
2. `createBudget(actor, request)` validates reference data and duplicate constraints, then saves a budget.
3. `updateBudget(actor, id, request)` edits an active budget and revalidates duplicate constraints.
4. `deleteBudget(actor, id)` soft-deletes a budget.

Duplicate checks ignore the same record during update and ignore soft-deleted records.

## API

### List Budgets

`GET /api/budgets?fiscalYear=2026`

Returns non-deleted budgets for the selected fiscal year.

### Create Budget

`POST /api/budgets`

Creates a carry-over, offering income, or expense budget.

### Update Budget

`PUT /api/budgets/{id}`

Updates an active budget.

### Delete Budget

`DELETE /api/budgets/{id}`

Soft-deletes a budget and returns the deleted budget.

## Frontend

Route: `/budgets`

Layout:

- Header: `Budgets`.
- Fiscal year selector in the toolbar.
- Button: `Add budget`.
- Left panel with budget table.
- Right panel with budget form.
- Same interaction pattern as Members, Offerings, and Finance:
  - click a row to load it into the form
  - selected row is highlighted
  - no text Edit button
  - delete uses a trash-bin icon

Table columns:

- Type
- Fiscal year
- Category
- Sub-category
- Budget
- Memo
- Delete icon

Form behavior:

- Budget type dropdown: `Carry over`, `Offering income`, or `Expense`.
- Fiscal year numeric input.
- Budget numeric input.
- For `CARRY_OVER`, category and sub-category controls are hidden or disabled, and the row represents the amount carried over from the previous fiscal year.
- For `OFFERING_INCOME`, category dropdown loads active `OFFERING_FUND_CATEGORY`.
- For `EXPENSE`, category dropdown loads active `FINANCIAL_CATEGORY`.
- For `EXPENSE`, sub-category dropdown loads active `FINANCIAL_SUB_CATEGORY?parentCode=<category>` after a category is selected.
- Changing budget type clears category and sub-category.
- Changing expense category clears sub-category and reloads filtered options.

## Testing

Backend tests:

- Admin/Treasurer can list budgets for a fiscal year.
- Viewer cannot maintain budgets.
- Carry-over budget allows one active row per fiscal year.
- Offering income budget validates active offering fund/category.
- Expense budget validates active financial category.
- Expense budget rejects a sub-category outside the selected category.
- Duplicate active budget rows are rejected.
- Update ignores the current row for duplicate checks.
- Delete soft-deletes a budget.

Frontend verification:

- Build/type-check passes.
- Budget route loads the real view.
- Fiscal year filter reloads budget rows.
- Budget type switches the category reference-data source.
- Expense category filters sub-categories.
- Row click loads a budget into the form.
- Delete uses a trash-bin icon and calls the delete API.

## Open Follow-Ups

- Actual-versus-budget financial report.
- Budget approval workflow.
- Budget import/export.
- Budget change audit trail.

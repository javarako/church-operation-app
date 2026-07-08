# Finance Transactions Design

## Purpose

Add the first Finance Transactions slice. Treasurer and Admin users can view offering-generated income transactions, add/edit/delete manual expense transactions, and manage cheque/approval details. This gives the app a real `/finance` screen and prepares the data needed for budget comparison reports.

## Scope

This slice includes:

- Finance transaction list API and Vue screen.
- Existing offering-generated income rows shown as read-only finance records.
- Manual expense create, edit, and soft-delete/cancel.
- Category dropdown from `FINANCIAL_CATEGORY`.
- Sub-category dropdown filtered by selected category using `FINANCIAL_SUB_CATEGORY.parentCode`.
- Expense fields: date, amount, category, sub-category, HST included, cheque number, cheque cleared, payable to, treasurer approval name, memo.
- Same list/detail editing pattern as Members and Offerings: clicking a row loads it into the form and highlights it.
- Trash-bin icon for expense delete.

This slice does not include:

- Evidence attachment upload.
- Full audit persistence.
- Budget report calculations.
- CSV/PDF export.
- Editing offering-generated income from the finance screen.

## Roles And Access

- `ADMIN`: can list finance transactions, create/edit/delete manual expenses, and view offering-generated income.
- `TREASURER`: can list finance transactions, create/edit/delete manual expenses, and view offering-generated income.
- `VIEWER`: can view finance transactions if the route is enabled for read-only reporting.
- `PASTOR`, `MEMBERSHIP`, and `MEMBER`: no finance transaction access in this slice.

The backend enforces role access. The frontend hides unavailable navigation and guards `/finance`.

## Data Model

The existing `FinancialTransaction` document is extended for manual expenses.

Fields:

- `id`
- `type`: `INCOME` or `EXPENSE`
- `transactionDate`
- `amount`
- `category`
- `subCategory`
- `hstIncluded`
- `chequeNo`
- `chequeCleared`
- `payableTo`
- `treasurer`
- `memo`
- `sourceType`: `OFFERING` for offering-generated income, `MANUAL` for manual expenses
- `sourceId`: offering id for offering-generated income, empty for manual expenses
- `createdBy`
- `createdAt`
- `deleted`
- `deletedBy`
- `deletedAt`

Validation:

- Manual expenses require `type = EXPENSE`.
- Manual expense amount must be greater than zero.
- Manual expenses require date and category.
- Category must match active `FINANCIAL_CATEGORY` reference data.
- Sub-category is optional, but when present it must match active `FINANCIAL_SUB_CATEGORY` with `parentCode` equal to the selected category.
- Offering-generated income rows cannot be edited or deleted from `/finance`.

## Backend Flow

`FinancialTransactionService` owns finance workflows:

1. `listTransactions(actor)` returns non-deleted rows sorted newest first.
2. `createExpense(actor, request)` validates a manual expense and saves a transaction with `type = EXPENSE` and `sourceType = MANUAL`.
3. `updateExpense(actor, id, request)` edits only manual expense rows.
4. `deleteExpense(actor, id)` soft-deletes only manual expense rows.

Offering-created income remains owned by Offering workflows. Finance can display those rows but not modify them.

## API

### List Finance Transactions

`GET /api/finance/transactions`

Returns non-deleted finance transactions sorted newest first.

### Create Expense

`POST /api/finance/expenses`

Creates a manual expense.

### Update Expense

`PUT /api/finance/expenses/{id}`

Updates a manual expense.

### Delete Expense

`DELETE /api/finance/expenses/{id}`

Soft-deletes a manual expense and returns the deleted transaction.

## Frontend

Route: `/finance`

Layout:

- Header: `Finance`.
- Button: `Add expense`.
- Left panel with finance transaction table.
- Right panel with manual expense form.
- Same interaction pattern as Members and Offerings:
  - click an editable expense row to load it into the form
  - selected row is highlighted
  - no text Edit button
  - delete uses a trash-bin icon

Table columns:

- Type
- Date
- Category/Sub-category
- Amount
- HST
- Cheque #
- Cleared
- Payable To
- Approved By
- Source
- Delete icon for manual expenses only

Form behavior:

- Form is for manual expenses only.
- Category dropdown loads from `FINANCIAL_CATEGORY`.
- Sub-category dropdown is disabled until a category is selected, then loads active children from `FINANCIAL_SUB_CATEGORY?parentCode=<category>`.
- HST included and cheque cleared are checkboxes.
- Treasurer field defaults to the logged-in user's display name when available.
- Selecting an offering-generated income row does not enter edit mode; the row remains read-only.

## Testing

Backend tests:

- Admin/Treasurer can list non-deleted transactions.
- Viewer can list transactions read-only.
- Manual expense create validates amount, date, category, and sub-category parent.
- Manual expense update changes fields.
- Manual expense delete soft-deletes the transaction.
- Offering-generated income cannot be updated or deleted through expense APIs.

Frontend verification:

- Build/type-check passes.
- Finance route loads the real view.
- Category selection filters sub-categories.
- Row click loads manual expense into the form.
- Delete uses a trash-bin icon and calls the delete API only for manual expenses.

## Open Follow-Ups

- Evidence attachment upload.
- Audit entries for create/update/delete/approval changes.
- Finance report against budget.
- Expense pagination and date-range filtering.

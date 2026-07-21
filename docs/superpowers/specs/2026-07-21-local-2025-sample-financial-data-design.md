# Local 2025 Sample Financial Data Design

## Goal

Populate only the current local MongoDB database with representative 2025
financial data for yearly-report and year-end-closing testing. This is a
one-time data operation, not an application startup seeder or production
feature.

## Scope

- Copy every active, non-deleted 2026 budget into fiscal year 2025.
- Set each copied budget to 95% of its 2026 value, rounded to two decimal
  places.
- Generate 48 offerings and 24 manual expenses across all twelve months of
  2025.
- Use the application's existing active members and reference data.
- Do not create a database backup.
- Do not modify application source code or configuration.

## Population Method

Use the running application's authenticated REST APIs. This keeps validation,
actor metadata, offering-to-income linking, and normal business rules intact.
Direct MongoDB inserts are not used.

Before writing data, perform a preflight check:

1. Confirm 2026 budgets exist.
2. Confirm no active 2025 budgets exist.
3. Confirm no offering or manual expense has a memo containing
   `[SAMPLE-2025]`.
4. Confirm the required active reference data exists.

If any duplicate guard fails, stop without writing records and report the
conflict.

## Budgets

For each active 2026 budget:

- copy its budget type, category, sub-category, and memo;
- set `fiscalYear` to `2025`;
- set `budget` to `2026 budget * 0.95`, rounded to two decimal places; and
- append `[SAMPLE-2025]` to the memo while preserving any existing memo.

This includes ordinary income and expense budgets and the special
`CARRY_OVER` and `CONTINGENCY` rows when they exist in the 2026 data.

## Offerings

Generate four offering records per month, for 48 records total. Dates use two
Sundays per month, with `offeringDate` and `offeringSunday` set to the same
Sunday.

Records rotate across active Offering Fund, Offering Category, Payment Method,
and Giving Type reference values. The set includes member, anonymous, and group
giving. Member offerings use existing active members; no member records are
created. If no active non-administrator member is available, the system
administrator is used for the member-giving slots.

Amounts are deterministic and distributed across categories so total ordinary
2025 offerings equal 92% of the copied 2025 ordinary income budget, rounded to
two decimal places. Any rounding remainder is assigned to the final ordinary
offering record.
`CARRY_OVER` receives representative actual activity when that active fund and
budget exist. Every memo contains `[SAMPLE-2025]`.

Each offering is created through `/api/offerings`, allowing the application to
create its linked income transaction automatically.

## Expenditures

Generate two manual expenses per month, for 24 records total. Dates are spread
throughout each month and rotate across active Financial Category and matching
Financial Sub-category values.

Amounts are deterministic and distributed so total ordinary 2025 expenses
equal 85% of the copied 2025 ordinary expense budget, rounded to two decimal
places. Any rounding remainder is assigned to the final ordinary expense
record. `CONTINGENCY` receives representative actual activity when that active
category and budget exist.
Cheque, HST, cleared status, payee, treasurer, and memo values vary enough to
exercise report fields. Every memo contains `[SAMPLE-2025]`.

Expenses are created through `/api/finance/expenses`.

## Error Handling

- Stop on the first failed API request.
- Report the record type, sequence, and server error.
- Do not automatically delete records already created before a failure.
- Report partial counts clearly so any cleanup can be deliberate.

## Verification

After insertion, verify through the APIs and report:

- the 2025 budget count and total by budget type;
- exactly 48 active 2025 offerings with `[SAMPLE-2025]`;
- exactly 48 corresponding active linked income transactions;
- exactly 24 active 2025 manual expenses with `[SAMPLE-2025]`;
- records span January through December 2025; and
- the 2025 yearly Offering and Expenditure report endpoints generate
  successfully.

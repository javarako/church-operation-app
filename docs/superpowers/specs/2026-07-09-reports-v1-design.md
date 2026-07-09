# Reports V1 Design

## Goal

Add the first Reports screen for church operations. The screen gives permitted users weekly offering totals, member giving summaries, official annual tax-return extraction, and yearly actual-versus-budget finance reporting.

This slice focuses on accurate tables and CSV export. It does not include PDF receipt formatting, charts, or the broader audit trail implementation.

## Scope

Included:

- `/reports` route in the Vue app.
- Four report tabs:
  - Weekly Offering Status.
  - Offering Summary for Member(s).
  - Official Offering Tax Return.
  - Financial Actual vs Budget.
- Backend report APIs with role checks.
- CSV export from the displayed report rows.
- Hidden tax-report UI for users without permission.
- Deleted offerings and deleted financial transactions excluded from all calculations.

Excluded:

- PDF tax receipts.
- Audit entry persistence for tax extraction.
- Dashboard mini charts.
- Scheduled report delivery.

## Roles

- `ADMIN`: can view and export all reports.
- `TREASURER`: can view and export all reports.
- `PASTOR`: can view Weekly Offering Status, Offering Summary for Member(s), and Financial Actual vs Budget.
- `VIEWER`: can view Weekly Offering Status, Offering Summary for Member(s), and Financial Actual vs Budget.
- `MEMBERSHIP`: no access to `/reports` in this slice.
- `MEMBER`: no access to `/reports`; member self-service offering status remains separate from this slice.

The backend must enforce these permissions. The frontend should hide the Official Offering Tax Return tab unless the signed-in user is `ADMIN` or `TREASURER`.

## Report Definitions

### Weekly Offering Status

Purpose: show weekly giving totals for operational review.

Filters:

- Offering Sunday start date.
- Offering Sunday end date.
- Optional fund/category.
- Optional payment method.

Rows group active offerings by:

- Offering Sunday.
- Fund/category.
- Giving type: member, anonymous, or group.
- Payment method.

Each row includes count and total amount. Anonymous and group offerings are included. Deleted offerings are excluded.

### Offering Summary for Member(s)

Purpose: show giving totals for selected members across a date range.

Filters:

- Start date.
- End date.
- Optional member search/selection.
- Optional fund/category.

Rows group active member-linked offerings by:

- Member.
- Fund/category.

Each row includes member name, primary email, offering number, fund/category, count, and total amount. Anonymous and group offerings are excluded because this report is member-based. If no member is selected, the report summarizes all member-linked offerings in the date range.

### Official Offering Tax Return

Purpose: extract annual member giving totals suitable for official tax-return preparation by Treasurer or Admin users.

Filters:

- Tax year.
- Optional member search/selection.

The tax year uses the calendar year, January 1 through December 31. It does not use the church fiscal-year configuration.

Rows include:

- Church name, address, contact information, and treasurer name from application configuration.
- Member name, primary email, offering number, and contact address when available.
- Tax year.
- Giving date.
- Fund/category.
- Amount.

The screen shows an Official Use warning before export and requires confirmation before CSV extraction. Audit persistence is intentionally left for the later audit module, but the confirmation should remain so the user flow does not change when audit logging is added.

### Financial Actual vs Budget

Purpose: compare yearly income and expenses against configured budgets.

Filters:

- Fiscal year.

The report uses the configured fiscal-year start month from `application.yml` to calculate the date range for actuals.

Rows include:

- Carry-over amount from the active carry-over budget row for the fiscal year.
- Offering income actuals grouped by fund/category.
- Offering income budgets from offering income budget rows.
- Expense actuals grouped by category and sub-category.
- Expense budgets from expense budget rows.
- Budget amount.
- Actual amount.
- Variance.

Income actuals come from offering-generated financial income transactions, using the current finance behavior that summarizes offerings by day and category while excluding deleted/cancelled offerings. Expense actuals come from non-deleted manual expense transactions.

## Backend Design

Add `ReportController` under `com.church.operation.rest` and `ReportService` under `com.church.operation.service`.

Suggested endpoints:

- `GET /api/reports/weekly-offerings`
- `GET /api/reports/member-offerings`
- `GET /api/reports/tax-return`
- `GET /api/reports/financial-budget`

The endpoints return JSON rows. CSV generation is handled in the frontend from the returned rows for this slice.

DTOs should live under `com.church.operation.dto` and use explicit request parameters rather than relying on Java parameter-name reflection. This keeps the previous `-parameters` issue from reappearing.

Repositories will add focused query methods for:

- Active offerings by offering Sunday range.
- Active offerings by date range for member summaries and tax reports.
- Active financial transactions by fiscal-year date range.
- Active budgets by fiscal year.

## Frontend Design

Add a `/reports` route guarded for `ADMIN`, `TREASURER`, `PASTOR`, and `VIEWER`.

The page uses the same operational layout pattern as Members, Offerings, Finance, and Budgets:

- Title and short subtitle.
- Compact tab control for the four report types.
- Filter panel at the top.
- Results table below.
- Export button with an icon.

The Official Offering Tax Return tab is visible only to `ADMIN` and `TREASURER`. Its export button opens a confirmation dialog before creating the CSV.

## Error Handling

- Empty reports show a quiet empty state in the table area.
- Invalid date ranges show a form-level error and do not call the backend.
- Backend permission failures return forbidden errors.
- Backend validation errors return a clear message for invalid fiscal year, tax year, or date range.

## Testing

Backend tests:

- Weekly report excludes deleted offerings and groups by Sunday, category, giving type, and payment method.
- Member summary excludes anonymous/group offerings.
- Tax report is blocked for non-Admin/non-Treasurer roles.
- Tax report uses calendar-year dates.
- Financial report uses configured fiscal-year start month.
- Financial report excludes deleted offerings and deleted financial transactions.
- Financial report includes carry-over budget row.

Frontend tests:

- Reports route renders for allowed roles.
- Tax tab is hidden for Pastor and Viewer.
- Weekly filters call the weekly endpoint and render rows.
- Member summary renders member-linked rows only.
- Tax export requires confirmation.
- Financial report renders budget, actual, and variance columns.

## Acceptance Criteria

- Admin and Treasurer can open `/reports` and use all four report tabs.
- Pastor and Viewer can open `/reports` but cannot access the official tax report.
- Deleted offerings do not appear in offering reports or finance actuals.
- Weekly offering report groups by offering Sunday, fund/category, giving type, and payment method.
- Member summary report supports all members or a selected member.
- Official tax report exports calendar-year member giving data with church information.
- Financial report compares fiscal-year actuals to budget rows and includes carry-over.
- CSV exports match the rows visible in each report table.

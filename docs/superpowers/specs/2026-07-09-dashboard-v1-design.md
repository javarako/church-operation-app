# Dashboard V1 Design

## Goal

Replace the placeholder home page with a useful staff dashboard for daily church operations. The dashboard should give signed-in staff a fast read on offering, budget, finance, member, and report activity without duplicating the full detail screens.

This slice focuses on concise summary cards and quick navigation. It does not include charts, drill-down modals, alerts, or a dedicated backend dashboard API.

## Scope

Included:

- Update the existing `/` dashboard route.
- Summary cards for recent giving, month-to-date giving, year-to-date giving, and fiscal-year budget performance.
- Membership summary for users who manage members.
- Recent finance activity summary for finance users.
- Role-aware quick links to existing operational screens.
- Frontend tests for role-aware content and displayed totals.

Excluded:

- New backend dashboard endpoint.
- Graphs or chart library integration.
- Customizable dashboard widgets.
- Scheduled alerts or notifications.
- Member self-service dashboard changes.

## Roles

- `ADMIN`: can see all dashboard cards and all quick links.
- `TREASURER`: can see offering, finance, budget, and report summaries and quick links.
- `PASTOR`: can see read-only offering, budget/report summary cards and report quick links.
- `VIEWER`: can see read-only offering, budget/report summary cards and report quick links.
- `MEMBERSHIP`: can see member summary and member/reference-data quick links.
- `MEMBER`: no access to the staff dashboard in this slice; member self-service remains on `/profile`.

The existing route guard for `/` continues to use staff roles.

## Dashboard Content

### Offering Summary

Purpose: show current giving momentum.

Cards:

- This week offering total, using the current week Sunday range.
- Month-to-date offering total.
- Year-to-date offering total.

The dashboard uses the existing weekly offering report API and totals returned rows. Anonymous and group offerings are included because the weekly report includes them. Deleted offerings remain excluded by the report API.

### Fiscal Snapshot

Purpose: show how the current fiscal year is tracking against budgets.

Cards:

- Budgeted income.
- Actual income.
- Budgeted expense.
- Actual expense.
- Net actual, calculated as actual income minus actual expense.

The dashboard uses the existing financial actual-vs-budget report API for the current year as the selected fiscal-year number. The backend applies the configured fiscal-year start month from `application.yml` when it calculates actual date ranges, matching the Reports screen behavior.

### Membership Summary

Purpose: help membership users quickly understand member records.

Cards:

- Total members.
- Active members.
- Locked accounts.

The dashboard uses the existing members API. This panel is visible only to `ADMIN` and `MEMBERSHIP`.

### Recent Finance Activity

Purpose: give finance users a quick read on recent manual expense and offering-income activity.

Cards:

- Recent income total from finance transactions.
- Recent expense total from finance transactions.
- Recent transaction count.

The dashboard uses the existing finance transactions API and summarizes current-month rows. This panel is visible only to `ADMIN` and `TREASURER`.

### Quick Links

Quick links are displayed as simple action buttons matching the existing app style:

- Members for `ADMIN` and `MEMBERSHIP`.
- Offerings, Finance, and Budgets for `ADMIN` and `TREASURER`.
- Reference Data for `ADMIN`, `TREASURER`, and `MEMBERSHIP`.
- Reports for `ADMIN`, `TREASURER`, `PASTOR`, and `VIEWER`.

## Frontend Design

Use the same operational layout as Members, Offerings, Finance, Budgets, and Reports:

- Page header with title and short subtitle.
- Compact summary grid.
- White panels with 8px radius.
- No hero layout or marketing copy.
- Text should stay readable and fit on mobile and desktop.

The dashboard should remain calm and scannable. Cards should present labels, values, and short secondary text only where it clarifies the date range.

## Data Flow

On mount, the dashboard loads only the datasets allowed for the signed-in user's roles:

- Weekly offering report for offering summary roles.
- Financial budget report for report roles.
- Members for membership roles.
- Finance transactions for finance roles.

Each request is independent. If one section fails, the dashboard shows an error for that section while leaving other available sections visible.

## Error Handling

- Empty data shows `$0.00` or `0` instead of a broken state.
- Failed sections show a short inline error inside that panel.
- The whole page should not fail because one dashboard request fails.
- Date calculations use local dates and ISO `YYYY-MM-DD` strings.

## Testing

Frontend tests:

- Admin sees offering, fiscal, membership, finance, and quick-link sections.
- Treasurer sees finance links and does not see member-only summary.
- Membership user sees member summary and does not see finance summary.
- Pastor or Viewer sees read-only offering/fiscal/report content only.
- Totals are calculated from mocked API rows.
- Section failure shows an inline error without hiding other sections.

Backend tests:

- No new backend tests are needed for this slice because the dashboard reuses existing report, member, and finance APIs.

## Acceptance Criteria

- `/` no longer shows only the welcome placeholder.
- Staff users see role-appropriate dashboard summary cards.
- Admin and Treasurer can quickly reach Offerings, Finance, Budgets, and Reports.
- Membership users can quickly reach Members and Reference Data.
- Pastor and Viewer can see read-only report-oriented summary information.
- Deleted offerings and deleted transactions remain excluded through the existing APIs.
- Dashboard tests pass with mocked API data.

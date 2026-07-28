# Year-End Closing Design

## Goal

Add an independent year-end closing lifecycle to the yearly Offering and
Expenditure Excel reports. Closing preserves the exact approved workbook in
MongoDB, closed downloads return that immutable snapshot, and reopening
returns the report to live draft generation while retaining complete history.

## Scope

This change includes:

- Independent closing for yearly Offering and Expenditure workbooks.
- Current-password confirmation for closing and reopening.
- Admin and Treasurer lifecycle authorization.
- MongoDB GridFS storage of immutable closed workbooks.
- Audit-only history for closed and reopened versions.
- Closed, not-closed, and reopened status display with datetimes.
- Draft and finalized workbook titles and status markings.
- Live report generation before closing and after reopening.
- Snapshot-only download while closed.
- Backend, frontend, persistence, workbook, and security tests.

This change does not:

- Lock, archive, or delete offering, expense, budget, or reference data.
- Add a user-facing closing-history list.
- Change quarterly reports.
- Change the Official Tax Report or its PDF lifecycle. Persisting exact issued
  Tax Receipt PDFs in GridFS is a separate follow-up feature.

## Report Types

Each selected fiscal year has two independent lifecycle streams:

- `OFFERING`
- `EXPENDITURE`

Closing one report does not close or otherwise affect the other report. Each
stream may have only one active closed version at a time.

## Roles And Access

The following roles may close or reopen a yearly report:

- `ADMIN`
- `TREASURER`

The backend must enforce lifecycle authorization. Frontend visibility is not
a security boundary.

The existing yearly download roles remain unchanged:

- `ADMIN`
- `TREASURER`
- `PASTOR`
- `VIEWER`

Pastor and Viewer may download a live report or a stored closed snapshot, but
they do not see closing or reopening controls. `MEMBERSHIP` and `MEMBER` retain
no yearly-report access.

## Password Confirmation

Clicking `Year-End Closing` or `Reopen Closing` opens a password-confirmation
dialog immediately. The dialog identifies the selected fiscal year, report
type, and requested action and provides `Confirm` and `Cancel` commands.

The backend reloads the signed-in member record from MongoDB and verifies:

- The submitted password matches that current member's password hash.
- The member is active and unlocked.
- The member currently has `ADMIN` or `TREASURER`.
- The report is in the state required by the requested action.

An Admin confirms with the Admin's own password. A Treasurer confirms with the
Treasurer's own password. Passwords are never persisted, logged, returned, or
included in audit metadata.

## Fiscal-Year Eligibility

The selected fiscal period uses the configured `church.fiscal-year.start-month`.
Year-End Closing is available only when the current date is after the selected
fiscal period's end date.

Before that date:

- `Year-End Closing` remains visible to Admin and Treasurer.
- The button is grey and disabled.
- The UI exposes the fiscal end date through accessible explanatory text or a
  tooltip.
- The backend independently rejects premature closing attempts.

Reopening an existing closed report is not restricted by the fiscal end date.

## Lifecycle

### Not Closed

- Downloads generate the current workbook from live data.
- The workbook uses a draft title containing `예산안`.
- Row 3 identifies the workbook as a draft whose year-end closing is not
  completed.
- Admin and Treasurer see `Year-End Closing` when the fiscal period is
  eligible.

### Closing

1. Admin or Treasurer clicks `Year-End Closing` for one report type.
2. The UI collects the signed-in user's current password.
3. The backend validates authorization, account state, password, fiscal end
   date, and absence of an active closing.
4. The backend builds the current report model and renders the workbook once.
5. The renderer applies the finalized title, row-3 closing status, closing
   datetime, and version.
6. The backend calculates a SHA-256 checksum and stores the exact workbook
   bytes in GridFS.
7. The backend saves an active closing metadata record referring to the GridFS
   file.
8. The backend records the successful closing audit event.
9. The UI refreshes the report status and changes the action to
   `Reopen Closing`.

An incomplete operation must not leave an active closing. If GridFS storage
succeeds but metadata persistence fails, the newly stored GridFS file is
removed. A closing failure is audited without sensitive data.

### Closed

- Downloads read the exact workbook bytes from GridFS.
- The report is never regenerated while an active closing exists.
- Missing bytes or a checksum mismatch returns an explicit error and does not
  fall back to live generation.
- Admin and Treasurer see `Reopen Closing`.
- The status beside the report name shows the closing datetime.

### Reopening

1. Admin or Treasurer clicks `Reopen Closing`.
2. The UI collects and submits the signed-in user's current password.
3. The backend validates authorization, account state, password, and presence
   of an active closing.
4. The closing metadata is marked `REOPENED` and inactive, with reopening actor
   and datetime.
5. The immutable GridFS workbook remains stored for audit history.
6. The backend records the successful reopening audit event.
7. The UI refreshes the status and changes the action to `Year-End Closing`.

After reopening, downloads return to live draft generation. A later closing
creates a new immutable version instead of modifying an earlier version.

## Persistence Model

Add a `YearEndClosing` MongoDB document with at least:

- `id`
- `fiscalYear`
- `reportType`: `OFFERING` or `EXPENDITURE`
- `version`
- `status`: `CLOSED` or `REOPENED`
- `active`
- A nullable unique active key derived from fiscal year and report type
- `gridFsFileId`
- `filename`
- `contentType`
- `fileSize`
- `checksum`
- `closedByMemberId`
- `closedByEmail`
- `closedAt`
- `reopenedByMemberId`
- `reopenedByEmail`
- `reopenedAt`

Use a unique compound index on fiscal year, report type, and version. Use a
sparse unique active key so concurrent requests cannot produce two active
closings for the same fiscal year and report type. Clear the active key when a
closing is reopened.

Store workbook bytes in MongoDB GridFS with metadata identifying the closing
record, fiscal year, report type, version, and checksum. GridFS avoids the
MongoDB 16 MB document limit and keeps lifecycle queries lightweight.

Closing records and GridFS files are audit history and are not physically
deleted by reopening.

## Versioning And Concurrency

Versions are positive integers scoped to fiscal year and report type. The first
closing is version 1. Reopening and closing again creates version 2, and so on.

Database uniqueness is the final concurrency boundary. If simultaneous close
requests race:

- At most one active record may succeed.
- Any losing request removes the GridFS file it created.
- The losing request returns a conflict response.
- Both outcomes are audited.

The same close or reopen request must not silently change an existing lifecycle
record when its expected state no longer matches.

## Workbook Marking

The existing blank row 3 becomes a visible lifecycle-status line.

### Offering Titles

- Not closed or reopened: `<year>년도 수입 결산 및 예산안`
- Closed: `<year>년도 수입 결산 및 예산`

### Expenditure Titles

- Not closed or reopened: `<year>년도 지출 결산 및 예산안`
- Closed: `<year>년도 지출 결산 및 예산`

### Row 3 Status

- Never closed: `DRAFT - Year-end closing not completed`
- Reopened: `DRAFT - Reopened <datetime>`
- Closed: `YEAR-END CLOSED - <datetime> - Version <version>`

The row-3 text must fit without altering the established report table, logo,
print area, or repeated title rows. Closed status is fixed at snapshot time.
Draft/reopened status is generated from the latest lifecycle metadata.

### Filenames

- Live draft Offering: `yearly-offerings-<year>-draft.xlsx`
- Closed Offering: `yearly-offerings-<year>-closed-v<version>.xlsx`
- Live draft Expenditure: `yearly-expenditures-<year>-draft.xlsx`
- Closed Expenditure: `yearly-expenditures-<year>-closed-v<version>.xlsx`

## Reports UI

Keep the existing fiscal-year selector and two stacked report rows. Display the
latest lifecycle state and datetime directly beside each report name.

Examples:

- `Yearly Offering Excel · Not closed`
- `Yearly Offering Excel · Closed Jul 21, 2026, 3:42 PM`
- `Yearly Expenditure Excel · Reopened Jul 22, 2026, 9:15 AM`

For Admin and Treasurer, each row displays lifecycle action immediately beside
the existing download action:

```text
Yearly Offering Excel · Not closed
[Download Excel] [Year-End Closing]

Yearly Expenditure Excel · Closed Jul 21, 2026, 3:42 PM
[Download Excel] [Reopen Closing]
```

For Pastor and Viewer, only `Download Excel` is displayed.

Each report row has independent download, closing, reopening, and busy states.
Actions are disabled while that report row is processing. A successful close
or reopen refreshes both statuses without requiring navigation away from the
Reports page.

## API Design

Add a status endpoint that returns both report types for a selected fiscal
year, including status, latest event datetime, active version, fiscal end date,
and whether closing is currently eligible.

Add password-protected close and reopen endpoints scoped by fiscal year and
report type. Request bodies contain only the selected fiscal year and current
password. The server derives the actor from authenticated security context.

Keep the existing yearly workbook download endpoints for compatibility:

- `GET /api/reports/yearly-offerings.xlsx?fiscalYear=<year>`
- `GET /api/reports/yearly-expenditures.xlsx?fiscalYear=<year>`

Those endpoints delegate to a closing-aware download service that selects the
stored snapshot or live renderer according to the active closing state.

## Error Handling

Return clear errors for:

- Invalid fiscal year.
- Unauthorized role.
- Inactive or locked account.
- Incorrect password.
- Closing before the fiscal end date.
- Closing an already closed report.
- Reopening a report with no active closing.
- Concurrent lifecycle conflict.
- Workbook generation failure.
- GridFS write, read, or cleanup failure.
- Missing snapshot bytes.
- Snapshot checksum mismatch.

Password errors do not reveal account internals. Closed-download failures never
silently return a newly generated workbook.

## Audit History

Record success and failure events for:

- Year-end Offering closing.
- Year-end Expenditure closing.
- Year-end Offering reopening.
- Year-end Expenditure reopening.

Each event may include only approved non-sensitive metadata such as fiscal
year, report type, version, closing record ID, GridFS file ID, checksum, and
file size. Audit records identify the acting member and timestamp through the
existing audit service. Passwords and workbook bytes are forbidden.

History remains audit-only in this release. There is no history list or old
version download action in the UI.

## Testing And Verification

Backend service tests cover:

- Independent Offering and Expenditure states.
- Admin and Treasurer lifecycle access.
- Pastor, Viewer, Membership, and Member lifecycle rejection.
- Current-member password verification and wrong-password rejection.
- Active and locked account rejection.
- Configured fiscal periods and premature-closing rejection.
- First closing, reopening, and later version creation.
- One-active-closing uniqueness and concurrent conflict cleanup.
- GridFS persistence, checksum calculation, and orphan cleanup.
- Live generation when never closed or reopened.
- Exact stored-byte download while closed.
- Missing and checksum-invalid snapshot failures without regeneration.
- Success and failure audit events without sensitive metadata.

Workbook tests cover:

- Draft and finalized Korean titles for both report types.
- Never-closed, reopened, and closed row-3 status text.
- Version and closing datetime rendering.
- Existing formulas, styles, logo, print settings, and table layout remaining
  unchanged.

Controller tests cover:

- Status, close, reopen, and closing-aware download endpoints.
- Role enforcement, request validation, conflicts, and response filenames.
- Snapshot bytes being returned unchanged for a closed report.

Frontend tests cover:

- Independent status labels and datetimes.
- Admin and Treasurer lifecycle controls.
- Hidden lifecycle controls for Pastor and Viewer.
- Fiscal-end disabled state and explanation.
- Password dialog opening only from lifecycle actions.
- Confirm, cancel, wrong-password, busy, and error behavior.
- Immediate status and action-label refresh after success.
- Draft and closed download filenames.

Final verification includes focused backend and frontend tests, complete test
suites, the frontend production build, Docker backend build, workbook content
inspection, and visual rendering of Offering and Expenditure workbooks in
draft, closed, and reopened states.

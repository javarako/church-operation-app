# Runtime Operational Church Settings Design

## Purpose

Allow an ADMIN to manage these operational settings from the Church Settings page without restarting the application:

- Church time zone
- Fiscal year start month
- List page size
- Data-management operation expiry

The existing environment variables remain the server defaults:

- `CHURCH_TIME_ZONE=America/Toronto`
- `CHURCH_FISCAL_YEAR_START_MONTH=1`
- `CHURCH_LIST_PAGE_SIZE=20`
- `CHURCH_DATA_OPERATION_EXPIRY=30m`

## Access And Storage

Only users with the `ADMIN` role may view or change these settings through System Administration.

The values are stored in the existing singleton `ChurchSettings` MongoDB document. A missing database value falls back independently to its corresponding environment or `application.yml` value. Full database backup and restore includes these values through the existing `church_settings` collection behavior.

Every save or reset records an audit event. Audit metadata identifies which settings changed but does not expose unrelated sensitive configuration.

## Runtime Resolution

Introduce a central runtime operational-settings resolver. Services and controllers that currently inject immutable fiscal-year, UI, time-zone, or operation-expiry configuration use this resolver whenever they perform an operation.

The resolver returns a validated effective configuration assembled field by field from:

1. Saved `ChurchSettings` values, when present.
2. Existing server configuration defaults otherwise.

No Spring context refresh, bean reconstruction, or server restart is required.

## Setting Behavior

### Church Time Zone

The UI presents an IANA time-zone selector, defaulting to `America/Toronto`. The backend validates the value with `ZoneId` before saving it.

The selected zone takes effect immediately for new date and time calculations, including dashboard dates, audit display values, reports, receipts, and closing timestamps. Persisted `Instant` values remain unchanged; only their local representation and subsequent date-based calculations use the selected zone.

### Fiscal Year Start Month

The UI presents January through December as a month selector and stores an integer from 1 through 12.

The selected month takes effect immediately for live dashboard totals, budget periods, quarterly and yearly reports, archive ranges, and year-end closing eligibility. Existing source records are not rewritten.

Previously closed yearly report files remain immutable and continue to download from their saved snapshots. Previously issued tax receipts, fiscal archive files, and archive history also remain unchanged. New calculations use the newly selected fiscal-year start month.

### List Page Size

The UI presents a numeric control with an allowed range of 5 through 100 and defaults to 20.

After a successful save, the shared church-information state updates immediately. Every open paginated list adopts the new page size and returns to page 1 so that row ranges and page counts remain coherent.

### Data-Management Operation Expiry

The UI presents minutes using supported choices of 10, 20, 30, 60, and 120 minutes. The API stores the value as an ISO-8601-compatible duration and validates that it is positive and no longer than two hours.

The new duration applies to data-management operations prepared after the save. An already prepared or active backup/restore operation retains the expiration timestamp assigned when it was created. This avoids extending or shortening an operation while an administrator is using it.

## API And UI Changes

Extend the existing ADMIN Church Settings request and response with:

- `timeZone`
- `fiscalYearStartMonth`
- `listPageSize`
- `dataOperationExpiryMinutes`

Extend the public church-information response only with values required by authenticated application screens:

- Effective time zone
- Effective fiscal year start month
- Effective list page size

The data-operation expiry is administrative behavior and is not exposed through the public endpoint.

Place the four controls in an **Operational Settings** subsection beneath the existing church information and branding controls. Saving remains one atomic Church Settings action. Resetting Church Settings removes the saved operational overrides together with the existing information and branding overrides, restoring all server defaults.

## Validation And Failure Handling

The backend is authoritative and rejects:

- Unknown or malformed IANA time zones
- Fiscal months outside 1 through 12
- Page sizes outside 5 through 100
- Expiry durations outside 1 minute through 2 hours

Validation failure leaves the existing settings untouched and returns a clear field-oriented error. The UI preserves entered values and displays the error near the settings form.

## Testing

Backend coverage verifies:

- Field-by-field database and server-default resolution
- Validation boundaries for all four values
- Immediate use by dashboard, reports, archive/closing calculations, pagination response, and new data operations
- Existing operation expiry timestamps do not change
- Closed reports and issued receipts remain immutable
- ADMIN authorization and audit events
- Backup/restore round trips include the settings

Frontend coverage verifies:

- Initial values and server-default source display
- Validation and save/reset behavior
- Immediate shared-state refresh
- Open paginated lists reset to page 1 and use the new size
- Non-ADMIN users cannot access the settings page or API

## Out Of Scope

- Rewriting timestamps or historical source records after a time-zone change
- Regenerating closed reports, issued tax receipts, or existing archive files
- Changing the expiry of an operation that is already prepared or running
- Runtime configuration of upload limits, temporary directories, SMTP authentication, or STARTTLS

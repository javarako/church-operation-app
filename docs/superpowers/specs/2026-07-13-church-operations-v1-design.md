# Church Operations v1.0 Design

## Goal

Deliver the first production-oriented release of the church operations application by improving the staff dashboard and adding safe data administration, annual fiscal archival, CRA-oriented official donation receipts, member face images, and protected deletion.

The release continues to use Java 21, Spring Boot 4, Spring Security, Spring Data MongoDB, MongoDB 6+, Vue 3, Vue Router, and Docker Compose. Java packages remain under `com.church.operation` using the existing `config`, `dto`, `entity`, `exception`, `filter`, `repo`, `rest`, `service`, and `util` structure.

## Scope

Included:

- Dashboard summary-card redesign and 12-Sunday offering chart.
- Consistent Lucide icons for every left-menu item on every page.
- Admin-only System Administration page.
- AES-encrypted full-database backup and replacement restore.
- AES-encrypted fiscal-year archive, cleanup, and merge restoration.
- Official annual donation summary and two-up Letter-size PDF receipts.
- Individual and batch receipt generation.
- MongoDB GridFS member face-image storage and display.
- Protected hard deletion for members and reference data.
- Targeted audit events for high-risk v1.0 operations.
- Backend, frontend, integration, PDF, responsive, and Docker verification.

Excluded:

- Scheduled or server-retained backups.
- Cloud object storage.
- Querying downloaded fiscal archives without restoring them.
- Non-cash gifts or gifts that provide a donor advantage.
- Email delivery of tax receipts.
- Electronic or facsimile signatures.
- General-purpose audit-log search UI beyond operation outcomes shown in System Administration.
- Multi-instance restore coordination. v1.0 assumes the current single-backend deployment.

## Existing Baseline

- The current dashboard loads report, member, and transaction lists in the frontend and calculates its own summaries.
- The current Official Tax Report returns one row per offering and exports CSV.
- `Member` already contains `faceImageAttachmentId`, but no image storage API exists.
- Members and reference data support create/update but not deletion.
- Offerings and expenses already use soft deletion.
- MongoDB runs as a standalone Docker service, not a replica set, so multi-collection transactions are unavailable.

## Roles

### Dashboard

All staff roles see every dashboard card and chart:

- `ADMIN`
- `TREASURER`
- `PASTOR`
- `VIEWER`
- `MEMBERSHIP`

`MEMBER` continues to use My Profile rather than the staff dashboard.

### System Administration

Only `ADMIN` can:

- Download a full backup.
- Validate and perform a full restore.
- Download, clean, and restore a fiscal-year archive.
- View the outcome summaries for those operations.

### Official Tax Receipts

`ADMIN` and `TREASURER` can:

- View annual member receipt summaries.
- Generate individual or batch receipts.
- Re-download immutable issued receipts.
- Void and replace an incorrect receipt.

### Member Images And Deletion

- `ADMIN` and `MEMBERSHIP` can upload, replace, or remove any member image.
- `MEMBER` can upload, replace, or remove only their own image.
- `ADMIN` and `MEMBERSHIP` can delete an unreferenced member.
- Existing reference-data managers (`ADMIN`, `MEMBERSHIP`, and `TREASURER`) can delete unused reference data.

## Architecture

Add focused service boundaries that match the existing project:

- `DashboardService`: returns one role-safe dashboard summary.
- `DataManagementService`: exports, validates, and restores complete encrypted backups.
- `FiscalArchiveService`: exports, cleans, validates, and restores selected fiscal records.
- `ArchivePackageService`: owns the versioned BSON package, checksums, collection/index metadata, compression, and AES encryption.
- `TaxReceiptService`: annual aggregation, serial allocation, immutable snapshots, void/replacement behavior, and batch coordination.
- `TaxReceiptPdfService`: renders the two-up PDF with PDFBox or an equivalent maintained Java PDF library.
- `MemberImageService`: validates and stores protected images in GridFS.
- `SystemAuditService`: writes targeted immutable audit events.

The frontend adds API modules for dashboard, system administration, member images, and tax receipt files. The existing page/layout patterns remain in use.

## Dashboard

### Layout

Keep the current church banner and church-information header. Directly below it, show one compact four-card row matching the approved dashboard mockup:

1. Active Members.
2. YTD Offering vs Budget.
3. YTD Expense vs Budget.
4. Pending Cheques.

Keep the current Offering Overview below the top row. Remove the separate Membership and Recent Finance Activity panels. Add the 12-Sunday Offering Trend after Offering Overview.

### Active Members

- Main value: members where `active = true`.
- Secondary value: members created during the current calendar month.
- Add `createdAt` to `Member` and set it when creating new members.
- Existing members without `createdAt` are excluded from New This Month rather than being backdated.

### YTD Offering vs Budget

- Actual: non-deleted offerings dated from the configured fiscal-year start through today.
- Budget: sum of `OFFERING_INCOME` budgets for that fiscal year.
- Show actual/budget amount, percentage, and progress bar.
- Use the approved hand-and-heart offering icon rather than a dollar icon.
- A zero budget displays `Not budgeted` and no invalid percentage.

### YTD Expense vs Budget

- Actual: non-deleted expense transactions from fiscal-year start through today.
- Budget: sum of `EXPENSE` budgets for that fiscal year.
- Show actual/budget amount, percentage, and progress bar.
- A zero budget displays `Not budgeted`.

### Pending Cheques

- Include non-deleted expense transactions that have a nonblank cheque number and `chequeCleared = false`.
- Show count and total amount.

### Offering Overview And Trend

- Preserve the current week, month-to-date, and calendar-year-to-date Offering Overview behavior.
- Trend contains the last 12 Sundays in ascending order.
- Include zero-value Sundays so the time axis remains stable.
- Use Chart.js or another established Vue-compatible chart library rather than custom chart math.

### API

Add `GET /api/dashboard` for all staff roles. It returns:

- Active and new-member counts.
- Fiscal-year start/end and year-to-date offering/budget values.
- Fiscal-year expense/budget values.
- Pending cheque count/total.
- Existing Offering Overview totals.
- Twelve Sunday labels and totals.

## Navigation

- Keep the left navigation visible on every authenticated page.
- Add a Lucide icon to every menu item.
- Add an Admin-only `System Administration` item with a settings icon.
- Preserve role-based route visibility and route guards.
- Use familiar icon-only trash actions with tooltips for row deletion.

## Full Backup Package

### Contents

The full backup exports the complete church MongoDB database:

- Every application collection, including future collections discovered at export time.
- GridFS files and chunks.
- Collection options and index definitions.
- A versioned manifest containing archive type, format version, application version, creation time, collection counts, and file checksums.
- Raw BSON entries to preserve MongoDB types and identifiers.

The full package includes members, password hashes, password-reset data, reference data, offerings, transactions, budgets, tax receipts, receipt counters, archive registries, audit events, and member images.

### Encryption

- Use an AES-encrypted ZIP through a maintained Java ZIP library such as Zip4j.
- Admin enters and confirms the password for each download.
- Passwords are accepted only in request bodies, never query strings.
- Passwords are never stored, logged, included in audit data, or returned.
- Temporary unencrypted BSON files must not remain after success or failure.

### Download

- Full backups are download-only and are not retained on the server.
- Package creation uses bounded temporary storage and streaming where practical.
- The filename includes church-safe name, backup type, and timestamp.
- Temporary content is removed after transfer or expiration.

## Full Restore

Full restore replaces the entire current database after cleaning it.

### Staged Flow

1. Admin uploads the encrypted full-backup ZIP and enters its password.
2. Backend stages the upload under an operation ID with a short expiration.
3. Validate archive type, format compatibility, checksums, BSON, collection metadata, required admin account, indexes, counts, and known relationships.
4. Show the validation summary without changing current data.
5. Admin enters a separate password for a pre-restore safety backup.
6. Generate and require download of the encrypted safety backup.
7. Admin types the displayed confirmation phrase.
8. Enter maintenance mode and reject new writes.
9. Clear current collections, restore all BSON and GridFS data, and recreate indexes.
10. Verify collection counts, required indexes, and bootstrap admin availability.
11. Leave maintenance mode, invalidate all authentication tokens, and return the browser to Login.

The frontend can confirm that the safety-backup response completed; it cannot prove where the browser saved the file. The confirmation text must clearly tell the Admin to verify the downloaded file before continuing.

### Standalone MongoDB Limitation

The current standalone MongoDB deployment cannot atomically replace all collections. Therefore:

- Validation completes before any mutation.
- Only one data-management operation may run at a time.
- The safety backup is mandatory.
- The operation records per-collection progress and failure details.
- A failed restore leaves the application in maintenance mode with explicit recovery instructions instead of silently reopening a partial database.

## Fiscal-Year Archive And Cleanup

### Fiscal Range

Use `church.fiscal-year.start-month` to calculate the selected fiscal year's inclusive start and end dates.

### Archive Contents

- All offerings whose `offeringDate` falls in the fiscal range, including soft-deleted offerings.
- Every offering-generated income transaction linked to those offering IDs, regardless of minor date inconsistencies.
- All manual expense transactions whose `transactionDate` falls in the range, including soft-deleted expenses.
- All budgets for the selected fiscal year.
- A manifest with fiscal year, inclusive date range, IDs, counts, reference-code usage, member IDs, and checksums.

Tax receipt snapshots, members, users, reference data, password records, audit events, and GridFS images remain live.

### Archive And Clean Flow

1. Admin selects a fiscal year and enters/confirm an archive password.
2. Backend calculates record counts and relationship validation.
3. Generate and download the encrypted fiscal archive.
4. Require Admin acknowledgment that the download completed and a typed confirmation phrase.
5. Delete archived records by their manifest IDs in dependency order.
6. Verify those IDs are absent and save a `FiscalArchiveRegistry` summary.

Cleanup is idempotent. If deletion stops partway, retrying uses the same manifest IDs and completes the remaining deletions.

### Archive Registry

Retain only metadata needed for safety:

- Archive identifier and checksum.
- Fiscal year and date range.
- Record counts and cleanup/restore status.
- Referenced member IDs.
- Referenced group, membership, fund, payment, category, and sub-category codes.
- Actor and timestamps.

This registry does not retain the archived financial records, but prevents deletion of members or reference values required to restore a downloaded archive.

### Fiscal Archive Restore

- Upload encrypted fiscal archive and enter its password.
- Require archive type `FISCAL` and validate format, checksums, relationships, fiscal boundaries, and matching archive registry.
- Preflight every ID and business uniqueness constraint.
- Any duplicate or conflict blocks the whole import before insertion.
- Insert in dependency order and use compensating deletion of newly inserted IDs if an unexpected write failure occurs.
- Verify counts, then mark the registry restored.
- Never clear members, reference data, other fiscal years, or GridFS.

## Official Tax Report Summary

Replace the current per-offering Official Tax Report rows with one annual row per member.

Filters:

- Tax year.
- Optional offering number.

Columns:

- Offering number first.
- Donor name.
- Donor address.
- Tax year.
- Total offering amount.
- Receipt number.
- Receipt status.
- Receipt actions.

Remove Giving Date and Fund/Category. Sort by offering number. Anonymous and group offerings are excluded.

The annual amount sums all non-deleted `MEMBER` offerings in the selected calendar year. An inactive member may still receive a receipt for valid historical offerings.

## Official Receipt Lifecycle

### Eligibility

v1.0 supports monetary gifts with no donor advantage:

- Amount of gift equals eligible amount.
- Advantage amount is `$0.00` and description is `None`.
- Member record must still exist.
- Donor full name and complete postal address are required.
- All required church receipt configuration must be present.

Missing information blocks issuance and identifies the exact fields to correct.

### Serial Numbers

- Format: `YYYY-000001`.
- Sequence resets for each calendar tax year.
- Allocate through an atomic MongoDB counter operation.
- Each issued receipt has one unique serial number.
- The two copies printed on one page use the same serial number and represent one receipt.

### Immutable Snapshot

Create a `TaxReceipt` snapshot containing:

- Receipt number, status, tax year, issue date, and issuing actor.
- Member ID and offering number.
- Donor name, address, and email at issuance.
- Church name, CRA-filed address, registration number, website, issue location, and Treasurer at issuance.
- Gift amount, eligible amount, advantage amount/description.
- Thank-you note.
- Source offering IDs and a source-total checksum.
- Void/replacement references and timestamps when applicable.

Re-downloading an issued receipt renders from the snapshot, not current member or offering data. If current source totals differ, the report shows a warning. Issuing again downloads the existing receipt instead of allocating another serial.

### Void And Replacement

- Admin/Treasurer may void an incorrect receipt with a required reason.
- Retain the original snapshot with `VOID` status.
- A replacement receives the next serial number, stores a link to the void receipt, and uses a fresh current-data snapshot.
- Retain both records in full backups and after fiscal cleanup.

## Official Receipt PDF

### Page Format

- Letter size: 8.5 by 11 inches.
- Top half contains one complete receipt.
- Bottom half contains an identical complete receipt.
- A dashed horizontal cut line separates the halves.
- Do not label either copy as Donor, Church, Original, or Duplicate.
- Both halves use the same receipt serial and content.

### Required Content

Each half contains:

- `Official Receipt for Income Tax Purposes` statement.
- Church name and address as on file with CRA.
- Charity registration number.
- Unique serial receipt number.
- Place where the receipt was issued.
- Calendar year the cash donations were received.
- Receipt issue date.
- Donor full name and address.
- Amount of gift.
- Advantage amount `$0.00` and description `None`.
- Eligible amount, equal to total offering amount for this church.
- Blank authorized-signature line.
- Existing configured Treasurer name and title `Treasurer` beneath the signature line.
- Church website.
- `Canada Revenue Agency` and `canada.ca/charities-giving`.
- Church logo when the configured resource can be loaded.

CRA references:

- [Issuing complete and accurate donation receipts](https://www.canada.ca/en/revenue-agency/services/charities-giving/charities/checklists-charities/issuing-complete-accurate-donation-receipts.html)
- [Sample official donation receipts](https://www.canada.ca/en/revenue-agency/services/charities-giving/charities/sample-official-donation-receipts.html)
- [Computer-generated receipts](https://www.canada.ca/en/revenue-agency/services/charities-giving/charities/operating-a-registered-charity/issuing-receipts/computer-generated-receipts.html/1000)

### Thank-You Note

The report UI provides a multiline text box prefilled with:

> Thank you for your faithful and generous support over the past year. Because of you, we are able to continue serving our community and sharing God's message.

- Treasurer may edit the note before individual or batch issuance.
- One note applies to all newly issued receipts in that batch.
- Store the note in every immutable receipt snapshot.
- Re-download uses the original stored note.
- Render the note above the blank signature area.
- Accept plain text only and enforce a length that preserves both half-page layouts.

### Individual And Batch Generation

- Individual action returns one two-up PDF.
- Batch action creates missing eligible receipts and includes existing issued receipts for the selected tax year.
- Batch returns a ZIP containing one PDF per member.
- Validation failures are reported by member before allocating serials.
- Batch issuance does not partially allocate receipts when pre-validation finds errors.

## Church Configuration

Continue to source official church identity from `application.yml` and environment variables.

Existing:

- `church.information.name`
- `church.information.address`
- `church.information.contact-info`
- `church.information.treasurer-name`

Add:

- `church.information.charity-registration-number`
- `church.information.receipt-issue-location`
- `church.information.website`

The configured Treasurer is the authorized signer. The PDF provides a blank signature line for physical signing.

## Member Face Images

### Storage And Validation

- Store images in MongoDB GridFS.
- Save the GridFS ID in `Member.faceImageAttachmentId`.
- Accept JPEG, PNG, and WebP only.
- Maximum upload size: 5 MB.
- Validate filename extension, declared media type, and file signature/content.
- Reject SVG, animated formats, executable content, and malformed images.

### Display

- Members table: 38-pixel circular thumbnail; use member initials when absent.
- Create/Edit Member: 96-pixel preview with Replace and Remove controls above member fields.
- My Profile: same preview and controls for the signed-in member.
- Use authenticated binary endpoints. The Vue client fetches a Blob and manages object-URL cleanup.

### Replacement And Removal

- Store the new image first.
- Update the member reference.
- Delete the previous GridFS file only after the member update succeeds.
- If member update fails, remove the newly stored orphan.
- Removing an image clears the member reference before deleting the GridFS file.

Suggested endpoints:

- `GET /api/members/{id}/image`
- `PUT /api/members/{id}/image`
- `DELETE /api/members/{id}/image`
- Equivalent `/api/members/me/image` endpoints for self-service.

## Member Deletion

Add `DELETE /api/members/{id}`.

Hard deletion is allowed only when all conditions are true:

- Target is not the bootstrap System Administrator.
- Target is not the currently signed-in actor.
- No live offering, including soft-deleted offerings, references the member.
- No fiscal archive registry references the member.
- No issued, void, or replacement tax receipt references the member.

If blocked, return a structured dependency error and suggest making the member inactive or locked.

On success:

- Delete member GridFS image.
- Delete password-reset tokens for the member/email.
- Delete the member record.
- Write an audit event.

## Reference-Data Deletion

Add `DELETE /api/reference-data/{id}`.

Hard deletion is allowed only when the code is unused by:

- Member group code or membership status.
- Offering fund/category or payment method, including soft-deleted offerings.
- Financial category or sub-category, including soft-deleted transactions.
- Budgets.
- Fiscal archive registry metadata.
- Child sub-categories when deleting a parent financial category.

Seeded defaults follow the same rule and may be deleted when unused. The UI uses a trash icon, tooltip, and explicit confirmation dialog.

## New Persistent Models

### `TaxReceipt`

Immutable receipt snapshot and lifecycle fields described above. Index tax year, offering number, member ID, receipt number, status, and source offering IDs. Receipt number is unique.

### `TaxReceiptCounter`

One document per tax year with atomic next-sequence allocation.

### `FiscalArchiveRegistry`

Retained metadata for archive integrity and deletion protection. It does not contain archived financial amounts or documents beyond counts and identifiers needed for restoration safety.

### `SystemAuditEvent`

Targeted immutable event containing actor ID/email, operation, timestamp, outcome, affected IDs/year, counts, operation ID, and sanitized error summary.

Never store archive passwords, raw ZIP content, member password hashes, or uploaded image bytes in audit events.

## Suggested APIs

### Dashboard

- `GET /api/dashboard`

### System Administration

- `POST /api/admin/data/full-backup`
- `POST /api/admin/data/full-restore/validate`
- `POST /api/admin/data/full-restore/{operationId}/safety-backup`
- `POST /api/admin/data/full-restore/{operationId}/execute`
- `GET /api/admin/data/operations/{operationId}`
- `POST /api/admin/data/fiscal-archive/preview`
- `POST /api/admin/data/fiscal-archive/download`
- `POST /api/admin/data/fiscal-archive/{operationId}/clean`
- `POST /api/admin/data/fiscal-archive/restore/validate`
- `POST /api/admin/data/fiscal-archive/restore/{operationId}/execute`

### Tax Receipts

- `GET /api/reports/tax-receipts/summary`
- `POST /api/reports/tax-receipts/issue`
- `POST /api/reports/tax-receipts/issue-batch`
- `GET /api/reports/tax-receipts/{id}/pdf`
- `POST /api/reports/tax-receipts/{id}/void`
- `POST /api/reports/tax-receipts/{id}/replace`

Exact endpoint payloads will be defined in the implementation plan. Request parameters must always have explicit Spring names to avoid reflection parameter-name failures.

## System Administration UI

Add `/system-administration`, visible only to Admin.

Use tabs or full-width sections without nested cards:

- Full Backup.
- Full Restore.
- Fiscal Archive and Clean.
- Fiscal Archive Restore.
- Recent data-management outcomes.

Destructive workflows use step-based panels, record-count previews, password confirmation, typed confirmation phrases, progress status, and clear success/failure summaries. Password inputs are cleared immediately after each request.

## Error Handling

- Wrong password, corrupt ZIP, wrong archive type, incompatible format, checksum mismatch, malformed BSON, missing required admin, or relationship errors cause validation failure without mutation.
- Oversized uploads return a clear size-limit error.
- Only one backup/restore/archive mutation runs at a time.
- Maintenance mode returns HTTP 503 for writes with a user-readable message.
- Temporary operation IDs expire and cannot be replayed.
- Receipt issuance reports missing member and church fields before serial allocation.
- A zero budget never produces NaN or Infinity.
- Image endpoint returns a neutral not-found response when no image exists.
- Deletion returns structured dependency details rather than a generic failure.

## Testing

### Backend Unit Tests

- Fiscal-year date boundaries for all start months.
- Dashboard counts, totals, percentages, zero budgets, pending cheques, and 12 Sundays.
- Role enforcement for every new endpoint.
- Receipt annual aggregation, sorting, atomic serial format, immutable snapshots, void/replacement links, and custom/default notes.
- Member and reference deletion dependency checks.
- Image type, signature, size, ownership, replacement, and cleanup behavior.

### MongoDB Integration Tests

- Full encrypted backup/restore round trip.
- GridFS byte-for-byte preservation.
- Collection options and index recreation.
- Wrong password and corrupt/checksum-failed packages do not mutate data.
- Fiscal archive includes the correct records, cleans them, retains registry metadata, and restores them.
- Duplicate/conflict validation and compensating rollback.
- Archived member/reference dependencies block deletion.

### PDF Tests

- Letter page dimensions.
- Two identical half-page receipt copies and one serial number.
- Dashed cut line.
- Required CRA fields in both halves.
- Gift and eligible amounts match.
- Default and edited thank-you notes.
- Blank signature line and configured Treasurer identity.
- Existing receipt re-download renders from its stored snapshot.

### Frontend Tests

- Dashboard renders all four cards for each staff role.
- Membership and Recent Finance panels are absent.
- Trend has 12 Sunday points.
- Every menu entry has an icon and role visibility remains correct.
- Image upload/replace/remove and initials fallback.
- Member/reference delete confirmations and dependency errors.
- Tax summary columns, individual issuance, batch issuance, note editing, warnings, void/replacement, and downloads.
- Full restore and fiscal archive staged confirmations.

### Visual And Runtime Verification

- Desktop and mobile screenshots for Dashboard, Members, Reports, and System Administration.
- Verify no text overlap, clipped controls, blank charts, or horizontal page overflow.
- Docker Compose build and local smoke test.
- Restore test on disposable data before production deployment.

## Acceptance Criteria

- All staff roles see the approved dashboard with four top cards, current Offering Overview, and 12-Sunday chart.
- All navigation items display consistent icons on every page.
- Admin can download an encrypted complete database backup containing GridFS images.
- Admin can validate a full backup, download a safety backup, replace the database, and log in with restored credentials.
- Admin can download and clean one configured fiscal year, then restore it without changing other years or master data.
- Official Tax Report shows one annual member summary row without giving-date or fund columns.
- Admin/Treasurer can issue one receipt or a batch ZIP.
- Each PDF is one Letter page with two identical, unlabeled half-page receipts and all required configured fields.
- Receipt amount and eligible amount are equal, advantage is none, and the custom/default thank-you note is retained.
- Member images appear in list, edit, and profile views and survive full backup/restore.
- Unreferenced members and reference values can be hard deleted; referenced records are protected with clear explanations.
- Destructive and receipt-lifecycle operations leave targeted audit events.
- Backend, frontend, integration, PDF, responsive, and Docker checks pass.

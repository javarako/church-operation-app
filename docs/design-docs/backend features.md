# Church Operation Backend — Functional Summary

**Stack:** Spring Boot 4.0.7, Java 21, MongoDB, Spring Security (stateless Bearer tokens), GridFS for member photos and year-end Excel snapshots.

---

## 1. REST Controllers & Endpoints

### `AuthController` — `/api/auth`

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/login` | Authenticate by email/password; returns Bearer token + roles (**public**) |
| POST | `/change-password` | Change password for authenticated user |
| POST | `/logout` | Revoke Bearer token |
| POST | `/forgot-password` | Request password-reset email (**public**) |
| POST | `/reset-password` | Reset password with token (**public**) |

### `ChurchInformationController` — `/api/church-information`

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/` | Effective church identity, versioned branding URLs, UI settings, and application version (**public**) |
| GET | `/logo` | Effective church logo from GridFS or bundled fallback (**public**) |
| GET | `/banner` | Effective church banner from GridFS or bundled fallback (**public**) |

### `ChurchSettingsController` — `/api/admin/church-settings` (**ADMIN only**)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/` | Load effective church information, branding URLs, and configuration source |
| PUT | `/` | Save church information and optional logo/banner (multipart) |
| POST | `/reset` | Remove database overrides and return to server defaults |

### `MemberController` — `/api/members`

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/` | List members (optional `search`) |
| POST | `/` | Create member |
| GET | `/me` | Current user's profile |
| PUT | `/me` | Update own profile (limited fields for MEMBER role) |
| GET | `/me/image` | Download own profile photo |
| PUT | `/me/image` | Upload/replace own photo (multipart) |
| DELETE | `/me/image` | Remove own photo |
| GET | `/{id}` | Get member by ID |
| PUT | `/{id}` | Update member |
| DELETE | `/{id}` | Delete member (with dependency checks) |
| GET | `/{id}/image` | Download member photo |
| PUT | `/{id}/image` | Upload/replace member photo |
| DELETE | `/{id}/image` | Remove member photo |

### `OfferingController` — `/api/offerings`

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/` | List all non-deleted offerings |
| POST | `/` | Create offering (+ linked income transaction) |
| PUT | `/{id}` | Update offering |
| DELETE | `/{id}` | Soft-delete offering |

### `FinanceController` — `/api/finance`

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/transactions` | List visible financial transactions |
| POST | `/expenses` | Create expense transaction |
| PUT | `/expenses/{id}` | Update expense |
| DELETE | `/expenses/{id}` | Soft-delete expense |

### `BudgetController` — `/api/budgets`

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/` | List budgets for `fiscalYear` |
| POST | `/` | Create budget line |
| PUT | `/{id}` | Update budget |
| DELETE | `/{id}` | Soft-delete budget |

### `DashboardController` — `/api/dashboard`

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/` | Staff dashboard KPIs and trends |

### `ReferenceDataController` — `/api/reference-data`

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/{type}` | List active lookup values (optional `parentCode`) |
| GET | `/maintenance/{type}` | List all values including inactive (**ADMIN**) |
| POST | `/` | Create reference data entry (**ADMIN**) |
| PUT | `/{id}` | Update reference data (**ADMIN**) |
| DELETE | `/{id}` | Delete reference data with usage checks (**ADMIN**) |

**Types:** `GROUP_CODE`, `MEMBERSHIP_STATUS`, `COMMITTEE_CODE`, `OFFERING_FUND`, `OFFERING_CATEGORY`, `PAYMENT_METHOD`, `FINANCIAL_CATEGORY`, `FINANCIAL_SUB_CATEGORY` (+ legacy `OFFERING_FUND_CATEGORY` for migration only).

### `ReportController` — `/api/reports`

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/weekly-offerings` | Weekly offering summary (`start`, `end`, filters) |
| GET | `/member-offerings` | Per-member offering totals |
| GET | `/financial-budget` | Budget vs actual by fiscal year |
| GET | `/tax-receipts/summary` | Tax receipt eligibility/status by `taxYear` |
| POST | `/tax-receipts/issue` | Issue single official tax receipt |
| POST | `/tax-receipts/issue-batch` | Batch issue; returns ZIP of PDFs |
| GET | `/tax-receipts/{receiptId}/pdf` | Download receipt PDF |
| POST | `/tax-receipts/{receiptId}/void` | Void a receipt |
| POST | `/tax-receipts/{receiptId}/replace` | Replace receipt (void + reissue) |
| GET | `/quarterly-offerings.xlsx` | Quarterly offering Excel (`year`, `quarter`) |
| GET | `/quarterly-expenditures.xlsx` | Quarterly expenditure Excel |
| GET | `/yearly-offerings.xlsx` | Yearly offering workbook (`fiscalYear`) |
| GET | `/yearly-expenditures.xlsx` | Yearly expenditure workbook |
| GET | `/yearly-closing-status` | Year-end close status for both report types |
| POST | `/yearly-closing/{reportType}/close` | Close yearly report (password required) |
| POST | `/yearly-closing/{reportType}/reopen` | Reopen closed yearly report |

`reportType`: `OFFERING` | `EXPENDITURE`

### `DataManagementController` — `/api/admin/data-management` (**ADMIN only**)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/full-backup` | Password-protected full MongoDB backup (ZIP) |
| POST | `/restore/validate` | Upload backup + validate (multipart) |
| POST | `/restore/{id}/safety-backup` | Pre-restore safety backup |
| POST | `/restore/{id}/execute` | Execute full database restore |
| GET | `/restore/{id}` | Restore operation status |
| GET | `/fiscal/{year}/preview` | Preview fiscal-year archive contents |
| POST | `/fiscal/{year}/archive` | Create password-protected fiscal-year archive |
| POST | `/fiscal/{id}/clean` | Delete archived fiscal-year data from DB |
| POST | `/fiscal/restore/validate` | Validate uploaded fiscal archive |
| POST | `/fiscal/restore/{id}/execute` | Restore fiscal archive into DB |

### Other HTTP surfaces

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/actuator/health` | Health check (**public**) |
| GET | `/branding/**` | Static branding assets (**public**) |

---

## 2. Key Services & Business Logic

| Service | Responsibility |
|---------|----------------|
| **AuthService** | Login, password change, logout; BCrypt password verification |
| **AuthTokenService** | In-memory UUID Bearer tokens mapped to member email |
| **PasswordResetService** | Token-based reset flow with email (TTL configurable) |
| **ChurchInformationResolver** | Resolves each church information field from database settings with configuration fallback |
| **ChurchSettingsService** | ADMIN-only runtime church settings save/reset with audit events and safe branding replacement |
| **ChurchBrandingService** | GridFS logo/banner storage; PNG/JPEG, size, dimension, and pixel-count validation |
| **ApplicationVersionProvider** | Exposes the Maven build version to the public church-information response |
| **BootstrapAdminRunner** | Creates `admin` user on first boot (default password `password`, must change) |
| **MemberService** | CRUD, self-service updates, role assignment, search, reference-data validation |
| **MemberDeletionService** | Safe deletion with blocking dependency checks |
| **MemberImageService** | GridFS photo storage; JPEG/PNG validation; size limits |
| **OfferingService** | Offering CRUD; auto-creates linked **INCOME** transactions; Sunday resolution; fund/category hierarchy |
| **FinancialTransactionService** | Expense CRUD; validates categories against reference data; filters offering-linked income |
| **BudgetService** | Fiscal-year budgets (OFFERING_INCOME, EXPENSE, CARRY_OVER); uniqueness constraints |
| **ReferenceDataService** | Lookup CRUD; hierarchical parent/child codes |
| **ReferenceDataDeletionService** | Prevents deletion when data is in use |
| **DashboardService** | Member counts, offering/expense trends, budget utilization (staff only) |
| **ReportService** | Weekly offerings, member offering summaries, financial budget vs actual |
| **QuarterlyOfferingReportService / QuarterlyExpenditureReportService** | Build quarterly report data |
| **QuarterlyFinancialExcelService** | Render quarterly Excel workbooks |
| **YearlyOfferingReportService / YearlyExpenditureReportService** | Build yearly fiscal reports |
| **YearlyFinancialExcelService** | Render yearly Excel with church logo |
| **YearEndClosingService** | Close/reopen yearly reports; snapshot workbooks to GridFS; password-verified lifecycle |
| **YearEndSnapshotStore** | GridFS read/write for closed report files |
| **TaxReceiptService** | Issue/void/replace receipts; aggregates member offerings; validation; checksum tracking |
| **TaxReceiptCounterService** | Sequential receipt numbers per tax year |
| **TaxReceiptPdfService** | PDF generation for official receipts |
| **DataManagementService** | Full backup/restore workflow with maintenance mode, safety backup, confirmation phrases |
| **MongoDatabaseExportService / MongoDatabaseImportService** | BSON archive export/import |
| **FiscalArchiveService** | Fiscal-year selective archive, clean (purge), restore; registry tracking |
| **ArchivePackageService / FiscalArchiveCodec** | Password-protected ZIP packaging |
| **MaintenanceModeService** | Blocks mutations during restore (except restore status endpoints) |
| **SystemAuditService** | Immutable audit trail for sensitive operations |
| **DataOperationStore** | Tracks multi-step restore/archive operation state |

---

## 3. Security & Roles Model

### Authentication
- **Stateless Bearer tokens** (`Authorization: Bearer <uuid>`), stored in-memory (not persisted).
- `BearerTokenAuthenticationFilter` loads `Member` as principal; roles become `ROLE_<NAME>` authorities.
- **No Spring `@PreAuthorize`** — authorization is enforced in service layer via role checks.

### Public (no auth)
- `/api/auth/login`, `/forgot-password`, `/reset-password`
- `/api/church-information`
- `/actuator/health`, `/branding/**`

### Role enum (`Role.java`)
`ADMIN`, `TREASURER`, `PASTOR`, `MEMBERSHIP`, `VIEWER`, `MEMBER`

Members can hold **multiple roles**.

### Role access matrix (service-layer)

| Capability | Roles |
|------------|-------|
| Full backup / restore / fiscal archive | **ADMIN** |
| Church information and branding maintenance | **ADMIN** |
| Reference data maintenance | **ADMIN** |
| Member create/delete | **ADMIN**, **MEMBERSHIP** |
| Member list/view | **ADMIN**, **MEMBERSHIP**, **TREASURER**, **PASTOR**, **VIEWER** |
| Member self-update + own photo | **MEMBER** (own record) |
| Member photo (others) | **ADMIN**, **MEMBERSHIP** |
| Offerings write | **ADMIN**, **TREASURER** |
| Offerings read | + **VIEWER** |
| Expenses write | **ADMIN**, **TREASURER** |
| Expenses read | + **VIEWER** |
| Budgets | **ADMIN**, **TREASURER** |
| Reports / quarterly Excel / year-end status & download | **ADMIN**, **TREASURER**, **PASTOR**, **VIEWER** |
| Year-end close/reopen | **ADMIN**, **TREASURER** (+ current password) |
| Tax receipts | **ADMIN**, **TREASURER** |
| Dashboard | **ADMIN**, **TREASURER**, **PASTOR**, **VIEWER**, **MEMBERSHIP** |
| Active reference data read | Any authenticated user |

### Other security features
- **MaintenanceModeFilter**: Returns 503 on mutations during DB restore (GET/restore-status still allowed).
- Account lockout via `Member.locked`; inactive accounts cannot authenticate.
- Password reset tokens stored hashed with MongoDB TTL index.

---

## 4. Major Domain Entities (MongoDB)

| Collection | Entity | Key fields |
|------------|--------|------------|
| `members` | **Member** | email, profile, `offeringNumber`, roles, password hash, committee/group codes |
| `offerings` | **Offering** | giving type (MEMBER/ANONYMOUS/GROUP), fund/category, amount, offering Sunday, soft-delete |
| `financialTransactions` | **FinancialTransaction** | INCOME/EXPENSE, linked to offerings, cheque/HST fields, soft-delete |
| `budgets` | **Budget** | fiscal year, type (OFFERING_INCOME/EXPENSE/CARRY_OVER), category/subCategory |
| `referenceData` | **ReferenceData** | typed lookup codes with hierarchy |
| `tax_receipts` | **TaxReceipt** | receipt #, donor info, amounts, source offerings, void/replace chain |
| `tax_receipt_counters` | **TaxReceiptCounter** | per-year sequence for receipt numbers |
| `year_end_closings` | **YearEndClosing** | fiscal year + report type, versioned close/reopen, GridFS snapshot ref |
| `fiscalArchiveRegistries` | **FiscalArchiveRegistry** | archive metadata, counts, status (CREATED/CLEANED/RESTORED) |
| `password_reset_tokens` | **PasswordResetToken** | hashed token, TTL expiry |
| `system_audit_events` | **SystemAuditEvent** | immutable audit log with operation type + metadata |
| `church_settings` | **ChurchSettings** | singleton runtime identity fields and GridFS branding references |
| **GridFS (`fs.files` / `fs.chunks`)** | — | Member photos, runtime church branding, and year-end Excel snapshots |

**Embedded:** `Address` on Member (used for tax receipts).

**Fiscal year:** Configurable start month (`church.fiscal-year.start-month`, default January).

---

## 5. Notable Features

### Offerings & Finance
- Offerings auto-create **linked income transactions**; updates/deletes sync the transaction.
- Soft-delete pattern on offerings, expenses, and budgets.
- Fund/category hierarchy migrated from legacy combined fund-category codes.

### Reports
- **Weekly** and **member-level** offering reports with filters.
- **Financial budget** report: budget vs actual by category.
- **Quarterly** Excel exports (offerings & expenditures).
- **Yearly** Excel exports tied to fiscal year; new PDFs and workbooks use effective runtime church information and logo.

### Runtime Church Settings
- ADMIN can update church identity, contact, receipt, website, logo, and banner values without restarting the application.
- Settings use field-by-field fallback to `application.yml`; resetting removes the database override and restores bundled branding.
- Uploaded PNG/JPEG branding is stored in GridFS and is included in full backup/restore while fiscal archive/clean leaves it intact.
- Public branding URLs are versioned so the frontend refreshes images immediately after save or reset.
- Church settings changes and resets create immutable system audit events.

### Tax Receipts
- Official CRA-style receipts with sequential numbering per tax year.
- Validates donor address, offering totals, existing receipts.
- Single issue, batch issue (ZIP), PDF download, void, and replace workflows.
- Source offering IDs + checksum for integrity; full audit trail.

### Year-End Closing
- Separate close/reopen for **OFFERING** and **EXPENDITURE** reports per fiscal year.
- Closing generates Excel snapshot stored in **GridFS**; closed reports served from snapshot.
- Reopening allows live recalculation until re-closed.
- Requires **ADMIN/TREASURER** password confirmation.

### Full Backup & Restore
- Password-protected ZIP of entire MongoDB (BSON + indexes + GridFS).
- Multi-step workflow: validate → safety backup → execute with confirmation phrase `"RESTORE FULL DATABASE"`.
- Maintenance mode during restore; revokes all auth tokens on cutover.

### Fiscal Archive
- Archives one fiscal year's offerings, linked income, expenses, and budgets.
- Preview counts before archive; download password-protected ZIP.
- **Clean** step purges archived records from live DB (requires download first; confirmation `"CLEAN FISCAL YEAR {year}"`).
- Restore workflow validates and re-imports archived fiscal data.
- Registry tracks archive status and metadata for audit.

### Operational
- Bootstrap admin on first run.
- Reference data seeding (optional via config).
- System audit events for all sensitive admin/finance operations.
- Effective church information and branding are served from runtime database settings with environment/configuration fallbacks.
- Maven build metadata provides the application version shown by the frontend.

# Church Operations Frontend — Functionality Summary

Vue 3 SPA with Vue Router, bearer-token auth, and role-gated routes/nav. Authenticated app pages use `AppLayout` (sidebar + content); auth pages are standalone.

---

## 1. Routes & Pages

| Path | View | Layout | Route roles (`meta.roles`) |
|------|------|--------|---------------------------|
| `/login` | `LoginView` | Standalone | Public |
| `/forgot-password` | `ForgotPasswordView` | Standalone | Public |
| `/reset-password` | `ResetPasswordView` | Standalone | Public (token in query) |
| `/change-password` | `ChangePasswordView` | Standalone | Authenticated; forced if `mustChangePassword` |
| `/` | `DashboardView` | AppLayout | ADMIN, TREASURER, PASTOR, MEMBERSHIP, VIEWER |
| `/members` | `MembersView` | AppLayout | ADMIN, MEMBERSHIP |
| `/offerings` | `OfferingsView` | AppLayout | ADMIN, TREASURER |
| `/finance` | `FinanceView` | AppLayout | ADMIN, TREASURER |
| `/budgets` | `BudgetsView` | AppLayout | ADMIN, TREASURER |
| `/reference-data` | `ReferenceDataView` | AppLayout | ADMIN |
| `/reports` | `ReportsView` | AppLayout | ADMIN, TREASURER, PASTOR, VIEWER |
| `/profile` | `ProfileView` | AppLayout | ADMIN, MEMBER |
| `/system-administration` | `SystemAdministrationView` | AppLayout | ADMIN |

**Router guards** (`/Users/brad/Desktop/workspcace/church-operation-app-DEV/frontend/src/router/index.ts`):
- Unauthenticated users → `/login`
- `mustChangePassword` → `/change-password` (except on that page)
- Role mismatch → `/profile` if user has `MEMBER`, else `/`

---

## 2. What Each Page Does

### Auth (standalone)

- **Login** — Sign in with login ID + password; stores user + bearer token; redirects to change-password or dashboard.
- **Forgot password** — Request reset link by primary email.
- **Reset password** — Set new password via `?token=` query param.
- **Change password** — Required password update on first login or when flagged.

### Staff app (AppLayout)

- **Dashboard (`/`)** — Church banner/info, user role badge, fiscal snapshot cards (active members, YTD offering/expense vs budget, pending cheques), offering totals (week/MTD/YTD), 12-Sunday offering trend chart (Chart.js).
- **Members (`/members`)** — Searchable member list with pagination; create/edit/delete members; contact info, address, group/status, committees, offering number, roles, login enabled/locked, notes; member photo upload via `MemberImageEditor`. Bootstrap `admin` account cannot be deleted.
- **Offerings (`/offerings`)** — List/filter offerings (fund, category, giving type); record/edit/delete offerings for MEMBER, ANONYMOUS, or GROUP givers; auto-sync offering Sunday; shows linked income transaction status.
- **Finance (`/finance`)** — Combined income + expense ledger; offering income summarized by day/category; manual expenses CRUD (cheque fields, HST, payable-to, treasurer approval); income from offerings is read-only.
- **Budgets (`/budgets`)** — Fiscal-year budgets for OFFERING_INCOME and EXPENSE; fund/category/sub-category driven by reference data; CRUD with sorting.
- **Reference Data (`/reference-data`)** — Admin maintenance of dropdown values: group codes, membership status, committees, offering funds/categories, payment methods, financial categories/sub-categories.
- **Reports (`/reports`)** — Tabbed reporting (see workflows below): weekly offerings, member summaries, tax receipts, budget performance, quarterly/yearly Excel downloads, year-end closing.
- **My Profile (`/profile`)** — Self-service contact/address/notes update; photo upload; primary email read-only.
- **System Administration (`/system-administration`)** — Full DB backup/restore, fiscal-year archive/clean/restore, runtime church identity and branding, and email server settings.

---

## 3. Role-Based Access in UI

### Roles
`ADMIN` | `TREASURER` | `PASTOR` | `MEMBERSHIP` | `VIEWER` | `MEMBER`

Defined in `/Users/brad/Desktop/workspcace/church-operation-app-DEV/frontend/src/auth/roles.ts`.

### Sidebar nav visibility (`AppLayout.vue`)

| Nav item | Visible to |
|----------|------------|
| Dashboard | All staff (ADMIN, TREASURER, PASTOR, MEMBERSHIP, VIEWER) |
| Members | ADMIN, MEMBERSHIP |
| Offerings, Finance, Budgets | ADMIN, TREASURER |
| Reference Data, System Administration | ADMIN |
| Reports | ADMIN, TREASURER, PASTOR, VIEWER |
| My Profile | ADMIN, MEMBER |

### Additional in-page restrictions (`ReportsView.vue`)

| Feature | Roles |
|---------|-------|
| **Official tax** tab (issue/void/replace receipts, batch download) | ADMIN, TREASURER |
| **Year-end closing / reopen** (offering & expenditure) | ADMIN, TREASURER |

### Effective access by role

| Role | Typical landing | Can access |
|------|-----------------|------------|
| **ADMIN** | Dashboard | Everything |
| **TREASURER** | Dashboard | Dashboard, offerings, finance, budgets, reports (+ tax & year-end) |
| **PASTOR** | Dashboard | Dashboard, reports (no tax tab) |
| **MEMBERSHIP** | Dashboard | Dashboard, members |
| **VIEWER** | Dashboard | Dashboard, reports (no tax tab) |
| **MEMBER** | Profile (on denied routes) | Profile only |

---

## 4. API Modules & Endpoints

Base HTTP client: `/Users/brad/Desktop/workspcace/church-operation-app-DEV/frontend/src/api/http.ts` — JSON/multipart/blob helpers, attaches `Authorization: Bearer <token>`.

### Auth (direct `http` calls in views, not a dedicated module)

| Call | Used by |
|------|---------|
| `POST /api/auth/login` | LoginView |
| `POST /api/auth/logout` | AppLayout |
| `POST /api/auth/change-password` | ChangePasswordView |
| `POST /api/auth/forgot-password` | ForgotPasswordView |
| `POST /api/auth/reset-password` | ResetPasswordView |

### `churchInformation.ts`

| Function | Endpoint |
|----------|----------|
| `getChurchInformation()` | `GET /api/church-information` |

Used by the shared reactive church-information store for Dashboard, AppLayout branding/version, and pagination page size.

### `churchSettings.ts`

| Function | Endpoint |
|----------|----------|
| `getChurchSettings()` | `GET /api/admin/church-settings` |
| `saveChurchSettings(settings, logo?, banner?)` | `PUT /api/admin/church-settings` (multipart) |
| `resetChurchSettings()` | `POST /api/admin/church-settings/reset` |

### `dashboard.ts`

| Function | Endpoint |
|----------|----------|
| `getDashboard()` | `GET /api/dashboard` |

### `members.ts`

| Function | Endpoint |
|----------|----------|
| `listMembers(search?)` | `GET /api/members?search=` |
| `createMember(payload)` | `POST /api/members` |
| `updateMember(id, payload)` | `PUT /api/members/:id` |
| `deleteMember(id)` | `DELETE /api/members/:id` |
| `getMyProfile()` | `GET /api/members/me` |
| `updateMyProfile(payload)` | `PUT /api/members/me` |
| `getMemberImage(id)` / `replaceMemberImage` / `removeMemberImage` | `GET/PUT/DELETE /api/members/:id/image` |
| `getSelfImage()` / `replaceSelfImage` / `removeSelfImage` | `GET/PUT/DELETE /api/members/me/image` |

### `offerings.ts`

| Function | Endpoint |
|----------|----------|
| `listOfferings()` | `GET /api/offerings` |
| `createOffering(payload)` | `POST /api/offerings` |
| `updateOffering(id, payload)` | `PUT /api/offerings/:id` |
| `deleteOffering(id)` | `DELETE /api/offerings/:id` |

### `finance.ts`

| Function | Endpoint |
|----------|----------|
| `listFinanceTransactions()` | `GET /api/finance/transactions` |
| `createExpense(payload)` | `POST /api/finance/expenses` |
| `updateExpense(id, payload)` | `PUT /api/finance/expenses/:id` |
| `deleteExpense(id)` | `DELETE /api/finance/expenses/:id` |

### `budgets.ts`

| Function | Endpoint |
|----------|----------|
| `listBudgets(fiscalYear)` | `GET /api/budgets?fiscalYear=` |
| `createBudget(payload)` | `POST /api/budgets` |
| `updateBudget(id, payload)` | `PUT /api/budgets/:id` |
| `deleteBudget(id)` | `DELETE /api/budgets/:id` |

### `referenceData.ts`

| Function | Endpoint |
|----------|----------|
| `listReferenceData(type, parentCode?)` | `GET /api/reference-data/:type` |
| `listAllReferenceData(type, parentCode?)` | `GET /api/reference-data/maintenance/:type` |
| `createReferenceData(payload)` | `POST /api/reference-data` |
| `updateReferenceData(id, payload)` | `PUT /api/reference-data/:id` |
| `deleteReferenceData(id)` | `DELETE /api/reference-data/:id` |

Types: `GROUP_CODE`, `MEMBERSHIP_STATUS`, `COMMITTEE_CODE`, `OFFERING_FUND`, `OFFERING_CATEGORY`, `PAYMENT_METHOD`, `FINANCIAL_CATEGORY`, `FINANCIAL_SUB_CATEGORY`.

### `reports.ts`

| Function | Endpoint |
|----------|----------|
| `listWeeklyOfferingReport(filters)` | `GET /api/reports/weekly-offerings` |
| `listMemberOfferingSummaryReport(filters)` | `GET /api/reports/member-offerings` |
| `listTaxReceiptSummary(filters)` | `GET /api/reports/tax-receipts/summary` |
| `issueTaxReceipt(payload)` | `POST /api/reports/tax-receipts/issue` |
| `issueBatchTaxReceipts(payload)` | `POST /api/reports/tax-receipts/issue-batch` → ZIP blob |
| `downloadTaxReceiptPdf(receiptId)` | `GET /api/reports/tax-receipts/:id/pdf` |
| `voidTaxReceipt(receiptId, reason)` | `POST /api/reports/tax-receipts/:id/void` |
| `replaceTaxReceipt(receiptId, thankYouNote)` | `POST /api/reports/tax-receipts/:id/replace` |
| `listFinancialBudgetReport(filters)` | `GET /api/reports/financial-budget` |
| `downloadQuarterlyOfferingReport(filters)` | `GET /api/reports/quarterly-offerings.xlsx` |
| `downloadQuarterlyExpenditureReport(filters)` | `GET /api/reports/quarterly-expenditures.xlsx` |
| `getYearEndClosingStatus(filters)` | `GET /api/reports/yearly-closing-status` |
| `closeYearEndReport(type, request)` | `POST /api/reports/yearly-closing/:type/close` |
| `reopenYearEndReport(type, request)` | `POST /api/reports/yearly-closing/:type/reopen` |
| `downloadYearlyOfferingReport(filters)` | `GET /api/reports/yearly-offerings.xlsx` |
| `downloadYearlyExpenditureReport(filters)` | `GET /api/reports/yearly-expenditures.xlsx` |

### `dataManagement.ts`

| Function | Endpoint |
|----------|----------|
| `downloadFullBackup(password)` | `POST /api/admin/data-management/full-backup` |
| `validateFullRestore(file, password)` | `POST /api/admin/data-management/restore/validate` (multipart) |
| `downloadSafetyBackup(operationId, password)` | `POST .../restore/:id/safety-backup` |
| `executeFullRestore(operationId, confirmation)` | `POST .../restore/:id/execute` |
| `getRestoreStatus(operationId)` | `GET .../restore/:id` |
| `getFiscalArchivePreview(year)` | `GET .../fiscal/:year/preview` |
| `downloadFiscalArchive(year, password)` | `POST .../fiscal/:year/archive` |
| `cleanFiscalArchive(archiveId, confirmation)` | `POST .../fiscal/:archiveId/clean` |
| `validateFiscalRestore(file, password)` | `POST .../fiscal/restore/validate` (multipart) |
| `executeFiscalRestore(operationId, confirmation)` | `POST .../fiscal/restore/:id/execute` |

---

## 5. Key User Workflows (UI-visible)

### Authentication & account lifecycle
1. **Sign in** → token stored in memory (`authState`) → dashboard or forced password change.
2. **Forgot/reset password** → email link → set new password → return to login.
3. **Logout** → `POST /api/auth/logout` → clear session → login page.

### Member management (ADMIN / MEMBERSHIP)
1. Search members → select row or **New member**.
2. Fill profile, assign roles/committees, set offering number.
3. Upload photo → save → optional delete (except bootstrap admin).

### Offering recording (ADMIN / TREASURER)
1. Filter existing offerings or **Record offering**.
2. Choose giving type → member search or giver label.
3. Set date/Sunday, fund → category (fund-scoped), amount, payment method.
4. Save → backend creates linked income transaction (shown as "Created" in list).

### Expense management (ADMIN / TREASURER)
1. View ledger: offering income summarized daily; manual expenses editable.
2. **Add expense** → category/sub-category, cheque details, HST, approval fields.
3. Edit/delete manual expenses only; offering-sourced income is read-only.

### Budget planning (ADMIN / TREASURER)
1. Select fiscal year → view income/expense budget lines.
2. Create budget by type (income uses fund/category; expense uses financial category/sub-category).
3. Edit/delete budget rows.

### Reference data setup (ADMIN)
1. Pick reference type tab → list all values (including inactive).
2. Create/edit codes, labels, sort order, active flag; parent required for offering categories and financial sub-categories.

### Reporting (role-dependent tabs)
1. **Weekly offerings** — date range + filters → table + CSV export.
2. **Member offerings** — per-member totals by fund/category → CSV.
3. **Official tax** (ADMIN/TREASURER) — tax year summary → issue/download/void/replace receipts; batch ZIP download; warns if offerings changed post-issue.
4. **Budget performance** — fiscal year actual vs budget variance → CSV.
5. **Quarterly financial** — pick year/quarter → download offering or expenditure Excel.
6. **Yearly financial** — pick fiscal year → download workbooks; ADMIN/TREASURER can close/reopen year-end reports (password confirmation modal).

### Self-service (MEMBER / ADMIN)
1. Load own profile → edit contact/address/notes/photo → save.

### System administration (ADMIN)
**Church settings:** edit church identity/contact/receipt fields and upload PNG/JPEG logo or banner images. Save/reset updates the shared menu and dashboard branding immediately; no relogin is required. The application version is displayed below **Church Operations** in the menu.

**Full backup:** password → download encrypted ZIP.

**Full restore (3 steps):**
1. Validate backup ZIP + password.
2. Download safety backup of current DB.
3. Type `RESTORE FULL DATABASE` → execute → logout on success.

**Fiscal archive & clean:**
1. Preview fiscal year record counts.
2. Download encrypted fiscal archive.
3. Type `CLEAN FISCAL YEAR {year}` → remove archived records from live DB.

**Fiscal restore:**
1. Validate fiscal archive ZIP.
2. Type `RESTORE FISCAL YEAR {year}` → merge back into live data.

---

## Architecture Notes

- **State:** In-memory auth (`authStore.ts`) plus a reactive, request-deduplicating church-information store; no persisted session in frontend code.
- **Layout:** `App.vue` wraps non-auth routes in `AppLayout` (sidebar nav + runtime church logo/name + application version).
- **Shared UI:** `PaginationControls`, `MemberAvatar`, `MemberImageEditor`, `usePagination` composable (page size from church info).
- **Currency/locale:** CAD formatting throughout (`en-CA`).

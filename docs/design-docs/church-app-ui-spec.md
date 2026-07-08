# Church Operations App — Page-by-Page UI Spec

Based on: `2026-07-07-church-operations-app-design.md`
Frontend: Vue 3.x + Vue Router. Role-aware navigation and route guards throughout.

Legend for role access shorthand: **A**=ADMIN, **T**=TREASURER, **P**=PASTOR, **M**=MEMBERSHIP, **V**=VIEWER, **ME**=MEMBER

---

## 1. Login

**Route:** `/login`
**Access:** Public

**Layout**
- Church banner image (top, from resource folder) + church icon
- Centered card: "Sign In"
  - Field: Primary Email (or `admin` username for bootstrap)
  - Field: Password
  - Button: Sign In
  - Error area beneath form (inline, not toast) for invalid credentials, locked account

**Behavior**
- On success with `firstLoginPasswordChangeRequired = true` → redirect to `/change-password` (forced, no way to skip)
- On success otherwise → redirect to `/dashboard`
- No "forgot password" self-service in v1 (out of scope — email delivery not built) — show static text: "Contact your Admin/Treasurer to reset your password."

---

## 2. Forced Password Change

**Route:** `/change-password` (mandatory intercept route)
**Access:** Any authenticated user with `firstLogin` flag true; also reachable voluntarily later from Profile

**Layout**
- Card: "Update Your Password"
  - Field: Current Password
  - Field: New Password
  - Field: Confirm New Password
  - Password rules hint text (length/complexity)
  - Button: Update Password

**Behavior**
- Route guard: if `firstLogin === true`, all other routes redirect here
- On success: clears flag, redirects to `/dashboard` (or `/profile` for bootstrap admin to complete profile)

---

## 3. Dashboard

**Route:** `/dashboard`
**Access:** All roles (content varies by role)

**Layout**
- Top bar: church icon, church name, logged-in user name/role badge, logout
- Left nav: role-filtered menu (see Navigation Map, section 13)
- Main area: grid of widget cards, role-aware:

| Widget | A | T | P | M | V | ME |
|---|---|---|---|---|---|---|
| This week's offering total | ✓ | ✓ | | | ✓ | |
| YTD offering vs budget (mini chart) | ✓ | ✓ | | | ✓ | |
| Pending cheque approvals | ✓ | ✓ | | | | |
| New member registrations (last 30 days) | ✓ | | ✓ | ✓ | | |
| Membership status breakdown (chart) | ✓ | | ✓ | ✓ | ✓ | |
| Recent audit activity (last 10) | ✓ | | | | | |
| My offering history (mini) | | | | | | ✓ |
| My profile completeness | | | | | | ✓ |
| Upcoming fiscal year budget deadline banner | ✓ | ✓ | | | | |

**Behavior**
- Widgets fetch independently (skeleton loaders per card)
- Cards link through to relevant full screen (e.g., click chart → Reports)

---

## 4. Members — List

**Route:** `/members`
**Access:** A, M (full), P and V (read-only), ME (no access — self-service only)

**Layout**
- Page header: "Members" + button "Add Member" (A, M only)
- Filter bar: search (name/email/offering #), Membership Status dropdown, Group Code dropdown, Active/Locked toggle
- Data table columns: Name, Primary Email, Phone, Group Code, Membership Status, Offering #, Account State (Active/Locked/Pending), Actions
- Pagination + page size selector
- Row actions: View, Edit (A/M only), Enable/Disable Login (A/M only)

**Behavior**
- Table is server-paginated/filtered
- P and V see the table without Add/Edit/Actions columns
- Export button (CSV) — A/M only

---

## 5. Members — Detail / Edit

**Route:** `/members/:id` (view) and `/members/:id/edit` (edit)
**Access:** A, M full edit; P, V read-only view; ME limited (own record only, via Profile page instead — see section 12)

**Layout — tabbed detail view**
- Header: name, face image thumbnail, status badge, offering #
- Tabs:
  1. **Profile** — legal/display name, nickname, birth date, primary/secondary email, primary/secondary/mobile phone, mailing address block
  2. **Membership** — group code, membership status, offering number, important dates, notes
  3. **Household** — household/family linkage, list of linked members
  4. **Account** — roles (A only can edit), active/locked toggle, first-login flag, "Enable Login" action if not yet a login user
  5. **Face Image** — upload/replace/remove (uses Attachments)
  6. **Audit History** — read-only list of changes to this record (A only)

**Behavior**
- Edit mode: inline validation (uniqueness check on `primaryEmail`, `offeringNumber` — async check on blur)
- Save triggers audit entry
- Admin-only fields (roles, admin flags) hidden entirely for Membership-role editors, not just disabled

---

## 6. Members — Add New

**Route:** `/members/new`
**Access:** A, M

**Layout**
- Single-page form (same field groups as Profile/Membership tabs above), sectioned with headers, not tabs, since it's a first-time entry
- Optional section at bottom: "Create login account now?" toggle — if on, requires primary email (already mandatory) and sets first-login flag

**Behavior**
- On submit: validation errors shown inline per field
- Success → redirect to new member's detail page with confirmation banner

---

## 7. Offerings — List

**Route:** `/offerings`
**Access:** T, A (full); V (read-only, if permitted dashboards include it); ME (none — see Member Self-Service)

**Layout**
- Filter bar: Date range, Offering Sunday, Fund/Category, Payment Method, Member/Anonymous/Group toggle
- Table columns: Date, Offering Sunday, Member/Anonymous/Group label, Fund/Category, Amount, Payment Method, Memo, Created By
- Button: "Record Offering" (T, A)
- Summary strip above table: total for current filter

**Behavior**
- Clicking a row opens read-only detail drawer (shows linked financial transaction reference)

---

## 8. Offerings — Record New

**Route:** `/offerings/new`
**Access:** T, A

**Layout**
- Form:
  - Giving type selector: Member / Anonymous / Group (radio)
  - If Member: member search-select (autocomplete by name/offering #)
  - If Anonymous/Group: free-text label field
  - Date, Offering Sunday (date picker, defaults to nearest Sunday)
  - Fund/Category (reference-data dropdown)
  - Amount
  - Payment Method (reference-data dropdown)
  - Memo (optional)
- Info note: "This will automatically create a linked income transaction."

**Behavior**
- On submit: creates offering + linked financial transaction, shows success with links to both records

---

## 9. Finance — Transactions List

**Route:** `/finance`
**Access:** T, A (full); V (read-only where permitted)

**Layout**
- Tabs or toggle: Income / Expense / All
- Filter bar: Date range, Category, Sub-category, Cheque cleared (yes/no), Source (offering-generated vs manual)
- Table columns: Type, Date, Category/Sub-category, Amount, HST Included, Cheque #, Cheque Cleared, Payable To, Approved By, Source Ref, Actions
- Button: "Add Expense" (T, A) — income rows are read-only (system-generated from Offerings) except for approval/cheque-clearing actions

**Behavior**
- Cheque-cleared toggle inline in table (T, A) — writes audit entry
- Approve action (T, A) on pending expense rows

---

## 10. Finance — Add/Edit Expense

**Route:** `/finance/new` and `/finance/:id/edit`
**Access:** T, A

**Layout**
- Form: Date, Amount, Category, Sub-category, HST Included (checkbox), Cheque Number, Payable To, Memo
- Evidence Attachment uploader (drag/drop or file picker)
- Approval section (only visible/editable if user has approval rights): Approve toggle, Treasurer name (auto-filled from session)

**Behavior**
- Attachment upload shows progress + failure error handling per doc's error-handling spec
- Save creates audit entry

---

## 11. Budgets

**Route:** `/budgets`
**Access:** T, A

**Layout**
- Fiscal year selector (top)
- Two sections (tabs or side-by-side panels):
  1. **Offering Income Budgets** — table: Fund/Category, Budget Amount, Notes, editable inline
  2. **Expense Budgets** — table: Category, Sub-category, Budget Amount, Notes, editable inline
- Button: "Add Line" per section
- Banner if current fiscal year has no budget entered yet: "No budget set for FY [year] — enter estimates before the year starts"

**Behavior**
- Inline-editable grid, save per row or bulk "Save All"
- Created/updated-by and timestamp shown on hover per row

---

## 12. Reference Data

**Route:** `/reference-data`
**Access:** A (all lists); M (membership-related lists only); T (finance/offering-related lists only)

**Layout**
- Left sub-nav or tab strip filtered by role:
  - Membership Status *(A, M)*
  - Group Code *(A, M)*
  - Offering Fund/Category *(A, T)*
  - Expense Category *(A, T)*
  - Expense Sub-category *(A, T)*
- Each list: simple table (Code, Label, Active/Inactive, Order) + Add/Edit/Deactivate
- Deactivate rather than hard delete (preserves referential integrity with existing records) — confirm dialog explains this

**Behavior**
- Attempting to deactivate a value in use shows a warning with count of records referencing it, but allows it (future records just won't offer it)

---

## 13. Reports

**Route:** `/reports`
**Access:** varies per report (see below)

**Layout**
- Report picker (cards or tabs):
  1. **Weekly Offering Status** *(T, A, V)* — filters: Offering Sunday range, Fund/Category, Payment Method; grouped totals table + export
  2. **Offering Summary for Member(s)** *(T, A; ME sees own only via Profile)* — filters: member/household, date range, fund/category
  3. **Official Offering Tax Return Report** *(T, A only)* — filters: member(s), tax year; extraction triggers mandatory audit entry; explicit "Official Use" warning banner before export
  4. **Financial Budget Report** *(T, A, V)* — filters: fiscal year; actual vs. budget table + variance, by fund/category and by expense category/sub-category, with summary chart

**Behavior**
- All reports: filter panel (left or top) + results table/chart + Export button (CSV/PDF depending on report)
- Tax return report shows a confirmation dialog before export ("This extraction will be logged. Continue?")

---

## 14. User & Role Management

**Route:** `/admin/users`
**Access:** A only

**Layout**
- Table: Member Name, Primary Email, Roles (badges), Active/Locked, First-Login Pending, Actions
- Row actions: Edit Roles (multi-select checkboxes: ADMIN/TREASURER/PASTOR/MEMBERSHIP/VIEWER/MEMBER), Lock/Unlock, Force Password Reset (sets first-login flag true)
- Filter: by role, by active state

**Behavior**
- Role changes require confirmation dialog (shows before/after role list)
- Cannot remove ADMIN role from the last remaining admin (client + server validated)
- All changes create audit entries

---

## 15. Audit History

**Route:** `/admin/audit`
**Access:** A only

**Layout**
- Filter bar: Date range, Record Type (Member/Offering/Finance/Budget/ReferenceData/User/Password/Report), Actor
- Table columns: Timestamp, Action, Record Type, Record ID, Actor, Summary
- Row click → detail drawer with full before/after or extraction context if available

---

## 16. Member Self-Service — Profile

**Route:** `/profile`
**Access:** ME (also used by bootstrap Admin to complete their own profile post-first-login)

**Layout**
- Same profile field groups as Members detail (Profile tab), but only permitted fields editable:
  - Editable: display name, nickname, phone numbers, mailing address, secondary email, face image
  - Read-only (greyed, with tooltip "Contact Membership to update"): membership status, group code, offering number, roles
- Section: "My Offering History" — table (own offerings only), filter by date range/fund
- Section: "My Offering Status" summary card (YTD total, last gift date)

**Behavior**
- Save triggers audit entry
- No access to other members' records from this screen at all (route-guarded + API-enforced)

---

## 17. Navigation Map (role-aware left nav)

| Nav Item | A | T | P | M | V | ME |
|---|---|---|---|---|---|---|
| Dashboard | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Members | ✓ | | ✓(view) | ✓ | ✓(view) | |
| Offerings | ✓ | ✓ | | | ✓(view) | |
| Finance | ✓ | ✓ | | | ✓(view) | |
| Budgets | ✓ | ✓ | | | | |
| Reference Data | ✓ | ✓(subset) | | ✓(subset) | | |
| Reports | ✓ | ✓ | ✓(view-only) | | ✓ | |
| User & Role Management | ✓ | | | | | |
| Audit History | ✓ | | | | | |
| My Profile | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

- Frontend hides nav items and routes per role; backend independently enforces authorization (per doc's security model)
- Route guards redirect to `/dashboard` with a toast if a hidden route is accessed directly

---

## 18. Shared UI Patterns

- **Error display:** inline, field-adjacent (per doc's Error Handling section) — not global toasts for validation errors; global toast only for network/server failures
- **Confirmation dialogs:** required for role changes, reference-data deactivation, tax report extraction, cheque-cleared toggling on already-cleared items
- **Tables:** consistent searchable/filterable/paginated table component reused across Members, Offerings, Finance, Reports, Audit
- **Uploads:** consistent attachment component (progress bar, retry on failure, file-type/size validation client-side before hitting API) used for face images and evidence attachments
- **Empty states:** every list screen has a defined empty state (e.g., "No offerings recorded for this filter" with a clear-filters action)
- **Branding:** banner + icon rendered from config in top bar/login on every screen for consistency

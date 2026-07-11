# Church Operations User Guide

Version 1.0 | July 2026

## About This Guide

This guide explains the day-to-day tasks available in Church Operations. It is organized by function so users can go directly to the work they need to perform. The screens shown use the current Capstone Presbyterian Church branding and sample records.

The menu changes according to the roles assigned to the signed-in member. A user with more than one role receives the combined access of those roles.

## Roles and Access

| Role | Primary responsibilities |
|---|---|
| Admin | Full operational access, member accounts, reference data, finance, budgets, and reports |
| Treasurer | Offerings, finance, budgets, reference data, reports, and official tax extracts |
| Pastor | Dashboard and reports |
| Membership | Dashboard, member records, and member-related reference data |
| Viewer | Dashboard and read-only reporting |
| Member | Personal profile access and self-service password reset |

### Function access summary

| Function | Available to |
|---|---|
| Dashboard | Admin, Treasurer, Pastor, Membership, Viewer |
| Members | Admin, Membership |
| Offerings | Admin, Treasurer |
| Finance | Admin, Treasurer |
| Budgets | Admin, Treasurer |
| Reference Data | Admin, Treasurer, Membership |
| Reports | Admin, Treasurer, Pastor, Viewer |
| Official Tax Return report | Treasurer |
| My Profile | Admin, Member |
| Password reset and Logout | All users |

## Getting Started

### Sign in

**Available to:** All users

1. Open Church Operations in a web browser.
2. Enter the member's primary email in **Login ID**. The initial system administrator uses `admin`.
3. Enter the password.
4. Select **Sign in**.
5. The application opens the functions permitted by the member's assigned roles.

[[FIGURE:01-login.png|Sign-in page]]

### Change the initial password

**Available to:** Users whose account requires a password change

The initial administrator account is created with login ID `admin` and password `password`. Change this password immediately.

1. Sign in with the temporary password.
2. On **Change Password**, enter the current password.
3. Enter a new password containing at least eight characters.
4. Select **Update password**.
5. The dashboard opens after the password is updated.

[[FIGURE:12-change-password.png|Required first-login password change]]

### Reset a forgotten password

**Available to:** All users with an active, unlocked account and a valid primary email

1. On the sign-in page, select **Forgot password?**
2. Enter the account's primary email.
3. Select **Send reset link**. The same confirmation appears whether or not the email matches an account.
4. Open the password reset email and select its link within 30 minutes.
5. Enter the new password twice and select **Set new password**.
6. Return to sign in and use the new password.

[[FIGURE:02-forgot-password.png|Request a password reset link]]

[[FIGURE:03-reset-password.png|Set a new password from the emailed link]]

> **Security note:** A reset link works once and expires after 30 minutes. Completing a reset signs out any existing sessions for that member.

## Dashboard

**Available to:** Admin, Treasurer, Pastor, Membership, Viewer

The dashboard provides a quick operational summary:

- **Offering Overview** shows current-week, month-to-date, and year-to-date totals from active offering records.
- **Fiscal Snapshot** compares budgeted and actual income and expense for the configured fiscal year.
- **Membership** shows total, active, and locked member accounts.
- **Recent Finance Activity** summarizes recent income, expense, and transaction volume.

Use the left menu to open permitted functions. The church name, contact information, treasurer, current user initials, and role appear in the banner area.

[[FIGURE:04-dashboard.png|Dashboard overview]]

## Member Information

**Available to:** Admin, Membership

### Find and review a member

1. Select **Members** in the left menu.
2. Enter part of a name or email in **Search members**.
3. Select **Search**.
4. Select a row to load the record into **Member Detail**.
5. Use **Previous** and **Next** when the list has more than one page.

### Register a member

1. Select **New member**.
2. Enter the mandatory **Primary email**. This becomes the login ID.
3. Enter name, nickname, secondary email, phone numbers, birth date, and address as available.
4. Choose **Group code** and **Membership status** from church reference data.
5. Enter a numeric **Offering number** when required.
6. Assign one or more roles. New records default to **MEMBER**.
7. Use **Login enabled** to permit sign-in and **Login locked** to block sign-in.
8. Add internal notes and select **Create member**.

### Update a member

1. Select the member row.
2. Change the required fields, roles, or account flags.
3. Select **Save changes**.

[[FIGURE:05-members.png|Member search and registration screen]]

> **Important:** Membership status describes the person's relationship to the church. Login enabled and Login locked control account access; they are separate settings.

## Offering Management

**Available to:** Admin, Treasurer

### Review offerings

1. Select **Offerings**.
2. Filter by **Fund/category** or giving type.
3. Select **Refresh**.
4. Review the filtered total and offering count above the table.

### Record an offering

1. Select **Record offering**.
2. Choose **Member**, **Anonymous**, or **Group** as the giving type.
3. For a member offering, search for and select the member. For anonymous or group giving, enter a giver label.
4. Enter the **Offering date**. **Offering Sunday** automatically moves to the coming Sunday, or remains today when the date is Sunday; edit it when necessary.
5. Choose the **Fund/category** and **Payment method** from reference data.
6. Enter the amount and optional memo.
7. Select **Save offering**.

Saving an offering automatically creates the linked income transaction shown in Finance.

### Edit or delete an offering

1. Select an offering row to open **Edit Offering**.
2. Update fields and select **Save changes**, or select **Cancel** to leave edit mode.
3. To delete, select the trash-bin icon on the row and confirm. Deleted offerings do not appear in finance summaries or reports.

[[FIGURE:06-offerings.png|Offering list and entry form]]

## Financial Management

**Available to:** Admin, Treasurer

### Review financial activity

1. Select **Finance**.
2. Filter by category and by **Income**, **Expense**, or both.
3. Select **Refresh**.
4. Review net, income, and expense totals above the table.

Offering income is shown as daily category summaries and is read-only in Finance. Manual expenses can be maintained here.

### Record an expense

1. Select **Add expense**.
2. Enter transaction date and amount.
3. Choose a category. The **Sub-category** list is filtered by the selected category.
4. Set **HST included** and **Cheque cleared** when applicable.
5. Enter cheque number, payable party, approving treasurer, and memo as available.
6. Select **Save expense**.

### Edit or delete an expense

1. Select a manual expense row.
2. Update it and select **Save changes**, or select **Cancel**.
3. Use the row's trash-bin icon to delete a manual expense after confirmation.

[[FIGURE:07-finance.png|Financial activity and expense form]]

## Budget Management

**Available to:** Admin, Treasurer

Budgets are prepared by fiscal year for offering income by fund/category and for expenses by category and sub-category.

### Review budgets

1. Select **Budgets**.
2. Enter the fiscal year and optionally choose a budget type.
3. Select **Refresh**.

### Add or update a budget

1. Select **Add budget**.
2. Choose **Offering income** or **Expense**.
3. Enter the fiscal year and **Budget** amount.
4. Choose the category and, for expenses, the applicable sub-category.
5. Add an optional memo and select **Save budget**.
6. To edit, select an existing row, update the details, and select **Save changes**.

[[FIGURE:08-budgets.png|Fiscal-year budget list and entry form]]

## Reference Data

**Available to:** Admin, Treasurer, Membership

Reference data supplies church-specific dropdown values for member, offering, finance, and budget forms.

Supported types include group code, membership status, offering fund/category, payment method, financial category, and financial sub-category.

### Create or update a value

1. Select **Reference Data**.
2. Choose the reference type and select **Refresh**.
3. Select **New value**, enter an uppercase code, user-facing label, sort order, and active status.
4. For a financial sub-category, select its parent category.
5. Select **Create value**.
6. To edit, select a row, update the details, and save.

Inactive values remain stored but are omitted from normal dropdown choices.

[[FIGURE:09-reference-data.png|Reference data maintenance]]

## Reports

**Available to:** Admin, Treasurer, Pastor, Viewer. The Official Tax Return is available only to Treasurer.

### Weekly Offering Status

1. Select **Reports**, then **Weekly offerings**.
2. Set start and end dates. The end date is included in the report range.
3. Optionally filter by fund/category and payment method.
4. Select **Run report**.
5. Review totals by offering Sunday, fund, giving type, and payment method.

### Offering Summary

1. Select **Member offerings**.
2. Set the inclusive date range.
3. Optionally enter an offering number and select a fund/category.
4. Select **Run report**. Results are ordered by offering number.

### Official Tax Return

**Available to:** Treasurer

1. Select **Official tax**.
2. Enter the tax year and optionally an offering number.
3. Select **Run report**.
4. Review results ordered by offering number and giving date.
5. Select **Export CSV** for tax-receipt preparation.

### Financial Actual vs Budget

1. Select **Budget performance**.
2. Enter the fiscal year and select **Run report**.
3. Review budget, actual, and variance by income/expense category and sub-category.

For any visible report, select **Export CSV** to download the current report results.

[[FIGURE:10-reports.png|Report tabs, filters, results, and CSV export]]

## My Profile

**Available to:** Admin, Member

1. Select **My Profile**.
2. Review the primary email, which is the login ID and cannot be changed here.
3. Update display name, nickname, secondary email, phone numbers, address, and notes.
4. Select **Save profile**.

[[FIGURE:11-profile.png|Personal profile maintenance]]

## Logout

**Available to:** All signed-in users

1. Select **Logout** at the bottom of the left menu.
2. The current session is invalidated and the sign-in page opens.
3. Sign in again to begin a new session.

Do not leave Church Operations open on a shared or unattended computer.

## Troubleshooting and Security Notes

- **Invalid username or password:** Verify the primary email and password. Ask an administrator to confirm that Login enabled is selected and Login locked is cleared.
- **Reset email not received:** Confirm the primary email in the member record, check spam or junk folders, and request a new link. Only the newest link is valid.
- **Reset link invalid or expired:** Request a new link. Links expire after 30 minutes and can be used once.
- **Menu item missing:** Access is role-based. Ask an administrator or Membership user to confirm assigned roles.
- **Dropdown value missing:** Confirm the reference value is active and, for sub-categories, assigned to the correct parent category.
- **Report has no rows:** Check the inclusive date range, offering number, fiscal year, fund/category, and payment method filters.
- **Session ended:** Sign in again. Password reset and Logout invalidate existing sessions intentionally.

For financial and tax information, use only authorized church devices and follow the church's privacy and record-retention policies.

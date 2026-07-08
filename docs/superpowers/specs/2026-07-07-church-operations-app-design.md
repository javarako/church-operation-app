# Church Operations App Design

## Purpose

Build a multi-user web application for church operations. The first version covers member information, offering management, simple financial management, fiscal-year budgets, reference data, reports, and secure login.

## Architecture

The app will be a Docker-orchestrated modular monolith:

- Backend: Java 21, Spring Boot 4, Spring Security, Spring Data MongoDB.
- Frontend: Vue 3.x with Vue Router.
- Database: MongoDB 6+.
- Local runtime: Docker Compose starts MongoDB, backend, and frontend together.
- Church branding assets: banner image and church log asset are stored in a resource folder and served to the frontend.
- Church information config: church name, address, contact info, and treasurer name are configured in `application.yml`.
- Java package structure under `com.church.operation`: `config`, `dto`, `entity`, `exception`, `filter`, `repo`, `rest`, `service`, and `util`. Domain behavior is organized within these package types while REST endpoints remain domain-oriented.
- Frontend areas: login, required password-change screen, dashboard, members, offerings, finance, budgets, reference data, reports, user/role management, and member self-service.

For local development, the project will include backend and frontend Dockerfiles plus `docker-compose.yml`, so the full app can run locally with one compose command. Backend APIs enforce security and authorization. The Vue app also hides unavailable routes and navigation items by role.

## Security And Roles

The system seeds one default member/user at startup if no admin exists:

- Username: `admin`
- Temporary password: `password`
- Role: `ADMIN`
- Member profile: minimal bootstrap record with only the fields required to link the account
- First login: user must change password before accessing the rest of the app

After bootstrap, normal member-backed users log in with their mandatory `primaryEmail`. The seeded admin starts with the special bootstrap username `admin` and completes or updates its member profile after the forced password change.

Roles:

- `ADMIN`: full access, including user, role, reference data, and system management.
- `TREASURER`: offering management, finance management, budgets, evidence attachments, approvals, cheque clearing, and official giving report extraction.
- `PASTOR`: member visibility and read-only reporting without financial editing by default.
- `MEMBERSHIP`: member registration, member updates, membership-related reference data, and member account enablement.
- `VIEWER`: read-only access to permitted dashboards and reports.
- `MEMBER`: update their own member profile and view only their own offering status/history.

Every login user is backed by a member record. Passwords are hashed. Spring Security protects sessions or tokens. Sensitive actions record who performed them.

## Main Data

### Church Information

Church information is application configuration rather than user-maintained database data in the first version:

- Banner image path in the resource folder.
- Church log path in the resource folder.
- Church name from `application.yml`.
- Church address from `application.yml`.
- Church contact info from `application.yml`.
- Treasurer name from `application.yml`.

The frontend uses the banner and configured log path for church branding. Official reports use configured church name, address, contact info, and treasurer name where applicable.

### Member

Member records include:

- `primaryEmail`: mandatory and used as the login ID for normal member-backed users.
- `secondaryEmail`: optional.
- Phone numbers, including primary phone, secondary phone, and optional mobile phone.
- Mailing address, including address lines, city, province/state, postal/zip code, and country.
- Legal or display name fields.
- Nickname.
- Birth date.
- Group code.
- Membership status.
- Offering number.
- Optional face image.
- Household or family information.
- Important dates.
- Notes.
- Login/account state, including roles, active/locked state, and first-login password-change flag.

`primaryEmail` must be unique. `offeringNumber` must also be unique when present.

### Offering

Offering records include:

- Date.
- Offering Sunday, used for weekly statistics.
- Amount.
- Fund/category.
- Payment method.
- Optional member link.
- Optional anonymous or group label when not tied to a member.
- Memo.
- Created-by user.

An offering can be linked to a member or recorded as anonymous/group giving. Each offering automatically creates an income financial transaction. Offering fund/category is controlled by reference data type `OFFERING_FUND_CATEGORY`.

### Financial Transaction

Financial transaction records include:

- Type: income or expense.
- Date.
- Amount.
- Category.
- Sub-category, filtered by the selected category.
- HST-included flag.
- Cheque number.
- Cheque-cleared flag.
- Payable-to.
- Treasurer who approves.
- Memo.
- Evidence attachment reference.
- Source reference, such as the offering that generated an income record.
- Created-by user.

Income generated from offerings is linked back to the source offering. Expenses are entered separately. Financial category is controlled by reference data type `FINANCIAL_CATEGORY`. Financial sub-category is controlled by reference data type `FINANCIAL_SUB_CATEGORY` and must reference a parent financial category by `parentCode`.

### Budget

Budget records include:

- Fiscal year.
- Budget type: offering income or expense.
- Fund/category for offering income budgets.
- Category and sub-category for expense budgets, where sub-category is filtered by the selected category.
- Budget.
- Notes.
- Created/updated-by user.

Before each new fiscal year, Treasurer or Admin users enter estimated offering budgets by fund/category and expense budgets by category/sub-category. Expense sub-category choices are filtered by the selected financial category.

### Reference Data

Reference data records maintain controlled lists used by forms and reports:

- `MEMBERSHIP_STATUS`: member status values such as `ACTIVE`, `INACTIVE`, `VISITOR`, and `TRANSFERRED`.
- `GROUP_CODE`: member group values such as `ADULT`, `YOUTH`, `CHILDREN`, and `SENIOR`.
- `OFFERING_FUND_CATEGORY`: offering fund/category values such as `TITHE`, `THANKSGIVING`, `MISSION`, and `BUILDING`.
- `FINANCIAL_CATEGORY`: financial transaction category values such as `OFFICE`, `MINISTRY`, `FACILITY`, and `MISSIONS`.
- `FINANCIAL_SUB_CATEGORY`: financial transaction sub-category values linked to one `FINANCIAL_CATEGORY` through `parentCode`, such as `OFFICE` -> `SUPPLIES`, `FACILITY` -> `UTILITIES`, and `MINISTRY` -> `EVENT`.

Reference data has maintenance screens so common lists can be managed without code changes.
Reference data records include type, code, label, sort order, active flag, and optional parent code. `parentCode` is required for `FINANCIAL_SUB_CATEGORY` and must point to an active or inactive `FINANCIAL_CATEGORY` code so historic records remain interpretable even if a category is later deactivated.

### Attachments

Attachments store metadata for uploaded files, including evidence files for financial transactions and optional member face images. The initial implementation stores files in a local Docker-mounted volume with MongoDB metadata. The attachment service boundary keeps a future object-storage move isolated.

### Audit Entry

Audit records include:

- Action.
- Record type.
- Record ID.
- Actor.
- Timestamp.
- Summary.

Audit entries are created for sensitive changes, including member updates, user/role changes, offerings, finance transactions, budget changes, reference-data changes, password changes, and report extraction.

## Core Workflows

### First Login

1. System starts.
2. If no admin exists, it creates the bootstrap admin user with username `admin` and password `password`.
3. Admin logs in.
4. Admin must change password before accessing other features.
5. Admin can complete profile details and configure reference data.

### Member Management

Membership users and Admin users can create and update member records, assign group code and membership status, manage offering number, upload optional face image, and enable login for members.

Member users can update only their own permitted profile fields and cannot edit their roles, offering number, status, or administrative fields.

### Offering Management

Treasurer and Admin users can record offerings for:

- A specific member.
- Anonymous giving.
- Group giving.

Offerings require amount, date, offering Sunday, fund/category, and payment method. The system creates a linked income transaction automatically.

### Finance Management

Treasurer and Admin users can maintain expense transactions, attach evidence, record cheque details, mark cheques cleared, and approve transactions. Offering-created income transactions are visible in finance and retain their source offering reference.

### Budget Management

Before a new fiscal year starts, Treasurer or Admin users enter:

- Offering income budgets by fund/category.
- Expense budgets by category/sub-category, with sub-categories filtered by the selected financial category.

Reports compare actual income and expenses against the fiscal-year budgets.

### Reference Data Management

Admin users can maintain all reference data. Membership users can maintain membership-related reference data. Treasurer users can maintain finance and offering reference data. The reference data maintenance screen must support parent category selection for `FINANCIAL_SUB_CATEGORY`; the financial transaction and budget screens must use that parent relationship to filter sub-category dropdowns after a category is selected.

### Member Self-Service

Member users can log in with their primary email, update their own permitted profile fields, and view only their own offering status/history.

## Reports

The first version includes these reports:

- Weekly offering status report: totals offerings by offering Sunday, fund/category, member/anonymous/group source, and payment method.
- Offering summary for member(s): filters by member, household/group, date range, and fund/category.
- Official offering tax return report: Treasurer/Admin-only extraction for member annual giving, suitable for official tax-return preparation.
- Financial budget report: yearly income and expense actuals compared against offering budgets and expense budgets.

Reports include filters and export actions. Official tax-return report extraction must create an audit entry.

## API And UI

The backend exposes REST APIs for:

- Auth and password changes.
- Current user/session.
- Members and member self-service.
- Offerings.
- Financial transactions.
- Budgets.
- Reference data.
- Attachments.
- Reports.
- Audit history.
- User and role management.

The Vue app is an operational dashboard. It uses searchable tables, clear edit forms, filters by date/fiscal year/category, report screens, export actions, upload controls, and role-aware navigation.

## Error Handling

The API returns consistent errors for:

- Validation failures.
- Unauthorized or forbidden access.
- Missing records.
- Duplicate primary email.
- Duplicate offering number.
- Invalid reference-data usage.
- Invalid report filters.
- Failed attachment upload.
- First-login password-change requirement.

Frontend screens display errors close to the field or action that caused them.

## Testing

Backend tests use JUnit 5, Spring Test, and Mockito. They cover:

- Security rules by role.
- Admin bootstrap and first-login password change.
- Member validation and uniqueness.
- Offering creation and linked income transaction creation.
- Finance transaction behavior.
- Budget calculations.
- Report filters and access rules.
- Reference-data validation.

Frontend tests use Vitest or Jest and Vue Testing Library. They cover:

- Route guards.
- Role-aware navigation.
- Login and password-change flow.
- Core member, offering, finance, budget, reference-data, and report forms.

## Out Of Scope For First Version

- Microservices.
- External payment processing.
- Email delivery.
- Mobile app.
- Cloud object storage.
- Advanced accounting features beyond simple income, expenses, budgets, approvals, cheque status, and reports.

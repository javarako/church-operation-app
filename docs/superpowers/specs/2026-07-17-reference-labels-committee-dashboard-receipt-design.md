# Reference Labels, Committees, Dashboard Roles, And Receipt Logo Design

## Goal

Improve readability across operational lists and reports, allow members to
belong to multiple committees, show every assigned dashboard role, and enlarge
the official tax-receipt logo without changing stored reference codes or the
receipt's duplicated half-page layout.

## Member Committees

Members store `committeeCodes` as a set of zero or more `COMMITTEE_CODE`
reference codes.

- Member create and update requests accept `committeeCodes`.
- Member responses return the stored set.
- The backend normalizes codes, removes blank and duplicate values, and rejects
  codes that do not exist or are inactive.
- Existing members without the field load with an empty set.
- Member Detail displays a compact multi-select dropdown with checkbox choices
  from active Committee Code reference data.
- The member list does not add a Committee column.
- A Committee Code cannot be deleted while assigned to a member.
- Full database backup, restore, and fiscal processes need no special conversion
  because the values are embedded in member documents.

## Labels

Stored API and database values remain codes. Vue resolves labels from active
reference-data options already loaded for each screen.

- Members list: Group and Status display labels.
- Offerings list: Payment displays the Payment Method label.
- Weekly Offering Status: Fund, Category, Giving Type, and Payment Method
  display readable labels.
- Offering Summary: Fund and Category display labels.
- Financial Actual vs Budget: Category and Sub-category display labels.
- CSV exports use the same displayed labels as the tables.
- Unknown or historical codes fall back to the original code rather than
  rendering blank.

Giving Type uses fixed display labels because it is an application enum rather
than reference data: `MEMBER` becomes `Member`, `ANONYMOUS` becomes
`Anonymous`, and `GROUP` becomes `Group`.

For Financial Actual vs Budget, offering income categories use Offering Fund
and Offering Category reference data. Expense categories use Financial
Category and Financial Sub-category reference data.

## Budget Percentage

Financial Actual vs Budget adds a `Budget vs. Actual` column between Actual and
Variance.

- Formula: `Actual / Budget * 100`.
- Display precision: two decimal places followed by `%`.
- A zero Budget displays `-`.
- The CSV export includes the same percentage column and value.
- The report Type label displays `INCOME` for stored `OFFERING_INCOME`;
  `EXPENSE` and `CARRY_OVER` retain their current labels.

The calculation remains in Vue because it is presentation-only and both source
amounts already exist in every report row.

## Dashboard Roles

The dashboard identity area displays all roles assigned to the signed-in user,
in their existing API order, separated by commas. When no role is present it
falls back to `User`.

## Tax Receipt Logo

The existing church logo on both copies of the official tax receipt increases
to 130% of its current rendered dimensions.

- Width and height scale together to preserve the source aspect ratio.
- Text placement adjusts only as needed to prevent overlap.
- The letter-size page and two identical half-page receipt copies remain
  unchanged.
- Receipt rendering tests inspect the generated PDF layout or renderer
  dimensions to prevent regression.

## Testing

Backend tests cover:

- multiple Committee Code persistence and normalization;
- rejection of inactive or unknown Committee Codes;
- Committee Code deletion protection; and
- existing members with no committee assignments.

Frontend tests cover:

- Committee multi-selection and member save payload;
- member Group and Status labels;
- offering Payment Method labels;
- all requested report labels and CSV values;
- `INCOME` display;
- percentage formatting and zero-budget fallback; and
- all assigned dashboard roles.

Tax receipt tests cover the 30% logo-size increase while preserving the
duplicated half-page receipt format.

Full backend and frontend test suites, production build, Docker build, and local
runtime probes run before completion.

## Acceptance Criteria

1. A member can be assigned any number of active Committee Codes.
2. An assigned Committee Code cannot be deleted.
3. Group, Status, Payment Method, report reference values, and Giving Type show
   labels instead of raw codes.
4. Report CSV exports match the labels displayed on screen.
5. Financial Actual vs Budget displays `INCOME` and the correctly calculated
   Budget vs. Actual percentage.
6. Zero-budget rows display `-` for the percentage.
7. The dashboard displays every assigned role.
8. Both tax-receipt copies render the church logo 30% larger without overlap.

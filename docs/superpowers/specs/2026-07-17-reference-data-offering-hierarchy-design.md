# Reference Data And Offering Hierarchy Design

## Goal

Strengthen reference-data maintenance and split the current combined offering
fund/category value into a real Fund -> Category hierarchy without losing
existing church data.

## Scope

This enhancement includes:

- immutable reference-data identity after creation;
- ADMIN-only reference-data maintenance;
- active reference-data reads for authorized operational users;
- a new `COMMITTEE_CODE` reference-data type;
- separate `OFFERING_FUND` and `OFFERING_CATEGORY` reference-data types;
- migration of existing combined offering values beneath a default fund;
- Fund -> Category selection in offering and offering-budget workflows;
- corresponding linked-income, report, archive, and deletion behavior; and
- immediate refresh of parent dropdowns after creating or updating a category.

Adding Committee Code to member records is outside this enhancement. Committee
Code is available for administration and future use only.

## Reference Data Identity

A reference-data record is identified by its `type + code`.

- `type` and `code` are set during creation.
- Neither field can be changed afterward.
- The edit form renders Type and Code as disabled or read-only controls.
- The backend compares the submitted Type and Code with the stored record and
  rejects any mismatch, even if a client bypasses the UI.
- Label, parent, sort order, and active status remain editable.
- Code uniqueness remains scoped to the reference-data type.

An attempted identity change returns a clear validation error and does not
modify the stored record.

## Authorization

Only users with the `ADMIN` role may:

- see the Reference Data navigation item;
- navigate to `/reference-data`;
- list all active and inactive values for maintenance;
- create reference data;
- update reference data; or
- delete unused reference data.

Authenticated users may continue reading active reference values when an
authorized workflow needs them. This preserves dropdown access for:

- MEMBERSHIP users on Members;
- TREASURER users on Offerings, Finance, and Budgets;
- report users on Reports; and
- other existing authorized operational screens.

`GET /api/reference-data/{type}` returns active values only and remains the
operational dropdown endpoint. `GET /api/reference-data/maintenance/{type}`
returns active and inactive values and requires ADMIN. Backend services enforce
this boundary; menu and router restrictions are additional UI protections.

## Reference Types

The maintained reference types become:

- `GROUP_CODE`
- `MEMBERSHIP_STATUS`
- `COMMITTEE_CODE`
- `OFFERING_FUND`
- `OFFERING_CATEGORY`
- `PAYMENT_METHOD`
- `FINANCIAL_CATEGORY`
- `FINANCIAL_SUB_CATEGORY`

`OFFERING_CATEGORY.parentCode` is required and references an existing
`OFFERING_FUND` code.

`FINANCIAL_SUB_CATEGORY.parentCode` remains required and references an existing
`FINANCIAL_CATEGORY` code.

The legacy `OFFERING_FUND_CATEGORY` enum value remains readable during
migration so existing MongoDB documents can be loaded safely, but it is no
longer offered for new maintenance after migration.

No default Committee Code values are seeded because committee names are
church-specific.

## Existing Data Migration

Migration is idempotent and runs during application startup before ordinary
reference-data bootstrap. This preserves customized legacy labels, sort orders,
and active statuses before default values are seeded.

1. Create the active offering fund `GENERAL` with label `General Fund` when it
   does not already exist.
2. For every legacy `OFFERING_FUND_CATEGORY` value, create or retain an
   `OFFERING_CATEGORY` with the same code, label, sort order, and active status,
   and set its parent to `GENERAL`.
3. Migrate each existing offering:
   - `fundCode = GENERAL`
   - `categoryCode = previous fundCategory`
4. Migrate each linked offering income transaction:
   - `category = GENERAL`
   - `subCategory = previous category`
5. Migrate each `OFFERING_INCOME` budget:
   - `category = GENERAL`
   - `subCategory = previous category`
6. Preserve legacy fields until the new values have been written so migration
   can be retried safely after interruption.

Migration does not overwrite a nonblank new Fund or Category value. Existing
record IDs, offering amounts, member links, receipt data, and audit history
remain unchanged.

New installations seed `GENERAL` plus the existing sample categories:
`TITHE`, `THANKSGIVING`, `MISSION`, and `BUILDING`.

## Offering Workflow

Offering records store required `fundCode` and `categoryCode` values.

- Fund options come from active `OFFERING_FUND` values.
- Category options come from active `OFFERING_CATEGORY` values filtered by the
  selected Fund through `parentCode`.
- Changing Fund clears the selected Category and reloads the category list.
- Create and update validation requires an active Fund and an active Category
  whose parent matches that Fund.
- Offering list and filters display Fund and Category separately.

The automatically linked income transaction mirrors the hierarchy:

- financial `category` stores the offering Fund;
- financial `subCategory` stores the offering Category.

Creating or editing an offering updates the linked income transaction
immediately. Daily offering summaries group by date, Fund, and Category so
different categories are not combined accidentally.

## Budget Workflow

`OFFERING_INCOME` budgets use the same Fund -> Category relationship:

- budget `category` stores the offering Fund;
- budget `subCategory` stores the offering Category;
- both are required for newly created offering budgets; and
- Category options are filtered by the selected Fund.

Expense budgets retain their existing Financial Category -> Financial
Sub-category behavior.

Uniqueness validation includes both Fund and Category for offering budgets.

## Reports And Archives

Offering reports that currently expose or filter the combined fund/category
value use separate Fund and Category values. This includes weekly offering and
member offering summary data. Official tax receipt totals remain unchanged
because they aggregate eligible offering amounts rather than fund labels.

Fiscal archive preview, package creation, validation, cleanup, restore, and
reference dependency checks understand both offering Fund and Category.
Existing archives containing the legacy field remain restorable by mapping
their values to `GENERAL` during restore.

Full database backup and restore require no format conversion because they
preserve MongoDB collections exactly; startup migration applies after an older
database is restored.

## Deletion Protection

Reference-data deletion remains a hard delete only when no dependency exists.

- An Offering Fund cannot be deleted when used by an offering, linked income,
  offering budget, offering category, or cleaned fiscal archive.
- An Offering Category cannot be deleted when used by an offering, linked
  income, offering budget, or cleaned fiscal archive.
- A Committee Code has no domain dependency in this enhancement and can be
  deleted when otherwise unused.
- Existing dependency rules for member and financial reference types remain.

Only ADMIN can invoke deletion.

## Reference Data UI

The maintenance page shows all new reference types and uses parent selectors
for both hierarchical child types:

- Offering Category -> Parent Fund
- Financial Sub-category -> Parent Financial Category

When editing an existing row:

- Type is fixed.
- Code is visibly read-only.
- Label, parent, sort order, and active status are editable.

After a successful create, update, or delete, the page refreshes:

- the selected type's table;
- the Offering Fund parent-option cache; and
- the Financial Category parent-option cache.

This fixes the current defect where a newly created Financial Category is not
available in the Financial Sub-category parent dropdown until the user leaves
and revisits the page. The same immediate behavior applies to new Offering
Funds.

## Error Handling

- Changing an existing Type or Code: reject with an immutable-identity message.
- Duplicate Type and Code: reject with the existing duplicate-code message.
- Missing parent for a child type: reject with a type-specific message.
- Parent does not exist: reject.
- Parent and child hierarchy mismatch in an offering or budget: reject.
- Non-ADMIN maintenance request: reject without modifying data.
- Migration conflict: preserve existing new-format values, log a warning with
  the affected record ID, and leave the legacy field unchanged for a later
  retry.

## Testing

Backend tests cover:

- ADMIN-only list-all/create/update/delete behavior;
- active-value reads by operational users;
- immutable Type and Code updates;
- Committee Code creation;
- Offering Category parent validation;
- idempotent legacy migration;
- offering and linked-income Fund/Category persistence;
- offering-budget hierarchy validation;
- deletion dependencies;
- report mappings; and
- fiscal archive compatibility.

Frontend tests cover:

- ADMIN-only menu and route access;
- operational dropdown access for non-ADMIN roles;
- read-only Type and Code while editing;
- Committee Code in the type selector;
- immediate parent-cache refresh after creating a Financial Category;
- immediate parent-cache refresh after creating an Offering Fund;
- category filtering after selecting an Offering Fund; and
- offering and budget request payloads containing both hierarchy levels.

Full backend, frontend, production-build, and Docker verification run before
completion.

## Acceptance Criteria

1. An existing reference-data Code cannot be modified through the UI or API.
2. An existing reference-data Type cannot be modified through the UI or API.
3. Only ADMIN can access or maintain the Reference Data section.
4. Authorized non-ADMIN workflows continue to load active dropdown values.
5. Committee Code appears as a maintainable reference type.
6. Offerings and offering budgets use separate Fund and filtered Category
   values.
7. Existing combined offering values migrate beneath General Fund without data
   loss.
8. Linked income, reports, deletion checks, and archives preserve the new
   hierarchy.
9. A newly created Financial Category appears immediately as a parent option
   without navigating away.
10. A newly created Offering Fund appears immediately as a parent option
    without navigating away.

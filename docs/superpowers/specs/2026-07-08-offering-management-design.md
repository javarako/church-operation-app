# Offering Management Design

## Purpose

Add the first Offering Management slice to the church operations app. Treasurer and Admin users can record offerings for members, anonymous giving, and group giving. Each saved offering immediately creates a linked income financial transaction so finance reports can use offering income from the beginning.

## Scope

This slice includes:

- Offering record storage and REST APIs.
- A linked income financial transaction record created by the backend when an offering is saved.
- Offering list and record form in the Vue app.
- Member search/select for member offerings.
- Anonymous and group offering support.
- Offering fund/category dropdown from reference data.
- Offering Sunday field for weekly offering reports, auto-filled from the offering date and editable by the user.

This slice does not include:

- Full finance transaction management UI.
- Budget comparison reports.
- Tax receipt/report extraction.
- Attachment upload.
- Audit entry persistence, unless the existing app already has the audit boundary ready.

## Roles And Access

- `ADMIN`: can list and record offerings.
- `TREASURER`: can list and record offerings.
- `VIEWER`: can view offerings when the route is enabled for read-only reporting.
- `MEMBER`: cannot access the offering management screen; member self-service offering history is out of scope for this slice.
- `PASTOR` and `MEMBERSHIP`: no offering management access in this slice.

The backend enforces role access. The frontend hides unavailable navigation items and guards the `/offerings` route.

## Data Model

### Offering

Fields:

- `id`
- `givingType`: `MEMBER`, `ANONYMOUS`, or `GROUP`
- `memberId`: required when `givingType` is `MEMBER`
- `giverLabel`: required when `givingType` is `ANONYMOUS` or `GROUP`
- `offeringDate`
- `offeringSunday`: defaults to the offering date when the offering date is Sunday; otherwise defaults to the coming Sunday after the offering date
- `fundCategory`
- `amount`
- `paymentMethod`
- `memo`
- `incomeTransactionId`
- `createdBy`
- `createdAt`

Validation:

- `amount` must be greater than zero.
- `offeringDate` and `offeringSunday` are required.
- `offeringSunday` remains editable so Treasurer/Admin users can correct special cases.
- `fundCategory` is required and must match active `OFFERING_FUND_CATEGORY` reference data.
- `memberId` is required only for member offerings.
- `giverLabel` is required only for anonymous or group offerings.

### Financial Transaction

This slice adds the minimum transaction model needed for linked offering income:

- `id`
- `type`: `INCOME`
- `transactionDate`: copied from offering date
- `amount`: copied from offering amount
- `category`: copied from offering fund/category
- `subCategory`: empty for offering-created income in this slice
- `sourceType`: `OFFERING`
- `sourceId`: offering id
- `memo`: copied from offering memo or generated from offering source
- `createdBy`
- `createdAt`

Offering-created income transactions are treated as system-linked records. A future finance screen can show them as read-only or restrict edits to finance-only status fields.

## Backend Flow

`OfferingService.createOffering(request, actor)` performs the operation:

1. Validate the offering request.
2. Resolve member details when `givingType` is `MEMBER`.
3. Save the offering without `incomeTransactionId`.
4. Create an income financial transaction with `sourceType = OFFERING` and `sourceId = offering.id`.
5. Update the offering with the linked `incomeTransactionId`.
6. Return an offering response including the linked transaction id.

The offering and linked transaction must be created in one service operation. The first implementation keeps the flow isolated in `OfferingService`; if Mongo transaction support is configured in a future slice, the transaction boundary can be added around this service method without changing controllers.

## API

### List Offerings

`GET /api/offerings`

Initial filters:

- `fromDate`
- `toDate`
- `offeringSunday`
- `fundCategory`
- `givingType`

Returns offering rows sorted newest first.

### Create Offering

`POST /api/offerings`

Request contains giving type, member or label, dates, fund/category, amount, payment method, and memo.

Response includes the saved offering and `incomeTransactionId`.

## Frontend

Route: `/offerings`

Layout:

- Header: `Offerings` plus `Record offering` action for Admin/Treasurer.
- Filter bar: date range, offering Sunday, fund/category, giving type.
- Summary strip: total amount for current loaded rows.
- Table: date, offering Sunday, giver, fund/category, amount, payment method, memo, linked income transaction.
- Record form: shown as the right-side panel or page section using the existing operational UI style.

Record form behavior:

- Giving type selector controls whether the member search or free-text label is shown.
- Fund/category options come from `OFFERING_FUND_CATEGORY`.
- Offering Sunday is auto-calculated from the selected offering date: today if the selected date is Sunday, otherwise the coming Sunday. The field remains editable.
- Amount accepts numbers only and formats consistently.
- After save, the list refreshes and the form resets.
- API errors appear inline near the form.

## Testing

Backend tests:

- Member offering creates an offering and linked income transaction.
- Anonymous/group offering creates an offering and linked income transaction without member id.
- Invalid amount fails.
- Missing member id for member offering fails.
- Missing label for anonymous/group offering fails.

Frontend verification:

- Build/type-check passes.
- Offering page loads reference data.
- Giving type selection switches required fields.
- Save sends the expected payload shape.

## Open Follow-Ups

- Add full Finance Transactions UI.
- Add member self-service offering history.
- Add weekly offering report.
- Add audit entry persistence for offering creation and official report extraction.

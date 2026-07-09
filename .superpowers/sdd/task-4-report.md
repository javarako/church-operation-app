# Task 4 Report: Add Budget REST API

## Status
DONE

## What I changed
- Added `backend/src/main/java/com/church/operation/rest/BudgetController.java`.
- Wired the controller to `BudgetService` with:
  - `GET /api/budgets?fiscalYear=2026`
  - `POST /api/budgets`
  - `PUT /api/budgets/{id}`
  - `DELETE /api/budgets/{id}`
- Kept the controller aligned with the existing `OfferingController` and `FinanceController` pattern by using the authenticated principal as the `Member` actor and mapping service entities to `BudgetResponse`.

## Verification
- Ran `cd backend && mvn test`
- Result: PASS

## Notes
- No additional backend files were changed.
- No frontend files were touched.

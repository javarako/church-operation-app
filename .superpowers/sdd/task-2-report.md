# Task 2 Report

Status: complete with the expected red test state.

What changed:
- Added `backend/src/main/java/com/church/operation/dto/BudgetRequest.java`.
- Added `backend/src/main/java/com/church/operation/dto/BudgetResponse.java`.
- Added `backend/src/test/java/com/church/operation/service/BudgetServiceTest.java` with the requested scenarios and helpers.

Verification:
- Ran `cd backend && mvn test -Dtest=BudgetServiceTest`.
- Result: failed as expected because `BudgetService` is not implemented yet.
- Final failure after DTO creation is only `cannot find symbol: class BudgetService`.

Notes:
- I did not implement `BudgetService`.
- I did not touch frontend code or unrelated backend files.

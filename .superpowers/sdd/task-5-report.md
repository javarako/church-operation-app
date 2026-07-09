Task 5 Report

Status: DONE

Summary:
- Added `frontend/src/api/budgets.ts` with the budget API client and verbatim interfaces/functions from the task brief.
- Updated `frontend/src/router/index.ts` to route `/budgets` to `BudgetsView` with `financeRoles`.

Verification:
- Ran `cd frontend && npm run build`.
- Build failed as expected because `frontend/src/views/BudgetsView.vue` does not exist yet.

Concerns:
- None.

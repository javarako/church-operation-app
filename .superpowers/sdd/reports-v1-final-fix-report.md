## 2026-07-09 Reports V1 final review fixes

- Status: completed
- Files changed:
  - `backend/src/main/java/com/church/operation/service/ReportService.java`
  - `backend/src/test/java/com/church/operation/service/ReportServiceTest.java`
  - `frontend/src/views/ReportsView.vue`
  - `frontend/src/views/ReportsView.test.ts`
- Commit hash: `1a14cf8243ce29f95d6f6f6efd1e57cc6ce3c5ea`

### Commands and results

1. `cd frontend && npm test -- --run src/views/ReportsView.test.ts`
   - Result: failed before fixes
   - Notes:
     - `exports official tax csv with church metadata after confirmation` failed because the CSV omitted church metadata columns.
     - `shows a form error and skips weekly api calls when end date is before start date` failed because no form-level error was rendered.
     - `shows a form error and skips member api calls when end date is before start date` failed because no form-level error was rendered.

2. `cd backend && mvn -q -Dtest=ReportServiceTest test`
   - Result: failed before fixes
   - Notes:
     - `financialReportIncludesActualsWithoutMatchingBudgetRows` failed because actual-only keys were omitted from `financialBudget`.

3. `cd frontend && npm test -- --run src/views/ReportsView.test.ts`
   - Result: passed
   - Output summary: `Test Files  1 passed (1)` and `Tests  7 passed (7)`.

4. `cd backend && mvn -q -Dtest=ReportServiceTest test`
   - Result: passed
   - Output summary: exit code `0` with a Mockito self-attach warning only.

5. `cd backend && mvn -q -Dtest=ReportServiceTest test`
   - Result: passed
   - Output summary: exit code `0` with a Mockito self-attach warning only.

6. `cd frontend && npm test`
   - Result: passed
   - Output summary: `Test Files  2 passed (2)` and `Tests  10 passed (10)`.

7. `cd frontend && npm run build`
   - Result: passed
   - Output summary: `vite build` completed successfully with production assets emitted under `frontend/dist/`.

### Concerns

- Backend test runs emit the existing Mockito self-attach warning on this JDK, but the requested test command exits successfully.

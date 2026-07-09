### Task 5: Add Frontend Budget API And Route

**Files:**
- Create: `frontend/src/api/budgets.ts`
- Modify: `frontend/src/router/index.ts`

**Interfaces:**
- Produces `listBudgets(fiscalYear: number): Promise<Budget[]>`.
- Produces `createBudget(payload: BudgetPayload): Promise<Budget>`.
- Produces `updateBudget(id: string, payload: BudgetPayload): Promise<Budget>`.
- Produces `deleteBudget(id: string): Promise<Budget>`.

- [ ] **Step 1: Add API client**

Create `frontend/src/api/budgets.ts`:

```ts
import { deleteJson, getJson, postJson, putJson } from './http';

export type BudgetType = 'CARRY_OVER' | 'OFFERING_INCOME' | 'EXPENSE';

export interface Budget {
  id: string;
  fiscalYear: number;
  budgetType: BudgetType;
  category?: string;
  subCategory?: string;
  budget: number;
  memo?: string;
}

export interface BudgetPayload {
  fiscalYear: number;
  budgetType: BudgetType;
  category?: string;
  subCategory?: string;
  budget: number;
  memo?: string;
}

export function listBudgets(fiscalYear: number) {
  return getJson<Budget[]>(`/api/budgets?fiscalYear=${encodeURIComponent(String(fiscalYear))}`);
}

export function createBudget(payload: BudgetPayload) {
  return postJson<BudgetPayload, Budget>('/api/budgets', payload);
}

export function updateBudget(id: string, payload: BudgetPayload) {
  return putJson<BudgetPayload, Budget>(`/api/budgets/${id}`, payload);
}

export function deleteBudget(id: string) {
  return deleteJson<Budget>(`/api/budgets/${id}`);
}
```

- [ ] **Step 2: Route to real view**

Import `BudgetsView` and replace the `/budgets` placeholder with:

```ts
{ path: '/budgets', component: BudgetsView, meta: { roles: financeRoles } },
```

- [ ] **Step 3: Run frontend build to verify missing view**

Run: `cd frontend && npm run build`

Expected: FAIL because `BudgetsView.vue` does not exist.

---


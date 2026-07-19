# Budget List Reference Order Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display Budgets page rows in Income-first, Expense-second order, using parent and child reference-data `sortOrder` before pagination.

**Architecture:** Add a computed frontend ordering layer in `BudgetsView.vue`. It will resolve each budget against the reference collections already loaded by the page, compare type and reference order, and provide the sorted result to the existing pagination composable.

**Tech Stack:** Vue 3, TypeScript, Vitest, Vue Testing Library

## Global Constraints

- Type order is `OFFERING_INCOME`, then `EXPENSE`.
- Income uses `OFFERING_FUND` then `OFFERING_CATEGORY` reference order.
- Expense uses `FINANCIAL_CATEGORY` then `FINANCIAL_SUB_CATEGORY` reference order.
- Unknown and inactive historical reference codes remain visible and sort after
  configured active references.
- Sorting occurs before pagination.
- No backend or API changes.
- Leave changes uncommitted for the user.

---

### Task 1: Add Budget List Reference Ordering

**Files:**
- Modify: `frontend/src/views/BudgetsView.test.ts`
- Modify: `frontend/src/views/BudgetsView.vue`

**Interfaces:**
- Consumes: `Budget`, `BudgetType`, and `ReferenceDataOption`.
- Produces: the existing `filteredBudgets` computed value in the required display order.

- [ ] **Step 1: Write the failing ordering test**

Add a test that returns budgets in deliberately incorrect API order and reference
records with deliberately non-alphabetical `sortOrder` values:

```ts
it('sorts budgets by type and parent and child reference order', async () => {
  listBudgetsMock.mockResolvedValue([
    budget('expense-printing', 'EXPENSE', 'OFFICE', 'PRINTING'),
    budget('income-mission', 'OFFERING_INCOME', 'GENERAL', 'MISSION'),
    budget('expense-repair', 'EXPENSE', 'FACILITY', 'REPAIR'),
    budget('income-tithe', 'OFFERING_INCOME', 'GENERAL', 'TITHE'),
  ]);
  listReferenceDataMock.mockImplementation((type) => Promise.resolve(referenceData[type] ?? []));

  render(BudgetsView);
  await screen.findByText('Refreshed');

  expect(displayedBudgetMemos()).toEqual([
    'income-tithe',
    'income-mission',
    'expense-repair',
    'expense-printing',
  ]);
});
```

The fixture must assign:

```ts
OFFERING_FUND: GENERAL order 10
OFFERING_CATEGORY: TITHE order 10, MISSION order 20
FINANCIAL_CATEGORY: FACILITY order 10, OFFICE order 20
FINANCIAL_SUB_CATEGORY: REPAIR order 10 under FACILITY,
                        PRINTING order 10 under OFFICE
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd frontend
npx vitest run src/views/BudgetsView.test.ts
```

Expected: the new test fails because rows still follow the API response order.

- [ ] **Step 3: Add unknown-reference and filter assertions**

Add coverage showing:

```ts
configured reference row < unknown reference row
```

and select the `EXPENSE` list filter to verify only expense rows remain in the
same reference-data order.

Add a 21-row fixture with an Income row last in the API response and assert it
appears first on the 20-row first page. This proves sorting occurs before
pagination.

- [ ] **Step 4: Implement the minimal comparator**

In `BudgetsView.vue`, replace the filter-only computed value with filter and
sort:

```ts
const BUDGET_TYPE_ORDER: Record<BudgetType, number> = {
  OFFERING_INCOME: 0,
  EXPENSE: 1,
  CARRY_OVER: 2,
};

const filteredBudgets = computed(() =>
  budgets.value
    .filter((budget) => !filters.budgetType || budget.budgetType === filters.budgetType)
    .slice()
    .sort(compareBudgets),
);
```

Add lookup and comparison helpers that:

```ts
typeOrder
parent sortOrder -> label -> code
child sortOrder -> label -> code
budget.id
```

Use `Number.MAX_SAFE_INTEGER` for missing reference records and the budget code
as the fallback label. Resolve parent and child options from the matching
reference collection for each budget type.

- [ ] **Step 5: Run the focused test and verify GREEN**

Run:

```bash
cd frontend
npx vitest run src/views/BudgetsView.test.ts
```

Expected: all Budgets view tests pass.

- [ ] **Step 6: Run complete frontend verification**

Run:

```bash
cd frontend
npm test -- --run
npm run build
```

Expected: all frontend tests pass and the production build exits successfully.

- [ ] **Step 7: Review the final diff**

Run:

```bash
git diff --check
git diff -- frontend/src/views/BudgetsView.vue frontend/src/views/BudgetsView.test.ts
```

Confirm the sort is applied before `usePagination`, unknown references remain
visible, and no unrelated files were modified by this task.

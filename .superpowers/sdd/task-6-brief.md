### Task 6: Build Budget Management UI

**Files:**
- Create: `frontend/src/views/BudgetsView.vue`
- Uses: `frontend/src/api/budgets.ts`, `frontend/src/api/referenceData.ts`

**Interfaces:**
- Consumes active offering funds, financial categories, and filtered financial sub-categories.
- Consumes budget CRUD API.

- [ ] **Step 1: Create view using existing layout classes**

Use `workspace`, `page-header`, `two-column`, `panel`, `toolbar`, `table-wrap`, `form-grid`, `actions`, `row-actions`, and `icon-button`.

Header:

```html
<h2>Budgets</h2>
<button type="button" @click="startCreate">Add budget</button>
```

- [ ] **Step 2: Add toolbar and table**

Toolbar controls:

```html
<input v-model.number="selectedFiscalYear" type="number" min="2000" max="2100" @change="loadBudgets" />
<select v-model="filters.budgetType">
  <option value="">All types</option>
  <option value="CARRY_OVER">Carry over</option>
  <option value="OFFERING_INCOME">Offering income</option>
  <option value="EXPENSE">Expense</option>
</select>
<button type="button" @click="loadBudgets">Refresh</button>
```

Table columns:

```text
Type, Fiscal year, Category, Sub-category, Budget, Memo, [trash icon]
```

Row behavior:

- clicking any row calls `selectBudget(budget)`
- selected row gets `selected` class
- trash icon calls `deleteSelectedBudget(budget)`

- [ ] **Step 3: Add budget form**

Fields:

- budget type dropdown
- fiscal year
- budget
- category dropdown
- sub-category dropdown for expense only
- memo

Form title:

```html
<h3>{{ editingBudgetId ? 'Budget Detail' : 'New Budget' }}</h3>
```

- [ ] **Step 4: Implement category/sub-category behavior**

Rules:

- `CARRY_OVER`: hide category and sub-category controls.
- `OFFERING_INCOME`: load category options from `OFFERING_FUND_CATEGORY`.
- `EXPENSE`: load category options from `FINANCIAL_CATEGORY`.
- Expense sub-categories load from `FINANCIAL_SUB_CATEGORY` with selected category as parent.

Functions:

```ts
async function handleBudgetTypeChange() {
  form.category = '';
  form.subCategory = '';
  await loadCategoryOptions();
  await loadSubCategoryOptions();
}

async function handleCategoryChange() {
  form.subCategory = '';
  await loadSubCategoryOptions();
}
```

- [ ] **Step 5: Implement save/delete**

Save:

- require numeric `budget`
- create when no selected id
- update when selected id exists
- reload budget list
- reset form
- show `Saved` or `Updated`

Delete:

- confirm with `window.confirm`
- call `deleteBudget(id)`
- reload list
- reset form if selected
- show `Deleted`

- [ ] **Step 6: Run frontend build**

Run: `cd frontend && npm run build`

Expected: PASS.

---


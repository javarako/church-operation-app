# Dashboard V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder home page with a role-aware staff dashboard that summarizes offerings, fiscal budget performance, membership, finance activity, and quick navigation.

**Architecture:** This is a frontend-only slice. `DashboardView.vue` will call existing APIs, summarize returned rows locally, and render only the panels allowed by the signed-in user's roles. Tests will mock those APIs and verify role-aware content, totals, quick links, and section-level errors.

**Tech Stack:** Vue 3, Vue Router, TypeScript, Vue Testing Library, Vitest, existing Spring Boot report/member/finance APIs.

## Global Constraints

- Use the existing `/` route and staff role guard.
- Do not add a new backend dashboard endpoint in this slice.
- Do not add a chart library.
- Do not change member self-service behavior.
- Keep the UI operational and compact, matching Members, Offerings, Finance, Budgets, and Reports.
- Use existing APIs: weekly offering report, financial actual-vs-budget report, members, and finance transactions.
- Load only datasets allowed by the signed-in user's roles.
- If one section fails, show that section's inline error and leave other dashboard sections visible.

---

## File Structure

- Modify `frontend/src/views/DashboardView.vue`
  - Owns dashboard date calculations, API loading, role checks, local aggregation, section errors, and rendering.
- Create `frontend/src/views/DashboardView.test.ts`
  - Owns dashboard behavior tests using mocked API modules and `authState`.
- No backend files are modified.
- No router changes are required because `/` already points to `DashboardView.vue` with `staffRoles`.

---

### Task 1: Add Dashboard Role and Total Tests

**Files:**
- Create: `frontend/src/views/DashboardView.test.ts`
- Modify: none
- Test: `frontend/src/views/DashboardView.test.ts`

**Interfaces:**
- Consumes:
  - `authState.currentUser` from `frontend/src/auth/authStore.ts`
  - `listWeeklyOfferingReport(filters)` from `frontend/src/api/reports.ts`
  - `listFinancialBudgetReport(filters)` from `frontend/src/api/reports.ts`
  - `listMembers(search)` from `frontend/src/api/members.ts`
  - `listFinanceTransactions()` from `frontend/src/api/finance.ts`
- Produces:
  - A failing test suite that defines expected dashboard behavior.

- [ ] **Step 1: Create the failing dashboard test file**

Create `frontend/src/views/DashboardView.test.ts` with this content:

```ts
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/vue';
import { createRouter, createWebHistory } from 'vue-router';
import DashboardView from './DashboardView.vue';
import { authState } from '../auth/authStore';
import { listFinanceTransactions } from '../api/finance';
import { listMembers } from '../api/members';
import { listFinancialBudgetReport, listWeeklyOfferingReport } from '../api/reports';

vi.mock('../api/reports', () => ({
  listWeeklyOfferingReport: vi.fn().mockResolvedValue([]),
  listFinancialBudgetReport: vi.fn().mockResolvedValue([]),
}));

vi.mock('../api/members', () => ({
  listMembers: vi.fn().mockResolvedValue([]),
}));

vi.mock('../api/finance', () => ({
  listFinanceTransactions: vi.fn().mockResolvedValue([]),
}));

const weeklyReportMock = vi.mocked(listWeeklyOfferingReport);
const financialReportMock = vi.mocked(listFinancialBudgetReport);
const membersMock = vi.mocked(listMembers);
const financeMock = vi.mocked(listFinanceTransactions);

function router() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: DashboardView },
      { path: '/members', component: { template: '<span>Members</span>' } },
      { path: '/offerings', component: { template: '<span>Offerings</span>' } },
      { path: '/finance', component: { template: '<span>Finance</span>' } },
      { path: '/budgets', component: { template: '<span>Budgets</span>' } },
      { path: '/reference-data', component: { template: '<span>Reference Data</span>' } },
      { path: '/reports', component: { template: '<span>Reports</span>' } },
    ],
  });
}

async function renderDashboard() {
  const testRouter = router();
  testRouter.push('/');
  await testRouter.isReady();
  return render(DashboardView, {
    global: {
      plugins: [testRouter],
    },
  });
}

describe('DashboardView', () => {
  beforeEach(() => {
    weeklyReportMock.mockResolvedValue([
      {
        offeringSunday: '2026-07-05',
        fundCategory: 'TITHE',
        givingType: 'MEMBER',
        paymentMethod: 'CASH',
        count: 2,
        totalAmount: 100,
      },
      {
        offeringSunday: '2026-07-05',
        fundCategory: 'MISSION',
        givingType: 'GROUP',
        paymentMethod: 'CHEQUE',
        count: 1,
        totalAmount: 50,
      },
    ]);
    financialReportMock.mockResolvedValue([
      {
        fiscalYear: 2026,
        budgetType: 'OFFERING_INCOME',
        category: 'TITHE',
        budget: 1000,
        actual: 650,
        variance: -350,
      },
      {
        fiscalYear: 2026,
        budgetType: 'EXPENSE',
        category: 'FACILITY',
        subCategory: 'RENT',
        budget: 400,
        actual: 125,
        variance: 275,
      },
    ]);
    membersMock.mockResolvedValue([
      {
        id: 'member-1',
        primaryEmail: 'active@example.com',
        displayName: 'Active Member',
        roles: ['MEMBER'],
        active: true,
        locked: false,
        mustChangePassword: false,
      },
      {
        id: 'member-2',
        primaryEmail: 'locked@example.com',
        displayName: 'Locked Member',
        roles: ['MEMBER'],
        active: false,
        locked: true,
        mustChangePassword: false,
      },
    ]);
    financeMock.mockResolvedValue([
      {
        id: 'income-1',
        type: 'INCOME',
        transactionDate: '2026-07-05',
        amount: 150,
        category: 'TITHE',
        hstIncluded: false,
        chequeCleared: false,
        sourceType: 'OFFERING',
      },
      {
        id: 'expense-1',
        type: 'EXPENSE',
        transactionDate: '2026-07-06',
        amount: 75,
        category: 'FACILITY',
        hstIncluded: true,
        chequeCleared: true,
        sourceType: 'MANUAL',
      },
    ]);
  });

  afterEach(() => {
    cleanup();
    authState.currentUser = null;
    vi.clearAllMocks();
  });

  it('shows admin all dashboard sections and totals', async () => {
    authState.currentUser = {
      primaryEmail: 'admin@example.com',
      displayName: 'Admin',
      roles: ['ADMIN'],
      mustChangePassword: false,
      token: 'token',
    };

    await renderDashboard();

    expect(await screen.findByText('This Week')).toBeTruthy();
    expect(screen.getAllByText('$150.00').length).toBeGreaterThan(0);
    expect(screen.getByText('Fiscal Snapshot')).toBeTruthy();
    expect(screen.getByText('Budgeted Income')).toBeTruthy();
    expect(screen.getByText('$1,000.00')).toBeTruthy();
    expect(screen.getByText('Membership')).toBeTruthy();
    expect(screen.getByText('Total Members')).toBeTruthy();
    expect(screen.getByText('Recent Finance Activity')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Members' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Offerings' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Finance' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Budgets' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Reports' })).toBeTruthy();
  });

  it('hides membership summary from treasurer', async () => {
    authState.currentUser = {
      primaryEmail: 'treasurer@example.com',
      displayName: 'Treasurer',
      roles: ['TREASURER'],
      mustChangePassword: false,
      token: 'token',
    };

    await renderDashboard();

    expect(await screen.findByText('Recent Finance Activity')).toBeTruthy();
    expect(screen.queryByText('Membership')).toBeNull();
    expect(screen.queryByRole('link', { name: 'Members' })).toBeNull();
    expect(screen.getByRole('link', { name: 'Offerings' })).toBeTruthy();
  });

  it('shows membership user member summary without finance summary', async () => {
    authState.currentUser = {
      primaryEmail: 'membership@example.com',
      displayName: 'Membership',
      roles: ['MEMBERSHIP'],
      mustChangePassword: false,
      token: 'token',
    };

    await renderDashboard();

    expect(await screen.findByText('Membership')).toBeTruthy();
    expect(screen.queryByText('Recent Finance Activity')).toBeNull();
    expect(screen.queryByRole('link', { name: 'Finance' })).toBeNull();
    expect(screen.getByRole('link', { name: 'Members' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Reference Data' })).toBeTruthy();
  });

  it('shows viewer read-only report content only', async () => {
    authState.currentUser = {
      primaryEmail: 'viewer@example.com',
      displayName: 'Viewer',
      roles: ['VIEWER'],
      mustChangePassword: false,
      token: 'token',
    };

    await renderDashboard();

    expect(await screen.findByText('This Week')).toBeTruthy();
    expect(screen.getByText('Fiscal Snapshot')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Reports' })).toBeTruthy();
    expect(screen.queryByText('Membership')).toBeNull();
    expect(screen.queryByText('Recent Finance Activity')).toBeNull();
    expect(screen.queryByRole('link', { name: 'Offerings' })).toBeNull();
  });

  it('shows a section error without hiding other sections', async () => {
    authState.currentUser = {
      primaryEmail: 'admin@example.com',
      displayName: 'Admin',
      roles: ['ADMIN'],
      mustChangePassword: false,
      token: 'token',
    };
    weeklyReportMock.mockRejectedValue(new Error('Weekly report unavailable.'));

    await renderDashboard();

    expect(await screen.findByText('Weekly report unavailable.')).toBeTruthy();
    await waitFor(() => expect(screen.getByText('Membership')).toBeTruthy());
    expect(screen.getByText('Recent Finance Activity')).toBeTruthy();
  });
});
```

- [ ] **Step 2: Run the dashboard test to verify it fails**

Run:

```bash
cd frontend
npm test -- --run src/views/DashboardView.test.ts
```

Expected: FAIL because the current dashboard does not call the mocked APIs or render the expected cards.

- [ ] **Step 3: Commit the failing tests**

Run:

```bash
git add frontend/src/views/DashboardView.test.ts
git commit -m "Add dashboard behavior tests"
```

---

### Task 2: Build the Role-Aware Dashboard View

**Files:**
- Modify: `frontend/src/views/DashboardView.vue`
- Test: `frontend/src/views/DashboardView.test.ts`

**Interfaces:**
- Consumes:
  - Test expectations from Task 1.
  - Existing API functions from `frontend/src/api/reports.ts`, `frontend/src/api/members.ts`, and `frontend/src/api/finance.ts`.
- Produces:
  - Dashboard sections:
    - `Offering Overview`
    - `Fiscal Snapshot`
    - `Membership`
    - `Recent Finance Activity`
    - `Quick Links`
  - Helper functions local to `DashboardView.vue`:
    - `hasAnyRole(allowedRoles: Role[]): boolean`
    - `formatMoney(value: number): string`
    - `isoDate(date: Date): string`
    - `startOfMonth(date: Date): string`
    - `startOfYear(date: Date): string`
    - `currentSunday(date: Date): string`

- [ ] **Step 1: Replace the placeholder dashboard with the implementation**

Replace all content in `frontend/src/views/DashboardView.vue` with:

```vue
<template>
  <AppLayout>
    <section class="workspace dashboard-page">
      <header class="page-header">
        <div>
          <h2>Dashboard</h2>
          <p>Review today&apos;s church operations at a glance.</p>
        </div>
      </header>

      <section v-if="canViewReports" class="panel dashboard-panel">
        <div class="panel-title-row">
          <div>
            <h3>Offering Overview</h3>
            <p>Current giving totals from active offering records.</p>
          </div>
        </div>
        <p v-if="offeringError" class="error">{{ offeringError }}</p>
        <div class="dashboard-grid">
          <article class="metric-card">
            <span>This Week</span>
            <strong>{{ formatMoney(offeringTotals.week) }}</strong>
            <small>Current Sunday week</small>
          </article>
          <article class="metric-card">
            <span>Month to Date</span>
            <strong>{{ formatMoney(offeringTotals.month) }}</strong>
            <small>Since {{ monthStartLabel }}</small>
          </article>
          <article class="metric-card">
            <span>Year to Date</span>
            <strong>{{ formatMoney(offeringTotals.year) }}</strong>
            <small>Since {{ yearStartLabel }}</small>
          </article>
        </div>
      </section>

      <section v-if="canViewReports" class="panel dashboard-panel">
        <div class="panel-title-row">
          <div>
            <h3>Fiscal Snapshot</h3>
            <p>Budget and actuals for {{ currentYear }}.</p>
          </div>
        </div>
        <p v-if="fiscalError" class="error">{{ fiscalError }}</p>
        <div class="dashboard-grid">
          <article class="metric-card">
            <span>Budgeted Income</span>
            <strong>{{ formatMoney(fiscalTotals.budgetedIncome) }}</strong>
          </article>
          <article class="metric-card">
            <span>Actual Income</span>
            <strong>{{ formatMoney(fiscalTotals.actualIncome) }}</strong>
          </article>
          <article class="metric-card">
            <span>Budgeted Expense</span>
            <strong>{{ formatMoney(fiscalTotals.budgetedExpense) }}</strong>
          </article>
          <article class="metric-card">
            <span>Actual Expense</span>
            <strong>{{ formatMoney(fiscalTotals.actualExpense) }}</strong>
          </article>
          <article class="metric-card">
            <span>Net Actual</span>
            <strong>{{ formatMoney(fiscalTotals.netActual) }}</strong>
          </article>
        </div>
      </section>

      <div class="dashboard-two-column">
        <section v-if="canViewMembership" class="panel dashboard-panel">
          <h3>Membership</h3>
          <p v-if="membershipError" class="error">{{ membershipError }}</p>
          <div class="dashboard-grid compact">
            <article class="metric-card">
              <span>Total Members</span>
              <strong>{{ memberTotals.total }}</strong>
            </article>
            <article class="metric-card">
              <span>Active Members</span>
              <strong>{{ memberTotals.active }}</strong>
            </article>
            <article class="metric-card">
              <span>Locked Accounts</span>
              <strong>{{ memberTotals.locked }}</strong>
            </article>
          </div>
        </section>

        <section v-if="canViewFinance" class="panel dashboard-panel">
          <h3>Recent Finance Activity</h3>
          <p v-if="financeError" class="error">{{ financeError }}</p>
          <div class="dashboard-grid compact">
            <article class="metric-card">
              <span>Recent Income</span>
              <strong>{{ formatMoney(financeTotals.income) }}</strong>
            </article>
            <article class="metric-card">
              <span>Recent Expense</span>
              <strong>{{ formatMoney(financeTotals.expense) }}</strong>
            </article>
            <article class="metric-card">
              <span>Transactions</span>
              <strong>{{ financeTotals.count }}</strong>
            </article>
          </div>
        </section>
      </div>

      <section class="panel dashboard-panel">
        <h3>Quick Links</h3>
        <nav class="quick-links" aria-label="Dashboard quick links">
          <RouterLink v-if="canViewMembership" to="/members">Members</RouterLink>
          <RouterLink v-if="canViewFinance" to="/offerings">Offerings</RouterLink>
          <RouterLink v-if="canViewFinance" to="/finance">Finance</RouterLink>
          <RouterLink v-if="canViewFinance" to="/budgets">Budgets</RouterLink>
          <RouterLink v-if="canViewReferenceData" to="/reference-data">Reference Data</RouterLink>
          <RouterLink v-if="canViewReports" to="/reports">Reports</RouterLink>
        </nav>
      </section>
    </section>
  </AppLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive } from 'vue';
import { RouterLink } from 'vue-router';
import type { FinancialTransaction } from '../api/finance';
import { listFinanceTransactions } from '../api/finance';
import { listMembers } from '../api/members';
import { listFinancialBudgetReport, listWeeklyOfferingReport } from '../api/reports';
import { authState, type Role } from '../auth/authStore';
import AppLayout from '../layouts/AppLayout.vue';

const currentDate = new Date();
const currentYear = currentDate.getFullYear();
const today = isoDate(currentDate);
const currentWeekSunday = currentSunday(currentDate);
const monthStart = startOfMonth(currentDate);
const yearStart = startOfYear(currentDate);

const offeringTotals = reactive({
  week: 0,
  month: 0,
  year: 0,
});

const fiscalTotals = reactive({
  budgetedIncome: 0,
  actualIncome: 0,
  budgetedExpense: 0,
  actualExpense: 0,
  netActual: 0,
});

const memberTotals = reactive({
  total: 0,
  active: 0,
  locked: 0,
});

const financeTotals = reactive({
  income: 0,
  expense: 0,
  count: 0,
});

const offeringError = computedError();
const fiscalError = computedError();
const membershipError = computedError();
const financeError = computedError();

const canViewReports = computed(() => hasAnyRole(['ADMIN', 'TREASURER', 'PASTOR', 'VIEWER']));
const canViewFinance = computed(() => hasAnyRole(['ADMIN', 'TREASURER']));
const canViewMembership = computed(() => hasAnyRole(['ADMIN', 'MEMBERSHIP']));
const canViewReferenceData = computed(() => hasAnyRole(['ADMIN', 'TREASURER', 'MEMBERSHIP']));
const monthStartLabel = computed(() => monthStart);
const yearStartLabel = computed(() => yearStart);

onMounted(() => {
  if (canViewReports.value) {
    void loadOfferingOverview();
    void loadFiscalSnapshot();
  }
  if (canViewMembership.value) {
    void loadMembershipSummary();
  }
  if (canViewFinance.value) {
    void loadFinanceSummary();
  }
});

function computedError() {
  return reactive({ value: '' });
}

function setError(errorState: { value: string }, error: unknown) {
  errorState.value = error instanceof Error ? error.message : 'Request failed.';
}

async function loadOfferingOverview() {
  offeringError.value = '';
  try {
    const [weekRows, monthRows, yearRows] = await Promise.all([
      listWeeklyOfferingReport({ start: currentWeekSunday, end: today }),
      listWeeklyOfferingReport({ start: monthStart, end: today }),
      listWeeklyOfferingReport({ start: yearStart, end: today }),
    ]);
    offeringTotals.week = weekRows.reduce((total, row) => total + row.totalAmount, 0);
    offeringTotals.month = monthRows.reduce((total, row) => total + row.totalAmount, 0);
    offeringTotals.year = yearRows.reduce((total, row) => total + row.totalAmount, 0);
  } catch (error) {
    setError(offeringError, error);
  }
}

async function loadFiscalSnapshot() {
  fiscalError.value = '';
  try {
    const rows = await listFinancialBudgetReport({ fiscalYear: currentYear });
    fiscalTotals.budgetedIncome = rows
      .filter((row) => row.budgetType === 'OFFERING_INCOME')
      .reduce((total, row) => total + row.budget, 0);
    fiscalTotals.actualIncome = rows
      .filter((row) => row.budgetType === 'OFFERING_INCOME')
      .reduce((total, row) => total + row.actual, 0);
    fiscalTotals.budgetedExpense = rows
      .filter((row) => row.budgetType === 'EXPENSE')
      .reduce((total, row) => total + row.budget, 0);
    fiscalTotals.actualExpense = rows
      .filter((row) => row.budgetType === 'EXPENSE')
      .reduce((total, row) => total + row.actual, 0);
    fiscalTotals.netActual = fiscalTotals.actualIncome - fiscalTotals.actualExpense;
  } catch (error) {
    setError(fiscalError, error);
  }
}

async function loadMembershipSummary() {
  membershipError.value = '';
  try {
    const members = await listMembers('');
    memberTotals.total = members.length;
    memberTotals.active = members.filter((member) => member.active).length;
    memberTotals.locked = members.filter((member) => member.locked).length;
  } catch (error) {
    setError(membershipError, error);
  }
}

async function loadFinanceSummary() {
  financeError.value = '';
  try {
    const transactions = await listFinanceTransactions();
    const recentTransactions = transactions.filter(isCurrentMonthTransaction);
    financeTotals.income = recentTransactions
      .filter((transaction) => transaction.type === 'INCOME')
      .reduce((total, transaction) => total + transaction.amount, 0);
    financeTotals.expense = recentTransactions
      .filter((transaction) => transaction.type === 'EXPENSE')
      .reduce((total, transaction) => total + transaction.amount, 0);
    financeTotals.count = recentTransactions.length;
  } catch (error) {
    setError(financeError, error);
  }
}

function isCurrentMonthTransaction(transaction: FinancialTransaction) {
  return transaction.transactionDate >= monthStart && transaction.transactionDate <= today;
}

function hasAnyRole(allowedRoles: Role[]) {
  const roles = authState.currentUser?.roles ?? [];
  return roles.some((role) => allowedRoles.includes(role));
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('en-CA', {
    style: 'currency',
    currency: 'CAD',
  }).format(value);
}

function isoDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function startOfMonth(date: Date) {
  return isoDate(new Date(date.getFullYear(), date.getMonth(), 1));
}

function startOfYear(date: Date) {
  return isoDate(new Date(date.getFullYear(), 0, 1));
}

function currentSunday(date: Date) {
  const sunday = new Date(date);
  sunday.setDate(date.getDate() - date.getDay());
  return isoDate(sunday);
}
</script>

<style scoped>
.dashboard-page {
  gap: 18px;
}

.dashboard-panel {
  display: grid;
  gap: 14px;
}

.panel-title-row {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 16px;
}

.panel-title-row p,
.dashboard-panel > p {
  margin: 6px 0 0;
  color: #5b6778;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.dashboard-grid.compact {
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
}

.dashboard-two-column {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 18px;
}

.metric-card {
  min-height: 96px;
  display: grid;
  align-content: center;
  gap: 6px;
  border: 1px solid #edf0f4;
  border-radius: 8px;
  padding: 14px;
  background: #fbfcfe;
}

.metric-card span {
  color: #5b6778;
  font-size: 0.92rem;
}

.metric-card strong {
  color: #1f2933;
  font-size: 1.45rem;
  line-height: 1.2;
}

.metric-card small {
  color: #667085;
}

.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.quick-links a {
  min-height: 38px;
  display: inline-flex;
  align-items: center;
  border-radius: 6px;
  padding: 0 14px;
  background: #22577a;
  color: white;
  text-decoration: none;
}

@media (max-width: 760px) {
  .dashboard-two-column {
    grid-template-columns: 1fr;
  }
}
</style>
```

- [ ] **Step 2: Fix Vue reactivity if the compiler rejects `computedError`**

If TypeScript or Vue reports that the section error objects are not ideal for template use, replace this block:

```ts
import { computed, onMounted, reactive } from 'vue';
```

with:

```ts
import { computed, onMounted, reactive, ref } from 'vue';
```

Then replace:

```ts
const offeringError = computedError();
const fiscalError = computedError();
const membershipError = computedError();
const financeError = computedError();
```

with:

```ts
const offeringError = ref('');
const fiscalError = ref('');
const membershipError = ref('');
const financeError = ref('');
```

Delete:

```ts
function computedError() {
  return reactive({ value: '' });
}
```

Keep `setError(errorState: { value: string }, error: unknown)` unchanged.

- [ ] **Step 3: Run the dashboard test**

Run:

```bash
cd frontend
npm test -- --run src/views/DashboardView.test.ts
```

Expected: PASS.

- [ ] **Step 4: Commit the dashboard implementation**

Run:

```bash
git add frontend/src/views/DashboardView.vue
git commit -m "Build role-aware dashboard"
```

---

### Task 3: Run Full Frontend Verification

**Files:**
- Modify: none
- Test: all frontend tests and production build

**Interfaces:**
- Consumes:
  - `DashboardView.vue` from Task 2.
  - `DashboardView.test.ts` from Task 1.
- Produces:
  - Verified frontend test/build result.

- [ ] **Step 1: Run all frontend tests**

Run:

```bash
cd frontend
npm test
```

Expected: PASS for all frontend test files, including `DashboardView.test.ts`, `ReportsView.test.ts`, `BudgetsView.test.ts`, and `authStore.test.ts`.

- [ ] **Step 2: Run the frontend production build**

Run:

```bash
cd frontend
npm run build
```

Expected: PASS with Vite build output and no TypeScript errors.

- [ ] **Step 3: Commit any final verification fixes**

If verification required small fixes, commit them:

```bash
git add frontend/src/views/DashboardView.vue frontend/src/views/DashboardView.test.ts
git commit -m "Fix dashboard verification issues"
```

If no files changed, skip this commit.

---

## Self-Review

Spec coverage:

- Role-aware dashboard cards: Task 1 tests and Task 2 implementation.
- Offering, fiscal, membership, finance, and quick-link sections: Task 1 tests and Task 2 implementation.
- Existing API reuse without backend changes: Task 2 implementation.
- Section-level error handling: Task 1 failure test and Task 2 `setError` handling.
- Frontend verification: Task 3.

Placeholder scan:

- No `TBD`, `TODO`, or unspecified implementation placeholders remain.

Type consistency:

- `BudgetType` values match `frontend/src/api/reports.ts`.
- `FinancialTransaction` values match `frontend/src/api/finance.ts`.
- `Role` values match `frontend/src/auth/authStore.ts`.

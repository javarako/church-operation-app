<template>
  <AppLayout>
    <section class="workspace dashboard-page">
      <header class="page-header">
        <div>
          <h2>Dashboard</h2>
          <p>Review today's church operations at a glance.</p>
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
import { computed, onMounted, reactive, ref } from 'vue';
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

const offeringError = ref('');
const fiscalError = ref('');
const membershipError = ref('');
const financeError = ref('');

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

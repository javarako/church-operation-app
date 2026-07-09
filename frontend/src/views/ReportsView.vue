<template>
  <section class="workspace">
    <header class="page-header">
      <div>
        <h2>Reports</h2>
        <p>Review offering summaries, official giving extracts, and budget performance.</p>
      </div>
    </header>

    <section class="panel">
      <div class="toolbar reports-toolbar" role="tablist" aria-label="Reports">
        <button
          v-for="report in visibleReportTabs"
          :key="report.id"
          type="button"
          :class="{ secondary: report.id !== activeVisibleReportId }"
          @click="selectTab(report.id)"
        >
          {{ report.label }}
        </button>
      </div>

      <div class="reports-header">
        <div>
          <h3>{{ activeReport.title }}</h3>
          <p>{{ activeReport.description }}</p>
        </div>
        <button type="button" class="secondary" @click="exportActiveReport">
          Export CSV
        </button>
      </div>

      <form class="report-filters" @submit.prevent="runActiveReport">
        <template v-if="activeVisibleReportId === 'weekly-offerings'">
          <label>
            Start date
            <input v-model="weeklyFilters.start" type="date" />
          </label>

          <label>
            End date
            <input v-model="weeklyFilters.end" type="date" />
          </label>

          <label>
            Fund/category
            <input v-model="weeklyFilters.fundCategory" placeholder="All funds" />
          </label>

          <label>
            Payment method
            <input v-model="weeklyFilters.paymentMethod" placeholder="All methods" />
          </label>
        </template>

        <template v-else-if="activeVisibleReportId === 'member-offerings'">
          <label>
            Start date
            <input v-model="memberFilters.start" type="date" />
          </label>

          <label>
            End date
            <input v-model="memberFilters.end" type="date" />
          </label>

          <label>
            Member ID
            <input v-model="memberFilters.memberId" placeholder="All members" />
          </label>

          <label>
            Fund/category
            <input v-model="memberFilters.fundCategory" placeholder="All funds" />
          </label>
        </template>

        <template v-else-if="activeVisibleReportId === 'tax-return'">
          <label>
            Tax year
            <input v-model.number="taxFilters.taxYear" type="number" min="2000" step="1" />
          </label>

          <label>
            Member ID
            <input v-model="taxFilters.memberId" placeholder="All members" />
          </label>
        </template>

        <template v-else>
          <label>
            Fiscal year
            <input v-model.number="financialFilters.fiscalYear" type="number" min="2000" step="1" />
          </label>
        </template>

        <div class="actions">
          <button type="submit">Run report</button>
        </div>
      </form>

      <p v-if="error" class="error">{{ error }}</p>

      <div class="summary-strip">
        <strong>{{ loading ? 'Loading...' : activeSummary.label }}</strong>
        <span>{{ activeSummary.rows }} row{{ activeSummary.rows === 1 ? '' : 's' }}</span>
        <span v-if="activeSummary.total">{{ activeSummary.total }}</span>
      </div>

      <div class="table-wrap">
        <table v-if="activeVisibleReportId === 'weekly-offerings'">
          <thead>
            <tr>
              <th>Offering Sunday</th>
              <th>Fund/category</th>
              <th>Giving type</th>
              <th>Payment method</th>
              <th>Count</th>
              <th>Total</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in weeklyRows"
              :key="`${row.offeringSunday}-${row.fundCategory}-${row.givingType}-${row.paymentMethod}`"
            >
              <td>{{ row.offeringSunday }}</td>
              <td>{{ row.fundCategory }}</td>
              <td>{{ row.givingType }}</td>
              <td>{{ row.paymentMethod }}</td>
              <td>{{ row.count }}</td>
              <td>{{ formatMoney(row.totalAmount) }}</td>
            </tr>
            <tr v-if="!weeklyRows.length && !loading">
              <td colspan="6" class="empty-state">No weekly report rows found.</td>
            </tr>
          </tbody>
        </table>

        <table v-else-if="activeVisibleReportId === 'member-offerings'">
          <thead>
            <tr>
              <th>Member</th>
              <th>Email</th>
              <th>Offering number</th>
              <th>Fund/category</th>
              <th>Count</th>
              <th>Total</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in memberRows" :key="`${row.memberId}-${row.fundCategory}`">
              <td>{{ row.memberName }}</td>
              <td>{{ row.primaryEmail }}</td>
              <td>{{ row.offeringNumber || '-' }}</td>
              <td>{{ row.fundCategory }}</td>
              <td>{{ row.count }}</td>
              <td>{{ formatMoney(row.totalAmount) }}</td>
            </tr>
            <tr v-if="!memberRows.length && !loading">
              <td colspan="6" class="empty-state">No member report rows found.</td>
            </tr>
          </tbody>
        </table>

        <table v-else-if="activeVisibleReportId === 'tax-return'">
          <thead>
            <tr>
              <th>Giving date</th>
              <th>Member</th>
              <th>Email</th>
              <th>Offering number</th>
              <th>Address</th>
              <th>Fund/category</th>
              <th>Amount</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in taxRows" :key="`${row.memberId}-${row.givingDate}-${row.fundCategory}-${row.amount}`">
              <td>{{ row.givingDate }}</td>
              <td>{{ row.memberName }}</td>
              <td>{{ row.primaryEmail }}</td>
              <td>{{ row.offeringNumber || '-' }}</td>
              <td>{{ row.memberAddress || '-' }}</td>
              <td>{{ row.fundCategory }}</td>
              <td>{{ formatMoney(row.amount) }}</td>
            </tr>
            <tr v-if="!taxRows.length && !loading">
              <td colspan="7" class="empty-state">No tax report rows found.</td>
            </tr>
          </tbody>
        </table>

        <table v-else>
          <thead>
            <tr>
              <th>Type</th>
              <th>Category</th>
              <th>Sub-category</th>
              <th>Budget</th>
              <th>Actual</th>
              <th>Variance</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in financialRows"
              :key="`${row.fiscalYear}-${row.budgetType}-${row.category || ''}-${row.subCategory || ''}`"
            >
              <td>{{ row.budgetType }}</td>
              <td>{{ row.category || '-' }}</td>
              <td>{{ row.subCategory || '-' }}</td>
              <td>{{ formatMoney(row.budget) }}</td>
              <td>{{ formatMoney(row.actual) }}</td>
              <td>{{ formatMoney(row.variance) }}</td>
            </tr>
            <tr v-if="!financialRows.length && !loading">
              <td colspan="6" class="empty-state">No budget report rows found.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import {
  listFinancialBudgetReport,
  listMemberOfferingSummaryReport,
  listOfficialTaxReport,
  listWeeklyOfferingReport,
  type FinancialBudgetReportRow,
  type MemberOfferingSummaryReportRow,
  type OfficialTaxReportRow,
  type WeeklyOfferingReportRow,
} from '../api/reports';
import { authState, type Role } from '../auth/authStore';

interface ReportTab {
  id: 'weekly-offerings' | 'member-offerings' | 'tax-return' | 'financial-budget';
  label: string;
  title: string;
  description: string;
}

const reportTabs: ReportTab[] = [
  {
    id: 'weekly-offerings',
    label: 'Weekly offerings',
    title: 'Weekly Offering Status',
    description: 'Track offering totals by Sunday, fund, giver type, and payment method.',
  },
  {
    id: 'member-offerings',
    label: 'Member offerings',
    title: 'Offering Summary',
    description: 'Review member giving totals by fund category across a selected date range.',
  },
  {
    id: 'tax-return',
    label: 'Official tax',
    title: 'Official Tax Return',
    description: 'Prepare the annual giving extract used for official member tax receipts.',
  },
  {
    id: 'financial-budget',
    label: 'Budget performance',
    title: 'Financial Actual vs Budget',
    description: 'Compare budget, actuals, and variance for the selected fiscal year.',
  },
];

const officialTaxRoles: Role[] = ['ADMIN', 'TREASURER'];
const now = new Date();
const activeReportId = ref<ReportTab['id']>('weekly-offerings');
const weeklyRows = ref<WeeklyOfferingReportRow[]>([]);
const memberRows = ref<MemberOfferingSummaryReportRow[]>([]);
const taxRows = ref<OfficialTaxReportRow[]>([]);
const financialRows = ref<FinancialBudgetReportRow[]>([]);
const loading = ref(false);
const error = ref('');

const weeklyFilters = reactive({
  start: startOfYear(now),
  end: isoDate(now),
  fundCategory: '',
  paymentMethod: '',
});

const memberFilters = reactive({
  start: startOfYear(now),
  end: isoDate(now),
  memberId: '',
  fundCategory: '',
});

const taxFilters = reactive({
  taxYear: now.getFullYear(),
  memberId: '',
});

const financialFilters = reactive({
  fiscalYear: now.getFullYear(),
});

const visibleReportTabs = computed(() =>
  reportTabs.filter((report) => report.id !== 'tax-return' || hasOfficialTaxAccess()),
);

const activeVisibleReportId = computed<ReportTab['id']>(() => {
  return visibleReportTabs.value.some((report) => report.id === activeReportId.value)
    ? activeReportId.value
    : visibleReportTabs.value[0]?.id ?? 'weekly-offerings';
});

const activeReport = computed(
  () => visibleReportTabs.value.find((report) => report.id === activeVisibleReportId.value) ?? visibleReportTabs.value[0],
);

const activeSummary = computed(() => {
  if (activeVisibleReportId.value === 'weekly-offerings') {
    return {
      label: 'Weekly total',
      rows: weeklyRows.value.length,
      total: formatSummaryMoney(sum(weeklyRows.value.map((row) => row.totalAmount))),
    };
  }

  if (activeVisibleReportId.value === 'member-offerings') {
    return {
      label: 'Member total',
      rows: memberRows.value.length,
      total: formatSummaryMoney(sum(memberRows.value.map((row) => row.totalAmount))),
    };
  }

  if (activeVisibleReportId.value === 'tax-return') {
    return {
      label: 'Receipt total',
      rows: taxRows.value.length,
      total: formatSummaryMoney(sum(taxRows.value.map((row) => row.amount))),
    };
  }

  return {
    label: 'Variance total',
    rows: financialRows.value.length,
    total: formatSummaryMoney(sum(financialRows.value.map((row) => row.variance))),
  };
});

onMounted(() => {
  void runActiveReport();
});

function hasOfficialTaxAccess() {
  const roles = authState.currentUser?.roles ?? [];
  return roles.some((role) => officialTaxRoles.includes(role));
}

function selectTab(tabId: ReportTab['id']) {
  activeReportId.value = tabId;
  void runActiveReport();
}

async function runActiveReport() {
  error.value = '';
  loading.value = true;

  try {
    if (activeVisibleReportId.value === 'weekly-offerings') {
      weeklyRows.value = await listWeeklyOfferingReport({ ...weeklyFilters });
      return;
    }

    if (activeVisibleReportId.value === 'member-offerings') {
      memberRows.value = await listMemberOfferingSummaryReport({ ...memberFilters });
      return;
    }

    if (activeVisibleReportId.value === 'tax-return') {
      taxRows.value = await listOfficialTaxReport({ ...taxFilters });
      return;
    }

    financialRows.value = await listFinancialBudgetReport({ ...financialFilters });
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load report.';
  } finally {
    loading.value = false;
  }
}

function exportActiveReport() {
  if (activeVisibleReportId.value === 'weekly-offerings') {
    exportCsv(
      'weekly-offering-report.csv',
      ['Offering Sunday', 'Fund/category', 'Giving type', 'Payment method', 'Count', 'Total'],
      weeklyRows.value.map((row) => [
        row.offeringSunday,
        row.fundCategory,
        row.givingType,
        row.paymentMethod,
        row.count,
        row.totalAmount,
      ]),
    );
    return;
  }

  if (activeVisibleReportId.value === 'member-offerings') {
    exportCsv(
      'member-offering-summary.csv',
      ['Member', 'Email', 'Offering number', 'Fund/category', 'Count', 'Total'],
      memberRows.value.map((row) => [
        row.memberName,
        row.primaryEmail,
        row.offeringNumber,
        row.fundCategory,
        row.count,
        row.totalAmount,
      ]),
    );
    return;
  }

  if (activeVisibleReportId.value === 'tax-return') {
    const confirmed = window.confirm('This extraction is for official use. Continue?');
    if (!confirmed) {
      return;
    }

    exportCsv(
      'official-tax-return.csv',
      ['Giving date', 'Member', 'Email', 'Offering number', 'Address', 'Fund/category', 'Amount'],
      taxRows.value.map((row) => [
        row.givingDate,
        row.memberName,
        row.primaryEmail,
        row.offeringNumber,
        row.memberAddress,
        row.fundCategory,
        row.amount,
      ]),
    );
    return;
  }

  exportCsv(
    'financial-budget-report.csv',
    ['Type', 'Category', 'Sub-category', 'Budget', 'Actual', 'Variance'],
    financialRows.value.map((row) => [
      row.budgetType,
      row.category,
      row.subCategory,
      row.budget,
      row.actual,
      row.variance,
    ]),
  );
}

function exportCsv(filename: string, headers: string[], rows: Array<Array<string | number | undefined>>) {
  const escape = (value: string | number | undefined) => `"${String(value ?? '').replaceAll('"', '""')}"`;
  const csv = [headers, ...rows].map((row) => row.map(escape).join(',')).join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  const url = URL.createObjectURL(blob);
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('en-CA', {
    style: 'currency',
    currency: 'CAD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value ?? 0);
}

function formatSummaryMoney(value: number) {
  return value === 0 ? '' : formatMoney(value);
}

function sum(values: number[]) {
  return values.reduce((total, value) => total + Number(value || 0), 0);
}

function isoDate(value: Date) {
  return value.toISOString().slice(0, 10);
}

function startOfYear(value: Date) {
  return `${value.getFullYear()}-01-01`;
}
</script>

<style scoped>
.reports-toolbar {
  grid-template-columns: repeat(auto-fit, minmax(130px, max-content));
  justify-content: start;
}

.reports-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.reports-header p {
  margin: 6px 0 0;
  color: #5b6778;
}

.report-filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.report-filters label {
  display: grid;
  gap: 6px;
  color: #344054;
}

.report-filters input {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #c8d0d9;
  border-radius: 6px;
  padding: 9px 10px;
}

.report-filters .actions {
  align-self: end;
}

.empty-state {
  color: #5b6778;
}
</style>

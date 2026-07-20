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
          role="tab"
          :aria-selected="report.id === activeVisibleReportId"
          :class="{
            'active-report-tab': report.id === activeVisibleReportId,
            'inactive-report-tab': report.id !== activeVisibleReportId,
          }"
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
        <button
          v-if="activeVisibleReportId === 'tax-return'"
          type="button"
          :disabled="receiptBusy"
          @click="downloadAllReceipts"
        >
          Download all receipts
        </button>
        <button
          v-else-if="!isWorkbookDownloadTab"
          type="button"
          class="secondary"
          @click="exportActiveReport"
        >
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
            Fund
            <select v-model="weeklyFilters.fundCode" @change="weeklyFilters.categoryCode = ''">
              <option value="">All funds</option>
              <option v-for="option in offeringFundOptions" :key="option.id" :value="option.code">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label>
            Category
            <select v-model="weeklyFilters.categoryCode">
              <option value="">All categories</option>
              <option v-for="option in weeklyCategoryOptions" :key="option.id" :value="option.code">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label>
            Payment method
            <select v-model="weeklyFilters.paymentMethod">
              <option value="">All methods</option>
              <option v-for="option in paymentMethodOptions" :key="option.id" :value="option.code">
                {{ option.label }}
              </option>
            </select>
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
            Offering number
            <input v-model="memberFilters.offeringNumber" placeholder="All offering numbers" />
          </label>

          <label>
            Fund
            <select v-model="memberFilters.fundCode" @change="memberFilters.categoryCode = ''">
              <option value="">All funds</option>
              <option v-for="option in offeringFundOptions" :key="option.id" :value="option.code">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label>
            Category
            <select v-model="memberFilters.categoryCode">
              <option value="">All categories</option>
              <option v-for="option in memberCategoryOptions" :key="option.id" :value="option.code">
                {{ option.label }}
              </option>
            </select>
          </label>
        </template>

        <template v-else-if="activeVisibleReportId === 'tax-return'">
          <label>
            Tax year
            <input v-model.number="taxFilters.taxYear" type="number" min="2000" step="1" />
          </label>

          <label>
            Offering number
            <input v-model="taxFilters.offeringNumber" placeholder="All offering numbers" />
          </label>

          <label class="tax-note-field">
            Thank-you note
            <textarea v-model="thankYouNote" maxlength="500" rows="3"></textarea>
          </label>
        </template>

        <template v-else-if="activeVisibleReportId === 'quarterly-financial'">
          <label>
            Calendar year
            <input v-model.number="quarterlyFilters.year" type="number" min="2000" step="1" />
          </label>

          <label>
            Quarter
            <select v-model.number="quarterlyFilters.quarter">
              <option :value="1">Q1 (January-March)</option>
              <option :value="2">Q2 (April-June)</option>
              <option :value="3">Q3 (July-September)</option>
              <option :value="4">Q4 (October-December)</option>
            </select>
          </label>

          <div class="quarterly-downloads">
            <div class="quarterly-download-row">
              <strong>Quarterly Offering Excel</strong>
              <button
                type="button"
                aria-label="Download quarterly offering Excel"
                :disabled="quarterlyOfferingBusy"
                @click="downloadQuarterlyOfferingWorkbook"
              >
                {{ quarterlyOfferingBusy ? 'Preparing...' : 'Download Excel' }}
              </button>
            </div>
            <div class="quarterly-download-row">
              <strong>Quarterly Expenditure Excel</strong>
              <button
                type="button"
                aria-label="Download quarterly expenditure Excel"
                :disabled="quarterlyExpenditureBusy"
                @click="downloadQuarterlyExpenditureWorkbook"
              >
                {{ quarterlyExpenditureBusy ? 'Preparing...' : 'Download Excel' }}
              </button>
            </div>
          </div>
        </template>

        <template v-else-if="activeVisibleReportId === 'yearly-financial'">
          <label>
            Fiscal year
            <input v-model.number="yearlyFilters.fiscalYear" type="number" min="2000" step="1" />
          </label>

          <div class="quarterly-downloads">
            <div class="quarterly-download-row">
              <strong>Yearly Offering Excel</strong>
              <button
                type="button"
                aria-label="Download yearly offering Excel"
                :disabled="yearlyOfferingBusy"
                @click="downloadYearlyOfferingWorkbook"
              >
                {{ yearlyOfferingBusy ? 'Preparing...' : 'Download Excel' }}
              </button>
            </div>
            <div class="quarterly-download-row">
              <strong>Yearly Expenditure Excel</strong>
              <button
                type="button"
                aria-label="Download yearly expenditure Excel"
                :disabled="yearlyExpenditureBusy"
                @click="downloadYearlyExpenditureWorkbook"
              >
                {{ yearlyExpenditureBusy ? 'Preparing...' : 'Download Excel' }}
              </button>
            </div>
          </div>
        </template>

        <template v-else>
          <label>
            Fiscal year
            <input v-model.number="financialFilters.fiscalYear" type="number" min="2000" step="1" />
          </label>
        </template>

        <div v-if="!isWorkbookDownloadTab" class="actions">
          <button type="submit">Run report</button>
        </div>
      </form>

      <p v-if="error" class="error">{{ error }}</p>

      <div v-if="!isWorkbookDownloadTab" class="summary-strip">
        <strong>{{ loading ? 'Loading...' : activeSummary.label }}</strong>
        <span>{{ activeSummary.rows }} row{{ activeSummary.rows === 1 ? '' : 's' }}</span>
        <span v-if="activeSummary.total">{{ activeSummary.total }}</span>
      </div>

      <div v-if="!isWorkbookDownloadTab" class="table-wrap">
        <table v-if="activeVisibleReportId === 'weekly-offerings'">
          <thead>
            <tr>
              <th>Offering Sunday</th>
              <th>Fund</th>
              <th>Category</th>
              <th>Giving type</th>
              <th>Payment method</th>
              <th>Count</th>
              <th>Total</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in weeklyPagination.paginatedRows.value"
              :key="`${row.offeringSunday}-${row.fundCode}-${row.categoryCode}-${row.givingType}-${row.paymentMethod}`"
            >
              <td>{{ row.offeringSunday }}</td>
              <td>{{ referenceLabel(offeringFundOptions, row.fundCode) }}</td>
              <td>{{ referenceLabel(offeringCategoryOptions, row.categoryCode) }}</td>
              <td>{{ givingTypeLabel(row.givingType) }}</td>
              <td>{{ referenceLabel(paymentMethodOptions, row.paymentMethod) }}</td>
              <td>{{ row.count }}</td>
              <td>{{ formatMoney(row.totalAmount) }}</td>
            </tr>
            <tr v-if="!weeklyRows.length && !loading">
              <td colspan="7" class="empty-state">No weekly report rows found.</td>
            </tr>
          </tbody>
        </table>

        <table v-else-if="activeVisibleReportId === 'member-offerings'">
          <thead>
            <tr>
              <th>Offering number</th>
              <th>Member</th>
              <th>Email</th>
              <th>Fund</th>
              <th>Category</th>
              <th>Count</th>
              <th>Total</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in memberPagination.paginatedRows.value" :key="`${row.memberId}-${row.fundCode}-${row.categoryCode}`">
              <td>{{ row.offeringNumber || '-' }}</td>
              <td>{{ row.memberName }}</td>
              <td>{{ row.primaryEmail }}</td>
              <td>{{ referenceLabel(offeringFundOptions, row.fundCode) }}</td>
              <td>{{ referenceLabel(offeringCategoryOptions, row.categoryCode) }}</td>
              <td>{{ row.count }}</td>
              <td>{{ formatMoney(row.totalAmount) }}</td>
            </tr>
            <tr v-if="!memberRows.length && !loading">
              <td colspan="7" class="empty-state">No member report rows found.</td>
            </tr>
          </tbody>
        </table>

        <table v-else-if="activeVisibleReportId === 'tax-return'">
          <thead>
            <tr>
              <th>Offering #</th>
              <th>Donor</th>
              <th>Address</th>
              <th>Tax year</th>
              <th>Total offering</th>
              <th>Receipt #</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in taxPagination.paginatedRows.value"
              :key="`${row.memberId}-${row.taxYear}`"
            >
              <td>{{ row.offeringNumber }}</td>
              <td>{{ row.donorName }}</td>
              <td>{{ row.donorAddress || '-' }}</td>
              <td>{{ row.taxYear }}</td>
              <td>{{ formatMoney(row.totalAmount) }}</td>
              <td>
                {{ row.receiptNumber || '-' }}
                <span v-if="row.sourceChanged" class="receipt-warning">
                  Offerings changed after this receipt was issued.
                </span>
              </td>
              <td>{{ row.receiptStatus || 'Not issued' }}</td>
              <td class="receipt-actions">
                <button
                  v-if="!row.receiptId"
                  type="button"
                  :disabled="receiptBusy"
                  @click="issueReceipt(row)"
                >
                  Issue receipt
                </button>
                <template v-else-if="row.receiptStatus === 'ISSUED'">
                  <button type="button" class="secondary" :disabled="receiptBusy" @click="downloadReceipt(row)">
                    Download
                  </button>
                  <button type="button" class="secondary danger-text" :disabled="receiptBusy" @click="voidReceipt(row)">
                    Void receipt
                  </button>
                </template>
                <button
                  v-else
                  type="button"
                  :disabled="receiptBusy"
                  @click="replaceReceipt(row)"
                >
                  Replace receipt
                </button>
              </td>
            </tr>
            <tr v-if="!taxRows.length && !loading">
              <td colspan="8" class="empty-state">No eligible member offerings found for this tax year.</td>
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
              <th>Budget vs. Actual</th>
              <th>Variance</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in financialPagination.paginatedRows.value"
              :key="`${row.fiscalYear}-${row.budgetType}-${row.category || ''}-${row.subCategory || ''}`"
            >
              <td>{{ budgetTypeLabel(row.budgetType) }}</td>
              <td>{{ financialCategoryLabel(row) }}</td>
              <td>{{ financialSubCategoryLabel(row) }}</td>
              <td>{{ formatMoney(row.budget) }}</td>
              <td>{{ formatMoney(row.actual) }}</td>
              <td>{{ budgetActualPercentage(row.budget, row.actual) }}</td>
              <td>{{ formatMoney(row.variance) }}</td>
            </tr>
            <tr v-if="!financialRows.length && !loading">
              <td colspan="7" class="empty-state">No budget report rows found.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <PaginationControls
        v-if="activeVisibleReportId === 'weekly-offerings'"
        :current-page="weeklyPagination.currentPage.value"
        :page-count="weeklyPagination.pageCount.value"
        :page-size="weeklyPagination.pageSize.value"
        :total-rows="weeklyPagination.totalRows.value"
        :start-row="weeklyPagination.startRow.value"
        :end-row="weeklyPagination.endRow.value"
        @change-page="weeklyPagination.goToPage"
      />

      <PaginationControls
        v-else-if="activeVisibleReportId === 'member-offerings'"
        :current-page="memberPagination.currentPage.value"
        :page-count="memberPagination.pageCount.value"
        :page-size="memberPagination.pageSize.value"
        :total-rows="memberPagination.totalRows.value"
        :start-row="memberPagination.startRow.value"
        :end-row="memberPagination.endRow.value"
        @change-page="memberPagination.goToPage"
      />

      <PaginationControls
        v-else-if="activeVisibleReportId === 'tax-return'"
        :current-page="taxPagination.currentPage.value"
        :page-count="taxPagination.pageCount.value"
        :page-size="taxPagination.pageSize.value"
        :total-rows="taxPagination.totalRows.value"
        :start-row="taxPagination.startRow.value"
        :end-row="taxPagination.endRow.value"
        @change-page="taxPagination.goToPage"
      />

      <PaginationControls
        v-else-if="activeVisibleReportId === 'financial-budget'"
        :current-page="financialPagination.currentPage.value"
        :page-count="financialPagination.pageCount.value"
        :page-size="financialPagination.pageSize.value"
        :total-rows="financialPagination.totalRows.value"
        :start-row="financialPagination.startRow.value"
        :end-row="financialPagination.endRow.value"
        @change-page="financialPagination.goToPage"
      />
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import PaginationControls from '../components/PaginationControls.vue';
import {
  DEFAULT_THANK_YOU_NOTE,
  downloadQuarterlyExpenditureReport,
  downloadQuarterlyOfferingReport,
  downloadTaxReceiptPdf,
  downloadYearlyExpenditureReport,
  downloadYearlyOfferingReport,
  issueBatchTaxReceipts,
  issueTaxReceipt,
  listFinancialBudgetReport,
  listMemberOfferingSummaryReport,
  listTaxReceiptSummary,
  listWeeklyOfferingReport,
  replaceTaxReceipt,
  voidTaxReceipt,
  type FinancialBudgetReportRow,
  type MemberOfferingSummaryReportRow,
  type TaxReceiptSummaryRow,
  type WeeklyOfferingReportRow,
} from '../api/reports';
import { listReferenceData, type ReferenceDataOption } from '../api/referenceData';
import { authState, type Role } from '../auth/authStore';
import { usePagination } from '../composables/usePagination';

interface ReportTab {
  id: 'weekly-offerings' | 'member-offerings' | 'tax-return' | 'financial-budget' | 'quarterly-financial' | 'yearly-financial';
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
  {
    id: 'quarterly-financial',
    label: 'Quarterly Financial Report',
    title: 'Quarterly Financial Report',
    description: 'Download the calendar-quarter offering and expenditure workbooks.',
  },
  {
    id: 'yearly-financial',
    label: 'Yearly Financial Report',
    title: 'Yearly Financial Report',
    description: 'Download fiscal-year offering and expenditure workbooks with next-year budgets.',
  },
];

const officialTaxRoles: Role[] = ['ADMIN', 'TREASURER'];
const now = new Date();
const activeReportId = ref<ReportTab['id']>('weekly-offerings');
const weeklyRows = ref<WeeklyOfferingReportRow[]>([]);
const memberRows = ref<MemberOfferingSummaryReportRow[]>([]);
const taxRows = ref<TaxReceiptSummaryRow[]>([]);
const financialRows = ref<FinancialBudgetReportRow[]>([]);
const weeklyPagination = usePagination(weeklyRows);
const memberPagination = usePagination(memberRows);
const taxPagination = usePagination(taxRows);
const financialPagination = usePagination(financialRows);
const offeringFundOptions = ref<ReferenceDataOption[]>([]);
const offeringCategoryOptions = ref<ReferenceDataOption[]>([]);
const paymentMethodOptions = ref<ReferenceDataOption[]>([]);
const financialCategoryOptions = ref<ReferenceDataOption[]>([]);
const financialSubCategoryOptions = ref<ReferenceDataOption[]>([]);
const loading = ref(false);
const receiptBusy = ref(false);
const quarterlyOfferingBusy = ref(false);
const quarterlyExpenditureBusy = ref(false);
const yearlyOfferingBusy = ref(false);
const yearlyExpenditureBusy = ref(false);
const error = ref('');
const thankYouNote = ref(DEFAULT_THANK_YOU_NOTE);

const weeklyFilters = reactive({
  start: startOfYear(now),
  end: isoDate(now),
  fundCode: '',
  categoryCode: '',
  paymentMethod: '',
});

const memberFilters = reactive({
  start: startOfYear(now),
  end: isoDate(now),
  offeringNumber: '',
  fundCode: '',
  categoryCode: '',
});

const taxFilters = reactive({
  taxYear: now.getFullYear(),
  offeringNumber: '',
});

const financialFilters = reactive({
  fiscalYear: now.getFullYear(),
});

const quarterlyFilters = reactive({
  year: now.getFullYear(),
  quarter: (Math.floor(now.getMonth() / 3) + 1) as 1 | 2 | 3 | 4,
});

const yearlyFilters = reactive({
  fiscalYear: now.getFullYear(),
});

const visibleReportTabs = computed(() =>
  reportTabs.filter((report) => report.id !== 'tax-return' || hasOfficialTaxAccess()),
);

const weeklyCategoryOptions = computed(() => offeringCategoryOptions.value.filter(
  (option) => !weeklyFilters.fundCode || option.parentCode === weeklyFilters.fundCode,
));
const memberCategoryOptions = computed(() => offeringCategoryOptions.value.filter(
  (option) => !memberFilters.fundCode || option.parentCode === memberFilters.fundCode,
));

const activeVisibleReportId = computed<ReportTab['id']>(() => {
  return visibleReportTabs.value.some((report) => report.id === activeReportId.value)
    ? activeReportId.value
    : visibleReportTabs.value[0]?.id ?? 'weekly-offerings';
});

const isWorkbookDownloadTab = computed(() =>
  activeVisibleReportId.value === 'quarterly-financial'
    || activeVisibleReportId.value === 'yearly-financial',
);

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
      total: formatSummaryMoney(sum(taxRows.value.map((row) => row.totalAmount))),
    };
  }

  return {
    label: 'Variance total',
    rows: financialRows.value.length,
    total: formatSummaryMoney(sum(financialRows.value.map((row) => row.variance))),
  };
});

onMounted(() => {
  void loadReferenceOptions();
  void runActiveReport();
});

async function loadReferenceOptions() {
  try {
    const [funds, categories, paymentMethods, financialCategories, financialSubCategories] = await Promise.all([
      listReferenceData('OFFERING_FUND'),
      listReferenceData('OFFERING_CATEGORY'),
      listReferenceData('PAYMENT_METHOD'),
      listReferenceData('FINANCIAL_CATEGORY'),
      listReferenceData('FINANCIAL_SUB_CATEGORY'),
    ]);
    offeringFundOptions.value = activeSortedOptions(funds);
    offeringCategoryOptions.value = activeSortedOptions(categories);
    paymentMethodOptions.value = activeSortedOptions(paymentMethods);
    financialCategoryOptions.value = activeSortedOptions(financialCategories);
    financialSubCategoryOptions.value = activeSortedOptions(financialSubCategories);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load report filter options.';
  }
}

function hasOfficialTaxAccess() {
  const roles = authState.currentUser?.roles ?? [];
  return roles.some((role) => officialTaxRoles.includes(role));
}

function selectTab(tabId: ReportTab['id']) {
  activeReportId.value = tabId;
  if (tabId === 'quarterly-financial' || tabId === 'yearly-financial') {
    error.value = '';
    return;
  }
  void runActiveReport();
}

async function runActiveReport() {
  error.value = '';

  if (isWorkbookDownloadTab.value) {
    return;
  }

  const validationError = validateActiveFilters();
  if (validationError) {
    error.value = validationError;
    return;
  }

  loading.value = true;

  try {
    if (activeVisibleReportId.value === 'weekly-offerings') {
      weeklyRows.value = await listWeeklyOfferingReport({ ...weeklyFilters });
      weeklyPagination.resetPage();
      return;
    }

    if (activeVisibleReportId.value === 'member-offerings') {
      memberRows.value = await listMemberOfferingSummaryReport({ ...memberFilters });
      memberPagination.resetPage();
      return;
    }

    if (activeVisibleReportId.value === 'tax-return') {
      const rows = await listTaxReceiptSummary({ ...taxFilters });
      taxRows.value = rows.sort((left, right) => left.offeringNumber.localeCompare(
        right.offeringNumber,
        undefined,
        { numeric: true, sensitivity: 'base' },
      ));
      taxPagination.resetPage();
      return;
    }

    financialRows.value = await listFinancialBudgetReport({ ...financialFilters });
    financialPagination.resetPage();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load report.';
  } finally {
    loading.value = false;
  }
}

function quarterlyValidationError() {
  if (quarterlyFilters.year < 2000 || quarterlyFilters.quarter < 1 || quarterlyFilters.quarter > 4) {
    return 'A valid calendar year and quarter are required.';
  }
  return '';
}

async function downloadQuarterlyOfferingWorkbook() {
  error.value = quarterlyValidationError();
  if (error.value) {
    return;
  }

  quarterlyOfferingBusy.value = true;
  try {
    const blob = await downloadQuarterlyOfferingReport({
      year: quarterlyFilters.year,
      quarter: quarterlyFilters.quarter,
    });
    downloadBlob(
      blob,
      `quarterly-offerings-${quarterlyFilters.year}-q${quarterlyFilters.quarter}.xlsx`,
    );
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not generate the quarterly workbook.';
  } finally {
    quarterlyOfferingBusy.value = false;
  }
}

async function downloadQuarterlyExpenditureWorkbook() {
  error.value = quarterlyValidationError();
  if (error.value) {
    return;
  }

  quarterlyExpenditureBusy.value = true;
  try {
    const blob = await downloadQuarterlyExpenditureReport({
      year: quarterlyFilters.year,
      quarter: quarterlyFilters.quarter,
    });
    downloadBlob(
      blob,
      `quarterly-expenditures-${quarterlyFilters.year}-q${quarterlyFilters.quarter}.xlsx`,
    );
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not generate the quarterly workbook.';
  } finally {
    quarterlyExpenditureBusy.value = false;
  }
}

function yearlyValidationError() {
  if (yearlyFilters.fiscalYear < 2000) {
    return 'A valid fiscal year is required.';
  }
  return '';
}

async function downloadYearlyOfferingWorkbook() {
  error.value = yearlyValidationError();
  if (error.value) {
    return;
  }

  yearlyOfferingBusy.value = true;
  try {
    const blob = await downloadYearlyOfferingReport({ fiscalYear: yearlyFilters.fiscalYear });
    downloadBlob(blob, `yearly-offerings-${yearlyFilters.fiscalYear}.xlsx`);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not generate the yearly workbook.';
  } finally {
    yearlyOfferingBusy.value = false;
  }
}

async function downloadYearlyExpenditureWorkbook() {
  error.value = yearlyValidationError();
  if (error.value) {
    return;
  }

  yearlyExpenditureBusy.value = true;
  try {
    const blob = await downloadYearlyExpenditureReport({ fiscalYear: yearlyFilters.fiscalYear });
    downloadBlob(blob, `yearly-expenditures-${yearlyFilters.fiscalYear}.xlsx`);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not generate the yearly workbook.';
  } finally {
    yearlyExpenditureBusy.value = false;
  }
}

function exportActiveReport() {
  if (activeVisibleReportId.value === 'weekly-offerings') {
    exportCsv(
      'weekly-offering-report.csv',
      ['Offering Sunday', 'Fund', 'Category', 'Giving type', 'Payment method', 'Count', 'Total'],
      weeklyRows.value.map((row) => [
        row.offeringSunday,
        referenceLabel(offeringFundOptions.value, row.fundCode),
        referenceLabel(offeringCategoryOptions.value, row.categoryCode),
        givingTypeLabel(row.givingType),
        referenceLabel(paymentMethodOptions.value, row.paymentMethod),
        row.count,
        row.totalAmount,
      ]),
    );
    return;
  }

  if (activeVisibleReportId.value === 'member-offerings') {
    exportCsv(
      'member-offering-summary.csv',
      ['Offering number', 'Member', 'Email', 'Fund', 'Category', 'Count', 'Total'],
      memberRows.value.map((row) => [
        row.offeringNumber,
        row.memberName,
        row.primaryEmail,
        referenceLabel(offeringFundOptions.value, row.fundCode),
        referenceLabel(offeringCategoryOptions.value, row.categoryCode),
        row.count,
        row.totalAmount,
      ]),
    );
    return;
  }

  exportCsv(
    'financial-budget-report.csv',
    ['Type', 'Category', 'Sub-category', 'Budget', 'Actual', 'Budget vs. Actual', 'Variance'],
    financialRows.value.map((row) => [
      budgetTypeLabel(row.budgetType),
      financialCategoryLabel(row),
      financialSubCategoryLabel(row),
      row.budget,
      row.actual,
      budgetActualPercentage(row.budget, row.actual),
      row.variance,
    ]),
  );
}

async function issueReceipt(row: TaxReceiptSummaryRow) {
  await runReceiptAction(async () => {
    const receipt = await issueTaxReceipt({
      taxYear: row.taxYear,
      offeringNumber: row.offeringNumber,
      thankYouNote: thankYouNote.value,
    });
    await downloadReceiptResult(receipt.id, `receipt-${receipt.receiptNumber}.pdf`);
    await runActiveReport();
  });
}

async function downloadReceipt(row: TaxReceiptSummaryRow) {
  if (!row.receiptId || !row.receiptNumber) return;
  await runReceiptAction(() => downloadReceiptResult(row.receiptId!, `receipt-${row.receiptNumber}.pdf`));
}

async function downloadAllReceipts() {
  await runReceiptAction(async () => {
    const blob = await issueBatchTaxReceipts({
      taxYear: taxFilters.taxYear,
      thankYouNote: thankYouNote.value,
    });
    downloadBlob(blob, `tax-receipts-${taxFilters.taxYear}.zip`);
    await runActiveReport();
  });
}

async function voidReceipt(row: TaxReceiptSummaryRow) {
  if (!row.receiptId || !window.confirm(`Void receipt ${row.receiptNumber}?`)) return;
  const reason = window.prompt('Reason for voiding this receipt:')?.trim();
  if (!reason) return;
  await runReceiptAction(async () => {
    await voidTaxReceipt(row.receiptId!, reason);
    await runActiveReport();
  });
}

async function replaceReceipt(row: TaxReceiptSummaryRow) {
  if (!row.receiptId) return;
  await runReceiptAction(async () => {
    const receipt = await replaceTaxReceipt(row.receiptId!, thankYouNote.value);
    await downloadReceiptResult(receipt.id, `receipt-${receipt.receiptNumber}.pdf`);
    await runActiveReport();
  });
}

async function downloadReceiptResult(receiptId: string, filename: string) {
  downloadBlob(await downloadTaxReceiptPdf(receiptId), filename);
}

async function runReceiptAction(action: () => Promise<void>) {
  error.value = '';
  receiptBusy.value = true;
  try {
    await action();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not complete the receipt action.';
  } finally {
    receiptBusy.value = false;
  }
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function validateActiveFilters() {
  if (activeVisibleReportId.value === 'weekly-offerings') {
    return validateDateRange(weeklyFilters.start, weeklyFilters.end);
  }

  if (activeVisibleReportId.value === 'member-offerings') {
    return validateDateRange(memberFilters.start, memberFilters.end);
  }

  return '';
}

function validateDateRange(start: string, end: string) {
  if (start && end && end < start) {
    return 'Start date must be before or equal to end date.';
  }
  return '';
}

function activeSortedOptions(options: ReferenceDataOption[]) {
  return options
    .filter((option) => option.active)
    .sort((left, right) => left.sortOrder - right.sortOrder || left.label.localeCompare(right.label));
}

function referenceLabel(options: ReferenceDataOption[], code?: string) {
  if (!code) {
    return '-';
  }
  return options.find((option) => option.code === code)?.label ?? code;
}

function givingTypeLabel(type: WeeklyOfferingReportRow['givingType']) {
  return {
    MEMBER: 'Member',
    ANONYMOUS: 'Anonymous',
    GROUP: 'Group',
  }[type] ?? type;
}

function budgetTypeLabel(type: FinancialBudgetReportRow['budgetType']) {
  if (type === 'OFFERING_INCOME') {
    return 'INCOME';
  }
  return type;
}

function financialCategoryLabel(row: FinancialBudgetReportRow) {
  return row.budgetType === 'OFFERING_INCOME'
    ? referenceLabel(offeringFundOptions.value, row.category)
    : referenceLabel(financialCategoryOptions.value, row.category);
}

function financialSubCategoryLabel(row: FinancialBudgetReportRow) {
  return row.budgetType === 'OFFERING_INCOME'
    ? referenceLabel(offeringCategoryOptions.value, row.subCategory)
    : referenceLabel(financialSubCategoryOptions.value, row.subCategory);
}

function budgetActualPercentage(budget: number, actual: number) {
  const budgetAmount = Number(budget);
  if (budgetAmount === 0) {
    return '-';
  }
  return `${((Number(actual) / budgetAmount) * 100).toFixed(2)}%`;
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
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
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

.reports-toolbar button.active-report-tab {
  background: #22577a;
  color: white;
}

.reports-toolbar button.inactive-report-tab {
  border: 1px solid #c8d0d9;
  background: white;
  color: #22577a;
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

.quarterly-downloads {
  grid-column: 1 / -1;
  border-top: 1px solid #d9dee5;
}

.quarterly-download-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 52px;
  border-bottom: 1px solid #d9dee5;
}

.quarterly-download-row button {
  flex: 0 0 auto;
  border: 1px solid #22577a;
  border-radius: 6px;
  background: #22577a;
  color: white;
  padding: 9px 14px;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.quarterly-download-row button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.report-filters input,
.report-filters select,
.report-filters textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #c8d0d9;
  border-radius: 6px;
  padding: 9px 10px;
}

.tax-note-field {
  grid-column: 1 / -1;
}

.tax-note-field textarea {
  resize: vertical;
  min-height: 72px;
  font: inherit;
}

.receipt-warning {
  display: block;
  margin-top: 4px;
  color: #9a5b00;
  font-size: 12px;
  white-space: normal;
}

.receipt-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 180px;
}

.receipt-actions button {
  white-space: nowrap;
}

.danger-text {
  color: #a32929;
}

@media (max-width: 600px) {
  .reports-toolbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .reports-toolbar button {
    min-width: 0;
    padding: 6px 8px;
    white-space: normal;
  }

  .reports-header {
    flex-direction: column;
  }

  .reports-header button {
    width: 100%;
  }

  .report-filters {
    grid-template-columns: minmax(0, 1fr);
  }
}

.report-filters .actions {
  align-self: end;
}

.empty-state {
  color: #5b6778;
}
</style>

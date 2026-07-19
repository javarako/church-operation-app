<template>
  <section class="workspace">
    <header class="page-header">
      <div>
        <h2>Finance</h2>
        <p>Review offering income and manage manual expense transactions.</p>
      </div>
      <button type="button" @click="resetForm">Add expense</button>
    </header>

    <div class="two-column">
      <section class="panel">
        <div class="toolbar offering-toolbar">
          <select v-model="filters.category">
            <option value="">All categories</option>
            <option v-for="category in categoryOptions" :key="category.code" :value="category.code">
              {{ category.label }}
            </option>
          </select>
          <select v-model="filters.type">
            <option value="">Income & expense</option>
            <option value="INCOME">Income</option>
            <option value="EXPENSE">Expense</option>
          </select>
          <button type="button" @click="loadTransactions">Refresh</button>
        </div>

        <p v-if="error" class="error">{{ error }}</p>

        <div class="summary-strip">
          <strong>{{ formatMoney(filteredIncome - filteredExpense) }}</strong>
          <span>{{ filteredRows.length }} row{{ filteredRows.length === 1 ? '' : 's' }}</span>
          <span>Income {{ formatMoney(filteredIncome) }}</span>
          <span>Expense {{ formatMoney(filteredExpense) }}</span>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Type</th>
                <th>Date</th>
                <th>Category</th>
                <th>Sub-category</th>
                <th>Amount</th>
                <th>HST</th>
                <th>Cheque #</th>
                <th>Cleared</th>
                <th>Payable To</th>
                <th>Approved By</th>
                <th>Source</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="transaction in financePagination.paginatedRows.value"
                :key="transaction.id"
                :class="{ selected: editingExpenseId === transaction.id, 'read-only-row': !isManualExpense(transaction) }"
                @click="selectExpense(transaction)"
              >
                <td>{{ transaction.type }}</td>
                <td>{{ transaction.transactionDate }}</td>
                <td>{{ labelForCategory(transaction.category) }}</td>
                <td>{{ labelForSubCategory(transaction.subCategory) }}</td>
                <td>{{ formatMoney(transaction.amount) }}</td>
                <td>{{ transaction.hstIncluded ? 'Yes' : '-' }}</td>
                <td>{{ transaction.chequeNo || '-' }}</td>
                <td>{{ transaction.chequeCleared ? 'Yes' : '-' }}</td>
                <td>{{ transaction.payableTo || '-' }}</td>
                <td>{{ transaction.treasurer || '-' }}</td>
                <td>{{ sourceLabel(transaction) }}</td>
                <td class="row-actions">
                  <button
                    v-if="isManualExpense(transaction)"
                    type="button"
                    class="icon-button danger"
                    title="Delete expense"
                    aria-label="Delete expense"
                    @click.stop="deleteSelectedExpense(transaction)"
                  >
                    &#128465;
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationControls
          :current-page="financePagination.currentPage.value"
          :page-count="financePagination.pageCount.value"
          :page-size="financePagination.pageSize.value"
          :total-rows="financePagination.totalRows.value"
          :start-row="financePagination.startRow.value"
          :end-row="financePagination.endRow.value"
          @change-page="financePagination.goToPage"
        />
      </section>

      <form class="panel form-grid" @submit.prevent="saveExpense">
        <h3>{{ editingExpenseId ? 'Expense Detail' : 'New Expense' }}</h3>
        <p v-if="formError" class="error wide">{{ formError }}</p>

        <label>
          Transaction date
          <input v-model="form.transactionDate" type="date" required />
        </label>

        <label>
          Amount
          <input v-model.number="form.amount" type="number" min="0.01" step="0.01" required />
        </label>

        <label>
          Category
          <select v-model="form.category" required @change="handleCategoryChange">
            <option value="">Select category</option>
            <option v-for="category in financialCategoryOptions" :key="category.code" :value="category.code">
              {{ category.label }}
            </option>
          </select>
        </label>

        <label>
          Sub-category
          <select v-model="form.subCategory">
            <option value="">No sub-category</option>
            <option v-for="subCategory in subCategoryOptions" :key="subCategory.code" :value="subCategory.code">
              {{ subCategory.label }}
            </option>
          </select>
        </label>

        <label class="check-row">
          <input v-model="form.hstIncluded" type="checkbox" />
          HST included
        </label>

        <label class="check-row">
          <input v-model="form.chequeCleared" type="checkbox" />
          Cheque cleared
        </label>

        <label>
          Cheque #
          <input v-model="form.chequeNo" />
        </label>

        <label>
          Payable to
          <input v-model="form.payableTo" />
        </label>

        <label>
          Approved by
          <input v-model="form.treasurer" />
        </label>

        <label class="wide">
          Memo
          <textarea v-model="form.memo" rows="3"></textarea>
        </label>

        <div class="actions wide">
          <button type="submit">{{ editingExpenseId ? 'Save changes' : 'Save expense' }}</button>
          <button v-if="editingExpenseId" type="button" class="secondary" @click="resetForm">Cancel</button>
          <span v-if="savedMessage">{{ savedMessage }}</span>
        </div>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import {
  createExpense,
  deleteExpense,
  listFinanceTransactions,
  updateExpense,
  type FinancialTransaction,
  type FinancialTransactionPayload,
  type FinancialTransactionType,
} from '../api/finance';
import { listReferenceData, type ReferenceDataOption } from '../api/referenceData';
import PaginationControls from '../components/PaginationControls.vue';
import { usePagination } from '../composables/usePagination';

interface ExpenseForm {
  transactionDate: string;
  amount: number | null;
  category: string;
  subCategory: string;
  hstIncluded: boolean;
  chequeNo: string;
  chequeCleared: boolean;
  payableTo: string;
  treasurer: string;
  memo: string;
}

interface FinanceDisplayRow extends FinancialTransaction {
  summaryCount?: number;
}

const today = new Date().toISOString().slice(0, 10);
const transactions = ref<FinancialTransaction[]>([]);
const offeringFundOptions = ref<ReferenceDataOption[]>([]);
const offeringCategoryOptions = ref<ReferenceDataOption[]>([]);
const financialCategoryOptions = ref<ReferenceDataOption[]>([]);
const subCategoryOptions = ref<ReferenceDataOption[]>([]);
const allSubCategoryOptions = ref<ReferenceDataOption[]>([]);
const error = ref('');
const formError = ref('');
const savedMessage = ref('');
const editingExpenseId = ref('');

const filters = reactive({
  category: '',
  type: '' as '' | FinancialTransactionType,
});

const categoryOptions = computed(() => {
  const options = new Map<string, ReferenceDataOption>();
  offeringFundOptions.value.forEach((option) => options.set(option.code, option));
  financialCategoryOptions.value.forEach((option) => options.set(option.code, option));
  return Array.from(options.values()).sort((left, right) => left.label.localeCompare(right.label));
});

const form = reactive<ExpenseForm>({
  transactionDate: today,
  amount: null,
  category: '',
  subCategory: '',
  hstIncluded: false,
  chequeNo: '',
  chequeCleared: false,
  payableTo: '',
  treasurer: '',
  memo: '',
});

const visibleTransactions = computed(() =>
  transactions.value.filter((transaction) => !isCancelledOfferingIncome(transaction)),
);

const displayRows = computed<FinanceDisplayRow[]>(() => {
  const incomeByDayAndCategory = new Map<string, FinanceDisplayRow>();
  const expenseRows: FinanceDisplayRow[] = [];

  visibleTransactions.value.forEach((transaction) => {
    if (transaction.type === 'INCOME' && transaction.sourceType === 'OFFERING') {
      const summaryKey = `${transaction.transactionDate}:${transaction.category}:${transaction.subCategory ?? ''}`;
      const existing = incomeByDayAndCategory.get(summaryKey);
      if (existing) {
        existing.amount += Number(transaction.amount);
        existing.summaryCount = (existing.summaryCount ?? 1) + 1;
        return;
      }

      incomeByDayAndCategory.set(summaryKey, {
        ...transaction,
        id: `income-summary-${transaction.transactionDate}-${transaction.category}`,
        amount: Number(transaction.amount),
        memo: 'Daily offering income summary',
        summaryCount: 1,
      });
      return;
    }

    expenseRows.push(transaction);
  });

  return [...incomeByDayAndCategory.values(), ...expenseRows].sort((left, right) =>
    right.transactionDate.localeCompare(left.transactionDate),
  );
});

const filteredRows = computed(() =>
  displayRows.value.filter((transaction) => {
    const matchesCategory = !filters.category || transaction.category === filters.category;
    const matchesType = !filters.type || transaction.type === filters.type;
    return matchesCategory && matchesType;
  }),
);

const filteredIncome = computed(() =>
  filteredRows.value
    .filter((transaction) => transaction.type === 'INCOME')
    .reduce((total, transaction) => total + Number(transaction.amount), 0),
);

const filteredExpense = computed(() =>
  filteredRows.value
    .filter((transaction) => transaction.type === 'EXPENSE')
    .reduce((total, transaction) => total + Number(transaction.amount), 0),
);
const financePagination = usePagination(filteredRows);

watch(filters, () => financePagination.resetPage());

onMounted(async () => {
  await Promise.all([
    loadTransactions(), loadOfferingFunds(), loadOfferingCategories(),
    loadFinancialCategories(), loadAllSubCategories(),
  ]);
});

async function loadTransactions() {
  error.value = '';
  savedMessage.value = '';
  try {
    transactions.value = await listFinanceTransactions();
    financePagination.resetPage();
    savedMessage.value = 'Refreshed';
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load finance transactions.';
  }
}

async function loadOfferingFunds() {
  try {
    offeringFundOptions.value = (await listReferenceData('OFFERING_FUND')).filter((option) => option.active);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load offering funds.';
  }
}

async function loadOfferingCategories() {
  try {
    offeringCategoryOptions.value = (await listReferenceData('OFFERING_CATEGORY')).filter((option) => option.active);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load offering categories.';
  }
}

async function loadFinancialCategories() {
  try {
    financialCategoryOptions.value = (await listReferenceData('FINANCIAL_CATEGORY')).filter((option) => option.active);
    if (!form.category) {
      form.category = financialCategoryOptions.value[0]?.code ?? '';
      await loadSubCategories();
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load financial categories.';
  }
}

async function loadAllSubCategories() {
  try {
    allSubCategoryOptions.value = await listReferenceData('FINANCIAL_SUB_CATEGORY');
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load financial sub-categories.';
  }
}

async function loadSubCategories() {
  try {
    subCategoryOptions.value = form.category ? await listReferenceData('FINANCIAL_SUB_CATEGORY', form.category) : [];
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load financial sub-categories.';
  }
}

async function saveExpense() {
  formError.value = '';
  savedMessage.value = '';
  try {
    if (form.amount === null) {
      throw new Error('Expense amount is required.');
    }
    const payload: FinancialTransactionPayload = {
      transactionDate: form.transactionDate,
      amount: form.amount,
      category: form.category,
      subCategory: form.subCategory || undefined,
      hstIncluded: form.hstIncluded,
      chequeNo: form.chequeNo.trim() || undefined,
      chequeCleared: form.chequeCleared,
      payableTo: form.payableTo.trim() || undefined,
      treasurer: form.treasurer.trim() || undefined,
      memo: form.memo.trim() || undefined,
    };

    const wasEditing = Boolean(editingExpenseId.value);
    if (wasEditing) {
      await updateExpense(editingExpenseId.value, payload);
    } else {
      await createExpense(payload);
    }
    await loadTransactions();
    resetForm();
    savedMessage.value = wasEditing ? 'Updated' : 'Saved';
  } catch (err) {
    formError.value = err instanceof Error ? err.message : 'Could not save expense.';
  }
}

function selectExpense(transaction: FinancialTransaction) {
  if (!isManualExpense(transaction)) {
    return;
  }
  formError.value = '';
  savedMessage.value = '';
  editingExpenseId.value = transaction.id;
  form.transactionDate = transaction.transactionDate;
  form.amount = Number(transaction.amount);
  form.category = transaction.category;
  form.subCategory = transaction.subCategory ?? '';
  form.hstIncluded = transaction.hstIncluded;
  form.chequeNo = transaction.chequeNo ?? '';
  form.chequeCleared = transaction.chequeCleared;
  form.payableTo = transaction.payableTo ?? '';
  form.treasurer = transaction.treasurer ?? '';
  form.memo = transaction.memo ?? '';
  void loadSubCategories();
}

async function deleteSelectedExpense(transaction: FinancialTransaction) {
  const confirmed = window.confirm(`Delete expense ${formatMoney(transaction.amount)} from ${transaction.transactionDate}?`);
  if (!confirmed) {
    return;
  }
  error.value = '';
  savedMessage.value = '';
  try {
    await deleteExpense(transaction.id);
    await loadTransactions();
    if (editingExpenseId.value === transaction.id) {
      resetForm();
    }
    savedMessage.value = 'Deleted';
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not delete expense.';
  }
}

async function handleCategoryChange() {
  form.subCategory = '';
  await loadSubCategories();
}

function resetForm() {
  formError.value = '';
  savedMessage.value = '';
  editingExpenseId.value = '';
  form.transactionDate = today;
  form.amount = null;
  form.category = financialCategoryOptions.value[0]?.code ?? '';
  form.subCategory = '';
  form.hstIncluded = false;
  form.chequeNo = '';
  form.chequeCleared = false;
  form.payableTo = '';
  form.treasurer = '';
  form.memo = '';
  void loadSubCategories();
}

function isManualExpense(transaction: FinancialTransaction) {
  return transaction.type === 'EXPENSE' && transaction.sourceType === 'MANUAL';
}

function isCancelledOfferingIncome(transaction: FinancialTransaction) {
  return transaction.type === 'INCOME'
    && transaction.sourceType === 'OFFERING'
    && Number(transaction.amount) <= 0;
}

function sourceLabel(transaction: FinanceDisplayRow) {
  if (transaction.type === 'INCOME' && transaction.sourceType === 'OFFERING') {
    return `Offering summary (${transaction.summaryCount ?? 1})`;
  }
  return transaction.sourceType === 'OFFERING' ? 'Offering' : 'Manual';
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value);
}

function labelForCategory(code: string) {
  return financialCategoryOptions.value.find((category) => category.code === code)?.label
    ?? offeringFundOptions.value.find((category) => category.code === code)?.label
    ?? code;
}

function labelForSubCategory(code?: string) {
  if (!code) {
    return '-';
  }
  return [...offeringCategoryOptions.value, ...allSubCategoryOptions.value]
    .find((subCategory) => subCategory.code === code)?.label ?? code;
}
</script>

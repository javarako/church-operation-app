<template>
  <section class="workspace">
    <header class="page-header">
      <h2>Budgets</h2>
      <button type="button" @click="startCreate">Add budget</button>
    </header>

    <div class="two-column">
      <section class="panel">
        <div class="toolbar">
          <input
            v-model.number="selectedFiscalYear"
            type="number"
            min="2000"
            max="2100"
            @change="loadBudgets"
          />
          <select v-model="filters.budgetType">
            <option value="">All types</option>
            <option value="OFFERING_INCOME">Income</option>
            <option value="EXPENSE">Expense</option>
          </select>
          <button type="button" @click="loadBudgets">Refresh</button>
        </div>

        <p v-if="error" class="error">{{ error }}</p>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Type</th>
                <th>Fiscal year</th>
                <th>Category</th>
                <th>Sub-category</th>
                <th>Budget</th>
                <th>Memo</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="budget in budgetPagination.paginatedRows.value"
                :key="budget.id"
                :class="{ selected: editingBudgetId === budget.id }"
                @click="selectBudget(budget)"
              >
                <td>{{ typeLabel(budget.budgetType) }}</td>
                <td>{{ budget.fiscalYear }}</td>
                <td>{{ labelForCategory(budget.budgetType, budget.category) }}</td>
                <td>{{ labelForSubCategory(budget.subCategory) }}</td>
                <td>{{ formatMoney(budget.budget) }}</td>
                <td>{{ budget.memo || '-' }}</td>
                <td class="row-actions">
                  <button
                    type="button"
                    class="icon-button danger"
                    title="Delete budget"
                    aria-label="Delete budget"
                    @click.stop="deleteSelectedBudget(budget)"
                  >
                    &#128465;
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationControls
          :current-page="budgetPagination.currentPage.value"
          :page-count="budgetPagination.pageCount.value"
          :page-size="budgetPagination.pageSize.value"
          :total-rows="budgetPagination.totalRows.value"
          :start-row="budgetPagination.startRow.value"
          :end-row="budgetPagination.endRow.value"
          @change-page="budgetPagination.goToPage"
        />
      </section>

      <form class="panel form-grid" @submit.prevent="saveBudget">
        <h3>{{ editingBudgetId ? 'Budget Detail' : 'New Budget' }}</h3>
        <p v-if="formError" class="error wide">{{ formError }}</p>

        <label>
          Budget type
          <select v-model="form.budgetType" @change="handleBudgetTypeChange">
            <option value="OFFERING_INCOME">Income</option>
            <option value="EXPENSE">Expense</option>
          </select>
        </label>

        <label>
          Fiscal year
          <input v-model.number="form.fiscalYear" type="number" min="2000" max="2100" required />
        </label>

        <label>
          Budget
          <input v-model.number="form.budget" type="number" min="0" step="0.01" required />
        </label>

        <label v-if="showsCategory">
          {{ form.budgetType === 'OFFERING_INCOME' ? 'Fund' : 'Category' }}
          <select v-model="form.category" :required="showsCategory" @change="handleCategoryChange">
            <option value="">Select category</option>
            <option v-for="category in categoryOptions" :key="category.code" :value="category.code">
              {{ category.label }}
            </option>
          </select>
        </label>

        <label v-if="showsSubCategory">
          {{ form.budgetType === 'OFFERING_INCOME' ? 'Category' : 'Sub-category' }}
          <select v-model="form.subCategory" :required="form.budgetType === 'OFFERING_INCOME'">
            <option value="">{{ form.budgetType === 'OFFERING_INCOME' ? 'Select category' : 'No sub-category' }}</option>
            <option v-for="subCategory in subCategoryOptions" :key="subCategory.code" :value="subCategory.code">
              {{ subCategory.label }}
            </option>
          </select>
        </label>

        <label class="wide">
          Memo
          <textarea v-model="form.memo" rows="3"></textarea>
        </label>

        <div class="actions wide">
          <button type="submit">{{ editingBudgetId ? 'Save changes' : 'Save budget' }}</button>
          <button v-if="editingBudgetId" type="button" class="secondary" @click="resetForm">Cancel</button>
          <span v-if="savedMessage">{{ savedMessage }}</span>
        </div>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import {
  createBudget,
  deleteBudget,
  listBudgets,
  updateBudget,
  type Budget,
  type BudgetPayload,
  type BudgetType,
} from '../api/budgets';
import { listReferenceData, type ReferenceDataOption } from '../api/referenceData';
import PaginationControls from '../components/PaginationControls.vue';
import { usePagination } from '../composables/usePagination';

interface BudgetForm {
  budgetType: BudgetType;
  fiscalYear: number;
  category: string;
  subCategory: string;
  budget: number | null;
  memo: string;
}

const currentYear = new Date().getFullYear();
const budgets = ref<Budget[]>([]);
const selectedFiscalYear = ref(currentYear);
const offeringFundOptions = ref<ReferenceDataOption[]>([]);
const allOfferingCategoryOptions = ref<ReferenceDataOption[]>([]);
const financialCategoryOptions = ref<ReferenceDataOption[]>([]);
const allFinancialSubCategoryOptions = ref<ReferenceDataOption[]>([]);
const categoryOptions = ref<ReferenceDataOption[]>([]);
const subCategoryOptions = ref<ReferenceDataOption[]>([]);
const error = ref('');
const formError = ref('');
const savedMessage = ref('');
const editingBudgetId = ref('');

const filters = reactive({
  budgetType: '' as '' | BudgetType,
});

const form = reactive<BudgetForm>({
  budgetType: 'OFFERING_INCOME',
  fiscalYear: currentYear,
  category: '',
  subCategory: '',
  budget: null,
  memo: '',
});

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
const budgetPagination = usePagination(filteredBudgets);

watch(filters, () => budgetPagination.resetPage());

const showsCategory = computed(() => form.budgetType !== 'CARRY_OVER');
const showsSubCategory = computed(() => form.budgetType === 'EXPENSE' || form.budgetType === 'OFFERING_INCOME');

onMounted(async () => {
  await Promise.all([
    loadOfferingFunds(),
    loadAllOfferingCategories(),
    loadFinancialCategories(),
    loadAllSubCategoryOptions(),
    loadBudgets(),
  ]);
  await loadCategoryOptions();
  await loadSubCategoryOptions();
});

async function loadBudgets() {
  error.value = '';
  savedMessage.value = '';
  try {
    budgets.value = await listBudgets(selectedFiscalYear.value);
    budgetPagination.resetPage();
    savedMessage.value = 'Refreshed';
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load budgets.';
  }
}

async function loadOfferingFunds() {
  try {
    offeringFundOptions.value = (await listReferenceData('OFFERING_FUND')).filter((option) => option.active);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load offering funds.';
  }
}

async function loadAllOfferingCategories() {
  try {
    allOfferingCategoryOptions.value = await listReferenceData('OFFERING_CATEGORY');
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load offering categories.';
  }
}

async function loadFinancialCategories() {
  try {
    financialCategoryOptions.value = (await listReferenceData('FINANCIAL_CATEGORY')).filter((option) => option.active);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load financial categories.';
  }
}

async function loadAllSubCategoryOptions() {
  try {
    allFinancialSubCategoryOptions.value = await listReferenceData('FINANCIAL_SUB_CATEGORY');
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load financial sub-categories.';
  }
}

async function loadCategoryOptions() {
  if (form.budgetType === 'OFFERING_INCOME') {
    categoryOptions.value = offeringFundOptions.value;
    return;
  }
  if (form.budgetType === 'EXPENSE') {
    categoryOptions.value = financialCategoryOptions.value;
    return;
  }
  categoryOptions.value = [];
}

async function loadSubCategoryOptions() {
  if (!form.category) {
    subCategoryOptions.value = [];
    return;
  }
  try {
    const type = form.budgetType === 'OFFERING_INCOME' ? 'OFFERING_CATEGORY' : 'FINANCIAL_SUB_CATEGORY';
    subCategoryOptions.value = await listReferenceData(type, form.category);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load financial sub-categories.';
  }
}

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

async function saveBudget() {
  formError.value = '';
  savedMessage.value = '';
  try {
    if (form.budget === null || Number.isNaN(form.budget)) {
      throw new Error('Budget is required.');
    }
    if (showsCategory.value && !form.category) {
      throw new Error('Category is required.');
    }

    const payload: BudgetPayload = {
      fiscalYear: form.fiscalYear,
      budgetType: form.budgetType,
      category: showsCategory.value ? form.category : undefined,
      subCategory: showsSubCategory.value && form.subCategory ? form.subCategory : undefined,
      budget: form.budget,
      memo: form.memo.trim() || undefined,
    };

    const wasEditing = Boolean(editingBudgetId.value);
    if (wasEditing) {
      await updateBudget(editingBudgetId.value, payload);
    } else {
      await createBudget(payload);
    }
    if (selectedFiscalYear.value !== form.fiscalYear) {
      selectedFiscalYear.value = form.fiscalYear;
    }
    await loadBudgets();
    resetForm();
    savedMessage.value = wasEditing ? 'Updated' : 'Saved';
  } catch (err) {
    formError.value = err instanceof Error ? err.message : 'Could not save budget.';
  }
}

async function selectBudget(budget: Budget) {
  formError.value = '';
  savedMessage.value = '';
  editingBudgetId.value = budget.id;
  form.budgetType = budget.budgetType;
  form.fiscalYear = budget.fiscalYear;
  form.category = budget.category ?? '';
  form.subCategory = budget.subCategory ?? '';
  form.budget = Number(budget.budget);
  form.memo = budget.memo ?? '';
  await loadCategoryOptions();
  await loadSubCategoryOptions();
}

async function deleteSelectedBudget(budget: Budget) {
  const confirmed = window.confirm(`Delete ${typeLabel(budget.budgetType)} budget for ${budget.fiscalYear}?`);
  if (!confirmed) {
    return;
  }
  error.value = '';
  savedMessage.value = '';
  try {
    await deleteBudget(budget.id);
    await loadBudgets();
    if (editingBudgetId.value === budget.id) {
      resetForm();
    }
    savedMessage.value = 'Deleted';
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not delete budget.';
  }
}

function startCreate() {
  resetForm();
}

function resetForm() {
  formError.value = '';
  savedMessage.value = '';
  editingBudgetId.value = '';
  form.budgetType = 'OFFERING_INCOME';
  form.fiscalYear = selectedFiscalYear.value;
  form.category = '';
  form.subCategory = '';
  form.budget = null;
  form.memo = '';
  void loadCategoryOptions();
  void loadSubCategoryOptions();
}

function typeLabel(type: BudgetType) {
  switch (type) {
    case 'CARRY_OVER':
      return 'Carry over';
    case 'OFFERING_INCOME':
      return 'Income';
    case 'EXPENSE':
      return 'Expense';
  }
}

function compareBudgets(left: Budget, right: Budget) {
  const typeDifference = BUDGET_TYPE_ORDER[left.budgetType] - BUDGET_TYPE_ORDER[right.budgetType];
  if (typeDifference !== 0) {
    return typeDifference;
  }

  const parentDifference = compareReferenceOrder(
    parentReference(left),
    left.category,
    parentReference(right),
    right.category,
  );
  if (parentDifference !== 0) {
    return parentDifference;
  }

  const childDifference = compareReferenceOrder(
    childReference(left),
    left.subCategory,
    childReference(right),
    right.subCategory,
  );
  if (childDifference !== 0) {
    return childDifference;
  }

  return left.id.localeCompare(right.id);
}

function parentReference(budget: Budget) {
  const options = budget.budgetType === 'OFFERING_INCOME'
    ? offeringFundOptions.value
    : budget.budgetType === 'EXPENSE'
      ? financialCategoryOptions.value
      : [];
  return options.find((option) => option.code === budget.category);
}

function childReference(budget: Budget) {
  const options = budget.budgetType === 'OFFERING_INCOME'
    ? allOfferingCategoryOptions.value
    : budget.budgetType === 'EXPENSE'
      ? allFinancialSubCategoryOptions.value
      : [];
  return options.find((option) =>
    option.code === budget.subCategory
      && (!option.parentCode || option.parentCode === budget.category),
  );
}

function compareReferenceOrder(
  left: ReferenceDataOption | undefined,
  leftCode: string | undefined,
  right: ReferenceDataOption | undefined,
  rightCode: string | undefined,
) {
  const sortDifference = (left?.sortOrder ?? Number.MAX_SAFE_INTEGER)
    - (right?.sortOrder ?? Number.MAX_SAFE_INTEGER);
  if (sortDifference !== 0) {
    return sortDifference;
  }

  const leftLabel = left?.label || leftCode || '';
  const rightLabel = right?.label || rightCode || '';
  const labelDifference = leftLabel.localeCompare(rightLabel);
  if (labelDifference !== 0) {
    return labelDifference;
  }

  return (leftCode || '').localeCompare(rightCode || '');
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value);
}

function labelForCategory(type: BudgetType, code?: string) {
  if (!code) {
    return '-';
  }
  const options = type === 'OFFERING_INCOME' ? offeringFundOptions.value : financialCategoryOptions.value;
  return options.find((option) => option.code === code)?.label ?? code;
}

function labelForSubCategory(code?: string) {
  if (!code) {
    return '-';
  }
  return [...allOfferingCategoryOptions.value, ...allFinancialSubCategoryOptions.value]
    .find((option) => option.code === code)?.label ?? code;
}
</script>

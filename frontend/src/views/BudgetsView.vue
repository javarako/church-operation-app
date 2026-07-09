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
            <option value="CARRY_OVER">Carry over</option>
            <option value="OFFERING_INCOME">Offering income</option>
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
                v-for="budget in filteredBudgets"
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
      </section>

      <form class="panel form-grid" @submit.prevent="saveBudget">
        <h3>{{ editingBudgetId ? 'Budget Detail' : 'New Budget' }}</h3>
        <p v-if="formError" class="error wide">{{ formError }}</p>

        <label>
          Budget type
          <select v-model="form.budgetType" @change="handleBudgetTypeChange">
            <option value="CARRY_OVER">Carry over</option>
            <option value="OFFERING_INCOME">Offering income</option>
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
          Category
          <select v-model="form.category" :required="showsCategory" @change="handleCategoryChange">
            <option value="">Select category</option>
            <option v-for="category in categoryOptions" :key="category.code" :value="category.code">
              {{ category.label }}
            </option>
          </select>
        </label>

        <label v-if="showsSubCategory">
          Sub-category
          <select v-model="form.subCategory">
            <option value="">No sub-category</option>
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
import { computed, onMounted, reactive, ref } from 'vue';
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
  budgetType: 'CARRY_OVER',
  fiscalYear: currentYear,
  category: '',
  subCategory: '',
  budget: null,
  memo: '',
});

const filteredBudgets = computed(() =>
  budgets.value.filter((budget) => !filters.budgetType || budget.budgetType === filters.budgetType),
);

const showsCategory = computed(() => form.budgetType !== 'CARRY_OVER');
const showsSubCategory = computed(() => form.budgetType === 'EXPENSE');

onMounted(async () => {
  await Promise.all([
    loadOfferingFunds(),
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
    savedMessage.value = 'Refreshed';
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load budgets.';
  }
}

async function loadOfferingFunds() {
  try {
    offeringFundOptions.value = (await listReferenceData('OFFERING_FUND_CATEGORY')).filter((option) => option.active);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load offering funds.';
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
  if (form.budgetType !== 'EXPENSE' || !form.category) {
    subCategoryOptions.value = [];
    return;
  }
  try {
    subCategoryOptions.value = await listReferenceData('FINANCIAL_SUB_CATEGORY', form.category);
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
  form.budgetType = 'CARRY_OVER';
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
      return 'Offering income';
    case 'EXPENSE':
      return 'Expense';
  }
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
  return allFinancialSubCategoryOptions.value.find((option) => option.code === code)?.label ?? code;
}
</script>

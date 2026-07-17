<template>
  <section class="workspace">
    <header class="page-header">
      <div>
        <h2>Reference Data</h2>
        <p>Maintain church-specific dropdown values used by member records.</p>
      </div>
      <button type="button" @click="startCreate">New value</button>
    </header>

    <div class="two-column">
      <section class="panel">
        <div class="toolbar">
          <select v-model="selectedType" @change="loadOptions">
            <option v-for="typeOption in typeOptions" :key="typeOption.type" :value="typeOption.type">
              {{ typeOption.label }}
            </option>
          </select>
          <button type="button" @click="loadOptions">Refresh</button>
        </div>

        <p v-if="error" class="error">{{ error }}</p>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Code</th>
                <th>Label</th>
                <th>Parent</th>
                <th>Order</th>
                <th>Active</th>
                <th aria-label="Actions"></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="option in referencePagination.paginatedRows.value"
                :key="option.id"
                :class="{ selected: selectedOption?.id === option.id }"
                @click="selectOption(option)"
              >
                <td>{{ option.code }}</td>
                <td>{{ option.label }}</td>
                <td>{{ parentLabel(option) }}</td>
                <td>{{ option.sortOrder }}</td>
                <td>{{ option.active ? 'Yes' : 'No' }}</td>
                <td class="row-actions">
                  <button
                    type="button"
                    class="icon-button danger"
                    title="Delete reference value"
                    :aria-label="`Delete reference value ${option.label}`"
                    @click.stop="deleteSelectedOption(option)"
                  >
                    <Trash2 :size="17" aria-hidden="true" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationControls
          :current-page="referencePagination.currentPage.value"
          :page-count="referencePagination.pageCount.value"
          :page-size="referencePagination.pageSize.value"
          :total-rows="referencePagination.totalRows.value"
          :start-row="referencePagination.startRow.value"
          :end-row="referencePagination.endRow.value"
          @change-page="referencePagination.goToPage"
        />
      </section>

      <form class="panel form-grid" @submit.prevent="saveOption">
        <h3>{{ selectedOption ? 'Reference Detail' : 'New Reference Value' }}</h3>

        <label>
          Type
          <select v-model="form.type" :disabled="Boolean(selectedOption)" @change="handleFormTypeChange">
            <option v-for="typeOption in typeOptions" :key="typeOption.type" :value="typeOption.type">
              {{ typeOption.label }}
            </option>
          </select>
        </label>
        <label v-if="form.type === 'OFFERING_CATEGORY'">
          Parent fund
          <select v-model="form.parentCode" required>
            <option value="">Select fund</option>
            <option v-for="fund in offeringFundOptions" :key="fund.code" :value="fund.code">
              {{ fund.label }}
            </option>
          </select>
        </label>
        <label v-if="form.type === 'FINANCIAL_SUB_CATEGORY'">
          Parent category
          <select v-model="form.parentCode" required>
            <option value="">Select category</option>
            <option v-for="category in financialCategoryOptions" :key="category.code" :value="category.code">
              {{ category.label }}
            </option>
          </select>
        </label>
        <label>
          Code
          <input v-model="form.code" :disabled="Boolean(selectedOption)" required />
        </label>
        <label>
          Label
          <input v-model="form.label" required />
        </label>
        <label>
          Sort order
          <input v-model.number="form.sortOrder" type="number" />
        </label>
        <label class="check-row wide">
          <input v-model="form.active" type="checkbox" />
          Active
        </label>

        <div class="actions wide">
          <button type="submit">{{ selectedOption ? 'Save changes' : 'Create value' }}</button>
          <span v-if="savedMessage">{{ savedMessage }}</span>
        </div>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import {
  createReferenceData,
  deleteReferenceData,
  listAllReferenceData,
  updateReferenceData,
  type ReferenceDataOption,
  type ReferenceDataPayload,
  type ReferenceDataType,
} from '../api/referenceData';
import { Trash2 } from '@lucide/vue';
import PaginationControls from '../components/PaginationControls.vue';
import { usePagination } from '../composables/usePagination';

const selectedType = ref<ReferenceDataType>('GROUP_CODE');
const selectedOption = ref<ReferenceDataOption | null>(null);
const options = ref<ReferenceDataOption[]>([]);
const offeringFundOptions = ref<ReferenceDataOption[]>([]);
const financialCategoryOptions = ref<ReferenceDataOption[]>([]);
const error = ref('');
const savedMessage = ref('');
const referencePagination = usePagination(options);

const typeOptions: Array<{ type: ReferenceDataType; label: string }> = [
  { type: 'GROUP_CODE', label: 'Group code' },
  { type: 'MEMBERSHIP_STATUS', label: 'Membership status' },
  { type: 'COMMITTEE_CODE', label: 'Committee code' },
  { type: 'OFFERING_FUND', label: 'Offering fund' },
  { type: 'OFFERING_CATEGORY', label: 'Offering category' },
  { type: 'PAYMENT_METHOD', label: 'Payment method' },
  { type: 'FINANCIAL_CATEGORY', label: 'Financial category' },
  { type: 'FINANCIAL_SUB_CATEGORY', label: 'Financial sub-category' },
];

const form = reactive<ReferenceDataPayload>({
  type: 'GROUP_CODE',
  code: '',
  label: '',
  parentCode: '',
  sortOrder: 10,
  active: true,
});

onMounted(async () => {
  await refreshLists();
});

async function loadOptions() {
  error.value = '';
  try {
    options.value = await listAllReferenceData(selectedType.value);
    referencePagination.resetPage();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load reference data.';
  }
}

async function loadFinancialCategories() {
  try {
    financialCategoryOptions.value = await listAllReferenceData('FINANCIAL_CATEGORY');
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load financial categories.';
  }
}

async function loadOfferingFunds() {
  try {
    offeringFundOptions.value = await listAllReferenceData('OFFERING_FUND');
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load offering funds.';
  }
}

async function refreshLists() {
  await Promise.all([loadOptions(), loadOfferingFunds(), loadFinancialCategories()]);
}

function startCreate() {
  selectedOption.value = null;
  applyToForm({
    type: selectedType.value,
    code: '',
    label: '',
    parentCode: '',
    sortOrder: nextSortOrder(),
    active: true,
  });
}

function selectOption(option: ReferenceDataOption) {
  selectedOption.value = option;
  applyToForm(option);
}

function parentLabel(option: ReferenceDataOption) {
  if (!option.parentCode) {
    return '-';
  }
  const parentOptions = option.type === 'OFFERING_CATEGORY'
    ? offeringFundOptions.value
    : option.type === 'FINANCIAL_SUB_CATEGORY'
      ? financialCategoryOptions.value
      : [];
  return parentOptions.find((parent) => parent.code === option.parentCode)?.label ?? option.parentCode;
}

async function saveOption() {
  error.value = '';
  savedMessage.value = '';
  try {
    const payload = {
      ...form,
      code: form.code.trim().toUpperCase(),
      label: form.label.trim(),
      parentCode: isChildType(form.type) ? form.parentCode?.trim().toUpperCase() : undefined,
    };
    const saved = selectedOption.value
      ? await updateReferenceData(selectedOption.value.id, payload)
      : await createReferenceData(payload);
    selectedType.value = saved.type;
    selectedOption.value = saved;
    applyToForm(saved);
    savedMessage.value = 'Saved';
    await refreshLists();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not save reference data.';
  }
}

async function deleteSelectedOption(option: ReferenceDataOption) {
  if (!window.confirm(`Delete reference value ${option.label}? This cannot be undone.`)) {
    return;
  }
  error.value = '';
  savedMessage.value = '';
  try {
    await deleteReferenceData(option.id);
    if (selectedOption.value?.id === option.id) {
      startCreate();
    }
    await refreshLists();
    savedMessage.value = 'Deleted';
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not delete reference data.';
  }
}

function applyToForm(option: ReferenceDataPayload) {
  form.type = option.type;
  form.code = option.code;
  form.label = option.label;
  form.parentCode = option.parentCode ?? '';
  form.sortOrder = option.sortOrder;
  form.active = option.active;
}

function handleFormTypeChange() {
  if (!isChildType(form.type)) {
    form.parentCode = '';
  }
}

function isChildType(type: ReferenceDataType) {
  return type === 'OFFERING_CATEGORY' || type === 'FINANCIAL_SUB_CATEGORY';
}

function nextSortOrder() {
  const maxSortOrder = options.value.reduce((max, option) => Math.max(max, option.sortOrder), 0);
  return maxSortOrder + 10;
}
</script>

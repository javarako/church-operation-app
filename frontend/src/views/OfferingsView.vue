<template>
  <section class="workspace">
    <header class="page-header">
      <div>
        <h2>Offerings</h2>
        <p>Record member, anonymous, and group offerings with linked income tracking.</p>
      </div>
      <button type="button" @click="resetForm">Record offering</button>
    </header>

    <div class="two-column">
      <section class="panel">
        <div class="toolbar offering-toolbar">
          <select v-model="filters.fundCode">
            <option value="">All funds</option>
            <option v-for="fund in fundOptions" :key="fund.code" :value="fund.code">
              {{ fund.label }}
            </option>
          </select>
          <select v-model="filters.categoryCode">
            <option value="">All categories</option>
            <option v-for="category in allCategoryOptions" :key="category.code" :value="category.code">
              {{ category.label }}
            </option>
          </select>
          <select v-model="filters.givingType">
            <option value="">All types</option>
            <option value="MEMBER">Member</option>
            <option value="ANONYMOUS">Anonymous</option>
            <option value="GROUP">Group</option>
          </select>
          <button type="button" @click="loadOfferings">Refresh</button>
        </div>

        <p v-if="error" class="error">{{ error }}</p>

        <div class="summary-strip">
          <strong>{{ formatMoney(filteredTotal) }}</strong>
          <span>{{ filteredOfferings.length }} offering{{ filteredOfferings.length === 1 ? '' : 's' }}</span>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Sunday</th>
                <th>Giver</th>
                <th>Fund</th>
                <th>Category</th>
                <th>Amount</th>
                <th>Payment</th>
                <th>Linked income</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="offering in offeringPagination.paginatedRows.value"
                :key="offering.id"
                :class="{ selected: editingOfferingId === offering.id }"
                @click="selectOffering(offering)"
              >
                <td>{{ offering.offeringDate }}</td>
                <td>{{ offering.offeringSunday }}</td>
                <td>{{ offering.giverDisplayName || offering.giverLabel || '-' }}</td>
                <td>{{ labelForFund(offering.fundCode) }}</td>
                <td>{{ labelForCategory(offering.categoryCode) }}</td>
                <td>{{ formatMoney(offering.amount) }}</td>
                <td>{{ labelForPaymentMethod(offering.paymentMethod) }}</td>
                <td>{{ offering.incomeTransactionId ? 'Created' : '-' }}</td>
                <td class="row-actions">
                  <button
                    type="button"
                    class="icon-button danger"
                    title="Delete offering"
                    aria-label="Delete offering"
                    @click.stop="deleteSelectedOffering(offering)"
                  >
                    &#128465;
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationControls
          :current-page="offeringPagination.currentPage.value"
          :page-count="offeringPagination.pageCount.value"
          :page-size="offeringPagination.pageSize.value"
          :total-rows="offeringPagination.totalRows.value"
          :start-row="offeringPagination.startRow.value"
          :end-row="offeringPagination.endRow.value"
          @change-page="offeringPagination.goToPage"
        />
      </section>

      <form class="panel form-grid" @submit.prevent="saveOffering">
        <h3>{{ editingOfferingId ? 'Edit Offering' : 'Record Offering' }}</h3>
        <p v-if="formError" class="error wide">{{ formError }}</p>

        <label>
          Giving type
          <select v-model="form.givingType" @change="handleGivingTypeChange">
            <option value="MEMBER">Member</option>
            <option value="ANONYMOUS">Anonymous</option>
            <option value="GROUP">Group</option>
          </select>
        </label>

        <label v-if="form.givingType === 'MEMBER'">
          Member search
          <input v-model="memberSearch" placeholder="Search member" @input="loadMembers" />
        </label>

        <label v-if="form.givingType === 'MEMBER'" class="wide">
          Member
          <select v-model="form.memberId" required>
            <option value="">Select member</option>
            <option v-for="member in members" :key="member.id" :value="member.id">
              {{ member.displayName || member.primaryEmail }}
            </option>
          </select>
        </label>

        <label v-if="form.givingType !== 'MEMBER'" class="wide">
          Giver label
          <input v-model="form.giverLabel" required />
        </label>

        <label>
          Offering date
          <input v-model="form.offeringDate" type="date" required @change="syncOfferingSunday" />
        </label>

        <label>
          Offering Sunday
          <input v-model="form.offeringSunday" type="date" min="1970-01-04" step="7" required />
        </label>

        <label>
          Fund
          <select v-model="form.fundCode" required @change="handleFundChange">
            <option value="">Select fund</option>
            <option v-for="fund in fundOptions" :key="fund.code" :value="fund.code">
              {{ fund.label }}
            </option>
          </select>
        </label>

        <label>
          Category
          <select v-model="form.categoryCode" required>
            <option value="">Select category</option>
            <option v-for="category in categoryOptions" :key="category.code" :value="category.code">
              {{ category.label }}
            </option>
          </select>
        </label>

        <label>
          Amount
          <input v-model.number="form.amount" type="number" min="0.01" step="0.01" required />
        </label>

        <label>
          Payment method
          <select v-model="form.paymentMethod" required>
            <option value="">Select payment</option>
            <option v-for="method in paymentMethodOptions" :key="method.code" :value="method.code">
              {{ method.label }}
            </option>
          </select>
        </label>

        <label class="wide">
          Memo
          <textarea v-model="form.memo" rows="3"></textarea>
        </label>

        <div class="actions wide">
          <button type="submit">{{ editingOfferingId ? 'Save changes' : 'Save offering' }}</button>
          <button v-if="editingOfferingId" type="button" class="secondary" @click="resetForm">Cancel</button>
          <span v-if="savedMessage">{{ savedMessage }}</span>
        </div>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import {
  createOffering,
  deleteOffering,
  listOfferings,
  updateOffering,
  type GivingType,
  type Offering,
  type OfferingPayload,
} from '../api/offerings';
import { listMembers, type MemberRecord } from '../api/members';
import { listReferenceData, type ReferenceDataOption } from '../api/referenceData';
import PaginationControls from '../components/PaginationControls.vue';
import { usePagination } from '../composables/usePagination';
import { calculateComingSunday } from '../utils/offeringDates';

interface OfferingForm {
  givingType: GivingType;
  memberId: string;
  giverLabel: string;
  offeringDate: string;
  offeringSunday: string;
  fundCode: string;
  categoryCode: string;
  amount: number | null;
  paymentMethod: string;
  memo: string;
}

const today = new Date().toISOString().slice(0, 10);
const offerings = ref<Offering[]>([]);
const fundOptions = ref<ReferenceDataOption[]>([]);
const categoryOptions = ref<ReferenceDataOption[]>([]);
const allCategoryOptions = ref<ReferenceDataOption[]>([]);
const paymentMethodOptions = ref<ReferenceDataOption[]>([]);
const members = ref<MemberRecord[]>([]);
const memberSearch = ref('');
const error = ref('');
const formError = ref('');
const savedMessage = ref('');
const editingOfferingId = ref('');

const filters = reactive({
  fundCode: '',
  categoryCode: '',
  givingType: '' as '' | GivingType,
});

const form = reactive<OfferingForm>({
  givingType: 'MEMBER',
  memberId: '',
  giverLabel: '',
  offeringDate: today,
  offeringSunday: calculateComingSunday(today),
  fundCode: '',
  categoryCode: '',
  amount: null,
  paymentMethod: '',
  memo: '',
});

const filteredOfferings = computed(() =>
  offerings.value.filter((offering) => {
    const matchesFund = !filters.fundCode || offering.fundCode === filters.fundCode;
    const matchesCategory = !filters.categoryCode || offering.categoryCode === filters.categoryCode;
    const matchesType = !filters.givingType || offering.givingType === filters.givingType;
    return matchesFund && matchesCategory && matchesType;
  }),
);

const filteredTotal = computed(() =>
  filteredOfferings.value.reduce((total, offering) => total + Number(offering.amount), 0),
);
const offeringPagination = usePagination(filteredOfferings);

watch(filters, () => offeringPagination.resetPage());

onMounted(async () => {
  await Promise.all([loadOfferings(), loadFunds(), loadPaymentMethods(), loadMembers(), loadAllCategories()]);
  await loadCategories();
});

async function loadOfferings() {
  error.value = '';
  savedMessage.value = '';
  try {
    offerings.value = await listOfferings();
    offeringPagination.resetPage();
    savedMessage.value = 'Refreshed';
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load offerings.';
  }
}

async function loadFunds() {
  try {
    fundOptions.value = await listReferenceData('OFFERING_FUND');
    if (!form.fundCode) {
      form.fundCode = fundOptions.value[0]?.code ?? '';
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load offering funds.';
  }
}

async function loadAllCategories() {
  try {
    allCategoryOptions.value = await listReferenceData('OFFERING_CATEGORY');
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load offering categories.';
  }
}

async function loadCategories() {
  try {
    categoryOptions.value = form.fundCode
      ? await listReferenceData('OFFERING_CATEGORY', form.fundCode)
      : [];
    if (!categoryOptions.value.some((category) => category.code === form.categoryCode)) {
      form.categoryCode = categoryOptions.value[0]?.code ?? '';
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load offering categories.';
  }
}

async function handleFundChange() {
  form.categoryCode = '';
  await loadCategories();
}

async function loadPaymentMethods() {
  try {
    paymentMethodOptions.value = await listReferenceData('PAYMENT_METHOD');
    if (!form.paymentMethod) {
      form.paymentMethod = paymentMethodOptions.value[0]?.code ?? '';
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load payment methods.';
  }
}

async function loadMembers() {
  if (form.givingType !== 'MEMBER') {
    return;
  }
  try {
    members.value = await listMembers(memberSearch.value);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load members.';
  }
}

async function saveOffering() {
  formError.value = '';
  savedMessage.value = '';
  try {
    if (form.amount === null) {
      throw new Error('Offering amount is required.');
    }
    const payload: OfferingPayload = {
      givingType: form.givingType,
      offeringDate: form.offeringDate,
      offeringSunday: form.offeringSunday,
      fundCode: form.fundCode,
      categoryCode: form.categoryCode,
      amount: form.amount,
      paymentMethod: form.paymentMethod.trim() || undefined,
      memo: form.memo.trim() || undefined,
    };

    if (form.givingType === 'MEMBER') {
      payload.memberId = form.memberId;
    } else {
      payload.giverLabel = form.giverLabel.trim();
    }

    const wasEditing = Boolean(editingOfferingId.value);
    if (wasEditing) {
      await updateOffering(editingOfferingId.value, payload);
    } else {
      await createOffering(payload);
    }
    await loadOfferings();
    resetForm();
    savedMessage.value = wasEditing ? 'Updated' : 'Saved';
  } catch (err) {
    formError.value = err instanceof Error ? err.message : 'Could not save offering.';
  }
}

async function selectOffering(offering: Offering) {
  formError.value = '';
  savedMessage.value = '';
  editingOfferingId.value = offering.id;
  form.givingType = offering.givingType;
  form.memberId = offering.memberId ?? '';
  form.giverLabel = offering.giverLabel ?? '';
  form.offeringDate = offering.offeringDate;
  form.offeringSunday = offering.offeringSunday;
  form.fundCode = offering.fundCode;
  await loadCategories();
  form.categoryCode = offering.categoryCode;
  form.amount = Number(offering.amount);
  form.paymentMethod = offering.paymentMethod ?? '';
  form.memo = offering.memo ?? '';
  if (offering.givingType === 'MEMBER') {
    memberSearch.value = offering.giverDisplayName ?? '';
    void loadMembers();
  }
}

async function deleteSelectedOffering(offering: Offering) {
  const confirmed = window.confirm(`Delete offering from ${offering.giverDisplayName || offering.giverLabel || 'giver'}?`);
  if (!confirmed) {
    return;
  }
  error.value = '';
  savedMessage.value = '';
  try {
    await deleteOffering(offering.id);
    await loadOfferings();
    if (editingOfferingId.value === offering.id) {
      resetForm();
    }
    savedMessage.value = 'Deleted';
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not delete offering.';
  }
}

function handleGivingTypeChange() {
  form.memberId = '';
  form.giverLabel = form.givingType === 'ANONYMOUS' ? 'Anonymous' : '';
  void loadMembers();
}

function syncOfferingSunday() {
  form.offeringSunday = calculateComingSunday(form.offeringDate);
}

function resetForm() {
  formError.value = '';
  savedMessage.value = '';
  editingOfferingId.value = '';
  form.givingType = 'MEMBER';
  form.memberId = '';
  form.giverLabel = '';
  form.offeringDate = today;
  form.offeringSunday = calculateComingSunday(today);
  form.fundCode = fundOptions.value[0]?.code ?? '';
  form.categoryCode = '';
  void loadCategories();
  form.amount = null;
  form.paymentMethod = paymentMethodOptions.value[0]?.code ?? '';
  form.memo = '';
  memberSearch.value = '';
  void loadMembers();
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value);
}

function labelForFund(code: string) {
  return fundOptions.value.find((fund) => fund.code === code)?.label ?? code;
}

function labelForCategory(code: string) {
  return allCategoryOptions.value.find((category) => category.code === code)?.label ?? code;
}

function labelForPaymentMethod(code?: string) {
  if (!code) {
    return '-';
  }
  return paymentMethodOptions.value.find((method) => method.code === code)?.label ?? code;
}
</script>

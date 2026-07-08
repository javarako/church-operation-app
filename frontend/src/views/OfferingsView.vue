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
          <select v-model="filters.fundCategory">
            <option value="">All funds</option>
            <option v-for="fund in fundOptions" :key="fund.code" :value="fund.code">
              {{ fund.label }}
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
                <th>Fund/category</th>
                <th>Amount</th>
                <th>Payment</th>
                <th>Linked income</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="offering in filteredOfferings" :key="offering.id">
                <td>{{ offering.offeringDate }}</td>
                <td>{{ offering.offeringSunday }}</td>
                <td>{{ offering.giverDisplayName || offering.giverLabel || '-' }}</td>
                <td>{{ labelForFund(offering.fundCategory) }}</td>
                <td>{{ formatMoney(offering.amount) }}</td>
                <td>{{ offering.paymentMethod || '-' }}</td>
                <td>{{ offering.incomeTransactionId ? 'Created' : '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <form class="panel form-grid" @submit.prevent="saveOffering">
        <h3>Record Offering</h3>
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
          <input v-model="form.offeringSunday" type="date" required />
        </label>

        <label>
          Fund/category
          <select v-model="form.fundCategory" required>
            <option value="">Select fund</option>
            <option v-for="fund in fundOptions" :key="fund.code" :value="fund.code">
              {{ fund.label }}
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
          <button type="submit">Save offering</button>
          <span v-if="savedMessage">{{ savedMessage }}</span>
        </div>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { createOffering, listOfferings, type GivingType, type Offering, type OfferingPayload } from '../api/offerings';
import { listMembers, type MemberRecord } from '../api/members';
import { listReferenceData, type ReferenceDataOption } from '../api/referenceData';

interface OfferingForm {
  givingType: GivingType;
  memberId: string;
  giverLabel: string;
  offeringDate: string;
  offeringSunday: string;
  fundCategory: string;
  amount: number | null;
  paymentMethod: string;
  memo: string;
}

const today = new Date().toISOString().slice(0, 10);
const offerings = ref<Offering[]>([]);
const fundOptions = ref<ReferenceDataOption[]>([]);
const paymentMethodOptions = ref<ReferenceDataOption[]>([]);
const members = ref<MemberRecord[]>([]);
const memberSearch = ref('');
const error = ref('');
const formError = ref('');
const savedMessage = ref('');

const filters = reactive({
  fundCategory: '',
  givingType: '' as '' | GivingType,
});

const form = reactive<OfferingForm>({
  givingType: 'MEMBER',
  memberId: '',
  giverLabel: '',
  offeringDate: today,
  offeringSunday: calculateComingSunday(today),
  fundCategory: '',
  amount: null,
  paymentMethod: '',
  memo: '',
});

const filteredOfferings = computed(() =>
  offerings.value.filter((offering) => {
    const matchesFund = !filters.fundCategory || offering.fundCategory === filters.fundCategory;
    const matchesType = !filters.givingType || offering.givingType === filters.givingType;
    return matchesFund && matchesType;
  }),
);

const filteredTotal = computed(() =>
  filteredOfferings.value.reduce((total, offering) => total + Number(offering.amount), 0),
);

onMounted(async () => {
  await Promise.all([loadOfferings(), loadFunds(), loadPaymentMethods(), loadMembers()]);
});

async function loadOfferings() {
  error.value = '';
  savedMessage.value = '';
  try {
    offerings.value = await listOfferings();
    savedMessage.value = 'Refreshed';
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load offerings.';
  }
}

async function loadFunds() {
  try {
    fundOptions.value = await listReferenceData('OFFERING_FUND_CATEGORY');
    if (!form.fundCategory) {
      form.fundCategory = fundOptions.value[0]?.code ?? '';
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load offering funds.';
  }
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
      fundCategory: form.fundCategory,
      amount: form.amount,
      paymentMethod: form.paymentMethod.trim() || undefined,
      memo: form.memo.trim() || undefined,
    };

    if (form.givingType === 'MEMBER') {
      payload.memberId = form.memberId;
    } else {
      payload.giverLabel = form.giverLabel.trim();
    }

    await createOffering(payload);
    await loadOfferings();
    resetForm();
    savedMessage.value = 'Saved';
  } catch (err) {
    formError.value = err instanceof Error ? err.message : 'Could not save offering.';
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
  form.givingType = 'MEMBER';
  form.memberId = '';
  form.giverLabel = '';
  form.offeringDate = today;
  form.offeringSunday = calculateComingSunday(today);
  form.fundCategory = fundOptions.value[0]?.code ?? '';
  form.amount = null;
  form.paymentMethod = paymentMethodOptions.value[0]?.code ?? '';
  form.memo = '';
  memberSearch.value = '';
  void loadMembers();
}

function calculateComingSunday(dateValue: string) {
  const date = new Date(`${dateValue}T00:00:00`);
  const day = date.getDay();
  const daysUntilSunday = day === 0 ? 0 : 7 - day;
  date.setDate(date.getDate() + daysUntilSunday);
  return date.toISOString().slice(0, 10);
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value);
}

function labelForFund(code: string) {
  return fundOptions.value.find((fund) => fund.code === code)?.label ?? code;
}
</script>

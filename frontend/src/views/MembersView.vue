<template>
  <section class="workspace">
    <header class="page-header">
      <div>
        <h2>Members</h2>
        <p>Register members, update contact details, and manage account roles.</p>
      </div>
      <button type="button" @click="startCreate">New member</button>
    </header>

    <div class="two-column">
      <section class="panel">
        <div class="toolbar">
          <input v-model="search" placeholder="Search members" @keyup.enter="loadMembers" />
          <button type="button" @click="loadMembers">Search</button>
        </div>

        <p v-if="error" class="error">{{ error }}</p>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Group</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="member in members"
                :key="member.id"
                :class="{ selected: selectedMember?.id === member.id }"
                @click="selectMember(member)"
              >
                <td>{{ member.displayName || member.nickname || 'Unnamed' }}</td>
                <td>{{ member.primaryEmail }}</td>
                <td>{{ member.groupCode || '-' }}</td>
                <td>{{ member.membershipStatus || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <form class="panel form-grid" @submit.prevent="saveMember">
        <h3>{{ selectedMember ? 'Member Detail' : 'New Member' }}</h3>

        <label>
          Primary email
          <input v-model="form.primaryEmail" required />
        </label>
        <label>
          Display name
          <input v-model="form.displayName" />
        </label>
        <label>
          Nickname
          <input v-model="form.nickname" />
        </label>
        <label>
          Secondary email
          <input v-model="form.secondaryEmail" />
        </label>
        <label>
          Primary phone
          <input v-model="form.primaryPhone" />
        </label>
        <label>
          Mobile phone
          <input v-model="form.mobilePhone" />
        </label>
        <label>
          Group code
          <select v-model="form.groupCode">
            <option value="">Select group</option>
            <option v-for="option in groupCodeOptions" :key="option.code" :value="option.code">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label>
          Membership status
          <select v-model="form.membershipStatus">
            <option value="">Select status</option>
            <option v-for="option in membershipStatusOptions" :key="option.code" :value="option.code">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label>
          Offering number
          <input v-model="form.offeringNumber" inputmode="numeric" pattern="[0-9]*" @input="keepOfferingNumberNumeric" />
        </label>
        <label>
          Birth date
          <input v-model="form.birthDate" type="date" />
        </label>
        <label class="wide">
          Address line 1
          <input v-model="form.mailingAddress.addressLine1" />
        </label>
        <label>
          City
          <input v-model="form.mailingAddress.city" />
        </label>
        <label>
          Province / State
          <input v-model="form.mailingAddress.provinceState" />
        </label>
        <label>
          Postal / Zip
          <input v-model="form.mailingAddress.postalZipCode" />
        </label>
        <label>
          Country
          <input v-model="form.mailingAddress.country" />
        </label>
        <fieldset class="wide">
          <legend>Roles</legend>
          <label v-for="role in roleOptions" :key="role" class="check-row">
            <input v-model="form.roles" type="checkbox" :value="role" />
            {{ role }}
          </label>
        </fieldset>
        <label class="check-row">
          <input v-model="form.active" type="checkbox" />
          Active
        </label>
        <label class="check-row">
          <input v-model="form.locked" type="checkbox" />
          Locked
        </label>
        <label class="wide">
          Notes
          <textarea v-model="form.notes" rows="4"></textarea>
        </label>

        <div class="actions wide">
          <button type="submit">{{ selectedMember ? 'Save changes' : 'Create member' }}</button>
          <span v-if="savedMessage">{{ savedMessage }}</span>
        </div>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { createMember, listMembers, updateMember, type Address, type MemberPayload, type MemberRecord } from '../api/members';
import { listReferenceData, type ReferenceDataOption } from '../api/referenceData';
import type { Role } from '../auth/authStore';

interface MemberForm extends MemberPayload {
  mailingAddress: Address;
  roles: Role[];
}

const roleOptions: Role[] = ['ADMIN', 'TREASURER', 'PASTOR', 'MEMBERSHIP', 'VIEWER', 'MEMBER'];
const members = ref<MemberRecord[]>([]);
const selectedMember = ref<MemberRecord | null>(null);
const search = ref('');
const error = ref('');
const savedMessage = ref('');
const groupCodeOptions = ref<ReferenceDataOption[]>([]);
const membershipStatusOptions = ref<ReferenceDataOption[]>([]);

const form = reactive<MemberForm>({
  primaryEmail: '',
  displayName: '',
  nickname: '',
  secondaryEmail: '',
  primaryPhone: '',
  mobilePhone: '',
  groupCode: '',
  membershipStatus: '',
  offeringNumber: '',
  birthDate: '',
  mailingAddress: {},
  roles: ['MEMBER'],
  active: true,
  locked: false,
  notes: '',
});

onMounted(async () => {
  await Promise.all([loadReferenceData(), loadMembers()]);
});

async function loadReferenceData() {
  try {
    const [groups, statuses] = await Promise.all([
      listReferenceData('GROUP_CODE'),
      listReferenceData('MEMBERSHIP_STATUS'),
    ]);
    groupCodeOptions.value = groups;
    membershipStatusOptions.value = statuses;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load reference data.';
  }
}

async function loadMembers() {
  error.value = '';
  try {
    members.value = await listMembers(search.value);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load members.';
  }
}

function startCreate() {
  selectedMember.value = null;
  applyToForm({
    primaryEmail: '',
    displayName: '',
    nickname: '',
    secondaryEmail: '',
    primaryPhone: '',
    mobilePhone: '',
    groupCode: '',
    membershipStatus: '',
    offeringNumber: '',
    birthDate: '',
    mailingAddress: {},
    roles: ['MEMBER'],
    active: true,
    locked: false,
    notes: '',
  });
}

function selectMember(member: MemberRecord) {
  selectedMember.value = member;
  applyToForm(member);
}

async function saveMember() {
  error.value = '';
  savedMessage.value = '';
  try {
    const payload = cleanPayload(form);
    const saved = selectedMember.value
      ? await updateMember(selectedMember.value.id, payload)
      : await createMember(payload);
    selectedMember.value = saved;
    applyToForm(saved);
    savedMessage.value = 'Saved';
    await loadMembers();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not save member.';
  }
}

function applyToForm(member: MemberPayload) {
  form.primaryEmail = member.primaryEmail ?? '';
  form.displayName = member.displayName ?? '';
  form.nickname = member.nickname ?? '';
  form.secondaryEmail = member.secondaryEmail ?? '';
  form.primaryPhone = member.primaryPhone ?? '';
  form.mobilePhone = member.mobilePhone ?? '';
  form.groupCode = member.groupCode ?? '';
  form.membershipStatus = member.membershipStatus ?? '';
  form.offeringNumber = member.offeringNumber ?? '';
  form.birthDate = member.birthDate ?? '';
  form.mailingAddress = { ...(member.mailingAddress ?? {}) };
  form.roles = [...(member.roles ?? ['MEMBER'])];
  form.active = member.active ?? true;
  form.locked = member.locked ?? false;
  form.notes = member.notes ?? '';
}

function cleanPayload(member: MemberPayload): MemberPayload {
  return {
    ...member,
    mailingAddress: { ...(member.mailingAddress ?? {}) },
    roles: member.roles?.length ? member.roles : ['MEMBER'],
  };
}

function keepOfferingNumberNumeric() {
  form.offeringNumber = form.offeringNumber?.replace(/\D/g, '') ?? '';
}
</script>

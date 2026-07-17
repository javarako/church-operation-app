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
                <th aria-label="Actions"></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="member in memberPagination.paginatedRows.value"
                :key="member.id"
                :class="{ selected: selectedMember?.id === member.id }"
                @click="selectMember(member)"
              >
                <td>
                  <div class="member-name-cell">
                    <MemberAvatar :member-id="member.id" :name="memberDisplayName(member)" :size="38" />
                    <span>{{ memberDisplayName(member) }}</span>
                  </div>
                </td>
                <td>{{ member.primaryEmail }}</td>
                <td>{{ referenceLabel(groupCodeOptions, member.groupCode) }}</td>
                <td>{{ referenceLabel(membershipStatusOptions, member.membershipStatus) }}</td>
                <td class="row-actions">
                  <button
                    type="button"
                    class="icon-button danger"
                    :disabled="isBootstrapAdmin(member)"
                    :title="isBootstrapAdmin(member) ? 'System Administrator cannot be deleted' : 'Delete member'"
                    :aria-label="`Delete member ${memberDisplayName(member)}`"
                    @click.stop="deleteSelectedMember(member)"
                  >
                    <Trash2 :size="17" aria-hidden="true" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationControls
          :current-page="memberPagination.currentPage.value"
          :page-count="memberPagination.pageCount.value"
          :page-size="memberPagination.pageSize.value"
          :total-rows="memberPagination.totalRows.value"
          :start-row="memberPagination.startRow.value"
          :end-row="memberPagination.endRow.value"
          @change-page="memberPagination.goToPage"
        />
      </section>

      <form class="panel form-grid" @submit.prevent="saveMember">
        <h3>{{ selectedMember ? 'Member Detail' : 'New Member' }}</h3>
        <MemberImageEditor
          class="wide"
          :member-id="selectedMember?.id"
          :name="form.displayName || form.nickname || 'Unnamed'"
        />

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
        <details class="wide committee-picker">
          <summary>Committees ({{ form.committeeCodes.length }})</summary>
          <div class="committee-options">
            <label v-for="option in visibleCommitteeOptions" :key="option.code" class="check-row">
              <input v-model="form.committeeCodes" type="checkbox" :value="option.code" />
              {{ option.label }}
            </label>
            <span v-if="visibleCommitteeOptions.length === 0">No active committees</span>
          </div>
        </details>
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
          Login enabled
        </label>
        <label class="check-row">
          <input v-model="form.locked" type="checkbox" />
          Login locked
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
import { computed, onMounted, reactive, ref } from 'vue';
import { createMember, deleteMember, listMembers, updateMember, type Address, type MemberPayload, type MemberRecord } from '../api/members';
import { Trash2 } from '@lucide/vue';
import { listReferenceData, type ReferenceDataOption } from '../api/referenceData';
import type { Role } from '../auth/authStore';
import PaginationControls from '../components/PaginationControls.vue';
import MemberAvatar from '../components/MemberAvatar.vue';
import MemberImageEditor from '../components/MemberImageEditor.vue';
import { usePagination } from '../composables/usePagination';

interface MemberForm extends MemberPayload {
  mailingAddress: Address;
  committeeCodes: string[];
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
const committeeCodeOptions = ref<ReferenceDataOption[]>([]);
const memberPagination = usePagination(members);

const form = reactive<MemberForm>({
  primaryEmail: '',
  displayName: '',
  nickname: '',
  secondaryEmail: '',
  primaryPhone: '',
  mobilePhone: '',
  groupCode: '',
  membershipStatus: '',
  committeeCodes: [],
  offeringNumber: '',
  birthDate: '',
  mailingAddress: {},
  roles: ['MEMBER'],
  active: true,
  locked: false,
  notes: '',
});

const visibleCommitteeOptions = computed<ReferenceDataOption[]>(() => {
  const activeOptions = [...committeeCodeOptions.value];
  const activeCodes = new Set(activeOptions.map((option) => option.code));
  const inactiveAssignments = form.committeeCodes
    .filter((code) => !activeCodes.has(code))
    .map((code) => ({
      id: `inactive-${code}`,
      type: 'COMMITTEE_CODE' as const,
      code,
      label: `${code} (Inactive)`,
      sortOrder: Number.MAX_SAFE_INTEGER,
      active: false,
    }));
  return [...activeOptions, ...inactiveAssignments];
});

onMounted(async () => {
  await Promise.all([loadReferenceData(), loadMembers()]);
});

async function loadReferenceData() {
  try {
    const [groups, statuses, committees] = await Promise.all([
      listReferenceData('GROUP_CODE'),
      listReferenceData('MEMBERSHIP_STATUS'),
      listReferenceData('COMMITTEE_CODE'),
    ]);
    groupCodeOptions.value = groups.filter((option) => option.active);
    membershipStatusOptions.value = statuses.filter((option) => option.active);
    committeeCodeOptions.value = committees.filter((option) => option.active);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load reference data.';
  }
}

async function loadMembers() {
  error.value = '';
  try {
    members.value = await listMembers(search.value);
    memberPagination.resetPage();
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
    committeeCodes: [],
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

async function deleteSelectedMember(member: MemberRecord) {
  if (isBootstrapAdmin(member)) {
    return;
  }
  if (!window.confirm(`Delete member ${memberDisplayName(member)}? This cannot be undone.`)) {
    return;
  }
  error.value = '';
  savedMessage.value = '';
  try {
    await deleteMember(member.id);
    if (selectedMember.value?.id === member.id) {
      startCreate();
    }
    await loadMembers();
    savedMessage.value = 'Deleted';
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not delete member.';
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
  form.committeeCodes = [...(member.committeeCodes ?? [])];
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
    committeeCodes: [...(member.committeeCodes ?? [])],
    roles: member.roles?.length ? member.roles : ['MEMBER'],
  };
}

function keepOfferingNumberNumeric() {
  form.offeringNumber = form.offeringNumber?.replace(/\D/g, '') ?? '';
}

function memberDisplayName(member: MemberRecord) {
  return member.displayName || member.nickname || 'Unnamed';
}

function referenceLabel(options: ReferenceDataOption[], code?: string) {
  if (!code) {
    return '-';
  }
  return options.find((option) => option.code === code)?.label ?? code;
}

function isBootstrapAdmin(member: MemberRecord) {
  return member.primaryEmail.trim().toLowerCase() === 'admin';
}
</script>

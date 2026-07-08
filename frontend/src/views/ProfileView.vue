<template>
  <section class="workspace">
    <header class="page-header">
      <div>
        <h2>My Profile</h2>
        <p>Update your own contact details and household notes.</p>
      </div>
    </header>

    <form class="panel form-grid profile-form" @submit.prevent="saveProfile">
      <label>
        Primary email
        <input v-model="form.primaryEmail" disabled />
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
      <label class="wide">
        Notes
        <textarea v-model="form.notes" rows="4"></textarea>
      </label>

      <p v-if="error" class="error wide">{{ error }}</p>
      <div class="actions wide">
        <button type="submit" :disabled="!memberId">Save profile</button>
        <span v-if="savedMessage">{{ savedMessage }}</span>
      </div>
    </form>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { getMyProfile, updateMyProfile, type Address, type MemberPayload } from '../api/members';

interface ProfileForm extends MemberPayload {
  mailingAddress: Address;
}

const memberId = ref('');
const error = ref('');
const savedMessage = ref('');
const form = reactive<ProfileForm>({
  primaryEmail: '',
  displayName: '',
  nickname: '',
  secondaryEmail: '',
  primaryPhone: '',
  mobilePhone: '',
  mailingAddress: {},
  notes: '',
});

onMounted(loadProfile);

async function loadProfile() {
  try {
    const profile = await getMyProfile();
    memberId.value = profile.id;
    Object.assign(form, {
      primaryEmail: profile.primaryEmail,
      displayName: profile.displayName ?? '',
      nickname: profile.nickname ?? '',
      secondaryEmail: profile.secondaryEmail ?? '',
      primaryPhone: profile.primaryPhone ?? '',
      mobilePhone: profile.mobilePhone ?? '',
      mailingAddress: { ...(profile.mailingAddress ?? {}) },
      notes: profile.notes ?? '',
    });
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not load profile.';
  }
}

async function saveProfile() {
  if (!memberId.value) {
    return;
  }
  error.value = '';
  savedMessage.value = '';
  try {
    await updateMyProfile({ ...form, mailingAddress: { ...(form.mailingAddress ?? {}) } });
    savedMessage.value = 'Saved';
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Could not save profile.';
  }
}
</script>

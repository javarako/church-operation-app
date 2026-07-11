<template>
  <main class="auth-page">
    <form class="auth-panel" @submit.prevent="changePassword">
      <h1>Change Password</h1>
      <label>
        Current password
        <input v-model="currentPassword" type="password" autocomplete="current-password" />
      </label>
      <label>
        New password
        <input v-model="newPassword" type="password" autocomplete="new-password" />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit">Update password</button>
    </form>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { postEmpty } from '../api/http';
import { authState, setCurrentUser } from '../auth/authStore';

const router = useRouter();
const currentPassword = ref('');
const newPassword = ref('');
const error = ref('');

async function changePassword() {
  error.value = '';
  if (!authState.currentUser) {
    await router.push('/login');
    return;
  }
  try {
    await postEmpty('/api/auth/change-password', {
      username: authState.currentUser.primaryEmail,
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    });
    setCurrentUser({ ...authState.currentUser, mustChangePassword: false });
    await router.push('/');
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Password change failed.';
  }
}
</script>

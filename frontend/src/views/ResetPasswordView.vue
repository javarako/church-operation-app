<template>
  <main class="auth-page">
    <form class="auth-panel" @submit.prevent="resetPassword">
      <h1>Set new password</h1>
      <template v-if="!complete">
        <label>
          New password
          <input v-model="newPassword" type="password" autocomplete="new-password" minlength="8" required />
        </label>
        <label>
          Confirm password
          <input v-model="confirmPassword" type="password" autocomplete="new-password" minlength="8" required />
        </label>
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit">Set new password</button>
      </template>
      <template v-else>
        <p class="success">Your password has been reset.</p>
        <RouterLink to="/login">Return to sign in</RouterLink>
      </template>
    </form>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import { postEmpty } from '../api/http';

const route = useRoute();
const newPassword = ref('');
const confirmPassword = ref('');
const error = ref('');
const complete = ref(false);

async function resetPassword() {
  error.value = '';
  const token = typeof route.query.token === 'string' ? route.query.token : '';
  if (!token) {
    error.value = 'This password reset link is invalid or expired.';
    return;
  }
  if (newPassword.value !== confirmPassword.value) {
    error.value = 'Passwords do not match.';
    return;
  }

  try {
    await postEmpty('/api/auth/reset-password', {
      token,
      newPassword: newPassword.value,
    });
    complete.value = true;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unable to reset password.';
  }
}
</script>

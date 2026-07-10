<template>
  <main class="auth-page">
    <form class="auth-panel" @submit.prevent="submitRequest">
      <h1>Reset password</h1>
      <p>Enter your primary email to receive a password reset link.</p>
      <label>
        Primary email
        <input v-model="email" type="email" autocomplete="email" required />
      </label>
      <p v-if="message" class="success">{{ message }}</p>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit">Send reset link</button>
      <RouterLink to="/login">Back to sign in</RouterLink>
    </form>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { postJson } from '../api/http';

const email = ref('');
const message = ref('');
const error = ref('');

async function submitRequest() {
  message.value = '';
  error.value = '';
  try {
    const response = await postJson<{ email: string }, { message: string }>('/api/auth/forgot-password', {
      email: email.value,
    });
    message.value = response.message;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unable to request a password reset.';
  }
}
</script>

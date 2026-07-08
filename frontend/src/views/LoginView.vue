<template>
  <main class="auth-page">
    <form class="auth-panel" @submit.prevent="login">
      <h1>Church Operations</h1>
      <label>
        Login ID
        <input v-model="username" autocomplete="username" />
      </label>
      <label>
        Password
        <input v-model="password" type="password" autocomplete="current-password" />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit">Sign in</button>
    </form>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { postJson } from '../api/http';
import { setCurrentUser, type CurrentUser } from '../auth/authStore';

const router = useRouter();
const username = ref('admin');
const password = ref('password');
const error = ref('');

async function login() {
  error.value = '';
  try {
    const user = await postJson<{ username: string; password: string }, CurrentUser>('/api/auth/login', {
      username: username.value,
      password: password.value,
    });
    setCurrentUser(user);
    await router.push(user.mustChangePassword ? '/change-password' : '/');
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Login failed.';
  }
}
</script>

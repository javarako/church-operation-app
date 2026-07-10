<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <img v-if="churchInfo?.logPath" :src="churchInfo.logPath" :alt="`${churchInfo.name} logo`" />
        <div v-else class="sidebar-logo-fallback">CO</div>
        <h1>Church Operations</h1>
      </div>

      <nav class="sidebar-nav" aria-label="Main menu">
        <RouterLink to="/">Dashboard</RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'MEMBERSHIP'])" to="/members">Members</RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'TREASURER'])" to="/offerings">Offerings</RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'TREASURER'])" to="/finance">Finance</RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'TREASURER'])" to="/budgets">Budgets</RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'TREASURER', 'MEMBERSHIP'])" to="/reference-data">Reference Data</RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'TREASURER', 'PASTOR', 'VIEWER'])" to="/reports">Reports</RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'MEMBER'])" to="/profile">My Profile</RouterLink>
      </nav>
      <div class="sidebar-spacer"></div>
      <button type="button" class="sidebar-logout" @click="logout">Logout</button>
    </aside>
    <section class="content">
      <slot />
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getChurchInformation, type ChurchInformation } from '../api/churchInformation';
import { postEmpty } from '../api/http';
import { authState, setCurrentUser, type Role } from '../auth/authStore';

const churchInfo = ref<ChurchInformation | null>(null);
const router = useRouter();

onMounted(async () => {
  try {
    churchInfo.value = await getChurchInformation();
  } catch {
    churchInfo.value = null;
  }
});

function hasAny(roles: Role[]) {
  return authState.currentUser?.roles.some((role) => roles.includes(role)) ?? false;
}

async function logout() {
  try {
    await postEmpty('/api/auth/logout', {});
  } finally {
    setCurrentUser(null);
    await router.push('/login');
  }
}
</script>

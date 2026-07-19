<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <img v-if="churchInfo?.logPath" :src="churchInfo.logPath" :alt="`${churchInfo.name} logo`" />
        <div v-else class="sidebar-logo-fallback">CO</div>
        <h1>Church Operations</h1>
      </div>

      <nav class="sidebar-nav" aria-label="Main menu">
        <RouterLink to="/"><House :size="20" aria-hidden="true" /><span>Dashboard</span></RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'MEMBERSHIP'])" to="/members"><Users :size="20" aria-hidden="true" /><span>Members</span></RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'TREASURER'])" to="/offerings"><HandHeart :size="20" aria-hidden="true" /><span>Offerings</span></RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'TREASURER'])" to="/finance"><Landmark :size="20" aria-hidden="true" /><span>Finance</span></RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'TREASURER'])" to="/budgets"><ChartPie :size="20" aria-hidden="true" /><span>Budgets</span></RouterLink>
        <RouterLink v-if="hasAny(['ADMIN'])" to="/reference-data"><BookOpen :size="20" aria-hidden="true" /><span>Reference Data</span></RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'TREASURER', 'PASTOR', 'VIEWER'])" to="/reports"><ChartColumn :size="20" aria-hidden="true" /><span>Reports</span></RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'MEMBER'])" to="/profile"><UserRound :size="20" aria-hidden="true" /><span>My Profile</span></RouterLink>
        <RouterLink v-if="hasAny(['ADMIN'])" to="/system-administration"><Settings :size="20" aria-hidden="true" /><span>System Administration</span></RouterLink>
      </nav>
      <div class="sidebar-spacer"></div>
      <button type="button" class="sidebar-logout" @click="logout"><LogOut :size="20" aria-hidden="true" /><span>Logout</span></button>
    </aside>
    <section class="content">
      <slot />
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { BookOpen, ChartColumn, ChartPie, HandHeart, House, Landmark, LogOut, Settings, UserRound, Users } from '@lucide/vue';
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

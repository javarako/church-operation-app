<template>
  <div class="layout">
    <aside class="sidebar">
      <h1>Church Operations</h1>
      <RouterLink to="/">Dashboard</RouterLink>
      <RouterLink v-if="hasAny(['ADMIN', 'MEMBERSHIP'])" to="/members">Members</RouterLink>
      <RouterLink v-if="hasAny(['ADMIN', 'TREASURER'])" to="/offerings">Offerings</RouterLink>
      <RouterLink v-if="hasAny(['ADMIN', 'TREASURER'])" to="/finance">Finance</RouterLink>
      <RouterLink v-if="hasAny(['ADMIN', 'TREASURER'])" to="/budgets">Budgets</RouterLink>
      <RouterLink v-if="hasAny(['ADMIN', 'TREASURER', 'MEMBERSHIP'])" to="/reference-data">Reference Data</RouterLink>
      <RouterLink v-if="hasAny(['ADMIN', 'TREASURER', 'PASTOR', 'VIEWER'])" to="/reports">Reports</RouterLink>
      <RouterLink v-if="hasAny(['ADMIN', 'MEMBER'])" to="/profile">My Profile</RouterLink>
    </aside>
    <section class="content">
      <slot />
    </section>
  </div>
</template>

<script setup lang="ts">
import { authState, type Role } from '../auth/authStore';

function hasAny(roles: Role[]) {
  return authState.currentUser?.roles.some((role) => roles.includes(role)) ?? false;
}
</script>

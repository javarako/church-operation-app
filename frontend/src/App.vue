<template>
  <RouterView v-slot="{ Component, route }">
    <component :is="Component" v-if="isStandaloneRoute(route.path)" />
    <AppLayout v-else>
      <component :is="Component" />
    </AppLayout>
  </RouterView>
</template>

<script setup lang="ts">
import AppLayout from './layouts/AppLayout.vue';

function isStandaloneRoute(path: string) {
  return ['/login', '/change-password', '/forgot-password', '/reset-password'].includes(path);
}
</script>

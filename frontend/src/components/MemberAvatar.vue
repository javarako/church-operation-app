<template>
  <span
    class="member-avatar"
    :style="{ '--avatar-size': `${size}px` }"
    :aria-label="`${displayName} profile image`"
  >
    <img v-if="imageUrl" :src="imageUrl" alt="" />
    <span v-else aria-hidden="true">{{ initials }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { getMemberImage, getSelfImage } from '../api/members';

const props = withDefaults(defineProps<{
  memberId?: string;
  name?: string;
  self?: boolean;
  size?: number;
  refreshKey?: number;
}>(), {
  memberId: '',
  name: '',
  self: false,
  size: 38,
  refreshKey: 0,
});

const imageUrl = ref('');
const displayName = computed(() => props.name.trim() || 'Unnamed member');
const initials = computed(() => displayName.value
  .split(/\s+/)
  .filter(Boolean)
  .slice(0, 2)
  .map((part) => part[0].toUpperCase())
  .join('') || '?');

watch(
  [() => props.memberId, () => props.self, () => props.refreshKey],
  async () => {
    revokeCurrentUrl();
    if (!props.memberId) {
      return;
    }
    try {
      const blob = props.self ? await getSelfImage() : await getMemberImage(props.memberId);
      imageUrl.value = URL.createObjectURL(blob);
    } catch {
      imageUrl.value = '';
    }
  },
  { immediate: true },
);

onBeforeUnmount(revokeCurrentUrl);

function revokeCurrentUrl() {
  if (imageUrl.value && typeof URL.revokeObjectURL === 'function') {
    URL.revokeObjectURL(imageUrl.value);
  }
  imageUrl.value = '';
}
</script>

<style scoped>
.member-avatar {
  width: var(--avatar-size);
  height: var(--avatar-size);
  flex: 0 0 var(--avatar-size);
  display: inline-grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid #cbd5df;
  border-radius: 50%;
  background: #e9eef3;
  color: #344054;
  font-size: calc(var(--avatar-size) * 0.34);
  font-weight: 700;
}

.member-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>

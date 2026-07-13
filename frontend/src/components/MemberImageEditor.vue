<template>
  <div class="member-image-editor">
    <MemberAvatar
      :member-id="memberId"
      :name="name"
      :self="self"
      :size="96"
      :refresh-key="refreshKey"
    />
    <div class="member-image-actions">
      <input
        ref="fileInput"
        class="visually-hidden"
        type="file"
        accept="image/png,image/jpeg,image/webp,.png,.jpg,.jpeg,.webp"
        aria-label="Choose member image"
        :disabled="disabled || busy"
        @change="uploadSelected"
      />
      <button type="button" class="secondary" :disabled="disabled || busy" @click="fileInput?.click()">
        <Upload :size="17" aria-hidden="true" />
        <span>Replace image</span>
      </button>
      <button
        type="button"
        class="icon-button danger"
        aria-label="Remove image"
        title="Remove image"
        :disabled="disabled || busy"
        @click="remove"
      >
        <Trash2 :size="17" aria-hidden="true" />
      </button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { Trash2, Upload } from '@lucide/vue';
import {
  removeMemberImage,
  removeSelfImage,
  replaceMemberImage,
  replaceSelfImage,
} from '../api/members';
import MemberAvatar from './MemberAvatar.vue';

const props = withDefaults(defineProps<{
  memberId?: string;
  name?: string;
  self?: boolean;
}>(), {
  memberId: '',
  name: '',
  self: false,
});

const emit = defineEmits<{ updated: [] }>();
const fileInput = ref<HTMLInputElement | null>(null);
const refreshKey = ref(0);
const busy = ref(false);
const error = ref('');
const disabled = computed(() => !props.memberId);

async function uploadSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) {
    return;
  }
  error.value = '';
  if (file.size > 5 * 1024 * 1024) {
    error.value = 'Member image must not exceed 5 MB.';
    input.value = '';
    return;
  }
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
    error.value = 'Only JPEG, PNG, and WebP images are supported.';
    input.value = '';
    return;
  }

  busy.value = true;
  try {
    if (props.self) {
      await replaceSelfImage(file);
    } else {
      await replaceMemberImage(props.memberId, file);
    }
    refreshKey.value += 1;
    emit('updated');
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Could not replace member image.';
  } finally {
    busy.value = false;
    input.value = '';
  }
}

async function remove() {
  error.value = '';
  busy.value = true;
  try {
    if (props.self) {
      await removeSelfImage();
    } else {
      await removeMemberImage(props.memberId);
    }
    refreshKey.value += 1;
    emit('updated');
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Could not remove member image.';
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.member-image-editor {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  align-items: center;
  gap: 14px;
}

.member-image-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.member-image-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
}

.member-image-actions .secondary {
  min-height: 36px;
  border: 1px solid #c8d0d9;
  border-radius: 6px;
  padding: 0 12px;
  background: white;
  color: #22577a;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.member-image-editor .error {
  grid-column: 1 / -1;
  margin: 0;
}
</style>

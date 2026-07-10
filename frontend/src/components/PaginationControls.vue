<template>
  <div class="pagination-controls">
    <span>
      Showing {{ startRow }}-{{ endRow }} of {{ totalRows }}
    </span>
    <div>
      <button type="button" :disabled="currentPage <= 1" @click="$emit('change-page', currentPage - 1)">
        Previous
      </button>
      <strong>Page {{ currentPage }} of {{ pageCount }}</strong>
      <button type="button" :disabled="currentPage >= pageCount" @click="$emit('change-page', currentPage + 1)">
        Next
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  currentPage: number;
  pageCount: number;
  pageSize: number;
  totalRows: number;
  startRow: number;
  endRow: number;
}>();

defineEmits<{
  'change-page': [page: number];
}>();
</script>

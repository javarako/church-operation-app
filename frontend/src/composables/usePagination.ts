import { computed, onMounted, ref, watch, type ComputedRef, type Ref } from 'vue';
import { getChurchInformation } from '../api/churchInformation';

const fallbackPageSize = 20;

export function usePagination<T>(rows: Ref<T[]> | ComputedRef<T[]>) {
  const currentPage = ref(1);
  const pageSize = ref(fallbackPageSize);

  const totalRows = computed(() => rows.value.length);
  const pageCount = computed(() => Math.max(1, Math.ceil(totalRows.value / pageSize.value)));
  const startRow = computed(() => (totalRows.value === 0 ? 0 : (currentPage.value - 1) * pageSize.value + 1));
  const endRow = computed(() => Math.min(currentPage.value * pageSize.value, totalRows.value));
  const paginatedRows = computed(() => {
    const start = (currentPage.value - 1) * pageSize.value;
    return rows.value.slice(start, start + pageSize.value);
  });

  watch([totalRows, pageCount], () => {
    if (currentPage.value > pageCount.value) {
      currentPage.value = pageCount.value;
    }
  });

  onMounted(async () => {
    try {
      const information = await getChurchInformation();
      if (information.listPageSize > 0) {
        pageSize.value = information.listPageSize;
      }
    } catch {
      pageSize.value = fallbackPageSize;
    }
  });

  function goToPage(page: number) {
    currentPage.value = Math.min(Math.max(page, 1), pageCount.value);
  }

  function resetPage() {
    currentPage.value = 1;
  }

  return {
    currentPage,
    pageSize,
    totalRows,
    pageCount,
    startRow,
    endRow,
    paginatedRows,
    goToPage,
    resetPage,
  };
}

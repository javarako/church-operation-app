import { reactive } from 'vue';
import { getChurchInformation, type ChurchInformation } from '../api/churchInformation';

export const churchInformationState = reactive<{
  value: ChurchInformation | null;
  loading: boolean;
}>({
  value: null,
  loading: false,
});

let pendingLoad: Promise<ChurchInformation> | null = null;

export function applyChurchInformation(value: ChurchInformation) {
  churchInformationState.value = value;
  return value;
}

export async function loadChurchInformation(force = false) {
  if (churchInformationState.value && !force) {
    return churchInformationState.value;
  }
  if (pendingLoad) {
    return pendingLoad;
  }

  churchInformationState.loading = true;
  pendingLoad = getChurchInformation()
    .then(applyChurchInformation)
    .finally(() => {
      churchInformationState.loading = false;
      pendingLoad = null;
    });
  return pendingLoad;
}

export function resetChurchInformationStore() {
  churchInformationState.value = null;
  churchInformationState.loading = false;
  pendingLoad = null;
}

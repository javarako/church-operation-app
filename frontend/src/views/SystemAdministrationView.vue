<template>
  <section class="workspace administration-page">
    <header class="page-header">
      <div>
        <h2>System Administration</h2>
        <p>Protect and recover the complete church database.</p>
      </div>
    </header>

    <div class="administration-tabs" role="tablist" aria-label="Data management mode">
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'backup'"
        :class="{ active: activeTab === 'backup' }"
        @click="activeTab = 'backup'"
      >
        <DatabaseBackup :size="18" aria-hidden="true" />
        Full Backup
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'restore'"
        :class="{ active: activeTab === 'restore' }"
        @click="activeTab = 'restore'"
      >
        <DatabaseZap :size="18" aria-hidden="true" />
        Full Restore
      </button>
    </div>

    <p v-if="error" class="error administration-message" role="alert">{{ error }}</p>
    <p v-if="success" class="success administration-message">{{ success }}</p>

    <form v-if="activeTab === 'backup'" class="panel administration-form" @submit.prevent="runBackup">
      <header class="administration-section-header">
        <div>
          <h3>Complete database backup</h3>
          <p>Members, offerings, finances, settings, receipts, and member images are included.</p>
        </div>
        <ShieldCheck :size="26" aria-hidden="true" />
      </header>

      <div class="administration-fields">
        <label>
          Backup password
          <input v-model="backupPassword" :type="showBackupPasswords ? 'text' : 'password'" autocomplete="new-password" />
        </label>
        <label>
          Confirm backup password
          <input v-model="backupConfirmation" :type="showBackupPasswords ? 'text' : 'password'" autocomplete="new-password" />
        </label>
      </div>
      <label class="check-row administration-toggle">
        <input v-model="showBackupPasswords" type="checkbox" />
        Show passwords
      </label>
      <div class="administration-actions">
        <button type="submit" :disabled="busy">
          <Download :size="18" aria-hidden="true" />
          {{ busy ? 'Preparing backup...' : 'Download full backup' }}
        </button>
      </div>
    </form>

    <section v-else class="panel administration-form restore-workflow">
      <header class="administration-section-header danger-heading">
        <div>
          <h3>Complete database restore</h3>
          <p>The current database will be replaced only after validation and a safety backup.</p>
        </div>
        <TriangleAlert :size="26" aria-hidden="true" />
      </header>

      <section class="restore-step">
        <div class="restore-step-number">1</div>
        <div class="restore-step-content">
          <h4>Validate backup</h4>
          <div class="administration-fields">
            <label>
              Backup ZIP file
              <input type="file" accept=".zip,application/zip" @change="selectRestoreFile" />
            </label>
            <label>
              Restore password
              <input v-model="restorePassword" type="password" autocomplete="off" />
            </label>
          </div>
          <button type="button" class="secondary-command" :disabled="busy || !restoreFile" @click="validateRestore">
            <FileCheck2 :size="18" aria-hidden="true" />
            {{ busy ? 'Validating...' : 'Validate backup' }}
          </button>
          <div v-if="operation" class="validation-summary" aria-label="Validated backup summary">
            <span><strong>{{ operation.collectionCount }}</strong> collections</span>
            <span><strong>{{ operation.documentCount }}</strong> documents</span>
            <span><strong>{{ operation.indexCount }}</strong> indexes</span>
            <small>Expires {{ formatExpiry(operation.expiresAt) }}</small>
          </div>
        </div>
      </section>

      <section class="restore-step" :class="{ unavailable: !operation }">
        <div class="restore-step-number">2</div>
        <div class="restore-step-content">
          <h4>Download safety backup</h4>
          <div class="administration-fields">
            <label>
              Safety backup password
              <input v-model="safetyPassword" type="password" autocomplete="new-password" :disabled="!operation" />
            </label>
            <label>
              Confirm safety backup password
              <input v-model="safetyConfirmation" type="password" autocomplete="new-password" :disabled="!operation" />
            </label>
          </div>
          <button type="button" class="secondary-command" :disabled="busy || !operation" @click="runSafetyBackup">
            <Download :size="18" aria-hidden="true" />
            Download safety backup
          </button>
          <span v-if="safetyDownloaded" class="completion-mark"><CheckCircle2 :size="17" /> Safety backup downloaded</span>
        </div>
      </section>

      <section class="restore-step destructive-step" :class="{ unavailable: !safetyDownloaded }">
        <div class="restore-step-number">3</div>
        <div class="restore-step-content">
          <h4>Replace current database</h4>
          <label class="confirmation-field">
            Restore confirmation
            <input
              v-model="restoreConfirmation"
              autocomplete="off"
              placeholder="RESTORE FULL DATABASE"
              :disabled="!safetyDownloaded"
            />
          </label>
          <button
            type="button"
            class="danger-command"
            :disabled="busy || !canExecuteRestore"
            @click="runRestore"
          >
            <DatabaseZap :size="18" aria-hidden="true" />
            {{ busy ? 'Restoring database...' : 'Restore full database' }}
          </button>
        </div>
      </section>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  CheckCircle2,
  DatabaseBackup,
  DatabaseZap,
  Download,
  FileCheck2,
  ShieldCheck,
  TriangleAlert,
} from '@lucide/vue';
import { authState, setCurrentUser } from '../auth/authStore';
import {
  downloadFullBackup,
  downloadSafetyBackup,
  executeFullRestore,
  validateFullRestore,
  type DataOperationResponse,
} from '../api/dataManagement';

const router = useRouter();
const activeTab = ref<'backup' | 'restore'>('backup');
const busy = ref(false);
const error = ref('');
const success = ref('');

const backupPassword = ref('');
const backupConfirmation = ref('');
const showBackupPasswords = ref(false);

const restoreFile = ref<File | null>(null);
const restorePassword = ref('');
const operation = ref<DataOperationResponse | null>(null);
const safetyPassword = ref('');
const safetyConfirmation = ref('');
const safetyDownloaded = ref(false);
const restoreConfirmation = ref('');

const canExecuteRestore = computed(() =>
  safetyDownloaded.value && restoreConfirmation.value === 'RESTORE FULL DATABASE',
);

async function runBackup() {
  clearMessages();
  if (!backupPassword.value) {
    error.value = 'Backup password is required.';
    return;
  }
  if (backupPassword.value !== backupConfirmation.value) {
    error.value = 'Backup passwords must match.';
    return;
  }
  busy.value = true;
  try {
    downloadBlob(await downloadFullBackup(backupPassword.value), 'church-full-backup.zip');
    success.value = 'Full backup downloaded.';
  } catch (reason) {
    error.value = message(reason, 'Could not create the full backup.');
  } finally {
    backupPassword.value = '';
    backupConfirmation.value = '';
    busy.value = false;
  }
}

function selectRestoreFile(event: Event) {
  restoreFile.value = (event.target as HTMLInputElement).files?.[0] ?? null;
  operation.value = null;
  safetyDownloaded.value = false;
  restoreConfirmation.value = '';
}

async function validateRestore() {
  clearMessages();
  if (!restoreFile.value || !restorePassword.value) {
    error.value = 'Choose a backup ZIP file and enter its password.';
    return;
  }
  busy.value = true;
  try {
    operation.value = await validateFullRestore(restoreFile.value, restorePassword.value);
    safetyDownloaded.value = false;
    success.value = 'Backup archive validated.';
  } catch (reason) {
    error.value = message(reason, 'Could not validate the backup archive.');
  } finally {
    restorePassword.value = '';
    busy.value = false;
  }
}

async function runSafetyBackup() {
  clearMessages();
  if (!operation.value) return;
  if (!safetyPassword.value) {
    error.value = 'Safety backup password is required.';
    return;
  }
  if (safetyPassword.value !== safetyConfirmation.value) {
    error.value = 'Safety backup passwords must match.';
    return;
  }
  busy.value = true;
  try {
    const blob = await downloadSafetyBackup(operation.value.id, safetyPassword.value);
    downloadBlob(blob, 'pre-restore-safety-backup.zip');
    safetyDownloaded.value = true;
    success.value = 'Safety backup downloaded. Verify the file before restoring.';
  } catch (reason) {
    error.value = message(reason, 'Could not create the safety backup.');
  } finally {
    safetyPassword.value = '';
    safetyConfirmation.value = '';
    busy.value = false;
  }
}

async function runRestore() {
  clearMessages();
  if (!operation.value || !canExecuteRestore.value) return;
  busy.value = true;
  try {
    const result = await executeFullRestore(operation.value.id, restoreConfirmation.value);
    if (result.status === 'COMPLETE') {
      setCurrentUser(null);
      await router.push('/login');
    } else {
      operation.value = result;
    }
  } catch (reason) {
    error.value = message(reason, 'Restore failed. The application remains in maintenance mode.');
  } finally {
    restoreConfirmation.value = '';
    busy.value = false;
  }
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

function clearMessages() {
  error.value = '';
  success.value = '';
}

function message(reason: unknown, fallback: string) {
  return reason instanceof Error ? reason.message : fallback;
}

function formatExpiry(value: string) {
  return new Date(value).toLocaleString();
}
</script>

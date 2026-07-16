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
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'fiscal'"
        :class="{ active: activeTab === 'fiscal' }"
        @click="openFiscal"
      >
        <Archive :size="18" aria-hidden="true" />
        Fiscal Archive
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

    <section v-else-if="activeTab === 'restore'" class="panel administration-form restore-workflow">
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

    <section v-else class="panel administration-form fiscal-workflow">
      <header class="administration-section-header">
        <div>
          <h3>Fiscal-year archive</h3>
          <p>Download selected fiscal records, clean them from the live database, or merge them back later.</p>
        </div>
        <Archive :size="26" aria-hidden="true" />
      </header>

      <div class="fiscal-mode-switch" aria-label="Fiscal archive mode">
        <button type="button" :class="{ active: fiscalMode === 'archive' }" @click="fiscalMode = 'archive'">Archive &amp; Clean</button>
        <button type="button" :class="{ active: fiscalMode === 'restore' }" @click="fiscalMode = 'restore'">Restore Archive</button>
      </div>

      <div v-if="fiscalMode === 'archive'" class="restore-workflow">
        <section class="restore-step">
          <div class="restore-step-number">1</div>
          <div class="restore-step-content">
            <h4>Review fiscal records</h4>
            <label class="fiscal-year-field">
              Fiscal year
              <input v-model.number="fiscalYear" type="number" min="2000" max="2200" @change="loadFiscalPreview" />
            </label>
            <div v-if="fiscalPreview" class="validation-summary fiscal-summary" aria-label="Fiscal archive preview">
              <span><strong>{{ fiscalPreview.offeringCount }}</strong> offerings</span>
              <span><strong>{{ fiscalPreview.linkedIncomeCount }}</strong> linked income</span>
              <span><strong>{{ fiscalPreview.expenseCount }}</strong> expenses</span>
              <span><strong>{{ fiscalPreview.budgetCount }}</strong> budgets</span>
              <span><strong>{{ fiscalPreview.totalRecordCount }}</strong> total records</span>
              <small>{{ fiscalPreview.startDate }} to {{ fiscalPreview.endDate }}</small>
            </div>
          </div>
        </section>

        <section class="restore-step">
          <div class="restore-step-number">2</div>
          <div class="restore-step-content">
            <h4>Download encrypted archive</h4>
            <div class="administration-fields">
              <label>Fiscal archive password<input v-model="fiscalPassword" type="password" autocomplete="new-password" /></label>
              <label>Confirm fiscal archive password<input v-model="fiscalPasswordConfirmation" type="password" autocomplete="new-password" /></label>
            </div>
            <button type="button" class="secondary-command" :disabled="busy || !fiscalPreview" @click="runFiscalArchive">
              <Download :size="18" aria-hidden="true" /> Download fiscal archive
            </button>
            <span v-if="fiscalArchiveId" class="completion-mark"><CheckCircle2 :size="17" /> Fiscal archive downloaded</span>
          </div>
        </section>

        <section class="restore-step destructive-step" :class="{ unavailable: !fiscalArchiveId }">
          <div class="restore-step-number">3</div>
          <div class="restore-step-content">
            <h4>Clean archived records</h4>
            <label class="confirmation-field">Fiscal cleanup confirmation
              <input v-model="fiscalCleanConfirmation" :placeholder="fiscalCleanPhrase" :disabled="!fiscalArchiveId" />
            </label>
            <button type="button" class="danger-command" :disabled="busy || !canCleanFiscal" @click="runFiscalClean">
              <DatabaseZap :size="18" aria-hidden="true" /> Clean archived fiscal data
            </button>
          </div>
        </section>
      </div>

      <div v-else class="restore-workflow">
        <section class="restore-step">
          <div class="restore-step-number">1</div>
          <div class="restore-step-content">
            <h4>Validate fiscal archive</h4>
            <div class="administration-fields">
              <label>Fiscal archive ZIP file<input type="file" accept=".zip,application/zip" @change="selectFiscalRestoreFile" /></label>
              <label>Fiscal restore password<input v-model="fiscalRestorePassword" type="password" autocomplete="off" /></label>
            </div>
            <button type="button" class="secondary-command" :disabled="busy || !fiscalRestoreFile" @click="runFiscalValidation">
              <FileCheck2 :size="18" aria-hidden="true" /> Validate fiscal archive
            </button>
            <div v-if="fiscalRestoreOperation" class="validation-summary">
              <span><strong>{{ fiscalRestoreOperation.totalRecordCount }}</strong> records</span>
              <span><strong>{{ fiscalRestoreOperation.fiscalYear }}</strong> fiscal year</span>
            </div>
          </div>
        </section>
        <section class="restore-step destructive-step" :class="{ unavailable: !fiscalRestoreOperation }">
          <div class="restore-step-number">2</div>
          <div class="restore-step-content">
            <h4>Merge archive into live data</h4>
            <label class="confirmation-field">Fiscal restore confirmation
              <input v-model="fiscalRestoreConfirmation" :placeholder="fiscalRestorePhrase" :disabled="!fiscalRestoreOperation" />
            </label>
            <button type="button" class="danger-command" :disabled="busy || !canRestoreFiscal" @click="runFiscalRestore">
              <ArchiveRestore :size="18" aria-hidden="true" /> Restore fiscal archive
            </button>
          </div>
        </section>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  Archive,
  ArchiveRestore,
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
  cleanFiscalArchive,
  downloadFiscalArchive,
  executeFiscalRestore,
  getFiscalArchivePreview,
  validateFiscalRestore,
  type FiscalArchivePreview,
  type FiscalRestorePreview,
} from '../api/dataManagement';

const router = useRouter();
const activeTab = ref<'backup' | 'restore' | 'fiscal'>('backup');
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

const fiscalMode = ref<'archive' | 'restore'>('archive');
const fiscalYear = ref(new Date().getFullYear());
const fiscalPreview = ref<FiscalArchivePreview | null>(null);
const fiscalPassword = ref('');
const fiscalPasswordConfirmation = ref('');
const fiscalArchiveId = ref('');
const fiscalCleanConfirmation = ref('');
const fiscalRestoreFile = ref<File | null>(null);
const fiscalRestorePassword = ref('');
const fiscalRestoreOperation = ref<FiscalRestorePreview | null>(null);
const fiscalRestoreConfirmation = ref('');

const canExecuteRestore = computed(() =>
  safetyDownloaded.value && restoreConfirmation.value === 'RESTORE FULL DATABASE',
);
const fiscalCleanPhrase = computed(() => `CLEAN FISCAL YEAR ${fiscalYear.value}`);
const canCleanFiscal = computed(() => !!fiscalArchiveId.value && fiscalCleanConfirmation.value === fiscalCleanPhrase.value);
const fiscalRestorePhrase = computed(() => fiscalRestoreOperation.value
  ? `RESTORE FISCAL YEAR ${fiscalRestoreOperation.value.fiscalYear}` : 'RESTORE FISCAL YEAR');
const canRestoreFiscal = computed(() => !!fiscalRestoreOperation.value
  && fiscalRestoreConfirmation.value === fiscalRestorePhrase.value);

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

async function openFiscal() {
  activeTab.value = 'fiscal';
  await loadFiscalPreview();
}

async function loadFiscalPreview() {
  clearMessages();
  fiscalArchiveId.value = '';
  fiscalCleanConfirmation.value = '';
  try {
    fiscalPreview.value = await getFiscalArchivePreview(fiscalYear.value);
  } catch (reason) {
    error.value = message(reason, 'Could not load the fiscal archive preview.');
  }
}

async function runFiscalArchive() {
  clearMessages();
  if (!fiscalPassword.value || fiscalPassword.value !== fiscalPasswordConfirmation.value) {
    error.value = fiscalPassword.value ? 'Fiscal archive passwords must match.' : 'Fiscal archive password is required.';
    return;
  }
  busy.value = true;
  try {
    const result = await downloadFiscalArchive(fiscalYear.value, fiscalPassword.value);
    downloadBlob(result.blob, `church-fiscal-${fiscalYear.value}.zip`);
    fiscalArchiveId.value = result.archiveId;
    success.value = 'Fiscal archive downloaded. Verify the file before cleaning records.';
  } catch (reason) {
    error.value = message(reason, 'Could not create the fiscal archive.');
  } finally {
    fiscalPassword.value = '';
    fiscalPasswordConfirmation.value = '';
    busy.value = false;
  }
}

async function runFiscalClean() {
  if (!canCleanFiscal.value) return;
  clearMessages();
  busy.value = true;
  try {
    await cleanFiscalArchive(fiscalArchiveId.value, fiscalCleanConfirmation.value);
    fiscalCleanConfirmation.value = '';
    fiscalArchiveId.value = '';
    await loadFiscalPreview();
    success.value = `Fiscal year ${fiscalYear.value} records were archived and cleaned.`;
  } catch (reason) {
    error.value = message(reason, 'Could not clean the archived fiscal records.');
  } finally {
    busy.value = false;
  }
}

function selectFiscalRestoreFile(event: Event) {
  fiscalRestoreFile.value = (event.target as HTMLInputElement).files?.[0] ?? null;
  fiscalRestoreOperation.value = null;
  fiscalRestoreConfirmation.value = '';
}

async function runFiscalValidation() {
  clearMessages();
  if (!fiscalRestoreFile.value || !fiscalRestorePassword.value) {
    error.value = 'Choose a fiscal archive ZIP file and enter its password.';
    return;
  }
  busy.value = true;
  try {
    fiscalRestoreOperation.value = await validateFiscalRestore(fiscalRestoreFile.value, fiscalRestorePassword.value);
    success.value = 'Fiscal archive validated with no conflicts.';
  } catch (reason) {
    error.value = message(reason, 'Could not validate the fiscal archive.');
  } finally {
    fiscalRestorePassword.value = '';
    busy.value = false;
  }
}

async function runFiscalRestore() {
  if (!fiscalRestoreOperation.value || !canRestoreFiscal.value) return;
  clearMessages();
  busy.value = true;
  try {
    await executeFiscalRestore(fiscalRestoreOperation.value.id, fiscalRestoreConfirmation.value);
    success.value = `Fiscal year ${fiscalRestoreOperation.value.fiscalYear} was restored.`;
    fiscalRestoreOperation.value = null;
    fiscalRestoreFile.value = null;
    fiscalRestoreConfirmation.value = '';
  } catch (reason) {
    error.value = message(reason, 'Could not restore the fiscal archive.');
  } finally {
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

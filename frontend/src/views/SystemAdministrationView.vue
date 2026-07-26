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
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'church'"
        :class="{ active: activeTab === 'church' }"
        @click="openChurchSettings"
      >
        <Church :size="18" aria-hidden="true" />
        Church Settings
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'email'"
        :class="{ active: activeTab === 'email' }"
        @click="openEmailSettings"
      >
        <Mail :size="18" aria-hidden="true" />
        Email Settings
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

    <section v-else-if="activeTab === 'fiscal'" class="panel administration-form fiscal-workflow">
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

    <form v-else-if="activeTab === 'church'" class="panel administration-form church-settings-form" @submit.prevent="saveChurchConfiguration">
      <header class="administration-section-header">
        <div>
          <h3>Church settings</h3>
          <p>Manage church information and branding used throughout the application and newly generated reports.</p>
        </div>
        <Church :size="26" aria-hidden="true" />
      </header>

      <div v-if="churchSettings" class="email-settings-status" aria-label="Church settings status">
        <span class="settings-status source">
          {{ churchSettings.source === 'DATABASE' ? 'Database settings' : 'Server defaults' }}
        </span>
        <small>Changes appear immediately. A relogin or server restart is not required.</small>
      </div>

      <div class="administration-fields church-settings-grid">
        <label>Church name<input v-model.trim="churchForm.name" required maxlength="200" /></label>
        <label>Church address<input v-model.trim="churchForm.address" required maxlength="500" /></label>
        <label>Contact information<input v-model.trim="churchForm.contactInfo" maxlength="500" /></label>
        <label>Treasurer name<input v-model.trim="churchForm.treasurerName" maxlength="200" /></label>
        <label>Charity registration number<input v-model.trim="churchForm.charityRegistrationNumber" maxlength="200" /></label>
        <label>Receipt issue location<input v-model.trim="churchForm.receiptIssueLocation" maxlength="200" /></label>
        <label class="church-website-field">Church website<input v-model.trim="churchForm.website" type="url" maxlength="500" placeholder="https://" /></label>
      </div>

      <section class="church-operational-settings" aria-labelledby="operational-settings-title">
        <h4 id="operational-settings-title">Operational Settings</h4>
        <div class="administration-fields church-settings-grid">
          <label>Church time zone
            <select v-model="churchForm.timeZone" required>
              <option v-for="zone in churchTimeZones" :key="zone" :value="zone">{{ zone }}</option>
            </select>
          </label>
          <label>Fiscal year starts
            <select v-model.number="churchForm.fiscalYearStartMonth" required>
              <option v-for="month in fiscalMonths" :key="month.value" :value="month.value">{{ month.label }}</option>
            </select>
          </label>
          <label>List page size
            <input v-model.number="churchForm.listPageSize" type="number" min="5" max="100" required />
          </label>
          <label>Data operation expiry
            <select v-model.number="churchForm.dataOperationExpiryMinutes" required>
              <option v-for="minutes in operationExpiryOptions" :key="minutes" :value="minutes">
                {{ minutes }} minutes
              </option>
            </select>
          </label>
        </div>
        <p class="administration-note">Changes apply immediately. Prepared restore operations keep their existing expiry time.</p>
      </section>

      <div class="branding-upload-grid">
        <section class="branding-upload">
          <div class="branding-preview logo-preview">
            <img v-if="effectiveLogoPreview" :src="effectiveLogoPreview" alt="Current church logo" />
            <span v-else>No logo</span>
          </div>
          <label>Church logo
            <input type="file" accept="image/png,image/jpeg,.png,.jpg,.jpeg" @change="selectBrandingFile('logo', $event)" />
          </label>
          <small>PNG or JPEG, up to 5 MB.</small>
        </section>
        <section class="branding-upload">
          <div class="branding-preview banner-preview">
            <img v-if="effectiveBannerPreview" :src="effectiveBannerPreview" alt="Current church banner" />
            <span v-else>No banner</span>
          </div>
          <label>Church banner
            <input type="file" accept="image/png,image/jpeg,.png,.jpg,.jpeg" @change="selectBrandingFile('banner', $event)" />
          </label>
          <small>PNG or JPEG, up to 5 MB.</small>
        </section>
      </div>

      <p class="administration-note">Uploaded branding is stored in the database, included in full backups, and used by new tax receipts and financial workbooks.</p>

      <div class="email-settings-actions">
        <button type="submit" :disabled="busy">
          <Save :size="18" aria-hidden="true" /> {{ busy ? 'Saving...' : 'Save church settings' }}
        </button>
        <button type="button" class="reset-command" :disabled="busy" @click="showChurchResetConfirmation = true">
          <RotateCcw :size="18" aria-hidden="true" /> Reset church settings
        </button>
      </div>
    </form>

    <form v-else class="panel administration-form email-settings-form" @submit.prevent="saveTestedEmailSettings">
      <header class="administration-section-header">
        <div>
          <h3>Email server settings</h3>
          <p>Send a successful test email before saving changes used for password reset messages.</p>
        </div>
        <div class="email-settings-header-actions">
          <button type="button" class="icon-command" aria-label="Email settings guide" title="Email settings guide" @click="showEmailGuide = true">
            <Info :size="20" aria-hidden="true" />
          </button>
          <Mail :size="26" aria-hidden="true" />
        </div>
      </header>

      <div v-if="emailSettings" class="email-settings-status" aria-label="Email settings status">
        <span class="settings-status" :class="{ configured: emailSettings.passwordConfigured }">
          {{ emailSettings.passwordConfigured ? 'Password configured' : 'Password not configured' }}
        </span>
        <span class="settings-status source">
          {{ emailSettings.source === 'DATABASE' ? 'Database settings' : 'Server defaults' }}
        </span>
      </div>

      <div class="administration-fields email-settings-grid">
        <label>SMTP host<input v-model.trim="emailForm.host" required autocomplete="off" /></label>
        <label>SMTP port<input v-model.number="emailForm.port" required type="number" min="1" max="65535" /></label>
        <label>Username<input v-model.trim="emailForm.username" autocomplete="username" /></label>
        <label>SMTP password
          <input v-model="emailForm.password" type="password" autocomplete="new-password" placeholder="Leave blank to keep the configured password" />
        </label>
        <label>From address<input v-model.trim="emailForm.fromAddress" required type="email" autocomplete="email" /></label>
        <label>Test recipient<input v-model.trim="testRecipient" required type="email" autocomplete="email" /></label>
      </div>

      <p v-if="verificationToken" class="completion-mark">
        <CheckCircle2 :size="17" aria-hidden="true" /> Test passed. These exact settings can now be saved.
      </p>

      <div class="email-settings-actions">
        <button type="button" class="secondary-command" :disabled="busy" @click="sendEmailTest">
          <Send :size="18" aria-hidden="true" /> {{ busy ? 'Sending...' : 'Send test email' }}
        </button>
        <button type="submit" :disabled="busy || !verificationToken">
          <Save :size="18" aria-hidden="true" /> Save settings
        </button>
        <button type="button" class="reset-command" :disabled="busy" @click="showResetConfirmation = true">
          <RotateCcw :size="18" aria-hidden="true" /> Reset to server defaults
        </button>
      </div>
    </form>

    <div v-if="showChurchResetConfirmation" class="email-guide-backdrop" @click.self="showChurchResetConfirmation = false">
      <section class="email-guide-dialog confirmation-dialog" role="dialog" aria-modal="true" aria-labelledby="church-reset-title">
        <header>
          <h3 id="church-reset-title">Reset church settings</h3>
          <button type="button" class="icon-command" aria-label="Close church settings reset" @click="showChurchResetConfirmation = false">
            <X :size="20" aria-hidden="true" />
          </button>
        </header>
        <p>Database information and uploaded branding will be removed. The application will immediately use the server defaults.</p>
        <div class="email-settings-actions">
          <button type="button" class="secondary-command" @click="showChurchResetConfirmation = false">Cancel</button>
          <button type="button" class="danger-command" :disabled="busy" @click="confirmChurchReset">
            <RotateCcw :size="18" aria-hidden="true" /> Confirm reset church settings
          </button>
        </div>
      </section>
    </div>

    <div v-if="showEmailGuide" class="email-guide-backdrop" @click.self="showEmailGuide = false">
      <section class="email-guide-dialog" role="dialog" aria-modal="true" aria-labelledby="email-guide-title">
        <header>
          <h3 id="email-guide-title">Email settings guide</h3>
          <button type="button" class="icon-command" aria-label="Close email settings guide" @click="showEmailGuide = false">
            <X :size="20" aria-hidden="true" />
          </button>
        </header>
        <p>Enter the provider's SMTP details, send a test message, and save only after the test succeeds.</p>
        <p>The password is encrypted in the database using <code>CHURCH_SETTINGS_ENCRYPTION_KEY</code> from the server's <code>.env</code> file. Keep the same key after a restart or deployment.</p>
        <p>SMTP authentication and STARTTLS are managed by the server administrator in <code>.env</code>. They are intentionally not shown on this page.</p>
        <p>Resetting first tests the server defaults, then removes the database override. It does not remove values from <code>.env</code>.</p>
        <button type="button" class="secondary-command" @click="showEmailGuide = false">Close</button>
      </section>
    </div>

    <div v-if="showResetConfirmation" class="email-guide-backdrop" @click.self="showResetConfirmation = false">
      <section class="email-guide-dialog confirmation-dialog" role="dialog" aria-modal="true" aria-labelledby="email-reset-title">
        <header>
          <h3 id="email-reset-title">Reset email settings</h3>
          <button type="button" class="icon-command" aria-label="Close reset confirmation" @click="showResetConfirmation = false">
            <X :size="20" aria-hidden="true" />
          </button>
        </header>
        <p>The server defaults will be tested using the test recipient before database settings are removed.</p>
        <div class="email-settings-actions">
          <button type="button" class="secondary-command" @click="showResetConfirmation = false">Cancel</button>
          <button type="button" class="danger-command" :disabled="busy" @click="confirmEmailReset">
            <RotateCcw :size="18" aria-hidden="true" /> Reset settings
          </button>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  Archive,
  ArchiveRestore,
  CheckCircle2,
  Church,
  DatabaseBackup,
  DatabaseZap,
  Download,
  FileCheck2,
  Info,
  Mail,
  RotateCcw,
  Save,
  Send,
  ShieldCheck,
  TriangleAlert,
  X,
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
import {
  getEmailSettings,
  resetEmailSettings,
  saveEmailSettings,
  testEmailSettings,
  type EmailSettingsResponse,
} from '../api/emailSettings';
import {
  getChurchSettings,
  resetChurchSettings,
  saveChurchSettings,
  type ChurchSettingsDraft,
  type ChurchSettingsResponse,
} from '../api/churchSettings';
import {
  applyChurchInformation,
  churchInformationState,
} from '../stores/churchInformationStore';

const router = useRouter();
const activeTab = ref<'backup' | 'restore' | 'fiscal' | 'church' | 'email'>('backup');
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

const emailSettings = ref<EmailSettingsResponse | null>(null);
const emailForm = reactive({ host: '', port: 587, username: '', password: '', fromAddress: '' });
const currentEmail = authState.currentUser?.primaryEmail ?? '';
const testRecipient = ref(isEmail(currentEmail) ? currentEmail : '');
const verificationToken = ref('');
const applyingEmailSettings = ref(false);
const showEmailGuide = ref(false);
const showResetConfirmation = ref(false);

const churchSettings = ref<ChurchSettingsResponse | null>(null);
const churchForm = reactive<ChurchSettingsDraft>({
  name: '',
  address: '',
  contactInfo: '',
  treasurerName: '',
  charityRegistrationNumber: '',
  receiptIssueLocation: '',
  website: '',
  timeZone: 'America/Toronto',
  fiscalYearStartMonth: 1,
  listPageSize: 20,
  dataOperationExpiryMinutes: 30,
});
const churchTimeZones = [
  'America/Toronto',
  'America/St_Johns',
  'America/Halifax',
  'America/Winnipeg',
  'America/Edmonton',
  'America/Vancouver',
  'America/Whitehorse',
  'UTC',
];
const fiscalMonths = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
].map((label, index) => ({ label, value: index + 1 }));
const operationExpiryOptions = [10, 20, 30, 60, 120];
const churchLogoFile = ref<File | null>(null);
const churchBannerFile = ref<File | null>(null);
const logoPreviewUrl = ref('');
const bannerPreviewUrl = ref('');
const showChurchResetConfirmation = ref(false);
const effectiveLogoPreview = computed(() => logoPreviewUrl.value || churchSettings.value?.logoUrl || '');
const effectiveBannerPreview = computed(() => bannerPreviewUrl.value || churchSettings.value?.bannerUrl || '');

onBeforeUnmount(() => clearBrandingSelections());

watch(
  () => [emailForm.host, emailForm.port, emailForm.username, emailForm.password, emailForm.fromAddress],
  () => {
    if (!applyingEmailSettings.value) verificationToken.value = '';
  },
);

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

async function openEmailSettings() {
  activeTab.value = 'email';
  clearMessages();
  busy.value = true;
  try {
    await applyEmailSettings(await getEmailSettings());
  } catch (reason) {
    error.value = message(reason, 'Could not load email settings.');
  } finally {
    busy.value = false;
  }
}

async function openChurchSettings() {
  activeTab.value = 'church';
  clearMessages();
  busy.value = true;
  try {
    applyChurchSettings(await getChurchSettings());
  } catch (reason) {
    error.value = message(reason, 'Could not load church settings.');
  } finally {
    busy.value = false;
  }
}

function applyChurchSettings(settings: ChurchSettingsResponse) {
  churchSettings.value = settings;
  churchForm.name = settings.name;
  churchForm.address = settings.address;
  churchForm.contactInfo = settings.contactInfo;
  churchForm.treasurerName = settings.treasurerName;
  churchForm.charityRegistrationNumber = settings.charityRegistrationNumber;
  churchForm.receiptIssueLocation = settings.receiptIssueLocation;
  churchForm.website = settings.website;
  churchForm.timeZone = settings.timeZone;
  churchForm.fiscalYearStartMonth = settings.fiscalYearStartMonth;
  churchForm.listPageSize = settings.listPageSize;
  churchForm.dataOperationExpiryMinutes = settings.dataOperationExpiryMinutes;
  applyEffectiveChurchInformation(settings);
}

function applyEffectiveChurchInformation(settings: ChurchSettingsResponse) {
  const current = churchInformationState.value;
  applyChurchInformation({
    name: settings.name,
    address: settings.address,
    contactInfo: settings.contactInfo,
    treasurerName: settings.treasurerName,
    charityRegistrationNumber: settings.charityRegistrationNumber,
    receiptIssueLocation: settings.receiptIssueLocation,
    website: settings.website,
    logPath: settings.logoUrl,
    bannerPath: settings.bannerUrl,
    timeZone: settings.timeZone,
    fiscalYearStartMonth: settings.fiscalYearStartMonth,
    listPageSize: settings.listPageSize,
    applicationVersion: current?.applicationVersion ?? '',
  });
}

async function selectBrandingFile(kind: 'logo' | 'banner', event: Event) {
  clearMessages();
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  if (!['image/png', 'image/jpeg'].includes(file.type)) {
    error.value = 'Choose a PNG or JPEG image.';
    input.value = '';
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    error.value = 'Church branding images must not exceed 5 MB.';
    input.value = '';
    return;
  }
  try {
    const dimensions = await decodeImageDimensions(file);
    if (dimensions.width <= 0 || dimensions.height <= 0
      || dimensions.width > 8_000 || dimensions.height > 8_000
      || dimensions.width * dimensions.height > 40_000_000) {
      error.value = 'The image dimensions must not exceed 8,000 pixels or 40 megapixels.';
      input.value = '';
      return;
    }
  } catch {
    error.value = 'The image must be a valid PNG or JPEG file.';
    input.value = '';
    return;
  }

  if (kind === 'logo') {
    revokePreview(logoPreviewUrl.value);
    churchLogoFile.value = file;
    logoPreviewUrl.value = URL.createObjectURL(file);
  } else {
    revokePreview(bannerPreviewUrl.value);
    churchBannerFile.value = file;
    bannerPreviewUrl.value = URL.createObjectURL(file);
  }
}

async function decodeImageDimensions(file: File): Promise<{ width: number; height: number }> {
  if (typeof createImageBitmap === 'function') {
    const bitmap = await createImageBitmap(file);
    try {
      return { width: bitmap.width, height: bitmap.height };
    } finally {
      bitmap.close();
    }
  }

  const source = URL.createObjectURL(file);
  try {
    return await new Promise((resolve, reject) => {
      const image = new Image();
      image.onload = () => resolve({ width: image.naturalWidth, height: image.naturalHeight });
      image.onerror = () => reject(new Error('Invalid image'));
      image.src = source;
    });
  } finally {
    URL.revokeObjectURL(source);
  }
}

async function saveChurchConfiguration() {
  clearMessages();
  busy.value = true;
  try {
    const saved = await saveChurchSettings(
      { ...churchForm },
      churchLogoFile.value ?? undefined,
      churchBannerFile.value ?? undefined,
    );
    clearBrandingSelections();
    applyChurchSettings(saved);
    success.value = 'Church settings saved.';
  } catch (reason) {
    error.value = message(reason, 'Could not save church settings.');
  } finally {
    busy.value = false;
  }
}

async function confirmChurchReset() {
  clearMessages();
  busy.value = true;
  try {
    const defaults = await resetChurchSettings();
    clearBrandingSelections();
    applyChurchSettings(defaults);
    showChurchResetConfirmation.value = false;
    success.value = 'Church settings reset to server defaults.';
  } catch (reason) {
    error.value = message(reason, 'Could not reset church settings.');
  } finally {
    busy.value = false;
  }
}

function clearBrandingSelections() {
  revokePreview(logoPreviewUrl.value);
  revokePreview(bannerPreviewUrl.value);
  churchLogoFile.value = null;
  churchBannerFile.value = null;
  logoPreviewUrl.value = '';
  bannerPreviewUrl.value = '';
}

function revokePreview(value: string) {
  if (value) URL.revokeObjectURL(value);
}

async function applyEmailSettings(settings: EmailSettingsResponse) {
  applyingEmailSettings.value = true;
  emailSettings.value = settings;
  emailForm.host = settings.host;
  emailForm.port = settings.port;
  emailForm.username = settings.username;
  emailForm.password = '';
  emailForm.fromAddress = settings.fromAddress;
  verificationToken.value = '';
  await nextTick();
  applyingEmailSettings.value = false;
}

async function sendEmailTest() {
  clearMessages();
  if (!isEmail(testRecipient.value)) {
    error.value = 'Enter a valid test recipient email address.';
    return;
  }
  busy.value = true;
  try {
    const result = await testEmailSettings({ ...emailForm, testRecipient: testRecipient.value });
    verificationToken.value = result.verificationToken;
    success.value = result.message;
  } catch (reason) {
    verificationToken.value = '';
    error.value = message(reason, 'Could not send the test email.');
  } finally {
    busy.value = false;
  }
}

async function saveTestedEmailSettings() {
  if (!verificationToken.value) return;
  clearMessages();
  busy.value = true;
  try {
    const settings = await saveEmailSettings({ ...emailForm, verificationToken: verificationToken.value });
    await applyEmailSettings(settings);
    success.value = 'Email settings saved.';
  } catch (reason) {
    error.value = message(reason, 'Could not save email settings.');
  } finally {
    busy.value = false;
  }
}

async function confirmEmailReset() {
  clearMessages();
  if (!isEmail(testRecipient.value)) {
    error.value = 'Enter a valid test recipient email address.';
    showResetConfirmation.value = false;
    return;
  }
  busy.value = true;
  try {
    await applyEmailSettings(await resetEmailSettings(testRecipient.value));
    showResetConfirmation.value = false;
    success.value = 'Email settings reset to server defaults.';
  } catch (reason) {
    error.value = message(reason, 'Could not reset email settings.');
  } finally {
    busy.value = false;
  }
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

function isEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}
</script>

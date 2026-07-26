import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/vue';
import SystemAdministrationView from './SystemAdministrationView.vue';
import { authState } from '../auth/authStore';
import {
  downloadFullBackup,
  downloadSafetyBackup,
  executeFullRestore,
  cleanFiscalArchive,
  downloadFiscalArchive,
  executeFiscalRestore,
  getFiscalArchivePreview,
  validateFiscalRestore,
  validateFullRestore,
} from '../api/dataManagement';
import {
  getEmailSettings,
  resetEmailSettings,
  saveEmailSettings,
  testEmailSettings,
} from '../api/emailSettings';
import {
  getChurchSettings,
  resetChurchSettings,
  saveChurchSettings,
} from '../api/churchSettings';
import {
  applyChurchInformation,
  churchInformationState,
  resetChurchInformationStore,
} from '../stores/churchInformationStore';

const routerPush = vi.fn();

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
}));

vi.mock('../api/dataManagement', () => ({
  downloadFullBackup: vi.fn(),
  validateFullRestore: vi.fn(),
  downloadSafetyBackup: vi.fn(),
  executeFullRestore: vi.fn(),
  getRestoreStatus: vi.fn(),
  getFiscalArchivePreview: vi.fn(),
  downloadFiscalArchive: vi.fn(),
  cleanFiscalArchive: vi.fn(),
  validateFiscalRestore: vi.fn(),
  executeFiscalRestore: vi.fn(),
}));

vi.mock('../api/emailSettings', () => ({
  getEmailSettings: vi.fn(),
  testEmailSettings: vi.fn(),
  saveEmailSettings: vi.fn(),
  resetEmailSettings: vi.fn(),
}));

vi.mock('../api/churchSettings', () => ({
  getChurchSettings: vi.fn(),
  saveChurchSettings: vi.fn(),
  resetChurchSettings: vi.fn(),
}));

const backupMock = vi.mocked(downloadFullBackup);
const validateMock = vi.mocked(validateFullRestore);
const safetyMock = vi.mocked(downloadSafetyBackup);
const executeMock = vi.mocked(executeFullRestore);
const fiscalPreviewMock = vi.mocked(getFiscalArchivePreview);
const fiscalDownloadMock = vi.mocked(downloadFiscalArchive);
const fiscalCleanMock = vi.mocked(cleanFiscalArchive);
const fiscalValidateMock = vi.mocked(validateFiscalRestore);
const fiscalExecuteMock = vi.mocked(executeFiscalRestore);
const getEmailSettingsMock = vi.mocked(getEmailSettings);
const testEmailSettingsMock = vi.mocked(testEmailSettings);
const saveEmailSettingsMock = vi.mocked(saveEmailSettings);
const resetEmailSettingsMock = vi.mocked(resetEmailSettings);
const getChurchSettingsMock = vi.mocked(getChurchSettings);
const saveChurchSettingsMock = vi.mocked(saveChurchSettings);
const resetChurchSettingsMock = vi.mocked(resetChurchSettings);

const churchSettingsResponse = {
  name: 'Grace Community Church',
  address: '123 Church Street',
  contactInfo: '416-555-0100',
  treasurerName: 'Daniel Kim',
  charityRegistrationNumber: '123456789RR0001',
  receiptIssueLocation: 'Toronto, Ontario',
  website: 'https://church.example.org',
  logoUrl: '/branding/church_logo_sample.png',
  bannerUrl: '/branding/church_banner_sample.png',
  timeZone: 'America/Toronto',
  fiscalYearStartMonth: 1,
  listPageSize: 20,
  dataOperationExpiryMinutes: 30,
  source: 'SERVER_DEFAULTS' as const,
};

const validatedOperation = {
  id: 'op-1',
  type: 'FULL_RESTORE' as const,
  status: 'VALIDATED' as const,
  expiresAt: '2026-07-16T20:00:00Z',
  collectionCount: 8,
  documentCount: 350,
  indexCount: 12,
  message: 'Backup archive validated.',
};

describe('SystemAdministrationView', () => {
  const originalCreateObjectUrl = URL.createObjectURL;
  const originalRevokeObjectUrl = URL.revokeObjectURL;

  beforeEach(() => {
    resetChurchInformationStore();
    applyChurchInformation({
      ...churchSettingsResponse,
      logPath: churchSettingsResponse.logoUrl,
      bannerPath: churchSettingsResponse.bannerUrl,
      timeZone: churchSettingsResponse.timeZone,
      fiscalYearStartMonth: churchSettingsResponse.fiscalYearStartMonth,
      listPageSize: churchSettingsResponse.listPageSize,
      applicationVersion: '1.0.0',
    });
    authState.currentUser = {
      primaryEmail: 'admin@example.org', displayName: 'Admin', roles: ['ADMIN'],
      mustChangePassword: false, token: 'token',
    };
    backupMock.mockResolvedValue(new Blob(['backup'], { type: 'application/zip' }));
    validateMock.mockResolvedValue(validatedOperation);
    safetyMock.mockResolvedValue(new Blob(['safety'], { type: 'application/zip' }));
    executeMock.mockResolvedValue({ ...validatedOperation, status: 'COMPLETE', message: 'Restore completed.' });
    fiscalPreviewMock.mockResolvedValue({
      fiscalYear: 2026, startDate: '2026-01-01', endDate: '2026-12-31', offeringCount: 4,
      linkedIncomeCount: 4, expenseCount: 2, budgetCount: 3, totalRecordCount: 13,
    });
    fiscalDownloadMock.mockResolvedValue({
      blob: new Blob(['fiscal'], { type: 'application/zip' }), archiveId: 'archive-1',
    });
    fiscalCleanMock.mockResolvedValue({ archiveId: 'archive-1', fiscalYear: 2026, status: 'CLEANED' });
    fiscalValidateMock.mockResolvedValue({
      id: 'fiscal-restore-1', archiveId: 'archive-1', fiscalYear: 2026, totalRecordCount: 13, status: 'VALIDATED',
    });
    fiscalExecuteMock.mockResolvedValue({ archiveId: 'archive-1', fiscalYear: 2026, status: 'RESTORED' });
    getEmailSettingsMock.mockResolvedValue({
      host: 'smtp.example.org', port: 587, username: 'smtp-user', fromAddress: 'church@example.org',
      passwordConfigured: true, source: 'SERVER_DEFAULTS',
    });
    testEmailSettingsMock.mockResolvedValue({
      verificationToken: 'verification-1', expiresAt: '2026-07-22T15:10:00Z',
      message: 'Test email sent successfully.',
    });
    saveEmailSettingsMock.mockResolvedValue({
      host: 'smtp.example.org', port: 587, username: 'smtp-user', fromAddress: 'church@example.org',
      passwordConfigured: true, source: 'DATABASE', updatedAt: '2026-07-22T15:00:00Z',
    });
    resetEmailSettingsMock.mockResolvedValue({
      host: 'localhost', port: 1025, username: '', fromAddress: 'no-reply@church.local',
      passwordConfigured: false, source: 'SERVER_DEFAULTS',
    });
    getChurchSettingsMock.mockResolvedValue(churchSettingsResponse);
    saveChurchSettingsMock.mockResolvedValue({
      ...churchSettingsResponse,
      name: 'Updated Church',
      logoUrl: '/api/church-information/logo?v=2',
      source: 'DATABASE',
    });
    resetChurchSettingsMock.mockResolvedValue(churchSettingsResponse);
    URL.createObjectURL = vi.fn(() => 'blob:download');
    URL.revokeObjectURL = vi.fn();
    vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue({
      width: 100,
      height: 100,
      close: vi.fn(),
    }));
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.restoreAllMocks();
    authState.currentUser = null;
    resetChurchInformationStore();
    URL.createObjectURL = originalCreateObjectUrl;
    URL.revokeObjectURL = originalRevokeObjectUrl;
    vi.unstubAllGlobals();
  });

  it('loads editable church settings and shows their source', async () => {
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Church Settings' }));

    expect(getChurchSettingsMock).toHaveBeenCalled();
    expect((await screen.findByLabelText('Church name') as HTMLInputElement).value)
      .toBe('Grace Community Church');
    expect(screen.getByText('Server defaults')).toBeTruthy();
    expect(screen.getByAltText('Current church logo').getAttribute('src'))
      .toBe('/branding/church_logo_sample.png');
  });

  it('uploads church settings and refreshes shared branding immediately', async () => {
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Church Settings' }));
    await screen.findByLabelText('Church name');
    const logo = new File([new Uint8Array([1, 2, 3])], 'logo.png', { type: 'image/png' });

    await fireEvent.update(screen.getByLabelText('Church name'), 'Updated Church');
    await fireEvent.change(screen.getByLabelText('Church logo'), { target: { files: [logo] } });
    await fireEvent.click(screen.getByRole('button', { name: 'Save church settings' }));

    expect(saveChurchSettingsMock).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Updated Church' }), logo, undefined,
    );
    expect(churchInformationState.value?.name).toBe('Updated Church');
    expect(churchInformationState.value?.logPath).toBe('/api/church-information/logo?v=2');
  });

  it('saves operational settings and refreshes shared values immediately', async () => {
    saveChurchSettingsMock.mockResolvedValueOnce({
      ...churchSettingsResponse,
      timeZone: 'America/Vancouver',
      fiscalYearStartMonth: 4,
      listPageSize: 50,
      dataOperationExpiryMinutes: 60,
      source: 'DATABASE',
    });
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Church Settings' }));
    await screen.findByLabelText('Church time zone');

    await fireEvent.update(screen.getByLabelText('Church time zone'), 'America/Vancouver');
    await fireEvent.update(screen.getByLabelText('Fiscal year starts'), '4');
    await fireEvent.update(screen.getByLabelText('List page size'), '50');
    await fireEvent.update(screen.getByLabelText('Data operation expiry'), '60');
    await fireEvent.click(screen.getByRole('button', { name: 'Save church settings' }));

    expect(saveChurchSettingsMock).toHaveBeenCalledWith(
      expect.objectContaining({
        timeZone: 'America/Vancouver',
        fiscalYearStartMonth: 4,
        listPageSize: 50,
        dataOperationExpiryMinutes: 60,
      }),
      undefined,
      undefined,
    );
    expect(churchInformationState.value?.timeZone).toBe('America/Vancouver');
    expect(churchInformationState.value?.fiscalYearStartMonth).toBe(4);
    expect(churchInformationState.value?.listPageSize).toBe(50);
  });

  it('rejects unsupported branding files and confirms reset to defaults', async () => {
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Church Settings' }));
    await screen.findByLabelText('Church name');
    const invalid = new File([new Uint8Array([1])], 'logo.gif', { type: 'image/gif' });

    await fireEvent.change(screen.getByLabelText('Church logo'), { target: { files: [invalid] } });
    expect((await screen.findByRole('alert')).textContent).toContain('PNG or JPEG');

    await fireEvent.click(screen.getByRole('button', { name: 'Reset church settings' }));
    expect(screen.getByRole('dialog', { name: 'Reset church settings' })).toBeTruthy();
    await fireEvent.click(screen.getByRole('button', { name: 'Confirm reset church settings' }));

    expect(resetChurchSettingsMock).toHaveBeenCalled();
    expect(churchInformationState.value?.name).toBe('Grace Community Church');
  });

  it('rejects branding images whose decoded dimensions exceed the safe limits', async () => {
    vi.mocked(createImageBitmap).mockResolvedValueOnce({
      width: 8_001,
      height: 100,
      close: vi.fn(),
    } as unknown as ImageBitmap);
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Church Settings' }));
    await screen.findByLabelText('Church name');
    const oversized = new File([new Uint8Array([1, 2, 3])], 'wide.png', { type: 'image/png' });

    await fireEvent.change(screen.getByLabelText('Church logo'), { target: { files: [oversized] } });

    expect((await screen.findByRole('alert')).textContent).toContain('8,000 pixels or 40 megapixels');
    expect(saveChurchSettingsMock).not.toHaveBeenCalled();
  });

  it('requires matching backup passwords and clears them after download', async () => {
    render(SystemAdministrationView);
    await fireEvent.update(screen.getByLabelText('Backup password'), 'long backup password');
    await fireEvent.update(screen.getByLabelText('Confirm backup password'), 'different');
    await fireEvent.click(screen.getByRole('button', { name: 'Download full backup' }));
    expect(screen.getByText('Backup passwords must match.')).toBeTruthy();
    expect(backupMock).not.toHaveBeenCalled();

    await fireEvent.update(screen.getByLabelText('Confirm backup password'), 'long backup password');
    await fireEvent.click(screen.getByRole('button', { name: 'Download full backup' }));
    expect(backupMock).toHaveBeenCalledWith('long backup password');
    expect((screen.getByLabelText('Backup password') as HTMLInputElement).value).toBe('');
    expect((screen.getByLabelText('Confirm backup password') as HTMLInputElement).value).toBe('');
  });

  it('validates the restore file and displays its summary', async () => {
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Full Restore' }));
    const file = new File(['zip'], 'church-backup.zip', { type: 'application/zip' });
    await fireEvent.change(screen.getByLabelText('Backup ZIP file'), { target: { files: [file] } });
    await fireEvent.update(screen.getByLabelText('Restore password'), 'restore password');
    await fireEvent.click(screen.getByRole('button', { name: 'Validate backup' }));

    expect(validateMock).toHaveBeenCalledWith(file, 'restore password');
    const summary = await screen.findByLabelText('Validated backup summary');
    expect(summary.textContent).toContain('8 collections');
    expect(summary.textContent).toContain('350 documents');
    expect(summary.textContent).toContain('12 indexes');
    expect((screen.getByLabelText('Restore password') as HTMLInputElement).value).toBe('');
  });

  it('keeps restore disabled until safety backup and exact phrase are complete', async () => {
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Full Restore' }));
    expect((screen.getByRole('button', { name: 'Restore full database' }) as HTMLButtonElement).disabled).toBe(true);

    const file = new File(['zip'], 'church-backup.zip', { type: 'application/zip' });
    await fireEvent.change(screen.getByLabelText('Backup ZIP file'), { target: { files: [file] } });
    await fireEvent.update(screen.getByLabelText('Restore password'), 'restore password');
    await fireEvent.click(screen.getByRole('button', { name: 'Validate backup' }));
    expect((screen.getByRole('button', { name: 'Restore full database' }) as HTMLButtonElement).disabled).toBe(true);

    await fireEvent.update(screen.getByLabelText('Safety backup password'), 'safety password');
    await fireEvent.update(screen.getByLabelText('Confirm safety backup password'), 'safety password');
    await fireEvent.click(screen.getByRole('button', { name: 'Download safety backup' }));
    expect(safetyMock).toHaveBeenCalledWith('op-1', 'safety password');

    await fireEvent.update(screen.getByLabelText('Restore confirmation'), 'RESTORE FULL DATABASE');
    expect((screen.getByRole('button', { name: 'Restore full database' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('clears authentication and redirects to login after restore completes', async () => {
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Full Restore' }));
    const file = new File(['zip'], 'church-backup.zip', { type: 'application/zip' });
    await fireEvent.change(screen.getByLabelText('Backup ZIP file'), { target: { files: [file] } });
    await fireEvent.update(screen.getByLabelText('Restore password'), 'restore password');
    await fireEvent.click(screen.getByRole('button', { name: 'Validate backup' }));
    await fireEvent.update(screen.getByLabelText('Safety backup password'), 'safety password');
    await fireEvent.update(screen.getByLabelText('Confirm safety backup password'), 'safety password');
    await fireEvent.click(screen.getByRole('button', { name: 'Download safety backup' }));
    await fireEvent.update(screen.getByLabelText('Restore confirmation'), 'RESTORE FULL DATABASE');
    await fireEvent.click(screen.getByRole('button', { name: 'Restore full database' }));

    expect(executeMock).toHaveBeenCalledWith('op-1', 'RESTORE FULL DATABASE');
    expect(authState.currentUser).toBeNull();
    expect(routerPush).toHaveBeenCalledWith('/login');
  });

  it('previews fiscal counts and gates cleanup behind the downloaded archive and exact phrase', async () => {
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Fiscal Archive' }));
    expect((await screen.findByLabelText('Fiscal archive preview')).textContent).toContain('13 total records');
    expect(fiscalPreviewMock).toHaveBeenCalledWith(2026);

    await fireEvent.update(screen.getByLabelText('Fiscal archive password'), 'fiscal password');
    await fireEvent.update(screen.getByLabelText('Confirm fiscal archive password'), 'fiscal password');
    await fireEvent.click(screen.getByRole('button', { name: 'Download fiscal archive' }));
    expect(fiscalDownloadMock).toHaveBeenCalledWith(2026, 'fiscal password');

    const cleanButton = screen.getByRole('button', { name: 'Clean archived fiscal data' }) as HTMLButtonElement;
    expect(cleanButton.disabled).toBe(true);
    await fireEvent.update(screen.getByLabelText('Fiscal cleanup confirmation'), 'CLEAN FISCAL YEAR 2026');
    expect(cleanButton.disabled).toBe(false);
    await fireEvent.click(cleanButton);
    expect(fiscalCleanMock).toHaveBeenCalledWith('archive-1', 'CLEAN FISCAL YEAR 2026');
  });

  it('validates and restores a fiscal archive with the exact year phrase', async () => {
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Fiscal Archive' }));
    await fireEvent.click(screen.getByRole('button', { name: 'Restore Archive' }));
    const file = new File(['zip'], 'fiscal.zip', { type: 'application/zip' });
    await fireEvent.change(screen.getByLabelText('Fiscal archive ZIP file'), { target: { files: [file] } });
    await fireEvent.update(screen.getByLabelText('Fiscal restore password'), 'fiscal password');
    await fireEvent.click(screen.getByRole('button', { name: 'Validate fiscal archive' }));
    await fireEvent.update(screen.getByLabelText('Fiscal restore confirmation'), 'RESTORE FISCAL YEAR 2026');
    await fireEvent.click(screen.getByRole('button', { name: 'Restore fiscal archive' }));

    expect(fiscalValidateMock).toHaveBeenCalledWith(file, 'fiscal password');
    expect(fiscalExecuteMock).toHaveBeenCalledWith('fiscal-restore-1', 'RESTORE FISCAL YEAR 2026');
  });

  it('loads masked email settings and defaults the test recipient to the admin email', async () => {
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Email Settings' }));

    expect(getEmailSettingsMock).toHaveBeenCalled();
    expect((await screen.findByLabelText('SMTP host') as HTMLInputElement).value).toBe('smtp.example.org');
    expect((screen.getByLabelText('SMTP password') as HTMLInputElement).value).toBe('');
    expect((screen.getByLabelText('Test recipient') as HTMLInputElement).value).toBe('admin@example.org');
    expect(screen.getByText('Password configured')).toBeTruthy();
    expect(screen.getByText('Server defaults')).toBeTruthy();
  });

  it('requires an exact successful email test before save and invalidates after edits', async () => {
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Email Settings' }));
    await screen.findByLabelText('SMTP host');
    const saveButton = screen.getByRole('button', { name: 'Save settings' }) as HTMLButtonElement;
    expect(saveButton.disabled).toBe(true);

    await fireEvent.update(screen.getByLabelText('SMTP password'), 'new-secret');
    await fireEvent.click(screen.getByRole('button', { name: 'Send test email' }));
    expect(testEmailSettingsMock).toHaveBeenCalledWith(expect.objectContaining({
      password: 'new-secret', testRecipient: 'admin@example.org',
    }));
    expect(saveButton.disabled).toBe(false);

    await fireEvent.update(screen.getByLabelText('SMTP port'), '2525');
    expect(saveButton.disabled).toBe(true);
  });

  it('saves a tested configuration and clears the password field', async () => {
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Email Settings' }));
    await screen.findByLabelText('SMTP host');
    await fireEvent.update(screen.getByLabelText('SMTP password'), 'new-secret');
    await fireEvent.click(screen.getByRole('button', { name: 'Send test email' }));
    await fireEvent.click(screen.getByRole('button', { name: 'Save settings' }));

    expect(saveEmailSettingsMock).toHaveBeenCalledWith(expect.objectContaining({
      password: 'new-secret', verificationToken: 'verification-1',
    }));
    expect((screen.getByLabelText('SMTP password') as HTMLInputElement).value).toBe('');
    expect(await screen.findByText('Database settings')).toBeTruthy();
  });

  it('keeps save disabled when the test email fails', async () => {
    testEmailSettingsMock.mockRejectedValueOnce(new Error('Email authentication failed.'));
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Email Settings' }));
    await fireEvent.click(await screen.findByRole('button', { name: 'Send test email' }));

    expect((await screen.findByRole('alert')).textContent).toContain('Email authentication failed.');
    expect((screen.getByRole('button', { name: 'Save settings' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('confirms and resets database settings to tested server defaults', async () => {
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Email Settings' }));
    await screen.findByLabelText('SMTP host');
    await fireEvent.click(screen.getByRole('button', { name: 'Reset to server defaults' }));
    expect(screen.getByRole('dialog', { name: 'Reset email settings' })).toBeTruthy();
    await fireEvent.click(screen.getByRole('button', { name: 'Reset settings' }));

    expect(resetEmailSettingsMock).toHaveBeenCalledWith('admin@example.org');
    expect(await screen.findByText('Server defaults')).toBeTruthy();
    expect((screen.getByLabelText('SMTP host') as HTMLInputElement).value).toBe('localhost');
  });

  it('explains encryption and deployment-only settings in an accessible guide', async () => {
    render(SystemAdministrationView);
    await fireEvent.click(screen.getByRole('tab', { name: 'Email Settings' }));
    await screen.findByLabelText('SMTP host');
    await fireEvent.click(screen.getByRole('button', { name: 'Email settings guide' }));

    const guide = screen.getByRole('dialog', { name: 'Email settings guide' });
    expect(guide.textContent).toContain('CHURCH_SETTINGS_ENCRYPTION_KEY');
    expect(guide.textContent).toContain('STARTTLS');
  });
});

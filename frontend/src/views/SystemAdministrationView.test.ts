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

const backupMock = vi.mocked(downloadFullBackup);
const validateMock = vi.mocked(validateFullRestore);
const safetyMock = vi.mocked(downloadSafetyBackup);
const executeMock = vi.mocked(executeFullRestore);
const fiscalPreviewMock = vi.mocked(getFiscalArchivePreview);
const fiscalDownloadMock = vi.mocked(downloadFiscalArchive);
const fiscalCleanMock = vi.mocked(cleanFiscalArchive);
const fiscalValidateMock = vi.mocked(validateFiscalRestore);
const fiscalExecuteMock = vi.mocked(executeFiscalRestore);

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
    authState.currentUser = {
      primaryEmail: 'admin', displayName: 'Admin', roles: ['ADMIN'],
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
    URL.createObjectURL = vi.fn(() => 'blob:download');
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.restoreAllMocks();
    authState.currentUser = null;
    URL.createObjectURL = originalCreateObjectUrl;
    URL.revokeObjectURL = originalRevokeObjectUrl;
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
});

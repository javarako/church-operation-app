import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/vue';
import SystemAdministrationView from './SystemAdministrationView.vue';
import { authState } from '../auth/authStore';
import {
  downloadFullBackup,
  downloadSafetyBackup,
  executeFullRestore,
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
}));

const backupMock = vi.mocked(downloadFullBackup);
const validateMock = vi.mocked(validateFullRestore);
const safetyMock = vi.mocked(downloadSafetyBackup);
const executeMock = vi.mocked(executeFullRestore);

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
});

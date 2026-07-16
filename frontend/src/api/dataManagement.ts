import { getJson, postBlob, postBlobResponse, postJson, postMultipartJson } from './http';

export type DataOperationStatus =
  | 'UPLOADED'
  | 'VALIDATED'
  | 'SAFETY_BACKUP_DOWNLOADED'
  | 'RESTORING'
  | 'COMPLETE'
  | 'FAILED_MAINTENANCE';

export interface DataOperationResponse {
  id: string;
  type: 'FULL_RESTORE';
  status: DataOperationStatus;
  expiresAt: string;
  collectionCount: number;
  documentCount: number;
  indexCount: number;
  message: string;
}

export function downloadFullBackup(password: string) {
  return postBlob('/api/admin/data-management/full-backup', { password });
}

export function validateFullRestore(file: File, password: string) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('password', password);
  return postMultipartJson<DataOperationResponse>('/api/admin/data-management/restore/validate', formData);
}

export function downloadSafetyBackup(operationId: string, password: string) {
  return postBlob(`/api/admin/data-management/restore/${operationId}/safety-backup`, { password });
}

export function executeFullRestore(operationId: string, confirmation: string) {
  return postJson<{ confirmation: string }, DataOperationResponse>(
    `/api/admin/data-management/restore/${operationId}/execute`,
    { confirmation },
  );
}

export function getRestoreStatus(operationId: string) {
  return getJson<DataOperationResponse>(`/api/admin/data-management/restore/${operationId}`);
}

export interface FiscalArchivePreview {
  fiscalYear: number;
  startDate: string;
  endDate: string;
  offeringCount: number;
  linkedIncomeCount: number;
  expenseCount: number;
  budgetCount: number;
  totalRecordCount: number;
}

export interface FiscalArchiveRegistry {
  archiveId: string;
  fiscalYear: number;
  status: 'STAGED' | 'CLEANED' | 'RESTORED';
}

export interface FiscalRestorePreview {
  id: string;
  archiveId: string;
  fiscalYear: number;
  totalRecordCount: number;
  status: 'VALIDATED';
}

export function getFiscalArchivePreview(year: number) {
  return getJson<FiscalArchivePreview>(`/api/admin/data-management/fiscal/${year}/preview`);
}

export async function downloadFiscalArchive(year: number, password: string) {
  const response = await postBlobResponse(`/api/admin/data-management/fiscal/${year}/archive`, { password });
  const archiveId = response.headers.get('X-Fiscal-Archive-Id');
  if (!archiveId) throw new Error('Fiscal archive identifier is missing.');
  return { blob: await response.blob(), archiveId };
}

export function cleanFiscalArchive(archiveId: string, confirmation: string) {
  return postJson<{ confirmation: string }, FiscalArchiveRegistry>(
    `/api/admin/data-management/fiscal/${archiveId}/clean`, { confirmation },
  );
}

export function validateFiscalRestore(file: File, password: string) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('password', password);
  return postMultipartJson<FiscalRestorePreview>('/api/admin/data-management/fiscal/restore/validate', formData);
}

export function executeFiscalRestore(operationId: string, confirmation: string) {
  return postJson<{ confirmation: string }, FiscalArchiveRegistry>(
    `/api/admin/data-management/fiscal/restore/${operationId}/execute`, { confirmation },
  );
}

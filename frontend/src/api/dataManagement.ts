import { getJson, postBlob, postJson, postMultipartJson } from './http';

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

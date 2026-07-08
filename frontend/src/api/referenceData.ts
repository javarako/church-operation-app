import { getJson, postJson, putJson } from './http';

export type ReferenceDataType =
  | 'GROUP_CODE'
  | 'MEMBERSHIP_STATUS'
  | 'OFFERING_FUND_CATEGORY'
  | 'FINANCIAL_CATEGORY'
  | 'FINANCIAL_SUB_CATEGORY';

export interface ReferenceDataOption {
  id: string;
  type: ReferenceDataType;
  code: string;
  label: string;
  parentCode?: string;
  sortOrder: number;
  active: boolean;
}

export interface ReferenceDataPayload {
  type: ReferenceDataType;
  code: string;
  label: string;
  parentCode?: string;
  sortOrder: number;
  active: boolean;
}

export function listReferenceData(type: ReferenceDataType, parentCode?: string) {
  const query = parentCode ? `?parentCode=${encodeURIComponent(parentCode)}` : '';
  return getJson<ReferenceDataOption[]>(`/api/reference-data/${type}${query}`);
}

export function createReferenceData(payload: ReferenceDataPayload) {
  return postJson<ReferenceDataPayload, ReferenceDataOption>('/api/reference-data', payload);
}

export function updateReferenceData(id: string, payload: ReferenceDataPayload) {
  return putJson<ReferenceDataPayload, ReferenceDataOption>(`/api/reference-data/${id}`, payload);
}

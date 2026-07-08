import { getJson } from './http';

export type ReferenceDataType = 'GROUP_CODE' | 'MEMBERSHIP_STATUS';

export interface ReferenceDataOption {
  id: string;
  type: ReferenceDataType;
  code: string;
  label: string;
  sortOrder: number;
  active: boolean;
}

export function listReferenceData(type: ReferenceDataType) {
  return getJson<ReferenceDataOption[]>(`/api/reference-data/${type}`);
}

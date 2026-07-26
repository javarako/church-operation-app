import { getJson, postJson, putMultipartJson } from './http';

export type ChurchSettingsSource = 'SERVER_DEFAULTS' | 'DATABASE';

export interface ChurchSettingsDraft {
  name: string;
  address: string;
  contactInfo: string;
  treasurerName: string;
  charityRegistrationNumber: string;
  receiptIssueLocation: string;
  website: string;
  timeZone: string;
  fiscalYearStartMonth: number;
  listPageSize: number;
  dataOperationExpiryMinutes: number;
}

export interface ChurchSettingsResponse extends ChurchSettingsDraft {
  logoUrl: string;
  bannerUrl: string;
  source: ChurchSettingsSource;
  updatedAt?: string;
}

export function getChurchSettings(): Promise<ChurchSettingsResponse> {
  return getJson('/api/admin/church-settings');
}

export function saveChurchSettings(
  settings: ChurchSettingsDraft,
  logo?: File,
  banner?: File,
): Promise<ChurchSettingsResponse> {
  const formData = new FormData();
  formData.append('settings', new Blob([JSON.stringify(settings)], { type: 'application/json' }));
  if (logo) formData.append('logo', logo);
  if (banner) formData.append('banner', banner);
  return putMultipartJson('/api/admin/church-settings', formData);
}

export function resetChurchSettings(): Promise<ChurchSettingsResponse> {
  return postJson('/api/admin/church-settings/reset', {});
}

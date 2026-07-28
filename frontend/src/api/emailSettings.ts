import { getJson, postJson, putJson } from './http';

export type EmailSettingsSource = 'SERVER_DEFAULTS' | 'DATABASE';

export interface EmailSettingsResponse {
  host: string;
  port: number;
  username: string;
  fromAddress: string;
  passwordConfigured: boolean;
  source: EmailSettingsSource;
  updatedAt?: string;
}

export interface EmailSettingsDraft {
  host: string;
  port: number;
  username: string;
  password: string;
  fromAddress: string;
}

export interface EmailSettingsTestResponse {
  verificationToken: string;
  expiresAt: string;
  message: string;
}

export function getEmailSettings(): Promise<EmailSettingsResponse> {
  return getJson('/api/admin/email-settings');
}

export function testEmailSettings(request: EmailSettingsDraft & { testRecipient: string }) {
  return postJson<typeof request, EmailSettingsTestResponse>('/api/admin/email-settings/test', request);
}

export function saveEmailSettings(request: EmailSettingsDraft & { verificationToken: string }) {
  return putJson<typeof request, EmailSettingsResponse>('/api/admin/email-settings', request);
}

export function resetEmailSettings(testRecipient: string): Promise<EmailSettingsResponse> {
  return postJson('/api/admin/email-settings/reset', { testRecipient });
}

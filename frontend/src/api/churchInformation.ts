import { getJson } from './http';

export interface ChurchInformation {
  name: string;
  address: string;
  contactInfo: string;
  treasurerName: string;
  charityRegistrationNumber: string;
  receiptIssueLocation: string;
  website: string;
  bannerPath: string;
  logPath: string;
  listPageSize: number;
  applicationVersion: string;
}

export function getChurchInformation() {
  return getJson<ChurchInformation>('/api/church-information');
}

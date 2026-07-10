import { getJson } from './http';

export interface ChurchInformation {
  name: string;
  address: string;
  contactInfo: string;
  treasurerName: string;
  bannerPath: string;
  logPath: string;
  listPageSize: number;
}

export function getChurchInformation() {
  return getJson<ChurchInformation>('/api/church-information');
}

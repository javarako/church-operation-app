import { getJson } from './http';

export interface ChurchInformation {
  name: string;
  address: string;
  contactInfo: string;
  treasurerName: string;
  bannerPath: string;
  logPath: string;
}

export function getChurchInformation() {
  return getJson<ChurchInformation>('/api/church-information');
}

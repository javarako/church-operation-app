import { getJson, postJson } from './http';

export type GivingType = 'MEMBER' | 'ANONYMOUS' | 'GROUP';

export interface Offering {
  id: string;
  givingType: GivingType;
  memberId?: string;
  giverLabel?: string;
  giverDisplayName?: string;
  offeringDate: string;
  offeringSunday: string;
  fundCategory: string;
  amount: number;
  paymentMethod?: string;
  memo?: string;
  incomeTransactionId?: string;
}

export interface OfferingPayload {
  givingType: GivingType;
  memberId?: string;
  giverLabel?: string;
  offeringDate: string;
  offeringSunday?: string;
  fundCategory: string;
  amount: number;
  paymentMethod?: string;
  memo?: string;
}

export function listOfferings() {
  return getJson<Offering[]>('/api/offerings');
}

export function createOffering(payload: OfferingPayload) {
  return postJson<OfferingPayload, Offering>('/api/offerings', payload);
}

import { deleteJson, getJson, postJson, putJson } from './http';

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

export function updateOffering(id: string, payload: OfferingPayload) {
  return putJson<OfferingPayload, Offering>(`/api/offerings/${id}`, payload);
}

export function deleteOffering(id: string) {
  return deleteJson<Offering>(`/api/offerings/${id}`);
}

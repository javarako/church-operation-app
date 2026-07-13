import { getJson, postJson, putJson } from './http';
import type { Role } from '../auth/authStore';

export interface Address {
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  provinceState?: string;
  postalZipCode?: string;
  country?: string;
}

export interface MemberPayload {
  primaryEmail: string;
  secondaryEmail?: string;
  primaryPhone?: string;
  secondaryPhone?: string;
  mobilePhone?: string;
  mailingAddress?: Address;
  displayName?: string;
  nickname?: string;
  birthDate?: string;
  groupCode?: string;
  membershipStatus?: string;
  offeringNumber?: string;
  faceImageAttachmentId?: string;
  householdName?: string;
  notes?: string;
  roles?: Role[];
  active?: boolean;
  locked?: boolean;
}

export interface MemberRecord extends MemberPayload {
  id: string;
  roles: Role[];
  active: boolean;
  locked: boolean;
  mustChangePassword: boolean;
  createdAt?: string;
}

export function listMembers(search: string) {
  const query = search.trim() ? `?search=${encodeURIComponent(search.trim())}` : '';
  return getJson<MemberRecord[]>(`/api/members${query}`);
}

export function createMember(payload: MemberPayload) {
  return postJson<MemberPayload, MemberRecord>('/api/members', payload);
}

export function updateMember(id: string, payload: MemberPayload) {
  return putJson<MemberPayload, MemberRecord>(`/api/members/${id}`, payload);
}

export function getMyProfile() {
  return getJson<MemberRecord>('/api/members/me');
}

export function updateMyProfile(payload: MemberPayload) {
  return putJson<MemberPayload, MemberRecord>('/api/members/me', payload);
}

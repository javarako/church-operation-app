import type { Role } from './authStore';

export const staffRoles: Role[] = ['ADMIN', 'TREASURER', 'PASTOR', 'MEMBERSHIP', 'VIEWER'];
export const adminRoles: Role[] = ['ADMIN'];
export const financeRoles: Role[] = ['ADMIN', 'TREASURER'];
export const membershipRoles: Role[] = ['ADMIN', 'MEMBERSHIP'];
export const reportRoles: Role[] = ['ADMIN', 'TREASURER', 'PASTOR', 'VIEWER'];
export const selfServiceRoles: Role[] = ['ADMIN', 'MEMBER'];

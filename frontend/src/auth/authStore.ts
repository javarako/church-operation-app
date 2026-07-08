export type Role = 'ADMIN' | 'TREASURER' | 'PASTOR' | 'MEMBERSHIP' | 'VIEWER' | 'MEMBER';

export interface CurrentUser {
  primaryEmail: string;
  displayName: string;
  roles: Role[];
  mustChangePassword: boolean;
  token: string;
}

export const authState: { currentUser: CurrentUser | null } = {
  currentUser: null,
};

export function setCurrentUser(user: CurrentUser | null) {
  authState.currentUser = user;
}

export function canAccessRoute(userRoles: Role[], allowedRoles: Role[]) {
  return userRoles.some((role) => allowedRoles.includes(role));
}

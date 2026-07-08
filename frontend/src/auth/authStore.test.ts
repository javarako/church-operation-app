import { describe, expect, it } from 'vitest';
import { canAccessRoute, type Role } from './authStore';

describe('authStore', () => {
  it('allows treasurer to access finance routes', () => {
    expect(canAccessRoute(['TREASURER'], ['TREASURER', 'ADMIN'])).toBe(true);
  });

  it('blocks member from finance routes', () => {
    expect(canAccessRoute(['MEMBER'], ['TREASURER', 'ADMIN'])).toBe(false);
  });

  it('allows member self-service route', () => {
    const roles: Role[] = ['MEMBER'];
    expect(canAccessRoute(roles, ['MEMBER', 'ADMIN'])).toBe(true);
  });
});

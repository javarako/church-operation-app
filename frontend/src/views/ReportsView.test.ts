import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/vue';
import ReportsView from './ReportsView.vue';
import { authState } from '../auth/authStore';

vi.mock('../api/reports', () => ({
  listWeeklyOfferingReport: vi.fn().mockResolvedValue([]),
  listMemberOfferingSummaryReport: vi.fn().mockResolvedValue([]),
  listOfficialTaxReport: vi.fn().mockResolvedValue([]),
  listFinancialBudgetReport: vi.fn().mockResolvedValue([]),
}));

describe('ReportsView', () => {
  afterEach(() => {
    cleanup();
    authState.currentUser = null;
    vi.clearAllMocks();
  });

  it('hides official tax tab for viewer', () => {
    authState.currentUser = {
      primaryEmail: 'viewer@example.com',
      displayName: 'Viewer',
      roles: ['VIEWER'],
      mustChangePassword: false,
      token: 'token',
    };

    render(ReportsView);

    expect(screen.queryByRole('button', { name: /official tax/i })).toBeNull();
  });

  it('shows official tax tab for treasurer', () => {
    authState.currentUser = {
      primaryEmail: 'treasurer@example.com',
      displayName: 'Treasurer',
      roles: ['TREASURER'],
      mustChangePassword: false,
      token: 'token',
    };

    render(ReportsView);

    expect(screen.getByRole('button', { name: /official tax/i })).toBeTruthy();
  });

  it('shows weekly report filters and export action', () => {
    authState.currentUser = {
      primaryEmail: 'admin@example.com',
      displayName: 'Admin',
      roles: ['ADMIN'],
      mustChangePassword: false,
      token: 'token',
    };

    render(ReportsView);

    expect(screen.getByLabelText('Start date')).toBeTruthy();
    expect(screen.getByLabelText('End date')).toBeTruthy();
    expect(screen.getByRole('button', { name: /export csv/i })).toBeTruthy();
  });
});

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/vue';
import DashboardView from './DashboardView.vue';
import { authState, type Role } from '../auth/authStore';
import { getChurchInformation } from '../api/churchInformation';
import { getDashboard } from '../api/dashboard';

vi.mock('../api/dashboard', () => ({
  getDashboard: vi.fn(),
}));

vi.mock('../api/churchInformation', () => ({
  getChurchInformation: vi.fn(),
}));

vi.mock('vue-chartjs', () => ({
  Bar: { template: '<div data-testid="offering-trend-chart"></div>' },
}));

const dashboardMock = vi.mocked(getDashboard);
const churchInformationMock = vi.mocked(getChurchInformation);

const dashboardResponse = {
  activeMemberCount: 128,
  newMemberCount: 3,
  ytdOfferingActual: 215600,
  ytdOfferingBudget: 298000,
  ytdOfferingPercentage: 72.35,
  ytdExpenseActual: 96420,
  ytdExpenseBudget: 156000,
  ytdExpensePercentage: 61.81,
  pendingChequeCount: 5,
  pendingChequeTotal: 8250,
  weekOfferingTotal: 12450,
  monthOfferingTotal: 38925,
  yearOfferingTotal: 215600,
  fiscalYearStart: '2026-01-01',
  fiscalYearEnd: '2026-12-31',
  offeringTrend: Array.from({ length: 12 }, (_, index) => ({
    sunday: `2026-0${index < 4 ? 4 : index < 8 ? 5 : 6}-${String((index % 4) * 7 + 1).padStart(2, '0')}`,
    amount: index * 100,
  })),
};

function setUser(role: Role) {
  authState.currentUser = {
    primaryEmail: `${role.toLowerCase()}@example.com`,
    displayName: `${role} User`,
    roles: [role],
    mustChangePassword: false,
    token: 'token',
  };
}

async function renderDashboard(role: Role = 'ADMIN') {
  setUser(role);
  render(DashboardView);
  await screen.findByText('Active Members');
}

describe('DashboardView', () => {
  beforeEach(() => {
    dashboardMock.mockResolvedValue(dashboardResponse);
    churchInformationMock.mockResolvedValue({
      name: 'Grace Community Church',
      address: '123 Church Street, Toronto, ON M1A 1A1',
      contactInfo: '416-555-0100',
      treasurerName: 'Daniel Kim',
      bannerPath: '/branding/church-banner.png',
      logPath: '/branding/church_logo.png',
      listPageSize: 20,
    });
  });

  afterEach(() => {
    cleanup();
    authState.currentUser = null;
    vi.clearAllMocks();
  });

  it.each<Role>(['ADMIN', 'TREASURER', 'PASTOR', 'VIEWER', 'MEMBERSHIP'])(
    'shows every dashboard section to %s',
    async (role) => {
      await renderDashboard(role);

      expect(screen.getByText('YTD Offering vs Budget')).toBeTruthy();
      expect(screen.getByText('YTD Expense vs Budget')).toBeTruthy();
      expect(screen.getByText('Pending Cheques')).toBeTruthy();
      expect(screen.getByText('Offering Overview')).toBeTruthy();
      expect(screen.getByText('Offering Trend')).toBeTruthy();
      expect(screen.queryByText('Recent Finance Activity')).toBeNull();
      expect(screen.queryByText('Membership')).toBeNull();
      expect(screen.getByTestId('offering-trend-chart')).toBeTruthy();
      expect(dashboardMock).toHaveBeenCalledTimes(1);
    },
  );

  it('shows approved card values and church branding', async () => {
    await renderDashboard();

    expect(screen.getByText('128')).toBeTruthy();
    expect(screen.getByText('New this month: 3')).toBeTruthy();
    expect(screen.getByText('72.35%')).toBeTruthy();
    expect(screen.getByText('$215,600.00 / $298,000.00')).toBeTruthy();
    expect(screen.getByText('5')).toBeTruthy();
    expect(screen.getByText('Total: $8,250.00')).toBeTruthy();
    expect(screen.getByText('Grace Community Church')).toBeTruthy();
    expect(screen.getByText('Treasurer: Daniel Kim')).toBeTruthy();
  });

  it('shows not budgeted when a budget is zero', async () => {
    dashboardMock.mockResolvedValue({
      ...dashboardResponse,
      ytdOfferingBudget: 0,
      ytdOfferingPercentage: null,
    });

    await renderDashboard();

    expect(screen.getByText('Not budgeted')).toBeTruthy();
  });

  it('shows a dashboard error without hiding church branding', async () => {
    dashboardMock.mockRejectedValue(new Error('Dashboard unavailable.'));
    setUser('ADMIN');
    render(DashboardView);

    expect(await screen.findByText('Dashboard unavailable.')).toBeTruthy();
    expect(await screen.findByText('Grace Community Church')).toBeTruthy();
  });
});

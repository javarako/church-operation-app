import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/vue';
import { createRouter, createWebHistory } from 'vue-router';
import DashboardView from './DashboardView.vue';
import { authState } from '../auth/authStore';
import { getChurchInformation } from '../api/churchInformation';
import { listFinanceTransactions } from '../api/finance';
import { listMembers } from '../api/members';
import { listFinancialBudgetReport, listWeeklyOfferingReport } from '../api/reports';

vi.mock('../api/reports', () => ({
  listWeeklyOfferingReport: vi.fn().mockResolvedValue([]),
  listFinancialBudgetReport: vi.fn().mockResolvedValue([]),
}));

vi.mock('../api/members', () => ({
  listMembers: vi.fn().mockResolvedValue([]),
}));

vi.mock('../api/finance', () => ({
  listFinanceTransactions: vi.fn().mockResolvedValue([]),
}));

vi.mock('../api/churchInformation', () => ({
  getChurchInformation: vi.fn().mockResolvedValue({
    name: 'Grace Community Church',
    address: '123 Church Street, Toronto, ON M1A 1A1',
    contactInfo: '416-555-0100',
    treasurerName: 'Daniel Kim',
    bannerPath: '/branding/church-banner.png',
    logPath: '/branding/church_logo.png',
  }),
}));

const weeklyReportMock = vi.mocked(listWeeklyOfferingReport);
const financialReportMock = vi.mocked(listFinancialBudgetReport);
const membersMock = vi.mocked(listMembers);
const financeMock = vi.mocked(listFinanceTransactions);
const churchInformationMock = vi.mocked(getChurchInformation);

function router() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: DashboardView },
      { path: '/members', component: { template: '<span>Members</span>' } },
      { path: '/offerings', component: { template: '<span>Offerings</span>' } },
      { path: '/finance', component: { template: '<span>Finance</span>' } },
      { path: '/budgets', component: { template: '<span>Budgets</span>' } },
      { path: '/reference-data', component: { template: '<span>Reference Data</span>' } },
      { path: '/reports', component: { template: '<span>Reports</span>' } },
      { path: '/profile', component: { template: '<span>Profile</span>' } },
    ],
  });
}

async function renderDashboard() {
  const testRouter = router();
  testRouter.push('/');
  await testRouter.isReady();
  return render(DashboardView, {
    global: {
      plugins: [testRouter],
    },
  });
}

describe('DashboardView', () => {
  beforeEach(() => {
    weeklyReportMock.mockResolvedValue([
      {
        offeringSunday: '2026-07-05',
        fundCategory: 'TITHE',
        givingType: 'MEMBER',
        paymentMethod: 'CASH',
        count: 2,
        totalAmount: 100,
      },
      {
        offeringSunday: '2026-07-05',
        fundCategory: 'MISSION',
        givingType: 'GROUP',
        paymentMethod: 'CHEQUE',
        count: 1,
        totalAmount: 50,
      },
    ]);
    financialReportMock.mockResolvedValue([
      {
        fiscalYear: 2026,
        budgetType: 'OFFERING_INCOME',
        category: 'TITHE',
        budget: 1000,
        actual: 650,
        variance: -350,
      },
      {
        fiscalYear: 2026,
        budgetType: 'EXPENSE',
        category: 'FACILITY',
        subCategory: 'RENT',
        budget: 400,
        actual: 125,
        variance: 275,
      },
    ]);
    membersMock.mockResolvedValue([
      {
        id: 'member-1',
        primaryEmail: 'active@example.com',
        displayName: 'Active Member',
        roles: ['MEMBER'],
        active: true,
        locked: false,
        mustChangePassword: false,
      },
      {
        id: 'member-2',
        primaryEmail: 'locked@example.com',
        displayName: 'Locked Member',
        roles: ['MEMBER'],
        active: false,
        locked: true,
        mustChangePassword: false,
      },
    ]);
    financeMock.mockResolvedValue([
      {
        id: 'income-1',
        type: 'INCOME',
        transactionDate: '2026-07-05',
        amount: 150,
        category: 'TITHE',
        hstIncluded: false,
        chequeCleared: false,
        sourceType: 'OFFERING',
      },
      {
        id: 'expense-1',
        type: 'EXPENSE',
        transactionDate: '2026-07-06',
        amount: 75,
        category: 'FACILITY',
        hstIncluded: true,
        chequeCleared: true,
        sourceType: 'MANUAL',
      },
    ]);
    churchInformationMock.mockResolvedValue({
      name: 'Grace Community Church',
      address: '123 Church Street, Toronto, ON M1A 1A1',
      contactInfo: '416-555-0100',
      treasurerName: 'Daniel Kim',
      bannerPath: '/branding/church-banner.png',
      logPath: '/branding/church_logo.png',
    });
  });

  afterEach(() => {
    cleanup();
    authState.currentUser = null;
    vi.clearAllMocks();
  });

  it('shows admin all dashboard sections and totals', async () => {
    authState.currentUser = {
      primaryEmail: 'admin@example.com',
      displayName: 'Admin Demo',
      roles: ['ADMIN'],
      mustChangePassword: false,
      token: 'token',
    };

    await renderDashboard();

    expect(await screen.findByText('This Week')).toBeTruthy();
    expect(screen.getAllByText('$150.00').length).toBeGreaterThan(0);
    expect(screen.getByText('Fiscal Snapshot')).toBeTruthy();
    expect(screen.getByText('Budgeted Income')).toBeTruthy();
    expect(screen.getByText('$1,000.00')).toBeTruthy();
    expect(screen.getByText('Membership')).toBeTruthy();
    expect(screen.getByText('Total Members')).toBeTruthy();
    expect(screen.getByText('Recent Finance Activity')).toBeTruthy();
    expect(await screen.findByText('Faith, Hope, Love')).toBeTruthy();
    expect(screen.getByText('Grace Community Church')).toBeTruthy();
    expect(screen.getByText('123 Church Street, Toronto, ON M1A 1A1')).toBeTruthy();
    expect(screen.getByText('416-555-0100')).toBeTruthy();
    expect(screen.getByText('Treasurer: Daniel Kim')).toBeTruthy();
    expect(screen.getByText('AD')).toBeTruthy();
    expect(screen.getByText('ADMIN')).toBeTruthy();
    expect(screen.queryByRole('navigation', { name: 'Dashboard quick links' })).toBeNull();
  });

  it('hides membership summary from treasurer', async () => {
    authState.currentUser = {
      primaryEmail: 'treasurer@example.com',
      displayName: 'Treasurer',
      roles: ['TREASURER'],
      mustChangePassword: false,
      token: 'token',
    };

    await renderDashboard();

    expect(await screen.findByText('Recent Finance Activity')).toBeTruthy();
    expect(screen.queryByText('Membership')).toBeNull();
  });

  it('shows membership user member summary without finance summary', async () => {
    authState.currentUser = {
      primaryEmail: 'membership@example.com',
      displayName: 'Membership',
      roles: ['MEMBERSHIP'],
      mustChangePassword: false,
      token: 'token',
    };

    await renderDashboard();

    expect(await screen.findByText('Membership')).toBeTruthy();
    expect(screen.queryByText('Recent Finance Activity')).toBeNull();
  });

  it('shows viewer read-only report content only', async () => {
    authState.currentUser = {
      primaryEmail: 'viewer@example.com',
      displayName: 'Viewer',
      roles: ['VIEWER'],
      mustChangePassword: false,
      token: 'token',
    };

    await renderDashboard();

    expect(await screen.findByText('This Week')).toBeTruthy();
    expect(screen.getByText('Fiscal Snapshot')).toBeTruthy();
    expect(screen.queryByText('Membership')).toBeNull();
    expect(screen.queryByText('Recent Finance Activity')).toBeNull();
  });

  it('shows a section error without hiding other sections', async () => {
    authState.currentUser = {
      primaryEmail: 'admin@example.com',
      displayName: 'Admin',
      roles: ['ADMIN'],
      mustChangePassword: false,
      token: 'token',
    };
    weeklyReportMock.mockRejectedValue(new Error('Weekly report unavailable.'));

    await renderDashboard();

    expect(await screen.findByText('Weekly report unavailable.')).toBeTruthy();
    await waitFor(() => expect(screen.getByText('Membership')).toBeTruthy());
    expect(screen.getByText('Recent Finance Activity')).toBeTruthy();
  });
});

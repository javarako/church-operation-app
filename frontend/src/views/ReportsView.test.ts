import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/vue';
import ReportsView from './ReportsView.vue';
import { authState } from '../auth/authStore';
import {
  listFinancialBudgetReport,
  listMemberOfferingSummaryReport,
  listOfficialTaxReport,
  listWeeklyOfferingReport,
} from '../api/reports';

vi.mock('../api/reports', () => ({
  listWeeklyOfferingReport: vi.fn().mockResolvedValue([]),
  listMemberOfferingSummaryReport: vi.fn().mockResolvedValue([]),
  listOfficialTaxReport: vi.fn().mockResolvedValue([]),
  listFinancialBudgetReport: vi.fn().mockResolvedValue([]),
}));

const weeklyReportMock = vi.mocked(listWeeklyOfferingReport);
const memberReportMock = vi.mocked(listMemberOfferingSummaryReport);
const taxReportMock = vi.mocked(listOfficialTaxReport);
const financialReportMock = vi.mocked(listFinancialBudgetReport);

function readBlobText(blob: Blob) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(blob);
  });
}

describe('ReportsView', () => {
  const originalCreateObjectUrl = URL.createObjectURL;
  const originalRevokeObjectUrl = URL.revokeObjectURL;
  const originalConfirm = window.confirm;

  beforeEach(() => {
    weeklyReportMock.mockResolvedValue([]);
    memberReportMock.mockResolvedValue([]);
    taxReportMock.mockResolvedValue([]);
    financialReportMock.mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
    authState.currentUser = null;
    vi.restoreAllMocks();
    vi.clearAllMocks();
    URL.createObjectURL = originalCreateObjectUrl;
    URL.revokeObjectURL = originalRevokeObjectUrl;
    window.confirm = originalConfirm;
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

    expect(screen.queryByRole('tab', { name: /official tax/i })).toBeNull();
  });

  it('shows official tax tab for treasurer with tab semantics', async () => {
    authState.currentUser = {
      primaryEmail: 'treasurer@example.com',
      displayName: 'Treasurer',
      roles: ['TREASURER'],
      mustChangePassword: false,
      token: 'token',
    };

    render(ReportsView);

    const weeklyTab = screen.getByRole('tab', { name: /weekly offerings/i });
    const taxTab = screen.getByRole('tab', { name: /official tax/i });

    expect(weeklyTab.getAttribute('aria-selected')).toBe('true');
    expect(taxTab.getAttribute('aria-selected')).toBe('false');

    await fireEvent.click(taxTab);

    expect(taxTab.getAttribute('aria-selected')).toBe('true');
    expect(weeklyTab.getAttribute('aria-selected')).toBe('false');
  });

  it('exports weekly csv from the loaded report rows', async () => {
    authState.currentUser = {
      primaryEmail: 'admin@example.com',
      displayName: 'Admin',
      roles: ['ADMIN'],
      mustChangePassword: false,
      token: 'token',
    };

    weeklyReportMock.mockResolvedValue([
      {
        offeringSunday: '2026-07-05',
        fundCategory: 'General',
        givingType: 'MEMBER',
        paymentMethod: 'CASH',
        count: 3,
        totalAmount: 125.5,
      },
    ]);

    const createObjectUrl = vi.fn((blob: Blob) => {
      (createObjectUrl as typeof createObjectUrl & { lastBlob?: Blob }).lastBlob = blob;
      return 'blob:weekly-report';
    });

    URL.createObjectURL = createObjectUrl as typeof URL.createObjectURL;
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    render(ReportsView);

    await screen.findByText('General');
    await fireEvent.click(screen.getByRole('button', { name: /export csv/i }));

    expect(createObjectUrl).toHaveBeenCalledTimes(1);

    const csvBlob = (createObjectUrl as typeof createObjectUrl & { lastBlob?: Blob }).lastBlob;

    expect(csvBlob).toBeDefined();
    await expect(readBlobText(csvBlob as Blob)).resolves.toContain(
      '"2026-07-05","General","MEMBER","CASH","3","125.5"',
    );
  });

  it('confirms official tax export before creating the csv', async () => {
    authState.currentUser = {
      primaryEmail: 'treasurer@example.com',
      displayName: 'Treasurer',
      roles: ['TREASURER'],
      mustChangePassword: false,
      token: 'token',
    };

    taxReportMock.mockResolvedValue([
      {
        churchName: 'Grace Church',
        churchAddress: '1 Main St',
        churchContactInfo: '555-0100',
        treasurerName: 'Pat Doe',
        taxYear: 2026,
        memberId: 'member-1',
        memberName: 'Alex Smith',
        primaryEmail: 'alex@example.com',
        offeringNumber: '42',
        memberAddress: '99 King St',
        givingDate: '2026-01-12',
        fundCategory: 'General',
        amount: 500,
      },
    ]);

    const createObjectUrl = vi.fn(() => 'blob:tax-report');
    URL.createObjectURL = createObjectUrl as typeof URL.createObjectURL;
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    const confirmSpy = vi.fn(() => false);
    window.confirm = confirmSpy;

    render(ReportsView);

    const taxTab = screen.getByRole('tab', { name: /official tax/i });
    await fireEvent.click(taxTab);
    await screen.findByText('Alex Smith');

    await fireEvent.click(screen.getByRole('button', { name: /export csv/i }));

    expect(confirmSpy).toHaveBeenCalledWith('This extraction is for official use. Continue?');
    expect(createObjectUrl).not.toHaveBeenCalled();

    confirmSpy.mockReturnValue(true);

    await fireEvent.click(screen.getByRole('button', { name: /export csv/i }));

    expect(createObjectUrl).toHaveBeenCalledTimes(1);
    expect(confirmSpy.mock.invocationCallOrder[1]).toBeLessThan(createObjectUrl.mock.invocationCallOrder[0]);
  });
});

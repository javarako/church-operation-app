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
import { listReferenceData } from '../api/referenceData';

vi.mock('../api/churchInformation', () => ({
  getChurchInformation: vi.fn().mockResolvedValue({
    name: 'Grace Community Church',
    address: '123 Church Street',
    contactInfo: '416-555-0100',
    treasurerName: 'Daniel Kim',
    bannerPath: '/branding/church-banner.png',
    logPath: '/branding/church_logo.png',
    listPageSize: 20,
  }),
}));

vi.mock('../api/reports', () => ({
  listWeeklyOfferingReport: vi.fn().mockResolvedValue([]),
  listMemberOfferingSummaryReport: vi.fn().mockResolvedValue([]),
  listOfficialTaxReport: vi.fn().mockResolvedValue([]),
  listFinancialBudgetReport: vi.fn().mockResolvedValue([]),
}));

vi.mock('../api/referenceData', () => ({
  listReferenceData: vi.fn().mockResolvedValue([]),
}));

const weeklyReportMock = vi.mocked(listWeeklyOfferingReport);
const memberReportMock = vi.mocked(listMemberOfferingSummaryReport);
const taxReportMock = vi.mocked(listOfficialTaxReport);
const financialReportMock = vi.mocked(listFinancialBudgetReport);
const referenceDataMock = vi.mocked(listReferenceData);

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
    referenceDataMock.mockImplementation((type) => {
      if (type === 'OFFERING_FUND_CATEGORY') {
        return Promise.resolve([
          { id: 'fund-1', type, code: 'TITHE', label: 'Tithe', sortOrder: 1, active: true },
          { id: 'fund-2', type, code: 'MISSION', label: 'Mission', sortOrder: 2, active: true },
        ]);
      }
      if (type === 'PAYMENT_METHOD') {
        return Promise.resolve([
          { id: 'payment-1', type, code: 'CASH', label: 'Cash', sortOrder: 1, active: true },
          { id: 'payment-2', type, code: 'CHEQUE', label: 'Cheque', sortOrder: 2, active: true },
        ]);
      }
      return Promise.resolve([]);
    });
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
    expect(weeklyTab.classList.contains('active-report-tab')).toBe(true);
    expect(weeklyTab.classList.contains('inactive-report-tab')).toBe(false);
    expect(taxTab.getAttribute('aria-selected')).toBe('false');
    expect(taxTab.classList.contains('inactive-report-tab')).toBe(true);

    await fireEvent.click(taxTab);

    expect(taxTab.getAttribute('aria-selected')).toBe('true');
    expect(taxTab.classList.contains('active-report-tab')).toBe(true);
    expect(taxTab.classList.contains('inactive-report-tab')).toBe(false);
    expect(weeklyTab.getAttribute('aria-selected')).toBe('false');
    expect(weeklyTab.classList.contains('inactive-report-tab')).toBe(true);
  });

  it('uses reference data dropdowns for weekly fund and payment filters', async () => {
    authState.currentUser = {
      primaryEmail: 'admin@example.com',
      displayName: 'Admin',
      roles: ['ADMIN'],
      mustChangePassword: false,
      token: 'token',
    };

    render(ReportsView);

    const fundSelect = await screen.findByLabelText('Fund/category');
    const paymentSelect = await screen.findByLabelText('Payment method');

    expect(fundSelect.tagName).toBe('SELECT');
    expect(paymentSelect.tagName).toBe('SELECT');

    await fireEvent.update(fundSelect, 'TITHE');
    await fireEvent.update(paymentSelect, 'CASH');
    await fireEvent.click(screen.getByRole('button', { name: /run report/i }));

    expect(weeklyReportMock).toHaveBeenLastCalledWith({
      start: expect.any(String),
      end: expect.any(String),
      fundCategory: 'TITHE',
      paymentMethod: 'CASH',
    });
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

  it('exports official tax csv with church metadata after confirmation', async () => {
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

    const createObjectUrl = vi.fn((blob: Blob) => {
      (createObjectUrl as typeof createObjectUrl & { lastBlob?: Blob }).lastBlob = blob;
      return 'blob:tax-report';
    });
    URL.createObjectURL = createObjectUrl as typeof URL.createObjectURL;
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    window.confirm = vi.fn(() => true);

    render(ReportsView);

    await fireEvent.click(screen.getByRole('tab', { name: /official tax/i }));
    await screen.findByText('Alex Smith');
    await fireEvent.click(screen.getByRole('button', { name: /export csv/i }));

    const csvBlob = (createObjectUrl as typeof createObjectUrl & { lastBlob?: Blob }).lastBlob;

    expect(csvBlob).toBeDefined();
    await expect(readBlobText(csvBlob as Blob)).resolves.toContain(
      '"Grace Church","1 Main St","555-0100","Pat Doe","2026","42","2026-01-12","Alex Smith","alex@example.com","99 King St","General","500"',
    );
  });

  it('uses offering number criteria and first column for member summaries', async () => {
    authState.currentUser = {
      primaryEmail: 'viewer@example.com',
      displayName: 'Viewer',
      roles: ['VIEWER'],
      mustChangePassword: false,
      token: 'token',
    };

    memberReportMock.mockResolvedValue([
      {
        memberId: 'member-1',
        memberName: 'Alex Smith',
        primaryEmail: 'alex@example.com',
        offeringNumber: '100',
        fundCategory: 'TITHE',
        count: 1,
        totalAmount: 25,
      },
    ]);

    render(ReportsView);

    await fireEvent.click(screen.getByRole('tab', { name: /member offerings/i }));
    await fireEvent.update(screen.getByLabelText('Offering number'), '100');
    await fireEvent.click(screen.getByRole('button', { name: /run report/i }));

    expect(memberReportMock).toHaveBeenLastCalledWith({
      start: expect.any(String),
      end: expect.any(String),
      offeringNumber: '100',
      fundCategory: '',
    });
    expect(screen.getAllByRole('columnheader')[0].textContent).toBe('Offering number');
    expect(await screen.findByText('Alex Smith')).toBeTruthy();
  });

  it('uses offering number criteria and first column for official tax reports', async () => {
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
        offeringNumber: '100',
        memberAddress: '99 King St',
        givingDate: '2026-01-12',
        fundCategory: 'General',
        amount: 500,
      },
    ]);

    render(ReportsView);

    await fireEvent.click(screen.getByRole('tab', { name: /official tax/i }));
    await fireEvent.update(screen.getByLabelText('Offering number'), '100');
    await fireEvent.click(screen.getByRole('button', { name: /run report/i }));

    expect(taxReportMock).toHaveBeenLastCalledWith({
      taxYear: expect.any(Number),
      offeringNumber: '100',
    });
    expect(screen.getAllByRole('columnheader')[0].textContent).toBe('Offering number');
    expect(await screen.findByText('Alex Smith')).toBeTruthy();
  });

  it('shows a form error and skips weekly api calls when end date is before start date', async () => {
    authState.currentUser = {
      primaryEmail: 'viewer@example.com',
      displayName: 'Viewer',
      roles: ['VIEWER'],
      mustChangePassword: false,
      token: 'token',
    };

    render(ReportsView);
    weeklyReportMock.mockClear();

    const dateInputs = screen.getAllByLabelText(/date/i);
    await fireEvent.update(dateInputs[0], '2026-07-10');
    await fireEvent.update(dateInputs[1], '2026-07-01');
    await fireEvent.click(screen.getByRole('button', { name: /run report/i }));

    expect(screen.getByText('Start date must be before or equal to end date.')).toBeTruthy();
    expect(weeklyReportMock).not.toHaveBeenCalled();
  });

  it('shows a form error and skips member api calls when end date is before start date', async () => {
    authState.currentUser = {
      primaryEmail: 'viewer@example.com',
      displayName: 'Viewer',
      roles: ['VIEWER'],
      mustChangePassword: false,
      token: 'token',
    };

    render(ReportsView);

    await fireEvent.click(screen.getByRole('tab', { name: /member offerings/i }));
    memberReportMock.mockClear();

    const dateInputs = screen.getAllByLabelText(/date/i);
    await fireEvent.update(dateInputs[0], '2026-08-10');
    await fireEvent.update(dateInputs[1], '2026-08-01');
    await fireEvent.click(screen.getByRole('button', { name: /run report/i }));

    expect(screen.getByText('Start date must be before or equal to end date.')).toBeTruthy();
    expect(memberReportMock).not.toHaveBeenCalled();
  });
});

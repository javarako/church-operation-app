import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/vue';
import ReportsView from './ReportsView.vue';
import { authState } from '../auth/authStore';
import {
  DEFAULT_THANK_YOU_NOTE,
  downloadTaxReceiptPdf,
  issueBatchTaxReceipts,
  issueTaxReceipt,
  listFinancialBudgetReport,
  listMemberOfferingSummaryReport,
  listTaxReceiptSummary,
  listWeeklyOfferingReport,
  replaceTaxReceipt,
  voidTaxReceipt,
} from '../api/reports';
import { listReferenceData } from '../api/referenceData';

vi.mock('../api/churchInformation', () => ({
  getChurchInformation: vi.fn().mockResolvedValue({ listPageSize: 20 }),
}));

vi.mock('../api/reports', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/reports')>();
  return {
    ...actual,
    listWeeklyOfferingReport: vi.fn().mockResolvedValue([]),
    listMemberOfferingSummaryReport: vi.fn().mockResolvedValue([]),
    listTaxReceiptSummary: vi.fn().mockResolvedValue([]),
    listFinancialBudgetReport: vi.fn().mockResolvedValue([]),
    issueTaxReceipt: vi.fn(),
    issueBatchTaxReceipts: vi.fn(),
    downloadTaxReceiptPdf: vi.fn(),
    voidTaxReceipt: vi.fn(),
    replaceTaxReceipt: vi.fn(),
  };
});

vi.mock('../api/referenceData', () => ({ listReferenceData: vi.fn().mockResolvedValue([]) }));

const weeklyMock = vi.mocked(listWeeklyOfferingReport);
const memberMock = vi.mocked(listMemberOfferingSummaryReport);
const taxSummaryMock = vi.mocked(listTaxReceiptSummary);
const financialMock = vi.mocked(listFinancialBudgetReport);
const issueMock = vi.mocked(issueTaxReceipt);
const batchMock = vi.mocked(issueBatchTaxReceipts);
const pdfMock = vi.mocked(downloadTaxReceiptPdf);
const voidMock = vi.mocked(voidTaxReceipt);
const replaceMock = vi.mocked(replaceTaxReceipt);
const referenceMock = vi.mocked(listReferenceData);

const receiptRow = {
  memberId: 'member-1', offeringNumber: '1001', donorName: 'Ada Wong',
  donorAddress: '100 Main St, Toronto, ON', taxYear: 2026, totalAmount: 500,
  receiptId: 'r1', receiptNumber: '2026-000001', receiptStatus: 'ISSUED' as const, sourceChanged: false,
};

function signIn(role: 'ADMIN' | 'TREASURER' | 'VIEWER') {
  authState.currentUser = {
    primaryEmail: `${role.toLowerCase()}@example.com`, displayName: role, roles: [role],
    mustChangePassword: false, token: 'token',
  };
}

describe('ReportsView', () => {
  const originalCreateObjectUrl = URL.createObjectURL;
  const originalRevokeObjectUrl = URL.revokeObjectURL;
  const originalConfirm = window.confirm;
  const originalPrompt = window.prompt;

  beforeEach(() => {
    weeklyMock.mockResolvedValue([]);
    memberMock.mockResolvedValue([]);
    taxSummaryMock.mockResolvedValue([]);
    financialMock.mockResolvedValue([]);
    batchMock.mockResolvedValue(new Blob(['zip'], { type: 'application/zip' }));
    pdfMock.mockResolvedValue(new Blob(['pdf'], { type: 'application/pdf' }));
    referenceMock.mockImplementation((type) => {
      const options = {
        OFFERING_FUND: [{ id: 'f1', type, code: 'GENERAL', label: 'General Fund', sortOrder: 1, active: true }],
        OFFERING_CATEGORY: [{
          id: 'c1', type, code: 'TITHE', label: 'Tithe', parentCode: 'GENERAL', sortOrder: 1, active: true,
        }],
        PAYMENT_METHOD: [{ id: 'p1', type, code: 'CHEQUE', label: 'Cheque', sortOrder: 1, active: true }],
        FINANCIAL_CATEGORY: [{ id: 'fc1', type, code: 'ADMIN', label: 'Administration', sortOrder: 1, active: true }],
        FINANCIAL_SUB_CATEGORY: [{
          id: 'fs1', type, code: 'OFFICE', label: 'Office Supplies', parentCode: 'ADMIN', sortOrder: 1, active: true,
        }],
      };
      return Promise.resolve(options[type as keyof typeof options] ?? []);
    });
  });

  afterEach(() => {
    cleanup();
    authState.currentUser = null;
    vi.clearAllMocks();
    URL.createObjectURL = originalCreateObjectUrl;
    URL.revokeObjectURL = originalRevokeObjectUrl;
    window.confirm = originalConfirm;
    window.prompt = originalPrompt;
  });

  it('keeps selected tab styling and hides official tax from viewers', async () => {
    signIn('VIEWER');
    render(ReportsView);
    const weekly = screen.getByRole('tab', { name: /weekly offerings/i });
    const member = screen.getByRole('tab', { name: /member offerings/i });
    expect(screen.queryByRole('tab', { name: /official tax/i })).toBeNull();
    expect(weekly.getAttribute('aria-selected')).toBe('true');
    await fireEvent.click(member);
    expect(member.getAttribute('aria-selected')).toBe('true');
    expect(member.classList.contains('active-report-tab')).toBe(true);
  });

  it('uses reference dropdowns and validates report date ranges', async () => {
    signIn('VIEWER');
    render(ReportsView);
    expect((await screen.findByLabelText('Fund')).tagName).toBe('SELECT');
    expect((await screen.findByLabelText('Category')).tagName).toBe('SELECT');
    expect((await screen.findByLabelText('Payment method')).tagName).toBe('SELECT');
    weeklyMock.mockClear();
    const dates = screen.getAllByLabelText(/date/i);
    await fireEvent.update(dates[0], '2026-07-10');
    await fireEvent.update(dates[1], '2026-07-01');
    await fireEvent.click(screen.getByRole('button', { name: /run report/i }));
    expect(screen.getByText('Start date must be before or equal to end date.')).toBeTruthy();
    expect(weeklyMock).not.toHaveBeenCalled();
  });

  it('shows annual receipt columns, default note, and numeric offering-number order', async () => {
    signIn('TREASURER');
    taxSummaryMock.mockResolvedValue([
      { ...receiptRow, memberId: 'm10', offeringNumber: '10', donorName: 'Ten' },
      { ...receiptRow, memberId: 'm2', offeringNumber: '2', donorName: 'Two' },
    ]);
    render(ReportsView);
    await fireEvent.click(screen.getByRole('tab', { name: /official tax/i }));
    expect(await screen.findByDisplayValue(DEFAULT_THANK_YOU_NOTE)).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Offering #' })).toBeTruthy();
    expect(screen.queryByRole('columnheader', { name: 'Giving Date' })).toBeNull();
    expect(screen.queryByRole('columnheader', { name: 'Fund / Category' })).toBeNull();
    const rows = screen.getAllByRole('row');
    expect(rows[1].textContent).toContain('Two');
    expect(rows[2].textContent).toContain('Ten');
  });

  it('issues and downloads an individual receipt', async () => {
    signIn('TREASURER');
    taxSummaryMock.mockResolvedValue([{ ...receiptRow, receiptId: undefined, receiptNumber: undefined, receiptStatus: undefined }]);
    issueMock.mockResolvedValue({ id: 'r1', receiptNumber: '2026-000001', status: 'ISSUED' });
    const createUrl = vi.fn(() => 'blob:receipt');
    URL.createObjectURL = createUrl as typeof URL.createObjectURL;
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    render(ReportsView);
    await fireEvent.click(screen.getByRole('tab', { name: /official tax/i }));
    await fireEvent.click(await screen.findByRole('button', { name: 'Issue receipt' }));
    expect(issueMock).toHaveBeenCalledWith({ taxYear: 2026, offeringNumber: '1001', thankYouNote: DEFAULT_THANK_YOU_NOTE });
    expect(pdfMock).toHaveBeenCalledWith('r1');
    expect(createUrl).toHaveBeenCalled();
  });

  it('downloads all receipts as a batch zip', async () => {
    signIn('ADMIN');
    const createUrl = vi.fn(() => 'blob:batch');
    URL.createObjectURL = createUrl as typeof URL.createObjectURL;
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    render(ReportsView);
    await fireEvent.click(screen.getByRole('tab', { name: /official tax/i }));
    await fireEvent.click(screen.getByRole('button', { name: 'Download all receipts' }));
    expect(batchMock).toHaveBeenCalledWith({ taxYear: expect.any(Number), thankYouNote: DEFAULT_THANK_YOU_NOTE });
    expect(createUrl).toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:batch');
  });

  it('shows source mismatch and voids with a required reason', async () => {
    signIn('TREASURER');
    taxSummaryMock.mockResolvedValue([{ ...receiptRow, sourceChanged: true }]);
    window.confirm = vi.fn(() => true);
    window.prompt = vi.fn(() => 'Donor address was incorrect');
    voidMock.mockResolvedValue({ id: 'r1', receiptNumber: '2026-000001', status: 'VOID' });
    render(ReportsView);
    await fireEvent.click(screen.getByRole('tab', { name: /official tax/i }));
    expect(await screen.findByText('Offerings changed after this receipt was issued.')).toBeTruthy();
    await fireEvent.click(screen.getByRole('button', { name: 'Void receipt' }));
    expect(window.confirm).toHaveBeenCalled();
    expect(voidMock).toHaveBeenCalledWith('r1', 'Donor address was incorrect');
  });

  it('replaces a void receipt with the current note', async () => {
    signIn('TREASURER');
    taxSummaryMock.mockResolvedValue([{ ...receiptRow, receiptStatus: 'VOID' }]);
    replaceMock.mockResolvedValue({ id: 'r2', receiptNumber: '2026-000002', status: 'ISSUED' });
    URL.createObjectURL = vi.fn(() => 'blob:replacement');
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    render(ReportsView);
    await fireEvent.click(screen.getByRole('tab', { name: /official tax/i }));
    await fireEvent.click(await screen.findByRole('button', { name: 'Replace receipt' }));
    expect(replaceMock).toHaveBeenCalledWith('r1', DEFAULT_THANK_YOU_NOTE);
    expect(pdfMock).toHaveBeenCalledWith('r2');
  });

  it('shows receipt validation failures without downloading', async () => {
    signIn('TREASURER');
    taxSummaryMock.mockResolvedValue([{ ...receiptRow, receiptId: undefined, receiptNumber: undefined, receiptStatus: undefined }]);
    issueMock.mockRejectedValue(new Error('Receipt validation failed: donor address is incomplete'));
    render(ReportsView);
    await fireEvent.click(screen.getByRole('tab', { name: /official tax/i }));
    await fireEvent.click(await screen.findByRole('button', { name: 'Issue receipt' }));
    expect(await screen.findByText('Receipt validation failed: donor address is incomplete')).toBeTruthy();
    expect(pdfMock).not.toHaveBeenCalled();
  });

  it('shows reference labels and budget utilization across operational reports', async () => {
    signIn('VIEWER');
    weeklyMock.mockResolvedValue([{
      offeringSunday: '2026-07-12',
      fundCode: 'GENERAL',
      categoryCode: 'TITHE',
      givingType: 'ANONYMOUS',
      paymentMethod: 'CHEQUE',
      count: 1,
      totalAmount: 125,
    }]);
    memberMock.mockResolvedValue([{
      memberId: 'member-1',
      memberName: 'Grace Park',
      primaryEmail: 'grace@example.com',
      offeringNumber: '1001',
      fundCode: 'GENERAL',
      categoryCode: 'TITHE',
      count: 2,
      totalAmount: 250,
    }]);
    financialMock.mockResolvedValue([
      {
        fiscalYear: 2026,
        budgetType: 'OFFERING_INCOME',
        category: 'GENERAL',
        subCategory: 'TITHE',
        budget: 1000,
        actual: 500,
        variance: -500,
      },
      {
        fiscalYear: 2026,
        budgetType: 'EXPENSE',
        category: 'ADMIN',
        subCategory: 'OFFICE',
        budget: 0,
        actual: 25,
        variance: 25,
      },
    ]);

    render(ReportsView);

    const weeklyRow = (await screen.findByText('2026-07-12')).closest('tr');
    expect(weeklyRow).not.toBeNull();
    expect(within(weeklyRow!).getByText('General Fund')).toBeTruthy();
    expect(within(weeklyRow!).getByText('Tithe')).toBeTruthy();
    expect(within(weeklyRow!).getByText('Anonymous')).toBeTruthy();
    expect(within(weeklyRow!).getByText('Cheque')).toBeTruthy();

    await fireEvent.click(screen.getByRole('tab', { name: /member offerings/i }));
    const memberRow = (await screen.findByText('Grace Park')).closest('tr');
    expect(within(memberRow!).getByText('General Fund')).toBeTruthy();
    expect(within(memberRow!).getByText('Tithe')).toBeTruthy();

    await fireEvent.click(screen.getByRole('tab', { name: /budget performance/i }));
    const incomeRow = (await screen.findByText('INCOME')).closest('tr');
    expect(within(incomeRow!).getByText('General Fund')).toBeTruthy();
    expect(within(incomeRow!).getByText('Tithe')).toBeTruthy();
    expect(within(incomeRow!).getByText('50.00%')).toBeTruthy();
    const expenseRow = screen.getByText('Administration').closest('tr');
    expect(within(expenseRow!).getByText('Office Supplies')).toBeTruthy();
    expect(within(expenseRow!).getByText('-')).toBeTruthy();
  });
});

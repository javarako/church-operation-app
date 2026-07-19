import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/vue';
import BudgetsView from './BudgetsView.vue';
import { listBudgets, type Budget, type BudgetType } from '../api/budgets';
import {
  listReferenceData,
  type ReferenceDataOption,
  type ReferenceDataType,
} from '../api/referenceData';

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

vi.mock('../api/budgets', () => ({
  listBudgets: vi.fn().mockResolvedValue([]),
  createBudget: vi.fn(),
  updateBudget: vi.fn(),
  deleteBudget: vi.fn(),
}));

vi.mock('../api/referenceData', () => ({
  listReferenceData: vi.fn().mockResolvedValue([]),
}));

const listBudgetsMock = vi.mocked(listBudgets);
const listReferenceDataMock = vi.mocked(listReferenceData);

function budget(
  id: string,
  budgetType: BudgetType,
  category: string,
  subCategory: string,
): Budget {
  return {
    id,
    fiscalYear: new Date().getFullYear(),
    budgetType,
    category,
    subCategory,
    budget: 1000,
    memo: id,
  };
}

function reference(
  type: ReferenceDataType,
  code: string,
  label: string,
  sortOrder: number,
  parentCode?: string,
): ReferenceDataOption {
  return {
    id: `${type}-${code}`,
    type,
    code,
    label,
    parentCode,
    sortOrder,
    active: true,
  };
}

function displayedBudgetMemos() {
  return within(screen.getByRole('table'))
    .getAllByRole('row')
    .slice(1)
    .map((row) => within(row).getAllByRole('cell')[5]?.textContent);
}

describe('BudgetsView', () => {
  beforeEach(() => {
    listBudgetsMock.mockResolvedValue([]);
    listReferenceDataMock.mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('removes carry over from budget type selectors', async () => {
    render(BudgetsView);

    await screen.findByText('Refreshed');

    const budgetTypeSelectors = screen.getAllByRole('combobox').filter((select) =>
      Array.from((select as HTMLSelectElement).options).some((option) => option.value === 'OFFERING_INCOME'),
    );

    expect(budgetTypeSelectors.length).toBeGreaterThan(0);
    budgetTypeSelectors.forEach((select) => {
      const values = Array.from((select as HTMLSelectElement).options).map((option) => option.value);

      expect(values).not.toContain('CARRY_OVER');
      expect(values).toContain('OFFERING_INCOME');
      expect(values).toContain('EXPENSE');
    });
  });

  it('displays offering income budgets as Income', async () => {
    listBudgetsMock.mockResolvedValue([{
      id: 'budget-1',
      fiscalYear: new Date().getFullYear(),
      budgetType: 'OFFERING_INCOME',
      category: 'GENERAL',
      subCategory: 'TITHE',
      budget: 1000,
    }]);

    render(BudgetsView);

    await screen.findByText('Refreshed');

    const budgetTypeSelectors = screen.getAllByRole('combobox').filter((select) =>
      Array.from((select as HTMLSelectElement).options).some((option) => option.value === 'OFFERING_INCOME'),
    );
    expect(budgetTypeSelectors).toHaveLength(2);
    budgetTypeSelectors.forEach((select) => {
      const incomeOption = Array.from((select as HTMLSelectElement).options)
        .find((option) => option.value === 'OFFERING_INCOME');
      expect(incomeOption?.textContent).toBe('Income');
    });
    expect(screen.getByRole('cell', { name: 'Income' })).toBeTruthy();
    expect(screen.queryByText('Offering income')).toBeNull();
  });

  it('sorts budgets by type and parent and child reference order', async () => {
    listBudgetsMock.mockResolvedValue([
      budget('expense-printing', 'EXPENSE', 'OFFICE', 'PRINTING'),
      budget('income-mission', 'OFFERING_INCOME', 'GENERAL', 'MISSION'),
      budget('expense-repair', 'EXPENSE', 'FACILITY', 'REPAIR'),
      budget('income-outreach', 'OFFERING_INCOME', 'MISSIONS', 'OUTREACH'),
      budget('expense-utilities', 'EXPENSE', 'FACILITY', 'UTILITIES'),
      budget('income-tithe', 'OFFERING_INCOME', 'GENERAL', 'TITHE'),
    ]);
    const referenceData: Partial<Record<ReferenceDataType, ReferenceDataOption[]>> = {
      OFFERING_FUND: [
        reference('OFFERING_FUND', 'GENERAL', 'General Fund', 10),
        reference('OFFERING_FUND', 'MISSIONS', 'Mission Fund', 5),
      ],
      OFFERING_CATEGORY: [
        reference('OFFERING_CATEGORY', 'MISSION', 'Mission', 20, 'GENERAL'),
        reference('OFFERING_CATEGORY', 'TITHE', 'Tithe', 10, 'GENERAL'),
        reference('OFFERING_CATEGORY', 'OUTREACH', 'Outreach', 10, 'MISSIONS'),
      ],
      FINANCIAL_CATEGORY: [
        reference('FINANCIAL_CATEGORY', 'OFFICE', 'Office', 20),
        reference('FINANCIAL_CATEGORY', 'FACILITY', 'Facility', 10),
      ],
      FINANCIAL_SUB_CATEGORY: [
        reference('FINANCIAL_SUB_CATEGORY', 'PRINTING', 'Printing', 10, 'OFFICE'),
        reference('FINANCIAL_SUB_CATEGORY', 'REPAIR', 'Repair', 20, 'FACILITY'),
        reference('FINANCIAL_SUB_CATEGORY', 'UTILITIES', 'Utilities', 10, 'FACILITY'),
      ],
    };
    listReferenceDataMock.mockImplementation((type) =>
      Promise.resolve(referenceData[type] ?? []),
    );

    render(BudgetsView);

    await screen.findByText('Refreshed');

    expect(displayedBudgetMemos()).toEqual([
      'income-outreach',
      'income-tithe',
      'income-mission',
      'expense-utilities',
      'expense-repair',
      'expense-printing',
    ]);
  });

  it('sorts unknown references last and preserves expense order when filtered', async () => {
    listBudgetsMock.mockResolvedValue([
      budget('expense-unknown', 'EXPENSE', 'UNKNOWN', 'OTHER'),
      budget('income-general', 'OFFERING_INCOME', 'GENERAL', 'TITHE'),
      budget('expense-office', 'EXPENSE', 'OFFICE', 'SUPPLIES'),
      budget('expense-facility', 'EXPENSE', 'FACILITY', 'REPAIR'),
    ]);
    const referenceData: Partial<Record<ReferenceDataType, ReferenceDataOption[]>> = {
      OFFERING_FUND: [
        reference('OFFERING_FUND', 'GENERAL', 'General Fund', 10),
      ],
      OFFERING_CATEGORY: [
        reference('OFFERING_CATEGORY', 'TITHE', 'Tithe', 10, 'GENERAL'),
      ],
      FINANCIAL_CATEGORY: [
        reference('FINANCIAL_CATEGORY', 'OFFICE', 'Office', 20),
        reference('FINANCIAL_CATEGORY', 'FACILITY', 'Facility', 10),
      ],
      FINANCIAL_SUB_CATEGORY: [
        reference('FINANCIAL_SUB_CATEGORY', 'SUPPLIES', 'Supplies', 10, 'OFFICE'),
        reference('FINANCIAL_SUB_CATEGORY', 'REPAIR', 'Repair', 10, 'FACILITY'),
      ],
    };
    listReferenceDataMock.mockImplementation((type) =>
      Promise.resolve(referenceData[type] ?? []),
    );

    render(BudgetsView);

    await screen.findByText('Refreshed');
    const typeFilter = screen.getAllByRole('combobox').find((select) =>
      Array.from((select as HTMLSelectElement).options)
        .some((option) => option.value === '' && option.textContent === 'All types'),
    );

    expect(typeFilter).toBeTruthy();
    await fireEvent.update(typeFilter!, 'EXPENSE');

    expect(displayedBudgetMemos()).toEqual([
      'expense-facility',
      'expense-office',
      'expense-unknown',
    ]);
  });

  it('sorts the complete list before applying pagination', async () => {
    listBudgetsMock.mockResolvedValue([
      ...Array.from({ length: 20 }, (_, index) =>
        budget(`expense-${String(index).padStart(2, '0')}`, 'EXPENSE', 'UNKNOWN', 'OTHER')),
      budget('income-last-from-api', 'OFFERING_INCOME', 'UNKNOWN', 'OTHER'),
    ]);

    render(BudgetsView);

    await screen.findByText('Refreshed');

    expect(displayedBudgetMemos()[0]).toBe('income-last-from-api');
    expect(displayedBudgetMemos()).not.toContain('expense-19');
    expect(screen.getByText('Showing 1-20 of 21')).toBeTruthy();
  });
});

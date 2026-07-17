import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/vue';
import BudgetsView from './BudgetsView.vue';
import { listBudgets } from '../api/budgets';
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
});

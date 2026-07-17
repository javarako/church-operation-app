import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, within } from '@testing-library/vue';
import OfferingsView from './OfferingsView.vue';

vi.mock('../api/offerings', () => ({
  listOfferings: vi.fn().mockResolvedValue([{
    id: 'offering-1',
    givingType: 'ANONYMOUS',
    giverLabel: 'Anonymous',
    offeringDate: '2026-07-12',
    offeringSunday: '2026-07-12',
    fundCode: 'GENERAL',
    categoryCode: 'TITHE',
    amount: 125,
    paymentMethod: 'CHEQUE',
    incomeTransactionId: 'income-1',
  }]),
  createOffering: vi.fn(),
  updateOffering: vi.fn(),
  deleteOffering: vi.fn(),
}));

vi.mock('../api/members', () => ({
  listMembers: vi.fn().mockResolvedValue([]),
}));

vi.mock('../api/referenceData', () => ({
  listReferenceData: vi.fn().mockImplementation((type) => {
    if (type === 'OFFERING_FUND') {
      return Promise.resolve([{ id: 'f1', type, code: 'GENERAL', label: 'General Fund', sortOrder: 1, active: true }]);
    }
    if (type === 'OFFERING_CATEGORY') {
      return Promise.resolve([{
        id: 'c1', type, code: 'TITHE', label: 'Tithe', parentCode: 'GENERAL', sortOrder: 1, active: true,
      }]);
    }
    return Promise.resolve([{ id: 'p1', type, code: 'CHEQUE', label: 'Cheque', sortOrder: 1, active: true }]);
  }),
}));

vi.mock('../api/churchInformation', () => ({
  getChurchInformation: vi.fn().mockResolvedValue({ listPageSize: 20 }),
}));

describe('OfferingsView', () => {
  afterEach(cleanup);

  it('shows the payment method label in the offering list', async () => {
    render(OfferingsView);

    const row = (await screen.findByRole('button', { name: 'Delete offering' })).closest('tr');
    expect(row).not.toBeNull();
    expect(within(row!).getByText('Cheque')).toBeTruthy();
    expect(within(row!).queryByText('CHEQUE')).toBeNull();
  });
});

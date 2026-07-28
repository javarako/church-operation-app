import { defineComponent, nextTick, ref } from 'vue';
import { cleanup, fireEvent, render, screen } from '@testing-library/vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { usePagination } from './usePagination';
import {
  applyChurchInformation,
  resetChurchInformationStore,
} from '../stores/churchInformationStore';

vi.mock('../api/churchInformation', () => ({
  getChurchInformation: vi.fn(),
}));

const PaginationHarness = defineComponent({
  setup() {
    const rows = ref(Array.from({ length: 45 }, (_, index) => index + 1));
    return usePagination(rows);
  },
  template: `
    <div>
      <span data-testid="page">{{ currentPage }}</span>
      <span data-testid="end">{{ endRow }}</span>
      <button type="button" @click="goToPage(2)">Page 2</button>
    </div>
  `,
});

describe('usePagination', () => {
  afterEach(() => {
    cleanup();
    resetChurchInformationStore();
  });

  it('applies a changed shared page size and returns to page one', async () => {
    applyChurchInformation(information(20));
    render(PaginationHarness);
    await fireEvent.click(screen.getByRole('button', { name: 'Page 2' }));
    expect(screen.getByTestId('page').textContent).toBe('2');

    applyChurchInformation(information(50));
    await nextTick();

    expect(screen.getByTestId('page').textContent).toBe('1');
    expect(screen.getByTestId('end').textContent).toBe('45');
  });
});

function information(listPageSize: number) {
  return {
    name: 'Church',
    address: 'Address',
    contactInfo: '',
    treasurerName: '',
    charityRegistrationNumber: '',
    receiptIssueLocation: '',
    website: '',
    bannerPath: '/banner.png',
    logPath: '/logo.png',
    timeZone: 'America/Toronto',
    fiscalYearStartMonth: 1,
    listPageSize,
    applicationVersion: '1.0.0',
  };
}

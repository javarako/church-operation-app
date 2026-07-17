import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/vue';
import ReferenceDataView from './ReferenceDataView.vue';
import {
  createReferenceData,
  deleteReferenceData,
  listAllReferenceData,
} from '../api/referenceData';

vi.mock('../api/referenceData', () => ({
  listReferenceData: vi.fn().mockResolvedValue([]),
  listAllReferenceData: vi.fn(),
  createReferenceData: vi.fn(),
  updateReferenceData: vi.fn(),
  deleteReferenceData: vi.fn().mockResolvedValue(undefined),
}));

vi.mock('../api/churchInformation', () => ({
  getChurchInformation: vi.fn().mockResolvedValue({ listPageSize: 20 }),
}));

const listAllMock = vi.mocked(listAllReferenceData);
const createMock = vi.mocked(createReferenceData);

describe('ReferenceDataView', () => {
  beforeEach(() => {
    listAllMock.mockImplementation(async (type) => type === 'GROUP_CODE' ? [{
      id: 'ref-1',
      type: 'GROUP_CODE',
      code: 'CHOIR',
      label: 'Choir',
      sortOrder: 10,
      active: true,
    }] : []);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('deletes a reference value from its row trash action after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(ReferenceDataView);

    await fireEvent.click(await screen.findByRole('button', { name: 'Delete reference value Choir' }));

    expect(deleteReferenceData).toHaveBeenCalledWith('ref-1');
  });

  it('keeps type and code read-only after selecting an existing value', async () => {
    render(ReferenceDataView);

    await fireEvent.click(await screen.findByText('Choir'));

    expect((screen.getByLabelText('Type') as HTMLSelectElement).disabled).toBe(true);
    expect((screen.getByLabelText('Code') as HTMLInputElement).disabled).toBe(true);
  });

  it('offers Committee Code as a maintainable type', async () => {
    render(ReferenceDataView);

    expect((await screen.findAllByRole('option', { name: 'Committee code' })).length).toBe(2);
  });

  it('refreshes financial category parents immediately after creation', async () => {
    let financialCategories = [{
      id: 'office',
      type: 'FINANCIAL_CATEGORY' as const,
      code: 'OFFICE',
      label: 'Office',
      sortOrder: 10,
      active: true,
    }];
    listAllMock.mockImplementation(async (type) => {
      if (type === 'FINANCIAL_CATEGORY') {
        return financialCategories;
      }
      return [];
    });
    createMock.mockImplementation(async (payload) => {
      const created = {
        id: 'ministry',
        ...payload,
        type: 'FINANCIAL_CATEGORY' as const,
        code: 'MINISTRY',
        label: 'Ministry',
      };
      financialCategories = [...financialCategories, created];
      return created;
    });
    render(ReferenceDataView);

    await fireEvent.update(screen.getByLabelText('Type'), 'FINANCIAL_CATEGORY');
    await fireEvent.update(screen.getByLabelText('Code'), 'MINISTRY');
    await fireEvent.update(screen.getByLabelText('Label'), 'Ministry');
    await fireEvent.click(screen.getByRole('button', { name: 'Create value' }));
    await waitFor(() => expect(createMock).toHaveBeenCalled());

    await fireEvent.click(screen.getByRole('button', { name: 'New value' }));
    await fireEvent.update(screen.getByLabelText('Type'), 'FINANCIAL_SUB_CATEGORY');

    expect(await screen.findByRole('option', { name: 'Ministry' })).toBeTruthy();
  });

  it('displays parent labels for child reference data rows', async () => {
    listAllMock.mockImplementation(async (type) => {
      if (type === 'OFFERING_FUND') {
        return [{
          id: 'fund-general',
          type,
          code: 'GENERAL',
          label: 'General Fund',
          sortOrder: 10,
          active: true,
        }];
      }
      if (type === 'OFFERING_CATEGORY') {
        return [{
          id: 'category-tithe',
          type,
          code: 'TITHE',
          label: 'Tithe',
          parentCode: 'GENERAL',
          sortOrder: 10,
          active: true,
        }];
      }
      if (type === 'FINANCIAL_CATEGORY') {
        return [{
          id: 'category-admin',
          type,
          code: 'ADMIN',
          label: 'Administration',
          sortOrder: 10,
          active: true,
        }];
      }
      if (type === 'FINANCIAL_SUB_CATEGORY') {
        return [{
          id: 'subcategory-office',
          type,
          code: 'OFFICE',
          label: 'Office Supplies',
          parentCode: 'ADMIN',
          sortOrder: 10,
          active: true,
        }];
      }
      return [];
    });
    render(ReferenceDataView);

    const listTypeSelector = screen.getAllByRole('combobox')[0];
    await fireEvent.update(listTypeSelector, 'OFFERING_CATEGORY');
    const offeringRow = (await screen.findByText('Tithe')).closest('tr');
    expect(offeringRow).not.toBeNull();
    expect(within(offeringRow!).getByText('General Fund')).toBeTruthy();
    expect(within(offeringRow!).queryByText('GENERAL')).toBeNull();

    await fireEvent.update(listTypeSelector, 'FINANCIAL_SUB_CATEGORY');
    const financialRow = (await screen.findByText('Office Supplies')).closest('tr');
    expect(financialRow).not.toBeNull();
    expect(within(financialRow!).getByText('Administration')).toBeTruthy();
    expect(within(financialRow!).queryByText('ADMIN')).toBeNull();
  });
});

import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/vue';
import ReferenceDataView from './ReferenceDataView.vue';
import { deleteReferenceData } from '../api/referenceData';

vi.mock('../api/referenceData', () => ({
  listReferenceData: vi.fn().mockResolvedValue([{
    id: 'ref-1',
    type: 'GROUP_CODE',
    code: 'CHOIR',
    label: 'Choir',
    sortOrder: 10,
    active: true,
  }]),
  createReferenceData: vi.fn(),
  updateReferenceData: vi.fn(),
  deleteReferenceData: vi.fn().mockResolvedValue(undefined),
}));

vi.mock('../api/churchInformation', () => ({
  getChurchInformation: vi.fn().mockResolvedValue({ listPageSize: 20 }),
}));

describe('ReferenceDataView deletion', () => {
  afterEach(cleanup);

  it('deletes a reference value from its row trash action after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(ReferenceDataView);

    await fireEvent.click(await screen.findByRole('button', { name: 'Delete reference value Choir' }));

    expect(deleteReferenceData).toHaveBeenCalledWith('ref-1');
  });
});

import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/vue';
import MembersView from './MembersView.vue';

vi.mock('../api/members', () => ({
  listMembers: vi.fn().mockResolvedValue([{
    id: 'member-1',
    primaryEmail: 'grace@example.com',
    displayName: 'Grace Park',
    nickname: 'Grace',
    roles: ['MEMBER'],
    active: true,
    locked: false,
    mustChangePassword: false,
  }]),
  createMember: vi.fn(),
  updateMember: vi.fn(),
  getMemberImage: vi.fn().mockRejectedValue(new Error('No image')),
  getSelfImage: vi.fn(),
  replaceMemberImage: vi.fn(),
  replaceSelfImage: vi.fn(),
  removeMemberImage: vi.fn(),
  removeSelfImage: vi.fn(),
}));

vi.mock('../api/referenceData', () => ({
  listReferenceData: vi.fn().mockResolvedValue([]),
}));

vi.mock('../api/churchInformation', () => ({
  getChurchInformation: vi.fn().mockResolvedValue({ listPageSize: 20 }),
}));

describe('MembersView member images', () => {
  afterEach(cleanup);

  it('shows a row avatar and selected-member image editor', async () => {
    render(MembersView);

    const name = await screen.findByText('Grace Park');
    expect(screen.getByLabelText('Grace Park profile image')).toBeTruthy();

    await fireEvent.click(name);

    expect(screen.getByRole('button', { name: 'Replace image' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Remove image' })).toBeTruthy();
  });
});

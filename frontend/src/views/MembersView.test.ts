import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/vue';
import MembersView from './MembersView.vue';
import { deleteMember, updateMember } from '../api/members';

vi.mock('../api/members', () => ({
  listMembers: vi.fn().mockResolvedValue([{
    id: 'member-1',
    primaryEmail: 'grace@example.com',
    displayName: 'Grace Park',
    nickname: 'Grace',
    groupCode: 'ADULT',
    membershipStatus: 'ACTIVE',
    committeeCodes: ['WORSHIP', 'LEGACY'],
    roles: ['MEMBER'],
    active: true,
    locked: false,
    mustChangePassword: false,
  }]),
  createMember: vi.fn(),
  updateMember: vi.fn().mockImplementation((id, payload) => Promise.resolve({
    id,
    ...payload,
    mustChangePassword: false,
  })),
  deleteMember: vi.fn().mockResolvedValue(undefined),
  getMemberImage: vi.fn().mockRejectedValue(new Error('No image')),
  getSelfImage: vi.fn(),
  replaceMemberImage: vi.fn(),
  replaceSelfImage: vi.fn(),
  removeMemberImage: vi.fn(),
  removeSelfImage: vi.fn(),
}));

vi.mock('../api/referenceData', () => ({
  listReferenceData: vi.fn().mockImplementation((type) => {
    const options = {
      GROUP_CODE: [{ id: 'g1', type, code: 'ADULT', label: 'Adult', sortOrder: 1, active: true }],
      MEMBERSHIP_STATUS: [{ id: 's1', type, code: 'ACTIVE', label: 'Active', sortOrder: 1, active: true }],
      COMMITTEE_CODE: [
        { id: 'c1', type, code: 'WORSHIP', label: 'Worship', sortOrder: 1, active: true },
        { id: 'c2', type, code: 'OUTREACH', label: 'Outreach', sortOrder: 2, active: true },
      ],
    };
    return Promise.resolve(options[type as keyof typeof options] ?? []);
  }),
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

  it('deletes a member from the row trash action after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(MembersView);

    await fireEvent.click(await screen.findByRole('button', { name: 'Delete member Grace Park' }));

    expect(deleteMember).toHaveBeenCalledWith('member-1');
  });

  it('shows reference labels and saves multiple committee assignments', async () => {
    render(MembersView);

    const memberRow = (await screen.findByText('grace@example.com')).closest('tr');
    expect(memberRow).not.toBeNull();
    expect(within(memberRow!).getByText('Adult')).toBeTruthy();
    expect(within(memberRow!).getByText('Active')).toBeTruthy();

    await fireEvent.click(screen.getByText('Grace Park'));
    expect((screen.getByRole('checkbox', { name: 'Worship' }) as HTMLInputElement).checked).toBe(true);
    expect((screen.getByRole('checkbox', { name: 'LEGACY (Inactive)' }) as HTMLInputElement).checked).toBe(true);
    await fireEvent.click(screen.getByRole('checkbox', { name: 'LEGACY (Inactive)' }));
    await fireEvent.click(screen.getByRole('checkbox', { name: 'Outreach' }));
    await fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(updateMember).toHaveBeenCalledWith(
      'member-1',
      expect.objectContaining({ committeeCodes: ['WORSHIP', 'OUTREACH'] }),
    );
  });
});

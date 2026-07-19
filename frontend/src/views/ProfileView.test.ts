import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/vue';
import ProfileView from './ProfileView.vue';
import { getSelfImage } from '../api/members';

vi.mock('../api/members', () => ({
  getMyProfile: vi.fn().mockResolvedValue({
    id: 'member-1',
    primaryEmail: 'grace@example.com',
    displayName: 'Grace Park',
    roles: ['MEMBER'],
    active: true,
    locked: false,
    mustChangePassword: false,
  }),
  updateMyProfile: vi.fn(),
  getSelfImage: vi.fn().mockRejectedValue(new Error('No image')),
  getMemberImage: vi.fn(),
  replaceMemberImage: vi.fn(),
  replaceSelfImage: vi.fn(),
  removeMemberImage: vi.fn(),
  removeSelfImage: vi.fn(),
}));

describe('ProfileView member image', () => {
  afterEach(cleanup);

  it('shows the self-service image editor after loading the profile', async () => {
    render(ProfileView);

    expect(await screen.findByLabelText('Grace Park profile image')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Replace image' })).toBeTruthy();
    expect(getSelfImage).toHaveBeenCalledOnce();
  });
});

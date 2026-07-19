import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue';
import MemberAvatar from './MemberAvatar.vue';
import MemberImageEditor from './MemberImageEditor.vue';
import {
  getMemberImage,
  getSelfImage,
  removeMemberImage,
  removeSelfImage,
  replaceMemberImage,
  replaceSelfImage,
} from '../api/members';

vi.mock('../api/members', () => ({
  getMemberImage: vi.fn(),
  getSelfImage: vi.fn(),
  removeMemberImage: vi.fn(),
  removeSelfImage: vi.fn(),
  replaceMemberImage: vi.fn(),
  replaceSelfImage: vi.fn(),
}));

const getMemberImageMock = vi.mocked(getMemberImage);
const getSelfImageMock = vi.mocked(getSelfImage);
const removeMemberImageMock = vi.mocked(removeMemberImage);
const removeSelfImageMock = vi.mocked(removeSelfImage);
const replaceMemberImageMock = vi.mocked(replaceMemberImage);
const replaceSelfImageMock = vi.mocked(replaceSelfImage);

describe('member image components', () => {
  beforeEach(() => {
    getMemberImageMock.mockRejectedValue(new Error('No image'));
    getSelfImageMock.mockRejectedValue(new Error('No image'));
    replaceMemberImageMock.mockResolvedValue({ id: 'member-1' } as never);
    replaceSelfImageMock.mockResolvedValue({ id: 'member-1' } as never);
    removeMemberImageMock.mockResolvedValue(undefined);
    removeSelfImageMock.mockResolvedValue(undefined);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('uses initials when no image is available', () => {
    render(MemberAvatar, { props: { name: 'Grace Park', size: 38 } });

    expect(screen.getByText('GP')).toBeTruthy();
  });

  it('uploads an accepted image for a managed member', async () => {
    render(MemberImageEditor, { props: { memberId: 'member-1', name: 'Grace Park' } });
    const file = new File(['png'], 'face.png', { type: 'image/png' });

    await fireEvent.change(screen.getByLabelText('Choose member image'), { target: { files: [file] } });

    await waitFor(() => expect(replaceMemberImageMock).toHaveBeenCalledWith('member-1', file));
  });

  it('rejects a file larger than five megabytes before upload', async () => {
    render(MemberImageEditor, { props: { memberId: 'member-1', name: 'Grace Park' } });
    const file = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'face.png', { type: 'image/png' });

    await fireEvent.change(screen.getByLabelText('Choose member image'), { target: { files: [file] } });

    expect(await screen.findByText('Member image must not exceed 5 MB.')).toBeTruthy();
    expect(replaceMemberImageMock).not.toHaveBeenCalled();
  });

  it('removes a managed member image', async () => {
    render(MemberImageEditor, { props: { memberId: 'member-1', name: 'Grace Park' } });

    await fireEvent.click(screen.getByRole('button', { name: 'Remove image' }));

    expect(removeMemberImageMock).toHaveBeenCalledWith('member-1');
  });

  it('uses self-service APIs in profile mode', async () => {
    render(MemberImageEditor, { props: { memberId: 'member-1', name: 'Grace Park', self: true } });
    const file = new File(['png'], 'face.png', { type: 'image/png' });

    await fireEvent.change(screen.getByLabelText('Choose member image'), { target: { files: [file] } });
    await fireEvent.click(screen.getByRole('button', { name: 'Remove image' }));

    await waitFor(() => expect(replaceSelfImageMock).toHaveBeenCalledWith(file));
    expect(removeSelfImageMock).toHaveBeenCalledOnce();
    expect(replaceMemberImageMock).not.toHaveBeenCalled();
  });
});

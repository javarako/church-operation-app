import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getChurchInformation, type ChurchInformation } from '../api/churchInformation';
import {
  applyChurchInformation,
  churchInformationState,
  loadChurchInformation,
  resetChurchInformationStore,
} from './churchInformationStore';

vi.mock('../api/churchInformation', () => ({
  getChurchInformation: vi.fn(),
}));

const getChurchInformationMock = vi.mocked(getChurchInformation);
const initial: ChurchInformation = {
  name: 'Grace Community Church',
  address: '123 Church Street',
  contactInfo: '416-555-0100',
  treasurerName: 'Daniel Kim',
  charityRegistrationNumber: '123456789RR0001',
  receiptIssueLocation: 'Toronto, Ontario',
  website: 'https://church.example.org',
  bannerPath: '/branding/church_banner_sample.png',
  logPath: '/branding/church_logo_sample.png',
  timeZone: 'America/Toronto',
  fiscalYearStartMonth: 1,
  listPageSize: 20,
  applicationVersion: '1.0.0',
};

describe('churchInformationStore', () => {
  beforeEach(() => {
    resetChurchInformationStore();
    getChurchInformationMock.mockReset();
    getChurchInformationMock.mockResolvedValue(initial);
  });

  it('loads once and shares the cached information', async () => {
    const [first, second] = await Promise.all([
      loadChurchInformation(),
      loadChurchInformation(),
    ]);

    expect(first).toEqual(initial);
    expect(second).toEqual(initial);
    expect(churchInformationState.value).toEqual(initial);
    expect(getChurchInformationMock).toHaveBeenCalledTimes(1);
  });

  it('applies saved church information without another API request', async () => {
    await loadChurchInformation();

    applyChurchInformation({ ...initial, name: 'Updated Church', logPath: '/api/logo?v=2' });

    expect(churchInformationState.value?.name).toBe('Updated Church');
    expect(churchInformationState.value?.logPath).toBe('/api/logo?v=2');
    expect(getChurchInformationMock).toHaveBeenCalledTimes(1);
  });
});

import { afterEach, describe, expect, it, vi } from 'vitest';
import { calculateComingSunday } from './offeringDates';

describe('calculateComingSunday', () => {
  afterEach(() => vi.unstubAllEnvs());

  it('returns the same Sunday date in a timezone ahead of UTC', () => {
    vi.stubEnv('TZ', 'Asia/Seoul');

    expect(calculateComingSunday('2026-07-10')).toBe('2026-07-12');
  });
});

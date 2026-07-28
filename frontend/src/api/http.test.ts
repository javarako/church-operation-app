import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { authState } from '../auth/authStore';
import { deleteEmpty, getBlob, getBlobResponse, postBlob, postJson, putFile } from './http';
import {
  closeYearEndReport,
  downloadYearlyOfferingReport,
  getYearEndClosingStatus,
  reopenYearEndReport,
} from './reports';

describe('HTTP file helpers', () => {
  beforeEach(() => {
    authState.currentUser = {
      primaryEmail: 'admin@example.com',
      displayName: 'Admin',
      roles: ['ADMIN'],
      mustChangePassword: false,
      token: 'issued-token',
    };
  });

  afterEach(() => {
    authState.currentUser = null;
    vi.unstubAllGlobals();
  });

  it('keeps JSON content type for JSON requests', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: true }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);

    await postJson('/api/example', { value: 1 });

    const headers = new Headers(fetchMock.mock.calls[0][1].headers);
    expect(headers.get('Content-Type')).toBe('application/json');
  });

  it('does not set a multipart boundary manually', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 'member-1' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);

    await putFile('/api/members/member-1/image', 'file', new File(['image'], 'face.png', { type: 'image/png' }));

    const headers = new Headers(fetchMock.mock.calls[0][1].headers);
    expect(headers.has('Content-Type')).toBe(false);
    expect(fetchMock.mock.calls[0][1].body).toBeInstanceOf(FormData);
  });

  it('adds authorization to Blob downloads', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(new Blob(['image']), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await getBlob('/api/members/member-1/image');

    const headers = new Headers(fetchMock.mock.calls[0][1].headers);
    expect(headers.get('Authorization')).toBe('Bearer issued-token');
    expect(result).toBeInstanceOf(Blob);
  });

  it('returns headers for filename-aware downloads', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('xlsx', {
      status: 200,
      headers: {
        'Content-Disposition': 'attachment; filename=yearly-offerings-2025-closed-v1.xlsx',
      },
    }));
    vi.stubGlobal('fetch', fetchMock);

    const response = await getBlobResponse('/api/reports/yearly-offerings.xlsx?fiscalYear=2025');

    expect(response.headers.get('Content-Disposition')).toContain('closed-v1.xlsx');
    const headers = new Headers(fetchMock.mock.calls[0][1].headers);
    expect(headers.get('Authorization')).toBe('Bearer issued-token');
  });

  it('uses the server filename for yearly workbook downloads', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('xlsx', {
      status: 200,
      headers: {
        'Content-Disposition': 'attachment; filename="yearly-offerings-2025-closed-v2.xlsx"',
      },
    }));
    vi.stubGlobal('fetch', fetchMock);

    const download = await downloadYearlyOfferingReport({ fiscalYear: 2025 });

    expect(download.blob).toBeInstanceOf(Blob);
    expect(download.filename).toBe('yearly-offerings-2025-closed-v2.xlsx');
  });

  it('loads status and posts password-confirmed closing actions', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        fiscalYear: 2025,
        fiscalEndDate: '2025-12-31',
        closeEligible: true,
        offering: { reportType: 'OFFERING', status: 'NOT_CLOSED' },
        expenditure: { reportType: 'EXPENDITURE', status: 'NOT_CLOSED' },
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        reportType: 'OFFERING', status: 'CLOSED', version: 1,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        reportType: 'OFFERING', status: 'REOPENED', version: 1,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);

    await getYearEndClosingStatus({ fiscalYear: 2025 });
    await closeYearEndReport('OFFERING', { fiscalYear: 2025, currentPassword: 'secret' });
    await reopenYearEndReport('OFFERING', { fiscalYear: 2025, currentPassword: 'secret' });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/reports/yearly-closing-status?fiscalYear=2025');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/reports/yearly-closing/OFFERING/close');
    expect(fetchMock.mock.calls[1][1].body).toBe(JSON.stringify({
      fiscalYear: 2025,
      currentPassword: 'secret',
    }));
    expect(fetchMock.mock.calls[2][0]).toBe('/api/reports/yearly-closing/OFFERING/reopen');
  });

  it('posts JSON and returns a Blob download', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(new Blob(['zip']), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await postBlob('/api/reports/tax-receipts/issue-batch', { taxYear: 2026 });

    expect(result).toBeInstanceOf(Blob);
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(fetchMock.mock.calls[0][1].body).toBe(JSON.stringify({ taxYear: 2026 }));
    const headers = new Headers(fetchMock.mock.calls[0][1].headers);
    expect(headers.get('Content-Type')).toBe('application/json');
  });

  it('sends an empty DELETE request', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await deleteEmpty('/api/members/member-1/image');

    expect(fetchMock.mock.calls[0][1].method).toBe('DELETE');
    expect(fetchMock.mock.calls[0][1].body).toBeUndefined();
  });
});

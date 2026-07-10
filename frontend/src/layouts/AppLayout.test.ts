import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue';
import { createRouter, createWebHistory } from 'vue-router';
import AppLayout from './AppLayout.vue';
import { authState } from '../auth/authStore';
import { getChurchInformation } from '../api/churchInformation';
import { postEmpty } from '../api/http';

vi.mock('../api/churchInformation', () => ({
  getChurchInformation: vi.fn().mockResolvedValue({
    name: 'Grace Community Church',
    address: '123 Church Street',
    contactInfo: '416-555-0100',
    treasurerName: 'Daniel Kim',
    bannerPath: '/branding/church-banner.png',
    logPath: '/branding/church_logo.png',
    listPageSize: 20,
  }),
}));

vi.mock('../api/http', () => ({
  postEmpty: vi.fn(),
}));

const churchInformationMock = vi.mocked(getChurchInformation);
const postEmptyMock = vi.mocked(postEmpty);

function router() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<span>Dashboard</span>' } },
      { path: '/members', component: { template: '<span>Members</span>' } },
      { path: '/offerings', component: { template: '<span>Offerings</span>' } },
      { path: '/finance', component: { template: '<span>Finance</span>' } },
      { path: '/budgets', component: { template: '<span>Budgets</span>' } },
      { path: '/reference-data', component: { template: '<span>Reference Data</span>' } },
      { path: '/reports', component: { template: '<span>Reports</span>' } },
      { path: '/profile', component: { template: '<span>Profile</span>' } },
      { path: '/login', component: { template: '<span>Login</span>' } },
    ],
  });
}

async function renderLayout() {
  const testRouter = router();
  testRouter.push('/');
  await testRouter.isReady();
  const result = render(AppLayout, {
    slots: {
      default: '<main>Page Content</main>',
    },
    global: {
      plugins: [testRouter],
    },
  });
  return { result, testRouter };
}

describe('AppLayout', () => {
  beforeEach(() => {
    churchInformationMock.mockResolvedValue({
      name: 'Grace Community Church',
      address: '123 Church Street',
      contactInfo: '416-555-0100',
      treasurerName: 'Daniel Kim',
      bannerPath: '/branding/church-banner.png',
      logPath: '/branding/church_logo.png',
      listPageSize: 20,
    });
    authState.currentUser = {
      primaryEmail: 'admin@example.com',
      displayName: 'Admin User',
      roles: ['ADMIN'],
      mustChangePassword: false,
      token: 'token',
    };
  });

  afterEach(() => {
    cleanup();
    authState.currentUser = null;
    vi.clearAllMocks();
  });

  it('shows church logo and app name in the shared menu', async () => {
    await renderLayout();

    const logo = await screen.findByAltText('Grace Community Church logo');

    expect(logo.getAttribute('src')).toBe('/branding/church_logo.png');
    expect(screen.getByText('Church Operations')).toBeTruthy();
    expect(screen.getByText('Page Content')).toBeTruthy();
  });

  it('keeps role-aware menu links for admin', async () => {
    await renderLayout();

    expect(await screen.findByRole('link', { name: 'Dashboard' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Members' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Offerings' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Finance' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Budgets' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Reports' })).toBeTruthy();
  });

  it('falls back to text branding when church information fails', async () => {
    churchInformationMock.mockRejectedValue(new Error('Church info unavailable.'));

    await renderLayout();

    expect(await screen.findByText('Church Operations')).toBeTruthy();
    expect(screen.queryByAltText(/logo/i)).toBeNull();
  });

  it('logs out, clears authentication, and returns to login', async () => {
    postEmptyMock.mockResolvedValue(undefined);
    const { testRouter } = await renderLayout();

    await fireEvent.click(screen.getByRole('button', { name: 'Logout' }));

    expect(postEmptyMock).toHaveBeenCalledWith('/api/auth/logout', {});
    expect(authState.currentUser).toBeNull();
    await waitFor(() => expect(testRouter.currentRoute.value.path).toBe('/login'));
  });
});

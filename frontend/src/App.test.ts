import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/vue';
import { createRouter, createWebHistory } from 'vue-router';
import App from './App.vue';
import { authState } from './auth/authStore';
import { getChurchInformation } from './api/churchInformation';

vi.mock('./api/churchInformation', () => ({
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

const churchInformationMock = vi.mocked(getChurchInformation);

function router(path: string) {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/login', component: { template: '<main>Login Page</main>' } },
      { path: '/change-password', component: { template: '<main>Change Password</main>' } },
      { path: '/', component: { template: '<main>Dashboard Page</main>' } },
      { path: '/members', component: { template: '<main>Members Page</main>' } },
    ],
  });
}

async function renderApp(path: string) {
  const testRouter = router(path);
  testRouter.push(path);
  await testRouter.isReady();
  return render(App, {
    global: {
      plugins: [testRouter],
    },
  });
}

describe('App', () => {
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

  it('keeps the shared menu around non-dashboard pages', async () => {
    await renderApp('/members');

    expect(await screen.findByRole('navigation', { name: 'Main menu' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Dashboard' })).toBeTruthy();
    expect(screen.getByText('Members Page')).toBeTruthy();
  });

  it('does not show the shared menu on login routes', async () => {
    await renderApp('/login');

    expect(screen.getByText('Login Page')).toBeTruthy();
    expect(screen.queryByRole('navigation', { name: 'Main menu' })).toBeNull();
  });
});

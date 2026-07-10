import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/vue';
import { createRouter, createWebHistory } from 'vue-router';
import { postEmpty, postJson } from '../api/http';
import ForgotPasswordView from './ForgotPasswordView.vue';
import LoginView from './LoginView.vue';
import ResetPasswordView from './ResetPasswordView.vue';

vi.mock('../api/http', () => ({
  postJson: vi.fn(),
  postEmpty: vi.fn(),
}));

const postJsonMock = vi.mocked(postJson);
const postEmptyMock = vi.mocked(postEmpty);

function testRouter(path = '/login') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/login', component: LoginView },
      { path: '/forgot-password', component: ForgotPasswordView },
      { path: '/reset-password', component: ResetPasswordView },
      { path: '/', component: { template: '<main>Dashboard</main>' } },
    ],
  });
  router.push(path);
  return router;
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('password reset views', () => {
  it('links from login to forgot password', async () => {
    const router = testRouter();
    await router.isReady();
    render(LoginView, { global: { plugins: [router] } });

    expect(screen.getByRole('link', { name: 'Forgot password?' }).getAttribute('href'))
      .toBe('/forgot-password');
  });

  it('submits a generic password reset request', async () => {
    postJsonMock.mockResolvedValue({
      message: 'If an account matches that email, a password reset link has been sent.',
    });
    const router = testRouter('/forgot-password');
    await router.isReady();
    render(ForgotPasswordView, { global: { plugins: [router] } });

    await fireEvent.update(screen.getByLabelText('Primary email'), 'member@example.com');
    await fireEvent.click(screen.getByRole('button', { name: 'Send reset link' }));

    expect(postJsonMock).toHaveBeenCalledWith('/api/auth/forgot-password', {
      email: 'member@example.com',
    });
    expect(await screen.findByText(/if an account matches that email/i)).toBeTruthy();
  });

  it('rejects password confirmation mismatch without calling the API', async () => {
    const router = testRouter('/reset-password?token=reset-token');
    await router.isReady();
    render(ResetPasswordView, { global: { plugins: [router] } });

    await fireEvent.update(screen.getByLabelText('New password'), 'new-password');
    await fireEvent.update(screen.getByLabelText('Confirm password'), 'different-password');
    await fireEvent.click(screen.getByRole('button', { name: 'Set new password' }));

    expect(screen.getByText('Passwords do not match.')).toBeTruthy();
    expect(postEmptyMock).not.toHaveBeenCalled();
  });

  it('sets a new password using the query token', async () => {
    postEmptyMock.mockResolvedValue(undefined);
    const router = testRouter('/reset-password?token=reset-token');
    await router.isReady();
    render(ResetPasswordView, { global: { plugins: [router] } });

    await fireEvent.update(screen.getByLabelText('New password'), 'new-password');
    await fireEvent.update(screen.getByLabelText('Confirm password'), 'new-password');
    await fireEvent.click(screen.getByRole('button', { name: 'Set new password' }));

    expect(postEmptyMock).toHaveBeenCalledWith('/api/auth/reset-password', {
      token: 'reset-token',
      newPassword: 'new-password',
    });
    expect(await screen.findByText('Your password has been reset.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Return to sign in' })).toBeTruthy();
  });
});

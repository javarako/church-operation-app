# Logout and Password Reset Design

## Goal

Add secure logout and email-based self-service password reset before producing the English user guide.

## Logout

Add a Logout command to the persistent application sidebar for every authenticated user. The frontend sends the current bearer token to `POST /api/auth/logout`, clears the in-memory user state, and returns to `/login`. The backend removes that token from `AuthTokenService`, so it cannot authenticate later requests.

## Password Reset Request

Add a **Forgot password?** link to the login page and a public `/forgot-password` view. The user enters a primary email address. `POST /api/auth/forgot-password` always returns the same success message whether or not the address exists, preventing account discovery.

For an active, unlocked matching member, the backend creates a cryptographically random token, stores only its SHA-256 hash in MongoDB, sets a 30-minute expiry, and emails a link to `/reset-password?token=...`. Creating a new token invalidates outstanding reset tokens for that member.

## Password Reset Completion

The public `/reset-password` view accepts and confirms a password of at least eight characters. `POST /api/auth/reset-password` validates the token hash, expiry, and unused state; updates the member's BCrypt password hash; marks the token consumed; clears `mustChangePassword`; and revokes all active login tokens for that member. The user then returns to the login page and signs in with the new password.

Self-service reset does not force another password change at login because the member selected the new password through the secure reset page. Initial accounts and administrator-issued temporary passwords continue to use `mustChangePassword`.

## Email Delivery

Use Spring Boot Mail with standard SMTP configuration.

- Local Docker: add Mailpit, exposing SMTP on `1025` and its browser inbox on `8025`.
- Production: support Brevo SMTP through environment variables without hard-coded credentials.
- Configure sender address, public frontend base URL, token lifetime, SMTP host, port, username, password, and TLS through `application.yml` environment placeholders.

Email delivery failure is logged server-side while the public request response remains generic. Reset tokens are never logged or returned by the API.

## Backend Structure

Follow the existing `com.church.operation` package layout:

- `entity`: password reset token document
- `repo`: token repository
- `dto`: forgot-password and reset-password requests
- `service`: reset-token lifecycle and email delivery
- `rest`: authentication endpoints
- `config`: reset and mail settings when configuration binding is useful

Add `spring-boot-starter-mail` and MongoDB indexes for token hash, member email, and expiration cleanup.

## Frontend Structure

- Add `ForgotPasswordView.vue` and `ResetPasswordView.vue` as standalone authentication pages.
- Add public router entries for `/forgot-password` and `/reset-password`.
- Add the login-page reset link.
- Add a sidebar Logout command that follows the existing navigation styling.
- Show clear success and error states without revealing whether an email address is registered.

## Security

- Generate reset tokens with `SecureRandom` and sufficient entropy.
- Persist only token hashes.
- Enforce one-time use and a 30-minute expiry.
- Return a generic forgot-password response.
- Require at least eight characters for the new password, matching current policy.
- Revoke all login tokens after reset and the current token after logout.
- Permit only login, forgot-password, reset-password, church information, health, and branding endpoints without authentication.

## Testing

- Backend unit tests cover token creation, hashing, expiry, one-time use, generic responses, password updates, and login-token revocation.
- Controller/security tests cover public reset endpoints and authenticated logout.
- Frontend tests cover the login reset link, request/reset forms, success and error states, logout API call, cleared auth state, and navigation to login.
- Docker configuration is verified with Mailpit and a captured local reset email.
- Run the full backend and frontend test suites and production frontend build.

## User Guide Impact

The English manual will include logout, forgot-password request, reset-email retrieval in local Mailpit where relevant, reset completion, and the distinction between self-service reset and forced first-login password change.

# Logout and Password Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add authenticated logout and secure email-based self-service password reset with Brevo-compatible SMTP and local Mailpit delivery.

**Architecture:** Keep login tokens in the existing in-memory `AuthTokenService`, adding explicit revocation for logout and password-reset invalidation. Store hashed, expiring, one-time password reset tokens in MongoDB; send raw tokens only inside reset links through Spring Mail. Add standalone Vue authentication views and a sidebar logout command.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, Spring Data MongoDB, Spring Mail, Vue 3, Vue Router, Docker Compose, Mailpit, JUnit 5, Mockito, Vitest, Vue Testing Library

## Global Constraints

- Follow `com.church.operation` packages: `config`, `dto`, `entity`, `exception`, `filter`, `repo`, `rest`, `service`, and `util`.
- Store only SHA-256 reset-token hashes; never persist, log, or return raw reset tokens.
- Reset tokens expire after 30 minutes and are single-use.
- Forgot-password responses must not reveal whether an account exists.
- New passwords require at least eight characters.
- Self-service reset clears `mustChangePassword` and revokes existing login tokens.
- Logout revokes the current bearer token and clears frontend authentication state.
- Use Mailpit for local Docker email and environment-configured SMTP for Brevo production delivery.

---

### Task 1: Add Login Token Revocation and Logout

**Files:**
- Modify: `backend/src/main/java/com/church/operation/service/AuthTokenService.java`
- Modify: `backend/src/main/java/com/church/operation/service/AuthService.java`
- Modify: `backend/src/main/java/com/church/operation/rest/AuthController.java`
- Test: `backend/src/test/java/com/church/operation/service/AuthTokenServiceTest.java`
- Test: `backend/src/test/java/com/church/operation/service/AuthServiceTest.java`

**Interfaces:**
- Produces: `void revokeToken(String token)`, `void revokeAllForMember(String primaryEmail)`, and `POST /api/auth/logout`
- Consumes: the current bearer token from the `Authorization` header

- [ ] **Step 1: Write failing token-revocation tests**

Create `AuthTokenServiceTest` with real `Member` objects and a mocked `MemberRepository`:

```java
@Test
void revokedTokenCannotResolveMember() {
    String token = service.issueToken(activeMember("member@example.com"));
    service.revokeToken(token);
    assertThat(service.findMember(token)).isEmpty();
}

@Test
void revokingMemberRemovesEveryIssuedToken() {
    Member member = activeMember("member@example.com");
    String first = service.issueToken(member);
    String second = service.issueToken(member);
    service.revokeAllForMember("member@example.com");
    assertThat(service.findMember(first)).isEmpty();
    assertThat(service.findMember(second)).isEmpty();
}
```

- [ ] **Step 2: Run the tests and confirm the missing-method failures**

```bash
cd backend
mvn -Dtest=AuthTokenServiceTest test
```

Expected: compilation fails because the two revocation methods do not exist.

- [ ] **Step 3: Implement login-token revocation**

Add these methods to `AuthTokenService`:

```java
public void revokeToken(String token) {
    tokenToPrimaryEmail.remove(token);
}

public void revokeAllForMember(String primaryEmail) {
    String normalized = primaryEmail.trim().toLowerCase();
    tokenToPrimaryEmail.entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(normalized));
}
```

Add `AuthService.logout(String token)` delegating to `revokeToken`. Add `POST /api/auth/logout`, extracting the bearer value from `Authorization` and returning HTTP 204.

- [ ] **Step 4: Verify logout tests pass**

```bash
cd backend
mvn -Dtest=AuthTokenServiceTest,AuthServiceTest test
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit the logout backend**

```bash
git add backend/src/main/java/com/church/operation/service/AuthTokenService.java backend/src/main/java/com/church/operation/service/AuthService.java backend/src/main/java/com/church/operation/rest/AuthController.java backend/src/test/java/com/church/operation/service/AuthTokenServiceTest.java backend/src/test/java/com/church/operation/service/AuthServiceTest.java
git commit -m "Add authenticated logout"
```

---

### Task 2: Add Secure Password Reset Token Persistence

**Files:**
- Create: `backend/src/main/java/com/church/operation/entity/PasswordResetToken.java`
- Create: `backend/src/main/java/com/church/operation/repo/PasswordResetTokenRepository.java`
- Create: `backend/src/main/java/com/church/operation/config/PasswordResetProperties.java`
- Modify: `backend/src/main/java/com/church/operation/ChurchOperationApplication.java`
- Test: `backend/src/test/java/com/church/operation/service/PasswordResetServiceTest.java`

**Interfaces:**
- Produces: MongoDB reset-token document and configuration values `frontendBaseUrl`, `tokenLifetime`, and `fromAddress`
- Consumes: normalized member primary email and SHA-256 token hash

- [ ] **Step 1: Write the failing service test skeleton**

Create `PasswordResetServiceTest` with mocked `MemberRepository`, `PasswordResetTokenRepository`, `PasswordEncoder`, `AuthTokenService`, and `PasswordResetEmailService`. Assert that requesting a reset for an active member deletes old tokens and saves a token whose hash differs from the raw token sent by email:

```java
@Test
void requestResetStoresHashAndEmailsRawToken() {
    Member member = activeMember("member@example.com");
    when(memberRepository.findByPrimaryEmail("member@example.com")).thenReturn(Optional.of(member));
    when(tokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.requestReset("member@example.com");

    ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
    ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
    verify(tokenRepository).save(saved.capture());
    verify(emailService).sendResetEmail(eq(member), raw.capture());
    assertThat(saved.getValue().getTokenHash()).isNotEqualTo(raw.getValue());
    assertThat(saved.getValue().getMemberEmail()).isEqualTo("member@example.com");
}
```

- [ ] **Step 2: Run the test and confirm missing-type failures**

```bash
cd backend
mvn -Dtest=PasswordResetServiceTest test
```

Expected: compilation fails because reset-token types and services do not exist.

- [ ] **Step 3: Add the reset-token entity and repository**

Create `PasswordResetToken` with `id`, unique indexed `tokenHash`, indexed `memberEmail`, `expiresAt` using `@Indexed(expireAfter = "0s")`, and nullable `usedAt`. Create repository methods:

```java
Optional<PasswordResetToken> findByTokenHash(String tokenHash);
void deleteByMemberEmail(String memberEmail);
```

- [ ] **Step 4: Add strongly typed reset configuration**

Create:

```java
@ConfigurationProperties(prefix = "church.password-reset")
public record PasswordResetProperties(
    String frontendBaseUrl,
    Duration tokenLifetime,
    String fromAddress
) {}
```

Register it in `ChurchOperationApplication` with the existing `@EnableConfigurationProperties` list.

- [ ] **Step 5: Commit persistence and configuration types**

```bash
git add backend/src/main/java/com/church/operation/entity/PasswordResetToken.java backend/src/main/java/com/church/operation/repo/PasswordResetTokenRepository.java backend/src/main/java/com/church/operation/config/PasswordResetProperties.java backend/src/main/java/com/church/operation/ChurchOperationApplication.java backend/src/test/java/com/church/operation/service/PasswordResetServiceTest.java
git commit -m "Add password reset token persistence"
```

---

### Task 3: Implement Password Reset Email and Token Lifecycle

**Files:**
- Create: `backend/src/main/java/com/church/operation/service/PasswordResetEmailService.java`
- Create: `backend/src/main/java/com/church/operation/service/PasswordResetService.java`
- Modify: `backend/pom.xml`
- Test: `backend/src/test/java/com/church/operation/service/PasswordResetServiceTest.java`

**Interfaces:**
- Produces: `void requestReset(String email)` and `void resetPassword(String rawToken, String newPassword)`
- Consumes: `JavaMailSender`, `PasswordResetProperties`, repositories, `PasswordEncoder`, and `AuthTokenService`

- [ ] **Step 1: Complete failing lifecycle tests**

Add tests for unknown email returning normally without saving or emailing, expired/used/unknown token rejection with `"This password reset link is invalid or expired."`, short-password rejection, successful BCrypt update, `mustChangePassword=false`, used timestamp, and `revokeAllForMember` invocation.

```java
@Test
void successfulResetUpdatesPasswordConsumesTokenAndRevokesSessions() {
    String rawToken = "raw-reset-token";
    PasswordResetToken token = validTokenFor(rawToken, "member@example.com");
    Member member = activeMember("member@example.com");
    when(tokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(token));
    when(memberRepository.findByPrimaryEmail("member@example.com")).thenReturn(Optional.of(member));
    when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

    service.resetPassword(rawToken, "new-password");

    assertThat(member.getPasswordHash()).isEqualTo("new-hash");
    assertThat(member.isMustChangePassword()).isFalse();
    assertThat(token.getUsedAt()).isNotNull();
    verify(authTokenService).revokeAllForMember("member@example.com");
}
```

- [ ] **Step 2: Add Spring Mail dependency**

Add `spring-boot-starter-mail` under backend dependencies.

- [ ] **Step 3: Implement email delivery**

`PasswordResetEmailService.sendResetEmail(Member member, String rawToken)` builds the link using `frontendBaseUrl + "/reset-password?token=" + URLEncoder.encode(rawToken, UTF_8)`, sends plain text through `JavaMailSender`, and uses the configured sender address.

- [ ] **Step 4: Implement token generation and completion**

Use `SecureRandom` to generate 32 bytes and URL-safe Base64 without padding. Hash with SHA-256 before repository access. Normalize email to lowercase. Validate member active/unlocked on request. Validate token exists, is unused, and expires after `Instant.now()` on completion. Save the member and consumed token, then revoke member sessions.

- [ ] **Step 5: Run lifecycle tests**

```bash
cd backend
mvn -Dtest=PasswordResetServiceTest test
```

Expected: all reset lifecycle tests pass.

- [ ] **Step 6: Commit reset service implementation**

```bash
git add backend/pom.xml backend/src/main/java/com/church/operation/service/PasswordResetEmailService.java backend/src/main/java/com/church/operation/service/PasswordResetService.java backend/src/test/java/com/church/operation/service/PasswordResetServiceTest.java
git commit -m "Implement secure password reset lifecycle"
```

---

### Task 4: Expose Public Password Reset APIs Securely

**Files:**
- Create: `backend/src/main/java/com/church/operation/dto/ForgotPasswordRequest.java`
- Create: `backend/src/main/java/com/church/operation/dto/ResetPasswordRequest.java`
- Modify: `backend/src/main/java/com/church/operation/rest/AuthController.java`
- Modify: `backend/src/main/java/com/church/operation/config/SecurityConfig.java`
- Create: `backend/src/test/java/com/church/operation/rest/AuthControllerTest.java`

**Interfaces:**
- Produces: `POST /api/auth/forgot-password`, `POST /api/auth/reset-password`, and authenticated `POST /api/auth/logout`
- Consumes: JSON `{ "email": string }` and `{ "token": string, "newPassword": string }`

- [ ] **Step 1: Write failing controller tests**

Use `@WebMvcTest(AuthController.class)` with mocked services. Verify forgot-password returns HTTP 202 and the generic message `"If an account matches that email, a password reset link has been sent."`; reset returns HTTP 204; logout without authentication returns 401; and malformed requests return 400.

- [ ] **Step 2: Add validated request records**

```java
public record ForgotPasswordRequest(@NotBlank @Email String email) {}

public record ResetPasswordRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8) String newPassword
) {}
```

- [ ] **Step 3: Add controller endpoints and security rules**

Permit `/api/auth/login`, `/api/auth/forgot-password`, and `/api/auth/reset-password`. Keep `/api/auth/logout` authenticated. Return `ResponseEntity.accepted().body(Map.of("message", genericMessage))` for forgot-password and no content for reset/logout.

- [ ] **Step 4: Run controller and service tests**

```bash
cd backend
mvn -Dtest=AuthControllerTest,AuthServiceTest,AuthTokenServiceTest,PasswordResetServiceTest test
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit authentication APIs**

```bash
git add backend/src/main/java/com/church/operation/dto/ForgotPasswordRequest.java backend/src/main/java/com/church/operation/dto/ResetPasswordRequest.java backend/src/main/java/com/church/operation/rest/AuthController.java backend/src/main/java/com/church/operation/config/SecurityConfig.java backend/src/test/java/com/church/operation/rest/AuthControllerTest.java
git commit -m "Expose password reset APIs"
```

---

### Task 5: Configure Brevo-Compatible SMTP and Local Mailpit

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `docker-compose.yml`
- Modify: `README.md`

**Interfaces:**
- Produces: SMTP configuration and Mailpit browser inbox at `http://localhost:8025`
- Consumes: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS`, `PASSWORD_RESET_FROM_ADDRESS`, `PASSWORD_RESET_FRONTEND_BASE_URL`, and `PASSWORD_RESET_TOKEN_LIFETIME`

- [ ] **Step 1: Add application mail and reset configuration**

Add:

```yaml
spring:
  mail:
    host: ${MAIL_HOST:localhost}
    port: ${MAIL_PORT:1025}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail.smtp.auth: ${MAIL_SMTP_AUTH:false}
      mail.smtp.starttls.enable: ${MAIL_SMTP_STARTTLS:false}

church:
  password-reset:
    frontend-base-url: ${PASSWORD_RESET_FRONTEND_BASE_URL:http://localhost:5173}
    token-lifetime: ${PASSWORD_RESET_TOKEN_LIFETIME:30m}
    from-address: ${PASSWORD_RESET_FROM_ADDRESS:no-reply@church.local}
```

- [ ] **Step 2: Add Mailpit to Docker Compose**

Add `mailpit` using `axllent/mailpit:latest`, expose `1025:1025` and `8025:8025`, set backend `MAIL_HOST=mailpit`, and make backend depend on Mailpit.

- [ ] **Step 3: Document local and Brevo settings**

Document Mailpit at `http://localhost:8025`. Provide Brevo production environment examples using `smtp-relay.brevo.com`, port `587`, SMTP login, SMTP key, auth enabled, and STARTTLS enabled without committing credentials.

- [ ] **Step 4: Validate configuration**

```bash
docker compose config
cd backend
mvn test
```

Expected: Compose configuration is valid and the complete backend test suite passes.

- [ ] **Step 5: Commit mail configuration**

```bash
git add backend/src/main/resources/application.yml docker-compose.yml README.md
git commit -m "Configure password reset email delivery"
```

---

### Task 6: Add Forgot and Reset Password Views

**Files:**
- Create: `frontend/src/views/ForgotPasswordView.vue`
- Create: `frontend/src/views/ResetPasswordView.vue`
- Create: `frontend/src/views/PasswordResetViews.test.ts`
- Modify: `frontend/src/views/LoginView.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/App.vue`

**Interfaces:**
- Produces: public routes `/forgot-password` and `/reset-password`
- Consumes: forgot/reset API endpoints through `postJson`

- [ ] **Step 1: Write failing view tests**

Test that Login contains a `Forgot password?` link; forgot form posts `{ email }` and shows the generic success message; reset form reads `token` from the query, validates matching passwords, posts `{ token, newPassword }`, and shows a link back to login after success.

```typescript
it('submits a password reset request without revealing account status', async () => {
  render(ForgotPasswordView, { global: { plugins: [router] } });
  await fireEvent.update(screen.getByLabelText('Primary email'), 'member@example.com');
  await fireEvent.click(screen.getByRole('button', { name: 'Send reset link' }));
  expect(postJson).toHaveBeenCalledWith('/api/auth/forgot-password', { email: 'member@example.com' });
  expect(await screen.findByText(/if an account matches/i)).toBeTruthy();
});
```

- [ ] **Step 2: Run view tests and confirm failures**

```bash
cd frontend
npx vitest run src/views/PasswordResetViews.test.ts
```

Expected: compilation/import failures because the views do not exist.

- [ ] **Step 3: Implement forgot-password view**

Use the existing `.auth-page` and `.auth-panel` pattern. Include primary email, Send reset link, generic success text, error state, and Back to sign in link.

- [ ] **Step 4: Implement reset-password view**

Include New password and Confirm password fields, reject mismatch before API call, require eight characters, handle missing token, show completion, and link back to sign in.

- [ ] **Step 5: Wire public routes and standalone layout**

Add both route imports and records. Update router guards so login, forgot-password, and reset-password remain public. Update `App.vue` standalone-route detection for both paths. Add the login reset link.

- [ ] **Step 6: Run password reset view tests**

```bash
cd frontend
npx vitest run src/views/PasswordResetViews.test.ts src/App.test.ts
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit reset views**

```bash
git add frontend/src/views/ForgotPasswordView.vue frontend/src/views/ResetPasswordView.vue frontend/src/views/PasswordResetViews.test.ts frontend/src/views/LoginView.vue frontend/src/router/index.ts frontend/src/App.vue frontend/src/App.test.ts
git commit -m "Add self-service password reset screens"
```

---

### Task 7: Add Sidebar Logout Command

**Files:**
- Modify: `frontend/src/layouts/AppLayout.vue`
- Modify: `frontend/src/layouts/AppLayout.test.ts`
- Modify: `frontend/src/api/http.ts`

**Interfaces:**
- Produces: Logout button that posts to `/api/auth/logout`, clears current user, and routes to `/login`
- Consumes: current bearer token automatically attached by `requestJson`

- [ ] **Step 1: Write the failing logout test**

Mock `postJson`, render the authenticated layout, click Logout, and assert API call, cleared `authState.currentUser`, and `/login` navigation:

```typescript
expect(postJson).toHaveBeenCalledWith('/api/auth/logout', {});
expect(authState.currentUser).toBeNull();
expect(router.currentRoute.value.path).toBe('/login');
```

- [ ] **Step 2: Run the layout test and confirm failure**

```bash
cd frontend
npx vitest run src/layouts/AppLayout.test.ts
```

Expected: Logout button cannot be found.

- [ ] **Step 3: Implement logout**

Add a Logout button at the bottom of the sidebar. On click, attempt `postJson('/api/auth/logout', {})`, then clear user state and route to `/login` in `finally` so the browser session ends even when the server is unavailable.

- [ ] **Step 4: Verify layout tests**

```bash
cd frontend
npx vitest run src/layouts/AppLayout.test.ts
```

Expected: all layout tests pass.

- [ ] **Step 5: Commit frontend logout**

```bash
git add frontend/src/layouts/AppLayout.vue frontend/src/layouts/AppLayout.test.ts frontend/src/api/http.ts
git commit -m "Add sidebar logout"
```

---

### Task 8: End-to-End Verification

**Files:**
- Verify: all backend and frontend changes
- Update: `docs/superpowers/plans/2026-07-10-logout-password-reset.md` checkbox status

**Interfaces:**
- Produces: verified logout and local email reset workflows ready for the user guide
- Consumes: Docker Compose services and browser interaction

- [ ] **Step 1: Run all automated verification**

```bash
cd backend
mvn test
cd ../frontend
npm test -- --run
npm run build
cd ..
docker compose config
```

Expected: backend tests, frontend tests, type checking, production build, and Compose validation all succeed.

- [ ] **Step 2: Start the local application**

```bash
docker compose up --build -d
```

Expected: MongoDB, backend, frontend, and Mailpit become healthy/available.

- [ ] **Step 3: Verify password reset through Mailpit**

Request a reset for a sample member, open `http://localhost:8025`, follow the latest reset link, choose a new password, and verify login succeeds. Confirm replaying the same link fails.

- [ ] **Step 4: Verify logout**

Log in, select Logout, confirm navigation to login, and verify the old bearer token receives HTTP 401 on a protected endpoint.

- [ ] **Step 5: Stop local verification services**

```bash
docker compose down
```

Expected: containers stop without deleting the MongoDB volume.

- [ ] **Step 6: Commit final verification updates**

```bash
git add docs/superpowers/plans/2026-07-10-logout-password-reset.md
git commit -m "Verify logout and password reset workflows"
```

# Runtime Email Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an Admin test and activate SMTP host, port, username, encrypted password, and sender-address changes from System Administration without restarting the backend.

**Architecture:** A MongoDB singleton stores non-secret SMTP fields plus an AES-GCM encrypted password. A resolver selects the database override or startup defaults, while a per-operation mail sender applies the resolved values immediately. Saving is guarded by a short-lived, actor-bound verification token proving that the exact effective settings successfully sent a test email.

**Tech Stack:** Java 21, Spring Boot 4.0.7, Spring Security, Spring Mail, Spring Data MongoDB, Jakarta Bean Validation, JUnit 5, Mockito, Vue 3.5, TypeScript, Vitest, Vue Testing Library, Docker Compose.

## Global Constraints

- Only `ADMIN` may read, test, save, or reset runtime email settings.
- `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS`, `PASSWORD_RESET_FRONTEND_BASE_URL`, and `CHURCH_SETTINGS_ENCRYPTION_KEY` remain deployment-only settings.
- `CHURCH_SETTINGS_ENCRYPTION_KEY` is a Base64-encoded 256-bit key and must never enter MongoDB, API responses, logs, audit metadata, or backup content.
- SMTP passwords are write-only and AES-256-GCM encrypted at rest with a fresh 12-byte nonce and 128-bit tag.
- Database settings override startup defaults; deleting the override restores startup defaults.
- A successful test of the exact effective values is required before save.
- The backend, not only the Vue client, enforces test-before-save.
- No current-login-password confirmation is required.
- A failed test or reset must leave the active database override unchanged.
- Do not overwrite unrelated local changes in `docker-compose.yml` or any other dirty file.
- Never commit a real SMTP password or encryption key.

---

### Task 1: Runtime Properties And Authenticated Encryption

**Files:**
- Create: `backend/src/main/java/com/church/operation/config/RuntimeEmailProperties.java`
- Create: `backend/src/main/java/com/church/operation/exception/EmailConfigurationException.java`
- Create: `backend/src/main/java/com/church/operation/service/EmailSettingsCrypto.java`
- Modify: `backend/src/main/java/com/church/operation/ChurchOperationApplication.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/church/operation/service/EmailSettingsCryptoTest.java`

**Interfaces:**
- Produces: `RuntimeEmailProperties(String host, int port, String username, String password, boolean auth, boolean startTls, String fromAddress, String encryptionKey, Duration verificationLifetime)`.
- Produces: `EmailSettingsCrypto.EncryptedSecret(String ciphertext, String nonce, int version)`.
- Produces: `EncryptedSecret encrypt(char[] plaintext)`, `char[] decrypt(EncryptedSecret secret)`, and `byte[] fingerprint(String canonicalSettings)`.

- [ ] **Step 1: Write failing crypto and property-binding tests**

```java
@Test
void encryptsWithFreshNoncesAndRejectsTampering() {
    EmailSettingsCrypto crypto = new EmailSettingsCrypto(base64Key());
    var first = crypto.encrypt("smtp-secret".toCharArray());
    var second = crypto.encrypt("smtp-secret".toCharArray());

    assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    assertThat(first.nonce()).isNotEqualTo(second.nonce());
    assertThat(crypto.decrypt(first)).containsExactly("smtp-secret".toCharArray());

    var tampered = new EmailSettingsCrypto.EncryptedSecret(first.ciphertext() + "A", first.nonce(), first.version());
    assertThatThrownBy(() -> crypto.decrypt(tampered))
        .isInstanceOf(EmailConfigurationException.class)
        .hasMessage("The saved email password cannot be decrypted with this server configuration.");
}

@Test
void rejectsMissingMalformedAndWrongLengthKeysWithoutEchoingThem() {
    EmailSettingsCrypto crypto = new EmailSettingsCrypto("not-base64");
    assertThatThrownBy(() -> crypto.encrypt("smtp-secret".toCharArray()))
        .isInstanceOf(EmailConfigurationException.class)
        .hasMessage("CHURCH_SETTINGS_ENCRYPTION_KEY must be a Base64-encoded 256-bit key.");
}
```

- [ ] **Step 2: Run the focused test and confirm the red state**

Run: `cd backend && mvn -Dtest=EmailSettingsCryptoTest test`

Expected: compilation fails because `EmailSettingsCrypto` and `EmailConfigurationException` do not exist.

- [ ] **Step 3: Add runtime properties and register them**

```java
@ConfigurationProperties(prefix = "church.runtime-email")
public record RuntimeEmailProperties(
    String host,
    int port,
    String username,
    String password,
    boolean auth,
    boolean startTls,
    String fromAddress,
    String encryptionKey,
    Duration verificationLifetime
) {}
```

Add `RuntimeEmailProperties.class` to `@EnableConfigurationProperties`. Bind it in `application.yml`:

```yaml
church:
  runtime-email:
    host: ${MAIL_HOST:localhost}
    port: ${MAIL_PORT:1025}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    auth: ${MAIL_SMTP_AUTH:false}
    start-tls: ${MAIL_SMTP_STARTTLS:false}
    from-address: ${PASSWORD_RESET_FROM_ADDRESS:no-reply@church.local}
    encryption-key: ${CHURCH_SETTINGS_ENCRYPTION_KEY:}
    verification-lifetime: 10m
```

- [ ] **Step 4: Implement AES-GCM and derived HMAC keys**

```text
EmailSettingsCrypto(RuntimeEmailProperties properties)
EncryptedSecret encrypt(char[] plaintext)
char[] decrypt(EncryptedSecret secret)
byte[] fingerprint(String canonicalSettings)
EncryptedSecret(String ciphertext, String nonce, int version)
```

Derive separate encryption and verification keys from the master key using HMAC-SHA-256 labels `email-settings-encryption-v1` and `email-settings-verification-v1`. Convert byte arrays and temporary character arrays in `finally` blocks where possible. Wrap all key/decryption failures in `EmailConfigurationException` with the exact sanitized messages asserted above.
Construction must remain lazy with respect to key validation: a blank, malformed,
or wrong-length key does not prevent Spring Boot from starting while the
application uses server-default email settings. `encrypt`, `decrypt`, and
`fingerprint` validate the key when invoked and return the sanitized
configuration error.

- [ ] **Step 5: Run focused and configuration tests**

Run: `cd backend && mvn -Dtest=EmailSettingsCryptoTest test`

Expected: PASS, including unique nonce, round trip, tamper, wrong-key, and invalid-key cases.

- [ ] **Step 6: Commit Task 1**

```bash
git add backend/src/main/java/com/church/operation/ChurchOperationApplication.java backend/src/main/java/com/church/operation/config/RuntimeEmailProperties.java backend/src/main/java/com/church/operation/exception/EmailConfigurationException.java backend/src/main/java/com/church/operation/service/EmailSettingsCrypto.java backend/src/main/resources/application.yml backend/src/test/java/com/church/operation/service/EmailSettingsCryptoTest.java
git commit -m "feat: add email settings encryption"
```

---

### Task 2: Persistence And Effective Settings Resolution

**Files:**
- Create: `backend/src/main/java/com/church/operation/entity/EmailSettings.java`
- Create: `backend/src/main/java/com/church/operation/repo/EmailSettingsRepository.java`
- Create: `backend/src/main/java/com/church/operation/service/EffectiveEmailSettings.java`
- Create: `backend/src/main/java/com/church/operation/service/EmailSettingsDraft.java`
- Create: `backend/src/main/java/com/church/operation/service/EmailSettingsResolver.java`
- Create: `backend/src/main/java/com/church/operation/util/EmailSettingsSource.java`
- Test: `backend/src/test/java/com/church/operation/service/EmailSettingsResolverTest.java`

**Interfaces:**
- Consumes: `RuntimeEmailProperties` and `EmailSettingsCrypto.EncryptedSecret` from Task 1.
- Produces: `EffectiveEmailSettings(String host, int port, String username, char[] password, boolean auth, boolean startTls, String fromAddress, EmailSettingsSource source)`.
- Produces: `EffectiveEmailSettings resolve()` and `EffectiveEmailSettings resolveDraft(EmailSettingsDraft draft)`.
- Produces: `EmailSettingsDraft(String host, int port, String username, char[] password, String fromAddress)` where an empty password preserves the active password.

- [ ] **Step 1: Write resolver tests for database precedence and fallback**

```java
@Test
void databaseOverrideWinsButAuthAndTlsRemainFromEnvironment() {
    EmailSettings saved = savedSettings("db.smtp.test", 2525, "db-user", encrypted("db-secret"), "church@test.org");
    when(repository.findById(EmailSettings.SINGLETON_ID)).thenReturn(Optional.of(saved));

    EffectiveEmailSettings result = resolver.resolve();

    assertThat(result.host()).isEqualTo("db.smtp.test");
    assertThat(result.password()).containsExactly("db-secret".toCharArray());
    assertThat(result.auth()).isTrue();
    assertThat(result.startTls()).isTrue();
    assertThat(result.source()).isEqualTo(EmailSettingsSource.DATABASE);
}

@Test
void emptyDraftPasswordPreservesDatabaseOrEnvironmentPassword() {
    EffectiveEmailSettings result = resolver.resolveDraft(
        new EmailSettingsDraft("new.smtp.test", 587, "new-user", new char[0], "from@test.org")
    );
    assertThat(result.password()).containsExactly("existing-secret".toCharArray());
}
```

- [ ] **Step 2: Run the resolver test and confirm it fails**

Run: `cd backend && mvn -Dtest=EmailSettingsResolverTest test`

Expected: compilation fails because the persistence and resolver types do not exist.

- [ ] **Step 3: Add the singleton entity and repository**

```java
@Document("email_settings")
public class EmailSettings {
    public static final String SINGLETON_ID = "runtime-email";
    @Id private String id = SINGLETON_ID;
    private String host;
    private int port;
    private String username;
    private String fromAddress;
    private String passwordCiphertext;
    private String passwordNonce;
    private int cipherVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdByMemberId;
    private String updatedByMemberId;
}

public interface EmailSettingsRepository extends MongoRepository<EmailSettings, String> {}
```

- [ ] **Step 4: Implement explicit effective-settings resolution**

```java
public record EffectiveEmailSettings(
    String host, int port, String username, char[] password,
    boolean auth, boolean startTls, String fromAddress, EmailSettingsSource source
) {
    public void clearPassword() { Arrays.fill(password, '\0'); }
}

public record EmailSettingsDraft(
    String host, int port, String username, char[] password, String fromAddress
) {}

public enum EmailSettingsSource { DATABASE, SERVER_DEFAULTS }
```

`EmailSettingsResolver.resolve()` loads `EmailSettings.SINGLETON_ID`; decrypts its password when present; otherwise uses all startup defaults. `resolveDraft(...)` uses draft host, port, username, and sender, but replaces an empty draft password with the currently effective password. Both paths always take `auth` and `startTls` from `RuntimeEmailProperties`.

- [ ] **Step 5: Add missing-key behavior tests**

```java
@Test
void applicationCanReadStatusButEmailResolutionFailsWhenDatabaseSecretCannotBeDecrypted() {
    when(repository.findById(EmailSettings.SINGLETON_ID)).thenReturn(Optional.of(savedWithForeignKey()));
    assertThatThrownBy(resolver::resolve)
        .isInstanceOf(EmailConfigurationException.class)
        .hasMessageNotContaining("foreign-secret");
}
```

- [ ] **Step 6: Run resolver tests**

Run: `cd backend && mvn -Dtest=EmailSettingsResolverTest test`

Expected: PASS for environment fallback, database precedence, blank-password preservation, and wrong-key behavior.

- [ ] **Step 7: Commit Task 2**

```bash
git add backend/src/main/java/com/church/operation/entity/EmailSettings.java backend/src/main/java/com/church/operation/repo/EmailSettingsRepository.java backend/src/main/java/com/church/operation/service/EffectiveEmailSettings.java backend/src/main/java/com/church/operation/service/EmailSettingsDraft.java backend/src/main/java/com/church/operation/service/EmailSettingsResolver.java backend/src/main/java/com/church/operation/util/EmailSettingsSource.java backend/src/test/java/com/church/operation/service/EmailSettingsResolverTest.java
git commit -m "feat: resolve persisted email settings"
```

---

### Task 3: Runtime Mail Delivery And Password Reset Integration

**Files:**
- Create: `backend/src/main/java/com/church/operation/exception/EmailDeliveryException.java`
- Create: `backend/src/main/java/com/church/operation/service/RuntimeEmailSender.java`
- Modify: `backend/src/main/java/com/church/operation/service/PasswordResetEmailService.java`
- Test: `backend/src/test/java/com/church/operation/service/RuntimeEmailSenderTest.java`
- Test: `backend/src/test/java/com/church/operation/service/PasswordResetEmailServiceTest.java`
- Modify: `backend/src/test/java/com/church/operation/service/PasswordResetServiceTest.java`

**Interfaces:**
- Consumes: `EffectiveEmailSettings` and `EmailSettingsResolver` from Task 2.
- Produces: `void send(String recipient, String subject, String body)` using active settings.
- Produces: `void send(EffectiveEmailSettings settings, String recipient, String subject, String body)` for tests of unsaved settings.
- Produces: safe `EmailDeliveryException.Category` values `CONNECTION`, `AUTHENTICATION`, `TLS`, `SENDER_REJECTED`, `RECIPIENT_REJECTED`, and `DELIVERY`.

- [ ] **Step 1: Write failing runtime sender tests**

```java
@Test
void createsSenderFromEveryResolvedConfiguration() {
    when(resolver.resolve()).thenReturn(settings("first.smtp.test", "one"), settings("second.smtp.test", "two"));

    sender.send("admin@test.org", "First", "Body");
    sender.send("admin@test.org", "Second", "Body");

    assertThat(factory.createdHosts()).containsExactly("first.smtp.test", "second.smtp.test");
}

@Test
void mapsAuthenticationFailureToSanitizedCategory() {
    factory.failWith(new MailAuthenticationException("535 username secret rejected"));
    assertThatThrownBy(() -> sender.send(settings(), "admin@test.org", "Test", "Body"))
        .isInstanceOfSatisfying(EmailDeliveryException.class, failure -> {
            assertThat(failure.category()).isEqualTo(AUTHENTICATION);
            assertThat(failure.getMessage()).isEqualTo("The email server rejected the configured credentials.");
            assertThat(failure.getMessage()).doesNotContain("username", "secret");
        });
}
```

- [ ] **Step 2: Run sender tests and confirm the red state**

Run: `cd backend && mvn -Dtest=RuntimeEmailSenderTest,PasswordResetEmailServiceTest test`

Expected: compilation fails because the runtime sender does not exist and password-reset email still injects the startup `JavaMailSender`.

- [ ] **Step 3: Implement per-operation Spring mail sender construction**

```java
JavaMailSenderImpl build(EffectiveEmailSettings settings) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(settings.host());
    sender.setPort(settings.port());
    sender.setUsername(settings.username());
    sender.setPassword(new String(settings.password()));
    Properties mail = sender.getJavaMailProperties();
    mail.setProperty("mail.smtp.auth", Boolean.toString(settings.auth()));
    mail.setProperty("mail.smtp.starttls.enable", Boolean.toString(settings.startTls()));
    mail.setProperty("mail.smtp.connectiontimeout", "10000");
    mail.setProperty("mail.smtp.timeout", "10000");
    mail.setProperty("mail.smtp.writetimeout", "10000");
    return sender;
}
```

`RuntimeEmailSender` sets the message `from` from the resolved settings, sends it, converts mail exceptions to the safe categories above, and clears the resolved password in `finally`. Do not propagate provider exception messages to callers.

- [ ] **Step 4: Route password reset through the runtime sender**

```java
public PasswordResetEmailService(RuntimeEmailSender emailSender, PasswordResetProperties properties) {
    this.emailSender = emailSender;
    this.properties = properties;
}

public void sendResetEmail(Member member, String rawToken) {
    String resetLink = UriComponentsBuilder
        .fromUriString(properties.frontendBaseUrl())
        .path("/reset-password")
        .queryParam("token", rawToken)
        .build()
        .encode()
        .toUriString();
    emailSender.send(
        member.getPrimaryEmail(),
        "Reset your Church Operations password",
        "Use this link within 30 minutes to set a new password:\n\n" + resetLink
            + "\n\nIf you did not request this change, you can ignore this email."
    );
}
```

- [ ] **Step 5: Run focused mail and reset tests**

Run: `cd backend && mvn -Dtest=RuntimeEmailSenderTest,PasswordResetEmailServiceTest,PasswordResetServiceTest test`

Expected: PASS; tests prove a second send observes changed settings without recreating the Spring context.

- [ ] **Step 6: Commit Task 3**

```bash
git add backend/src/main/java/com/church/operation/exception/EmailDeliveryException.java backend/src/main/java/com/church/operation/service/RuntimeEmailSender.java backend/src/main/java/com/church/operation/service/PasswordResetEmailService.java backend/src/test/java/com/church/operation/service/RuntimeEmailSenderTest.java backend/src/test/java/com/church/operation/service/PasswordResetEmailServiceTest.java backend/src/test/java/com/church/operation/service/PasswordResetServiceTest.java
git commit -m "feat: send password resets with runtime email settings"
```

---

### Task 4: Verified Test-Before-Save Workflow

**Files:**
- Create: `backend/src/main/java/com/church/operation/service/EmailSettingsVerificationStore.java`
- Create: `backend/src/main/java/com/church/operation/service/EmailSettingsService.java`
- Create: `backend/src/main/java/com/church/operation/dto/EmailSettingsResponse.java`
- Create: `backend/src/main/java/com/church/operation/dto/EmailSettingsTestRequest.java`
- Create: `backend/src/main/java/com/church/operation/dto/EmailSettingsTestResponse.java`
- Create: `backend/src/main/java/com/church/operation/dto/EmailSettingsSaveRequest.java`
- Create: `backend/src/main/java/com/church/operation/dto/EmailSettingsResetRequest.java`
- Test: `backend/src/test/java/com/church/operation/service/EmailSettingsVerificationStoreTest.java`
- Test: `backend/src/test/java/com/church/operation/service/EmailSettingsServiceTest.java`

**Interfaces:**
- Consumes: crypto, resolver, repository, runtime sender, and `RuntimeEmailProperties` from Tasks 1-3.
- Produces: `EmailSettingsResponse get(Member actor)`.
- Produces: `EmailSettingsTestResponse test(Member actor, EmailSettingsTestRequest request)`.
- Produces: `EmailSettingsResponse save(Member actor, EmailSettingsSaveRequest request)`.
- Produces: `EmailSettingsResponse reset(Member actor, EmailSettingsResetRequest request)`.
- Produces: `Verification remember(String actorId, byte[] fingerprint)` and `void consume(String token, String actorId, byte[] fingerprint)`.

- [ ] **Step 1: Write verification-store tests**

```java
@Test
void tokenIsActorBoundFingerprintBoundExpiringAndSingleUse() {
    String token = store.remember("admin-1", bytes("fingerprint"));
    store.consume(token, "admin-1", bytes("fingerprint"));

    assertThatThrownBy(() -> store.consume(token, "admin-1", bytes("fingerprint")))
        .hasMessage("Test the email settings again before saving.");
}

@Test
void rejectsDifferentActorChangedSettingsAndExpiredToken() {
    String token = store.remember("admin-1", bytes("first"));
    assertThatThrownBy(() -> store.consume(token, "admin-2", bytes("first"))).isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> store.consume(token, "admin-1", bytes("second"))).isInstanceOf(IllegalArgumentException.class);
    clock.advance(Duration.ofMinutes(11));
    assertThatThrownBy(() -> store.consume(token, "admin-1", bytes("first"))).isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: Write service tests before implementation**

```java
@Test
void successfulTestDoesNotPersistAndReturnsVerificationToken() {
    var response = service.test(admin, testRequest("smtp.test", "new-secret", "admin@test.org"));
    verify(sender).send(any(EffectiveEmailSettings.class), eq("admin@test.org"), anyString(), anyString());
    verify(repository, never()).save(any());
    assertThat(response.verificationToken()).isNotBlank();
}

@Test
void saveRequiresExactSuccessfulTestAndEncryptsChangedPassword() {
    String token = service.test(admin, testRequest("smtp.test", "new-secret", "admin@test.org"))
        .verificationToken();
    EmailSettingsResponse response = service.save(admin, saveRequest("smtp.test", "new-secret", token));
    verify(repository).save(argThat(saved -> !saved.getPasswordCiphertext().contains("new-secret")));
    assertThat(response.passwordConfigured()).isTrue();
}

@Test
void failedDefaultTestKeepsDatabaseOverride() {
    doThrow(new EmailDeliveryException(CONNECTION)).when(sender).send(any(), anyString(), anyString(), anyString());
    assertThatThrownBy(() -> service.reset(admin, new EmailSettingsResetRequest("admin@test.org")))
        .isInstanceOf(EmailDeliveryException.class);
    verify(repository, never()).deleteById(anyString());
}
```

- [ ] **Step 3: Run focused workflow tests and confirm failure**

Run: `cd backend && mvn -Dtest=EmailSettingsVerificationStoreTest,EmailSettingsServiceTest test`

Expected: compilation fails because the workflow types do not exist.

- [ ] **Step 4: Add validated request and secret-free response DTOs**

```java
public record EmailSettingsTestRequest(
    @NotBlank @Size(max = 253) String host,
    @Min(1) @Max(65535) int port,
    @Size(max = 320) String username,
    @Size(max = 1024) String password,
    @NotBlank @Email @Size(max = 320) String fromAddress,
    @NotBlank @Email @Size(max = 320) String testRecipient
) {}

public record EmailSettingsResponse(
    String host, int port, String username, String fromAddress,
    boolean passwordConfigured, EmailSettingsSource source, Instant updatedAt
) {}
```

`EmailSettingsSaveRequest` repeats the five persisted draft fields and adds a nonblank `verificationToken`. `EmailSettingsResetRequest` contains only a validated `testRecipient`. `EmailSettingsTestResponse` contains `verificationToken`, `expiresAt`, and the safe message `Test email sent successfully.` Service validation requires username and an effective password only when the deployment setting `MAIL_SMTP_AUTH` is enabled.

- [ ] **Step 5: Implement transient token storage and canonical fingerprints**

Canonicalize using length-prefixed values in this exact order: host after trim/lowercase, decimal port, username after trim, password bytes, and from address after trim/lowercase. Include the effective password even when the request password was blank. HMAC the canonical byte sequence through `EmailSettingsCrypto`; do not store the canonical sequence.

```text
Verification(String actorId, byte[] fingerprint, Instant expiresAt)
String remember(String actorId, byte[] fingerprint)
void consume(String token, String actorId, byte[] fingerprint)
```

Implement `remember` with a 32-byte `SecureRandom` token encoded using URL-safe
Base64 without padding and a `ConcurrentHashMap<String, Verification>`.
Implement `consume` by removing the token first, checking actor and expiry,
and comparing fingerprints with `MessageDigest.isEqual`.

- [ ] **Step 6: Implement Admin-only service workflows**

`get` returns saved fields without decrypting the password and reports `DATABASE`, or returns startup values and `SERVER_DEFAULTS`. `test` validates Admin, resolves the draft password, sends the test message, computes the exact effective fingerprint, and returns a token. `save` resolves and fingerprints the request, consumes the matching token before persistence, encrypts only a nonblank new password, preserves existing encrypted values for a blank password, and stamps actor/timestamps. `reset` constructs settings only from startup defaults, sends the test, and deletes the singleton only after successful delivery.

```java
private void requireAdmin(Member actor) {
    if (actor == null || !actor.getRoles().contains(Role.ADMIN)) {
        throw new SecurityException("Administrator access is required.");
    }
}
```

- [ ] **Step 7: Run workflow tests**

Run: `cd backend && mvn -Dtest=EmailSettingsVerificationStoreTest,EmailSettingsServiceTest test`

Expected: PASS for authorization, test-no-save, exact token binding, expiry, blank password, encryption, immediate source status, and safe reset.

- [ ] **Step 8: Commit Task 4**

```bash
git add backend/src/main/java/com/church/operation/dto/EmailSettings*.java backend/src/main/java/com/church/operation/service/EmailSettingsVerificationStore.java backend/src/main/java/com/church/operation/service/EmailSettingsService.java backend/src/test/java/com/church/operation/service/EmailSettingsVerificationStoreTest.java backend/src/test/java/com/church/operation/service/EmailSettingsServiceTest.java
git commit -m "feat: require tested email settings before save"
```

---

### Task 5: Admin API, Safe Errors, And Audit Trail

**Files:**
- Create: `backend/src/main/java/com/church/operation/rest/EmailSettingsController.java`
- Modify: `backend/src/main/java/com/church/operation/exception/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/church/operation/util/SystemAuditOperation.java`
- Modify: `backend/src/main/java/com/church/operation/service/SystemAuditService.java`
- Modify: `backend/src/main/java/com/church/operation/service/EmailSettingsService.java`
- Test: `backend/src/test/java/com/church/operation/rest/EmailSettingsControllerTest.java`
- Modify: `backend/src/test/java/com/church/operation/service/EmailSettingsServiceTest.java`
- Modify: `backend/src/test/java/com/church/operation/service/SystemAuditServiceTest.java`

**Interfaces:**
- Consumes: `EmailSettingsService` and its DTOs from Task 4.
- Produces endpoints `GET /api/admin/email-settings`, `POST /api/admin/email-settings/test`, `PUT /api/admin/email-settings`, and `POST /api/admin/email-settings/reset`.
- Produces safe API error codes `EMAIL_CONFIGURATION_ERROR` and `EMAIL_DELIVERY_ERROR`.

- [ ] **Step 1: Write controller and redaction tests**

```java
@Test
void readsTestsSavesAndResetsWithoutReturningPasswordMaterial() throws Exception {
    mockMvc.perform(get("/api/admin/email-settings").header(AUTHORIZATION, adminToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passwordConfigured").value(true))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordCiphertext").doesNotExist());
}

@Test
void sanitizesProviderFailure() throws Exception {
    when(service.test(any(), any())).thenThrow(new EmailDeliveryException(AUTHENTICATION));
    mockMvc.perform(post("/api/admin/email-settings/test").contentType(APPLICATION_JSON)
            .header(AUTHORIZATION, adminToken()).content(validTestJson("super-secret")))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_ERROR"))
        .andExpect(content().string(not(containsString("super-secret"))));
}
```

- [ ] **Step 2: Run controller tests and confirm failure**

Run: `cd backend && mvn -Dtest=EmailSettingsControllerTest,SystemAuditServiceTest test`

Expected: compilation fails because the controller, API handlers, and audit operations do not exist.

- [ ] **Step 3: Add the controller**

```java
@RestController
@RequestMapping("/api/admin/email-settings")
public class EmailSettingsController {
    @GetMapping EmailSettingsResponse get(Authentication authentication) { return service.get(actor(authentication)); }
    @PostMapping("/test") EmailSettingsTestResponse test(Authentication authentication,
        @Valid @RequestBody EmailSettingsTestRequest request) { return service.test(actor(authentication), request); }
    @PutMapping EmailSettingsResponse save(Authentication authentication,
        @Valid @RequestBody EmailSettingsSaveRequest request) { return service.save(actor(authentication), request); }
    @PostMapping("/reset") EmailSettingsResponse reset(Authentication authentication,
        @Valid @RequestBody EmailSettingsResetRequest request) { return service.reset(actor(authentication), request); }
}
```

Keep `actor(Authentication)` identical to existing controllers and let `EmailSettingsService` enforce `ADMIN` for every operation.

- [ ] **Step 4: Add safe exception mapping**

```java
@ExceptionHandler(EmailConfigurationException.class)
ResponseEntity<ApiError> handleEmailConfiguration(EmailConfigurationException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ApiError("EMAIL_CONFIGURATION_ERROR", ex.getMessage()));
}

@ExceptionHandler(EmailDeliveryException.class)
ResponseEntity<ApiError> handleEmailDelivery(EmailDeliveryException ex) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(new ApiError("EMAIL_DELIVERY_ERROR", ex.getMessage()));
}
```

- [ ] **Step 5: Add safe audit operations**

Add `EMAIL_SETTINGS_TEST`, `EMAIL_SETTINGS_UPDATE`, and `EMAIL_SETTINGS_RESET` to `SystemAuditOperation`. Add only `configurationSource` and `configurationVersion` to `ALLOWED_METADATA_KEYS`. Wrap each service operation with `recordSuccess`/`recordFailure`; pass only the source/version and already-sanitized custom exceptions.

```java
audit.recordSuccess(actor, SystemAuditOperation.EMAIL_SETTINGS_UPDATE,
    Map.of("configurationSource", "DATABASE", "configurationVersion", saved.getCipherVersion()));
```

- [ ] **Step 6: Run controller, service, and audit tests**

Run: `cd backend && mvn -Dtest=EmailSettingsControllerTest,EmailSettingsServiceTest,SystemAuditServiceTest test`

Expected: PASS; JSON and captured audit records contain no request password, ciphertext, nonce, key, token, fingerprint, sender, recipient, host, or username.

- [ ] **Step 7: Commit Task 5**

```bash
git add backend/src/main/java/com/church/operation/rest/EmailSettingsController.java backend/src/main/java/com/church/operation/exception/GlobalExceptionHandler.java backend/src/main/java/com/church/operation/util/SystemAuditOperation.java backend/src/main/java/com/church/operation/service/SystemAuditService.java backend/src/main/java/com/church/operation/service/EmailSettingsService.java backend/src/test/java/com/church/operation/rest/EmailSettingsControllerTest.java backend/src/test/java/com/church/operation/service/EmailSettingsServiceTest.java backend/src/test/java/com/church/operation/service/SystemAuditServiceTest.java
git commit -m "feat: expose audited email settings API"
```

---

### Task 6: System Administration Email Settings UI

**Files:**
- Create: `frontend/src/api/emailSettings.ts`
- Modify: `frontend/src/views/SystemAdministrationView.vue`
- Modify: `frontend/src/views/SystemAdministrationView.test.ts`
- Modify: `frontend/src/styles/main.css`

**Interfaces:**
- Consumes: Task 5 endpoints.
- Produces: typed API functions `getEmailSettings`, `testEmailSettings`, `saveEmailSettings`, and `resetEmailSettings`.
- Produces: an `email` System Administration tab with test-before-save and an accessible guide dialog.

- [ ] **Step 1: Add failing Vue workflow tests and API mocks**

```ts
it('tests exact settings before enabling save and invalidates after edits', async () => {
  render(SystemAdministrationView);
  await fireEvent.click(screen.getByRole('tab', { name: 'Email Settings' }));
  expect(await screen.findByLabelText('Test recipient')).toHaveValue('admin@example.org');
  expect(screen.getByRole('button', { name: 'Save settings' })).toBeDisabled();

  await fireEvent.update(screen.getByLabelText('SMTP host'), 'smtp.example.org');
  await fireEvent.update(screen.getByLabelText('SMTP password'), 'secret');
  await fireEvent.click(screen.getByRole('button', { name: 'Send test email' }));
  expect(testEmailSettingsMock).toHaveBeenCalledWith(expect.objectContaining({ testRecipient: 'admin@example.org' }));
  expect(await screen.findByRole('button', { name: 'Save settings' })).toBeEnabled();

  await fireEvent.update(screen.getByLabelText('SMTP port'), '2525');
  expect(screen.getByRole('button', { name: 'Save settings' })).toBeDisabled();
});

it('explains encryption and deployment-only settings in the administration guide', async () => {
  render(SystemAdministrationView);
  await fireEvent.click(screen.getByRole('tab', { name: 'Email Settings' }));
  await fireEvent.click(screen.getByRole('button', { name: 'Email settings guide' }));
  expect(screen.getByRole('dialog')).toHaveTextContent('CHURCH_SETTINGS_ENCRYPTION_KEY');
  expect(screen.getByRole('dialog')).toHaveTextContent('STARTTLS');
});
```

- [ ] **Step 2: Run the view test and confirm failure**

Run: `cd frontend && npm test -- src/views/SystemAdministrationView.test.ts`

Expected: FAIL because the Email Settings tab and API module do not exist.

- [ ] **Step 3: Add the typed frontend API**

```ts
export interface EmailSettingsResponse {
  host: string;
  port: number;
  username: string;
  fromAddress: string;
  passwordConfigured: boolean;
  source: 'DATABASE' | 'SERVER_DEFAULTS';
  updatedAt?: string;
}

export interface EmailSettingsDraft {
  host: string; port: number; username: string; password: string; fromAddress: string;
}

export const getEmailSettings = () => getJson<EmailSettingsResponse>('/api/admin/email-settings');
export const testEmailSettings = (body: EmailSettingsDraft & { testRecipient: string }) =>
  postJson<typeof body, EmailSettingsTestResponse>('/api/admin/email-settings/test', body);
export const saveEmailSettings = (body: EmailSettingsDraft & { verificationToken: string }) =>
  putJson<typeof body, EmailSettingsResponse>('/api/admin/email-settings', body);
export const resetEmailSettings = (testRecipient: string) =>
  postJson<{ testRecipient: string }, EmailSettingsResponse>('/api/admin/email-settings/reset', { testRecipient });
```

- [ ] **Step 4: Add the Email Settings tab and form**

Extend `activeTab` to `'backup' | 'restore' | 'fiscal' | 'email'`. Use Lucide `Mail`, `Send`, `Save`, `RotateCcw`, `Info`, and `X` icons. Load settings only when the tab opens. Initialize `testRecipient` from `authState.currentUser?.primaryEmail` only when it passes email validation; the bootstrap login ID `admin` initializes an empty recipient. Always set the password input to an empty string after load or save. In the view test, set the signed-in Admin primary email to `admin@example.org` before asserting the default.

```vue
<button type="button" role="tab" :aria-selected="activeTab === 'email'" @click="openEmailSettings">
  <Mail :size="18" aria-hidden="true" /><span>Email Settings</span>
</button>

<form v-else-if="activeTab === 'email'" class="panel administration-form email-settings-form"
      @submit.prevent="saveTestedEmailSettings">
  <label>SMTP host<input v-model.trim="emailForm.host" required /></label>
  <label>SMTP port<input v-model.number="emailForm.port" type="number" min="1" max="65535" required /></label>
  <label>Username<input v-model.trim="emailForm.username" required /></label>
  <label>SMTP password<input v-model="emailForm.password" type="password" autocomplete="new-password" /></label>
  <label>From address<input v-model.trim="emailForm.fromAddress" type="email" required /></label>
  <label>Test recipient<input v-model.trim="testRecipient" type="email" required /></label>
  <button type="button" @click="sendEmailTest"><Send :size="18" />Send test email</button>
  <button type="submit" :disabled="!verificationToken"><Save :size="18" />Save settings</button>
</form>
```

Watch the five persisted form fields and clear `verificationToken` after any user edit. Suppress invalidation while applying API data through an `applyingEmailSettings` flag. Show `Password configured` or `No password configured`, plus `Database settings` or `Server defaults`, without displaying secrets.

- [ ] **Step 5: Add safe reset and guide dialog**

Reset opens a confirmation dialog, calls `resetEmailSettings(testRecipient)`, clears password/token, and reloads source status. The information icon opens a native accessible `<dialog>` or existing modal pattern with all guide points from the design. Focus the close button on open and return focus to the information button on close.

- [ ] **Step 6: Add responsive styling**

Add `.email-settings-grid`, `.email-settings-status`, `.email-guide-dialog`, and action-row rules to `frontend/src/styles/main.css`. Reuse existing colors, input styles, 8px-or-less radii, and responsive administration breakpoints. Keep labels and controls from overflowing at 320px width.

- [ ] **Step 7: Run frontend tests and build**

Run: `cd frontend && npm test -- src/views/SystemAdministrationView.test.ts`

Expected: PASS for load, default recipient, password masking, test/save invalidation, failures, reset, and guide accessibility.

Run: `cd frontend && npm run build`

Expected: `vue-tsc --noEmit` and Vite build both complete successfully.

- [ ] **Step 8: Commit Task 6**

```bash
git add frontend/src/api/emailSettings.ts frontend/src/views/SystemAdministrationView.vue frontend/src/views/SystemAdministrationView.test.ts frontend/src/styles/main.css
git commit -m "feat: manage runtime email settings"
```

---

### Task 7: Deployment Configuration, Backup Coverage, And Guides

**Files:**
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `docs/user-guide/user-guide-content.md`
- Modify: `docs/user-guide/user-guide-content-ko.md`
- Modify: `docs/user-guide/build_user_guide.py`
- Modify: `docs/user-guide/build_user_guide_ko.py`
- Create: `docs/user-guide/screenshots/13-email-settings.png`
- Regenerate: `docs/user-guide/Church Operations User Guide.docx`
- Regenerate: `docs/user-guide/Church Operations User Guide.pdf`
- Regenerate: `docs/user-guide/교회운영 메뉴얼.docx`
- Regenerate: `docs/user-guide/교회운영 메뉴얼.pdf`
- Modify: `backend/src/test/java/com/church/operation/service/MongoDatabaseRoundTripIntegrationTest.java`

**Interfaces:**
- Consumes: the `email_settings` MongoDB collection from Task 2.
- Produces: deployment examples and bilingual operating instructions that contain no real secrets.

- [ ] **Step 1: Add a failing full-backup round-trip assertion**

```java
database.getCollection("email_settings").insertOne(new Document("_id", "runtime-email")
    .append("host", "smtp.example.org")
    .append("passwordCiphertext", "encrypted-value")
    .append("passwordNonce", "nonce"));

// After export, destructive replacement, and restore:
assertThat(database.getCollection("email_settings").find(eq("_id", "runtime-email")).first())
    .isNotNull()
    .extracting(document -> document.getString("passwordCiphertext"))
    .isEqualTo("encrypted-value");
```

- [ ] **Step 2: Run the backup integration test**

Run: `cd backend && mvn -Dtest=MongoDatabaseRoundTripIntegrationTest test`

Expected: PASS when Docker/Testcontainers is available because full backup already exports every application collection; the new assertion documents required coverage. If Docker is unavailable, record the environment limitation and run `MongoDatabaseExportServiceTest` as the focused fallback.

- [ ] **Step 3: Add the encryption-key deployment placeholder**

Add this to `.env.example` without a value:

```dotenv
# Base64-encoded 32-byte key. Generate once, store securely, and retain for restores.
CHURCH_SETTINGS_ENCRYPTION_KEY=
```

Add this environment pass-through to the backend service while preserving all existing user changes in `docker-compose.yml`:

```yaml
CHURCH_SETTINGS_ENCRYPTION_KEY: ${CHURCH_SETTINGS_ENCRYPTION_KEY:-}
```

Validate with `docker compose config`; do not proceed while Compose reports interpolation errors.

- [ ] **Step 4: Add English and Korean guide sections**

Document:

```markdown
### Email Settings

Administrators can test and save the SMTP host, port, username, password, and sender address without restarting the application. Send a test email before saving. A blank password field keeps the currently configured password.

SMTP authentication and STARTTLS remain server settings. Keep `CHURCH_SETTINGS_ENCRYPTION_KEY` secure and unchanged. A full backup contains the encrypted SMTP password, but a restored server must use the same encryption key.
```

Add an equivalent natural Korean translation to `user-guide-content-ko.md`, including the exact environment-variable names. Update both document builders so `System Administration` / `시스템 관리` appears in the generated contents list. Capture `13-email-settings.png` from the completed local UI using current church branding and reference it from both guide content files.

- [ ] **Step 5: Regenerate and inspect both manuals**

Run the existing builders with the workspace document runtime:

```bash
python3 docs/user-guide/build_user_guide.py
python3 docs/user-guide/build_user_guide_ko.py
python3 docs/user-guide/build_user_guide_ko_pdf.py
```

Render the DOCX files to page images using the documents skill workflow. Verify the new section is present, headings are not orphaned, screenshots and tables do not overlap, and both PDFs match the regenerated documents.

- [ ] **Step 6: Validate deployment configuration and focused tests**

Run: `docker compose config`

Expected: exit code 0 and the backend environment contains `CHURCH_SETTINGS_ENCRYPTION_KEY` without invalid interpolation.

Run: `cd backend && mvn -Dtest=MongoDatabaseExportServiceTest,MongoDatabaseImportServiceTest test`

Expected: PASS.

- [ ] **Step 7: Commit Task 7**

```bash
git add .env.example docker-compose.yml docs/user-guide backend/src/test/java/com/church/operation/service/MongoDatabaseRoundTripIntegrationTest.java
git commit -m "docs: explain runtime email configuration"
```

---

### Task 8: End-To-End Verification And Visual QA

**Files:**
- Modify only if verification exposes a defect in files already listed above.

**Interfaces:**
- Consumes: all Tasks 1-7.
- Produces: verified runtime email settings with no outstanding test or visual regressions.

- [ ] **Step 1: Run the complete backend suite**

Run: `cd backend && mvn test`

Expected: all JUnit, Mockito, Spring MVC, and available Testcontainers tests pass with zero failures and zero errors.

- [ ] **Step 2: Run the complete frontend suite and production build**

Run: `cd frontend && npm test`

Expected: all Vitest suites pass.

Run: `cd frontend && npm run build`

Expected: TypeScript checking and Vite production build pass.

- [ ] **Step 3: Rebuild the local Docker application with a generated development key**

Generate a local-only Base64 key with `openssl rand -base64 32`, place it only in the untracked `.env`, then run:

```bash
docker compose up --build -d
docker compose ps
```

Expected: MongoDB, backend, and frontend are healthy/running; no key or SMTP password appears in versioned diffs.

- [ ] **Step 4: Verify the live workflow**

Using the in-app browser at `http://localhost:5173`:

1. Sign in as an Admin.
2. Open System Administration > Email Settings.
3. Confirm the test recipient defaults to the Admin login ID/email.
4. Confirm authentication and STARTTLS controls are absent.
5. Open and read the administration guide.
6. Enter local Mailpit settings, send a test, and confirm Save becomes enabled.
7. Change one persisted field and confirm Save becomes disabled.
8. Test again, save, and confirm source changes to `Database settings` without restart.
9. Request a password reset and confirm Mailpit receives it.
10. Restart only the backend and confirm another reset still sends.
11. Reset to server defaults and confirm the fallback test succeeds before deletion.

- [ ] **Step 5: Perform responsive and console QA**

Capture Playwright/browser screenshots at desktop `1440x900` and mobile `390x844`. Verify no text overlap, horizontal scrolling, nested cards, clipped buttons, or inaccessible dialog controls. Confirm the browser console has no errors and all form labels are discoverable by accessible name.

- [ ] **Step 6: Confirm secret redaction and repository cleanliness**

Run:

```bash
git diff --check
git status --short
git grep -nE '^CHURCH_SETTINGS_ENCRYPTION_KEY=.+$' -- '*.env' '.env*'
```

Expected: no whitespace errors; only intentional files are changed; the secret scan returns no matches; no committed encryption-key value or SMTP password exists. Preserve unrelated pre-existing worktree changes.

- [ ] **Step 7: Request final code review**

Use `superpowers:requesting-code-review` against the implementation range. Address correctness, security, backup/restore, redaction, UI, and test findings before claiming completion.

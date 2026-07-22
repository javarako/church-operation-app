# Runtime Email Settings Design

## Goal

Add an Admin-only Email Settings area to System Administration. Administrators
can test and activate SMTP server changes without restarting the backend while
the SMTP password remains encrypted at rest.

## Scope

This change includes:

- Runtime SMTP host, port, username, password, and sender-address settings.
- MongoDB persistence with authenticated encryption for the SMTP password.
- Existing environment-based email configuration as the initial and fallback
  configuration.
- Test-before-save enforcement.
- Immediate use of saved settings by password-reset email.
- Reset to server defaults.
- An in-page administration guide.
- Audit events, backend tests, and frontend tests.

This change does not:

- Move SMTP authentication or STARTTLS settings out of `.env`.
- Make the password-reset frontend URL configurable through the UI.
- Make the encryption key configurable through the UI.
- Add email templates, bulk email, marketing email, or email history.
- Add the broader Church Settings page.

## Roles And Access

Only `ADMIN` may read the Email Settings status, test settings, save settings,
or reset them to server defaults. The backend enforces this authorization; the
frontend is not a security boundary.

No current-login-password confirmation is required for these actions.

## Configuration Ownership

### Runtime Database Settings

The Email Settings page manages:

- SMTP host
- SMTP port
- SMTP username
- SMTP password
- From address

When a database override exists, these values take precedence over the
corresponding startup values. Changes take effect without a backend restart.

### Deployment Settings

The following remain in `.env` and require a backend restart when changed:

- `MAIL_SMTP_AUTH`
- `MAIL_SMTP_STARTTLS`
- `PASSWORD_RESET_FRONTEND_BASE_URL`
- `CHURCH_SETTINGS_ENCRYPTION_KEY`

The existing `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, and
`PASSWORD_RESET_FROM_ADDRESS` values remain the server defaults. They are used
when no database override exists.

`CHURCH_SETTINGS_ENCRYPTION_KEY` must contain a Base64-encoded 256-bit random
key. It is a deployment secret and must never be stored in MongoDB, returned by
an API, printed in logs, or added to an archive.

## Persistence And Encryption

Store one singleton `EmailSettings` MongoDB document with:

- A fixed identifier
- Host
- Port
- Username
- From address
- Cipher format version
- Encrypted SMTP password ciphertext
- Random nonce
- Created and updated timestamps
- Creating and updating member identifiers

Encrypt a newly supplied SMTP password with AES-256-GCM and a new 12-byte
cryptographically random nonce. Use a 128-bit authentication tag. The
application decrypts the password only in memory immediately before building a
mail sender. Plaintext passwords must not be persisted, logged, audited, or
included in exceptions.

The API never returns ciphertext, a nonce, or plaintext. It returns only a
`passwordConfigured` boolean. An empty password submitted while editing means
preserve the currently stored encrypted password. If no database password
exists, an empty value means use the current `.env` password.

The full database backup includes the encrypted settings document. A server
restoring that document must have the same `CHURCH_SETTINGS_ENCRYPTION_KEY`.
The administration guide must identify that operational requirement. A missing
or different key must not prevent the rest of the application from starting,
but database-backed email sends must fail with a clear, sanitized configuration
error.

## Effective Settings Service

Introduce one service responsible for resolving effective email settings:

1. Load the database override when present.
2. Otherwise read the existing Spring Boot environment defaults.
3. Read authentication and STARTTLS exclusively from startup configuration.
4. Decrypt the SMTP password only when a sender is required.
5. Construct an isolated mail sender for the email operation.
6. Clear temporary password character arrays where practical after use.

Constructing a sender per operation is preferred over caching. Email volume is
low, and this ensures saved settings and full database restores take effect
without restart or cache invalidation.

Password-reset email must use this effective-settings service. The reset-link
base URL and token lifetime continue to come from the existing password-reset
startup properties.

## Administration UI

Add an `Email Settings` tab to the existing System Administration page. Keep
the existing Full Backup, Full Restore, and Fiscal Archive workflows unchanged.

The form contains:

- SMTP host text input
- SMTP port numeric input
- Username text input
- Password input
- From address email input
- Test recipient email input
- `Send test email` command
- `Save settings` command
- `Reset to server defaults` command

The test recipient initially uses the signed-in administrator's primary email
and remains editable. The password input is always blank when settings are
loaded. Adjacent status text communicates whether a password is already
configured without revealing it.

Place an information icon beside the section title. It opens a concise
administration guide that explains:

- Where SMTP host, port, username, password, and sender values come from.
- That the password is encrypted in MongoDB.
- That an empty password field preserves the current password.
- That settings must pass a test before saving.
- That SMTP authentication and STARTTLS remain in `.env`.
- That the encryption key must not be changed or lost.
- That another server restoring a backup needs the same encryption key.
- That reset returns email configuration to `.env` defaults.

Do not show the encryption key, SMTP password, authentication toggle, STARTTLS
toggle, or password-reset frontend URL in the UI.

## Test-Before-Save Workflow

1. Admin opens Email Settings and edits the form.
2. `Send test email` submits the unsaved values and test recipient.
3. The backend resolves an empty draft password from the existing database
   password or `.env` fallback.
4. The backend sends a short, clearly identified configuration test email.
5. On success, the backend returns an opaque, single-use verification token.
6. The token is bound to the Admin member, an HMAC fingerprint of the exact
   effective tested values, and a ten-minute expiry.
7. The frontend enables `Save settings` only while the token is valid.
8. Changing any persisted field clears the token and requires another test.
9. Save submits the form and token. The backend independently recomputes and
   compares the HMAC fingerprint before persisting settings.
10. A successful save consumes the token and takes effect immediately.

The fingerprint must use a keyed HMAC rather than a plain password hash so it
cannot be used for offline guessing if exposed. Test sessions are transient
server memory and are invalidated by backend restart; they contain no plaintext
SMTP password.

A failed test never changes the active database configuration.

## Reset To Server Defaults

`Reset to server defaults` displays a confirmation dialog but does not request
the Admin's login password. Before deleting the database override, the backend
uses the `.env` defaults to send a test message to the entered test recipient.
The override is deleted only when that test succeeds. The change takes effect
immediately.

If server-default testing fails, retain the current working database override
and return a sanitized error.

## Validation And Errors

Validate on both client and server:

- Host is required and has a reasonable maximum length.
- Port is between 1 and 65535.
- Username is required.
- From address and test recipient are valid email addresses.
- A password is required when neither a stored password nor an environment
  password exists.
- All input sizes are bounded.

Map email failures to actionable categories without leaking provider response
details that may contain credentials:

- Connection failure
- Authentication failure
- TLS negotiation failure
- Sender rejected
- Recipient rejected
- Invalid or unavailable encryption key
- General delivery failure

API errors, application logs, and audit events must not include the SMTP
password, ciphertext, nonce, encryption key, verification fingerprint, or
verification token. The global error response must not expose nested mail
library exceptions.

## API Boundaries

Provide Admin-only endpoints for:

- Reading the editable settings and effective-source status
- Sending a test email from unsaved settings
- Saving settings with a valid verification token
- Testing and resetting to server defaults

The read response indicates whether the effective source is `DATABASE` or
`SERVER_DEFAULTS` and whether a password is configured. Secret fields are
write-only.

Use dedicated request and response DTOs with Bean Validation. Do not expose the
MongoDB entity directly.

## Audit

Add distinct system audit operations for:

- Email settings test
- Email settings update
- Email settings reset

Record success and failure, actor, timestamp, result, and safe metadata such as
configuration version or source. Do not audit host, username, sender,
recipient, exception chains, or any secret material.

## Testing

### Backend

- AES-GCM round trip, random nonce, tamper detection, missing key, and wrong key
- Database override and environment fallback resolution
- Blank password preservation
- No-password validation when no fallback exists
- Runtime password-reset email after an update without restart
- Test-email success and categorized failures
- Verification token actor binding, fingerprint binding, expiry, single use,
  and invalidation after changes
- Save rejection when the test was not completed
- Server-default reset success and failure preservation
- ADMIN-only endpoint authorization
- API and audit redaction of all secret values
- Full backup and restore of encrypted settings with the same key
- Restored settings behavior with a missing or different key

### Frontend

- Email Settings tab visibility and form loading
- Password field remains blank and displays configured status
- Current Admin email initializes the test recipient
- Successful test enables save
- Editing a persisted field invalidates the test
- Failed test leaves save disabled and shows a safe error
- Save success refreshes source and configured status
- Reset confirmation, success, and failure behavior
- Administration guide contents and accessibility

## Documentation

Update `.env.example`, deployment documentation, and both English and Korean
user guides. Document key generation, secure retention, backup restoration,
runtime versus restart-required settings, testing, saving, and resetting.

The repository must contain only a placeholder for
`CHURCH_SETTINGS_ENCRYPTION_KEY`; no real key or SMTP credential may be
committed.

## Acceptance Criteria

- An Admin can test and save SMTP host, port, username, password, and sender
  changes without restarting the backend.
- An untested configuration cannot be saved through either the UI or API.
- Password-reset email uses the saved configuration immediately.
- SMTP authentication and STARTTLS remain deployment-only `.env` settings.
- The SMTP password is authenticated-encrypted at rest and never returned.
- Restarting the backend preserves working database email settings when the
  same encryption key is present.
- A failed test or failed reset cannot replace the working configuration.
- Full backup includes encrypted settings but excludes the encryption key.
- The administration guide clearly explains setup and key-retention duties.
- Tests cover authorization, encryption, runtime behavior, restore behavior,
  error redaction, and the complete UI workflow.

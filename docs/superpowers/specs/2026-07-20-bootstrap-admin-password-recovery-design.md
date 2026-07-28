# Bootstrap Administrator Password Recovery Design

## Purpose

Allow the protected bootstrap administrator, whose login ID is `admin`, to use
the existing email-based forgot-password workflow without changing the bootstrap
login ID or creating another system administrator.

## User Experience

- The bootstrap administrator continues to sign in with `admin`.
- On My Profile, the bootstrap administrator's existing Secondary Email field is
  labelled `Recovery email`.
- The bootstrap administrator enters a valid recovery email and saves the
  profile.
- On Forgot Password, the administrator enters that recovery email.
- The reset email is delivered to the recovery email, and the resulting token
  resets the password for the `admin` member.
- Other members continue to request password resets with their primary email.
- The forgot-password response remains generic so it does not reveal whether an
  account or recovery email exists.

## Backend Design

`PasswordResetService` resolves a reset destination in this order:

1. An active, unlocked member whose primary email matches the submitted email.
2. The active, unlocked bootstrap member whose primary email is `admin` and
   whose secondary email matches the submitted email.

The generated reset token remains associated with the target member's primary
identifier. The email service receives the resolved delivery address explicitly,
so bootstrap reset mail is sent to the recovery email instead of to `admin`.

The bootstrap administrator's recovery email must not match another member's
primary email. This validation occurs when the administrator profile is saved,
preventing an ambiguous forgot-password request.

## Security

- Only the bootstrap member identified by primary email `admin` receives this
  secondary-email recovery behavior.
- Recovery requires access to the configured mailbox and possession of the
  short-lived, single-use reset token.
- Existing password rules, token expiry, token hashing, token invalidation, and
  generic forgot-password responses remain unchanged.
- Successful reset revokes active sessions for the bootstrap administrator.

## Frontend Design

`ProfileView` detects the bootstrap member by its primary email and changes only
the visible label from `Secondary email` to `Recovery email`. The underlying
member field and API payload remain unchanged.

The Forgot Password screen needs no layout or workflow change.

## Validation And Errors

- Blank recovery email remains allowed, but password recovery for `admin` is
  unavailable until one is saved.
- Normal email-format validation applies.
- A recovery email already used as another member's primary email is rejected
  with a clear profile-save error.
- Mail delivery failure continues to be logged without exposing account details
  in the public response.

## Tests

- Bootstrap admin reset requests resolve by secondary email.
- Reset mail is sent to the recovery address and the token targets `admin`.
- Regular members still resolve by primary email.
- Another member's primary email cannot be saved as the bootstrap recovery
  email.
- Non-bootstrap secondary emails do not initiate password resets.
- My Profile displays `Recovery email` for `admin` and `Secondary email` for
  normal members.

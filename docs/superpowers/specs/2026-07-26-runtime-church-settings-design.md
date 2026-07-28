# Runtime Church Settings And MongoDB 8 Design

## Goal

Add an ADMIN-only Church Settings area to System Administration. Administrators
can update church identity and branding without restarting the application or
logging in again. Also display the deployed application version in the shared
menu and upgrade the supported MongoDB deployment from 6.0 to the MongoDB 8.0
release family.

## Scope

This change includes:

- Runtime church information stored in MongoDB.
- Runtime logo and dashboard banner stored in MongoDB GridFS.
- Existing `application.yml` values and bundled images as server defaults.
- Immediate refresh of church information and branding after save or reset.
- Use of the effective logo by newly generated tax receipts and financial Excel
  reports.
- Full backup and restore of runtime church settings and branding.
- Backend build-version display below `Church Operations` in the menu.
- A pinned MongoDB `8.0.28` Docker image for completed installations.
- A documented, staged MongoDB 6.0 to 7.0 to 8.0 upgrade procedure.

This change does not:

- Modify previously downloaded PDF or Excel reports.
- Store deployment-only settings such as timezone, upload limits, SMTP flags,
  encryption keys, or filesystem paths in the church settings document.
- Automatically change MongoDB feature compatibility version during application
  startup.
- Automatically perform a production database upgrade.

## Roles And Access

Only `ADMIN` may read the editable Church Settings form, save overrides, upload
or replace branding, or reset to server defaults. The backend enforces this
authorization; frontend visibility is not a security boundary.

All authenticated application users may read the effective church information
and branding required by the shared menu and dashboard. Public report downloads
continue to follow their existing authorization rules.

No current-login-password confirmation is required for Church Settings changes.
Every update and reset records a safe system audit event.

## Configuration Ownership

### Runtime Database Settings

The Church Settings page manages:

- Church name
- Address
- Contact information
- Treasurer name
- Charity registration number
- Receipt issue location
- Website
- Church logo
- Dashboard banner

### Server Defaults

The existing values under `church.information` and `church.branding` in
`application.yml` remain the defaults. They are used when no database override
exists, after an administrator resets the settings, or when a referenced GridFS
image is unavailable.

Timezone, list page size, upload limits, data-management paths, SMTP settings,
and other operational values remain deployment configuration.

## Persistence

Store one singleton `ChurchSettings` MongoDB document containing:

- A fixed identifier
- Each editable text value
- Optional GridFS identifiers for logo and banner
- Logo and banner content types
- Created and updated timestamps
- Creating and updating member identifiers

Store image bytes in the application's existing MongoDB database through
GridFS. Do not store Base64 image data in the settings document and do not add a
separate filesystem volume.

The full database export and restore includes the settings document and GridFS
files. Fiscal archive and clean operations do not remove church settings or
branding because they are system-wide configuration, not fiscal-year data.

When replacing an image, upload the new GridFS file first, save the settings
reference, and only then remove the previous file. A failure before the settings
save leaves the active image unchanged and removes the unreferenced new file.
After a successful reset, remove the formerly referenced override images only
after deleting the settings document.

## Effective Settings Resolver

Introduce one resolver as the source of effective church identity and branding:

1. Load the singleton database override when present.
2. Fall back field-by-field to `application.yml` defaults for absent values.
3. Resolve GridFS image endpoints when referenced files exist.
4. Fall back to bundled branding paths when an override is absent or missing.
5. Expose the same effective values to API controllers, tax receipts, and Excel
   report generators.

The current immutable `ChurchInformationProperties` remains the deployment
defaults object. Runtime consumers must use the resolver instead of reading the
properties record directly.

## Branding Delivery And Refresh

Expose read-only endpoints for the effective logo and banner. Database-backed
image URLs include an update-version query parameter, for example:

```text
/api/church-information/logo?v=20260726131500
/api/church-information/banner?v=20260726131500
```

The version changes whenever branding changes and prevents stale browser cache
entries. Responses use the stored image content type and conservative cache
headers. Bundled images remain accessible through their current static paths.

After a successful save or reset, the frontend updates one shared church
information state and notifies the application layout and dashboard. The menu
logo, dashboard banner, and visible church text change immediately without a
new login or page reload.

Newly generated tax-receipt PDFs and quarterly or yearly Excel reports load the
effective logo bytes. Existing generated or downloaded artifacts remain
unchanged.

## Administration UI

Add a `Church Settings` tab to the existing System Administration page. It is
visible only to ADMIN and contains:

- Text inputs for all editable church information fields
- Current logo preview and logo file chooser
- Current banner preview and banner file chooser
- `Save settings`
- `Reset to server defaults`

The image preview updates locally after file selection, while the rest of the
application changes only after a successful save. Reset requires confirmation
and restores the effective server defaults immediately.

The page explains that settings and images are included in full backup and
restore, while `application.yml` and bundled assets remain the reset defaults.

## Image Validation

Validate on both client and server:

- Accept PNG and JPEG only.
- Maximum size is 5 MB per image.
- Verify the decoded image format rather than trusting the filename or supplied
  content type.
- Reject empty, malformed, or unsupported image files.
- Limit width and height to 8,000 pixels each and total decoded area to 40
  megapixels to prevent decompression-bomb inputs.
- Return a clear validation error without replacing the active image.

The dashboard preserves the banner's natural aspect ratio without cropping or
stretching. Existing report layout rules continue to constrain the logo within
their allocated space.

## Application Version

Generate Spring Boot build metadata during the Maven build and expose the
deployed backend version with effective church information. Display the value
as `v<version>` immediately below `Church Operations` in the shared menu.

The backend build is authoritative so the displayed value identifies the
deployed application package. Development runs without build metadata use the
project version fallback rather than failing startup.

## API Boundaries

Provide:

- An authenticated read endpoint for effective church information, branding
  URLs, list page size, and application version.
- ADMIN-only endpoints to read the editable override state.
- An ADMIN-only multipart save endpoint for text fields and optional logo or
  banner replacements.
- An ADMIN-only reset endpoint.
- Read-only effective logo and banner endpoints.

Use dedicated request and response DTOs. Do not expose the MongoDB entity or
GridFS identifiers to the frontend. Existing API fields remain compatible while
adding the application version.

## Validation And Errors

- Trim every text field. Limit church name, treasurer name, charity registration
  number, and receipt issue location to 200 characters; address and contact
  information to 500 characters; and website to 500 characters.
- Require church name and address.
- Validate the website as an HTTP or HTTPS URL when supplied.
- Return `403` for non-ADMIN modification attempts.
- Return `400` for invalid text or image input.
- Preserve current settings when file upload or persistence fails.
- Fall back to server defaults when runtime settings or branding are absent.
- Do not expose internal GridFS identifiers or exception details in API errors.

Audit success and failure for church settings update and reset. Safe metadata
may identify whether logo or banner changed, but audit entries must not contain
image bytes or complete submitted field values.

## MongoDB 8 Upgrade

Pin completed installations to the official `mongo:8.0.28` image rather than a
floating major tag.

Existing MongoDB 6.0 data cannot be upgraded directly to MongoDB 8.0. The
deployment guide must require this sequence:

1. Stop writes and create a full application database backup plus a provider or
   volume snapshot where available.
2. Confirm the MongoDB 6.0 feature compatibility version is `6.0`.
3. Upgrade the MongoDB container to the pinned 7.0 release `7.0.39`.
4. Start and verify the application, database collections, GridFS files, and
   backup/restore workflow.
5. Set feature compatibility version to `7.0` with explicit confirmation.
6. Stop the application and upgrade MongoDB to `8.0.28`.
7. Start and verify the application again.
8. Keep FCV at `7.0` for a short burn-in period so binary rollback remains
   practical.
9. After approval, set FCV to `8.0` with explicit confirmation.

The application never changes FCV automatically. Fresh installations start on
MongoDB 8.0.28. Upgrade commands and rollback boundaries are documented for
local Docker and hosted deployments.

Primary references:

- [MongoDB standalone upgrade to 7.0](https://www.mongodb.com/docs/manual/release-notes/7.0-upgrade-standalone/)
- [MongoDB standalone upgrade to 8.0](https://www.mongodb.com/docs/manual/release-notes/8.0-upgrade-standalone/)
- [Official MongoDB Docker image tags](https://hub.docker.com/_/mongo/tags)

## Testing

### Backend

- ADMIN-only read-edit, update, image replacement, and reset authorization
- Database override and server-default fallback resolution
- Field-by-field fallback for incomplete legacy settings
- PNG and JPEG validation, unsupported formats, corrupt files, excessive size,
  and excessive dimensions
- GridFS upload, replacement cleanup, failed-save rollback, and missing-file
  fallback
- Effective logo loading in tax-receipt and Excel report generation
- Full backup and restore of settings plus GridFS branding
- Fiscal archive and clean preservation of settings and branding
- Build-version response with build metadata and development fallback
- Application context startup with all new services
- MongoDB 8 Testcontainers integration coverage where Docker is available

### Frontend

- ADMIN-only Church Settings tab
- Form loading and server-default status
- Logo and banner preview and validation
- Save success and safe failure behavior
- Immediate menu, dashboard, and church-information refresh without relogin
- Reset confirmation and immediate fallback branding
- Application version display under the menu title

### Deployment

- Fresh MongoDB 8.0.28 startup
- Staged test migration from a copy of MongoDB 6 data through 7.0.39 to 8.0.28
- FCV verification at each stage
- Post-upgrade login, member, offering, finance, report, branding, and full
  backup/restore smoke tests

## Documentation

Update:

- Docker deployment and upgrade instructions
- English user guide
- Korean user guide
- Backend feature inventory
- Frontend functionality inventory

The administration guide explains which values are runtime settings, which
remain deployment settings, how reset works, and how branding participates in
backup and restore.

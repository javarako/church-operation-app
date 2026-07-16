# Church Operations App

Multi-user church operations web app for members, offerings, finance, budgets, reports, and reference data.

## Local Run

```bash
docker compose up --build
```

Open:

- Frontend: http://localhost:5173
- Backend health: http://localhost:8080/actuator/health

Initial login:

- Username: `admin`
- Password: `password`

The first login requires a password change before using the app.

## Configuration

Church information is configured in `backend/src/main/resources/application.yml` or environment variables:

- `CHURCH_NAME`
- `CHURCH_ADDRESS`
- `CHURCH_CONTACT_INFO`
- `CHURCH_TREASURER_NAME`
- `CHURCH_CHARITY_REGISTRATION_NUMBER`
- `CHURCH_RECEIPT_ISSUE_LOCATION`
- `CHURCH_WEBSITE`

Copy `.env.example` to `.env` for local Docker configuration. The example also documents the fiscal-year start month, list page size, member-image limit, and the maximum encrypted backup upload size. Do not commit `.env`, SMTP credentials, or backup/archive passwords.

Full backup and restore operations use the `church-temp` Docker volume at `/var/lib/church-operation/temp`. It is temporary working space only, not a backup destination. Downloaded backup files must be retained separately by the church. Completed and expired operations clean their temporary files; the volume can also be removed when the application is fully stopped and no restore is pending.

Branding assets are served from `backend/src/main/resources/static/branding`.

## Password reset email

For delivery through Brevo or another SMTP provider, configure these environment variables
without committing the SMTP credentials:

```text
MAIL_HOST=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USERNAME=<Brevo SMTP login>
MAIL_PASSWORD=<Brevo SMTP key>
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
PASSWORD_RESET_FROM_ADDRESS=no-reply@your-church-domain.org
PASSWORD_RESET_FRONTEND_BASE_URL=https://your-church-app.example.org
PASSWORD_RESET_TOKEN_LIFETIME=30m
```

The sender address or domain must be authorized in the configured email
provider before production messages can be delivered reliably.

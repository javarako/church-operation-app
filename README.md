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

Branding assets are served from `backend/src/main/resources/static/branding`.

## Password reset email

Local Docker runs include Mailpit. Password reset messages are captured at
`http://localhost:8025` and are not delivered outside the local machine.

For production delivery through Brevo, configure these environment variables
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

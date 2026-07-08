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

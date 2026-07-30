# Per-Installation Frontend Hostname Design

## Goal

Deploy the same Church Operations source and container image for every church while allowing each installation to use its own DNS hostname. A deployment operator must not edit `vite.config.ts` when adding or changing a church hostname.

## Context

The frontend runs the Vite development server inside its Docker container. Vite rejects HTTP requests whose `Host` value is not explicitly allowed. The current configuration hard-codes `church.operation.mooo.com`, which couples the shared application source to one installation.

Vite supports adding allowed hosts at runtime through `__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS`. This is preferable to generating source files or allowing every host.

## Configuration Design

Introduce `CHURCH_APP_HOSTNAME` as the deployment-facing setting. Each server records its exact public hostname in its uncommitted `.env` file:

```env
CHURCH_APP_HOSTNAME=church.operation.mooo.com
PASSWORD_RESET_FRONTEND_BASE_URL=https://church.operation.mooo.com
```

`docker-compose.yml` passes the hostname to the frontend container as Vite's supported runtime variable:

```yaml
frontend:
  environment:
    __VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS: ${CHURCH_APP_HOSTNAME:-}
```

The empty default preserves local development, where Vite already accepts `localhost` and IP addresses. `vite.config.ts` will no longer contain any church-specific hostname.

An installation needing more than one public hostname may set a comma-separated value, which Vite supports:

```env
CHURCH_APP_HOSTNAME=church.example.org,www.church.example.org
```

## Deployment Flow

For a new installation:

1. Point the selected DNS hostname to the church server.
2. Set `CHURCH_APP_HOSTNAME` in the server's `.env` file.
3. Set `PASSWORD_RESET_FRONTEND_BASE_URL` to the corresponding HTTPS URL.
4. Configure the same hostname in Nginx's `server_name` and provision its HTTPS certificate.
5. Start or recreate the frontend service with Docker Compose.

Changing a hostname requires updating the server configuration and recreating the affected containers; it does not require a source-code change. The frontend service reads the allowed hostname when it starts. The backend must also receive the updated password-reset base URL.

## Security

Only exact hostnames controlled by the church are allowed. The design will not set `server.allowedHosts` to `true`, because doing so permits DNS-rebinding attacks. It will not allow `.mooo.com`, because `mooo.com` is a shared domain and unrelated users can control other subdomains beneath it.

The `.env` file remains uncommitted. `.env.example` documents the setting without containing a real church hostname.

## Error Handling

- If the hostname is absent, local access through `localhost` or an IP address continues to work, but arbitrary DNS hostnames remain rejected.
- If the configured hostname differs from the browser hostname, Vite returns its standard blocked-host response. The deployment guide will identify `CHURCH_APP_HOSTNAME` as the correction point.
- Nginx and certificate configuration remain separate requirements. Allowing a host in Vite does not create DNS records or provision HTTPS.

## Verification

Verification will cover:

- A request carrying a configured custom `Host` header returns the frontend instead of HTTP 403.
- An unrelated hostname remains blocked.
- Localhost access remains available with no configured custom hostname.
- Docker Compose resolves `CHURCH_APP_HOSTNAME` into the frontend container environment.
- The frontend test suite and production build continue to pass.

## Scope

This change makes the existing Vite-based frontend deployment configurable. Replacing the Vite development server with a production static-file server is a separate deployment-hardening project.

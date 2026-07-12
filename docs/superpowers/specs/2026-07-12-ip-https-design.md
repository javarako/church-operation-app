# IP-Based HTTPS Design

## Purpose

Provide browser-trusted HTTPS for the AWS Lightsail test deployment at `16.54.60.43` without purchasing or registering a domain.

## Certificate

- Certificate authority: Let's Encrypt
- Certificate subject: public IPv4 address `16.54.60.43`
- Profile: short-lived
- Lifetime: approximately 160 hours
- ACME client: Certbot 5.4 or newer with IP-address and webroot support
- Registration email: configured church contact address
- Renewal: automated and checked multiple times per day

The certificate and private key remain on the Lightsail instance under `/etc/letsencrypt`. Certificate material is never committed to Git or copied into project documentation.

## Request Flow

Nginx runs on the Ubuntu host and listens on public ports `80` and `443`.

- Port `80` serves `/.well-known/acme-challenge/` for certificate validation and redirects all other requests to the equivalent HTTPS IP URL.
- Port `443` terminates TLS and proxies requests to the existing Vue/Vite container on `127.0.0.1:5173`.
- Vite continues proxying `/api`, `/actuator`, and `/branding` to Spring Boot through the private Docker network.
- Spring Boot remains on host port `8080`, and MongoDB remains on host port `27017`; neither port is allowed by the Lightsail firewall.

## Renewal

A systemd timer runs Certbot renewal checks every 12 hours. Successful renewal triggers an Nginx configuration test and reload. Because IP certificates are short-lived, monitoring verifies both the timer and the current certificate expiration date.

## Firewall

Lightsail IPv4 and IPv6 firewalls allow TCP `80` and TCP `443`. The temporary TCP `5173` rules are removed only after external HTTPS verification passes. TCP `8080` and TCP `27017` remain absent.

## Application Configuration

`PASSWORD_RESET_FRONTEND_BASE_URL` changes from `http://16.54.60.43:5173` to `https://16.54.60.43`. The Compose stack is recreated after this environment update so password-reset emails use the HTTPS address.

## Verification

- `https://16.54.60.43` returns HTTP `200` with a publicly trusted certificate for the IP address.
- `http://16.54.60.43` redirects to `https://16.54.60.43`.
- Login succeeds and the bootstrap administrator still requires a password change.
- Church information, logo, and banner load through HTTPS.
- Certbot renewal dry run succeeds.
- The systemd renewal timer is enabled and scheduled.
- Public ports `5173`, `8080`, and `27017` are blocked.
- All three Docker Compose services remain running and backend health remains `UP`.

## Rollback

If certificate issuance or HTTPS verification fails, keep TCP `5173` available for the existing test-only HTTP deployment. Do not remove the working containers or MongoDB volume. Any partially enabled Nginx configuration is disabled only after its configuration test fails, preserving SSH access and application data.

## Safety Boundary

HTTPS protects data in transit but does not by itself make the development-container deployment production-ready. Real church data remains prohibited until the production deployment, backup, access-control, and security-review gates are complete.

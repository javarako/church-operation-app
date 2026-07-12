# AWS Lightsail Test Deployment Handoff

## Deployment Status

- Deployment date: 2026-07-12
- Environment: Testing only
- Application URL: `https://16.54.60.43`
- Transport security: Browser-trusted HTTPS; the development-container deployment remains test-only
- Deployed Git commit: `f0b4ff6`

## AWS Resources

- Service: Amazon Lightsail
- Region: Montreal, Canada (`ca-central-1`)
- Availability Zone: `ca-central-1a`
- Instance: `church-operation`
- Operating system: Ubuntu 24.04 LTS
- Plan: General purpose, dual-stack, 4 GB RAM, 2 vCPUs, 80 GB SSD
- Static IP resource: `church-operation-static-ip`
- Static IPv4 address: `16.54.60.43`
- Automatic snapshots: Disabled

## Installed Server Software

- Docker Engine: `29.6.1`
- Docker Compose: `5.3.1`
- Nginx: `1.24.0`
- Certbot: `5.6.0`
- Git: Ubuntu package
- Application directory: `/opt/church-operation-app`
- Runtime environment file: `/opt/church-operation-app/.env`, mode `0600`

## Running Services

- `mongo`: MongoDB 6 with persistent volume `church-operation-app_mongo-data`
- `backend`: Spring Boot on host port `8080`
- `frontend`: Vue/Vite on host port `5173`

The Vite server proxies `/api`, `/actuator`, and `/branding` requests to the backend over the Docker network.
Host Nginx terminates TLS on port `443`, redirects port `80` to HTTPS, and proxies requests privately to Vite on `127.0.0.1:5173`.

## HTTPS Certificate

- Certificate authority: Let's Encrypt
- Certificate subject alternative name: `IP Address:16.54.60.43`
- Certificate profile: `shortlived`
- Current expiration: 2026-07-19 11:17:42 UTC
- Certificate path: `/etc/letsencrypt/live/16.54.60.43/fullchain.pem`
- Private-key path: `/etc/letsencrypt/live/16.54.60.43/privkey.pem`
- Renewal timer: `snap.certbot.renew.timer`, enabled
- Deploy hook: `/etc/letsencrypt/renewal-hooks/deploy/reload-nginx`

IP certificates are valid for approximately 160 hours. Certbot checks for renewal automatically and validates Nginx before reloading it after a successful renewal.

## Lightsail Firewall

IPv4 and IPv6 firewall rules allow:

- TCP `22`: SSH
- TCP `80`: ACME HTTP-01 validation and redirect to HTTPS
- TCP `443`: HTTPS application access

The Lightsail firewall does not allow public TCP `5173`, TCP `8080`, or TCP `27017`. External connection checks confirmed all three ports are blocked.

## Verification Evidence

- Backend actuator health returned `UP`.
- HTTPS returned `200` with a trusted Let's Encrypt certificate containing `IP Address:16.54.60.43`.
- HTTP returned `301` and redirected to `https://16.54.60.43/`.
- Church information, actuator health, banner, and church logo returned `200` through HTTPS.
- Certbot renewal dry run completed successfully.
- The Certbot renewal timer is enabled and scheduled.
- All three Docker Compose services remained running after a stack restart.
- MongoDB volume `church-operation-app_mongo-data` remained present after restart.
- Recent Compose logs contained no repeated startup or database connection failures.
- Default test administrator authentication succeeded.
- The administrator response required a first-login password change.
- Church information returned the configured name, address, contact information, treasurer, and list page size.
- The temporary verification session was logged out immediately after testing.

## Operating Commands

Connect:

```bash
ssh -i ~/.ssh/church-operation-oci ubuntu@16.54.60.43
```

Check HTTPS and certificate renewal:

```bash
curl --head https://16.54.60.43/
sudo certbot certificates
systemctl list-timers --all | grep snap.certbot.renew
sudo certbot renew --dry-run
```

Check services:

```bash
cd /opt/church-operation-app
docker compose ps
docker compose logs --since=10m --no-color
```

Restart services without deleting data:

```bash
cd /opt/church-operation-app
docker compose restart
```

Update from the public GitHub repository:

```bash
cd /opt/church-operation-app
git pull --ff-only origin main
docker compose build
docker compose up -d
```

Do not run `docker compose down -v`; the `-v` option deletes the MongoDB volume.

## Production Gate

Before entering real member, offering, financial, or evidence-file data:

1. Replace the Vite development server with a production frontend image and Nginx.
2. Obtain a church-controlled domain or approved secure hostname for a stable production identity.
3. Stop publishing backend and MongoDB ports at the Docker host level.
4. Restrict SSH access and establish an operator access policy.
5. Enable encrypted backups and complete a documented restore test.
6. Complete a security and privacy review before importing real data.

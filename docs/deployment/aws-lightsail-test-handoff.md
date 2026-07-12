# AWS Lightsail Test Deployment Handoff

## Deployment Status

- Deployment date: 2026-07-12
- Environment: Testing only
- Application URL: `http://16.54.60.43:5173`
- Transport security: Plain HTTP; do not use real church data
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
- Git: Ubuntu package
- Application directory: `/opt/church-operation-app`
- Runtime environment file: `/opt/church-operation-app/.env`, mode `0600`

## Running Services

- `mongo`: MongoDB 6 with persistent volume `church-operation-app_mongo-data`
- `backend`: Spring Boot on host port `8080`
- `frontend`: Vue/Vite on host port `5173`

The Vite server proxies `/api`, `/actuator`, and `/branding` requests to the backend over the Docker network.

## Lightsail Firewall

IPv4 and IPv6 firewall rules allow:

- TCP `22`: SSH
- TCP `80`: Default HTTP rule; the test app does not currently use it
- TCP `5173`: Vue/Vite test access

The Lightsail firewall does not allow public TCP `8080` or TCP `27017`. External connection checks confirmed both ports are blocked.

## Verification Evidence

- Backend actuator health returned `UP`.
- Frontend returned HTTP `200` locally and through the static public IP.
- All three Docker Compose services remained running after a stack restart.
- MongoDB volume `church-operation-app_mongo-data` remained present after restart.
- Recent Compose logs contained no repeated startup or database connection failures.
- Default test administrator authentication succeeded.
- The administrator response required a first-login password change.
- Church information returned the configured name, address, contact information, treasurer, and list page size.
- Banner and church-logo resources returned HTTP `200`.
- The temporary verification session was logged out immediately after testing.

## Operating Commands

Connect:

```bash
ssh -i ~/.ssh/church-operation-oci ubuntu@16.54.60.43
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
2. Obtain a church-controlled domain or approved secure hostname.
3. Enable HTTPS and redirect all HTTP traffic to HTTPS.
4. Remove public port `5173` and serve only through ports `80` and `443`.
5. Stop publishing backend and MongoDB ports at the Docker host level.
6. Restrict SSH access and establish an operator access policy.
7. Enable encrypted backups and complete a documented restore test.
8. Complete a security and privacy review before importing real data.

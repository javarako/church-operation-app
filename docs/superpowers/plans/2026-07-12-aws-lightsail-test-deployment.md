# AWS Lightsail Test Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run the existing Church Operations development containers on the AWS Lightsail test instance and verify remote sample-data access without exposing MongoDB or Spring Boot publicly.

**Architecture:** One Ubuntu 24.04 Lightsail instance runs the repository's existing Docker Compose stack. Vue's Vite server is the only application port reachable through the Lightsail firewall; it proxies API, branding, and actuator requests to Spring Boot over the Docker network, while MongoDB persists in a named Docker volume.

**Tech Stack:** AWS Lightsail, Ubuntu 24.04 LTS, Docker Engine, Docker Compose plugin, Git, MongoDB 6, Java 21, Spring Boot 4, Node.js 22, Vue 3, Vite

## Global Constraints

- This is a testing-only HTTP deployment at `16.54.60.43`; do not enter real church data.
- AWS Region is Montreal, Canada (`ca-central-1`).
- Instance name is `church-operation`; static IP resource is `church-operation-static-ip`.
- Keep automatic Lightsail snapshots disabled during the test stage.
- Never print, commit, or include `.env` secret values in documentation.
- Do not expose TCP `27017` or TCP `8080` through the Lightsail firewall.
- Preserve the `mongo-data` Docker volume when containers are rebuilt.

---

### Task 1: Establish SSH Access

**Files:**
- Read: local AWS Lightsail regional private key downloaded from the instance Connect page
- Create: local `~/.ssh/church-operation-aws.pem` with mode `0600`

**Interfaces:**
- Consumes: Lightsail instance `church-operation`, static IP `16.54.60.43`, username `ubuntu`
- Produces: working non-interactive SSH access used by Tasks 2-5

- [ ] **Step 1: Download the Montreal default key**

Use the Lightsail instance **Connect** page and select **Download default key**. Move the downloaded key to `~/.ssh/church-operation-aws.pem` without opening or printing it.

- [ ] **Step 2: Restrict key permissions**

Run:

```bash
chmod 600 ~/.ssh/church-operation-aws.pem
```

Expected: command exits successfully with no output.

- [ ] **Step 3: Verify SSH identity**

Run:

```bash
ssh -o StrictHostKeyChecking=accept-new -i ~/.ssh/church-operation-aws.pem ubuntu@16.54.60.43 'printf "user=%s host=%s\n" "$(id -un)" "$(hostname)"'
```

Expected: output starts with `user=ubuntu host=`.

### Task 2: Install Server Prerequisites

**Files:**
- Modify: remote `/etc/apt/keyrings/docker.asc`
- Modify: remote `/etc/apt/sources.list.d/docker.sources`

**Interfaces:**
- Consumes: working SSH access from Task 1
- Produces: Docker Engine, Docker Compose plugin, Git, and an enabled Docker service

- [ ] **Step 1: Update Ubuntu packages**

Run remotely:

```bash
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get upgrade -y
```

Expected: both commands exit successfully.

- [ ] **Step 2: Install Docker's repository prerequisites**

Run remotely:

```bash
sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
```

Expected: commands exit successfully and `/etc/apt/keyrings/docker.asc` exists.

- [ ] **Step 3: Add the Docker repository**

Run remotely:

```bash
printf 'Types: deb\nURIs: https://download.docker.com/linux/ubuntu\nSuites: %s\nComponents: stable\nArchitectures: %s\nSigned-By: /etc/apt/keyrings/docker.asc\n' "$(. /etc/os-release && echo "$VERSION_CODENAME")" "$(dpkg --print-architecture)" | sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null
sudo apt-get update
```

Expected: Docker repository metadata downloads without signature errors.

- [ ] **Step 4: Install and enable Docker**

Run remotely:

```bash
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu
sudo systemctl enable --now docker
sudo docker version
sudo docker compose version
```

Expected: Docker client/server and Docker Compose version information are printed.

### Task 3: Transfer Application And Secrets

**Files:**
- Create: remote `/opt/church-operation-app`
- Create: remote `/opt/church-operation-app/.env` with mode `0600`
- Read: local `/Users/brad/Desktop/workspcace/church-operation-app/.env`

**Interfaces:**
- Consumes: GitHub repository `https://github.com/javarako/church-operation-app.git` and local ignored `.env`
- Produces: remote application checkout at commit `dd8e443` or a later approved `main` commit, plus protected runtime configuration

- [ ] **Step 1: Clone the repository**

Run remotely:

```bash
sudo git clone https://github.com/javarako/church-operation-app.git /opt/church-operation-app
sudo chown -R ubuntu:ubuntu /opt/church-operation-app
git -C /opt/church-operation-app checkout main
git -C /opt/church-operation-app pull --ff-only origin main
```

Expected: `/opt/church-operation-app/docker-compose.yml` exists and the checkout is on `main`.

- [ ] **Step 2: Transfer the ignored environment file**

Run locally without displaying file contents:

```bash
scp -i ~/.ssh/church-operation-aws.pem /Users/brad/Desktop/workspcace/church-operation-app/.env ubuntu@16.54.60.43:/opt/church-operation-app/.env
ssh -i ~/.ssh/church-operation-aws.pem ubuntu@16.54.60.43 'chmod 600 /opt/church-operation-app/.env'
```

Expected: both commands exit successfully.

- [ ] **Step 3: Set the test reset-password URL on the server**

Run remotely without displaying any secret values:

```bash
cd /opt/church-operation-app
sed -i '/^PASSWORD_RESET_FRONTEND_BASE_URL=/d' .env
printf '%s\n' 'PASSWORD_RESET_FRONTEND_BASE_URL=http://16.54.60.43:5173' >> .env
chmod 600 .env
grep -q '^PASSWORD_RESET_FRONTEND_BASE_URL=http://16.54.60.43:5173$' .env
```

Expected: all commands exit successfully with no output.

### Task 4: Start And Validate The Compose Stack

**Files:**
- Read: remote `/opt/church-operation-app/docker-compose.yml`
- Create: Docker named volume `church-operation-app_mongo-data`

**Interfaces:**
- Consumes: Docker installation and remote repository from Tasks 2-3
- Produces: running `mongo`, `backend`, and `frontend` containers

- [ ] **Step 1: Build all images**

Run remotely:

```bash
cd /opt/church-operation-app
docker compose build
```

Expected: frontend and backend images build successfully.

- [ ] **Step 2: Start services**

Run remotely:

```bash
cd /opt/church-operation-app
docker compose up -d
docker compose ps
```

Expected: `mongo`, `backend`, and `frontend` show running state.

- [ ] **Step 3: Verify backend health from the server**

Run remotely:

```bash
curl --fail --silent http://127.0.0.1:8080/actuator/health
```

Expected: JSON contains `"status":"UP"`.

- [ ] **Step 4: Verify frontend response from the server**

Run remotely:

```bash
curl --fail --silent --head http://127.0.0.1:5173/
```

Expected: response includes `HTTP/1.1 200 OK`.

### Task 5: Configure And Verify The Lightsail Firewall

**Files:**
- Modify: Lightsail IPv4 firewall for `church-operation`
- Modify: Lightsail IPv6 firewall for `church-operation`

**Interfaces:**
- Consumes: running frontend on TCP `5173`
- Produces: public testing access to the frontend while database and backend remain blocked

- [ ] **Step 1: Add frontend test rules**

In the instance **Networking** tab, add a custom TCP rule for port `5173` to the IPv4 firewall and the IPv6 firewall.

Expected: each firewall table lists TCP `5173`.

- [ ] **Step 2: Confirm forbidden public ports are absent**

Inspect both firewall tables.

Expected: neither TCP `27017` nor TCP `8080` is listed.

- [ ] **Step 3: Verify the remote login page**

Open:

```text
http://16.54.60.43:5173
```

Expected: the branded Church Operations login page renders without blank areas or failed assets.

- [ ] **Step 4: Verify public backend and MongoDB ports are blocked**

Run locally:

```bash
nc -z -w 5 16.54.60.43 8080; test $? -ne 0
nc -z -w 5 16.54.60.43 27017; test $? -ne 0
```

Expected: both combined commands exit successfully because the connection attempts fail.

### Task 6: Functional And Persistence Checks

**Files:**
- Read: runtime application through `http://16.54.60.43:5173`
- Read: remote Docker logs

**Interfaces:**
- Consumes: reachable application from Task 5
- Produces: deployment acceptance evidence and a documented test-only handoff

- [ ] **Step 1: Complete the initial login flow**

Sign in using the configured default administrator and verify the mandatory first-login password-change screen appears. Use only test credentials.

Expected: password change succeeds and the dashboard loads.

- [ ] **Step 2: Verify branding and sample records**

Check the church logo, dashboard banner, church contact information, and sample reference/member records.

Expected: assets render and sample data loads without page-level errors.

- [ ] **Step 3: Verify MongoDB persistence**

Run remotely:

```bash
cd /opt/church-operation-app
docker compose restart
docker compose ps
docker volume inspect church-operation-app_mongo-data
```

Expected: all services return to running state and the named volume remains present.

- [ ] **Step 4: Check recent service logs**

Run remotely:

```bash
cd /opt/church-operation-app
docker compose logs --since=10m --no-color
```

Expected: no repeated startup failures, database connection failures, or unhandled HTTP 500 errors.

- [ ] **Step 5: Record the test-only handoff**

Document the URL, AWS resource names, deployed Git commit, firewall ports, verification date, and the prohibition on real data. Record secret names only, never secret values.

Expected: the future deployment instruction has enough evidence to reproduce and audit this deployment.

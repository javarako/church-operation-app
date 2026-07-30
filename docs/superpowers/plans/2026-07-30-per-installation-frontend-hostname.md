# Per-Installation Frontend Hostname Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make each church's public frontend hostname a server-side environment setting so deployments never require church-specific edits to `vite.config.ts`.

**Architecture:** Docker Compose maps a friendly `CHURCH_APP_HOSTNAME` deployment value to Vite's supported `__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS` runtime variable. Vite retains its default localhost and IP allowances, while exact custom hostnames are configured independently on each server. The shared source, Docker image definition, and Vite configuration remain installation-neutral.

**Tech Stack:** Docker Compose, Vite 6, Vue 3, TypeScript, Nginx deployment documentation

## Global Constraints

- Each installation uses an exact hostname controlled by that church.
- Multiple exact hostnames may be comma-separated.
- Do not set `server.allowedHosts` to `true`.
- Do not allow a shared parent such as `.mooo.com`.
- Preserve Vite's default localhost and IP-address access.
- Keep `.env` uncommitted; document settings in `.env.example`.
- DNS records, Nginx `server_name`, and HTTPS certificates remain deployment responsibilities outside Vite.
- Replacing Vite with a production static-file server is out of scope.

---

### Task 1: Runtime Frontend Hostname Configuration

**Files:**
- Modify: `frontend/vite.config.ts:4-13`
- Modify: `docker-compose.yml:43-50`
- Modify: `.env.example:10-17`

**Interfaces:**
- Consumes: `CHURCH_APP_HOSTNAME`, an optional empty, single-hostname, or comma-separated hostname value from the deployment `.env` file.
- Produces: `__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS` in the frontend container environment, consumed directly by Vite 6.

- [ ] **Step 1: Demonstrate that an arbitrary installation hostname is currently rejected**

Start the existing frontend from `frontend/` in one terminal:

```bash
npm run dev -- --port 4179
```

From another terminal, send a request with a hostname that is not the currently hard-coded church hostname:

```bash
curl --silent --output /tmp/church-host-before.txt --write-out '%{http_code}\n' \
  --header 'Host: church-a.example.test' \
  http://127.0.0.1:4179/
sed -n '1,5p' /tmp/church-host-before.txt
```

Expected: HTTP `403` and Vite's blocked-host response naming `church-a.example.test`.

- [ ] **Step 2: Demonstrate that Compose does not currently pass a hostname to Vite**

Run:

```bash
CHURCH_APP_HOSTNAME=church-a.example.test docker compose config | \
  rg '__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS|church-a\.example\.test'
```

Expected: no matching output and a non-zero `rg` exit status.

- [ ] **Step 3: Implement the deployment-neutral runtime mapping**

Remove the installation-specific line from `frontend/vite.config.ts`, leaving the proxy configuration unchanged:

```ts
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://backend:8080',
      '/actuator': 'http://backend:8080',
      '/branding': 'http://backend:8080'
    }
  },
  test: {
    environment: 'jsdom'
  }
});
```

Add the runtime mapping to the `frontend` service in `docker-compose.yml`:

```yaml
  frontend:
    build:
      context: ./frontend
    restart: unless-stopped
    environment:
      __VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS: ${CHURCH_APP_HOSTNAME:-}
    ports:
      - "5173:5173"
    depends_on:
      - backend
```

Add the public hostname setting under `# Application behavior` in `.env.example`:

```env
# Exact public DNS hostname. Separate multiple hostnames with commas.
CHURCH_APP_HOSTNAME=
```

- [ ] **Step 4: Verify the Compose environment mapping**

Run:

```bash
CHURCH_APP_HOSTNAME=church-a.example.test docker compose config | \
  rg '__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS|church-a\.example\.test'
```

Expected: the resolved frontend environment contains `__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS: church-a.example.test`.

Also run without a configured hostname:

```bash
CHURCH_APP_HOSTNAME= docker compose config --quiet
```

Expected: exit status `0`.

- [ ] **Step 5: Verify configured-host, unrelated-host, and IP behavior**

Stop the server from Step 1. Start it again from `frontend/` with Vite's runtime variable:

```bash
__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS=church-a.example.test \
  npm run dev -- --port 4179
```

From another terminal, run:

```bash
curl --silent --output /dev/null --write-out '%{http_code}\n' \
  --header 'Host: church-a.example.test' http://127.0.0.1:4179/
curl --silent --output /dev/null --write-out '%{http_code}\n' \
  --header 'Host: unrelated.example.test' http://127.0.0.1:4179/
curl --silent --output /dev/null --write-out '%{http_code}\n' \
  --header 'Host: 127.0.0.1' http://127.0.0.1:4179/
```

Expected status codes, in order: `200`, `403`, `200`. Stop the temporary Vite server after verification.

- [ ] **Step 6: Run the frontend regression suite and build**

Run from `frontend/`:

```bash
npm test
npm run build
```

Expected: all Vitest tests pass, TypeScript validation succeeds, and Vite completes the production build.

- [ ] **Step 7: Commit the runtime configuration**

```bash
git add .env.example docker-compose.yml frontend/vite.config.ts
git commit -m "feat: configure frontend hostname per deployment"
```

---

### Task 2: Operator Documentation

**Files:**
- Modify: `README.md:23-39`
- Modify: `docs/deployment/aws-lightsail-fresh-install.md:193-221`
- Modify: `docs/deployment/aws-lightsail-fresh-install.md:475-525`

**Interfaces:**
- Consumes: `CHURCH_APP_HOSTNAME` and `PASSWORD_RESET_FRONTEND_BASE_URL` established in Task 1.
- Produces: Installation and troubleshooting instructions for setting, changing, and verifying a church hostname.

- [ ] **Step 1: Document the setting in the project README**

After the `.env` paragraph in `README.md`, add:

```markdown
For a DNS-based deployment, set `CHURCH_APP_HOSTNAME` to the exact public
hostname. Separate multiple exact hostnames with commas. Vite continues to
accept localhost and IP-address requests automatically. Keep
`PASSWORD_RESET_FRONTEND_BASE_URL` synchronized with the preferred public
HTTPS URL.
```

- [ ] **Step 2: Add the hostname to the Lightsail configuration checklist**

Add the hostname setting to the important `.env` example in `docs/deployment/aws-lightsail-fresh-install.md`:

```env
CHURCH_APP_HOSTNAME=YOUR_DNS_HOSTNAME
PASSWORD_RESET_FRONTEND_BASE_URL=https://YOUR_DNS_HOSTNAME
```

Immediately after that example, explain that IP-only deployments may leave `CHURCH_APP_HOSTNAME` empty because Vite permits IP hosts by default. State that DNS deployments must also use the hostname in Nginx `server_name` and provision an HTTPS certificate valid for that hostname.

- [ ] **Step 3: Add blocked-host troubleshooting instructions**

Under `## 14. Troubleshooting`, add this section before the MongoDB section:

````markdown
### Vite Blocks A Custom DNS Hostname

If the browser reports that the host is not allowed, set the exact hostname in
the server's uncommitted `.env` file:

```env
CHURCH_APP_HOSTNAME=church.example.org
PASSWORD_RESET_FRONTEND_BASE_URL=https://church.example.org
```

Recreate the affected services and verify the value received by Vite:

```bash
cd /opt/church-operation-app
docker compose up -d --force-recreate frontend backend
docker compose exec frontend printenv __VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS
CHURCH_HOST_TO_TEST=church.example.org
curl --head --header "Host: $CHURCH_HOST_TO_TEST" http://127.0.0.1:5173
```

The printed value must exactly match the browser hostname, and the local
request must return a successful HTTP status. Do not use `allowedHosts: true`
or allow a shared parent domain such as `.mooo.com`.
````

- [ ] **Step 4: Review documentation for consistency and formatting**

Run:

```bash
rg -n 'CHURCH_APP_HOSTNAME|__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS|allowedHosts' \
  .env.example README.md docker-compose.yml frontend/vite.config.ts \
  docs/deployment/aws-lightsail-fresh-install.md
git diff --check
```

Expected: every deployment-facing reference uses `CHURCH_APP_HOSTNAME`; only the Compose mapping and troubleshooting inspection use Vite's internal variable; no church-specific hostname remains in `vite.config.ts`; and `git diff --check` exits `0`.

- [ ] **Step 5: Run final configuration and frontend verification**

Run:

```bash
CHURCH_APP_HOSTNAME=church-a.example.test docker compose config --quiet
cd frontend
npm test
npm run build
```

Expected: Compose validation succeeds, all Vitest tests pass, and the frontend production build completes.

- [ ] **Step 6: Commit the operator documentation**

```bash
git add README.md docs/deployment/aws-lightsail-fresh-install.md
git commit -m "docs: explain per-installation frontend hostnames"
```

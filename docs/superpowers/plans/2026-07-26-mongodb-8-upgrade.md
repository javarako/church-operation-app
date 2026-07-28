# MongoDB 8 Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move fresh deployments and verified existing deployments to MongoDB 8.0.28 without risking a direct unsupported upgrade from MongoDB 6.

**Architecture:** Pin Docker and Testcontainers to MongoDB 8.0.28 while allowing an explicit `MONGO_IMAGE` override for the required 7.0.39 intermediate stage. Keep FCV changes manual and documented. Validate fresh startup separately from a staged copy of existing data before touching the primary local or hosted volume.

**Tech Stack:** MongoDB 6.0/7.0.39/8.0.28, Docker Compose, mongosh, mongodump/mongorestore, Spring Data MongoDB, Testcontainers, Java 21.

## Global Constraints

- Never start MongoDB 8 directly against a MongoDB 6 data directory.
- Existing data must move through MongoDB 7.0.39 and FCV 7.0 before MongoDB 8.0.28.
- Create and verify both an application full backup and an infrastructure-level dump/snapshot before changing binaries.
- Do not automatically change FCV in application startup, Docker entrypoints, or migration runners.
- Keep FCV 7.0 during the initial MongoDB 8 burn-in period.
- Pin exact image versions: intermediate `mongo:7.0.39`, final `mongo:8.0.28`.
- Preserve MongoDB GridFS files, indexes, views, and all application collections.
- Run migration rehearsal against a copied volume or dump before the primary local/AWS deployment.
- Obtain explicit user confirmation immediately before changing an existing database volume.
- Preserve unrelated dirty-worktree changes.

---

## File Structure

- Modify `docker-compose.yml`: final pinned image with explicit intermediate override support.
- Modify `backend/src/test/java/com/church/operation/V1WorkflowIntegrationTest.java`.
- Modify `backend/src/test/java/com/church/operation/service/FiscalArchiveRoundTripIntegrationTest.java`.
- Modify `backend/src/test/java/com/church/operation/service/MongoDatabaseRoundTripIntegrationTest.java`.
- Create `docs/deployment/mongodb-8-upgrade.md`: exact staged local/AWS runbook and rollback boundaries.
- Modify `.env.example`: document optional migration-only `MONGO_IMAGE` override.
- Modify deployment documentation that still states MongoDB 6 or 7.

---

### Task 1: Prove Application Compatibility With MongoDB 8

**Files:**
- Modify: `backend/src/test/java/com/church/operation/V1WorkflowIntegrationTest.java`
- Modify: `backend/src/test/java/com/church/operation/service/FiscalArchiveRoundTripIntegrationTest.java`
- Modify: `backend/src/test/java/com/church/operation/service/MongoDatabaseRoundTripIntegrationTest.java`

**Interfaces:**
- Consumes: official `mongo:8.0.28` Docker image.
- Produces: workflow, fiscal archive, and raw backup/restore compatibility evidence on MongoDB 8.

- [ ] **Step 1: Change integration-test image constants to MongoDB 8.0.28**

```java
private static final DockerImageName MONGO_IMAGE = DockerImageName.parse("mongo:8.0.28");
static final MongoDBContainer MONGODB = new MongoDBContainer(MONGO_IMAGE);
```

Apply the same exact tag in all three integration classes; do not leave mixed MongoDB test versions.

- [ ] **Step 2: Run each Docker-backed test independently**

Run: `cd backend && mvn -Dtest=V1WorkflowIntegrationTest test`

Run: `cd backend && mvn -Dtest=FiscalArchiveRoundTripIntegrationTest test`

Run: `cd backend && mvn -Dtest=MongoDatabaseRoundTripIntegrationTest test`

Expected: each test PASS. If Docker is unavailable, stop this task and report that MongoDB 8 compatibility is unverified; do not continue to change the deployment image.

- [ ] **Step 3: Run the complete backend suite on MongoDB 8**

Run: `cd backend && mvn test`

Expected: all backend tests PASS.

- [ ] **Step 4: Commit MongoDB 8 test coverage**

```bash
git add backend/src/test/java/com/church/operation/V1WorkflowIntegrationTest.java backend/src/test/java/com/church/operation/service/FiscalArchiveRoundTripIntegrationTest.java backend/src/test/java/com/church/operation/service/MongoDatabaseRoundTripIntegrationTest.java
git commit -m "test: verify MongoDB 8 compatibility"
```

---

### Task 2: Add A Reproducible Staged Upgrade Runbook

**Files:**
- Create: `docs/deployment/mongodb-8-upgrade.md`
- Modify: `.env.example`

**Interfaces:**
- Consumes: current Compose project, MongoDB volume, encrypted application backup, mongodump archive.
- Produces: exact preflight, 6-to-7, FCV 7, 7-to-8, burn-in, FCV 8, rollback, and AWS steps.

- [ ] **Step 1: Document preflight and backup commands**

The runbook must start with application write shutdown and both backup layers:

```bash
docker compose stop frontend backend
docker compose exec mongo mongosh --quiet --eval 'db.adminCommand({getParameter:1,featureCompatibilityVersion:1})'
docker compose exec mongo sh -c 'mongodump --db church_operations --archive=/tmp/church-before-mongo8.archive --gzip'
docker cp church-operation-app-mongo-1:/tmp/church-before-mongo8.archive ./church-before-mongo8.archive
```

Require the encrypted full backup from System Administration before stopping writes. Record `docker compose ps`, `docker compose exec mongo mongod --version`, dump size, checksum, FCV, and provider snapshot identifier.

- [ ] **Step 2: Document the MongoDB 7.0.39 intermediate stage**

```bash
MONGO_IMAGE=mongo:7.0.39 docker compose up -d mongo
docker compose exec mongo mongosh --quiet --eval 'db.adminCommand({ping:1})'
docker compose up -d backend frontend
docker compose exec mongo mongosh --quiet --eval 'db.adminCommand({setFeatureCompatibilityVersion:"7.0",confirm:true})'
docker compose exec mongo mongosh --quiet --eval 'db.adminCommand({getParameter:1,featureCompatibilityVersion:1})'
```

Require login, dashboard, member image, offering, finance, PDF, Excel, and full backup/restore smoke checks before proceeding.

- [ ] **Step 3: Document the MongoDB 8.0.28 stage and burn-in**

```bash
docker compose stop frontend backend mongo
MONGO_IMAGE=mongo:8.0.28 docker compose up -d mongo
docker compose exec mongo mongosh --quiet --eval 'db.adminCommand({ping:1})'
docker compose up -d backend frontend
docker compose exec mongo mongosh --quiet --eval 'db.adminCommand({getParameter:1,featureCompatibilityVersion:1})'
```

Keep FCV at `7.0` during burn-in. After explicit approval:

```bash
docker compose exec mongo mongosh --quiet --eval 'db.adminCommand({setFeatureCompatibilityVersion:"8.0",confirm:true})'
```

- [ ] **Step 4: Document rollback boundaries**

Before FCV 8.0, rollback means stop services and return to `mongo:7.0.39` using the same volume. After FCV 8.0, do not attempt binary rollback without first following MongoDB's official downgrade requirements; restore the verified pre-upgrade dump/snapshot when a clean rollback is required.

- [ ] **Step 5: Document AWS Lightsail execution**

Require a Lightsail snapshot, adequate free disk for the dump plus data files, the same staged tags, command/log capture, and post-upgrade HTTPS smoke tests. Do not put database port 27017 on the public firewall.

- [ ] **Step 6: Add the optional image override to `.env.example`**

```env
# Migration override only. Leave blank for the pinned Compose default.
MONGO_IMAGE=
```

- [ ] **Step 7: Validate all runbook commands against Compose configuration**

Run: `docker compose config --quiet`

Run: `MONGO_IMAGE=mongo:7.0.39 docker compose config | grep 'image: mongo:7.0.39'`

Expected: both commands succeed and the override resolves to the exact intermediate tag.

- [ ] **Step 8: Commit the upgrade runbook**

```bash
git add .env.example docs/deployment/mongodb-8-upgrade.md
git commit -m "docs: add MongoDB 8 upgrade runbook"
```

---

### Task 3: Pin Fresh Deployments To MongoDB 8.0.28

**Files:**
- Modify: `docker-compose.yml`
- Modify: deployment documentation containing MongoDB version requirements.

**Interfaces:**
- Consumes: `MONGO_IMAGE` environment override.
- Produces: `${MONGO_IMAGE:-mongo:8.0.28}` as the final default image.

- [ ] **Step 1: Write a Compose configuration regression check**

Run before editing: `docker compose config | grep 'image: mongo:6'`

Expected: PASS, proving the current default is MongoDB 6 and the deployment change is not already present.

- [ ] **Step 2: Change only the MongoDB image declaration**

```yaml
services:
  mongo:
    image: ${MONGO_IMAGE:-mongo:8.0.28}
```

Do not change the existing volume name or mount path.

- [ ] **Step 3: Update deployment requirements to MongoDB 8.0.28**

State that MongoDB 6 volumes must follow `docs/deployment/mongodb-8-upgrade.md`; never suggest running the final Compose default directly against an unmigrated 6.0 volume.

- [ ] **Step 4: Validate final and intermediate Compose resolution**

Run: `docker compose config | grep 'image: mongo:8.0.28'`

Run: `MONGO_IMAGE=mongo:7.0.39 docker compose config | grep 'image: mongo:7.0.39'`

Expected: both exact image checks PASS.

- [ ] **Step 5: Commit the deployment pin**

```bash
git add docker-compose.yml docs/deployment
git commit -m "build: pin MongoDB 8 deployment"
```

---

### Task 4: Verify A Fresh MongoDB 8 Installation

**Files:**
- No source changes unless verification identifies a compatibility defect.

**Interfaces:**
- Consumes: a new isolated Compose project and volume.
- Produces: fresh-install evidence without touching existing `mongo-data`.

- [ ] **Step 1: Start an isolated project with a new volume**

```bash
docker compose stop frontend backend mongo
COMPOSE_PROJECT_NAME=church-mongo8-fresh docker compose up --build -d
```

Stopping does not remove the primary containers or volume. The isolated project
creates `church-mongo8-fresh_mongo-data` and cannot reuse the primary project's
volume. Stopping the primary project first avoids collisions on ports 27017,
8080, and 5173.

- [ ] **Step 2: Confirm binary version, FCV, and health**

```bash
COMPOSE_PROJECT_NAME=church-mongo8-fresh docker compose exec mongo mongod --version
COMPOSE_PROJECT_NAME=church-mongo8-fresh docker compose exec mongo mongosh --quiet --eval 'db.adminCommand({getParameter:1,featureCompatibilityVersion:1})'
COMPOSE_PROJECT_NAME=church-mongo8-fresh docker compose ps
```

Expected: MongoDB 8.0.28, FCV 8.0 for the fresh database, and all containers running.

- [ ] **Step 3: Perform fresh-install smoke tests**

Login with the bootstrap ADMIN, change the forced password, create one member, offering, expense, and budget, upload one member image and church logo/banner, generate one PDF and Excel report, then run full backup and restore.

- [ ] **Step 4: Stop and remove only the isolated project**

```bash
COMPOSE_PROJECT_NAME=church-mongo8-fresh docker compose down -v
docker compose up -d
```

Confirm the primary Compose project and `mongo-data` volume were not modified.

- [ ] **Step 5: Record verification results in the upgrade runbook**

Add a dated verification table with binary version, FCV, backend version, test result, and backup/restore result. Commit only the documentation update.

```bash
git add docs/deployment/mongodb-8-upgrade.md
git commit -m "docs: record fresh MongoDB 8 verification"
```

---

### Task 5: Rehearse The Existing-Data Upgrade On A Copy

**Files:**
- No source changes unless rehearsal identifies a compatibility defect.

**Interfaces:**
- Consumes: verified pre-upgrade `mongodump` archive.
- Produces: staged 6-to-7-to-8 rehearsal evidence using disposable volumes.

- [ ] **Step 1: Restore the dump into an isolated MongoDB 6 project**

Create an override file in `/tmp/church-mongo6-rehearsal.yml` that uses `mongo:6.0.26` and a rehearsal volume. Restore with:

```bash
docker cp ./church-before-mongo8.archive church-mongo8-rehearsal-mongo-1:/tmp/source.archive
docker exec church-mongo8-rehearsal-mongo-1 sh -c 'mongorestore --archive=/tmp/source.archive --gzip --drop'
```

- [ ] **Step 2: Follow the runbook through 7.0.39 and FCV 7.0**

Capture container logs, binary version, FCV, collection counts, GridFS file counts, and application smoke-test results.

- [ ] **Step 3: Follow the runbook through 8.0.28 while retaining FCV 7.0**

Run the full backend integration suite and frontend smoke tests against the rehearsal instance. Generate and restore a full application backup.

- [ ] **Step 4: Set FCV 8.0 in rehearsal and repeat critical checks**

Verify login, members, offerings, finance, reports, member images, church branding, archive/clean, and full backup/restore.

- [ ] **Step 5: Destroy only rehearsal resources and record results**

Do not alter the primary volume. Add observed durations, warnings, and exact rollback point to the runbook, then commit the documentation update.

---

### Task 6: Migrate The Primary Local Or AWS Deployment After Explicit Approval

**Files:**
- Operational task; no source edits expected.

**Interfaces:**
- Consumes: approved runbook, verified backups, successful rehearsal.
- Produces: primary deployment running MongoDB 8.0.28.

- [ ] **Step 1: Present the preflight evidence and request explicit approval**

Show backup filenames/checksums, provider snapshot, current binary/FCV, rehearsal result, expected downtime, and rollback procedure. Do not continue until the user explicitly approves this migration.

- [ ] **Step 2: Execute the 6-to-7 stage exactly from the runbook**

Stop writes, use `mongo:7.0.39`, verify data and application, then set FCV 7.0.

- [ ] **Step 3: Execute the 7-to-8 stage exactly from the runbook**

Use `mongo:8.0.28`, keep FCV 7.0, and complete the full post-upgrade smoke checklist.

- [ ] **Step 4: Complete burn-in before FCV 8.0**

Review backend and MongoDB logs, disk usage, backups, and church workflows for the agreed burn-in period. Set FCV 8.0 only after separate explicit approval.

- [ ] **Step 5: Record the deployment result**

Document date/time, previous and final versions, FCV, backup identifiers, downtime, verification results, warnings, and responsible administrator. Do not record credentials.

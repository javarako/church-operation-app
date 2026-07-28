# MongoDB 8 Staged Upgrade

This runbook upgrades the standalone MongoDB used by Church Operations from
MongoDB 6 to 7.0.39 and then 8.0.28. Never start MongoDB 8 against a MongoDB 6
data directory. Run every command from the repository root.

The official MongoDB standalone upgrade guides require successive major-version
upgrades. MongoDB 8 requires a MongoDB 7 deployment with feature compatibility
version (FCV) 7.0. Keep FCV 7.0 during the initial MongoDB 8 burn-in period.

## Preflight And Backups

1. Download an encrypted Full Backup from **System Administration > Full
   Backup** and verify that the ZIP opens with its backup password.
2. On AWS Lightsail, create an instance snapshot. Record its identifier.
3. Confirm there is enough free disk space for the database and dump:

   ```bash
   df -h
   docker system df
   ```

4. Stop application writes and record the current deployment:

   ```bash
   docker compose stop frontend backend
   docker compose ps
   docker compose exec mongo mongod --version
   docker compose exec mongo mongosh --quiet --eval \
     'db.adminCommand({getParameter:1,featureCompatibilityVersion:1})'
   ```

5. Create the infrastructure-level dump and copy it to the host:

   ```bash
   docker compose exec mongo sh -c \
     'mongodump --db church_operations --archive=/tmp/church-before-mongo8.archive --gzip'
   docker cp church-operation-app-mongo-1:/tmp/church-before-mongo8.archive \
     ./church-before-mongo8.archive
   ls -lh church-before-mongo8.archive
   shasum -a 256 church-before-mongo8.archive
   ```

6. Verify the dump by restoring it into a disposable rehearsal database before
   changing the primary volume. `mongorestore --dryRun` still requires a target
   database connection and is not a standalone archive-integrity check.

Record the application backup name, dump size and checksum, binary version,
FCV, free disk space, Compose status, and provider snapshot identifier.

## Stage 1: MongoDB 7.0.39

1. Stop MongoDB and start only the intermediate binary against the existing
   volume:

   ```bash
   docker compose stop mongo
   MONGO_IMAGE=mongo:7.0.39 docker compose up -d mongo
   MONGO_IMAGE=mongo:7.0.39 docker compose exec mongo mongosh --quiet --eval \
     'db.adminCommand({ping:1})'
   MONGO_IMAGE=mongo:7.0.39 docker compose exec mongo mongod --version
   ```

2. Start the application, confirm health, then set FCV 7.0:

   ```bash
   MONGO_IMAGE=mongo:7.0.39 docker compose up -d backend frontend
   curl -fsS http://localhost:8080/actuator/health
   MONGO_IMAGE=mongo:7.0.39 docker compose exec mongo mongosh --quiet --eval \
     'db.adminCommand({setFeatureCompatibilityVersion:"7.0",confirm:true})'
   MONGO_IMAGE=mongo:7.0.39 docker compose exec mongo mongosh --quiet --eval \
     'db.adminCommand({getParameter:1,featureCompatibilityVersion:1})'
   ```

3. Verify login, dashboard totals, a member image, offerings, finance, one PDF,
   one Excel report, and a new Full Backup. Review backend and MongoDB logs.

## Stage 2: MongoDB 8.0.28

1. Stop application writes and MongoDB, then start the MongoDB 8 binary:

   ```bash
   docker compose stop frontend backend mongo
   MONGO_IMAGE=mongo:8.0.28 docker compose up -d mongo
   MONGO_IMAGE=mongo:8.0.28 docker compose exec mongo mongosh --quiet --eval \
     'db.adminCommand({ping:1})'
   MONGO_IMAGE=mongo:8.0.28 docker compose exec mongo mongod --version
   MONGO_IMAGE=mongo:8.0.28 docker compose up -d backend frontend
   curl -fsS http://localhost:8080/actuator/health
   MONGO_IMAGE=mongo:8.0.28 docker compose exec mongo mongosh --quiet --eval \
     'db.adminCommand({getParameter:1,featureCompatibilityVersion:1})'
   ```

2. FCV must still report 7.0. Verify login, dashboard totals, members and member
   images, offerings, finance, budgets, references, reports, archives, branding,
   and Full Backup/Restore. Review logs and disk use.

3. Keep FCV 7.0 during burn-in. After a separate approval, enable MongoDB 8
   persisted features:

   ```bash
   docker compose exec mongo mongosh --quiet --eval \
     'db.adminCommand({setFeatureCompatibilityVersion:"8.0",confirm:true})'
   docker compose exec mongo mongosh --quiet --eval \
     'db.adminCommand({getParameter:1,featureCompatibilityVersion:1})'
   ```

## Rollback Boundaries

- Before setting FCV 7.0, stop services and return to the original MongoDB 6
  image and volume, or restore the verified pre-upgrade dump/snapshot.
- After FCV 7.0 and before FCV 8.0, MongoDB 8 can be stopped and the same volume
  reopened with `mongo:7.0.39`. Do not start MongoDB 6 on that volume.
- After FCV 8.0, do not attempt a binary rollback without following MongoDB's
  official downgrade requirements. For a clean rollback, restore the verified
  pre-upgrade dump or provider snapshot into a compatible deployment.
- Never use `docker compose down -v` on a deployment whose database volume must
  be retained.

## AWS Lightsail Checklist

- Create and identify a Lightsail snapshot before stopping writes.
- Confirm free disk can hold both current data files and the compressed dump.
- Run the same exact 7.0.39 and 8.0.28 stages and capture command output/logs.
- Keep TCP port 27017 closed on the Lightsail public firewall.
- After each stage, verify the public HTTPS login and all critical workflows.
- Retain the dump, application backup, checksum, and snapshot until burn-in and
  FCV 8.0 approval are complete.

## Verification Record

| Date | Environment | Binary | FCV | Backend tests | Backup/restore | Result |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-07-28 | Fresh local | 8.0.28 | 8.0 | 362 passed | Full backup and restore passed | Passed |
| 2026-07-28 | Copied local data | 8.0.28 | 7.0 and 8.0 | 362 passed | Full backup and restore passed | Passed |
| 2026-07-28 | Primary local | 8.0.28 | 7.0 | 362 passed | Post-upgrade full backup validated | Burn-in |

The copied-data rehearsal restored 304 documents with zero failures and
preserved all collection, index, and GridFS counts through MongoDB 6.0.28,
7.0.39, and 8.0.28. Login, dashboard totals, member images, tax PDF, yearly
Excel, and encrypted backup/restore were verified at both FCV 7.0 and FCV 8.0.

The primary migration retained FCV 7.0 for burn-in. Its pre-upgrade and
post-upgrade backups, password file, checksums, versions, counts, PDF, Excel,
and validation evidence are stored in
`~/Desktop/church-operation-backups/mongodb8-2026-07-28/`.

The aggregate actuator health endpoint reports `DOWN` when the static SMTP
configuration has no password because Spring's mail health contributor cannot
authenticate. During this migration the frontend, authenticated APIs, reports,
backup validation, and MongoDB all passed; MongoDB logs contained no critical
storage or startup errors. Configure valid SMTP credentials or assess the mail
health contributor separately from the database migration.

Official references:

- <https://www.mongodb.com/docs/manual/release-notes/7.0-upgrade-standalone/>
- <https://www.mongodb.com/docs/manual/release-notes/8.0-upgrade-standalone/>

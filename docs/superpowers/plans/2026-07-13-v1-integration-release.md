# v1.0 Integration And Release Verification Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add targeted audit events, verify every v1.0 workflow together, update operating documentation, and produce a releasable Docker build.

**Architecture:** A small immutable `SystemAuditEvent` service is injected only into high-risk operations. Final verification runs automated suites, disposable backup/archive round trips, PDF visual checks, role checks, responsive screenshots, and Docker smoke tests before versioning.

**Tech Stack:** Java 21, Spring Boot 4, MongoDB 6+, Vue 3, Docker Compose, JUnit 5, Testcontainers, Vitest, Vue Testing Library, Playwright, PDFBox.

## Global Constraints

- Do not audit passwords, ZIP contents, password hashes, or image bytes.
- Audit backup/restore/archive, receipt issue/void/replace, member delete, and reference delete outcomes.
- Keep the deployment runnable locally through Docker Compose.
- Preserve current AWS/environment configuration compatibility.
- Update English and Korean user-guide source content for new v1.0 workflows; regenerate documents only through their existing builders.

---

### Task 1: Add Targeted Immutable Audit Events

**Files:**
- Create: `backend/src/main/java/com/church/operation/entity/SystemAuditEvent.java`
- Create: `backend/src/main/java/com/church/operation/util/SystemAuditOperation.java`
- Create: `backend/src/main/java/com/church/operation/repo/SystemAuditEventRepository.java`
- Create: `backend/src/main/java/com/church/operation/service/SystemAuditService.java`
- Modify: `backend/src/main/java/com/church/operation/service/DataManagementService.java`
- Modify: `backend/src/main/java/com/church/operation/service/FiscalArchiveService.java`
- Modify: `backend/src/main/java/com/church/operation/service/TaxReceiptService.java`
- Modify: `backend/src/main/java/com/church/operation/service/MemberService.java`
- Modify: `backend/src/main/java/com/church/operation/service/ReferenceDataService.java`
- Test: `backend/src/test/java/com/church/operation/service/SystemAuditServiceTest.java`

**Interfaces:**
- Produces: `recordSuccess(...)` and `recordFailure(...)` with sanitized metadata.

- [ ] **Step 1: Write failing audit tests**

Assert actor ID/email, operation, timestamp, result, affected year/IDs, counts, operation ID, and sanitized error summary. Assert metadata keys containing `password`, `hash`, `bytes`, or `content` are rejected.

- [ ] **Step 2: Run and verify failure**

Run: `cd backend && mvn -Dtest=SystemAuditServiceTest test`

Expected: compilation failure.

- [ ] **Step 3: Implement immutable event service**

Expose factory-style methods rather than allowing callers to save arbitrary entities. Truncate sanitized failure messages and use allow-listed metadata keys.

- [ ] **Step 4: Integrate success/failure calls**

Wrap only public high-risk service methods. Logging failure must not hide the original operation exception; if audit persistence fails, log a server error and preserve the primary result.

- [ ] **Step 5: Run affected backend tests**

Run: `cd backend && mvn -Dtest=SystemAuditServiceTest,DataManagementServiceTest,FiscalArchiveServiceTest,TaxReceiptServiceTest,MemberServiceTest,ReferenceDataServiceTest test`

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/church/operation/entity/SystemAuditEvent.java backend/src/main/java/com/church/operation/util/SystemAuditOperation.java backend/src/main/java/com/church/operation/repo/SystemAuditEventRepository.java backend/src/main/java/com/church/operation/service/SystemAuditService.java backend/src/main/java/com/church/operation/service/DataManagementService.java backend/src/main/java/com/church/operation/service/FiscalArchiveService.java backend/src/main/java/com/church/operation/service/TaxReceiptService.java backend/src/main/java/com/church/operation/service/MemberService.java backend/src/main/java/com/church/operation/service/ReferenceDataService.java backend/src/test/java/com/church/operation/service/SystemAuditServiceTest.java
git commit -m "feat: audit high-risk v1 operations"
```

### Task 2: Add Cross-Feature Regression Tests

**Files:**
- Create: `backend/src/test/java/com/church/operation/V1WorkflowIntegrationTest.java`
- Modify: `frontend/src/App.test.ts`

**Interfaces:**
- Consumes: all previous v1.0 plans.
- Produces: one end-to-end backend lifecycle and route/role frontend regression coverage.

- [ ] **Step 1: Write backend workflow test**

In Testcontainers MongoDB:

1. Create members/images/reference data.
2. Create offering plus linked income and expense/budget.
3. Issue receipt.
4. Full backup.
5. Fiscal archive/clean.
6. Verify receipt/image/master data retained.
7. Restore fiscal archive.
8. Verify protected deletions block.
9. Full restore original backup.
10. Verify exact counts, indexes, GridFS bytes, and receipt snapshot.

- [ ] **Step 2: Run and fix only integration defects**

Run: `cd backend && mvn -Dtest=V1WorkflowIntegrationTest test`

Expected: pass.

- [ ] **Step 3: Add frontend route/role test matrix**

Assert System Administration is Admin-only, all staff reach Dashboard and see all cards, Admin/Treasurer reach Official Tax Receipts, and Member reaches only allowed self-service routes.

- [ ] **Step 4: Run frontend regression test**

Run: `cd frontend && npm test -- App.test.ts`

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/church/operation/V1WorkflowIntegrationTest.java frontend/src/App.test.ts
git commit -m "test: cover complete v1 workflows"
```

### Task 3: Update Configuration And Docker Limits

**Files:**
- Modify: `docker-compose.yml`
- Create: `.env.example`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `README.md`
- Test: `docker-compose.yml` config validation.

**Interfaces:**
- Produces: documented local configuration for receipt identity, upload limits, and temporary data.

- [ ] **Step 1: Add environment variables with safe empty defaults**

Document:

```dotenv
CHURCH_CHARITY_REGISTRATION_NUMBER=
CHURCH_RECEIPT_ISSUE_LOCATION=Toronto, Ontario
CHURCH_WEBSITE=
CHURCH_MEMBER_IMAGE_MAX_SIZE=5MB
CHURCH_DATA_MAX_UPLOAD_SIZE=2GB
```

Do not put real credentials or archive passwords in `.env.example`.

- [ ] **Step 2: Add multipart and temp-volume settings**

Set Spring multipart request/file limits compatible with the chosen backup maximum. Mount a dedicated temporary volume/path with documented cleanup; do not mount it as a permanent backup store.

- [ ] **Step 3: Validate Compose interpolation**

Run: `docker compose config`

Expected: valid resolved YAML with no unset-variable interpolation syntax errors.

- [ ] **Step 4: Build containers**

Run: `docker compose build`

Expected: backend/frontend images build successfully.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml .env.example backend/src/main/resources/application.yml README.md
git commit -m "docs: configure v1 data operations"
```

### Task 4: Update English And Korean User Guides

**Files:**
- Modify: `docs/user-guide/user-guide-content.md`
- Modify: `docs/user-guide/user-guide-content-ko.md`
- Modify: `docs/user-guide/build_user_guide.py` only if new screenshot/page mappings require it.
- Modify: `docs/user-guide/build_user_guide_ko.py` only if new screenshot/page mappings require it.
- Add: new PNG files under `docs/user-guide/screenshots/`
- Regenerate: `docs/user-guide/Church Operations User Guide.docx`
- Regenerate: `docs/user-guide/Church Operations User Guide.pdf`
- Regenerate: `docs/user-guide/교회운영 메뉴얼.docx`
- Regenerate: `docs/user-guide/교회운영 메뉴얼.pdf`

**Interfaces:**
- Produces: current role-aware v1.0 operating documentation.

- [ ] **Step 1: Capture branded screenshots with sample records**

Capture Dashboard, Members image controls, Official Tax summary, receipt preview, System Administration full backup/restore, fiscal archive, and blocked deletion. Do not expose passwords or private real-member data.

- [ ] **Step 2: Update English source content**

Document role access, backup password responsibility, mandatory safety backup, restore logout, fiscal archive retention, receipt issuance/signing, image controls, and deletion blockers.

- [ ] **Step 3: Translate the same content into Korean**

Keep the existing Korean title `교회운영 메뉴얼` and ensure role names/API-like codes remain recognizable.

- [ ] **Step 4: Regenerate and visually verify documents**

Use the existing builders and the document/PDF render workflow. Inspect every rendered page for clipped screenshots, broken Korean fonts, overflow, and stale captions.

- [ ] **Step 5: Commit**

```bash
git add docs/user-guide
git commit -m "docs: update v1 user guides"
```

### Task 5: Complete Release Verification And Versioning

**Files:**
- Modify: `backend/pom.xml`
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Create: `docs/release/v1.0-verification.md`

**Interfaces:**
- Produces: version `1.0.0` artifacts and evidence-based release checklist.

- [ ] **Step 1: Run clean backend verification**

Run: `cd backend && mvn clean test`

Expected: all unit, controller, PDF, and Testcontainers tests pass.

- [ ] **Step 2: Run clean frontend verification**

Run: `cd frontend && npm test && npm run build`

Expected: all tests and production build pass.

- [ ] **Step 3: Run Docker smoke and disposable destructive tests**

Run: `docker compose up --build -d`

Verify health, role matrix, Dashboard, image round trip, individual/batch PDFs, full backup/safety/restore, fiscal archive/restore, deletion blockers, and token invalidation.

- [ ] **Step 4: Capture responsive screenshots and canvas checks**

At desktop and mobile viewports confirm no overlap/overflow; verify Chart.js canvas has nonblank pixels and exactly 12 labels.

- [ ] **Step 5: Record evidence**

Write exact command outcomes, test counts, Docker image/container status, screenshot filenames, backup/archive test counts, PDF page/text checks, and any residual risks to `docs/release/v1.0-verification.md`.

- [ ] **Step 6: Set application versions to 1.0.0**

Change Maven project version and frontend package/lockfile version from `0.0.1` to `1.0.0`. Re-run backend package and frontend build.

- [ ] **Step 7: Commit release verification**

```bash
git add backend/pom.xml frontend/package.json frontend/package-lock.json docs/release/v1.0-verification.md
git commit -m "chore: prepare v1.0 release"
```

# Church Operations v1.0 Implementation Roadmap

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute the approved v1.0 design through six ordered, independently verifiable implementation plans.

**Architecture:** Build low-risk read-only dashboard changes first, then GridFS and receipt persistence, then the reusable backup foundation, destructive archive/deletion behavior, and finally cross-feature verification/release work. Each linked plan uses TDD and ends with a complete slice verification.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, Spring Data MongoDB, MongoDB 6+, Vue 3, Vue Router, Chart.js, Lucide, PDFBox 3.0.8, Zip4j 2.11.5, Docker Compose, JUnit 5, Mockito, Testcontainers, Vitest, Vue Testing Library, Playwright.

## Global Constraints

- Work on `develop` unless the user explicitly requests another branch.
- Preserve the Java package structure under `com.church.operation`.
- Follow the approved design in `docs/superpowers/specs/2026-07-13-church-operations-v1-design.md`.
- Run focused tests before each commit and full-slice verification after each linked plan.
- Do not deploy destructive features to AWS until disposable local round trips pass.
- Do not push, merge, or tag without explicit user approval.

---

### Task 1: Dashboard And Navigation

**Files:**
- Plan: `docs/superpowers/plans/2026-07-13-v1-dashboard-navigation.md`

**Interfaces:**
- Produces: `Member.createdAt`, `GET /api/dashboard`, menu icons, Admin route shell, dashboard UI.

- [ ] Execute every task in the linked plan and confirm its final verification passes.
### Task 2: Member Face Images

**Files:**
- Plan: `docs/superpowers/plans/2026-07-13-v1-member-images.md`

**Interfaces:**
- Consumes: menu/layout baseline.
- Produces: GridFS image service/endpoints/UI for full backup.

- [ ] Execute every task in the linked plan and confirm its final verification passes.

### Task 3: Official Tax Receipts

**Files:**
- Plan: `docs/superpowers/plans/2026-07-13-v1-tax-receipts.md`

**Interfaces:**
- Consumes: current member/offering records and church configuration.
- Produces: immutable receipt snapshots/counters/PDFs required by backup and deletion checks.

- [ ] Execute every task in the linked plan and confirm its final verification passes.

### Task 4: Full Backup And Restore

**Files:**
- Plan: `docs/superpowers/plans/2026-07-13-v1-full-backup-restore.md`

**Interfaces:**
- Consumes: GridFS and tax receipt collections.
- Produces: encrypted BSON package, complete round trip, operation staging, maintenance mode, Administration UI.

- [ ] Execute every task in the linked plan and confirm its final verification passes.

### Task 5: Fiscal Archive And Protected Deletion

**Files:**
- Plan: `docs/superpowers/plans/2026-07-13-v1-fiscal-archive-deletion.md`

**Interfaces:**
- Consumes: archive package/operation foundation and receipt/image services.
- Produces: fiscal archive registry/cleanup/restore and complete member/reference deletion guards.

- [ ] Execute every task in the linked plan and confirm its final verification passes.

### Task 6: Integration And Release

**Files:**
- Plan: `docs/superpowers/plans/2026-07-13-v1-integration-release.md`

**Interfaces:**
- Consumes: all completed feature slices.
- Produces: audit coverage, cross-feature tests, Docker/configuration/docs, user guides, version 1.0.0 verification.

- [ ] Execute every task in the linked plan and confirm its final verification passes.

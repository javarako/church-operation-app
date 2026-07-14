# Dashboard Corrections Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct the five marked dashboard display and data issues.

**Architecture:** Keep the existing dashboard API and Vue component contract. Correct date bucketing and legacy member-date fallback in `DashboardService`, then make tightly scoped template and CSS changes in `DashboardView`.

**Tech Stack:** Java 21, Spring Boot 4, Vue 3, Lucide Vue, JUnit 5, Mockito, Vitest, Vue Testing Library.

## Global Constraints

- Do not modify the paused backup/restore implementation.
- Preserve the banner image aspect ratio and do not crop it.
- Use no subagents for this small correction.

---

### Task 1: Correct Dashboard Data Dates

**Files:**
- Modify: `backend/src/main/java/com/church/operation/service/DashboardService.java`
- Test: `backend/src/test/java/com/church/operation/service/DashboardServiceTest.java`

- [ ] Add failing tests for ObjectId member-date fallback and a coming-Sunday offering trend.
- [ ] Run `mvn -Dtest=DashboardServiceTest test` and confirm the new assertions fail.
- [ ] Add the minimal member-date fallback and coming-Sunday window implementation.
- [ ] Run `mvn -Dtest=DashboardServiceTest test` and confirm all dashboard service tests pass.

### Task 2: Correct Dashboard Layout

**Files:**
- Modify: `frontend/src/views/DashboardView.vue`
- Test: `frontend/src/views/DashboardView.test.ts`

- [ ] Add a failing test requiring address, contact, and treasurer icons.
- [ ] Run `npm test -- --run src/views/DashboardView.test.ts` and confirm failure.
- [ ] Add the icon rows, proportional banner sizing, and compact summary-card layout.
- [ ] Run the focused frontend test and `npm run build`.

### Task 3: Verify The Slice

- [ ] Run the focused backend and frontend tests.
- [ ] Run `git diff --check`.
- [ ] Inspect the final diff and report any unavailable live-browser verification.


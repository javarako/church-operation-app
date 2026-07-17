# Reference Parent Label Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve and display parent labels in the Reference Data list without changing API values.

**Architecture:** Use the Offering Fund and Financial Category lists already loaded by `ReferenceDataView`. A small display helper selects the appropriate parent list by child reference type and falls back safely.

**Tech Stack:** Vue 3, TypeScript, Vitest, Vue Testing Library

## Global Constraints

- Preserve `parentCode` in storage and API payloads.
- Resolve labels for `OFFERING_CATEGORY` and `FINANCIAL_SUB_CATEGORY`.
- Display `-` for no parent and the raw code for an unknown parent.
- Leave changes uncommitted on `develop`.

---

### Task 1: Display Parent Labels In The Reference Data List

**Files:**
- Modify: `frontend/src/views/ReferenceDataView.test.ts`
- Modify: `frontend/src/views/ReferenceDataView.vue`

- [x] Add failing tests for Offering Fund and Financial Category parent labels.
- [x] Run `npm test -- ReferenceDataView.test.ts` and verify raw codes are still displayed.
- [x] Replace direct `parentCode` rendering with a type-aware `parentLabel` helper.
- [x] Run focused tests, the full frontend suite, and `npm run build`.
- [x] Rebuild and restart the frontend container and verify localhost returns 200.

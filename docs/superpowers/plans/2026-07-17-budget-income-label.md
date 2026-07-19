# Budget Income Label Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Budgets-page display text "Offering income" with "Income" without changing persisted values.

**Architecture:** Keep the `OFFERING_INCOME` enum/API contract unchanged. Update the two selector labels and the `typeLabel` display mapping in `BudgetsView.vue`.

**Tech Stack:** Vue 3, TypeScript, Vitest, Vue Testing Library

## Global Constraints

- Preserve `OFFERING_INCOME` as the option value and API payload.
- Change only Budgets-page display text.
- Leave changes uncommitted on `develop`.

---

### Task 1: Synchronize The Budgets Income Label

**Files:**
- Modify: `frontend/src/views/BudgetsView.test.ts`
- Modify: `frontend/src/views/BudgetsView.vue`

- [x] Add a test asserting both selectors use label `Income` for value `OFFERING_INCOME` and no `Offering income` text remains.
- [x] Run `npm test -- BudgetsView.test.ts` and verify the test fails against the old labels.
- [x] Replace both option labels and the `typeLabel('OFFERING_INCOME')` result with `Income`.
- [x] Run the focused test, full frontend suite, and `npm run build`.
- [x] Rebuild and restart the frontend container, then verify `http://localhost:5173/` returns 200.

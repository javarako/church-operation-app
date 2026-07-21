# Local 2025 Sample Financial Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Populate the current local application database with copied 2025 budgets, 48 representative offerings, and 24 representative expenses without changing application source code.

**Architecture:** A temporary Node.js module calls the running application's authenticated REST APIs. Pure helper functions build deterministic dates and cent-accurate allocations; a preflight phase validates credentials, source data, references, and duplicate guards before an explicit apply phase posts any records. A final verification phase reads the records back and downloads both yearly workbooks.

**Tech Stack:** Node.js 20+ built-in `fetch`, Spring Boot REST APIs, MongoDB through application services, Docker Compose.

## Global Constraints

- Operate only on the current local MongoDB database.
- Do not create a database backup.
- Do not modify application source code or configuration.
- Copy every active 2026 budget to 2025 at exactly 95%, rounded to two decimal places.
- Generate exactly 48 offerings and 24 manual expenses spanning January through December 2025.
- Every generated record must contain `[SAMPLE-2025]` in its memo.
- Stop before writing if any active 2025 budget or marked 2025 sample record already exists.
- Create offerings and expenses through the normal application APIs.
- Never write the administrator password to a repository file or command output.

---

### Task 1: Build And Test Deterministic Sample Calculations

**Files:**
- Create temporarily: `/private/tmp/church-operation-2025-sample-data.mjs`
- Create temporarily: `/private/tmp/church-operation-2025-sample-data.test.mjs`
- Modify: none

**Interfaces:**
- Produces: `toCents(value): number`, `fromCents(cents): string`, `scaleBudget(value): string`, `allocateCents(totalCents, count): number[]`, `monthSundays(year, month): string[]`, and `markedMemo(memo): string`.
- Consumes: Node.js `node:test` and `node:assert/strict` only.

- [ ] **Step 1: Write failing helper tests**

```javascript
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  allocateCents,
  markedMemo,
  monthSundays,
  scaleBudget,
  toCents,
} from './church-operation-2025-sample-data.mjs';

test('scales a budget to 95 percent with cent rounding', () => {
  assert.equal(scaleBudget('100.01'), '95.01');
});

test('allocates an exact total across every requested record', () => {
  const values = allocateCents(123457, 47);
  assert.equal(values.length, 47);
  assert.equal(values.reduce((sum, value) => sum + value, 0), 123457);
  assert.ok(values.every((value) => value > 0));
});

test('returns the first and third Sunday in a month', () => {
  assert.deepEqual(monthSundays(2025, 1), ['2025-01-05', '2025-01-19']);
});

test('adds the marker once while retaining an existing memo', () => {
  assert.equal(markedMemo('General Fund'), 'General Fund [SAMPLE-2025]');
  assert.equal(markedMemo('[SAMPLE-2025]'), '[SAMPLE-2025]');
});

test('converts decimal strings to integer cents', () => {
  assert.equal(toCents('21.257'), 2126);
});
```

- [ ] **Step 2: Run the tests and verify the intended RED state**

Run:

```bash
node --test /private/tmp/church-operation-2025-sample-data.test.mjs
```

Expected: FAIL because the helper exports do not exist yet.

- [ ] **Step 3: Implement the pure helpers**

```javascript
const MARKER = '[SAMPLE-2025]';

export function toCents(value) {
  return Math.round(Number(value) * 100);
}

export function fromCents(cents) {
  return (cents / 100).toFixed(2);
}

export function scaleBudget(value) {
  return fromCents(Math.round(toCents(value) * 0.95));
}

export function allocateCents(totalCents, count) {
  if (!Number.isInteger(count) || count < 1 || totalCents < count) {
    throw new Error('Allocation requires a positive cent amount for every record.');
  }
  const weights = Array.from({ length: count }, (_, index) => 90 + ((index * 17) % 21));
  const weightTotal = weights.reduce((sum, weight) => sum + weight, 0);
  const values = weights.map((weight) => Math.floor(totalCents * weight / weightTotal));
  let remainder = totalCents - values.reduce((sum, value) => sum + value, 0);
  for (let index = 0; remainder > 0; index = (index + 1) % values.length) {
    values[index] += 1;
    remainder -= 1;
  }
  return values;
}

export function monthSundays(year, month) {
  const first = new Date(Date.UTC(year, month - 1, 1));
  const offset = (7 - first.getUTCDay()) % 7;
  const firstSunday = 1 + offset;
  return [firstSunday, firstSunday + 14].map((day) =>
    `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  );
}

export function markedMemo(memo) {
  const value = String(memo ?? '').trim();
  if (value.includes(MARKER)) return value;
  return value ? `${value} ${MARKER}` : MARKER;
}
```

- [ ] **Step 4: Run helper tests to verify GREEN**

Run:

```bash
node --test /private/tmp/church-operation-2025-sample-data.test.mjs
```

Expected: 5 tests pass, 0 fail.

### Task 2: Add API Preflight And Dataset Construction

**Files:**
- Modify temporarily: `/private/tmp/church-operation-2025-sample-data.mjs`
- Modify temporarily: `/private/tmp/church-operation-2025-sample-data.test.mjs`

**Interfaces:**
- Consumes: the Task 1 helpers, `SAMPLE_API_BASE`, `SAMPLE_ADMIN_USERNAME`, and `SAMPLE_ADMIN_PASSWORD`.
- Produces: `api(path, options)`, `loadContext()`, `buildBudgetRequests(context)`, `buildOfferingRequests(context)`, `buildExpenseRequests(context)`, `preflight(context)`, `applyDataset(context)`, and `verifyDataset(context)`.

- [ ] **Step 1: Add failing construction tests**

Extend the test import and add this fixed context before the new tests:

```javascript
import {
  allocateCents,
  buildBudgetRequests,
  buildExpenseRequests,
  buildOfferingRequests,
  markedMemo,
  monthSundays,
  preflight,
  scaleBudget,
  toCents,
} from './church-operation-2025-sample-data.mjs';

function fixtureContext() {
  return {
    budgets2026: [
      { budgetType: 'OFFERING_INCOME', category: 'GENERAL', subCategory: 'TITHE', budget: '1000.00', memo: 'Annual budget' },
      { budgetType: 'OFFERING_INCOME', category: 'GENERAL', subCategory: 'MISSION', budget: '500.00', memo: null },
      { budgetType: 'EXPENSE', category: 'ADMIN', subCategory: 'OFFICE', budget: '800.00', memo: null },
      { budgetType: 'EXPENSE', category: 'CONTINGENCY', subCategory: null, budget: '200.00', memo: null },
      { budgetType: 'CARRY_OVER', category: null, subCategory: null, budget: '100.00', memo: null },
    ],
    budgets2025: [],
    offerings: [],
    transactions: [],
    offeringFunds: [
      { code: 'GENERAL', parentCode: null },
      { code: 'CARRY_OVER', parentCode: null },
    ],
    offeringCategories: [
      { code: 'TITHE', parentCode: 'GENERAL' },
      { code: 'MISSION', parentCode: 'GENERAL' },
      { code: 'PRIOR_YEAR', parentCode: 'CARRY_OVER' },
    ],
    paymentMethods: [{ code: 'CASH' }, { code: 'CHEQUE' }],
    financialCategories: [{ code: 'ADMIN' }, { code: 'CONTINGENCY' }],
    financialSubCategories: [{ code: 'OFFICE', parentCode: 'ADMIN' }],
    members: [{ id: 'member-1', active: true, locked: false, roles: ['ADMIN'] }],
    churchInformation: { treasurerName: 'Treasurer' },
  };
}
```

Add the construction and preflight tests:

```javascript
test('builds copied budgets at 95 percent', () => {
  const requests = buildBudgetRequests(fixtureContext());
  assert.equal(requests.length, fixtureContext().budgets2026.length);
  assert.deepEqual(requests[0], {
    fiscalYear: 2025,
    budgetType: 'OFFERING_INCOME',
    category: 'GENERAL',
    subCategory: 'TITHE',
    budget: '950.00',
    memo: 'Annual budget [SAMPLE-2025]',
  });
});

test('builds 48 offerings spanning every month with valid hierarchy pairs', () => {
  const requests = buildOfferingRequests(fixtureContext());
  assert.equal(requests.length, 48);
  assert.deepEqual(new Set(requests.map((row) => row.offeringDate.slice(0, 7))).size, 12);
  assert.ok(requests.every((row) => row.memo.includes('[SAMPLE-2025]')));
  assert.ok(requests.every((row) =>
    fixtureContext().offeringCategories.some((category) =>
      category.code === row.categoryCode && category.parentCode === row.fundCode
    )
  ));
});

test('builds 24 expenses spanning every month with valid hierarchy pairs', () => {
  const requests = buildExpenseRequests(fixtureContext());
  assert.equal(requests.length, 24);
  assert.deepEqual(new Set(requests.map((row) => row.transactionDate.slice(0, 7))).size, 12);
  assert.ok(requests.every((row) => row.memo.includes('[SAMPLE-2025]')));
  assert.ok(requests.every((row) => !row.subCategory ||
    fixtureContext().financialSubCategories.some((subCategory) =>
      subCategory.code === row.subCategory && subCategory.parentCode === row.category
    )
  ));
});

test('preflight rejects existing 2025 budgets or marked records', () => {
  assert.throws(
    () => preflight({ ...fixtureContext(), budgets2025: [{ id: 'existing' }] }),
    /2025 budgets already exist/
  );
});
```

- [ ] **Step 2: Run construction tests and verify RED**

Run:

```bash
node --test /private/tmp/church-operation-2025-sample-data.test.mjs
```

Expected: the original 5 tests pass and the 4 new tests fail because the
dataset builders do not exist.

- [ ] **Step 3: Implement authenticated API loading and strict preflight**

The implementation must:

```javascript
const baseUrl = process.env.SAMPLE_API_BASE ?? 'http://localhost:8080';
const username = process.env.SAMPLE_ADMIN_USERNAME ?? 'admin';
const password = process.env.SAMPLE_ADMIN_PASSWORD;
if (!password) throw new Error('SAMPLE_ADMIN_PASSWORD is required.');

const login = await fetch(`${baseUrl}/api/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password }),
});
if (!login.ok) throw new Error(`Login failed with HTTP ${login.status}.`);
const { token, roles, mustChangePassword } = await login.json();
if (mustChangePassword) throw new Error('The selected account must change its password first.');
if (!roles.includes('ADMIN') && !roles.includes('TREASURER')) {
  throw new Error('An ADMIN or TREASURER account is required.');
}
```

`loadContext()` must fetch:

- `/api/budgets?fiscalYear=2026`
- `/api/budgets?fiscalYear=2025`
- `/api/offerings`
- `/api/finance/transactions`
- `/api/members`
- `/api/church-information`
- active `OFFERING_FUND`, `OFFERING_CATEGORY`, `PAYMENT_METHOD`,
  `FINANCIAL_CATEGORY`, and `FINANCIAL_SUB_CATEGORY` reference lists.

`preflight(context)` must reject an empty 2026 budget list, any 2025 budget,
any marked offering or manual expense, an empty required reference list, no
valid offering fund/category pair, no financial category, or no active unlocked
member.

- [ ] **Step 4: Implement deterministic request builders**

The builders must apply these exact rules:

- Budget requests preserve type/category/sub-category, use fiscal year 2025,
  call `scaleBudget`, and call `markedMemo`.
- Offering dates are the first and third Sundays of every month, two records per
  selected Sunday.
- Offering rows rotate through valid parent/child hierarchy pairs and payment
  methods. Giving types rotate `MEMBER`, `ANONYMOUS`, and `GROUP`; non-member
  labels are `Anonymous 2025` and `Sample Group 2025`.
- Ordinary offering allocations sum to 92% of copied ordinary income-budget
  cents. If a valid `CARRY_OVER` hierarchy and copied carry-over budget exist,
  one of the 48 records uses that hierarchy and its amount equals the copied
  carry-over budget; otherwise all 48 records are ordinary.
- Expense dates are the 10th and 24th of every month.
- Expense rows rotate through valid financial category/sub-category pairs.
- Ordinary expense allocations sum to 85% of copied ordinary expense-budget
  cents. If `CONTINGENCY` exists with a copied budget, one of the 24 records uses
  it at 25% of its copied budget; otherwise all 24 records are ordinary.
- Expense fields rotate HST flags, cheque numbers, cleared flags, payees, and
  the `treasurerName` returned by `/api/church-information` without violating
  API validation.

- [ ] **Step 5: Implement apply and verification functions**

`applyDataset(context)` posts requests in this order and stops on the first
non-2xx response:

1. `POST /api/budgets`
2. `POST /api/offerings`
3. `POST /api/finance/expenses`

It prints only counts and the current record sequence, never credentials or
tokens. `verifyDataset(context)` reloads all collections and requires:

```javascript
assert.equal(markedBudgets2025.length, context.budgets2026.length);
assert.equal(markedOfferings2025.length, 48);
assert.equal(markedOfferingIncome2025.length, 48);
assert.equal(markedManualExpenses2025.length, 24);
assert.equal(new Set(markedOfferings2025.map((row) => row.offeringDate.slice(0, 7))).size, 12);
assert.equal(new Set(markedManualExpenses2025.map((row) => row.transactionDate.slice(0, 7))).size, 12);
```

It then requires HTTP 200 and the Excel content type from:

- `/api/reports/yearly-offerings.xlsx?fiscalYear=2025`
- `/api/reports/yearly-expenditures.xlsx?fiscalYear=2025`

- [ ] **Step 6: Run all temporary module tests**

Run:

```bash
node --test /private/tmp/church-operation-2025-sample-data.test.mjs
node --check /private/tmp/church-operation-2025-sample-data.mjs
```

Expected: 9 tests pass, 0 fail, and syntax checking exits 0.

### Task 3: Preflight And Populate The Local Database

**Files:**
- Use temporarily: `/private/tmp/church-operation-2025-sample-data.mjs`
- Modify: none

**Interfaces:**
- Consumes: running local frontend/backend/MongoDB stack and the user's current local ADMIN password.
- Produces: 2025 operational records in local MongoDB only.

- [ ] **Step 1: Confirm the local stack is running**

Run:

```bash
docker compose ps
curl --fail --silent http://localhost:8080/actuator/health
```

Expected: `mongo`, `backend`, and `frontend` are running; backend health is
`UP`. If not, run `docker compose up -d --build` and repeat the checks.

- [ ] **Step 2: Run read-only preflight**

Run without echoing the password:

```bash
read -s SAMPLE_ADMIN_PASSWORD
export SAMPLE_ADMIN_PASSWORD
node /private/tmp/church-operation-2025-sample-data.mjs --dry-run
```

Expected output reports source budget/reference/member counts, proposed counts
of all three record types, monthly coverage, and `Preflight passed`; it performs
zero POST requests.

- [ ] **Step 3: Apply the dataset after successful preflight**

Run in the same shell so the password remains only in the environment:

```bash
node /private/tmp/church-operation-2025-sample-data.mjs --apply
```

Expected: all 2025 budgets are created, followed by 48 offerings and 24 manual
expenses. The command exits 0 after API verification.

### Task 4: Verify Reports And Leave A Clean Workspace

**Files:**
- Delete temporary files after successful verification:
  `/private/tmp/church-operation-2025-sample-data.mjs`
  `/private/tmp/church-operation-2025-sample-data.test.mjs`
- Modify: none

**Interfaces:**
- Consumes: populated local database.
- Produces: final record totals and report-download evidence.

- [ ] **Step 1: Run the script's read-only verification mode**

Run:

```bash
node /private/tmp/church-operation-2025-sample-data.mjs --verify
```

Expected: copied budget count matches 2026, 48 offerings, 48 linked income
transactions, and 24 expenses; both yearly report downloads return valid Excel
responses.

- [ ] **Step 2: Inspect repository state**

Run:

```bash
git diff --check
git status --short
```

Expected: no data-population source files or generated report files appear in
the repository.

- [ ] **Step 3: Remove temporary generator files and clear the password**

Run:

```bash
rm /private/tmp/church-operation-2025-sample-data.mjs
rm /private/tmp/church-operation-2025-sample-data.test.mjs
unset SAMPLE_ADMIN_PASSWORD
```

Expected: both temporary files are gone and no credential remains in the
shell environment.

- [ ] **Step 4: Report exact results**

Report the 2025 budget count and totals by type, offering count and total,
linked-income count and total, expense count and total, January-to-December
coverage, and successful yearly Offering and Expenditure workbook generation.

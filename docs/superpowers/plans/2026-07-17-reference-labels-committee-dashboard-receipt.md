# Reference Labels, Committees, Dashboard Roles, And Receipt Logo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add multiple member Committee Codes, replace requested raw codes with labels, show all dashboard roles, add budget utilization percentages, and enlarge the tax-receipt logo by 30%.

**Architecture:** Persist committee assignments as normalized reference codes on `Member`. Keep report APIs code-based and resolve presentation labels in Vue from active reference data, with code fallback for historical values. Keep percentage calculation in Vue and scale the existing PDFBox logo bounds without changing the two-copy page geometry.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data MongoDB, PDFBox, Vue 3, Vue Router, Vitest, Vue Testing Library, JUnit 5, Mockito.

## Global Constraints

- Keep Java packages under `com.church.operation`.
- Preserve stored reference codes and existing report API contracts.
- Member committees allow zero or more active `COMMITTEE_CODE` values.
- Unknown historical display codes fall back to the raw code.
- Budget vs. Actual is `Actual / Budget * 100`, two decimals; zero Budget displays `-`.
- Tax-receipt page size and duplicated half-page layout remain unchanged.
- Follow TDD for each behavior change.
- Leave all changes uncommitted for the user to commit.

---

## File Structure

### Backend

- Modify `entity/Member.java`: persist `Set<String> committeeCodes`.
- Modify `dto/MemberRequest.java` and `dto/MemberResponse.java`: carry committee assignments.
- Modify `service/MemberService.java`: normalize and validate active Committee Codes.
- Modify `service/ReferenceDataDeletionService.java`: block deletion of assigned committees.
- Modify `service/TaxReceiptPdfService.java`: scale logo bounds by 1.3.
- Extend focused service and PDF tests.

### Frontend

- Modify `api/members.ts`: expose `committeeCodes`.
- Modify `views/MembersView.vue`: Committee multi-select and Group/Status labels.
- Modify `views/OfferingsView.vue`: Payment Method labels.
- Modify `views/ReportsView.vue`: requested labels, type rename, utilization percentage, and matching CSV export.
- Modify `views/DashboardView.vue`: display all assigned roles.
- Extend the corresponding Vitest files.

---

### Task 1: Persist And Validate Multiple Member Committees

**Files:**
- Modify: `backend/src/main/java/com/church/operation/entity/Member.java`
- Modify: `backend/src/main/java/com/church/operation/dto/MemberRequest.java`
- Modify: `backend/src/main/java/com/church/operation/dto/MemberResponse.java`
- Modify: `backend/src/main/java/com/church/operation/service/MemberService.java`
- Modify: `backend/src/main/java/com/church/operation/service/ReferenceDataDeletionService.java`
- Test: `backend/src/test/java/com/church/operation/service/MemberServiceTest.java`
- Test: `backend/src/test/java/com/church/operation/service/ReferenceDataDeletionServiceTest.java`

**Interfaces:**
- `Member.getCommitteeCodes(): Set<String>`
- `MemberRequest.committeeCodes(): Set<String>`
- `MemberResponse.committeeCodes(): Set<String>`

- [ ] **Step 1: Write failing member committee tests**

Add service tests that submit `["worship", "WORSHIP", "outreach"]`, mock active
`COMMITTEE_CODE` references, and assert the saved member contains
`["WORSHIP", "OUTREACH"]`. Add separate tests rejecting a missing or inactive
code.

```java
assertThat(saved.getCommitteeCodes()).containsExactly("WORSHIP", "OUTREACH");
assertThatThrownBy(() -> service.createMember(admin, requestWithCommittees("MISSING")))
    .hasMessage("Committee code was not found.");
```

- [ ] **Step 2: Write the failing deletion-protection test**

Mock `MongoTemplate.exists` for `Member.committeeCodes` and assert deleting
`COMMITTEE_CODE/WORSHIP` throws `DeletionBlockedException`.

```java
assertThatThrownBy(() -> service.delete(admin, "committee-id"))
    .isInstanceOf(DeletionBlockedException.class)
    .hasMessageContaining("member records");
```

- [ ] **Step 3: Run tests and verify RED**

Run:

```bash
cd backend
mvn -Dtest=MemberServiceTest,ReferenceDataDeletionServiceTest test
```

Expected: compilation or assertion failures because committee assignments do
not exist and Committee Code currently has no dependency.

- [ ] **Step 4: Implement committee persistence and validation**

Add a `LinkedHashSet<String>` field defaulting to empty. In managed member
create/update, normalize each nonblank value to uppercase, preserve input order,
remove duplicates, and require an active `COMMITTEE_CODE` reference:

```java
private Set<String> normalizeCommitteeCodes(Set<String> codes) {
    if (codes == null) return new LinkedHashSet<>();
    return codes.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .map(value -> value.toUpperCase(Locale.ROOT))
        .peek(this::requireActiveCommittee)
        .collect(Collectors.toCollection(LinkedHashSet::new));
}
```

Inject `ReferenceDataRepository` into `MemberService`. Do not allow the member
self-service path to change committees.

Change the `COMMITTEE_CODE` deletion branch to:

```java
case COMMITTEE_CODE ->
    addIfUsed(dependencies, Member.class, "committeeCodes", code, "member records");
```

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the Step 3 command. Expected: all focused tests pass.

---

### Task 2: Member Committee Control And Member List Labels

**Files:**
- Modify: `frontend/src/api/members.ts`
- Modify: `frontend/src/views/MembersView.vue`
- Modify: `frontend/src/views/MembersView.test.ts`

**Interfaces:**
- `MemberPayload.committeeCodes?: string[]`
- `labelFor(options, code): string`

- [ ] **Step 1: Write failing Vue tests**

Mock active Group, Membership Status, and Committee references. Assert the
member row renders `Adult` and `Active` instead of `ADULT` and `ACTIVE`.
Select two committee checkboxes and assert the update payload contains both
codes.

```ts
expect(await screen.findByText('Adult')).toBeTruthy();
expect(screen.getByText('Active')).toBeTruthy();
expect(updateMember).toHaveBeenCalledWith('member-1',
  expect.objectContaining({ committeeCodes: ['WORSHIP', 'OUTREACH'] }));
```

- [ ] **Step 2: Run the test and verify RED**

```bash
cd frontend
npm test -- src/views/MembersView.test.ts
```

Expected: missing labels, Committee control, and payload.

- [ ] **Step 3: Implement the Member UI**

Load `COMMITTEE_CODE` alongside Group and Status. Add `committeeCodes` to form
defaults and form mapping. Render a compact checkbox menu:

```vue
<details class="multi-select">
  <summary>{{ committeeSummary }}</summary>
  <label v-for="option in committeeOptions" :key="option.code">
    <input v-model="form.committeeCodes" type="checkbox" :value="option.code" />
    {{ option.label }}
  </label>
</details>
```

Use a shared local lookup helper for Group and Status cells, falling back to the
code or `-`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Step 2 command. Expected: pass.

---

### Task 3: Offering And Report Labels With Budget Percentage

**Files:**
- Modify: `frontend/src/views/OfferingsView.vue`
- Create: `frontend/src/views/OfferingsView.test.ts`
- Modify: `frontend/src/views/ReportsView.vue`
- Modify: `frontend/src/views/ReportsView.test.ts`

**Interfaces:**
- `referenceLabel(options, code): string`
- `givingTypeLabel(type): string`
- `budgetTypeLabel(type): string`
- `budgetActualPercentage(budget, actual): string`

- [ ] **Step 1: Write failing offering-label test**

Return offering payment code `CHEQUE` and reference label `Cheque`; assert the
table renders `Cheque` and does not render a standalone `CHEQUE`.

- [ ] **Step 2: Write failing report display tests**

Seed weekly, member, and financial rows plus all five relevant reference types:
Offering Fund, Offering Category, Payment Method, Financial Category, and
Financial Sub-category. Assert:

```ts
expect(screen.getByText('General Fund')).toBeTruthy();
expect(screen.getByText('Tithe')).toBeTruthy();
expect(screen.getByText('Anonymous')).toBeTruthy();
expect(screen.getByText('Cheque')).toBeTruthy();
expect(screen.getByText('INCOME')).toBeTruthy();
expect(screen.getByText('75.00%')).toBeTruthy();
```

Add a zero-budget row and assert its percentage cell is `-`.

- [ ] **Step 3: Run tests and verify RED**

```bash
cd frontend
npm test -- src/views/OfferingsView.test.ts src/views/ReportsView.test.ts
```

Expected: raw codes and missing percentage column.

- [ ] **Step 4: Implement label lookups**

Offerings uses `paymentMethodOptions` for the Payment table cell.

Reports loads `FINANCIAL_CATEGORY` and `FINANCIAL_SUB_CATEGORY` in addition to
the existing offering references. Resolve labels by row type:

```ts
function financialCategoryLabel(row: FinancialBudgetReportRow) {
  return referenceLabel(
    row.budgetType === 'OFFERING_INCOME' ? offeringFundOptions.value : financialCategoryOptions.value,
    row.category,
  );
}
```

Use Offering Category for income sub-categories and Financial Sub-category for
expense sub-categories. Fixed enum labels convert Giving Type and
`OFFERING_INCOME` to `INCOME`.

- [ ] **Step 5: Implement percentage and CSV parity**

Insert the column after Actual:

```vue
<td>{{ budgetActualPercentage(row.budget, row.actual) }}</td>
```

```ts
function budgetActualPercentage(budget: number, actual: number) {
  return budget === 0 ? '-' : `${((actual / budget) * 100).toFixed(2)}%`;
}
```

Use the same helper and label functions when constructing all report CSV rows.

- [ ] **Step 6: Run focused tests and verify GREEN**

Run the Step 3 command. Expected: pass.

---

### Task 4: Display All Dashboard Roles

**Files:**
- Modify: `frontend/src/views/DashboardView.vue`
- Modify: `frontend/src/views/DashboardView.test.ts`

**Interfaces:**
- `currentRoleLabel: ComputedRef<string>`

- [ ] **Step 1: Write the failing dashboard test**

Set the current user roles to `['ADMIN', 'TREASURER', 'MEMBER']` and assert the
identity area contains `ADMIN, TREASURER, MEMBER`.

- [ ] **Step 2: Run the test and verify RED**

```bash
cd frontend
npm test -- src/views/DashboardView.test.ts
```

Expected: only `ADMIN` is displayed.

- [ ] **Step 3: Implement all-role display**

```ts
const currentRoleLabel = computed(() => {
  const roles = authState.currentUser?.roles ?? [];
  return roles.length ? roles.join(', ') : 'User';
});
```

Allow the existing role container to wrap cleanly at narrow widths.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Step 2 command. Expected: pass.

---

### Task 5: Enlarge Tax Receipt Logo Without Changing Page Geometry

**Files:**
- Modify: `backend/src/main/java/com/church/operation/service/TaxReceiptPdfService.java`
- Modify: `backend/src/test/java/com/church/operation/service/TaxReceiptPdfServiceTest.java`

**Interfaces:**
- Maximum logo box changes from `72 x 44` points to `93.6 x 57.2` points.

- [ ] **Step 1: Write the failing PDF renderer test**

Render a receipt with the real test logo. Inspect the page content stream or
rendered pixels to verify each logo's bounding box is approximately 30% larger
while the PDF remains one letter-size page containing two official receipt
headings.

- [ ] **Step 2: Run the test and verify RED**

```bash
cd backend
mvn -Dtest=TaxReceiptPdfServiceTest test
```

Expected: current logo bounds remain `72 x 44`.

- [ ] **Step 3: Scale logo and adjust church text origin**

Use constants:

```java
private static final float LOGO_MAX_WIDTH = 93.6f;
private static final float LOGO_MAX_HEIGHT = 57.2f;
```

Calculate one aspect-ratio-preserving scale with those maxima and move
`churchX` far enough right to prevent overlap. Keep both calls to
`renderReceipt` and `HALF_HEIGHT` unchanged.

- [ ] **Step 4: Run the PDF test and verify GREEN**

Run the Step 2 command. Expected: pass.

---

### Task 6: Documentation And Full Verification

**Files:**
- Modify: `docs/superpowers/specs/2026-07-07-church-operations-app-design.md`
- Modify: `docs/user-guide/user-guide-content.md`
- Modify: `docs/user-guide/user-guide-content-ko.md`
- Modify: `.superpowers/sdd/progress.md`

- [ ] **Step 1: Update current documentation**

Document multiple Committee assignments, label rendering, all dashboard roles,
Budget vs. Actual percentage, and the enlarged tax-receipt logo. Preserve old
historical plans as implementation history.

- [ ] **Step 2: Run all backend tests**

```bash
cd backend
mvn test
```

Expected: zero failures and errors.

- [ ] **Step 3: Run all frontend tests and production build**

```bash
cd frontend
npm test
npm run build
```

Expected: all test files pass and Vite produces `dist`.

- [ ] **Step 4: Verify Docker and local runtime**

```bash
cd ..
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
curl -fsS -o /dev/null -w '%{http_code}\n' http://localhost:5173/
curl -fsS -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/church-information
```

Expected: all services Up and both probes return `200`.

- [ ] **Step 5: Final review**

```bash
git diff --check
git status --short
```

Expected: no whitespace errors; all intended changes remain uncommitted.

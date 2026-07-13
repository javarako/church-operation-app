# v1.0 Dashboard And Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace frontend-computed dashboard summaries with one backend API, implement the approved four-card/12-Sunday dashboard, and add consistent icons plus the Admin-only System Administration route.

**Architecture:** Add `DashboardService` and `DashboardController` under the existing Java package boundaries. The Vue dashboard consumes one typed response and renders the approved cards with Chart.js; `AppLayout` uses `lucide-vue-next` for every menu action.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data MongoDB, Spring Security, Vue 3.5, Vue Router 4.5, Chart.js, vue-chartjs, lucide-vue-next, JUnit 5, Mockito, Vitest, Vue Testing Library.

## Global Constraints

- Keep Java packages under `com.church.operation` and the existing package structure.
- All staff roles (`ADMIN`, `TREASURER`, `PASTOR`, `VIEWER`, `MEMBERSHIP`) see every dashboard card and chart.
- Keep Offering Overview behavior; remove Membership and Recent Finance Activity panels.
- Use the configured fiscal-year start month for YTD budget cards.
- Include exactly 12 Sunday trend points, including zero-value Sundays.
- Use Lucide icons for every left-menu item; use a hand-and-heart for offering.
- Do not alter unrelated page behavior or styling.

---

### Task 1: Persist Member Creation Time

**Files:**
- Modify: `backend/src/main/java/com/church/operation/entity/Member.java`
- Modify: `backend/src/main/java/com/church/operation/service/MemberService.java`
- Modify: `backend/src/main/java/com/church/operation/dto/MemberResponse.java`
- Modify: `frontend/src/api/members.ts`
- Test: `backend/src/test/java/com/church/operation/service/MemberServiceTest.java`

**Interfaces:**
- Produces: `Member.getCreatedAt(): Instant`, `MemberResponse.createdAt(): Instant`.
- Consumed by: Task 2 dashboard aggregation.

- [ ] **Step 1: Write the failing creation-time test**

```java
@Test
void createMemberSetsCreatedAt() {
    Member actor = member(Role.MEMBERSHIP);
    when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Member saved = service.createMember(actor, validRequest("new@example.com"));

    assertThat(saved.getCreatedAt()).isNotNull();
}
```

- [ ] **Step 2: Run the focused test and verify failure**

Run: `cd backend && mvn -Dtest=MemberServiceTest#createMemberSetsCreatedAt test`

Expected: compilation failure because `Member.getCreatedAt()` does not exist.

- [ ] **Step 3: Add the timestamp field and set it only on creation**

```java
private Instant createdAt;

public Instant getCreatedAt() { return createdAt; }
public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
```

In `createMember`:

```java
member.setCreatedAt(Instant.now());
applyManagedFields(member, request);
```

Expose `createdAt` through `MemberResponse` and the frontend `Member` type. Do not synthesize timestamps for existing records.

- [ ] **Step 4: Run backend tests**

Run: `cd backend && mvn -Dtest=MemberServiceTest test`

Expected: all `MemberServiceTest` tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/church/operation/entity/Member.java backend/src/main/java/com/church/operation/service/MemberService.java backend/src/main/java/com/church/operation/dto/MemberResponse.java backend/src/test/java/com/church/operation/service/MemberServiceTest.java frontend/src/api/members.ts
git commit -m "feat: record member creation time"
```

### Task 2: Add The Dashboard Summary API

**Files:**
- Create: `backend/src/main/java/com/church/operation/dto/DashboardResponse.java`
- Create: `backend/src/main/java/com/church/operation/dto/DashboardTrendPoint.java`
- Create: `backend/src/main/java/com/church/operation/service/DashboardService.java`
- Create: `backend/src/main/java/com/church/operation/rest/DashboardController.java`
- Modify: `backend/src/main/java/com/church/operation/repo/OfferingRepository.java`
- Modify: `backend/src/main/java/com/church/operation/repo/FinancialTransactionRepository.java`
- Test: `backend/src/test/java/com/church/operation/service/DashboardServiceTest.java`

**Interfaces:**
- Consumes: `Member.createdAt`, `FiscalYearProperties.startMonth()`.
- Produces: `DashboardService.getDashboard(Member actor, LocalDate today): DashboardResponse` and `GET /api/dashboard`.

- [ ] **Step 1: Write failing aggregation tests**

Create tests for:

```java
@Test void returnsFourCardValuesAndTwelveSundayPoints() { /* fixed today 2026-07-13 */ }
@Test void zeroBudgetsReturnNullPercentages() { /* no division by zero */ }
@Test void membershipRoleCanReadAllFinancialCards() { /* no forbidden exception */ }
@Test void memberRoleCannotReadStaffDashboard() { /* SecurityException */ }
```

Assert a response shaped as:

```java
new DashboardResponse(
    128, 3,
    new BigDecimal("215600.00"), new BigDecimal("298000.00"), new BigDecimal("72.35"),
    new BigDecimal("96420.00"), new BigDecimal("156000.00"), new BigDecimal("61.81"),
    5, new BigDecimal("8250.00"),
    new BigDecimal("12450.00"), new BigDecimal("38925.00"), new BigDecimal("215600.00"),
    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
    twelvePoints
)
```

- [ ] **Step 2: Run the test and verify failure**

Run: `cd backend && mvn -Dtest=DashboardServiceTest test`

Expected: compilation failure because dashboard classes do not exist.

- [ ] **Step 3: Add focused repository range queries**

```java
@Query("{ 'deleted': false, 'offeringDate': { $gte: ?0, $lte: ?1 } }")
List<Offering> findActiveByOfferingDateBetween(LocalDate start, LocalDate end);

@Query("{ 'deleted': { $ne: true }, 'type': 'EXPENSE', 'transactionDate': { $gte: ?0, $lte: ?1 } }")
List<FinancialTransaction> findActiveExpensesBetween(LocalDate start, LocalDate end);
```

Use the existing weekly-offering query for trend and Offering Overview where possible.

- [ ] **Step 4: Implement DTOs and `DashboardService`**

Use a stable percentage helper:

```java
private BigDecimal percentage(BigDecimal actual, BigDecimal budget) {
    if (budget == null || budget.compareTo(BigDecimal.ZERO) == 0) return null;
    return actual.multiply(BigDecimal.valueOf(100)).divide(budget, 2, RoundingMode.HALF_UP);
}
```

Generate Sunday points with:

```java
LocalDate latestSunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
List<LocalDate> sundays = IntStream.range(0, 12)
    .mapToObj(index -> latestSunday.minusWeeks(11L - index))
    .toList();
```

Pending cheques require `type == EXPENSE`, nonblank `chequeNo`, `!deleted`, and `!chequeCleared`.

- [ ] **Step 5: Add the explicit controller endpoint**

```java
@GetMapping("/api/dashboard")
DashboardResponse dashboard(Authentication authentication) {
    return dashboardService.getDashboard((Member) authentication.getPrincipal(), LocalDate.now());
}
```

- [ ] **Step 6: Run backend tests**

Run: `cd backend && mvn -Dtest=DashboardServiceTest test`

Expected: all dashboard tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/church/operation/dto/DashboardResponse.java backend/src/main/java/com/church/operation/dto/DashboardTrendPoint.java backend/src/main/java/com/church/operation/service/DashboardService.java backend/src/main/java/com/church/operation/rest/DashboardController.java backend/src/main/java/com/church/operation/repo/OfferingRepository.java backend/src/main/java/com/church/operation/repo/FinancialTransactionRepository.java backend/src/test/java/com/church/operation/service/DashboardServiceTest.java
git commit -m "feat: add dashboard summary API"
```

### Task 3: Add Menu Icons And System Administration Route

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Modify: `frontend/src/auth/roles.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/layouts/AppLayout.vue`
- Modify: `frontend/src/layouts/AppLayout.test.ts`
- Create: `frontend/src/views/SystemAdministrationView.vue`

**Interfaces:**
- Produces: `adminRoles: Role[]`, route `/system-administration`.
- Consumed by: backup/archive plans.

- [ ] **Step 1: Add failing layout tests**

Assert every visible link contains an SVG and only Admin sees System Administration:

```ts
expect(screen.getByRole('link', { name: 'Dashboard' }).querySelector('svg')).toBeTruthy();
expect(screen.getByRole('link', { name: 'System Administration' })).toBeTruthy();
```

For Treasurer:

```ts
expect(screen.queryByRole('link', { name: 'System Administration' })).toBeNull();
```

- [ ] **Step 2: Run the test and verify failure**

Run: `cd frontend && npm test -- AppLayout.test.ts`

Expected: menu icon and System Administration assertions fail.

- [ ] **Step 3: Install Lucide Vue**

Run: `cd frontend && npm install lucide-vue-next`

Expected: `package.json` and lockfile contain `lucide-vue-next`.

- [ ] **Step 4: Add role and route**

```ts
export const adminRoles: Role[] = ['ADMIN'];
```

```ts
{ path: '/system-administration', component: SystemAdministrationView, meta: { roles: adminRoles } }
```

- [ ] **Step 5: Render icon-and-text links**

Import `House`, `Users`, `HandHeart`, `Landmark`, `ChartPie`, `BookOpen`, `ChartColumn`, `Settings`, `UserRound`, and `LogOut`. Keep accessible text and add `aria-hidden="true"` to decorative icons.

The placeholder Administration view must be an actual quiet tool shell:

```vue
<section class="workspace">
  <header><h2>System Administration</h2></header>
  <section class="panel"><p>Data management controls are not available yet.</p></section>
</section>
```

- [ ] **Step 6: Run frontend tests and build**

Run: `cd frontend && npm test -- AppLayout.test.ts`

Expected: pass.

Run: `cd frontend && npm run build`

Expected: TypeScript and Vite build pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/auth/roles.ts frontend/src/router/index.ts frontend/src/layouts/AppLayout.vue frontend/src/layouts/AppLayout.test.ts frontend/src/views/SystemAdministrationView.vue
git commit -m "feat: add navigation icons and admin route"
```

### Task 4: Build The Approved Dashboard UI

**Files:**
- Create: `frontend/src/api/dashboard.ts`
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Rewrite: `frontend/src/views/DashboardView.vue`
- Rewrite: `frontend/src/views/DashboardView.test.ts`
- Modify: `frontend/src/styles/main.css`

**Interfaces:**
- Consumes: `GET /api/dashboard` from Task 2.
- Produces: `getDashboard(): Promise<DashboardResponse>` and approved dashboard UI.

- [ ] **Step 1: Write failing dashboard tests against one API call**

Mock `getDashboard` and assert:

```ts
expect(await screen.findByText('Active Members')).toBeTruthy();
expect(screen.getByText('YTD Offering vs Budget')).toBeTruthy();
expect(screen.getByText('YTD Expense vs Budget')).toBeTruthy();
expect(screen.getByText('Pending Cheques')).toBeTruthy();
expect(screen.getByText('Offering Overview')).toBeTruthy();
expect(screen.getByText('Offering Trend')).toBeTruthy();
expect(screen.queryByText('Recent Finance Activity')).toBeNull();
expect(screen.queryByText('Membership')).toBeNull();
```

Repeat the visibility assertion for each staff role.

- [ ] **Step 2: Run tests and verify failure**

Run: `cd frontend && npm test -- DashboardView.test.ts`

Expected: failures because the view still calls report/member/finance APIs.

- [ ] **Step 3: Install chart dependencies and add API types**

Run: `cd frontend && npm install chart.js vue-chartjs`

Define `DashboardResponse` with nullable percentages and twelve `trend` points.

- [ ] **Step 4: Implement the card row and Offering Overview**

Use Lucide `Users`, `HandHeart`, `ChartNoAxesCombined`, and `ReceiptText`. Clamp progress width without hiding over-budget values:

```ts
const progressWidth = (value: number | null) => `${Math.min(Math.max(value ?? 0, 0), 100)}%`;
```

Display the actual percentage text even when over 100%.

- [ ] **Step 5: Add the 12-Sunday bar chart**

Register only required Chart.js components and set stable responsive dimensions. Dataset labels come directly from the backend; do not recalculate dates in Vue.

- [ ] **Step 6: Run focused tests and build**

Run: `cd frontend && npm test -- DashboardView.test.ts AppLayout.test.ts`

Expected: pass.

Run: `cd frontend && npm run build`

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/api/dashboard.ts frontend/src/views/DashboardView.vue frontend/src/views/DashboardView.test.ts frontend/src/styles/main.css
git commit -m "feat: refresh staff dashboard"
```

### Task 5: Verify Dashboard And Navigation Slice

**Files:**
- Modify only if verification reveals a scoped defect.

**Interfaces:**
- Consumes: completed dashboard/navigation slice.
- Produces: verified baseline for later v1.0 plans.

- [ ] **Step 1: Run complete automated suites**

Run: `cd backend && mvn test`

Expected: all backend tests pass.

Run: `cd frontend && npm test && npm run build`

Expected: all frontend tests and build pass.

- [ ] **Step 2: Start Docker Compose and smoke test**

Run: `docker compose up --build -d`

Expected: MongoDB, backend, and frontend containers are running.

Verify `/actuator/health`, login, dashboard cards, all menu icons, and Admin-only route.

- [ ] **Step 3: Capture desktop and mobile screenshots**

Use Playwright at a desktop viewport and a mobile viewport. Confirm no clipped card text, blank chart, overlapping controls, or horizontal page overflow.

- [ ] **Step 4: Confirm verification leaves no unreviewed changes**

Run: `git diff --check`

Expected: no output. If a defect was found, return to the task that owns the affected component, add a failing regression test there, fix it, and repeat this verification task.

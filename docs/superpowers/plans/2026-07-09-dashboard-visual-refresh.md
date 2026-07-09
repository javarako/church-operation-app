# Dashboard Visual Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the approved dashboard mockup direction to the shared menu and dashboard header/cards using existing church branding and information.

**Architecture:** This is a frontend-focused refresh. A small `churchInformation` API client will read the existing `/api/church-information` endpoint; `AppLayout.vue` will use it for the shared menu logo; `DashboardView.vue` will use it for the banner and church information panel while keeping Dashboard V1 data loading. Tests will mock the new client and existing dashboard APIs.

**Tech Stack:** Vue 3, Vue Router, TypeScript, Vue Testing Library, Vitest, existing Spring Boot `/api/church-information` endpoint.

## Global Constraints

- Restyle the shared left menu in `AppLayout` so every page keeps the same dashboard-style navigation shell.
- Show the configured church logo at the top of the left menu.
- Load church branding and information from `/api/church-information`.
- Add a dashboard hero row with the configured banner image.
- Show church name, address, contact info, treasurer name, current user initials, and role display beside the banner.
- Restyle existing dashboard summary cards to match the mockup direction.
- Remove the dashboard Quick Links panel because the left menu already provides navigation.
- Keep role-aware dashboard sections from Dashboard V1.
- Do not recreate the mockup's lower tabbed workspace inside the dashboard.
- Do not add icons from a new dependency.
- Do not add a new backend endpoint.
- Do not change page routing or role permissions.
- Do not change the login page design in this slice.

---

## File Structure

- Create `frontend/src/api/churchInformation.ts`
  - Typed API client for `/api/church-information`.
- Create `frontend/src/layouts/AppLayout.test.ts`
  - Verifies the shared menu loads logo/app branding and keeps role-aware navigation.
- Modify `frontend/src/layouts/AppLayout.vue`
  - Loads church information, renders menu logo/app name fallback, preserves existing links.
- Modify `frontend/src/views/DashboardView.test.ts`
  - Adds church information mock, hero assertions, role display assertions, and removes quick-link expectations.
- Modify `frontend/src/views/DashboardView.vue`
  - Loads church information, renders banner/church info/user role panel, removes quick links, refreshes summary card styling.
- Modify `frontend/src/styles/main.css`
  - Updates global layout/sidebar styling shared by every authenticated page.

---

### Task 1: Add Church Information API Client

**Files:**
- Create: `frontend/src/api/churchInformation.ts`
- Test: covered by later component tests that mock and consume this client

**Interfaces:**
- Produces:
  - `ChurchInformation` interface with fields `name`, `address`, `contactInfo`, `treasurerName`, `bannerPath`, `logPath`.
  - `getChurchInformation(): Promise<ChurchInformation>`.
- Consumes:
  - `getJson<TResponse>(path: string)` from `frontend/src/api/http.ts`.

- [ ] **Step 1: Create the API client**

Create `frontend/src/api/churchInformation.ts`:

```ts
import { getJson } from './http';

export interface ChurchInformation {
  name: string;
  address: string;
  contactInfo: string;
  treasurerName: string;
  bannerPath: string;
  logPath: string;
}

export function getChurchInformation() {
  return getJson<ChurchInformation>('/api/church-information');
}
```

- [ ] **Step 2: Run TypeScript build to verify the new client compiles**

Run:

```bash
cd frontend
npm run build
```

Expected: PASS with Vite build output.

- [ ] **Step 3: Commit the client**

Run:

```bash
git add frontend/src/api/churchInformation.ts
git commit -m "Add church information frontend API"
```

---

### Task 2: Restyle Shared Left Menu

**Files:**
- Create: `frontend/src/layouts/AppLayout.test.ts`
- Modify: `frontend/src/layouts/AppLayout.vue`
- Modify: `frontend/src/styles/main.css`
- Test: `frontend/src/layouts/AppLayout.test.ts`

**Interfaces:**
- Consumes:
  - `getChurchInformation(): Promise<ChurchInformation>` from Task 1.
  - `authState.currentUser` from `frontend/src/auth/authStore.ts`.
- Produces:
  - Shared menu brand block with logo image if `churchInfo.logPath` is available.
  - Existing role-aware navigation links with unchanged permissions.

- [ ] **Step 1: Write the failing AppLayout tests**

Create `frontend/src/layouts/AppLayout.test.ts`:

```ts
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/vue';
import { createRouter, createWebHistory } from 'vue-router';
import AppLayout from './AppLayout.vue';
import { authState } from '../auth/authStore';
import { getChurchInformation } from '../api/churchInformation';

vi.mock('../api/churchInformation', () => ({
  getChurchInformation: vi.fn().mockResolvedValue({
    name: 'Grace Community Church',
    address: '123 Church Street',
    contactInfo: '416-555-0100',
    treasurerName: 'Daniel Kim',
    bannerPath: '/branding/church-banner.png',
    logPath: '/branding/church_logo.png',
  }),
}));

const churchInformationMock = vi.mocked(getChurchInformation);

function router() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<span>Dashboard</span>' } },
      { path: '/members', component: { template: '<span>Members</span>' } },
      { path: '/offerings', component: { template: '<span>Offerings</span>' } },
      { path: '/finance', component: { template: '<span>Finance</span>' } },
      { path: '/budgets', component: { template: '<span>Budgets</span>' } },
      { path: '/reference-data', component: { template: '<span>Reference Data</span>' } },
      { path: '/reports', component: { template: '<span>Reports</span>' } },
      { path: '/profile', component: { template: '<span>Profile</span>' } },
    ],
  });
}

async function renderLayout() {
  const testRouter = router();
  testRouter.push('/');
  await testRouter.isReady();
  return render(AppLayout, {
    slots: {
      default: '<main>Page Content</main>',
    },
    global: {
      plugins: [testRouter],
    },
  });
}

describe('AppLayout', () => {
  beforeEach(() => {
    churchInformationMock.mockResolvedValue({
      name: 'Grace Community Church',
      address: '123 Church Street',
      contactInfo: '416-555-0100',
      treasurerName: 'Daniel Kim',
      bannerPath: '/branding/church-banner.png',
      logPath: '/branding/church_logo.png',
    });
    authState.currentUser = {
      primaryEmail: 'admin@example.com',
      displayName: 'Admin User',
      roles: ['ADMIN'],
      mustChangePassword: false,
      token: 'token',
    };
  });

  afterEach(() => {
    cleanup();
    authState.currentUser = null;
    vi.clearAllMocks();
  });

  it('shows church logo and app name in the shared menu', async () => {
    await renderLayout();

    const logo = await screen.findByAltText('Grace Community Church logo');

    expect(logo.getAttribute('src')).toBe('/branding/church_logo.png');
    expect(screen.getByText('Church Operations')).toBeTruthy();
    expect(screen.getByText('Page Content')).toBeTruthy();
  });

  it('keeps role-aware menu links for admin', async () => {
    await renderLayout();

    expect(await screen.findByRole('link', { name: 'Dashboard' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Members' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Offerings' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Finance' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Budgets' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Reports' })).toBeTruthy();
  });

  it('falls back to text branding when church information fails', async () => {
    churchInformationMock.mockRejectedValue(new Error('Church info unavailable.'));

    await renderLayout();

    expect(await screen.findByText('Church Operations')).toBeTruthy();
    expect(screen.queryByAltText(/logo/i)).toBeNull();
  });
});
```

- [ ] **Step 2: Run the AppLayout tests to verify they fail**

Run:

```bash
cd frontend
npm test -- --run src/layouts/AppLayout.test.ts
```

Expected: FAIL because `AppLayout.vue` does not load or render church information yet.

- [ ] **Step 3: Update AppLayout markup and script**

Replace `frontend/src/layouts/AppLayout.vue` with:

```vue
<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <img v-if="churchInfo?.logPath" :src="churchInfo.logPath" :alt="`${churchInfo.name} logo`" />
        <div v-else class="sidebar-logo-fallback">CO</div>
        <h1>Church Operations</h1>
      </div>

      <nav class="sidebar-nav" aria-label="Main menu">
        <RouterLink to="/">Dashboard</RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'MEMBERSHIP'])" to="/members">Members</RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'TREASURER'])" to="/offerings">Offerings</RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'TREASURER'])" to="/finance">Finance</RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'TREASURER'])" to="/budgets">Budgets</RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'TREASURER', 'MEMBERSHIP'])" to="/reference-data">Reference Data</RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'TREASURER', 'PASTOR', 'VIEWER'])" to="/reports">Reports</RouterLink>
        <RouterLink v-if="hasAny(['ADMIN', 'MEMBER'])" to="/profile">My Profile</RouterLink>
      </nav>
    </aside>
    <section class="content">
      <slot />
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getChurchInformation, type ChurchInformation } from '../api/churchInformation';
import { authState, type Role } from '../auth/authStore';

const churchInfo = ref<ChurchInformation | null>(null);

onMounted(async () => {
  try {
    churchInfo.value = await getChurchInformation();
  } catch {
    churchInfo.value = null;
  }
});

function hasAny(roles: Role[]) {
  return authState.currentUser?.roles.some((role) => roles.includes(role)) ?? false;
}
</script>
```

- [ ] **Step 4: Update shared layout styles**

In `frontend/src/styles/main.css`, replace the existing `.layout`, `.sidebar`, `.sidebar a`, `.sidebar a.router-link-active`, and `.content` blocks with:

```css
.layout {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
}

.sidebar {
  display: grid;
  align-content: start;
  gap: 18px;
  padding: 18px 14px;
  background: linear-gradient(180deg, #023544 0%, #052d3b 55%, #082636 100%);
  color: white;
}

.sidebar-brand {
  display: grid;
  justify-items: center;
  gap: 10px;
  padding: 8px 8px 18px;
}

.sidebar-brand img,
.sidebar-logo-fallback {
  width: 72px;
  height: 72px;
  object-fit: contain;
}

.sidebar-logo-fallback {
  display: grid;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.42);
  border-radius: 8px;
  color: white;
  font-weight: 700;
}

.sidebar h1 {
  margin: 0;
  font-size: 1.15rem;
  text-align: center;
}

.sidebar-nav {
  display: grid;
  gap: 8px;
}

.sidebar a {
  color: white;
  text-decoration: none;
  padding: 11px 12px;
  border-radius: 6px;
}

.sidebar a.router-link-active {
  background: linear-gradient(90deg, #048f93, #087d8f);
}

.content {
  min-width: 0;
  padding: 20px;
}
```

- [ ] **Step 5: Run the AppLayout tests**

Run:

```bash
cd frontend
npm test -- --run src/layouts/AppLayout.test.ts
```

Expected: PASS.

- [ ] **Step 6: Commit the shared menu refresh**

Run:

```bash
git add frontend/src/api/churchInformation.ts frontend/src/layouts/AppLayout.vue frontend/src/layouts/AppLayout.test.ts frontend/src/styles/main.css
git commit -m "Refresh shared app navigation"
```

---

### Task 3: Refresh Dashboard Header and Cards

**Files:**
- Modify: `frontend/src/views/DashboardView.test.ts`
- Modify: `frontend/src/views/DashboardView.vue`
- Test: `frontend/src/views/DashboardView.test.ts`

**Interfaces:**
- Consumes:
  - `getChurchInformation(): Promise<ChurchInformation>` from Task 1.
  - Existing dashboard APIs and role checks from Dashboard V1.
- Produces:
  - Dashboard hero with banner, church info, user initials, role label.
  - No dashboard Quick Links panel.
  - Refreshed card CSS local to `DashboardView.vue`.

- [ ] **Step 1: Update DashboardView tests for branding and removed quick links**

In `frontend/src/views/DashboardView.test.ts`, add this import:

```ts
import { getChurchInformation } from '../api/churchInformation';
```

Add this mock after the existing API mocks:

```ts
vi.mock('../api/churchInformation', () => ({
  getChurchInformation: vi.fn().mockResolvedValue({
    name: 'Grace Community Church',
    address: '123 Church Street, Toronto, ON M1A 1A1',
    contactInfo: '416-555-0100',
    treasurerName: 'Daniel Kim',
    bannerPath: '/branding/church-banner.png',
    logPath: '/branding/church_logo.png',
  }),
}));
```

Add this mock constant after the existing constants:

```ts
const churchInformationMock = vi.mocked(getChurchInformation);
```

Add this default setup inside `beforeEach`:

```ts
    churchInformationMock.mockResolvedValue({
      name: 'Grace Community Church',
      address: '123 Church Street, Toronto, ON M1A 1A1',
      contactInfo: '416-555-0100',
      treasurerName: 'Daniel Kim',
      bannerPath: '/branding/church-banner.png',
      logPath: '/branding/church_logo.png',
    });
```

In the admin test, remove the `quickLinks` assertions and replace them with:

```ts
    expect(await screen.findByText('Faith, Hope, Love')).toBeTruthy();
    expect(screen.getByText('Grace Community Church')).toBeTruthy();
    expect(screen.getByText('123 Church Street, Toronto, ON M1A 1A1')).toBeTruthy();
    expect(screen.getByText('416-555-0100')).toBeTruthy();
    expect(screen.getByText('Treasurer: Daniel Kim')).toBeTruthy();
    expect(screen.getByText('AD')).toBeTruthy();
    expect(screen.getByText('ADMIN')).toBeTruthy();
    expect(screen.queryByRole('navigation', { name: 'Dashboard quick links' })).toBeNull();
```

In the treasurer, membership, and viewer tests, remove the `within(screen.getByRole('navigation', { name: 'Dashboard quick links' }))` lines and remove all `quickLinks.*` assertions. Keep the section visibility assertions.

- [ ] **Step 2: Run the dashboard test to verify it fails**

Run:

```bash
cd frontend
npm test -- --run src/views/DashboardView.test.ts
```

Expected: FAIL because the dashboard does not yet render the branding hero and still renders Quick Links.

- [ ] **Step 3: Update DashboardView template**

In `frontend/src/views/DashboardView.vue`, replace the current page header block and remove the entire Quick Links panel.

Use this header block at the top inside `.dashboard-page`:

```vue
      <section class="dashboard-hero">
        <div class="banner-panel" :class="{ 'empty-banner': !churchInfo?.bannerPath }">
          <img v-if="churchInfo?.bannerPath" :src="churchInfo.bannerPath" alt="" />
          <div class="banner-copy">
            <h2>Faith, Hope, Love</h2>
            <p>Serving our community together</p>
          </div>
        </div>

        <aside class="church-info-panel">
          <div>
            <h2>{{ churchName }}</h2>
            <p>{{ churchAddress }}</p>
            <p>{{ churchContactInfo }}</p>
            <p>{{ treasurerLabel }}</p>
          </div>
          <div class="user-role">
            <span>{{ userInitials }}</span>
            <strong>{{ currentRoleLabel }}</strong>
          </div>
        </aside>
      </section>
```

Add a class to each major card section:

```vue
      <section v-if="canViewReports" class="panel dashboard-panel accent-offering">
```

```vue
      <section v-if="canViewReports" class="panel dashboard-panel accent-fiscal">
```

```vue
        <section v-if="canViewMembership" class="panel dashboard-panel accent-membership">
```

```vue
        <section v-if="canViewFinance" class="panel dashboard-panel accent-finance">
```

- [ ] **Step 4: Update DashboardView script**

In `frontend/src/views/DashboardView.vue`, add this import:

```ts
import { getChurchInformation, type ChurchInformation } from '../api/churchInformation';
```

Add this state and computed block after the existing error refs:

```ts
const churchInfo = ref<ChurchInformation | null>(null);

const churchName = computed(() => churchInfo.value?.name || 'Church Operations');
const churchAddress = computed(() => churchInfo.value?.address || 'Address not configured');
const churchContactInfo = computed(() => churchInfo.value?.contactInfo || 'Contact info not configured');
const treasurerLabel = computed(() => `Treasurer: ${churchInfo.value?.treasurerName || 'Not configured'}`);
const currentRoleLabel = computed(() => authState.currentUser?.roles[0] ?? 'User');
const userInitials = computed(() => {
  const source = authState.currentUser?.displayName || authState.currentUser?.primaryEmail || 'User';
  return source
    .split(/[\s@.]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('');
});
```

Add this call inside `onMounted` before the role-specific loads:

```ts
  void loadChurchInformation();
```

Add this function before `setError`:

```ts
async function loadChurchInformation() {
  try {
    churchInfo.value = await getChurchInformation();
  } catch {
    churchInfo.value = null;
  }
}
```

Remove the unused `RouterLink` import from `vue-router`.

- [ ] **Step 5: Replace DashboardView scoped styles**

In `frontend/src/views/DashboardView.vue`, replace the entire `<style scoped>` block with:

```css
.dashboard-page {
  gap: 18px;
}

.dashboard-hero {
  display: grid;
  grid-template-columns: minmax(360px, 1.4fr) minmax(320px, 0.9fr);
  gap: 0;
  overflow: hidden;
  border: 1px solid #d8dee6;
  border-radius: 8px;
  background: white;
}

.banner-panel {
  position: relative;
  min-height: 190px;
  overflow: hidden;
  background: #123047;
}

.banner-panel img {
  width: 100%;
  height: 100%;
  min-height: 190px;
  display: block;
  object-fit: cover;
}

.banner-panel::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(18, 48, 71, 0.72), rgba(18, 48, 71, 0.12));
}

.empty-banner {
  min-height: 190px;
}

.banner-copy {
  position: absolute;
  left: 28px;
  bottom: 28px;
  z-index: 1;
  color: white;
}

.banner-copy h2 {
  margin: 0;
  font-size: 2rem;
}

.banner-copy p {
  margin: 8px 0 0;
}

.church-info-panel {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 24px;
  background: white;
}

.church-info-panel h2 {
  margin: 0 0 12px;
}

.church-info-panel p {
  margin: 10px 0;
  color: #344054;
}

.user-role {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  white-space: nowrap;
}

.user-role span {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #0b6fd3;
  color: white;
  font-weight: 700;
}

.user-role strong {
  padding-top: 10px;
  font-size: 0.9rem;
}

.dashboard-panel {
  display: grid;
  gap: 14px;
  box-shadow: 0 10px 28px rgba(16, 24, 40, 0.05);
}

.accent-offering {
  border-color: #f3c56b;
}

.accent-fiscal {
  border-color: #9fc9f5;
}

.accent-membership {
  border-color: #aad8b3;
}

.accent-finance {
  border-color: #f3b38f;
}

.panel-title-row {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 16px;
}

.panel-title-row p,
.dashboard-panel > p {
  margin: 6px 0 0;
  color: #5b6778;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.dashboard-grid.compact {
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
}

.dashboard-two-column {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 18px;
}

.metric-card {
  min-height: 96px;
  display: grid;
  align-content: center;
  gap: 6px;
  border: 1px solid #edf0f4;
  border-radius: 8px;
  padding: 14px;
  background: linear-gradient(135deg, #ffffff, #fbfcfe);
}

.metric-card span {
  color: #5b6778;
  font-size: 0.92rem;
}

.metric-card strong {
  color: #1f2933;
  font-size: 1.45rem;
  line-height: 1.2;
}

.metric-card small {
  color: #667085;
}

@media (max-width: 900px) {
  .dashboard-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .dashboard-two-column {
    grid-template-columns: 1fr;
  }

  .church-info-panel {
    display: grid;
  }
}
```

- [ ] **Step 6: Run the dashboard test**

Run:

```bash
cd frontend
npm test -- --run src/views/DashboardView.test.ts
```

Expected: PASS.

- [ ] **Step 7: Commit the dashboard visual refresh**

Run:

```bash
git add frontend/src/views/DashboardView.vue frontend/src/views/DashboardView.test.ts
git commit -m "Apply branded dashboard visual refresh"
```

---

### Task 4: Full Frontend Verification

**Files:**
- Modify: none
- Test: all frontend tests and production build

**Interfaces:**
- Consumes:
  - Church information API client from Task 1.
  - Shared layout refresh from Task 2.
  - Dashboard visual refresh from Task 3.
- Produces:
  - Verified frontend test/build result.

- [ ] **Step 1: Run all frontend tests**

Run:

```bash
cd frontend
npm test
```

Expected: PASS for all frontend test files.

- [ ] **Step 2: Run the frontend production build**

Run:

```bash
cd frontend
npm run build
```

Expected: PASS with Vite build output and no TypeScript errors.

- [ ] **Step 3: Commit verification fixes if needed**

If verification required source or test fixes, commit only those files:

```bash
git add frontend/src/api/churchInformation.ts frontend/src/layouts/AppLayout.vue frontend/src/layouts/AppLayout.test.ts frontend/src/views/DashboardView.vue frontend/src/views/DashboardView.test.ts frontend/src/styles/main.css
git commit -m "Fix dashboard visual refresh verification"
```

If no source or test files changed, skip this commit.

---

## Self-Review

Spec coverage:

- Shared menu visual shell and logo: Task 2.
- Church information API use: Task 1, Task 2, Task 3.
- Dashboard banner/church info/user role: Task 3.
- Dashboard Quick Links removal: Task 3.
- Existing role-aware dashboard sections preserved: Task 3 tests continue to cover this.
- Full frontend verification: Task 4.

Red-flag wording scan:

- No incomplete task language remains.
- No unspecified implementation steps remain.

Type consistency:

- `ChurchInformation` fields match `ChurchInformationController.ChurchInformationResponse`.
- Dashboard role values match `Role` from `authStore.ts`.
- Existing report, member, and finance API types are unchanged.

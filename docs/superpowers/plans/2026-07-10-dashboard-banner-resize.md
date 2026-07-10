# Dashboard Banner Resize Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce the desktop dashboard banner by approximately 37% from its original size in both dimensions while displaying the complete image at its natural aspect ratio.

**Architecture:** Adjust only the desktop grid proportions in `DashboardView.vue`. The banner image remains width-driven with automatic height, and the existing mobile breakpoint continues to stack both hero sections at full width.

**Tech Stack:** Vue 3, scoped CSS, Vitest, Vue Testing Library, Vite

## Global Constraints

- Preserve the banner image's natural aspect ratio.
- Do not crop, stretch, or letterbox the banner.
- Keep the existing single-column layout below 900px.
- Do not modify other dashboard sections or shared page layouts.

---

### Task 1: Resize the Desktop Dashboard Banner

**Files:**
- Modify: `frontend/src/views/DashboardView.vue:332`
- Test: `frontend/src/views/DashboardView.test.ts`

**Interfaces:**
- Consumes: the existing `.dashboard-hero`, `.banner-panel`, and `.banner-panel img` rules
- Produces: a desktop hero grid whose banner column is approximately 70% of its previous proportional width

- [ ] **Step 1: Confirm the current presentation baseline**

Run the dashboard tests before editing:

```bash
cd frontend
npx vitest run src/views/DashboardView.test.ts
```

Expected: the existing dashboard tests pass. This CSS-only layout change has no reliable geometry in jsdom, so regression verification uses the existing component suite plus production compilation and browser inspection.

- [ ] **Step 2: Reduce the desktop banner column proportion**

In `frontend/src/views/DashboardView.vue`, replace the desktop grid columns with:

```css
.dashboard-hero {
  display: grid;
  grid-template-columns: minmax(234px, 0.625fr) minmax(320px, 1fr);
  gap: 0;
  overflow: hidden;
  border: 1px solid #d8dee6;
  border-radius: 8px;
  background: white;
}
```

This changes the banner from 60.9% of the original hero width to 38.5%, a 36.8% proportional reduction overall and approximately 10% less than the preceding version. Leave the existing `@media (max-width: 900px)` override unchanged.

- [ ] **Step 3: Run the frontend test suite**

```bash
cd frontend
npm test -- --run
```

Expected: 6 test files and 24 tests pass.

- [ ] **Step 4: Build the production frontend**

```bash
cd frontend
npm run build
```

Expected: TypeScript validation and the Vite production build complete successfully.

- [ ] **Step 5: Verify the rendered dashboard**

Open the dashboard at a desktop viewport and confirm:

- the entire banner image is visible;
- the image has no stretching, cropping, or empty internal bands;
- the banner width and resulting height are approximately 10% smaller than the preceding version;
- church information uses the recovered horizontal space;
- below 900px, the banner and church information still stack.

- [ ] **Step 6: Commit only the dashboard source change**

```bash
git add frontend/src/views/DashboardView.vue
git commit -m "Resize dashboard banner panel"
```

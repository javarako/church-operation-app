# English User Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a task-based English Church Operations user guide for all roles as a polished Word document and matching PDF with current application screenshots.

**Architecture:** Capture the live Docker application at a consistent desktop viewport using current branding and sample records. Build a role-aware manual from implemented routes and visible controls, generate DOCX/PDF with the `compact_reference_guide` design preset, and render every page to images for iterative visual QA.

**Tech Stack:** Vue 3 application, Docker Compose, in-app browser automation, Python `python-docx`, LibreOffice rendering, Poppler page rendering, Word DOCX, PDF

## Global Constraints

- Cover Admin, Treasurer, Pastor, Membership, Viewer, and Member roles.
- Organize by task and place an **Available to** role line at the start of every function section.
- Use current church branding and sample records.
- Include login, forced first-login password change, forgot password, reset password, dashboard, members, offerings, finance, budgets, reference data, reports, My Profile, and logout.
- Do not document controls or workflows that are not implemented.
- Store final deliverables under `docs/user-guide/`.
- Deliver both `.docx` and `.pdf`.
- Render and inspect every DOCX page before delivery.

---

### Task 1: Capture the Live Application Screens

**Files:**
- Create: `docs/user-guide/screenshots/01-login.png`
- Create: `docs/user-guide/screenshots/02-forgot-password.png`
- Create: `docs/user-guide/screenshots/03-reset-password.png`
- Create: `docs/user-guide/screenshots/04-dashboard.png`
- Create: `docs/user-guide/screenshots/05-members.png`
- Create: `docs/user-guide/screenshots/06-offerings.png`
- Create: `docs/user-guide/screenshots/07-finance.png`
- Create: `docs/user-guide/screenshots/08-budgets.png`
- Create: `docs/user-guide/screenshots/09-reference-data.png`
- Create: `docs/user-guide/screenshots/10-reports.png`
- Create: `docs/user-guide/screenshots/11-profile.png`
- Create: `docs/user-guide/screenshots/12-change-password.png`

**Interfaces:**
- Consumes: local Docker application at `http://localhost:5173` and Mailpit at `http://localhost:8025`
- Produces: consistently sized PNG screenshots used by the document builder

- [ ] **Step 1: Start the local application**

```bash
docker compose up --build -d
```

Expected: MongoDB, backend, frontend, and Mailpit are available.

- [ ] **Step 2: Set a consistent browser viewport**

Use a `1440 × 1000` viewport. Capture the application content without browser chrome. Keep the full sidebar visible on authenticated pages.

- [ ] **Step 3: Capture public authentication screens**

Capture login, forgot-password, reset-password, and forced change-password screens. Use a safe sample reset token in the URL screenshot and do not show a real active token.

- [ ] **Step 4: Capture authenticated task screens**

Sign in as Admin and capture dashboard, members, offerings, finance, budgets, reference data, reports, and My Profile. Use each screen's default stable state and current sample records.

- [ ] **Step 5: Verify screenshot quality**

Inspect all 12 PNG files. Confirm visible text is readable, no controls overlap, branding is present, and no password or live reset token appears.

---

### Task 2: Draft the Role-Aware Manual Content

**Files:**
- Create: `docs/user-guide/user-guide-content.md`

**Interfaces:**
- Consumes: Vue routes, role definitions, view labels, live screenshots, and approved design specs
- Produces: complete English source content for DOCX generation

- [ ] **Step 1: Create the manual outline**

Use these chapters: About This Guide; Roles and Access; Getting Started; Dashboard; Member Information; Offering Management; Financial Management; Budget Management; Reference Data; Reports; My Profile; Logout; Troubleshooting and Security Notes.

- [ ] **Step 2: Add exact access labels**

Use these route permissions:

```text
Dashboard: Admin, Treasurer, Pastor, Membership, Viewer
Members: Admin, Membership
Offerings: Admin, Treasurer
Finance: Admin, Treasurer
Budgets: Admin, Treasurer
Reference Data: Admin, Treasurer, Membership
Reports: Admin, Treasurer, Pastor, Viewer
My Profile: Admin, Member
Password reset and Logout: All users
```

- [ ] **Step 3: Write task procedures**

Write numbered actions for every major visible function, including add/edit/delete where implemented, filters, reference-dependent dropdowns, report criteria and exports, first-login password change, email reset, and logout.

- [ ] **Step 4: Add screenshot placement markers**

Insert markers such as `[[FIGURE:04-dashboard.png|Dashboard overview]]` immediately after the instructions they support.

- [ ] **Step 5: Audit content against the application**

Check every route and visible command. Remove any instruction that cannot be completed in the live UI and ensure every function has an **Available to** line.

---

### Task 3: Generate the Word Manual and PDF

**Files:**
- Create: `docs/user-guide/build_user_guide.py`
- Create: `docs/user-guide/Church Operations User Guide.docx`
- Create: `docs/user-guide/Church Operations User Guide.pdf`

**Interfaces:**
- Consumes: `user-guide-content.md`, screenshots, church logo, and banner assets
- Produces: branded DOCX and matching PDF

- [ ] **Step 1: Resolve the document design tokens**

Use the `compact_reference_guide` preset with US Letter portrait pages, 0.75-inch margins, Aptos/Arial-compatible fonts, dark teal headings, restrained blue accents, 10.5-point body text, readable numbered procedures, and figure captions. Use a branded cover with church logo, church name, title, version date, and a short audience line.

- [ ] **Step 2: Implement the document builder**

Use `python-docx` from the Codex workspace runtime. Create real Heading 1-3 styles, real numbered lists, page headers/footers with page numbers, an automatic table-of-contents field, role callouts, notes, figure captions, proportional image sizing, and deliberate page breaks between major chapters.

- [ ] **Step 3: Generate the DOCX**

Run the builder from `docs/user-guide/` and confirm the DOCX exists and is non-empty.

- [ ] **Step 4: Render the DOCX and emit PDF**

```bash
<workspace-python> <documents-skill>/render_docx.py \
  "docs/user-guide/Church Operations User Guide.docx" \
  --output_dir /tmp/church-user-guide-render \
  --emit_pdf
```

Copy the emitted PDF to `docs/user-guide/Church Operations User Guide.pdf` after visual approval.

---

### Task 4: Perform Full Visual and Structural QA

**Files:**
- Verify: `docs/user-guide/Church Operations User Guide.docx`
- Verify: `docs/user-guide/Church Operations User Guide.pdf`
- Verify: `/tmp/church-user-guide-render/page-*.png`

**Interfaces:**
- Consumes: rendered DOCX pages and PDF
- Produces: publication-ready user guide deliverables

- [ ] **Step 1: Inspect every rendered page at full resolution**

Check cover, contents, headings, procedures, role callouts, screenshots, captions, headers, footers, and page numbers. Reject clipping, overlap, tiny screenshots, awkward blank pages, split captions, and inconsistent spacing.

- [ ] **Step 2: Iterate on layout defects**

Adjust screenshot sizes, page breaks, keep-with-next settings, paragraph spacing, and image/caption placement in the builder. Regenerate and re-render after every layout-sensitive change.

- [ ] **Step 3: Run structural audits**

Run the document skill's heading, section, image, and accessibility audits. Confirm all screenshots have alt text, headings are correctly nested, and no personal author metadata remains.

- [ ] **Step 4: Verify PDF parity**

Render the final PDF pages and confirm page count and visible layout match the approved DOCX render.

- [ ] **Step 5: Stop local services**

```bash
docker compose down
```

Expected: containers stop without deleting the MongoDB volume.

- [ ] **Step 6: Report deliverables**

Provide direct links to the final `.docx` and `.pdf`, summarize coverage, and state that every page passed rendered visual inspection.

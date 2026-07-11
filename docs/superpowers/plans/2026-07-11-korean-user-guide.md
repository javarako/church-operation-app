# Korean User Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a complete Korean Word and PDF edition of the existing English Church Operations user guide.

**Architecture:** Keep the English artifacts unchanged. Add Korean Markdown content and a Korean builder derived from the verified English generator, with Korean typography and localized document furniture.

**Tech Stack:** Markdown, Python, python-docx, LibreOffice rendering, Documents skill audit scripts

## Global Constraints

- Cover title is `교회운영 메뉴얼`.
- Preserve all current branding and 12 screenshots.
- Keep exact English UI labels where they identify visible controls.
- Use a locally installed Korean OpenType font verified through the LibreOffice render.

---

### Task 1: Translate the guide content

**Files:**
- Create: `docs/user-guide/user-guide-content-ko.md`

- [ ] Translate every English section, role table, workflow, note, and screenshot caption.
- [ ] Preserve Markdown structure and all 12 figure markers.
- [ ] Compare headings and figure counts with the English source.

### Task 2: Build the Korean Word document

**Files:**
- Create: `docs/user-guide/build_user_guide_ko.py`
- Create: `docs/user-guide/교회운영 메뉴얼.docx`

- [ ] Reuse the approved compact reference guide geometry and branding.
- [ ] Localize cover, contents, header, footer, metadata, and typography.
- [ ] Generate the Korean DOCX and scrub private document metadata.

### Task 3: Render, inspect, and deliver

**Files:**
- Create: `docs/user-guide/교회운영 메뉴얼.pdf`

- [ ] Render the DOCX to PNG pages and PDF.
- [ ] Inspect every page and correct layout defects.
- [ ] Run structural and accessibility audits.
- [ ] Verify final DOCX and PDF files and report the result.

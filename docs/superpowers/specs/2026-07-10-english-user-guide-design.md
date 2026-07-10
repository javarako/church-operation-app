# English User Guide Design

## Goal

Create a polished English user guide for the Church Operations application covering all user roles and all currently available functions.

## Deliverables

- `docs/user-guide/Church Operations User Guide.docx`
- `docs/user-guide/Church Operations User Guide.pdf`
- Supporting screenshots stored under `docs/user-guide/screenshots/`

## Audience

The guide serves users assigned any of these roles: Admin, Treasurer, Pastor, Membership, Viewer, and Member.

## Information Architecture

Use a task-based structure rather than separate role chapters. Organize the guide around the application's functions:

1. Getting started, login, and first-login password change
2. Dashboard and navigation
3. Member information
4. Offering management
5. Financial management
6. Budget management
7. Reference data
8. Reports
9. My Profile
10. Role and access reference

Each function section begins with an **Available to** line listing the roles that can access it. Procedures use numbered steps, short notes, and screenshots placed near the relevant instructions.

## Screenshot Design

Capture the current application using its existing church branding and sample records. Use a consistent desktop viewport and crop screenshots to the relevant application area while retaining enough navigation context for orientation. Include the login page, dashboard, and each major function screen. Use callouts only when they materially clarify a control or workflow.

## Document Design

Create a professional Word manual using the `compact_reference_guide` design preset, adapted for a user handbook. Use a branded cover, concise table of contents, consistent heading hierarchy, page numbers, figure captions, restrained church-brand colors, and readable screenshots. Export the verified Word document to a matching PDF.

## Content Source

Use the implemented Vue routes, visible labels, role definitions, application behavior, project specifications, and live application screens as the source of truth. Do not describe unimplemented controls or workflows.

## Verification

- Confirm every current route appears in the guide.
- Confirm role labels match the application's route permissions.
- Confirm instructions match the live UI.
- Render the DOCX to page images and inspect every page for clipping, overlap, unreadable screenshots, and poor page breaks.
- Verify the final PDF matches the approved DOCX layout.

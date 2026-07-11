# Korean User Guide Design

## Purpose

Create a Korean companion edition of the finalized English Church Operations user guide.

## Deliverables

- `docs/user-guide/교회운영 메뉴얼.docx`
- `docs/user-guide/교회운영 메뉴얼.pdf`
- Korean source content and a reproducible Korean document builder.

## Translation Style

- Use the approved cover title `교회운영 메뉴얼`.
- Translate instructions, descriptions, role responsibilities, notes, and captions into natural Korean.
- Retain English UI labels in bold where users must match a visible control in the English application.
- Preserve role names in English and add Korean descriptions where helpful.
- Do not add features or access rights absent from the English guide.

## Visual Design

- Preserve the English edition's branding, palette, cover composition, screenshots, tables, callouts, headers, and footers.
- Use a locally installed Korean OpenType font that renders Hangul reliably in LibreOffice; verify the exact font through rendered pages.
- Reuse all 12 current screenshots without alteration.
- Allow pagination to change where Korean text requires different line wrapping.

## Quality Gates

- Verify all English source sections and figure markers have Korean counterparts.
- Render the DOCX to page PNGs and inspect every page for missing glyphs, clipping, overlap, and poor page breaks.
- Produce the PDF from the visually approved DOCX.
- Run heading, section, image, accessibility, and privacy checks before delivery.

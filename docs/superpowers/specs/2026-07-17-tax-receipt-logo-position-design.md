# Tax Receipt Logo Position Design

## Goal

Increase the tax receipt logo by 20% from its current rendered size and center it vertically between the two header divider lines.

## Layout

- Preserve the logo's source aspect ratio.
- Increase the maximum width from 93.6 points to 112.32 points.
- Increase the maximum height from 57.2 points to 68.64 points.
- Define the receipt header band's upper and lower divider positions explicitly.
- Calculate the logo's vertical position from the center of that band instead of using a fixed Y offset.
- Apply identical logo geometry to both half-page receipt copies.
- Position the church information 10 points after the logo's rendered right edge so the two elements cannot overlap.
- Keep the remaining receipt-detail positions unchanged.

## Verification

- Extend the PDF stream test to assert both logo transforms.
- Verify the rendered width is 112.32 points.
- Verify both logo copies share the same relative vertical center between their header divider lines.
- Verify both church-name lines begin 10 points after the rendered logo.
- Run the complete tax receipt PDF test class.

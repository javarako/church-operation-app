# Dashboard Banner Resize Design

## Goal

Reduce the dashboard banner panel by approximately 37% from its original size while continuing to display the full banner image at its natural aspect ratio.

## Design

Change the desktop dashboard hero grid so the banner column is approximately 37% smaller than its original proportional width, including an additional 10% reduction after the initial 30% adjustment. The church information panel receives the recovered width. Because the banner image determines its own height from its natural aspect ratio, its height decreases by the same proportion without cropping, stretching, or letterboxing.

Keep the existing single-column responsive layout below 900px so the banner remains readable on narrow screens.

## Scope

Only the dashboard hero column proportions will change. Banner content, church information, mobile stacking, and other page layouts remain unchanged.

## Verification

Run the frontend test suite and production build. Confirm that the desktop banner remains fully visible and is approximately 10% smaller than the preceding version in both dimensions.

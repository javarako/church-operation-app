# Dashboard Corrections Design

## Scope

Correct the five dashboard issues marked in `dashboard.png` without changing other pages or the paused backup/restore work.

## Design

- Increase the banner column width so the configured banner grows at its original aspect ratio and fills the hero height without cropping.
- Display Lucide icons beside the church address, contact information, and treasurer.
- Count members registered in the current month from `createdAt`; for legacy MongoDB records without that field, use the timestamp encoded in a valid ObjectId.
- Remove the summary cards' forced minimum height and align the grid to compact content height.
- Use the coming Sunday, or today when today is Sunday, as the dashboard's current offering Sunday. Show that Sunday plus the preceding eleven Sundays in the trend.

## Verification

- Backend tests cover legacy ObjectId registration dates and the coming-Sunday offering window.
- Frontend tests cover the three church-information icons and existing dashboard values.
- Focused backend/frontend suites and the frontend production build must pass.


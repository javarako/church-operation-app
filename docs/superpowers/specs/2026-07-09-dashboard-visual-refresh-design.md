# Dashboard Visual Refresh Design

## Goal

Apply the approved dashboard mockup style to the current app while keeping the existing page structure. The refresh should make the dashboard feel branded and operational, using the church logo, banner image, church information, and current user role that already exist in the system.

This slice focuses on the global left menu and the top/dashboard summary area. It does not recreate the lower tabbed dashboard workspace from the mockup because Members, Offerings, Finance, Budgets, Reference Data, Reports, and Profile already exist as real pages.

## Scope

Included:

- Restyle the shared left menu in `AppLayout` so every page keeps the same dashboard-style navigation shell.
- Show the configured church logo at the top of the left menu.
- Load church branding and information from `/api/church-information`.
- Add a dashboard hero row with the configured banner image.
- Show church name, address, contact info, treasurer name, current user initials, and role display beside the banner.
- Restyle existing dashboard summary cards to match the mockup direction.
- Remove the dashboard Quick Links panel because the left menu already provides navigation.
- Keep role-aware dashboard sections from Dashboard V1.

Excluded:

- Recreating the mockup's lower tabbed workspace inside the dashboard.
- Adding icons from a new dependency.
- Adding a new backend endpoint.
- Changing page routing or role permissions.
- Changing the login page design in this slice.

## Data Sources

Church information comes from the existing public endpoint:

- `GET /api/church-information`

Expected fields:

- `name`
- `address`
- `contactInfo`
- `treasurerName`
- `bannerPath`
- `logPath`

The current user comes from `authState.currentUser`:

- `displayName`
- `primaryEmail`
- `roles`

Dashboard totals continue to use the existing Dashboard V1 APIs:

- Weekly offering report.
- Financial actual-vs-budget report.
- Members.
- Finance transactions.

## Layout Design

### Shared Left Menu

The left menu should become a polished dark teal navigation panel similar to the mockup:

- Logo image centered at the top using `churchInformation.logPath`.
- App name below the logo.
- Existing role-aware menu items remain unchanged in behavior.
- Active route uses a brighter teal highlight.
- Menu stays visible on every authenticated page through the existing `AppLayout`.

The menu should still work if the logo image is missing. In that case, show a simple text fallback using the app name.

### Dashboard Header

The dashboard top area becomes a two-column hero panel:

- Left side: banner image using `churchInformation.bannerPath`.
- Text overlay on the banner: `Faith, Hope, Love` and `Serving our community together`.
- Right side: church information panel showing:
  - Church name.
  - Address.
  - Contact info.
  - Treasurer name.
  - User initials.
  - Current role label.

The current role label should use the first role in the signed-in user's role list. If no role exists, display `User`.

### Summary Cards

Keep the existing dashboard content, but restyle it into compact mockup-like cards:

- Offering card: this week total, month-to-date, and year-to-date.
- Fiscal card: budgeted income, actual income, budgeted expense, actual expense, and net actual.
- Membership card: total, active, and locked accounts.
- Finance card: recent income, recent expense, and transaction count.

Cards should use restrained accent colors and thin borders. They should remain readable on desktop and mobile.

### Removed Content

Remove the Dashboard V1 Quick Links panel from the dashboard page. The shared left menu remains the navigation source.

## Frontend Architecture

Add a small API client for church information if one does not already exist:

- `frontend/src/api/churchInformation.ts`

This client returns a typed `ChurchInformation` object and uses the existing `getJson` helper.

Update `AppLayout.vue` to load church information once on mount. It should handle request failure quietly and keep the menu usable.

Update `DashboardView.vue` to load church information for the hero area. This duplicates one small request with `AppLayout` in this slice to keep the files independent and avoid introducing shared state.

## Error Handling

- If church information fails to load in `AppLayout`, keep the menu visible with `Church Operations` text.
- If church information fails to load in `DashboardView`, show the dashboard with a neutral header and the existing cards.
- If banner or logo image paths are missing, hide the image areas gracefully.
- Existing dashboard section error behavior stays unchanged.

## Testing

Frontend tests:

- `AppLayout` shows the logo/app name when church information loads.
- `DashboardView` renders the banner, church information, user initials, and current role.
- `DashboardView` no longer renders the Quick Links panel.
- Existing Dashboard V1 role-aware content tests continue to pass.
- Dashboard still shows section-level errors without hiding other available sections.

Backend tests:

- No new backend tests are required because `/api/church-information` already exists.

## Acceptance Criteria

- The left menu visually matches the approved mockup direction and remains visible on all authenticated pages.
- The dashboard top area shows the configured banner and church information.
- Current user initials and role display appear beside the banner.
- Dashboard summary cards use the refreshed visual style.
- Dashboard Quick Links are removed.
- Existing role-based menu/page access behavior is unchanged.
- Frontend tests and production build pass.

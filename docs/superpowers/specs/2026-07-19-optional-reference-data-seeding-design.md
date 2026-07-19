# Optional Reference Data Seeding Design

## Goal

Make startup seeding of default reference data optional through
`application.yml`. Seeding is disabled unless the church explicitly enables
it.

## Configuration

Add the following application setting:

```yaml
church:
  reference-data:
    seed-defaults: ${CHURCH_REFERENCE_DATA_SEED_DEFAULTS:false}
```

The default value is `false`. Deployments can enable seeding by setting either
the YAML property to `true` or the
`CHURCH_REFERENCE_DATA_SEED_DEFAULTS=true` environment variable.

## Startup Behavior

Apply `@ConditionalOnProperty` to `ReferenceDataBootstrapRunner` with:

- Prefix: `church.reference-data`
- Property name: `seed-defaults`
- Required value: `true`
- Missing property: disabled

When enabled, the runner invokes the existing
`ReferenceDataService.seedDefaults()` method. Existing type-and-code records
remain unchanged, and only missing defaults are inserted.

When disabled or omitted, the runner is not created and no default reference
data is inserted. Existing reference data is never deleted, disabled, or
modified.

## Testing

Add focused Spring context tests proving:

- The runner exists when `church.reference-data.seed-defaults=true`.
- The runner does not exist when the property is `false`.
- The runner does not exist when the property is missing.

Keep the existing `ReferenceDataService` seed behavior and its tests unchanged.


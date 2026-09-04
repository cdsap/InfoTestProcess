# Changelog

## Unreleased

### Experimental — GBOS opt-in Develocity projection

- Added an **experimental, opt-in** GBOS (Gradle Build Observability Schema)
  Develocity projection. Enable with
  `infoTestProcess.gbos.develocity.enabled=true`.
- **No default behavior change.** With the property unset or `false`, builds
  continue to emit only the existing `testProcess.*` custom values, scan tags,
  and `statsTestTasks.json` output.
- Legacy keys and tags are **retained** for this major version while GBOS is
  dual-published when enabled.
- Schema validation of generated GBOS observation / report / NDJSON / Develocity
  examples runs in tests/CI via a vendored snapshot of
  [`cdsap/build-observability-schema`](https://github.com/cdsap/build-observability-schema);
  the plugin does not depend on JSON Schema at runtime.
- Adoption notes: [docs/gbos.md](docs/gbos.md).

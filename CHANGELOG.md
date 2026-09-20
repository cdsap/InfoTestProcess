# Changelog

## Unreleased

### Build

- Moved plugin sources into a `:plugin` subproject so the root project holds
  build-wide configuration only (Gradle best practice: no sources in root).

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
  examples runs in tests/CI against the released
  [`io.github.cdsap:build-observability-schema`](https://github.com/cdsap/build-observability-schema)
  Maven Central artifact (test-only dependency + Draft 2020-12 JSON Schema
  validator); schema resources are not vendored and are not on the plugin runtime
  classpath.
- Adoption notes: [docs/gbos.md](docs/gbos.md).

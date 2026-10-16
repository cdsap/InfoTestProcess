# Changelog

## Unreleased

### Build

- Moved plugin sources into a `:plugin` subproject so the root project holds
  build-wide configuration only (Gradle best practice: no sources in root).

### Experimental — GBOS opt-in Develocity projection

- Added an **experimental, opt-in** GBOS (Gradle Build Observability Schema)
  file-output feature. Enable `infoTestProcess.gbos.enabled=true` to write both
  local file sinks (`gbos.json` and `gbos.ndjson`).
- Develocity is detected automatically. When it is present and legacy
  reporting is enabled, the Build Scan receives both the existing
  `testProcess.*` custom values and the GBOS projection. The GBOS file-sink
  property does not control this projection.
- The independent legacy-output switch is
  `infoTestProcess.develocity.enabled` (default `true` with Develocity). Set it
  to `false` to omit both legacy and GBOS Develocity custom values and use the
  JSON report path instead.
- Schema validation of generated GBOS observation / report / NDJSON / Develocity
  examples runs in tests/CI against the released
  [`io.github.cdsap:build-observability-schema`](https://github.com/cdsap/build-observability-schema)
  Maven Central artifact (test-only dependency + Draft 2020-12 JSON Schema
  validator); schema resources are not vendored and are not on the plugin runtime
  classpath.
- Adoption notes: [docs/gbos.md](docs/gbos.md).

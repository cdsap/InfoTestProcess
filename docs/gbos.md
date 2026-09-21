# GBOS adoption (experimental / opt-in)

Info Test Process can publish [Gradle Build Observability Schema (GBOS)](https://github.com/cdsap/build-observability-schema) observations to Develocity or local report files.

GBOS support is **experimental** and **disabled by default**. When the Develocity GBOS projection is enabled, it replaces legacy `testProcess.*` Build Scan custom values for that build. When it is disabled, the legacy custom values remain unchanged. Existing scan tags are retained in either mode.

## Opt-in flags

Set Gradle properties (for example in `gradle.properties` or via `-P`):

| Property | Default | Effect |
|---|---|---|
| `infoTestProcess.gbos.develocity.enabled` | `false` | When `true` and Develocity is applied, publish producer-scoped GBOS custom values instead of legacy `testProcess.*` custom values |

Example:

```properties
infoTestProcess.gbos.develocity.enabled=true
```

With no GBOS properties set, builds behave exactly as before: only legacy `testProcess.*` custom values / tags (Develocity) or `statsTestTasks.json` (no Develocity).

## Compatibility and legacy key retention

- **Default behavior is unchanged.** Opt-in is required for any GBOS emission.
- **Legacy keys remain the default** when the Develocity GBOS opt-in is off. With it on, only GBOS custom values are emitted to Develocity; existing scan tags (`tests:cpu-heavy`, `tests:near-oom`, `tests:jit-bound`, `tests:no-snapshot`) keep their current behavior. The non-Develocity `statsTestTasks.json` output is unaffected.
- GBOS observations use base units (bytes, seconds, `{core}`, …) and store identity (PID, task path, executor) in observation `attributes`, never in custom-value names.
- Missing runtime snapshots omit unavailable measurements and may emit diagnostics instead of sentinel values such as `0` / `-1`.
- Schema validation runs in plugin **tests/CI only** against the released
  `io.github.cdsap:build-observability-schema` Maven Central artifact (Draft 2020-12
  JSON Schema via a test-only validator). Schema/registry resources are loaded from
  that artifact's classpath — they are **not** vendored under `src/test/resources`
  and are **not** on the plugin runtime classpath.

## Develocity projection

When enabled, the plugin emits:

- The global `gbos.schema=1.0.0` header, plus producer-scoped metadata:
  `gbos.v1.producer.info_test_process.name=info-test-process` and
  `gbos.v1.producer.info_test_process.version=0.0.4`.
- Repeated `gbos.v1.producer.info_test_process.observation` values containing
  headerless compact observation fragments. This namespace allows other plugins
  to publish their own GBOS observations in the same build.
- Allowlisted build-level indexes only:

| Key | Measurement |
|---|---|
| `gbos.v1.index.info_test_process.jvm.process.cpu.cores.max` | `jvm.process.cpu.cores` / `max` |
| `gbos.v1.index.info_test_process.jvm.process.cpu.time.sum` | `jvm.process.cpu.time` / `sum` |
| `gbos.v1.index.info_test_process.jvm.process.memory.heap.peak.max` | `jvm.process.memory.heap.peak` / `max` |

Public contract: https://github.com/cdsap/build-observability-schema

# GBOS adoption (experimental / opt-in)

Info Test Process can dual-publish [Gradle Build Observability Schema (GBOS)](https://github.com/cdsap/build-observability-schema) observations alongside the existing `testProcess.*` Build Scan custom values and local JSON report.

GBOS support is **experimental** and **disabled by default**. Enabling it does not replace or remove legacy output in this major version.

## Opt-in flags

Set Gradle properties (for example in `gradle.properties` or via `-P`):

| Property | Default | Effect |
|---|---|---|
| `infoTestProcess.gbos.develocity.enabled` | `false` | When `true` and Develocity is applied, also publish `gbos.v1.observation` custom values and allowlisted `gbos.v1.index.*` scalars |

Example:

```properties
infoTestProcess.gbos.develocity.enabled=true
```

With no GBOS properties set, builds behave exactly as before: only legacy `testProcess.*` custom values / tags (Develocity) or `statsTestTasks.json` (no Develocity).

## Compatibility and legacy key retention

- **Default behavior is unchanged.** Opt-in is required for any GBOS emission.
- **Legacy keys are retained** for the current major line: `testProcess.*` custom values, existing scan tags (`tests:cpu-heavy`, `tests:near-oom`, `tests:jit-bound`, `tests:no-snapshot`), and the non-Develocity `statsTestTasks.json` document keep their current shapes.
- GBOS observations use base units (bytes, seconds, `{core}`, …) and store identity (PID, task path, executor) in observation `attributes`, never in custom-value names.
- Missing runtime snapshots omit unavailable measurements and may emit diagnostics instead of sentinel values such as `0` / `-1`.
- Schema validation runs in plugin **tests/CI only** against a vendored snapshot of `cdsap/build-observability-schema`. The plugin does **not** ship a runtime JSON Schema dependency.

## Develocity projection

When enabled, the plugin emits:

- Repeated custom values named `gbos.v1.observation` (compact observation JSON)
- Allowlisted build-level indexes only:

| Key | Measurement |
|---|---|
| `gbos.v1.index.info_test_process.jvm.process.cpu.cores.max` | `jvm.process.cpu.cores` / `max` |
| `gbos.v1.index.info_test_process.jvm.process.cpu.time.sum` | `jvm.process.cpu.time` / `sum` |
| `gbos.v1.index.info_test_process.jvm.process.memory.heap.peak.max` | `jvm.process.memory.heap.peak` / `max` |

Public contract: https://github.com/cdsap/build-observability-schema

# GBOS adoption (experimental / opt-in)

Info Test Process can write [Gradle Build Observability Schema (GBOS)](https://github.com/cdsap/build-observability-schema) JSON and NDJSON files. If the Develocity plugin is applied and legacy Develocity reporting is enabled, the GBOS Develocity opt-in publishes the same canonical observations to the Build Scan and replaces legacy `testProcess.*` custom values (scan tags are unchanged).

## Configuration

GBOS output is disabled by default. File sinks and the Develocity projection are independent:

| Property | Default | Effect |
|---|---|---|
| `infoTestProcess.gbos.enabled` | `false` | Enables local GBOS file sinks (`gbos.json` and `gbos.ndjson`) |
| `infoTestProcess.gbos.develocity.enabled` | `false` | Selects the GBOS Build Scan projection when Develocity reporting is enabled |

The same flags are available on the Settings extension. Gradle properties are
conventions, so an explicitly assigned extension value wins when both are set:

```kotlin
infoTestProcess {
    gbos {
        enabled.set(true)
        develocity {
            enabled.set(true)
        }
    }
}
```

`infoTestProcess.develocity.enabled` remains the broader switch for Build Scan
reporting and `statsTestTasks.txt` persistence. The GBOS Develocity projection
can only be selected while that legacy Develocity reporting path is enabled.

## Compatibility

| Develocity plugin | `infoTestProcess.develocity.enabled` | `infoTestProcess.gbos.enabled` | `infoTestProcess.gbos.develocity.enabled` | Build Scan custom values | Local report output |
|---|---|---|---|---|---|
| absent | any | any | any | none | `statsTestTasks.json`, plus GBOS file sinks when enabled |
| present | `true` (default) | `false` | `false` | legacy `testProcess.*` values and tags | `statsTestTasks.txt` |
| present | `true` (default) | `true` | `false` | legacy `testProcess.*` values and tags | `statsTestTasks.txt`, plus both GBOS file sinks |
| present | `true` (default) | `false` | `true` | GBOS projection and tags (no `testProcess.*`) | `statsTestTasks.txt` |
| present | `true` (default) | `true` | `true` | GBOS projection and tags (no `testProcess.*`) | `statsTestTasks.txt`, plus both GBOS file sinks |
| present | `false` | any | any | none | `statsTestTasks.json`, plus GBOS file sinks when enabled |

`infoTestProcess.gbos.develocity.enabled` selects GBOS instead of legacy
`testProcess.*` values. `infoTestProcess.develocity.enabled` suppresses the
entire Develocity custom-value set, including GBOS values, when set to `false`.

Legacy `statsTestTasks.json` / `statsTestTasks.txt` and scan tags remain available
according to the matrix above. When GBOS Develocity projection is enabled, GBOS replaces
legacy `testProcess.*` custom values.
- Without Develocity, local GBOS JSON/NDJSON output still works and the legacy `statsTestTasks.json` report is written as before.
- `gbos.json` is a GBOS report envelope; it is not the legacy `statsTestTasks.json` format. NDJSON contains one canonical observation per line. Both are produced from the same observation model used for Develocity.
- Stable report paths are replaced only after successful generation. Outputs do not append across builds or create timestamped files by default.
- Missing worker snapshots omit unavailable measurements and may emit GBOS diagnostics instead of sentinel values such as `0` or `-1`.

## Develocity projection

The projection emits `gbos.schema`, producer metadata, repeated `gbos.v1.producer.info_test_process.observation` fragments, and only the allowlisted build-level indexes:

| Key | Measurement |
|---|---|
| `gbos.v1.index.info_test_process.jvm.process.cpu.cores.max` | `jvm.process.cpu.cores` / `max` |
| `gbos.v1.index.info_test_process.jvm.process.cpu.time.sum` | `jvm.process.cpu.time` / `sum` |
| `gbos.v1.index.info_test_process.jvm.process.memory.heap.peak.max` | `jvm.process.memory.heap.peak` / `max` |

Schema validation runs in plugin tests/CI only against the released `io.github.cdsap:build-observability-schema` artifact.

## Compatibility policy

GBOS core and schema updates are compatibility-sensitive. The producer stays on
the current core contract (`0.0.7` here), and generated JSON, NDJSON, and
Develocity projections must continue to pass the published-schema validation
tests before an update is adopted. A schema/core change that requires a new
producer contract must be introduced deliberately and documented with the new
version; it must not silently alter the legacy path. With both GBOS opt-ins
disabled, legacy output remains the compatibility fallback.

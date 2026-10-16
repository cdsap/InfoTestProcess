# GBOS adoption (experimental / opt-in)

Info Test Process can write [Gradle Build Observability Schema (GBOS)](https://github.com/cdsap/build-observability-schema) JSON and NDJSON files. If the Develocity plugin is applied and legacy Develocity reporting is enabled, the same canonical observations are also published to the Build Scan automatically.

## Configuration

GBOS file output is disabled by default. A single option enables both file sinks:

| Property | Default | Effect |
|---|---|---|
| `infoTestProcess.gbos.enabled` | `false` | Enables both local GBOS file sinks (`gbos.json` and `gbos.ndjson`) |

There is deliberately no `infoTestProcess.gbos.develocity.enabled` property. Develocity presence is detected from the applied plugin, while `infoTestProcess.develocity.enabled` remains the legacy switch for Build Scan reporting and `statsTestTasks.txt` persistence.

## Compatibility

The GBOS file sinks and Develocity custom-value projection are independent. The
accepted compatibility matrix is:

| Develocity plugin | `infoTestProcess.develocity.enabled` | `infoTestProcess.gbos.enabled` | Build Scan custom values | Local report output |
|---|---|---|---|---|
| absent | any | any | none | `statsTestTasks.json`, plus both GBOS file sinks when enabled |
| present | `true` (default) | any | legacy `testProcess.*` values **and** GBOS projection | `statsTestTasks.txt`, plus both GBOS file sinks when enabled |
| present | `false` | any | none | `statsTestTasks.json`, plus both GBOS file sinks when enabled |

`infoTestProcess.gbos.enabled` only controls `gbos.json` and `gbos.ndjson`. It does
not add or remove Build Scan values. Likewise, `infoTestProcess.develocity.enabled`
is the separate legacy-output switch; disabling it suppresses the entire
Develocity custom-value set, including GBOS values.

Legacy `statsTestTasks.json`, `statsTestTasks.txt`, `testProcess.*` custom values,
and scan tags remain available according to the matrix above. GBOS never
replaces legacy values in this major version.
- Without Develocity, local GBOS JSON/NDJSON output still works and the legacy `statsTestTasks.json` report is written as before.
- `gbos.json` is a GBOS report envelope; it is not the legacy `statsTestTasks.json` format. NDJSON contains one canonical observation per line. Both are produced from the same observation model used for Develocity.
- Stable report paths are replaced only after successful generation. Outputs do not append across builds or create timestamped files by default.
- Missing worker snapshots omit unavailable measurements and may emit GBOS diagnostics instead of sentinel values such as `0` or `-1`.

## Develocity projection

The automatic projection emits `gbos.schema`, producer metadata, repeated `gbos.v1.producer.info_test_process.observation` fragments, and only the allowlisted build-level indexes:

| Key | Measurement |
|---|---|
| `gbos.v1.index.info_test_process.jvm.process.cpu.cores.max` | `jvm.process.cpu.cores` / `max` |
| `gbos.v1.index.info_test_process.jvm.process.cpu.time.sum` | `jvm.process.cpu.time` / `sum` |
| `gbos.v1.index.info_test_process.jvm.process.memory.heap.peak.max` | `jvm.process.memory.heap.peak` / `max` |

Schema validation runs in plugin tests/CI only against the released `io.github.cdsap:build-observability-schema` artifact.

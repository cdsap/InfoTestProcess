# GBOS adoption (experimental / opt-in)

Info Test Process can write [Gradle Build Observability Schema (GBOS)](https://github.com/cdsap/build-observability-schema) JSON and NDJSON files. If the Develocity plugin is applied and legacy Develocity reporting is enabled, enabling GBOS also publishes the same canonical observations to the Build Scan and replaces legacy `testProcess.*` custom values (scan tags are unchanged).

## Configuration

GBOS output is disabled by default. A single option enables both file sinks and the Develocity projection:

| Property | Default | Effect |
|---|---|---|
| `infoTestProcess.gbos.enabled` | `false` | Enables local GBOS file sinks (`gbos.json` and `gbos.ndjson`) and, when Develocity reporting is on, the GBOS Build Scan projection |

There is deliberately no `infoTestProcess.gbos.develocity.enabled` property. Develocity presence is detected from the applied plugin, while `infoTestProcess.develocity.enabled` remains the switch for Build Scan reporting and `statsTestTasks.txt` persistence.

## Compatibility

| Develocity plugin | `infoTestProcess.develocity.enabled` | `infoTestProcess.gbos.enabled` | Build Scan custom values | Local report output |
|---|---|---|---|---|
| absent | any | any | none | `statsTestTasks.json`, plus both GBOS file sinks when enabled |
| present | `true` (default) | `false` | legacy `testProcess.*` values and tags | `statsTestTasks.txt` |
| present | `true` (default) | `true` | GBOS projection and tags (no `testProcess.*`) | `statsTestTasks.txt`, plus both GBOS file sinks |
| present | `false` | any | none | `statsTestTasks.json`, plus both GBOS file sinks when enabled |

`infoTestProcess.develocity.enabled` suppresses the entire Develocity custom-value
set, including GBOS values, when set to `false`.

Legacy `statsTestTasks.json` / `statsTestTasks.txt` and scan tags remain available
according to the matrix above. When GBOS is enabled with Develocity, GBOS replaces
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

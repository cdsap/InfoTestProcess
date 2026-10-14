# GBOS adoption (experimental / opt-in)

Info Test Process can write [Gradle Build Observability Schema (GBOS)](https://github.com/cdsap/build-observability-schema) JSON and NDJSON files. If the Develocity plugin is applied and legacy Develocity reporting is enabled, the same canonical observations are also published to the Build Scan automatically.

## Configuration

GBOS file output is disabled by default. The master option enables both file sinks; format-specific options can enable or disable each sink independently:

| Property | Default | Effect |
|---|---|---|
| `infoTestProcess.gbos.enabled` | `false` | Enables both local GBOS file sinks |
| `infoTestProcess.gbos.json.enabled` | follows `gbos.enabled` | Enables `gbos.json` |
| `infoTestProcess.gbos.ndjson.enabled` | follows `gbos.enabled` | Enables `gbos.ndjson` |

There is deliberately no `infoTestProcess.gbos.develocity.enabled` property. Develocity presence is detected from the applied plugin, while `infoTestProcess.develocity.enabled` remains the legacy switch for Build Scan reporting and `statsTestTasks.txt` persistence.

## Compatibility

- Legacy `statsTestTasks.json`, `statsTestTasks.txt`, `testProcess.*` custom values, and scan tags remain available according to the existing Develocity mode. GBOS never replaces them.
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

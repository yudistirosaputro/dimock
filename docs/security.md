# Security and privacy

What a reviewer needs to know before letting dimock into a codebase.

## What runs where

| Build type | Artifact | Behaviour |
|---|---|---|
| debug | `dimock-okhttp` (+ optional `dimock-ui`) | Interceptor, on-device capture store, rule store, loopback wire server, ContentProvider auto-init |
| release | `dimock-okhttp-no-op` | Same public API. No server, no storage, no notification, no ContentProvider. `Dimock.interceptor()` is `chain.proceed(chain.request())`. |
| any variant with `enabled = false` | `dimock-okhttp` (+ optional `dimock-ui`) | The real artifact is on the classpath but inert at runtime: no engine, no capture store, no rule store, no wire server, no notification, no shake handler. `Dimock.interceptor()` passes every request through. |

The release artifact has no dependency on `dimock-core`; a unit test in the no-op module fails if that class ever appears on its classpath.

`enabled = false` is a runtime switch for a variant that must ship the real artifact yet stay inert, for example a `productionDebug` build pointed at production servers. Set it from manifest meta-data (`com.yudistirosaputro.dimock.ENABLED`, fed by `manifestPlaceholders`) rather than from code: auto-init runs from a ContentProvider before `Application.onCreate`, so a code-only switch would arrive after the server had already started. A reviewer assessing what reaches a shipped build should still prefer the no-op artifact, which removes the code entirely rather than disarming it.

## Network surface

- The wire server binds `127.0.0.1` only. It is reachable from a computer solely through `adb forward`, which requires USB debugging authorised on the device.
- The library makes no outbound connections of its own. There is no telemetry, no update check, no crash reporting.
- Mock responses never reach the network: a matched request is answered inside the OkHttp interceptor chain.

## Data at rest

- Captures live in memory only (ring buffer, default 500 transactions / 50 MB / 1 MB per body). They are gone when the process dies.
- Rules are persisted to `filesDir/dimock/rules.json` in app-private storage. Add this path to your `data_extraction_rules` / `full_backup_content` exclusions if the app enables Auto Backup (the sample app sets `allowBackup=false`).
- Redaction runs before storage, not at display time. Defaults: `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key` header values are replaced with `«redacted»`. Add headers and body regexes through `Dimock.Config`.
- Binary bodies (images, protobuf, octet-stream) are stored as size and content type only.

## Agent access

- A per-device switch ("Agent may change mock rules", `EngineConfig.agentWriteEnabled`) turns every mutating wire call into `403 agent_write_disabled` while leaving reads available.
- Every read or write over the wire is logged with its timestamp and call and shown in the Agent tab (`GET /agent/activity`).
- The MCP server and CLI run on the developer's machine and talk only to `127.0.0.1`. They store nothing outside the current working directory (rule files the developer writes).

## Reporting

Security issues: open a private advisory on the GitHub repository rather than a public issue.

<div align="center">

# dimock

**Agent-native HTTP inspector and mock injector for Android.**

[![CI](https://github.com/yudistirosaputro/dimock/actions/workflows/ci.yml/badge.svg)](https://github.com/yudistirosaputro/dimock/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.yudistirosaputro/dimock-okhttp?label=maven%20central)](https://central.sonatype.com/search?q=io.github.yudistirosaputro.dimock)
[![npm](https://img.shields.io/npm/v/dimock?label=npm)](https://www.npmjs.com/package/dimock)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Android minSdk 21](https://img.shields.io/badge/minSdk-21-3DDC84?logo=android&logoColor=white)](android/gradle/libs.versions.toml)
[![MCP](https://img.shields.io/badge/MCP-server-000)](docs/agents.md)

A debug-only library that captures your app's OkHttp traffic, mocks responses on the device, and exposes both over a local HTTP/JSON protocol so a coding agent (Claude Code, Cursor, Codex, or plain `curl`) can read what the app sent and inject mocks to drive every UI state without touching a backend.

*Chucker shows a human what happened. dimock lets a human and an agent decide what happens next.*

</div>

## Table of contents

- [Why](#why)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [Concepts](#concepts)
- [Security and privacy](#security-and-privacy)
- [Repository layout](#repository-layout)
- [Development](#development)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

```
you:    "capture the posts call, then show me the empty and error states"
agent:  captures_list → mock_from_capture(empty) → "reload the screen"
        → mock_from_capture(error) → "reload again" → logcat_tail → mock_clear
```

Status: **0.1.0-alpha01.** Engine, wire protocol, CLI and MCP server are tested; the Android modules are being validated on real projects. Feedback welcome.

## Why

| | Chucker | dimock |
|---|---|---|
| See requests and responses on the device | yes | yes |
| Opened from a notification, no code in your app | yes | yes — `dimock · 12 calls`, newest calls listed, tap to open; plus optional shake-to-open |
| Force a state on a call from the phone | no | yes — *Mock this* → Status / Timeout / Connection reset / Slow in two taps, or edit the response body and status by hand |
| Mock a response without a backend | no | yes, per endpoint, with status, delay, body, failure types, `times`, `sequence` |
| Drive it from a coding agent | no | MCP server + CLI, one tool catalogue |
| Derive an empty / error / slow / timeout variant from a real capture | no | `mock_from_capture` |
| Release build | separate no-op artifact | separate no-op artifact, identical API |
| Data leaves the device | no | no (loopback only, no telemetry) |

## Quick start

**1. Gradle** (app module)

```kotlin
debugImplementation("io.github.yudistirosaputro:dimock-ui:0.1.0-alpha01")            // interceptor + in-app inspector
releaseImplementation("io.github.yudistirosaputro:dimock-okhttp-no-op:0.1.0-alpha01") // same API, does nothing
```

No Compose in the app? Use `dimock-okhttp` instead of `dimock-ui`.

**2. One line** where the `OkHttpClient` is built

```kotlin
import com.yudistirosaputro.dimock.okhttp.Dimock

OkHttpClient.Builder()
    .addInterceptor(Dimock.interceptor())   // first application interceptor
    .build()
```

A `ContentProvider` starts the engine and the loopback wire server (port 6767) in debug builds; the release artifact contains nothing. Opt out of auto-start with `<meta-data android:name="com.yudistirosaputro.dimock.AUTO_INIT" android:value="false"/>` and call `Dimock.init(context, Dimock.Config(...))` yourself; change the port with `com.yudistirosaputro.dimock.PORT`; keep the real artifact in a variant but start nothing at all with `com.yudistirosaputro.dimock.ENABLED` set to `false`. Every field and manifest key: [Configuration](#configuration).

**3. Connect an agent**

```bash
# Claude Code (plugin: MCP server + /dimock skill)
claude plugin marketplace add yudistirosaputro/dimock
claude plugin install dimock@dimock

# Cursor, Codex, anything reading AGENTS.md
npx dimock init

# Any terminal
npx dimock connect --app com.your.app
npx dimock capture list
npx dimock mock from <captureId> empty
```

**4. Look at it on the device.** Pull down the notification — `dimock · 12 calls`, the newest calls listed — and tap it, like Chucker. On Android 13+ request `POST_NOTIFICATIONS` once (the sample shows the one line). Without the permission: `Dimock.launch(context)` from a debug menu, or shake the phone with `<meta-data android:name="com.yudistirosaputro.dimock.SHAKE_TO_OPEN" android:value="true" />` (or `Config(shakeToOpen = true)`). The inspector follows the system dark/light setting with its own palette for each, never the host app's colours; pin one with `Config(inspectorTheme = InspectorTheme.Dark)`.

## Configuration

Everything is a field on `Dimock.Config`, passed to `Dimock.init(context, Dimock.Config(...))`. When the auto-init `ContentProvider` does the work instead, the manifest keys below cover the fields that have to be decided before any of your code runs.

| Field | Default | What it does |
|---|---|---|
| `enabled` | `true` | `false` makes the real artifact inert: no engine, no wire server, no shake handler, no notification, and `interceptor()` passes every request straight through |
| `port` | `6767` | Loopback port for the wire server |
| `maxTransactions` | `500` | Ring-buffer size; past it the oldest capture is evicted |
| `maxStoreBytes` | `50 MB` | Total capture budget, oldest out first |
| `maxBodyBytes` | `1 MB` | Bodies above this are stored truncated |
| `redactHeaders` | `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key` | Header names blanked to `«redacted»` before storage |
| `redactBodyPatterns` | empty | Regexes blanked in bodies before storage |
| `showNotification` | `true` | The ongoing silent notification, the primary way into the inspector |
| `shakeToOpen` | `false` | Shake any Activity to open the inspector; needs no permission |
| `startServer` | `true` | `false` keeps captures and rules on the device, unreachable by an agent |
| `agentWriteEnabled` | `true` | Starting value of the per-device *Agent may change mock rules* switch |
| `inspectorTheme` | `InspectorTheme.System` | `System` follows the system dark/light setting; `Dark` and `Light` pin one palette |

Manifest keys read by the auto-init provider, all under `com.yudistirosaputro.dimock.`: `AUTO_INIT`, `PORT`, `SHAKE_TO_OPEN`, `ENABLED`, `INSPECTOR_THEME` (`system` / `dark` / `light`).

**Inert in one variant.** The provider runs before `Application.onCreate`, so a code-only switch is decided too late. Drive `ENABLED` from a manifest placeholder instead:

```kotlin
// app/build.gradle.kts
defaultConfig { manifestPlaceholders["dimockEnabled"] = "true" }
productFlavors { create("production") { manifestPlaceholders["dimockEnabled"] = "false" } }
```

```xml
<meta-data android:name="com.yudistirosaputro.dimock.ENABLED" android:value="${dimockEnabled}" />
```

**`enabled = false`, or the no-op artifact?** For release builds, swap in `dimock-okhttp-no-op`: it contains no server, no storage, no notification and no ContentProvider at all, so there is nothing to switch off. Reach for `enabled = false` when a variant must ship the real artifact and still stay inert - a `productionDebug` build, say - because it flips per flavour without changing a dependency.

## Concepts

**Rule.** Which requests to catch and what to do instead of the network. Written in YAML or JSON by the agent or the CLI; the device stores and matches them.

```yaml
- id: login-error
  match: { method: POST, path: /v1/auth/login }
  times: 1                       # once, then the rule is spent
  respond: { status: 500, body: '{"error":"internal"}', delayMs: 800 }

- id: orders-recover
  match: { path: "/v1/orders**" }
  sequence:                      # first call times out, the rest succeed
    - fail: { type: timeout }
    - respond: { status: 200, bodyFile: fixtures/orders.json }
```

Matching: method, path glob or `re:` regex, host glob, query pairs, headers, JSONPath assertions on the body (GraphQL `operationName` works). Highest `priority` wins, ties go to the most recently added rule. Failure types: `timeout`, `connection_reset`, `malformed_body`, `empty_body`. Full reference: [docs/rule-format.md](docs/rule-format.md).

**Capture.** Every request through the interceptor, mocked or real, with redacted headers, bodies up to 1 MB (binary as metadata), timing, and the rule that answered it. Ring buffer in memory, 500 transactions / 50 MB by default.

**Wire protocol.** Plain HTTP/1.1 + JSON on `127.0.0.1:6767`, reached through `adb forward`. `/health`, `/transactions`, `/rules`, `/agent/activity`, `/events` (SSE). Anything that can `curl` can drive dimock; the TypeScript client and the MCP server are thin layers over it. Reference: [docs/wire-protocol.md](docs/wire-protocol.md).

**Agent tools** (MCP and CLI are 1:1): `devices_list`, `apps_list`, `health`, `captures_list`, `captures_get` (with a curl reproduction), `captures_clear`, `mock_list`, `mock_set`, `mock_add`, `mock_toggle`, `mock_clear`, `mock_from_capture`, `logcat_tail`, `agent_activity`. Details: [docs/agents.md](docs/agents.md).

**In-app inspector** (Compose, its own instrument-panel tokens in light and dark, never the host app's): **Traffic** — Recording/Paused, `Filter by path`, chips `All GET POST 2xx 4xx·5xx Mocked Failed`, rows with method, status, path, `MOCK` badge and duration; **Detail** — big status with transport wording, `Served by mock rule …` banner, Request/Response tabs with `HEADERS` and `BODY`, pretty JSON with line numbers, form bodies decoded to `key: value`, *Search in body*, **cURL** and **Mock this**; **Force a state** sheet — method and path locked, one-tap *Status* (400/401/403/404/500/503, HTTP status only, empty body) · *Timeout* · *Connection reset* · *Slow* (2/5/10 s), plus *Custom response* (status + body editor pre-filled from the capture), one rule per endpoint; **Mocks** — `METHOD path → effect`, switch, origin `local`/`agent`, counters, *Reset* when spent, long-press to remove, *Disable all* / *Clear all*; **Agent** — connection state, the per-device *Agent may change mock rules* switch, and a log of every wire call. Glob paths, header and body matching, `times` and sequences are authored by the agent or the CLI on purpose, so the in-app UI never drifts from the wire protocol. Tokens: [docs/design/tokens.md](docs/design/tokens.md).

## Security and privacy

- Server binds loopback only; reachable solely through `adb forward` on an authorised device.
- No outbound connections, no telemetry, no update checks.
- `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key` redacted **before** storage; add your own header names and body regexes via `Dimock.Config`.
- Release builds ship `dimock-okhttp-no-op`: no server, no storage, no notification, no ContentProvider (a test fails if `dimock-core` ever appears on its classpath).
- Per-device switch turns every mutating wire call into `403 agent_write_disabled`; the Agent tab logs every read and write.

Full write-up for your security review: [docs/security.md](docs/security.md).

## Repository layout

```
android/
  dimock-core/          engine: rules, matcher, capture store, redaction, wire server (pure JVM, 71 tests)
  dimock-okhttp/        OkHttp interceptor, Dimock entry point, ContentProvider auto-init
  dimock-okhttp-no-op/  release artifact, same API surface
  dimock-ui/            Compose inspector + notification
  sample/                Compose + Retrofit demo app hitting JSONPlaceholder (open source, no API key)
packages/dimock/         the `dimock` npm package — one artifact, three source folders
  src/core/              adb discovery, wire client, YAML rules, capture→rule variants
  src/mcp/               MCP server (stdio) over the shared tool catalogue
  src/cli/               the `dimock` command, `npx dimock`
plugin/                  Claude Code plugin: .mcp.json + skills/dimock/SKILL.md
.claude-plugin/          marketplace manifest
docs/                    wire-protocol, rule-format, security, agents, publishing, design tokens
scripts/contract/        curl-based wire-protocol contract tests (CI and TS client share them)
scripts/release/         preflight: every version string must match the tag before anything publishes
```

## Development

Requirements: JDK 17, Android Studio (Ladybug or newer) with an API 35 SDK, Node 22.

```bash
# Android
cd android && ./gradlew :dimock-core:test :dimock-okhttp:testDebugUnitTest :dimock-okhttp-no-op:testReleaseUnitTest :sample:assembleDebug

# TypeScript
npm install && npm test

# Wire-protocol contract against the JVM stand-in for a device
cd android && ./gradlew :dimock-core:runDevServer &   # port 6767
scripts/contract/run.sh

# Try the CLI against that stand-in
DIMOCK_BASE_URL=http://127.0.0.1:6767 node packages/dimock/dist/cli/bin.js health
```

Every behaviour is written as a Given/When/Then that doubles as a test; a change to behaviour changes the test first.

## Roadmap

- Phase 2: request breakpoints (edit a request before it leaves), device→desktop channel for a "send to agent" button in the inspector, WebSocket frame inspector with a floating overlay, HAR export, dynamic port discovery, Ktor client adapter.
- Not planned: iOS (the wire protocol is transport-neutral on purpose, but no Swift code is on the table).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the workflow, [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for expectations, and [SECURITY.md](SECURITY.md) for reporting vulnerabilities. Good first issues are labelled `good first issue`.

## License

Apache 2.0. See [LICENSE](LICENSE).

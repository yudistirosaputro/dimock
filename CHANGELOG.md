# Changelog

## 0.1.0

First stable release. Changes since 0.1.0-alpha02, from the first field review (a Retrofit + Hilt + Paging 3 app on a Galaxy S22, Android 16):

- Fix: the curl reproduction (CLI `capture get` and the in-app cURL sheet) quotes URLs containing `?`, `&` or `*`, so a line with a query string pastes into bash and zsh as one argument.
- `mock from --path <glob> <variant>` uses the newest real capture of that path; no capture id needed.
- `mock from --status`, `--body`, `--body-file` (MCP: `status`, `body`, `bodyFile`) adjust the `error`, `unauthorized`, `empty` and `slow` variants.
- `error` and `unauthorized` reuse the body of a real 4xx/5xx already captured from the same host, so the app's error parser meets the API's own error shape.
- adb is also found through `ANDROID_SDK_ROOT`, `sdk.dir` in `./local.properties`, and Android Studio's default SDK on macOS, Linux and Windows; the error lists every place it looked.
- `connect` prints `export DIMOCK_BASE_URL=...` only with `--print-env`.
- `mock add --help` and `mock set --help` show the rule format with an example and a link to `docs/rule-format.md`.
- `/dimock` skill and `dimock init` snippet: an agent with a device-interaction tool (argent, mobile-mcp, …) launches, reloads and checks the screen itself, and stops for the human only when it has none.

## 0.1.0-alpha02

- Inspector look: "Slate & Sand" palette (cool slate neutrals, one sand accent meaning mocked), Plus Jakarta Sans + DM Mono bundled, layered radii, grouped lists and a floating tab bar, in light and dark; the Custom response editor now scrolls into view above the keyboard and, while open, takes the top of the sheet with the other presets below it. New mark — a lowercase d with one diagonal cut, the cut-off piece in sand — as the sample's launcher, themed and notification icon (`docs/brand/`).

## 0.1.0-alpha01

First public version.

- Android: `dimock-core` engine (rule matcher with priority, `times`, `sequence`, JSONPath body match; capture ring buffer; redaction; loopback wire server with SSE events; agent write gating and activity log), `dimock-okhttp` interceptor and `Dimock` entry point with ContentProvider auto-init, `dimock-okhttp-no-op` release artifact, `dimock-ui` Compose inspector opened from a Chucker-style notification (Traffic with Recording/Paused, path filter and chips; Detail with HEADERS/BODY, pretty JSON, form decoding and body search; "Force a state" on a locked method + path — Status (HTTP status only), Timeout, Connection reset, Slow, Custom response editor; cURL sheet; Mocks with switch, counters, long-press remove; Agent tab), optional shake-to-open, `sample` app on JSONPlaceholder.
- TypeScript: `dimock` CLI, MCP server, shared core (adb discovery, wire client, YAML rules with `bodyFile`, variants derived from captures).
- Agents: Claude Code plugin with `/dimock` skill, `dimock init` for Cursor and Codex.
- Docs: wire protocol v1, rule format, security, agents, design tokens; curl contract tests.

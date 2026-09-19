# Changelog

## 0.1.0-alpha01

First public version.

- Android: `dimock-core` engine (rule matcher with priority, `times`, `sequence`, JSONPath body match; capture ring buffer; redaction; loopback wire server with SSE events; agent write gating and activity log), `dimock-okhttp` interceptor and `Dimock` entry point with ContentProvider auto-init, `dimock-okhttp-no-op` release artifact, `dimock-ui` Compose inspector opened from a Chucker-style notification (Traffic with Recording/Paused, path filter and chips; Detail with HEADERS/BODY, pretty JSON, form decoding and body search; "Force a state" on a locked method + path — Status (HTTP status only), Timeout, Connection reset, Slow, Custom response editor; cURL sheet; Mocks with switch, counters, long-press remove; Agent tab), optional shake-to-open, `sample` app on JSONPlaceholder.
- TypeScript: `dimock` CLI, MCP server, shared core (adb discovery, wire client, YAML rules with `bodyFile`, variants derived from captures).
- Agents: Claude Code plugin with `/dimock` skill, `dimock init` for Cursor and Codex.
- Docs: wire protocol v1, rule format, security, agents, design tokens; curl contract tests.

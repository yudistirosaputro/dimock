# Using dimock from an agent

Three ways in, all backed by one tool catalogue (`packages/mcp/src/tools.ts`), so behaviour is identical.

## Claude Code (first-class)

```
claude plugin marketplace add yudistirosaputro/dimock
claude plugin install dimock@dimock
```

You get the `dimock` MCP server (started with `npx -y dimock mcp`) and the `/dimock` skill, a step-by-step workflow with explicit stop points where the human must touch the device. Project-only alternative without the plugin: `npx dimock init --agent claude` writes `.mcp.json` and a `CLAUDE.md` block.

## Cursor

`npx dimock init --agent cursor` writes `.cursor/mcp.json` (the MCP server) and `.cursor/rules/dimock.mdc` (when and how to use it).

## Codex and everything else

`npx dimock init --agent codex` appends a marked block to `AGENTS.md` describing the workflow and the `npx dimock ... --json` commands. Point your agent's MCP configuration at `npx -y dimock mcp` if it supports MCP; otherwise the CLI with `--json` is the interface.

## Tool catalogue

| MCP tool | CLI | Purpose |
|---|---|---|
| `devices_list` | `dimock devices` | adb devices with model and API level |
| `apps_list` | `dimock apps` | apps with a reachable dimock on the device |
| `health` | `dimock health` | identity, protocol, counters |
| `captures_list` | `dimock capture list` | summaries, filters: `path`, `method`, `mocked`, `since`, `limit` |
| `captures_get` | `dimock capture get <id>` | full capture + `asCurl` |
| `captures_clear` | `dimock capture clear` | |
| `mock_list` | `dimock mock list` | rules with runtime state |
| `mock_set` | `dimock mock set <file>` | replace all rules (YAML/JSON, `bodyFile` inlined) |
| `mock_add` | `dimock mock add <file>` | upsert one rule |
| `mock_toggle` | `dimock mock on/off/reset <id>` | |
| `mock_clear` | `dimock mock clear [id]` | |
| `mock_from_capture` | `dimock mock from <id> <variant>` | derive `error`, `unauthorized`, `empty`, `slow`, `timeout`, `malformed` |
| `logcat_tail` | `dimock logcat -g <regex>` | app's logcat by pid |
| `agent_activity` | `dimock activity` | what clients read or changed |
| (stream) | `dimock watch` | live events |

Every tool accepts `device`, `app`, `port`, `baseUrl`. With one device and one dimock app connected nothing needs to be passed; with more, the tool answers with `candidates` and the agent asks.

## Environment

- `DIMOCK_BASE_URL`: skip adb and talk to this URL (a port you forwarded yourself, or the JVM dev server).
- `DIMOCK_PORTS`: extra device ports to probe besides 6767.
- `ADB` or `ANDROID_HOME`: where to find adb when it is not on PATH.

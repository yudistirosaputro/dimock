# dimock wire protocol (v1)

The contract between the device library (`android/dimock-core` → `WireServer`) and every client (`packages/dimock`, curl, any agent). HTTP/1.1 with JSON bodies, served on the device at `127.0.0.1:6767` (configurable) and reached from a computer through `adb forward tcp:6767 tcp:6767`. Nothing in this protocol names Android.

Conventions

- Every response carries `X-Dimock-Protocol: 1`. Clients refuse a different major with an upgrade message.
- Bodies are UTF-8 JSON. Unknown fields are ignored on input and may appear on output (forward-compatible).
- Errors are `{"error": "<message>"}` with 400 (malformed payload), 403 (`agent_write_disabled`), 404, 405.
- Clients should send `X-Dimock-Client: <name>` (e.g. `claude-code`, `cli`) on `/health`; the device shows it in the Agent tab.
- Timestamps are epoch milliseconds. Lists are newest first.

## Endpoints

| Method and path | Purpose | Response |
|---|---|---|
| `GET /health` | Liveness, identity, client heartbeat | 200 `Health` |
| `GET /transactions?limit=&since=&path=&method=&mocked=` | List captures (summaries) | 200 `TransactionSummary[]` |
| `GET /transactions/{id}` | One capture with headers and bodies | 200 `Transaction`, 404 |
| `DELETE /transactions` | Clear captures | 204 |
| `GET /rules` | All rules with runtime state | 200 `RuleEntry[]` |
| `PUT /rules` | Replace the whole rule set | 200 `RuleEntry[]`, 400 |
| `POST /rules` | Add or replace one rule by `id` | 201 `RuleEntry`, 400 |
| `GET /rules/{id}` | One rule | 200 `RuleEntry`, 404 |
| `PATCH /rules/{id}` | `{"enabled": bool}` and/or `{"reset": true}` | 200 `RuleEntry`, 404 |
| `DELETE /rules/{id}` | Remove one rule | 204, 404 |
| `DELETE /rules` | Remove all rules | 204 |
| `GET /agent/activity?limit=` | What clients read or changed (Agent tab log) | 200 `Activity[]` |
| `DELETE /agent/activity` | Clear that log | 204 |
| `GET /events` | Server-sent events stream | `text/event-stream` |

Write gating: when the device switch "Agent may change mock rules" is off, every mutating call under `/rules` and `DELETE /transactions` answers `403 {"error":"agent_write_disabled"}`. Reads keep working.

Query parameters on `GET /transactions`: `limit` (default 100), `since` (epoch ms, strictly newer), `path` (glob or `re:` regex, see rule-format), `method` (case-insensitive), `mocked` (`true`/`false`).

Client heartbeat: the device considers a client connected while `/health` has been called within the last 30 s. The first `/health` after that window logs a `connect` activity entry and emits `client_connected`. Clients that stay attached (the MCP server) poll `/health` every 10 s.

## Types

```jsonc
Health {
  "app": "com.yudistirosaputro.dimock.sample",   // host application id
  "appVersion": "0.1.0",         // optional
  "version": "0.1.0",            // dimock library version
  "protocol": 1,
  "activeMocks": 3,              // enabled, not spent
  "transactions": 128,
  "agentWriteEnabled": true
}

TransactionSummary {
  "id": "01j7...", "startedAt": 1726300000000, "durationMs": 812,
  "method": "POST", "url": "https://api.example.com/v1/auth/login?x=1",
  "host": "api.example.com", "path": "/v1/auth/login",
  "responseCode": 500,           // absent when the call failed before a response
  "error": "SocketTimeoutException: ...", // absent on success
  "mocked": true, "mockRuleId": "login-error",
  "tag": "checkout",             // optional, app-supplied
  "requestBytes": 42, "responseBytes": 21, "responseContentType": "application/json"
}

Transaction = TransactionSummary + {
  "requestHeaders": { "Authorization": ["«redacted»"], "Accept": ["*/*"] },
  "requestBody": Body | null,
  "responseHeaders": { ... },
  "responseBody": Body | null
}

Body =
  { "kind": "text",      "text": "...", "contentType": "application/json", "totalBytes": 21 }
| { "kind": "truncated", "text": "<first maxBodyBytes>", "contentType": "...", "totalBytes": 2097152 }
| { "kind": "binary",    "contentType": "image/png", "totalBytes": 18000 }

RuleEntry = Rule + {
  "state": { "hits": 1, "remaining": 0, "sequenceIndex": 1, "spent": true, "lastHitAt": 1726300000000 }
}
```

`Rule` is defined in [rule-format.md](rule-format.md). Clients resolve `bodyFile` before sending: the device only ever sees inline `body`.

## Events (`GET /events`)

Standard SSE frames: `event: <type>` then `data: <json>` then a blank line. A comment frame `: connected` is sent first. The stream stays open until the client disconnects.

| type | data | when |
|---|---|---|
| `transaction` | `TransactionSummary` | a capture was recorded (real or mocked) |
| `rule_hit` | `{ruleId, name?, method, path}` | a rule won a request |
| `rules_changed` | `{active, total}` | any rule mutation, including counters consumed by a hit |
| `client_connected` | `{client?, at}` | a client (re)appeared, see heartbeat |
| `agent_activity` | `Activity` | a new Agent-tab log line |

```jsonc
Activity { "at": 1726300000000, "kind": "read" | "write" | "connect", "summary": "Pushed 3 rules", "call": "PUT /rules" }
```

## Compatibility rules

- Adding a field or an event type is a minor change: no protocol bump.
- Renaming or removing a field, changing a status code, or changing matching semantics bumps `protocol` and the `X-Dimock-Protocol` value; both sides ship in the same PR with updated `scripts/contract`.
- `scripts/contract/run.sh` is the executable form of this document. CI runs it against `./gradlew :dimock-core:runDevServer`; the TypeScript client runs the same scripts against its own expectations.

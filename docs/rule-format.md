# Mock rule format

A rule says which requests to catch and what to do instead of the network. Agents and the CLI write rules as YAML or JSON (`dimock mock set rules.yaml`); the device stores and matches the JSON form. The in-app "Force a state" sheet creates rules too (`local:<method>-<path-slug>`, priority 100): an HTTP status with an empty body, a timeout or connection reset, the captured response delayed, or a custom status + body typed on the device — method and path locked.

```yaml
id: login-error              # optional; generated when absent. Stable ids let PUT /rules keep counters.
name: "Login → 500"          # optional, shown in the app
enabled: true                # default true
priority: 10                 # default 0; higher wins; ties go to the most recently added rule

match:                       # every present field must match (AND); an empty match catches everything
  method: POST               # exact, case-insensitive
  path: "/v1/auth/login"     # glob against the URL path only (never the query string), or re:<regex>
  host: "api.example.com"    # glob; "*.example.com"
  query: { grant_type: password }      # each listed pair must be present with that value
  headers: { X-Client: android }       # header names case-insensitive, values exact
  body:                                # JSONPath assertions against a JSON request body
    - path: "$.operationName"
      equals: "GetPortfolio"

times: 1                     # optional; after N hits the rule is auto-disabled and shown as spent

# Exactly one of the three below.
respond:
  status: 500                # default 200
  headers: { Content-Type: application/json }   # Content-Type defaults to application/json
  body: '{"error":"internal"}'                  # inline text
  # bodyFile: fixtures/login_error.json        # client-side only: inlined into body before PUT/POST
  delayMs: 800               # default 0

fail:
  type: timeout              # timeout | connection_reset | malformed_body | empty_body
  delayMs: 0                 # timeout with 0 waits the client's read timeout, then throws

sequence:                    # per-hit overrides, clamps at the last step
  - fail: { type: timeout }
  - respond: { status: 200, body: "[]" }
```

## Matching semantics

- Glob: `*` matches within one path segment (`/v1/*/summary`), `**` matches across segments (`/v1/orders**`), `?` matches one character. Hosts use `*` across dots.
- Regex: prefix with `re:`; must match the whole path.
- Body JSONPath subset: `$`, `.key`, `['key']`, `[index]`. `equals` is compared to the leaf's string form (`"5"` matches the number 5, `"true"` the boolean). A non-JSON or missing body never matches a rule that has body assertions.
- Selection: enabled and not spent, then highest `priority`, then most recently added. Selection consumes one hit even for `fail`.

## Failure types

| type | what the app sees |
|---|---|
| `timeout` | `SocketTimeoutException` after `delayMs`, or after the OkHttp read timeout when `delayMs` is 0 |
| `connection_reset` | `IOException("dimock: connection reset ...")` |
| `malformed_body` | 200 with `Content-Type: application/json` and a body that is not valid JSON |
| `empty_body` | 200 with an empty body |

## Runtime state (read-only, from `GET /rules`)

```json
"state": { "hits": 2, "remaining": 0, "sequenceIndex": 1, "spent": true, "lastHitAt": 1726300000000 }
```

`PATCH /rules/{id}` with `{"reset": true}` restores `hits`/`remaining`/`sequenceIndex` and re-enables the rule. `PUT /rules` keeps the state of a rule whose `id` and definition are unchanged, and resets the others.

## Agent-generated variants (`mock_from_capture`)

Given a captured 200, the client derives rules with the same `method` + `path`:

| variant | rule |
|---|---|
| `error` | `respond.status: 500`, body `{"error":"internal"}` |
| `unauthorized` | `respond.status: 401` |
| `empty` | `respond.status: 200`, body with top-level arrays emptied, envelope keys (`data`, `items`, `results`, `meta`) kept, leaf objects nulled |
| `slow` | captured response with `delayMs: 3000` |
| `timeout` | `fail.type: timeout` |
| `malformed` | `fail.type: malformed_body` |

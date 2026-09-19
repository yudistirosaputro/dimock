# shellcheck shell=bash
# Helpers shared by the contract scripts. Source, don't run.
B="${DIMOCK_BASE_URL:-http://127.0.0.1:6767}"
STATUS=""; BODY=""; HEADERS=""
# req METHOD PATH [JSON_BODY]  -> sets STATUS, BODY, HEADERS
req() {
  local method="$1" path="$2" data="${3:-}"
  local out
  if [ -n "$data" ]; then
    out=$(curl -sS -X "$method" "$B$path" -H 'Content-Type: application/json' -H 'X-Dimock-Client: contract' --data-binary "$data" -D - 2>&1)
  else
    out=$(curl -sS -X "$method" "$B$path" -H 'X-Dimock-Client: contract' -D - 2>&1)
  fi
  HEADERS=$(printf '%s' "$out" | sed -n '1,/^\r*$/p')
  BODY=$(printf '%s' "$out" | sed '1,/^\r*$/d')
  STATUS=$(printf '%s' "$HEADERS" | head -1 | awk '{print $2}')
}
expect_status() { [ "$STATUS" = "$1" ] || { echo "  expected status $1, got $STATUS: $BODY"; return 1; }; }
expect_header() { printf '%s' "$HEADERS" | grep -qi "^$1: $2" || { echo "  expected header $1: $2"; printf '%s\n' "$HEADERS"; return 1; }; }
# json EXPR  -> evaluates a JS expression over parsed BODY as `b`
json() { node -e 'const b=JSON.parse(process.argv[1]); const r=(function(b){return eval(process.argv[2])})(b); process.stdout.write(String(r))' -- "$BODY" "$1"; }
expect_json() { local got; got=$(json "$1"); [ "$got" = "$2" ] || { echo "  expected $1 == $2, got $got"; return 1; }; }

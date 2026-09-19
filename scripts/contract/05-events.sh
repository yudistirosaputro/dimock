#!/usr/bin/env bash
# Opens the SSE stream, pushes a rule, expects a rules_changed event within 3 seconds.
set -uo pipefail; source "$(dirname "$0")/lib.sh"
TMP=$(mktemp)
curl -sN --max-time 3 "$B/events" > "$TMP" 2>/dev/null &
sleep 0.4
req POST /rules '{"id":"contract-evt","match":{"path":"/evt"},"respond":{"status":204}}' && expect_status 201 || exit 1
wait || true
grep -q '^event: rules_changed' "$TMP" || { echo "  no rules_changed event in stream:"; cat "$TMP"; rm -f "$TMP"; exit 1; }
grep -q '^data: {' "$TMP" || { echo "  event without JSON data"; rm -f "$TMP"; exit 1; }
rm -f "$TMP"
req DELETE /rules/contract-evt && expect_status 204

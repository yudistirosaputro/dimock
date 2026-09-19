#!/usr/bin/env bash
# Wire-protocol contract tests. Requires a running dimock wire server (device via `adb forward`, or
# `./gradlew :dimock-core:runDevServer`). Needs curl and node.
set -uo pipefail
export DIMOCK_BASE_URL="${DIMOCK_BASE_URL:-http://127.0.0.1:6767}"
cd "$(dirname "$0")"
pass=0; fail=0
for t in ./[0-9][0-9]-*.sh; do
  if bash "$t"; then echo "PASS $t"; pass=$((pass+1)); else echo "FAIL $t"; fail=$((fail+1)); fi
done
echo "contract: $pass passed, $fail failed against $DIMOCK_BASE_URL"
[ "$fail" -eq 0 ]

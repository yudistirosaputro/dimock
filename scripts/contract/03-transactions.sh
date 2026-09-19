#!/usr/bin/env bash
set -uo pipefail; source "$(dirname "$0")/lib.sh"
req GET '/transactions?limit=5' && expect_status 200 && expect_json 'Array.isArray(b)' true || exit 1
if [ "$(json 'b.length')" -gt 0 ]; then
  expect_json 'typeof b[0].id' string && expect_json '"requestBody" in b[0]' false || exit 1
  ID=$(json 'b[0].id')
  req GET "/transactions/$ID" && expect_status 200 && expect_json 'b.id' "$ID" && expect_json '"requestHeaders" in b' true || exit 1
  req GET '/transactions?mocked=true' && expect_json 'b.every(t=>t.mocked===true)' true || exit 1
fi
req GET /transactions/does-not-exist && expect_status 404 && expect_json 'typeof b.error' string

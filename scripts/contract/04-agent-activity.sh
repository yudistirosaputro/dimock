#!/usr/bin/env bash
set -uo pipefail; source "$(dirname "$0")/lib.sh"
req GET /agent/activity && expect_status 200 && expect_json 'Array.isArray(b)' true || exit 1
req PUT /rules '[]' && expect_status 200 || exit 1
req GET /agent/activity && expect_json 'b.some(e=>e.call==="PUT /rules")' true && expect_json 'b.some(e=>e.kind==="connect")' true || exit 1
req DELETE /agent/activity && expect_status 204 || exit 1
req GET /agent/activity && expect_json 'b.length' 0

#!/usr/bin/env bash
set -uo pipefail; source "$(dirname "$0")/lib.sh"
RULE='{"id":"contract-login","name":"Login → 500","priority":10,"match":{"method":"POST","path":"/v1/auth/login"},"times":1,"respond":{"status":500,"body":"{\"error\":\"internal\"}","delayMs":800}}'
req PUT /rules "[$RULE]" && expect_status 200 && expect_json 'b.length' 1 && expect_json 'b[0].id' contract-login && expect_json 'b[0].state.hits' 0 && expect_json 'b[0].state.remaining' 1 || exit 1
req GET /rules && expect_status 200 && expect_json 'b[0].respond.delayMs' 800 || exit 1
req POST /rules '{"id":"contract-seq","match":{"path":"/v1/orders**"},"sequence":[{"fail":{"type":"timeout"}},{"respond":{"status":200,"body":"[]"}}]}' && expect_status 201 && expect_json 'b.sequence.length' 2 || exit 1
req PATCH /rules/contract-seq '{"enabled":false}' && expect_status 200 && expect_json 'b.enabled' false || exit 1
req PATCH /rules/contract-seq '{"enabled":true,"reset":true}' && expect_status 200 && expect_json 'b.enabled' true && expect_json 'b.state.hits' 0 || exit 1
req GET /rules/contract-seq && expect_status 200 && expect_json 'b.id' contract-seq || exit 1
req DELETE /rules/contract-seq && expect_status 204 || exit 1
req DELETE /rules/contract-seq && expect_status 404 || exit 1
req PUT /rules '[{"id":"bad","match":{}}]' && expect_status 400 && expect_json '/respond/.test(b.error)' true || exit 1
req PUT /rules 'not json' && expect_status 400 || exit 1
req DELETE /rules && expect_status 204 || exit 1
req GET /rules && expect_json 'b.length' 0

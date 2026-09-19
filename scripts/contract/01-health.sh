#!/usr/bin/env bash
set -uo pipefail; source "$(dirname "$0")/lib.sh"
req GET /health || exit 1
expect_status 200 && expect_header X-Dimock-Protocol 1 && expect_json 'b.protocol' 1 && expect_json 'typeof b.app' string && expect_json 'typeof b.activeMocks' number

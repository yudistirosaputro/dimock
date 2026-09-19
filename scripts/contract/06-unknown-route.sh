#!/usr/bin/env bash
set -uo pipefail; source "$(dirname "$0")/lib.sh"
req GET /nope && expect_status 404 && expect_json 'typeof b.error' string && expect_header X-Dimock-Protocol 1

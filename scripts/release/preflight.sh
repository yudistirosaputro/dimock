#!/usr/bin/env bash
# Verify every version string in the repo matches the release you are about to cut, and that the
# artefacts actually build. Run this BEFORE tagging: a version published to Maven Central can never
# be deleted or replaced.
#
#   bash scripts/release/preflight.sh 0.1.0-alpha01
#
# CI runs the same script from the tag, ahead of any publishing job.
set -euo pipefail
cd "$(dirname "$0")/../.."

V="${1:-}"
[ -n "$V" ] || { echo "usage: $0 <version>   e.g. $0 0.1.0-alpha01" >&2; exit 2; }

fail=0
check() { # check <label> <expected> <actual>
  if [ "$2" = "$3" ]; then printf '  ok    %-34s %s\n' "$1" "$3"
  else printf '  WRONG %-34s %s (expected %s)\n' "$1" "${3:-<not found>}" "$2"; fail=1; fi
}
value() { grep -m1 -oE "$2" "$1" 2>/dev/null | grep -oE '[0-9]+\.[0-9]+\.[0-9]+[A-Za-z0-9.-]*' || true; }

echo "dimock preflight for $V"
check "npm package.json"        "$V" "$(value packages/dimock/package.json '"version": "[^"]+"')"
check "npm cli --version"       "$V" "$(value packages/dimock/src/cli/cli.ts '\.version\("[^"]+"\)')"
check "mcp SERVER_VERSION"      "$V" "$(value packages/dimock/src/mcp/index.ts 'SERVER_VERSION = "[^"]+"')"
check "plugin manifest"         "$V" "$(value plugin/.claude-plugin/plugin.json '"version": "[^"]+"')"
check "marketplace manifest"    "$V" "$(value .claude-plugin/marketplace.json '"version": "[^"]+"')"
check "gradle VERSION_NAME"     "$V" "$(value android/gradle.properties 'VERSION_NAME=.*')"
check "Dimock.VERSION (debug)"  "$V" "$(value android/dimock-okhttp/src/main/kotlin/com/yudistirosaputro/dimock/okhttp/Dimock.kt 'VERSION = "[^"]+"')"
check "Dimock.VERSION (no-op)"  "$V" "$(value android/dimock-okhttp-no-op/src/main/kotlin/com/yudistirosaputro/dimock/okhttp/Dimock.kt 'VERSION = "[^"]+"')"

grep -q "^## $V\b" CHANGELOG.md && printf '  ok    %-34s\n' "CHANGELOG section" || { printf '  WRONG %-34s no \"## %s\" heading\n' "CHANGELOG section" "$V"; fail=1; }
grep -rqn "OWNER" README.md packages/dimock/package.json android/build.gradle.kts && { echo "  WRONG placeholder OWNER still present"; fail=1; } || printf '  ok    %-34s\n' "no OWNER placeholder"

echo "building and testing the npm package"
npm run build >/dev/null && npm test >/dev/null 2>&1 && printf '  ok    %-34s\n' "npm build + tests" || { printf '  WRONG %-34s\n' "npm build + tests"; fail=1; }

echo "packing"
( cd packages/dimock && npm pack --dry-run ) 2>&1 | sed -n '/Tarball Contents/,/Tarball Details/p' | head -20

[ "$fail" -eq 0 ] || { echo; echo "preflight FAILED — fix the versions above before tagging v$V"; exit 1; }
echo
echo "preflight OK. Next:  git tag v$V && git push origin v$V"

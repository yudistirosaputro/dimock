## What

<!-- One paragraph: what changes and why. Link the issue. -->

## Checklist

- [ ] Test written first; the named test updated if behaviour changed
- [ ] `cd android && ./gradlew check` and `npm test` pass locally
- [ ] Wire protocol untouched, or `docs/wire-protocol.md`, both sides, and `scripts/contract/` updated together
- [ ] No new dependency in `dimock-core` / `dimock-okhttp`; `dimock-okhttp-no-op` still free of core
- [ ] `CHANGELOG.md` entry under Unreleased

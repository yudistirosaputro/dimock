# Contributing to dimock

Thanks for taking the time. This project is small enough that one person can hold it in their head; the rules below keep it that way.

## Before you start

- Open an issue for anything larger than a bug fix, so the design is agreed before code exists. Small fixes and doc corrections can go straight to a pull request.
- Read the README (what the product is and is not) and `docs/security.md`. The wire protocol in `docs/wire-protocol.md` is a contract: changing it means changing both sides and the contract scripts in one PR.

## Setup

Requirements: JDK 17, Android Studio (Ladybug or newer) with an API 35 SDK, Node 22, `adb` on PATH for device work.

```bash
git clone https://github.com/yudistirosaputro/dimock && cd dimock
npm install
cd android && ./gradlew :dimock-core:test          # engine, pure JVM
./gradlew :dimock-okhttp:testDebugUnitTest         # interceptor against MockWebServer
./gradlew :sample:installDebug                      # demo app on a connected device
cd .. && npm test                                   # TypeScript client, MCP server, CLI
```

`scripts/contract/run.sh` exercises the wire protocol against a running device or `./gradlew :dimock-core:runDevServer -Pport=6767` (see `scripts/contract/README.md`).

## Making a change

1. Branch from `main`.
2. Write the test first. Every behaviour in this repo has a named test; a change to behaviour changes that test before the code.
3. Keep module boundaries: `dimock-core` and `dimock-okhttp` depend only on Kotlin stdlib, coroutines, kotlinx-serialization and OkHttp; `dimock-okhttp-no-op` must not depend on core; `dimock-ui` is Compose only.
4. Run the full check before pushing: `cd android && ./gradlew check` and `npm test`.
5. Open the PR against `main` with the template filled in. CI runs unit tests, the wire contract, and assembles the sample in debug and release.

## Style

- Kotlin: official code style, explicit public API, no wildcard imports.
- TypeScript: strict mode, `.js` import specifiers, no default exports.
- Commit messages: imperative subject, body explains why when it is not obvious.
- User-visible strings in the inspector follow `docs/design/tokens.md`; no emoji in the UI.

## Reporting bugs

Use the bug report template. The most useful bug report includes the `dimock` and Android versions, the output of `npx dimock health --json`, and a minimal rule file if mocking is involved. Captures may contain your data: redact before pasting.

## Licence

By contributing you agree that your contributions are licensed under the Apache License 2.0, the same as the project.

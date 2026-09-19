# Wire-protocol contract tests

Executable form of `docs/wire-protocol.md`. `run.sh` runs every `NN-*.sh` against `DIMOCK_BASE_URL` (default `http://127.0.0.1:6767`) and exits non-zero on the first failing assertion in any script. Needs `curl` and `node`.

Targets:

1. A device: `adb forward tcp:6767 tcp:6767 && scripts/contract/run.sh`
2. The JVM stand-in, no device needed: `./gradlew :dimock-core:runDevServer -Pport=6767 &` then `scripts/contract/run.sh`

`packages/dimock` runs these same scripts in its test suite so the TypeScript client and the device never drift.

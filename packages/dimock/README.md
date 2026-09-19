# dimock

CLI and MCP server for [dimock](https://github.com/yudistirosaputro/dimock) — an agent-native HTTP inspector and mock injector for Android.

The Android app runs the library; this package is how your machine (and your coding agent) talks to it over `adb forward`. Read the app's real traffic, then inject mock responses to drive Loading, Success, Error and Empty states without touching a backend.

```bash
npx dimock devices                 # adb devices with dimock reachable
npx dimock capture list            # what the app just sent
npx dimock mock from <id> empty    # turn a real capture into an empty-state mock
npx dimock mock clear              # back to the real backend
```

As an MCP server (Claude Code, Cursor, Codex — anything speaking MCP):

```bash
npx dimock init                    # writes .mcp.json / .cursor rules / AGENTS.md
npx dimock mcp                     # stdio MCP server, 14 tools
```

Needs Node 20+, `adb` on `PATH`, and a debug build of the app with the `dimock` Android library installed. Nothing leaves your machine: the device binds `127.0.0.1` only and is reached through `adb forward`.

Full documentation, the Android quick start and the wire protocol: **https://github.com/yudistirosaputro/dimock**

Apache-2.0

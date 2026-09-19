# Security policy

## Supported versions

Only the latest published `0.x` version receives fixes.

## Reporting a vulnerability

Please do not open a public issue. Use GitHub's private vulnerability reporting on this repository ("Security" tab → "Report a vulnerability"). You will get an acknowledgement within 72 hours and a fix or a mitigation plan within 14 days for confirmed issues.

## What counts

dimock runs only in debug builds and binds `127.0.0.1`. Reports we care most about:

- Anything that makes the wire server reachable from outside the device without `adb forward`.
- Anything that lets the release (`no-op`) artifact start a server, store data, or change traffic.
- Redaction bypasses: a default-redacted header or configured body pattern that reaches storage or the wire in clear.
- Ways for the MCP server or CLI to reach a host other than `127.0.0.1`, or to write outside the working directory.

The full security model is described in `docs/security.md`.

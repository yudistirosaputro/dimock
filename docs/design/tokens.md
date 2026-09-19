# dimock inspector design tokens

Mockups: Claude Design canvas "dimock Inspector" (artboards: Notification, Traffic, Detail, Force a state, cURL, Mocks, Agent connected, Agent not connected).

Component set: the inspector covers everything the internal Tuntun debug inspector shows — status bar with Recording/Paused, path filter, filter chips, rows with a MOCK badge, detail with HEADERS/BODY per side and body search, one-tap "Force a state" options plus a Custom response editor, cURL sheet, local mocks with switch and long-press remove, one ongoing notification — redesigned generically in this system.

## Color (fixed theme, does not follow host app)

| Token | Value | Use |
|---|---|---|
| surface | #0E1012 | screen background |
| surfaceRaised | #15181B | sheets, code blocks, notification cards |
| hairline | #262B30 | primary dividers, borders |
| hairlineSoft | #1C2024 | list row dividers |
| control | #3A424A | outlined button borders, inactive switch |
| text | #E8ECEF | primary text |
| textMuted | #8B949E | secondary text, method labels |
| textDim | #5C6670 | tertiary text, timestamps |
| accent | #C6F135 | one meaning only: mocked. MOCK tag, active switch, primary CTA, row marker |
| onAccent | #0E1012 | text on accent |
| status5xx | #F06A5B | 5xx codes, `ERR` (transport failure), failure banners, destructive actions |
| status4xx | #F2B84B | 4xx codes, spent rules, Paused, truncation notes |
| status3xx | #7FB2F0 | 3xx codes only |
| snackbar | #E8ECEF on #0E1012 | inverted |

2xx codes use textMuted, not a colour. Methods are told apart by weight, not hue: GET textMuted, POST/PUT/PATCH text, DELETE status4xx, others textDim.

## Type

- UI: Space Grotesk 400/500/600 (bundled font resource)
- Mono: JetBrains Mono 400/500/600 for paths, status codes, numbers, timestamps, commands
- Sizes: header count 44/44 mono; screen title 26/30; row primary 14/18 mono; row secondary 12/16; labels 13; MOCK tag 10 mono, tracking 0.08em

## Shape and spacing

- Radius 4 dp everywhere (2 dp for tiny markers, 8 dp for sheets and notification cards)
- Row height 68 dp, tap targets 44 dp minimum, horizontal gutter 20 dp
- Row marker: 3 dp accent bar on the left of mocked rows
- Switch: 44x24 rectangular, 18 dp knob
- Chips: 36 dp tall, hairline (`control`) border; selected = accent border + accent text on 8 % accent fill
- Recording dot: 8 dp accent square, pulsing 1 → 0.35 alpha over 900 ms while recording; textDim and still when paused
- Code block: surfaceRaised, line numbers in textDim, search matches on `control`, the current match on accent with onAccent text

## Notification

Ongoing, silent, `IMPORTANCE_LOW`, accent tint. Title `dimock · N calls[ · M mocks]`; InboxStyle lines `GET /posts · 200 · 241 ms`, `POST /posts · 500 · 12 ms · MOCK`, `GET /posts/1 · timeout · MOCK`; `Waiting for traffic…` before the first call. Actions **Clear**, and **Disable mocks** while any rule is enabled. Rebuilt at most every 750 ms.

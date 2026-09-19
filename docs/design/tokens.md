# dimock inspector design tokens

Mockups: Claude Design canvas "dimock Inspector" (artboards: Notification, Traffic, Detail, Force a state, cURL, Mocks, Agent connected, Agent not connected).

Component set: the inspector covers everything the internal Tuntun debug inspector shows — status bar with Recording/Paused, path filter, filter chips, rows with a MOCK badge, detail with HEADERS/BODY per side and body search, one-tap "Force a state" options plus a Custom response editor, cURL sheet, local mocks with switch and long-press remove, one ongoing notification — redesigned generically in this system.

## Color (follows system dark/light, never the host app's)

The inspector renders its own tokens in both modes, so it stays recognisable in any app. It follows the **system night mode**, not the host app's Compose theme: it runs in its own Activity in its own task, so the host's composition never reaches it. `Config.inspectorTheme` (`System` by default, `Dark`, `Light`) forces one palette; an app that switches theme only through `AppCompatDelegate.setDefaultNightMode` should set it explicitly.

| Token | Dark | Light | Use |
|---|---|---|---|
| surface | #0E1012 | #F1F3F5 | screen background |
| surfaceRaised | #15181B | #FBFCFD | sheets, code blocks, notification cards |
| hairline | #262B30 | #D3D9DE | primary dividers, borders |
| hairlineSoft | #1C2024 | #E4E8EB | list row dividers |
| control | #5C6670 | #7E8894 | outlined button borders, inactive switch |
| text | #E8ECEF | #101417 | primary text |
| textMuted | #8B949E | #4E5964 | secondary text, method labels |
| textDim | #7A858F | #646F79 | tertiary text, timestamps |
| accent | #C6F135 | #C6F135 | fill only, one meaning: mocked. MOCK tag, active switch, primary CTA |
| accentText | #C6F135 | #4C6600 | the accent as ink: accent text actions, row marker, thin accent marks |
| onAccent | #0E1012 | #0E1012 | text on an accent fill |
| status5xx | #F06A5B | #B3251B | 5xx codes, `ERR` (transport failure), failure banners, destructive actions |
| status4xx | #F2B84B | #7A5200 | 4xx codes, spent rules, Paused, truncation notes |
| status3xx | #7FB2F0 | #1B5FAF | 3xx codes only |
| snackbarSurface | #E8ECEF | #15181B | snackbar background, inverted against the mode |
| snackbarText | #0E1012 | #F1F3F5 | snackbar text |

Contrast is measured, not assumed: in each mode every text token reaches at least 4.5:1 (WCAG AA) against both `surface` and `surfaceRaised` (the snackbar pair against each other), and `control` reaches at least 3:1 for non-text UI boundaries (WCAG 1.4.11). Light measures text 16.64:1, textMuted 6.43:1, textDim 4.61:1, accentText 5.89:1, status5xx 5.90:1, status4xx 6.22:1, status3xx 5.72:1. Two dark values were corrected against the same floor: textDim `#5C6670` -> `#7A858F` (was 3.26:1) and control `#3A424A` -> `#5C6670` (was 1.87:1).

**accent and accentText.** `accent #C6F135` means one thing in both modes, "mocked", but it is a **fill colour only**. Against the light surface it measures 1.18:1, so it can never be text or a thin mark there. `accentText` is the accent as ink on a surface: identical to `accent` in dark, `#4C6600` in light. MOCK badges and the active switch use the `accent` fill with `onAccent` text, and in light mode that filled shape takes a 1 px `accentText` outline so it has a defined edge against near-white. Anything accent-coloured that is text, a border, or a mark thinner than a badge uses `accentText`: the 3 dp row marker, accent text actions, the selected chip, the recording dot.

2xx codes use textMuted, not a colour. Methods are told apart by weight, not hue: GET textMuted, POST/PUT/PATCH text, DELETE status4xx, others textDim.

## Type

- UI: Space Grotesk 400/500/600 (bundled font resource)
- Mono: JetBrains Mono 400/500/600 for paths, status codes, numbers, timestamps, commands
- Sizes: header count 44/44 mono; screen title 26/30; row primary 14/18 mono; row secondary 12/16; labels 13; MOCK tag 10 mono, tracking 0.08em

## Shape and spacing

- Radius 4 dp everywhere (2 dp for tiny markers, 8 dp for sheets and notification cards)
- Row height 68 dp, tap targets 44 dp minimum, horizontal gutter 20 dp
- Row marker: 3 dp accentText bar on the left of mocked rows
- Switch: 44x24 rectangular, 18 dp knob
- Chips: 36 dp tall, hairline (`control`) border; selected = accentText border and label on 8 % accent fill
- Recording dot: 8 dp accentText square, pulsing 1 → 0.35 alpha over 900 ms while recording; textDim and still when paused
- Code block: surfaceRaised, line numbers in textDim, search matches on `control`, the current match on accent with onAccent text

## Notification

Ongoing, silent, `IMPORTANCE_LOW`, accent tint. Title `dimock · N calls[ · M mocks]`; InboxStyle lines `GET /posts · 200 · 241 ms`, `POST /posts · 500 · 12 ms · MOCK`, `GET /posts/1 · timeout · MOCK`; `Waiting for traffic…` before the first call. Actions **Clear**, and **Disable mocks** while any rule is enabled. Rebuilt at most every 750 ms. The tint stays `#C6F135` in both modes: the notification is drawn by the system shade, not by dimock's surfaces.

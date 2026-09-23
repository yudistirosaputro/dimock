# dimock inspector design tokens

Direction: **"Slate & Sand"**. Mockups: the direction-C boards (Traffic, Detail, Force a state, Editing, Mocks — dark and light, 390×844). The mockups are the spec for spacing and structure; `theme/Tokens.kt` and `theme/DimockTheme.kt` are the spec for colour, type and radius, and this file mirrors them.

Component set: the inspector covers everything the internal Tuntun debug inspector shows — status row with Recording/Paused, path filter, filter chips, grouped rows with a MOCK badge, detail with a Request | Response segmented control, HEADERS/BODY per side and body search, one-tap "Force a state" presets plus a Custom response editor, cURL sheet, local mocks with switch and long-press remove, one ongoing notification — redesigned generically in this system. Cool slate neutrals carry the structure; one warm accent, sand, means exactly one thing: mocked.

## Color (follows system dark/light, never the host app's)

The inspector renders its own tokens in both modes, so it stays recognisable in any app. It follows the **system night mode**, not the host app's Compose theme: it runs in its own Activity in its own task, so the host's composition never reaches it. `Config.inspectorTheme` (`System` by default, `Dark`, `Light`) forces one palette; an app that switches theme only through `AppCompatDelegate.setDefaultNightMode` should set it explicitly.

| Token | Dark | Light | Use |
|---|---|---|---|
| surface | #15181D | #F2F3F5 | screen background |
| surfaceRaised | #1C2027 | #FFFFFF | grouped lists, cards, sheets, the floating bottom bar |
| surfaceSunken | #10131A | #E9EBEF | wells: text fields, code blocks, the segmented-control track; sits below the surface |
| hairline | #2A3038 | #E0E3E8 | primary dividers, the border of wells and of the floating bar |
| hairlineSoft | #252A32 | #EAECEF | row dividers inside a group |
| control | #646F7C | #78828E | outlined button borders, idle chip borders, the inactive switch, the "/N" of the Mocks count |
| text | #E6E9ED | #171A1F | primary text |
| textMuted | #98A1AC | #4F5864 | secondary text, 2xx codes, GET |
| textDim | #7F8A96 | #616B77 | tertiary text, timestamps, line numbers, the paused recording dot |
| accent | #D8C3A0 | #836136 | one meaning: mocked. MOCK tag fill, selected chip, active switch, primary button, wordmark square, tab indicator |
| accentText | #D8C3A0 | #836136 | the accent as ink: accent text actions, the Mocks count, code strings. Identical to `accent` in this palette |
| onAccent | #1A1712 | #FFFFFF | text on an accent fill: near-black on sand in dark, white on bronze in light |
| accentSoft | #363738 | #F0ECE7 | the accent at ~14 % over `surfaceRaised`, pre-blended: mock banner fill, focused-editor ring, highlighted Mocks row |
| status5xx | #EF6B62 | #BE3A33 | 5xx codes, `ERR` (transport failure), failure text, the recording dot, destructive actions (Clear all) |
| status4xx | #EC8F87 | #A34D47 | 4xx codes, spent rules and their Reset action, Paused, warnings |
| status3xx | #8FA8C8 | #3D5A80 | 3xx codes only |
| snackbarSurface | #E6E9ED | #1C2027 | snackbar background, inverted against the mode |
| snackbarText | #15181D | #F2F3F5 | snackbar text |

**accent and accentText.** Sand means one thing in both modes, "mocked". Unlike the previous palette it clears WCAG AA as ink on every surface in both modes — `#D8C3A0` on the dark surfaces, the deeper bronze `#836136` on the light ones — so `accentText == accent` and a filled shape needs no outline in either mode. The field stays separate so a future palette can split fill and ink again. MOCK badges, the selected chip and the active switch are an `accent` fill with `onAccent` text; accent text actions, the rules-active count and JSON strings in a code block are `accentText`. `accentSoft` is the accent already blended over the raised surface at about 14 %, so a soft fill never depends on what sits under it.

**Status colours are a red family.** 5xx is the full red, 4xx the same red softened — not amber, because the panel has exactly one warm hue and that hue is the accent. 3xx is a muted slate blue and is rare on a row. 2xx codes use `textMuted`, not a colour; a missing code (transport failure) reads as severe as a 5xx. Methods are told apart by weight of colour, not hue: GET `textMuted`, POST/PUT/PATCH and DELETE `text`, anything else `textDim`.

Contrast is measured, not assumed: `PaletteContrastTest` (`dimock-ui/src/test/.../theme/`) computes real WCAG 2.x ratios for every ink (`text`, `textMuted`, `textDim`, `accentText`, `status5xx`, `status4xx`, `status3xx`) against all three surfaces — `surface`, `surfaceRaised` and `surfaceSunken` — plus `onAccent` on `accent` and the snackbar pair against each other, at the 4.5:1 text floor, and `control` against `surface` and `surfaceRaised` at the 3:1 non-text floor (WCAG 1.4.11). The test prints the measured ratio of every probe in its report; that report, not this file, is where the numbers live. Changing a value without re-running it is how the panel stops being readable.

## Type

- UI: **Plus Jakarta Sans** 400/500/600 — everything a person reads
- Mono: **DM Mono** 400/500 — everything a machine produced: paths, status codes, durations, timestamps, headers, bodies, commands
- Both are bundled as latin-subset TTFs under `dimock-ui/src/main/res/font/` (`plus_jakarta_sans_{regular,medium,semibold}`, `dm_mono_{regular,medium}`), SIL Open Font License 1.1; the licences ship in `dimock-ui/fonts-license/`. No download, no system-font fallback drift.
- Every style trims platform font padding and centres the line height, so text sits on its line box.

`DimockType` scale (size/line):

| Style | Face | Size/line | Weight, tracking | Use |
|---|---|---|---|---|
| StatusBig | sans | 56/52 | SemiBold, −0.04em | the status code (or `ERR`) on Detail |
| HeaderCount | sans | 44/44 | SemiBold, −0.03em | the rules-active count on Mocks |
| ScreenTitle | sans | 26/30 | SemiBold, −0.02em | "Agent connected" |
| SheetTitle | sans | 20/26 | SemiBold | sheet titles |
| Wordmark | sans | 15/20 | SemiBold, 0.01em | "dimock" |
| Body | sans | 14/20 | Regular | body copy, key labels |
| BodyStrong | sans | 15/20 | Medium | row titles, preset titles |
| Label | sans | 13/18 | Medium | buttons, text actions, banner text |
| Caption | sans | 12/16 | Regular | hints, row second lines, descriptions |
| Tab | sans | 13/18 | Regular (SemiBold when active) | floating-bar tab titles |
| MonoRow | mono | 14/18 | Regular | the path on a row, field text |
| MonoSmall | mono | 12/16 | Regular | durations, meta, key-value values |
| MonoCode | mono | 13/20 | Regular | code blocks, commands, the connection line |
| MonoStatus | mono | 14/18 | Medium | the status code in a Traffic row |
| MonoTiny | mono | 10/12 | Medium, 0.08em | the method under the code |
| MockTag | mono | 10/12 | Medium, 0.06em | the MOCK badge |
| SectionLabel | mono | 11/14 | Regular, 0.06em | HEADERS / BODY / STATUS labels, the Mocks count in the bar |

## Shape and spacing

- Radii are layered, never uniform — the further out a shape sits, the softer its corners: **14 dp** containers (grouped lists, cards), **10 dp** controls (chips, buttons, fields, code blocks, banners, the segmented control), **6 dp** tags (MOCK badge, switch knob, tab indicator), **24 dp** sheet top corners, **18 dp** the floating bottom bar. Nothing is a pill; `CircleShape` is used only for the 8 dp recording dot and the Agent status dot.
- Rows 72 dp minimum, 16 dp horizontal padding inside a card; tap targets 44 dp minimum; screen content gutter 20 dp; grouped lists and the floating bar inset 14 dp from the screen edge. Row dividers are `hairlineSoft`, inset 76 dp on Traffic (past the status column, under the path) and 16 dp on Mocks and Agent; never after the last row.
- No row markers and no accent outlines: a mocked Traffic row carries the MOCK badge only; a highlighted Mocks row gets an `accentSoft` background.
- Floating bottom bar: 60 dp tall, `surfaceRaised`, 18 dp radius, 1 dp `hairline` border, 8 dp shadow, 16 dp above the navigation-bar inset. Active tab = `text` SemiBold plus a 16×3 dp `accent` pill 8 dp above the bar's bottom edge; inactive `textMuted`. The Scaffold reserves nothing for it — every scrolling screen leaves **92 dp** of clearance under itself so the bar never covers its last row. Hidden on Detail.
- Switch: 44×26 rounded rectangle (9 dp), 20 dp knob at 6 dp; `accent` fill with an `onAccent` knob when on, a 2 dp `control` outline with a `control` knob when off.
- Chips: 34 dp tall, `control` outline and muted mono text when idle; a solid `accent` fill with `onAccent` text when selected.
- Buttons: primary `AccentButton` 48 dp sand fill; `GhostButton` 48 dp control outline; `OutlinedAction` 36 dp outline in its text colour (`control` for plain text); `TextAction` 44 dp accent ink. Destructive = `status5xx`, spent = `status4xx`.
- Recording dot: an 8 dp `status5xx` circle inside a 3 dp soft ring of `status5xx` at 18 % alpha, pulsing 1 → 0.35 alpha over 900 ms while recording; `textDim`, no ring, still when paused. It is a record light, not an accent mark.
- Wordmark: a 12 dp sand square (3 dp radius), "dimock", the package in mono dim.
- Wells (`Well`): `surfaceSunken` with a 1 dp `hairline` edge and the control radius, for text fields, the STATUS field and code blocks. Focused = `accent` edge plus a 3 dp `accentSoft` ring outside it.
- Code block: line numbers `textDim`, JSON keys `textMuted`, strings `accentText`; a search hit sits on a `control` fill (3 dp radius), the current hit on `accent` with `onAccent` text.
- Segmented control (Request | Response): a sunken, hairline-edged track with 3 dp padding; the selected pane is `surfaceRaised`, shadowed, 7 dp radius, `text` SemiBold; header counts in `SectionLabel` dim.
- Banner: a soft fill — `accentSoft` for "mocked", the status colour at 12 % for a failure or warning — with an 8 dp dot of the colour at the start, no border.
- Sheets: `surfaceRaised`, 24 dp top corners, a 36×4 dp `control` handle. **Custom response while editing:** the sheet skips the partially expanded state, its column takes IME padding, and the BODY editor (a focused `Well`, 120–240 dp tall, scrolling inside) requests to be brought into view when it gains focus and when the Custom row opens — so tapping into the body expands the sheet fully and scrolls the whole editor above the keyboard, never half hidden.

## Notification

Ongoing, silent, `IMPORTANCE_LOW`, sand tint. Title `dimock · N calls[ · M mocks]`; InboxStyle lines `GET /posts · 200 · 241 ms`, `POST /posts · 500 · 12 ms · MOCK`, `GET /posts/1 · timeout · MOCK`; `Waiting for traffic…` before the first call. Actions **Clear**, and **Disable mocks** while any rule is enabled. Rebuilt at most every 750 ms. The tint stays `#D8C3A0` in both modes: the notification is drawn by the system shade, not by dimock's surfaces.

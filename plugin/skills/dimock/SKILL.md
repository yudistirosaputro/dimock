---
name: dimock
description: Drive an Android screen through its UI states (loading, success, empty, error, slow, timeout) by capturing real HTTP traffic and injecting mock responses on the device. Use when the user asks to mock, intercept, fake or capture mobile network calls, test an error or empty state, or install dimock in an Android project.
---

You control the device through the `dimock` MCP tools. The human holds the phone: you cannot tap the app, so every step that needs the screen ends with a **stop** where you tell them exactly what to do and wait.

## Branches

- **Install** when the project has no dimock dependency yet → follow "Install" then continue with "Drive states".
- **Drive states** when the app already runs dimock → start at step 1.
- **Debug a capture** ("what did the app send?") → steps 1 and 2 only, then answer from `captures_get`.

## Install

1. Add to the app module's Gradle file, matching its existing dependency style:
   `debugImplementation("io.github.yudistirosaputro:dimock-ui:<version>")` and `releaseImplementation("io.github.yudistirosaputro:dimock-okhttp-no-op:<version>")`. If the app has no Compose, use `dimock-okhttp` instead of `dimock-ui`.
2. Find where the `OkHttpClient` is built and add `.addInterceptor(Dimock.interceptor())` as the first application interceptor. Import `com.yudistirosaputro.dimock.okhttp.Dimock`. No `BuildConfig.DEBUG` check: the release artifact is a no-op.
3. **Stop**: ask the human to run the debug build on a device or emulator and open any screen that makes a network call.
4. Done when `health` answers for the app. If it does not: `devices_list` (is the device authorised?), then `apps_list` (is the debug build the one running? port overridden with `com.yudistirosaputro.dimock.PORT`?).

## Drive states

1. `health` to confirm the target. If it answers with candidates, ask which device or app, then retry with `device`/`app` set. Done when you have the app id.
2. **Stop**: ask the human to open the screen under test once so the real call is captured. Then `captures_list` (filter with `path` if the app is chatty). Done when you can name the capture id of the call that feeds the screen. If two calls feed it, pick both.
3. Pick the variants the task asks for; when the user just says "test the states", use `empty`, `error`, `slow`, `timeout` in that order. For each variant:
   - `mock_from_capture` with `dryRun: true`; read the rule. Adjust with `times: 1` when the state should only happen once, or with your own `mock_add` when the body needs a specific shape (docs/rule-format.md).
   - Apply it (`mock_from_capture` without dryRun, or `mock_add`).
   - **Stop**: tell the human "reload the screen now" and what they should see (for example "empty state, no crash").
   - When they report back, check `logcat_tail` with `pattern: "FATAL|Exception"` if anything looked wrong, and `captures_list mocked: true` to confirm the mocked response was served.
   - `mock_clear` with that rule's id before the next variant, unless the user wants to keep it.
   Done when every chosen variant has been observed by the human and you have written one line per variant: what was shown, whether it matched expectations.
4. Finish with `mock_clear` (all) and confirm `health` shows 0 active mocks. Never end a task with mocks left on the device; a forgotten mock is the most confusing bug a tester can meet.

## Rules of the road

- Use `--json`/structured results for decisions; quote the human-readable text when reporting.
- Edit rules on the device only through the tools; the human can toggle them in the app's Mocks tab, and the Agent tab shows them everything you did.
- A `403 agent_write_disabled` means the human switched off "Agent may change mock rules" on the device. Say so and ask them to enable it; do not retry.
- Sequences express transitions: `[timeout, 200]` shows retry behaviour, `[200 with items, 200 empty]` shows a list emptying. Reach for them when the task is about what happens between states, not the states themselves.
- Bodies you invent should keep the captured envelope (`data`, `meta`) so the app's parser stays happy; `empty` does this for you.

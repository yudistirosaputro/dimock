---
name: dimock
description: Drive an Android screen through its UI states (loading, success, empty, error, slow, timeout) by capturing real HTTP traffic and injecting mock responses on the device. Use when the user asks to mock, intercept, fake or capture mobile network calls, test an error or empty state, or install dimock in an Android project.
---

You control the network through the `dimock` MCP tools. Steps that need the screen are marked **Screen**. Before the first one, check whether you have a device-interaction tool (argent, mobile-mcp, adb `input`, or similar):

- **With one**, do the step yourself: launch or reload the app, navigate, then read the result from a screenshot plus the element tree. Stop only when a step needs something you cannot do (sign-in with the human's credentials, a physical gesture, a decision).
- **Without one**, the human holds the phone: stop, tell them exactly what to do and what they should see, and wait.

## Branches

- **Install** when the project has no dimock dependency yet → follow "Install" then continue with "Drive states".
- **Drive states** when the app already runs dimock → start at step 1.
- **Debug a capture** ("what did the app send?") → steps 1 and 2 only, then answer from `captures_get`.

## Install

1. Add to the app module's Gradle file, matching its existing dependency style:
   `debugImplementation("io.github.yudistirosaputro:dimock-ui:<version>")` and `releaseImplementation("io.github.yudistirosaputro:dimock-okhttp-no-op:<version>")`. If the app has no Compose, use `dimock-okhttp` instead of `dimock-ui`.
2. Find where the `OkHttpClient` is built and add `.addInterceptor(Dimock.interceptor())` as the first application interceptor. Import `com.yudistirosaputro.dimock.okhttp.Dimock`. No `BuildConfig.DEBUG` check: the release artifact is a no-op.
3. **Screen**: run the debug build on a device or emulator and open any screen that makes a network call.
4. Done when `health` answers for the app. If it does not: `devices_list` (is the device authorised?), then `apps_list` (is the debug build the one running? port overridden with `com.yudistirosaputro.dimock.PORT`?).

## Drive states

1. `health` to confirm the target. If it answers with candidates, ask which device or app, then retry with `device`/`app` set. Done when you have the app id.
2. **Screen**: open the screen under test once so the real call is captured. Then `captures_list` (filter with `path` if the app is chatty). Done when you can name the capture id of the call that feeds the screen. If two calls feed it, pick both.
3. Pick the variants the task asks for; when the user just says "test the states", use `empty`, `error`, `slow`, `timeout` in that order. For each variant:
   - `mock_from_capture` with `dryRun: true` (by `captureId`, or by `path` for the newest capture); read the rule. `error` and `unauthorized` reuse a real error body from the same host when one was captured. Adjust with `times: 1` when the state should only happen once, with `status` / `body` / `bodyFile` when the API's error shape matters, or with your own `mock_add` for anything else (docs/rule-format.md).
   - Apply it (`mock_from_capture` without dryRun, or `mock_add`).
   - **Screen**: reload the screen and compare it with what the variant should show (for example "empty state, no crash").
   - Then check `logcat_tail` with `pattern: "FATAL|Exception"` if anything looked wrong, and `captures_list mocked: true` to confirm the mocked response was served.
   - `mock_clear` with that rule's id before the next variant, unless the user wants to keep it.
   Done when every chosen variant has been observed (by you or the human) and you have written one line per variant: what was shown, whether it matched expectations.
4. Finish with `mock_clear` (all) and confirm `health` shows 0 active mocks. Never end a task with mocks left on the device; a forgotten mock is the most confusing bug a tester can meet.

## Rules of the road

- Use `--json`/structured results for decisions; quote the human-readable text when reporting.
- Edit rules on the device only through the tools; the human can toggle them in the app's Mocks tab, and the Agent tab shows them everything you did.
- A `403 agent_write_disabled` means the human switched off "Agent may change mock rules" on the device. Say so and ask them to enable it; do not retry.
- Sequences express transitions: `[timeout, 200]` shows retry behaviour, `[200 with items, 200 empty]` shows a list emptying. Reach for them when the task is about what happens between states, not the states themselves.
- Bodies you invent should keep the captured envelope (`data`, `meta`) so the app's parser stays happy; `empty` does this for you.

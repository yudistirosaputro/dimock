import assert from "node:assert/strict";
import { test } from "node:test";
import { Session } from "../session.js";

/** Runs only against a real wire server (device or `./gradlew :dimock-core:runDevServer`). */
const baseUrl = process.env.DIMOCK_BASE_URL;

test("integration: TypeScript client against the real wire server", { skip: !baseUrl && "set DIMOCK_BASE_URL" }, async () => {
  const session = new Session({ baseUrl, clientName: "ts-integration" });
  const health = await session.health();
  assert.equal(health.protocol, 1);

  await session.mockSet(undefined, {
    text: `- id: it-login\n  name: "Login → 500"\n  priority: 10\n  match: { method: POST, path: /v1/auth/login }\n  times: 1\n  respond: { status: 500, body: { error: internal }, delayMs: 800 }\n- id: it-seq\n  match: { path: "/v1/orders**" }\n  sequence:\n    - fail: { type: timeout }\n    - respond: { status: 200, body: "[]" }`,
  });
  const rules = await session.mockList();
  assert.deepEqual(rules.map((r) => r.id).sort(), ["it-login", "it-seq"]);
  assert.equal(rules.find((r) => r.id === "it-login")?.respond?.body, '{"error":"internal"}');
  assert.equal(rules.find((r) => r.id === "it-login")?.state.remaining, 1);

  const toggled = await session.mockToggle(undefined, "it-seq", false);
  assert.equal(toggled.enabled, false);
  await session.mockReset(undefined, "it-seq");

  const captures = await session.capturesList(undefined, { limit: 5 });
  if (captures[0]) {
    const full = await session.capturesGet(undefined, captures[0].id);
    assert.match(full.asCurl, /^curl/);
    const { rule, applied } = await session.mockFromCapture(undefined, captures[0].id, "error", { dryRun: true });
    assert.equal(rule.respond?.status, 500);
    assert.equal(applied, null);
  }

  const activity = await session.activity();
  assert.ok(activity.some((a) => a.call === "PUT /rules"));
  await session.mockClear(undefined);
  assert.equal((await session.mockList()).length, 0);
});

import assert from "node:assert/strict";
import { mkdtemp, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { loadRulesFile, parseRules, rulesToYaml, validateRule } from "../rules.js";
import { DimockError, type Transaction } from "../types.js";
import { emptyBody, ruleFromCapture } from "../variants.js";

test("parseRules accepts a single rule, a list, and { rules }", () => {
  const single = parseRules(`id: a\nmatch: { path: /a }\nrespond: { status: 204 }`);
  assert.equal(single.length, 1);
  const list = parseRules(`- id: a\n  match: {}\n  respond: { status: 200 }\n- id: b\n  match: {}\n  fail: { type: timeout }`);
  assert.deepEqual(list.map((r) => r.id), ["a", "b"]);
  const wrapped = parseRules(`{"rules":[{"id":"x","match":{},"respond":{"status":200}}]}`);
  assert.equal(wrapped[0]?.id, "x");
});

test("body written as a YAML mapping is serialised to a JSON string", () => {
  const [rule] = parseRules(`id: a\nmatch: {}\nrespond:\n  status: 200\n  body:\n    data: []\n    total: 0`);
  assert.equal(rule?.respond?.body, '{"data":[],"total":0}');
});

test("validation errors name the field", () => {
  assert.throws(() => validateRule({ id: "x", match: {} }), (e: unknown) => e instanceof DimockError && /respond \| fail/.test(e.message));
  assert.throws(() => validateRule({ match: {}, respond: { status: 999 } }), /status: must be 100\.\.599/);
  assert.throws(() => validateRule({ match: {}, fail: { type: "explode" } }), /fail\.type/);
  assert.throws(() => validateRule({ match: {}, respond: {}, times: 0 }), /times/);
  assert.throws(() => validateRule({ match: {}, sequence: [{ nope: 1 }] }), /sequence\[0\]/);
  assert.throws(() => parseRules("just: [a"), /not valid YAML/);
});

test("loadRulesFile inlines bodyFile relative to the rules file and drops the bodyFile key", async () => {
  const dir = await mkdtemp(join(tmpdir(), "dimock-"));
  await writeFile(join(dir, "portfolio_ok.json"), '{"positions":[1]}');
  await writeFile(
    join(dir, "rules.yaml"),
    `- id: seq\n  match: { path: "/v1/orders**" }\n  sequence:\n    - fail: { type: timeout }\n    - respond: { status: 200, bodyFile: portfolio_ok.json }\n- id: one\n  match: {}\n  respond: { bodyFile: portfolio_ok.json }\n`,
  );
  const rules = await loadRulesFile(join(dir, "rules.yaml"));
  const step = rules[0]?.sequence?.[1];
  assert.ok(step && "respond" in step);
  assert.equal(step.respond.body, '{"positions":[1]}');
  assert.equal("bodyFile" in step.respond, false);
  assert.equal(rules[1]?.respond?.body, '{"positions":[1]}');
  await assert.rejects(loadRulesFile(join(dir, "missing.yaml")), /rules file not found/);
});

test("rulesToYaml drops runtime state", () => {
  const yaml = rulesToYaml([{ id: "a", match: {}, respond: { status: 200 }, state: { hits: 3 } } as never]);
  assert.match(yaml, /id: a/);
  assert.doesNotMatch(yaml, /hits/);
});

const captured: Transaction = {
  id: "t1", startedAt: 1, durationMs: 241, method: "GET", url: "https://api.example.com/v1/portfolio/summary", host: "api.example.com", path: "/v1/portfolio/summary",
  responseCode: 200, mocked: false, requestBytes: 0, responseBytes: 60, requestHeaders: {}, requestBody: null,
  responseHeaders: { "Content-Type": ["application/json; charset=utf-8"] },
  responseBody: { kind: "text", text: '{"data":{"positions":[{"symbol":"BBCA"}],"total":1250000,"owner":{"name":"Y"}},"meta":{"page":1,"count":1}}', contentType: "application/json; charset=utf-8", totalBytes: 60 },
};

test("ruleFromCapture targets method+path and shapes each variant", () => {
  const error = ruleFromCapture(captured, "error");
  assert.deepEqual(error.match, { method: "GET", path: "/v1/portfolio/summary" });
  assert.equal(error.respond?.status, 500);
  assert.equal(error.id, "v1-portfolio-summary-error");

  assert.equal(ruleFromCapture(captured, "unauthorized").respond?.status, 401);

  const empty = ruleFromCapture(captured, "empty");
  assert.equal(empty.respond?.status, 200);
  assert.deepEqual(JSON.parse(empty.respond!.body!), { data: { positions: [], total: 0, owner: null }, meta: { page: 1, count: 0 } });

  const slow = ruleFromCapture(captured, "slow");
  assert.equal(slow.respond?.delayMs, 3000);
  assert.equal(slow.respond?.body, captured.responseBody && "text" in captured.responseBody ? captured.responseBody.text : "");

  assert.equal(ruleFromCapture(captured, "timeout").fail?.type, "timeout");
  assert.equal(ruleFromCapture(captured, "malformed").fail?.type, "malformed_body");
  assert.equal(ruleFromCapture(captured, "error", { times: 1, id: "custom" }).times, 1);
  assert.throws(() => ruleFromCapture(captured, "weird" as never), /unknown variant/);
});

test("ruleFromCapture takes status and body overrides for response variants", () => {
  const error = ruleFromCapture(captured, "error", { status: 422, body: '{"success":false}', contentType: "application/problem+json" });
  assert.equal(error.respond?.status, 422);
  assert.equal(error.respond?.body, '{"success":false}');
  assert.equal(error.respond?.headers?.["Content-Type"], "application/problem+json");
  assert.equal(ruleFromCapture(captured, "empty", { status: 206 }).respond?.status, 206);
  assert.equal(ruleFromCapture(captured, "slow", { body: "[]" }).respond?.body, "[]");
  assert.throws(() => ruleFromCapture(captured, "timeout", { status: 500 }), /status and body apply to error, unauthorized, empty, slow/);
  assert.throws(() => ruleFromCapture(captured, "error", { status: 99 }), /status: must be 100\.\.599/);
});

test("emptyBody heuristics", () => {
  assert.equal(emptyBody('[{"a":1}]'), "[]");
  assert.equal(emptyBody(undefined), "[]");
  assert.equal(emptyBody("<html>"), "");
  assert.equal(emptyBody('{"items":[1,2],"count":2,"next":"abc"}'), '{"items":[],"count":0,"next":"abc"}');
});

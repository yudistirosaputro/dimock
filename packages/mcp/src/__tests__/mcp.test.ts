import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { Adb } from "@dimock/core";
import { FakeWire } from "../../../core/src/__tests__/fake-wire.js";
import { createMcpServer } from "../index.js";
import { tools } from "../tools.js";

let fake: FakeWire;
let client: Client;

before(async () => {
  fake = new FakeWire();
  const baseUrl = await fake.start();
  const server = createMcpServer({ baseUrl, adb: new Adb(async () => { throw new Error("adb not expected"); }) });
  const [a, b] = InMemoryTransport.createLinkedPair();
  await server.connect(a);
  client = new Client({ name: "test", version: "0" });
  await client.connect(b);
});
after(async () => {
  await client.close();
  await fake.stop();
});

test("every catalogue tool is registered with a schema", async () => {
  const listed = await client.listTools();
  const names = listed.tools.map((t) => t.name).sort();
  assert.deepEqual(names, tools.map((t) => t.name).sort());
  const fromCapture = listed.tools.find((t) => t.name === "mock_from_capture")!;
  assert.ok(JSON.stringify(fromCapture.inputSchema).includes("captureId"));
  assert.equal(fromCapture.annotations?.readOnlyHint, false);
  assert.equal(listed.tools.find((t) => t.name === "captures_list")?.annotations?.readOnlyHint, true);
});

test("tools return text plus structuredContent, and errors are isError with a code", async () => {
  fake.addTransaction({ id: "c1", method: "GET", path: "/v1/portfolio", responseBody: { kind: "text", text: '{"data":[1]}', contentType: "application/json", totalBytes: 12 } });
  const list = await client.callTool({ name: "captures_list", arguments: {} });
  assert.match((list.content as Array<{ text: string }>)[0]!.text, /c1 {2}200 {2}GET \/v1\/portfolio/);
  assert.equal(((list.structuredContent as { result: unknown[] }).result).length, 1);

  const derived = await client.callTool({ name: "mock_from_capture", arguments: { captureId: "c1", variant: "empty" } });
  assert.equal(derived.isError, undefined);
  const sc = derived.structuredContent as { rule: { respond: { body: string } }; applied: { id: string } };
  assert.equal(sc.rule.respond.body, '{"data":[]}');
  assert.equal(sc.applied.id, "v1-portfolio-empty");

  const gated = await (async () => {
    fake.agentWriteEnabled = false;
    try {
      return await client.callTool({ name: "mock_clear", arguments: {} });
    } finally {
      fake.agentWriteEnabled = true;
    }
  })();
  assert.equal(gated.isError, true);
  assert.equal((gated.structuredContent as { error: string }).error, "agent_write_disabled");

  const missing = await client.callTool({ name: "captures_get", arguments: { id: "nope" } });
  assert.equal(missing.isError, true);
  assert.equal((missing.structuredContent as { error: string }).error, "not_found");
});

test("mock_set accepts inline YAML and mock_list reflects it", async () => {
  const set = await client.callTool({ name: "mock_set", arguments: { text: "- id: y\n  match: { path: /y }\n  respond: { status: 204 }" } });
  assert.match((set.content as Array<{ text: string }>)[0]!.text, /1 rule active/);
  const list = await client.callTool({ name: "mock_list", arguments: {} });
  assert.match((list.content as Array<{ text: string }>)[0]!.text, /on {2}y {2}\/y {2}→ 204/);
});

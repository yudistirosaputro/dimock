import assert from "node:assert/strict";
import { mkdtemp, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, beforeEach, test } from "node:test";
import { Adb, parseDevices } from "../adb.js";
import { asCurl } from "../curl.js";
import { AmbiguousTargetError, Session } from "../session.js";
import { DimockError } from "../types.js";
import { WireClient } from "../wire.js";
import { FakeWire } from "./fake-wire.js";

let fake: FakeWire;
let baseUrl: string;
let client: WireClient;

before(async () => {
  fake = new FakeWire();
  baseUrl = await fake.start();
});
after(async () => fake.stop());
beforeEach(() => {
  fake.rules.clear();
  fake.transactions.length = 0;
  fake.agentWriteEnabled = true;
  fake.protocol = 1;
  client = new WireClient({ baseUrl, clientName: "test" });
});

const rule = { id: "login-error", match: { method: "POST", path: "/v1/auth/login" }, respond: { status: 500, body: '{"error":"internal"}' } };

test("WireClient round-trips rules, transactions and activity", async () => {
  const health = await client.health();
  assert.equal(health.protocol, 1);
  const put = await client.replaceRules([rule]);
  assert.equal(put[0]?.id, "login-error");
  assert.equal(put[0]?.state.hits, 0);
  const added = await client.addRule({ match: {}, fail: { type: "timeout" } });
  assert.ok(added.id.startsWith("r-"));
  const toggled = await client.patchRule("login-error", { enabled: false });
  assert.equal(toggled.enabled, false);
  await client.deleteRule(added.id);
  assert.equal((await client.listRules()).length, 1);

  fake.addTransaction({ id: "a", path: "/v1/x", mocked: true });
  fake.addTransaction({ id: "b", path: "/v1/y" });
  assert.deepEqual((await client.listTransactions()).map((t) => t.id), ["b", "a"]);
  assert.equal((await client.listTransactions({ mocked: true })).length, 1);
  const full = await client.getTransaction("a");
  assert.equal(full.responseBody?.kind, "text");
  await client.clearTransactions();
  assert.equal((await client.listTransactions()).length, 0);
  assert.ok((await client.activity()).some((a) => a.kind === "connect"));
});

test("errors are actionable: 404, 403 gating, unreachable, protocol mismatch", async () => {
  await assert.rejects(client.getTransaction("nope"), (e: unknown) => e instanceof DimockError && e.code === "not_found");
  fake.agentWriteEnabled = false;
  await assert.rejects(client.replaceRules([rule]), (e: unknown) => e instanceof DimockError && e.code === "agent_write_disabled" && /Agent tab/.test(e.message));
  assert.equal((await client.listRules()).length, 0, "reads still work");
  const dead = new WireClient({ baseUrl: "http://127.0.0.1:1", timeoutMs: 500 });
  await assert.rejects(dead.health(), (e: unknown) => e instanceof DimockError && e.code === "unreachable" && /adb forward/.test(e.message));
  fake.protocol = 2;
  await assert.rejects(client.health(), (e: unknown) => e instanceof DimockError && e.code === "protocol_mismatch" && /npm i -g dimock@latest/.test(e.message));
});

test("events stream parses SSE frames", async () => {
  const seen: string[] = [];
  const controller = new AbortController();
  const done = client.events((e) => {
    seen.push(e.type);
    if (seen.length === 2) controller.abort();
  }, controller.signal);
  await new Promise((r) => setTimeout(r, 100));
  fake.emit("transaction", { id: "x" });
  await client.addRule(rule);
  await done;
  assert.deepEqual(seen, ["transaction", "rules_changed"]);
});

test("asCurl reproduces the request with redacted headers kept redacted", () => {
  const tx = fake.addTransaction({
    id: "c", method: "POST", url: "https://api.example.com/v1/auth/login?x=1", path: "/v1/auth/login",
    requestHeaders: { Authorization: ["«redacted»"], "Content-Type": ["application/json"], Host: ["api.example.com"] },
    requestBody: { kind: "text", text: '{"u":"a"}', contentType: "application/json", totalBytes: 9 },
  });
  const curl = asCurl(tx);
  assert.match(curl, /^curl -X POST 'https:\/\/api\.example\.com\/v1\/auth\/login\?x=1'/);
  assert.match(curl, /-H 'Authorization: «redacted»'/);
  assert.doesNotMatch(curl, /Host:/);
  assert.match(curl, /--data-raw '\{"u":"a"\}'/);
});

test("asCurl quotes URLs with shell metacharacters so the line pastes into bash and zsh", () => {
  const withQuery = fake.addTransaction({ id: "q", url: "https://api.themoviedb.org/3/discover/movie?language=en-US&with_genres=%2C12%2C16&page=1", path: "/3/discover/movie" });
  assert.equal(asCurl(withQuery), "curl -X GET 'https://api.themoviedb.org/3/discover/movie?language=en-US&with_genres=%2C12%2C16&page=1'");
  const glob = fake.addTransaction({ id: "g", url: "https://api.example.com/files/*", path: "/files/*" });
  assert.equal(asCurl(glob), "curl -X GET 'https://api.example.com/files/*'");
  const plain = fake.addTransaction({ id: "p", url: "https://api.example.com/v1/posts", path: "/v1/posts" });
  assert.equal(asCurl(plain), "curl -X GET https://api.example.com/v1/posts");
});

// ---- adb + session ------------------------------------------------------------------------------------------

const DEVICES = `List of devices attached
emulator-5554          device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 device:emu64a transport_id:1
R58M12345AB            device usb:1-1 product:beyond1 model:SM_G973F device:beyond1 transport_id:2
0123456789             unauthorized transport_id:3
`;

test("parseDevices", () => {
  const list = parseDevices(DEVICES);
  assert.equal(list.length, 3);
  assert.equal(list[1]?.model, "SM G973F");
  assert.equal(list[2]?.state, "unauthorized");
});

function fakeAdb(devicesOutput: string, port: number) {
  const calls: string[][] = [];
  const runner = async (args: string[]) => {
    calls.push(args);
    const cmd = args.join(" ");
    if (cmd === "devices -l") return devicesOutput;
    if (/forward tcp:0 tcp:\d+/.test(cmd)) return `${port}\n`;
    if (/getprop ro.build.version.sdk/.test(cmd)) return "34\n";
    if (/getprop ro.product.model/.test(cmd)) return "Pixel\n";
    if (/pidof/.test(cmd)) return "4242\n";
    if (/logcat/.test(cmd)) return "09-14 10:00:00.000 E/AndroidRuntime( 4242): FATAL EXCEPTION: main\n09-14 10:00:00.001 I/Foo( 4242): fine\n";
    return "";
  };
  return { adb: new Adb(runner), calls };
}

test("Session resolves a single device + app through adb forward and runs the tools", async () => {
  const port = Number(new URL(baseUrl).port);
  const { adb, calls } = fakeAdb(DEVICES.split("\n").filter((l) => !l.includes("R58M") && !l.includes("0123")).join("\n"), port);
  const session = new Session({ adb, clientName: "test" });

  const devices = await session.devicesList();
  assert.equal(devices[0]?.apiLevel, 34);

  const apps = await session.appsList();
  assert.equal(apps[0]?.app, "com.yudistirosaputro.dimock.fake");
  assert.equal(apps[0]?.localPort, port);

  fake.addTransaction({ id: "cap", method: "GET", path: "/v1/portfolio/summary", responseBody: { kind: "text", text: '{"items":[1]}', contentType: "application/json", totalBytes: 13 } });
  const { rule: derived, applied } = await session.mockFromCapture(undefined, "cap", "empty");
  assert.equal(derived.respond?.body, '{"items":[]}');
  assert.equal(applied?.id, "v1-portfolio-summary-empty");
  assert.equal((await session.mockList()).length, 1);

  const dry = await session.mockFromCapture(undefined, "cap", "timeout", { dryRun: true });
  assert.equal(dry.applied, null);
  assert.equal((await session.mockList()).length, 1);

  await session.mockSet(undefined, { text: `- id: a\n  match: {}\n  respond: { status: 204 }` });
  assert.deepEqual((await session.mockList()).map((r) => r.id), ["a"]);
  await session.mockToggle(undefined, "a", false);
  assert.equal((await session.mockList())[0]?.enabled, false);
  assert.deepEqual(await session.mockClear(undefined, "a"), { removed: "a" });

  const log = await session.logcatTail(undefined, { pattern: "FATAL" });
  assert.equal(log.lines.length, 1);
  assert.equal(log.app, "com.yudistirosaputro.dimock.fake");

  const got = await session.capturesGet(undefined, "cap");
  assert.match(got.asCurl, /^curl -X GET/);
  assert.equal(calls.filter((c) => c.includes("forward")).length, 1, "forward is cached per device+port");
});

test("Session asks when several devices match and when no device is connected", async () => {
  const port = Number(new URL(baseUrl).port);
  const many = new Session({ adb: fakeAdb(DEVICES, port).adb });
  await assert.rejects(many.mockList(), (e: unknown) => e instanceof AmbiguousTargetError && e.candidates.length === 2);
  assert.equal((await many.mockList({ device: "R58M" })).length, 0, "partial serial works");
  assert.equal((await many.mockList({ device: "emulator-5554" })).length, 0);
  await assert.rejects(many.mockList({ device: "zzz" }), /no connected device matches/);

  const none = new Session({ adb: fakeAdb("List of devices attached\n", port).adb });
  await assert.rejects(none.mockList(), (e: unknown) => e instanceof DimockError && e.code === "no_device");
});

test("Session with baseUrl skips adb", async () => {
  const session = new Session({ adb: new Adb(async () => { throw new Error("adb must not be called"); }), baseUrl });
  const health = await session.health();
  assert.equal(health.app, "com.yudistirosaputro.dimock.fake");
  await assert.rejects(session.logcatTail(undefined), /needs a device/);
});

test("mockFromCapture by path uses the newest real capture of that path", async () => {
  const session = new Session({ baseUrl, clientName: "test" });
  fake.addTransaction({ id: "old", path: "/3/discover/movie" });
  fake.addTransaction({ id: "other", path: "/3/genre/movie/list" });
  fake.addTransaction({ id: "new", path: "/3/discover/movie" });
  fake.addTransaction({ id: "served-by-mock", path: "/3/discover/movie", mocked: true });
  const r = await session.mockFromCapture(undefined, { path: "/3/discover/movie" }, "empty", { dryRun: true });
  assert.equal(r.captureId, "new");
  assert.deepEqual(r.rule.match, { method: "GET", path: "/3/discover/movie" });
  await assert.rejects(session.mockFromCapture(undefined, { path: "/nope" }, "empty"), (e: unknown) => e instanceof DimockError && e.code === "no_capture");
  await assert.rejects(session.mockFromCapture(undefined, {}, "empty"), (e: unknown) => e instanceof DimockError && e.code === "invalid_input");
  await assert.rejects(session.mockFromCapture(undefined, { captureId: "new", path: "/x" }, "empty"), /not both/);
});

test("error and unauthorized reuse the body of a real error from the same host", async () => {
  const session = new Session({ baseUrl, clientName: "test" });
  const tmdb = '{"success":false,"status_code":7,"status_message":"Invalid API key: You must be granted a valid key."}';
  const on = (host: string) => ({ host, url: `https://${host}/3/x` });
  fake.addTransaction({ id: "other-host-500", ...on("cdn.example.com"), path: "/img", responseCode: 500, responseBody: { kind: "text", text: '{"oops":1}', contentType: "application/json", totalBytes: 10 } });
  fake.addTransaction({ id: "tmdb-401", ...on("api.themoviedb.org"), path: "/3/account", responseCode: 401, responseBody: { kind: "text", text: tmdb, contentType: "application/json;charset=utf-8", totalBytes: tmdb.length } });
  fake.addTransaction({ id: "list", ...on("api.themoviedb.org"), path: "/3/discover/movie", responseBody: { kind: "text", text: '{"results":[1]}', contentType: "application/json", totalBytes: 15 } });

  const error = await session.mockFromCapture(undefined, "list", "error", { dryRun: true });
  assert.equal(error.rule.respond?.status, 500);
  assert.equal(error.rule.respond?.body, tmdb);
  assert.equal(error.rule.respond?.headers?.["Content-Type"], "application/json;charset=utf-8");
  assert.equal(error.bodyFrom, "tmdb-401");

  const unauthorized = await session.mockFromCapture(undefined, "list", "unauthorized", { dryRun: true });
  assert.equal(unauthorized.rule.respond?.status, 401);
  assert.equal(unauthorized.rule.respond?.body, tmdb);

  const explicit = await session.mockFromCapture(undefined, "list", "error", { dryRun: true, status: 422, body: '{"e":1}' });
  assert.equal(explicit.rule.respond?.status, 422);
  assert.equal(explicit.rule.respond?.body, '{"e":1}');
  assert.equal(explicit.bodyFrom, undefined);

  fake.transactions.length = 0;
  fake.addTransaction({ id: "alone", path: "/v1/a" });
  const generic = await session.mockFromCapture(undefined, "alone", "error", { dryRun: true });
  assert.equal(generic.rule.respond?.body, '{"error":"internal"}');
  assert.equal(generic.bodyFrom, undefined);
});

test("mockFromCapture reads bodyFile", async () => {
  const session = new Session({ baseUrl, clientName: "test" });
  const dir = await mkdtemp(join(tmpdir(), "dimock-body-"));
  await writeFile(join(dir, "err.json"), '{"status_message":"from file"}');
  fake.addTransaction({ id: "cap", path: "/v1/a" });
  const r = await session.mockFromCapture(undefined, "cap", "error", { dryRun: true, bodyFile: join(dir, "err.json") });
  assert.equal(r.rule.respond?.body, '{"status_message":"from file"}');
  await assert.rejects(session.mockFromCapture(undefined, "cap", "error", { body: "x", bodyFile: join(dir, "err.json") }), /body or bodyFile/);
  await assert.rejects(session.mockFromCapture(undefined, "cap", "error", { bodyFile: join(dir, "missing.json") }), /body file not found/);
});

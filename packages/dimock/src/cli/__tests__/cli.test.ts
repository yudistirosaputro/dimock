import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdtemp, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, test } from "node:test";
import { promisify } from "node:util";
import { FakeWire } from "../../core/__tests__/fake-wire.js";
import { writeAgentFiles } from "../init.js";

const exec = promisify(execFile);
const BIN = join(import.meta.dirname, "..", "..", "..", "dist", "cli", "bin.js");

let fake: FakeWire;
let baseUrl: string;
before(async () => {
  fake = new FakeWire();
  baseUrl = await fake.start();
});
after(() => fake.stop());

async function cli(...args: string[]) {
  try {
    const { stdout, stderr } = await exec("node", [BIN, "--base-url", baseUrl, ...args]);
    return { code: 0, stdout, stderr };
  } catch (e) {
    const err = e as { code: number; stdout: string; stderr: string };
    return { code: err.code, stdout: err.stdout, stderr: err.stderr };
  }
}

test("cli: health, mock set from stdin, list in --json and text, clear", async () => {
  const health = await cli("health");
  assert.equal(health.code, 0);
  assert.match(health.stdout, /com\.yudistirosaputro\.dimock\.fake/);

  // stdin path: pipe a rule
  const child = execFile("node", [BIN, "--base-url", baseUrl, "--json", "mock", "set", "-"]);
  child.stdin!.end("- id: a\n  match: { path: /a }\n  respond: { status: 204 }\n");
  const out = await new Promise<string>((r) => {
    let s = "";
    child.stdout!.on("data", (d) => (s += d));
    child.on("close", () => r(s));
  });
  assert.equal(JSON.parse(out)[0].id, "a");

  const json = await cli("--json", "mock", "list");
  assert.equal(JSON.parse(json.stdout)[0].id, "a");
  const text = await cli("mock", "list");
  assert.match(text.stdout, /on {2}a {2}\/a {2}→ 204/);

  const cleared = await cli("mock", "clear");
  assert.match(cleared.stdout, /all rules removed/);
});

test("cli: errors go to stderr with exit code 1, and --json wraps them", async () => {
  const r = await cli("capture", "get", "missing");
  assert.equal(r.code, 1);
  assert.match(r.stderr, /^dimock: GET \/transactions\/missing failed: HTTP 404/);
  const j = await cli("--json", "capture", "get", "missing");
  assert.equal(JSON.parse(j.stderr).error, "not_found");
});

test("cli: connect prints the export line only with --print-env", async () => {
  const plain = await cli("connect");
  assert.equal(plain.code, 0);
  assert.match(plain.stdout, /com\.yudistirosaputro\.dimock\.fake/);
  assert.doesNotMatch(plain.stdout, /export DIMOCK_BASE_URL/);
  const env = await cli("connect", "--print-env");
  assert.match(env.stdout, new RegExp(`^export DIMOCK_BASE_URL=${baseUrl}$`, "m"));
});

test("cli: mock add and mock set --help describe the rule format", async () => {
  for (const cmd of ["add", "set"]) {
    const help = await cli("mock", cmd, "--help");
    assert.match(help.stdout, /YAML or JSON/);
    assert.match(help.stdout, /docs\/rule-format\.md/);
    assert.match(help.stdout, /respond: \{ status: /);
  }
});

test("cli: mock from --path picks the newest capture; --status and --body adjust the variant", async () => {
  fake.transactions.length = 0;
  fake.addTransaction({ id: "older", path: "/3/discover/movie" });
  fake.addTransaction({ id: "newest", path: "/3/discover/movie" });
  const byPath = await cli("--json", "mock", "from", "--path", "/3/discover/movie", "empty", "--dry-run");
  assert.equal(byPath.code, 0, byPath.stderr);
  assert.equal(JSON.parse(byPath.stdout).captureId, "newest");

  const custom = await cli("--json", "mock", "from", "newest", "error", "--status", "401", "--body", '{"status_code":7}', "--dry-run");
  const rule = JSON.parse(custom.stdout).rule;
  assert.equal(rule.respond.status, 401);
  assert.equal(rule.respond.body, '{"status_code":7}');

  const neither = await cli("mock", "from", "error");
  assert.equal(neither.code, 1);
  assert.match(neither.stderr, /capture id or --path/);
});

test("init writes idempotent onboarding files for every agent", async () => {
  const dir = await mkdtemp(join(tmpdir(), "dimock-init-"));
  const first = await writeAgentFiles(dir, "all");
  assert.ok(first.some((f) => f.endsWith(".mcp.json")));
  assert.ok(first.some((f) => f.endsWith("CLAUDE.md")));
  assert.ok(first.some((f) => f.endsWith(".cursor/rules/dimock.mdc")));
  assert.ok(first.some((f) => f.endsWith("AGENTS.md")));
  const agents = await readFile(join(dir, "AGENTS.md"), "utf8");
  assert.match(agents, /mock_from_capture/);
  const mcp = JSON.parse(await readFile(join(dir, ".mcp.json"), "utf8"));
  assert.deepEqual(mcp.mcpServers.dimock.args, ["-y", "dimock", "mcp"]);

  const second = await writeAgentFiles(dir, "all");
  const agentsAgain = await readFile(join(dir, "AGENTS.md"), "utf8");
  assert.equal(agentsAgain, agents, "second run leaves the block unchanged");
  assert.ok(!second.some((f) => f.endsWith("AGENTS.md")), "unchanged files are not reported");
  assert.equal((agentsAgain.match(/dimock:start/g) ?? []).length, 1);
});

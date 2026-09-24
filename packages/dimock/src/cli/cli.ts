import { Session } from "../core/index.js";
import { describeError, runStdio, tools, type ToolResult } from "../mcp/index.js";
import { Command } from "commander";
import { writeAgentFiles } from "./init.js";

/**
 * `dimock` CLI. Commands mirror the MCP tools 1:1 (same names with `_` → space or `-`), and add
 * `mcp` (start the MCP server), `connect` (forward + health), `watch` (event stream) and `init` (agent onboarding).
 */
export function buildProgram(io: { out: (s: string) => void; err: (s: string) => void } = { out: console.log, err: console.error }): Command {
  const program = new Command("dimock")
    .description("Agent-native Android HTTP inspector and mock injector")
    .version("0.1.0-alpha02")
    .option("-d, --device <serial>", "adb serial (or unique substring)")
    .option("-a, --app <package>", "application id (or unique substring)")
    .option("-p, --port <port>", "device-side wire port (default 6767)", (v) => Number.parseInt(v, 10))
    .option("--base-url <url>", "skip adb; talk to this URL (DIMOCK_BASE_URL)")
    .option("--json", "machine-readable output (what agents should use)")
    .showHelpAfterError();

  const session = () => new Session({ clientName: "dimock-cli", baseUrl: program.opts().baseUrl });
  const target = () => {
    const o = program.opts();
    return o.device || o.app || o.port || o.baseUrl ? { device: o.device, app: o.app, port: o.port, baseUrl: o.baseUrl } : {};
  };

  const emit = (result: ToolResult) => io.out(program.opts().json ? JSON.stringify(result.data, null, 2) : result.text);
  const fail = (e: unknown): never => {
    const err = describeError(e);
    io.err(program.opts().json ? JSON.stringify({ error: err.code, message: err.message, ...(err.candidates ? { candidates: err.candidates } : {}) }) : `dimock: ${err.message}`);
    process.exitCode = err.code === "ambiguous_target" ? 3 : 1;
    return undefined as never;
  };
  const run = async (name: string, input: Record<string, unknown>) => {
    const tool = tools.find((t) => t.name === name)!;
    try {
      emit(await tool.run(session(), { ...target(), ...input } as never));
    } catch (e) {
      fail(e);
    }
  };

  program.command("devices").description("List devices and emulators").action(() => run("devices_list", {}));
  program.command("apps").description("Apps with a reachable dimock on the device").action(() => run("apps_list", { device: program.opts().device }));
  program.command("health").description("Identity and counters of the app's dimock").action(() => run("health", {}));
  program
    .command("connect")
    .description("Forward the port and print how to reach the app (what the Agent tab's copy button gives you)")
    .action(async () => {
      try {
        const s = session();
        const r = await s.resolve(target());
        const h = await r.client.health();
        emit({
          data: { device: r.device, app: r.app, baseUrl: r.baseUrl, health: h },
          text: `${r.app} on ${r.device ?? "baseUrl"} → ${r.baseUrl}  (dimock ${h.version}, ${h.activeMocks} mocks active)\nexport DIMOCK_BASE_URL=${r.baseUrl}`,
        });
      } catch (e) {
        fail(e);
      }
    });

  const capture = program.command("capture").description("Captured HTTP traffic");
  capture
    .command("list")
    .option("-n, --limit <n>", "max results", (v) => Number.parseInt(v, 10))
    .option("--since <ms>", "epoch ms", (v) => Number.parseInt(v, 10))
    .option("--path <glob>", "path glob or re:regex")
    .option("--method <m>")
    .option("--mocked", "only mocked")
    .option("--real", "only real")
    .action((o) => run("captures_list", { limit: o.limit, since: o.since, path: o.path, method: o.method, mocked: o.mocked ? true : o.real ? false : undefined }));
  capture.command("get <id>").action((id) => run("captures_get", { id }));
  capture.command("clear").action(() => run("captures_clear", {}));

  const mock = program.command("mock").description("Mock rules on the device");
  mock.command("list").action(() => run("mock_list", {}));
  mock
    .command("set [file]")
    .description("Replace all rules from a YAML/JSON file (or stdin with -)")
    .action(async (file?: string) => {
      const text = file === "-" || !file ? await readStdin() : undefined;
      await run("mock_set", text !== undefined ? { text } : { file });
    });
  mock
    .command("add [file]")
    .description("Add/replace one rule from a file or stdin")
    .action(async (file?: string) => {
      const text = file === "-" || !file ? await readStdin() : undefined;
      await run("mock_add", text !== undefined ? { text } : { file });
    });
  mock.command("on <id>").description("Enable a rule").action((id) => run("mock_toggle", { id, enabled: true }));
  mock.command("off <id>").description("Disable a rule").action((id) => run("mock_toggle", { id, enabled: false }));
  mock.command("reset <id>").description("Reset counters and re-enable").action((id) => run("mock_toggle", { id, enabled: true, reset: true }));
  mock.command("clear [id]").description("Remove one rule, or all").action((id) => run("mock_clear", { id }));
  mock
    .command("from <captureId> <variant>")
    .description("Derive a rule from a capture: error | unauthorized | empty | slow | timeout | malformed")
    .option("--dry-run", "print the rule without applying")
    .option("--times <n>", "consume after N hits", (v) => Number.parseInt(v, 10))
    .option("--delay <ms>", "delay in ms", (v) => Number.parseInt(v, 10))
    .option("--id <id>", "rule id")
    .action((captureId, variant, o) => run("mock_from_capture", { captureId, variant, dryRun: o.dryRun, times: o.times, delayMs: o.delay, id: o.id }));

  program
    .command("logcat")
    .description("Tail the app's logcat")
    .option("-g, --grep <regex>", "filter lines")
    .option("-n, --lines <n>", "line count", (v) => Number.parseInt(v, 10))
    .action((o) => run("logcat_tail", { pattern: o.grep, lines: o.lines }));

  program.command("activity").description("Agent activity log from the device").action(() => run("agent_activity", {}));

  program
    .command("watch")
    .description("Stream wire events (transactions, rule hits, rule changes) until Ctrl-C")
    .action(async () => {
      const controller = new AbortController();
      process.on("SIGINT", () => controller.abort());
      try {
        await session().watch(target(), (e) => io.out(program.opts().json ? JSON.stringify(e) : `${e.type}  ${JSON.stringify(e.data)}`), controller.signal);
      } catch (e) {
        fail(e);
      }
    });

  program
    .command("mcp")
    .description("Start the MCP server on stdio (what the Claude Code plugin runs)")
    .action(async () => {
      await runStdio({ baseUrl: program.opts().baseUrl });
    });

  program
    .command("init")
    .description("Write agent onboarding files for this project: Claude Code, Cursor, Codex/AGENTS.md")
    .option("--agent <name>", "claude | cursor | codex | all", "all")
    .option("--dir <path>", "project directory", process.cwd())
    .action(async (o) => {
      try {
        const written = await writeAgentFiles(o.dir, o.agent);
        emit({ data: { written }, text: written.length ? `wrote:\n${written.map((w) => `  ${w}`).join("\n")}` : "nothing to write" });
      } catch (e) {
        fail(e);
      }
    });

  return program;
}

async function readStdin(): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of process.stdin) chunks.push(chunk as Buffer);
  return Buffer.concat(chunks).toString("utf8");
}

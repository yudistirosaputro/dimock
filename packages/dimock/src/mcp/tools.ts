import { AmbiguousTargetError, DimockError, type Rule, Session, type TargetSpec, VARIANTS } from "../core/index.js";
import { z } from "zod";

/**
 * The tool catalogue, independent of MCP or CLI plumbing: name, description, input schema, and the Session call.
 * `packages/cli` builds its commands from this same list, so the two surfaces stay 1:1 (PRD section 6).
 */

export const targetShape = {
  device: z.string().optional().describe("adb serial (or unique substring). Omit when one device is connected."),
  app: z.string().optional().describe("application id (or unique substring). Omit when one dimock app is reachable."),
  port: z.number().int().optional().describe("device-side wire port when the app does not use 6767"),
  baseUrl: z.string().optional().describe("skip adb and talk to this URL (already-forwarded port or the JVM dev server)"),
};

const filterShape = {
  limit: z.number().int().min(1).max(500).optional().describe("max captures, newest first (default 50)"),
  since: z.number().optional().describe("epoch ms; only captures started after this"),
  path: z.string().optional().describe("glob (`/v1/**`) or `re:` regex against the URL path"),
  method: z.string().optional(),
  mocked: z.boolean().optional().describe("true = only mocked, false = only real"),
};

export interface ToolResult {
  /** JSON-able payload for --json and MCP structuredContent. */
  data: unknown;
  /** Human summary for chat and the default CLI output. */
  text: string;
}

export interface ToolDef<Shape extends z.ZodRawShape> {
  name: string;
  title: string;
  description: string;
  inputSchema: Shape;
  readOnly: boolean;
  run: (session: Session, input: z.infer<z.ZodObject<Shape>>) => Promise<ToolResult>;
}

function def<Shape extends z.ZodRawShape>(d: ToolDef<Shape>): ToolDef<Shape> {
  return d;
}

const target = (i: Partial<TargetSpec>): TargetSpec | undefined =>
  i.device || i.app || i.port || i.baseUrl ? { device: i.device, app: i.app, port: i.port, baseUrl: i.baseUrl } : undefined;

const ms = (n?: number) => (n === undefined ? "" : ` ${n} ms`);

export const tools = [
  def({
    name: "devices_list",
    title: "List devices",
    description: "List Android devices and emulators visible to adb, with model and API level.",
    inputSchema: {},
    readOnly: true,
    run: async (s) => {
      const devices = await s.devicesList();
      return {
        data: devices,
        text: devices.length
          ? devices.map((d) => `${d.serial}  ${d.state}${d.model ? `  ${d.model}` : ""}${d.apiLevel ? `  API ${d.apiLevel}` : ""}`).join("\n")
          : "no devices connected",
      };
    },
  }),
  def({
    name: "apps_list",
    title: "List apps with dimock",
    description: "Find apps on a device that have a reachable dimock wire server (probes port 6767 and DIMOCK_PORTS).",
    inputSchema: { device: targetShape.device },
    readOnly: true,
    run: async (s, i) => {
      const apps = await s.appsList(i.device);
      return {
        data: apps,
        text: apps.length
          ? apps.map((a) => `${a.app}  port ${a.port} (local ${a.localPort})  dimock ${a.health.version}  ${a.health.activeMocks} mocks active`).join("\n")
          : "no app with dimock reachable. Is the debug build running?",
      };
    },
  }),
  def({
    name: "health",
    title: "Health",
    description: "Identity and counters of one app's dimock: version, protocol, active mocks, captured transactions.",
    inputSchema: { ...targetShape },
    readOnly: true,
    run: async (s, i) => {
      const h = await s.health(target(i));
      return { data: h, text: `${h.app}${h.appVersion ? ` ${h.appVersion}` : ""}  dimock ${h.version} (protocol ${h.protocol})  ${h.activeMocks} mocks active  ${h.transactions} captures  agent writes ${h.agentWriteEnabled ? "enabled" : "DISABLED"}` };
    },
  }),
  def({
    name: "captures_list",
    title: "List captures",
    description: "Captured HTTP transactions (summaries, newest first). Filter by path glob, method, mocked, or since.",
    inputSchema: { ...targetShape, ...filterShape },
    readOnly: true,
    run: async (s, i) => {
      const list = await s.capturesList(target(i), { limit: i.limit, since: i.since, path: i.path, method: i.method, mocked: i.mocked });
      return {
        data: list,
        text: list.length
          ? list.map((t) => `${t.id}  ${t.responseCode ?? "ERR"}  ${t.method} ${t.path}${ms(t.durationMs)}${t.mocked ? "  MOCK" : ""}${t.error ? `  ${t.error}` : ""}`).join("\n")
          : "no captures yet. Use the app, then list again.",
      };
    },
  }),
  def({
    name: "captures_get",
    title: "Get capture",
    description: "One capture with headers and bodies (redacted), plus a curl reproduction.",
    inputSchema: { ...targetShape, id: z.string().describe("capture id from captures_list") },
    readOnly: true,
    run: async (s, i) => {
      const tx = await s.capturesGet(target(i), i.id);
      const body = tx.responseBody && "text" in tx.responseBody ? tx.responseBody.text : tx.responseBody ? `<binary ${tx.responseBody.totalBytes} bytes>` : "";
      return { data: tx, text: `${tx.method} ${tx.url}\n${tx.responseCode ?? tx.error}${ms(tx.durationMs)}${tx.mocked ? `  MOCK (${tx.mockRuleId})` : ""}\n\n${body.slice(0, 4000)}\n\n${tx.asCurl}` };
    },
  }),
  def({
    name: "captures_clear",
    title: "Clear captures",
    description: "Delete all captured transactions on the device.",
    inputSchema: { ...targetShape },
    readOnly: false,
    run: async (s, i) => {
      await s.capturesClear(target(i));
      return { data: { cleared: true }, text: "captures cleared" };
    },
  }),
  def({
    name: "mock_list",
    title: "List mock rules",
    description: "All mock rules on the device with runtime state (hits, remaining, spent).",
    inputSchema: { ...targetShape },
    readOnly: true,
    run: async (s, i) => {
      const rules = await s.mockList(target(i));
      return { data: rules, text: rules.length ? rules.map(formatRule).join("\n") : "no mock rules" };
    },
  }),
  def({
    name: "mock_set",
    title: "Replace mock rules",
    description: "Replace the whole rule set from a rules file (YAML/JSON, bodyFile resolved relative to it) or inline YAML/JSON text. See docs/rule-format.md.",
    inputSchema: { ...targetShape, file: z.string().optional().describe("path to rules.yaml / rules.json"), text: z.string().optional().describe("inline YAML or JSON rules") },
    readOnly: false,
    run: async (s, i) => {
      const rules = await s.mockSet(target(i), { file: i.file, text: i.text });
      return { data: rules, text: `${rules.length} rule${rules.length === 1 ? "" : "s"} active\n${rules.map(formatRule).join("\n")}` };
    },
  }),
  def({
    name: "mock_add",
    title: "Add mock rule",
    description: "Add or replace one rule by id without touching the others. Give the rule as an object or as YAML/JSON text.",
    inputSchema: { ...targetShape, rule: z.record(z.unknown()).optional().describe("rule object (see docs/rule-format.md)"), text: z.string().optional().describe("inline YAML/JSON rule") },
    readOnly: false,
    run: async (s, i) => {
      const added = await s.mockAdd(target(i), { rule: i.rule as Rule | undefined, text: i.text });
      return { data: added, text: added.map(formatRule).join("\n") };
    },
  }),
  def({
    name: "mock_toggle",
    title: "Enable/disable a rule",
    description: "Enable or disable one rule; optionally reset its counters.",
    inputSchema: { ...targetShape, id: z.string(), enabled: z.boolean(), reset: z.boolean().optional() },
    readOnly: false,
    run: async (s, i) => {
      const r = await s.mockToggle(target(i), i.id, i.enabled, i.reset);
      return { data: r, text: formatRule(r) };
    },
  }),
  def({
    name: "mock_clear",
    title: "Remove mock rules",
    description: "Remove one rule by id, or every rule when id is omitted.",
    inputSchema: { ...targetShape, id: z.string().optional() },
    readOnly: false,
    run: async (s, i) => {
      const r = await s.mockClear(target(i), i.id);
      return { data: r, text: r.removed === "all" ? "all rules removed" : `rule ${r.removed} removed` };
    },
  }),
  def({
    name: "mock_from_capture",
    title: "Mock from capture",
    description:
      "Derive a rule from a real capture and apply it: error (500), unauthorized (401), empty (200 with emptied arrays), slow (same body, +3 s), timeout, malformed. Use dryRun to review the rule first.",
    inputSchema: {
      ...targetShape,
      captureId: z.string(),
      variant: z.enum(VARIANTS as [string, ...string[]]),
      dryRun: z.boolean().optional(),
      times: z.number().int().min(1).optional().describe("consume the rule after N hits"),
      delayMs: z.number().int().min(0).optional(),
      id: z.string().optional().describe("rule id (default <path>-<variant>)"),
    },
    readOnly: false,
    run: async (s, i) => {
      const r = await s.mockFromCapture(target(i), i.captureId, i.variant as (typeof VARIANTS)[number], { dryRun: i.dryRun, times: i.times, delayMs: i.delayMs, id: i.id });
      return {
        data: r,
        text: `${r.applied ? "applied" : "dry run (not applied)"}: ${JSON.stringify(r.rule, null, 2)}`,
      };
    },
  }),
  def({
    name: "logcat_tail",
    title: "Tail logcat",
    description: "Last lines of logcat for the app's process, optionally filtered by a regex (e.g. FATAL|Exception).",
    inputSchema: { ...targetShape, pattern: z.string().optional(), lines: z.number().int().min(1).max(2000).optional() },
    readOnly: true,
    run: async (s, i) => {
      const r = await s.logcatTail(target(i), { pattern: i.pattern, lines: i.lines });
      return { data: r, text: r.lines.length ? r.lines.join("\n") : "no matching log lines" };
    },
  }),
  def({
    name: "agent_activity",
    title: "Agent activity",
    description: "What clients read or changed on the device (the Agent tab log).",
    inputSchema: { ...targetShape, limit: z.number().int().optional() },
    readOnly: true,
    run: async (s, i) => {
      const log = await s.activity(target(i), i.limit);
      return { data: log, text: log.map((a) => `${new Date(a.at).toISOString()}  ${a.kind.padEnd(7)}  ${a.summary}  (${a.call})`).join("\n") || "empty" };
    },
  }),
];

export type AnyTool = (typeof tools)[number];

export function formatRule(r: Rule & { enabled?: boolean; state?: { hits: number; remaining?: number; spent: boolean } }): string {
  const what = r.sequence?.length
    ? `sequence[${r.sequence.length}]`
    : r.respond
      ? `${r.respond.status ?? 200}${r.respond.delayMs ? ` +${r.respond.delayMs}ms` : ""}`
      : `fail:${r.fail?.type}`;
  const m = [r.match.method, r.match.path ?? "*"].filter(Boolean).join(" ");
  const state = r.state ? `  hits ${r.state.hits}${r.state.remaining !== undefined ? ` left ${r.state.remaining}` : ""}${r.state.spent ? " SPENT" : ""}` : "";
  return `${r.enabled === false ? "off " : "on  "}${r.id}  ${m}  → ${what}${r.priority ? `  prio ${r.priority}` : ""}${state}`;
}

/** Turn any error into the text an agent or human should see. */
export function describeError(e: unknown): { message: string; code: string; candidates?: unknown } {
  if (e instanceof AmbiguousTargetError) return { message: e.message, code: e.code, candidates: e.candidates };
  if (e instanceof DimockError) return { message: e.message, code: e.code };
  if (e instanceof Error) return { message: e.message, code: "error" };
  return { message: String(e), code: "error" };
}

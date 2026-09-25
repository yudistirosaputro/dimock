import { readFile } from "node:fs/promises";
import { resolve as resolvePath } from "node:path";
import { Adb, type Device } from "./adb.js";
import { asCurl } from "./curl.js";
import { loadRulesFile, parseRules, validateRule } from "./rules.js";
import {
  type Activity,
  DEFAULT_PORT,
  type Health,
  DimockError,
  type Rule,
  type RuleEntry,
  type Transaction,
  type TransactionFilter,
  type TransactionSummary,
  type Variant,
  type WireEvent,
} from "./types.js";
import { ruleFromCapture } from "./variants.js";
import { WireClient } from "./wire.js";

export interface TargetSpec {
  /** adb serial, or a unique substring of one. */
  device?: string;
  /** application id, or a unique substring of one. */
  app?: string;
  /** Device-side wire port when it is not 6767. */
  port?: number;
  /** Skip adb entirely and talk to this URL (a forwarded port you manage, or the JVM dev server). */
  baseUrl?: string;
}

export interface AppTarget {
  device: string;
  app: string;
  port: number;
  localPort: number;
  baseUrl: string;
  health: Health;
}

export interface Resolved {
  client: WireClient;
  device?: string;
  app: string;
  baseUrl: string;
}

export interface CaptureSource {
  captureId?: string;
  /** Glob or `re:` regex; the newest real (not mocked) capture matching it is used. */
  path?: string;
  method?: string;
}

export interface MockFromOptions {
  dryRun?: boolean;
  times?: number;
  delayMs?: number;
  id?: string;
  status?: number;
  body?: string;
  /** Path to a file whose content becomes the body, relative to the working directory. */
  bodyFile?: string;
}

export class AmbiguousTargetError extends DimockError {
  constructor(
    message: string,
    readonly candidates: Array<{ device?: string; app?: string; model?: string }>,
  ) {
    super(message, "ambiguous_target");
  }
}

export interface SessionOptions {
  adb?: Adb;
  clientName?: string;
  /** Device ports to probe for dimock (default 6767 plus DIMOCK_PORTS). */
  ports?: number[];
  fetchImpl?: typeof fetch;
  /** Default baseUrl for every call (DIMOCK_BASE_URL). */
  baseUrl?: string;
}

/**
 * Everything the MCP tools and CLI commands do, once. Both surfaces are thin wrappers over this class,
 * so they cannot drift from each other.
 */
export class Session {
  private readonly adb: Adb;
  private readonly clientName: string;
  private readonly ports: number[];
  private readonly fetchImpl?: typeof fetch;
  private readonly defaultBaseUrl?: string;
  private readonly forwards = new Map<string, number>(); // `${serial}:${port}` -> local port

  constructor(options: SessionOptions = {}) {
    this.adb = options.adb ?? new Adb();
    this.clientName = options.clientName ?? "dimock";
    const envPorts = (process.env.DIMOCK_PORTS ?? "")
      .split(",")
      .map((p) => Number.parseInt(p.trim(), 10))
      .filter((p) => !Number.isNaN(p));
    this.ports = options.ports ?? [...new Set([DEFAULT_PORT, ...envPorts])];
    this.fetchImpl = options.fetchImpl;
    this.defaultBaseUrl = options.baseUrl ?? process.env.DIMOCK_BASE_URL;
  }

  // ---- discovery ----------------------------------------------------------------------------------------

  async devicesList(): Promise<Device[]> {
    const devices = await this.adb.devices();
    return Promise.all(devices.map((d) => this.adb.describe(d)));
  }

  /** Apps with a reachable dimock on a device, found by probing the known ports. */
  async appsList(device?: string): Promise<AppTarget[]> {
    const serial = await this.pickDevice(device);
    const found: AppTarget[] = [];
    for (const port of this.ports) {
      const target = await this.probe(serial, port).catch(() => undefined);
      if (target) found.push(target);
    }
    return found;
  }

  async resolve(spec: TargetSpec = {}): Promise<Resolved> {
    const baseUrl = spec.baseUrl ?? (spec.device || spec.app ? undefined : this.defaultBaseUrl);
    if (baseUrl) {
      const client = this.client(baseUrl);
      const health = await client.health();
      return { client, app: health.app, baseUrl };
    }
    const serial = await this.pickDevice(spec.device);
    const ports = spec.port ? [spec.port] : this.ports;
    const targets: AppTarget[] = [];
    for (const port of ports) {
      const t = await this.probe(serial, port).catch(() => undefined);
      if (t) targets.push(t);
    }
    let matching = targets;
    if (spec.app) matching = targets.filter((t) => t.app === spec.app || t.app.includes(spec.app!));
    if (matching.length === 0) {
      const hint = spec.app ? `no app matching '${spec.app}' with dimock on ${serial}` : `no app with dimock reachable on ${serial}`;
      throw new DimockError(
        `${hint} (probed ports ${ports.join(", ")}). Is the debug build running in the foreground? Ports other than 6767 need --port or DIMOCK_PORTS.`,
        "no_app",
      );
    }
    if (matching.length > 1) {
      throw new AmbiguousTargetError(
        `several apps with dimock on ${serial}: ${matching.map((t) => `${t.app} (port ${t.port})`).join(", ")}. Pass app=<package>.`,
        matching.map((t) => ({ device: serial, app: t.app })),
      );
    }
    const target = matching[0]!;
    return { client: this.client(target.baseUrl), device: serial, app: target.app, baseUrl: target.baseUrl };
  }

  // ---- tools (1:1 with MCP tools and CLI commands) -------------------------------------------------------

  async health(spec?: TargetSpec): Promise<Health & { device?: string; baseUrl: string }> {
    const r = await this.resolve(spec);
    const health = await r.client.health();
    return { ...health, device: r.device, baseUrl: r.baseUrl };
  }

  async capturesList(spec: TargetSpec | undefined, filter: TransactionFilter = {}): Promise<TransactionSummary[]> {
    const r = await this.resolve(spec);
    return r.client.listTransactions({ limit: 50, ...filter });
  }

  async capturesGet(spec: TargetSpec | undefined, id: string): Promise<Transaction & { asCurl: string }> {
    const r = await this.resolve(spec);
    const tx = await r.client.getTransaction(id);
    return { ...tx, asCurl: asCurl(tx) };
  }

  async capturesClear(spec?: TargetSpec): Promise<void> {
    const r = await this.resolve(spec);
    await r.client.clearTransactions();
  }

  async mockList(spec?: TargetSpec): Promise<RuleEntry[]> {
    const r = await this.resolve(spec);
    return r.client.listRules();
  }

  /** Replace the whole rule set from a file path, YAML/JSON text, or parsed rules. */
  async mockSet(spec: TargetSpec | undefined, input: { file?: string; text?: string; rules?: Rule[] }): Promise<RuleEntry[]> {
    const rules = await this.rulesFrom(input);
    const r = await this.resolve(spec);
    return r.client.replaceRules(rules);
  }

  async mockAdd(spec: TargetSpec | undefined, input: { file?: string; text?: string; rule?: Rule }): Promise<RuleEntry[]> {
    const rules = input.rule ? [validateRule(input.rule)] : await this.rulesFrom(input);
    const r = await this.resolve(spec);
    const out: RuleEntry[] = [];
    for (const rule of rules) out.push(await r.client.addRule(rule));
    return out;
  }

  async mockToggle(spec: TargetSpec | undefined, id: string, enabled: boolean, reset = false): Promise<RuleEntry> {
    const r = await this.resolve(spec);
    return r.client.patchRule(id, { enabled, ...(reset ? { reset: true } : {}) });
  }

  async mockReset(spec: TargetSpec | undefined, id: string): Promise<RuleEntry> {
    const r = await this.resolve(spec);
    return r.client.patchRule(id, { reset: true });
  }

  async mockClear(spec: TargetSpec | undefined, id?: string): Promise<{ removed: string | "all" }> {
    const r = await this.resolve(spec);
    if (id) await r.client.deleteRule(id);
    else await r.client.clearRules();
    return { removed: id ?? "all" };
  }

  /**
   * Build a rule from a capture (by id, or the newest real capture matching `path`); apply it unless dryRun.
   * Without an explicit body, `error` and `unauthorized` reuse the body of a real 4xx/5xx from the same host,
   * so the app's error parser meets the API's own error shape.
   */
  async mockFromCapture(
    spec: TargetSpec | undefined,
    source: string | CaptureSource,
    variant: Variant,
    options: MockFromOptions = {},
  ): Promise<{ rule: Rule; applied: RuleEntry | null; captureId: string; bodyFrom?: string }> {
    const { captureId, path, method } = typeof source === "string" ? { captureId: source } : source;
    if (captureId && path) throw new DimockError("give a capture id or a path, not both", "invalid_input");
    if (!captureId && !path) throw new DimockError("mock from needs a capture id or --path <glob>", "invalid_input");
    if (options.body !== undefined && options.bodyFile) throw new DimockError("give body or bodyFile, not both", "invalid_input");
    const r = await this.resolve(spec);
    const id = captureId ?? (await this.newestCapture(r.client, path!, method));
    const tx = await r.client.getTransaction(id);
    let body = options.body ?? (options.bodyFile ? await readBodyFile(options.bodyFile) : undefined);
    let contentType: string | undefined;
    let bodyFrom: string | undefined;
    if (body === undefined && (variant === "error" || variant === "unauthorized")) {
      const template = await this.realErrorBody(r.client, tx.host, variant === "unauthorized" ? 401 : 500);
      if (template) ({ body, contentType, id: bodyFrom } = template);
    }
    const rule = ruleFromCapture(tx, variant, { id: options.id, times: options.times, delayMs: options.delayMs, status: options.status, body, contentType });
    const result = { rule, captureId: id, ...(bodyFrom ? { bodyFrom } : {}) };
    if (options.dryRun) return { ...result, applied: null };
    return { ...result, applied: await r.client.addRule(rule) };
  }

  async activity(spec?: TargetSpec, limit?: number): Promise<Activity[]> {
    const r = await this.resolve(spec);
    return r.client.activity(limit);
  }

  async logcatTail(spec: TargetSpec | undefined, options: { pattern?: string; lines?: number } = {}): Promise<{ device: string; app: string; lines: string[] }> {
    const r = await this.resolve(spec);
    if (!r.device) throw new DimockError("logcat needs a device target (not a bare baseUrl)", "no_device");
    const lines = await this.adb.logcatTail(r.device, r.app, options);
    return { device: r.device, app: r.app, lines };
  }

  /** Stream wire events until `signal` aborts. */
  async watch(spec: TargetSpec | undefined, onEvent: (e: WireEvent) => void, signal?: AbortSignal): Promise<void> {
    const r = await this.resolve(spec);
    await r.client.events(onEvent, signal);
  }

  // ---- internals -------------------------------------------------------------------------------------------

  private client(baseUrl: string): WireClient {
    return new WireClient({ baseUrl, clientName: this.clientName, fetchImpl: this.fetchImpl });
  }

  private async newestCapture(client: WireClient, path: string, method?: string): Promise<string> {
    const [newest] = await client.listTransactions({ path, method, mocked: false, limit: 1 });
    if (!newest) throw new DimockError(`no real capture matches ${method ? `${method} ` : ""}${path}. Open the screen once, then retry.`, "no_capture");
    return newest.id;
  }

  /** Body of the newest real error response from `host`: same status first, then same class, then any 4xx/5xx. */
  private async realErrorBody(client: WireClient, host: string, status: number): Promise<{ id: string; body: string; contentType?: string } | undefined> {
    const errors = (await client.listTransactions({ mocked: false, limit: 200 })).filter((t) => t.host === host && (t.responseCode ?? 0) >= 400);
    const rank = (code: number) => (code === status ? 0 : Math.floor(code / 100) === Math.floor(status / 100) ? 1 : 2);
    errors.sort((a, b) => rank(a.responseCode!) - rank(b.responseCode!));
    for (const candidate of errors) {
      const tx = await client.getTransaction(candidate.id);
      const b = tx.responseBody;
      if (b && b.kind !== "binary" && b.text.trim() !== "") return { id: tx.id, body: b.text, contentType: "contentType" in b ? b.contentType : undefined };
    }
    return undefined;
  }

  private async rulesFrom(input: { file?: string; text?: string; rules?: Rule[] }): Promise<Rule[]> {
    if (input.rules) return input.rules.map((r, i) => validateRule(r, `rules[${i}]`));
    if (input.file) return loadRulesFile(input.file);
    if (input.text !== undefined) return parseRules(input.text);
    throw new DimockError("provide rules as a file path, YAML/JSON text, or a rules array", "invalid_rules");
  }

  private async pickDevice(spec?: string): Promise<string> {
    const devices = (await this.adb.devices()).filter((d) => d.state === "device");
    if (devices.length === 0) {
      throw new DimockError("no Android device or emulator is connected (adb devices is empty). Plug one in with USB debugging on, or start an emulator.", "no_device");
    }
    if (spec) {
      const exact = devices.find((d) => d.serial === spec);
      const partial = devices.filter((d) => d.serial.includes(spec) || (d.model ?? "").toLowerCase().includes(spec.toLowerCase()));
      const pick = exact ?? (partial.length === 1 ? partial[0] : undefined);
      if (!pick) {
        throw new DimockError(`no connected device matches '${spec}'. Connected: ${devices.map((d) => d.serial).join(", ")}`, "no_device");
      }
      return pick.serial;
    }
    if (devices.length > 1) {
      throw new AmbiguousTargetError(
        `several devices are connected: ${devices.map((d) => `${d.serial}${d.model ? ` (${d.model})` : ""}`).join(", ")}. Pass device=<serial>.`,
        devices.map((d) => ({ device: d.serial, model: d.model })),
      );
    }
    return devices[0]!.serial;
  }

  private async probe(serial: string, port: number): Promise<AppTarget> {
    const localPort = await this.forwardFor(serial, port);
    const baseUrl = `http://127.0.0.1:${localPort}`;
    const client = new WireClient({ baseUrl, clientName: this.clientName, fetchImpl: this.fetchImpl, timeoutMs: 1500 });
    const health = await client.health();
    return { device: serial, app: health.app, port, localPort, baseUrl, health };
  }

  private async forwardFor(serial: string, port: number): Promise<number> {
    const key = `${serial}:${port}`;
    const existing = this.forwards.get(key);
    if (existing) return existing;
    const local = await this.adb.forward(serial, port, 0);
    this.forwards.set(key, local);
    return local;
  }
}

async function readBodyFile(file: string): Promise<string> {
  try {
    return await readFile(resolvePath(file), "utf8");
  } catch {
    throw new DimockError(`body file not found: ${file}`, "invalid_input");
  }
}

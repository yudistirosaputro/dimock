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

  /** Build a rule from a capture; apply it unless dryRun. */
  async mockFromCapture(
    spec: TargetSpec | undefined,
    captureId: string,
    variant: Variant,
    options: { dryRun?: boolean; times?: number; delayMs?: number; id?: string } = {},
  ): Promise<{ rule: Rule; applied: RuleEntry | null }> {
    const r = await this.resolve(spec);
    const tx = await r.client.getTransaction(captureId);
    const rule = ruleFromCapture(tx, variant, options);
    if (options.dryRun) return { rule, applied: null };
    const applied = await r.client.addRule(rule);
    return { rule, applied };
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

import {
  type Activity,
  type Health,
  DimockError,
  PROTOCOL_VERSION,
  type Rule,
  type RuleEntry,
  type Transaction,
  type TransactionFilter,
  type TransactionSummary,
  type WireEvent,
} from "./types.js";

export interface WireClientOptions {
  /** e.g. `http://127.0.0.1:6767` (a forwarded device port or the JVM dev server). */
  baseUrl: string;
  /** Sent as `X-Dimock-Client` so the device's Agent tab can name us. */
  clientName?: string;
  fetchImpl?: typeof fetch;
  timeoutMs?: number;
}

/** Thin, typed client for docs/wire-protocol.md. One instance per (device, app) target. */
export class WireClient {
  private readonly base: string;
  private readonly clientName: string;
  private readonly fetchImpl: typeof fetch;
  private readonly timeoutMs: number;

  constructor(options: WireClientOptions) {
    this.base = options.baseUrl.replace(/\/+$/, "");
    this.clientName = options.clientName ?? "dimock";
    this.fetchImpl = options.fetchImpl ?? fetch;
    this.timeoutMs = options.timeoutMs ?? 5000;
  }

  get baseUrl(): string {
    return this.base;
  }

  health(): Promise<Health> {
    return this.request<Health>("GET", "/health");
  }

  listTransactions(filter: TransactionFilter = {}): Promise<TransactionSummary[]> {
    const q = new URLSearchParams();
    if (filter.limit !== undefined) q.set("limit", String(filter.limit));
    if (filter.since !== undefined) q.set("since", String(filter.since));
    if (filter.path) q.set("path", filter.path);
    if (filter.method) q.set("method", filter.method);
    if (filter.mocked !== undefined) q.set("mocked", String(filter.mocked));
    const qs = q.toString();
    return this.request<TransactionSummary[]>("GET", `/transactions${qs ? `?${qs}` : ""}`);
  }

  getTransaction(id: string): Promise<Transaction> {
    return this.request<Transaction>("GET", `/transactions/${encodeURIComponent(id)}`);
  }

  clearTransactions(): Promise<void> {
    return this.request<void>("DELETE", "/transactions");
  }

  listRules(): Promise<RuleEntry[]> {
    return this.request<RuleEntry[]>("GET", "/rules");
  }

  getRule(id: string): Promise<RuleEntry> {
    return this.request<RuleEntry>("GET", `/rules/${encodeURIComponent(id)}`);
  }

  replaceRules(rules: Rule[]): Promise<RuleEntry[]> {
    return this.request<RuleEntry[]>("PUT", "/rules", rules);
  }

  addRule(rule: Rule): Promise<RuleEntry> {
    return this.request<RuleEntry>("POST", "/rules", rule);
  }

  patchRule(id: string, patch: { enabled?: boolean; reset?: boolean }): Promise<RuleEntry> {
    return this.request<RuleEntry>("PATCH", `/rules/${encodeURIComponent(id)}`, patch);
  }

  deleteRule(id: string): Promise<void> {
    return this.request<void>("DELETE", `/rules/${encodeURIComponent(id)}`);
  }

  clearRules(): Promise<void> {
    return this.request<void>("DELETE", "/rules");
  }

  activity(limit?: number): Promise<Activity[]> {
    return this.request<Activity[]>("GET", `/agent/activity${limit ? `?limit=${limit}` : ""}`);
  }

  clearActivity(): Promise<void> {
    return this.request<void>("DELETE", "/agent/activity");
  }

  /**
   * Subscribe to `GET /events`. Resolves when the stream ends or `signal` aborts.
   * The handler receives parsed events; malformed frames are skipped.
   */
  async events(onEvent: (event: WireEvent) => void, signal?: AbortSignal): Promise<void> {
    const res = await this.fetchImpl(`${this.base}/events`, {
      headers: { Accept: "text/event-stream", "X-Dimock-Client": this.clientName },
      signal: signal ?? null,
    });
    if (!res.ok || !res.body) throw new DimockError(`events stream failed: HTTP ${res.status}`, "wire_error");
    this.checkProtocol(res.headers.get("x-dimock-protocol"));
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let type: string | null = null;
    let data: string[] = [];
    const flush = () => {
      if (type && data.length) {
        try {
          onEvent({ type: type as WireEvent["type"], data: JSON.parse(data.join("\n")) });
        } catch {
          /* skip malformed frame */
        }
      }
      type = null;
      data = [];
    };
    try {
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let idx: number;
        while ((idx = buffer.indexOf("\n")) >= 0) {
          const line = buffer.slice(0, idx).replace(/\r$/, "");
          buffer = buffer.slice(idx + 1);
          if (line === "") flush();
          else if (line.startsWith("event: ")) type = line.slice(7);
          else if (line.startsWith("data: ")) data.push(line.slice(6));
        }
      }
      flush();
    } catch (e) {
      if ((e as Error).name !== "AbortError") throw e;
    }
  }

  private async request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    let res: Response;
    try {
      res = await this.fetchImpl(`${this.base}${path}`, {
        method,
        headers: {
          "X-Dimock-Client": this.clientName,
          ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        },
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      });
    } catch (e) {
      const cause = (e as Error).name === "AbortError" ? "timed out" : (e as Error).message;
      throw new DimockError(
        `cannot reach dimock at ${this.base} (${cause}). Is the debug app running and the port forwarded? Try: adb forward tcp:6767 tcp:6767`,
        "unreachable",
      );
    } finally {
      clearTimeout(timer);
    }
    this.checkProtocol(res.headers.get("x-dimock-protocol"));
    const text = await res.text();
    if (!res.ok) {
      let message = text;
      try {
        message = (JSON.parse(text) as { error?: string }).error ?? text;
      } catch {
        /* keep raw text */
      }
      if (res.status === 403 && message === "agent_write_disabled") {
        throw new DimockError(
          "the device has 'Agent may change mock rules' switched off. Ask the person holding the device to enable it in the dimock Agent tab, or edit rules there.",
          "agent_write_disabled",
        );
      }
      throw new DimockError(`${method} ${path} failed: HTTP ${res.status} ${message}`, res.status === 404 ? "not_found" : "wire_error");
    }
    if (res.status === 204 || text.length === 0) return undefined as T;
    return JSON.parse(text) as T;
  }

  private checkProtocol(header: string | null) {
    if (header === null) return; // dev proxies may strip it; the JSON `protocol` field still applies on /health
    const major = Number.parseInt(header, 10);
    if (Number.isNaN(major)) return;
    if (major !== PROTOCOL_VERSION) {
      throw new DimockError(
        `device speaks dimock protocol ${major}, this client speaks ${PROTOCOL_VERSION}. ${major > PROTOCOL_VERSION ? "Update the CLI/MCP: npm i -g dimock@latest" : "Update the dimock library in the app"}.`,
        "protocol_mismatch",
      );
    }
  }
}

import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import type { AddressInfo } from "node:net";
import type { Activity, Rule, RuleEntry, Transaction } from "../types.js";

/**
 * A deliberately small in-process stand-in for the device wire server, for client unit tests.
 * Semantics that matter to the client (status codes, headers, 403 gating, SSE framing) follow docs/wire-protocol.md;
 * matching semantics are not modelled here (the JVM server owns those, see scripts/contract).
 */
export class FakeWire {
  readonly rules = new Map<string, RuleEntry>();
  readonly transactions: Transaction[] = [];
  readonly activity: Activity[] = [];
  agentWriteEnabled = true;
  protocol = 1;
  app = "com.yudistirosaputro.dimock.fake";
  private server?: Server;
  private sse: ServerResponse[] = [];
  seq = 0;

  async start(): Promise<string> {
    this.server = createServer((req, res) => this.handle(req, res));
    await new Promise<void>((r) => this.server!.listen(0, "127.0.0.1", () => r()));
    return `http://127.0.0.1:${(this.server!.address() as AddressInfo).port}`;
  }

  async stop() {
    for (const r of this.sse) r.end();
    await new Promise<void>((r) => this.server?.close(() => r()));
  }

  emit(type: string, data: unknown) {
    for (const r of this.sse) r.write(`event: ${type}\ndata: ${JSON.stringify(data)}\n\n`);
  }

  addTransaction(tx: Partial<Transaction> & { id: string }): Transaction {
    const full: Transaction = {
      startedAt: Date.now(), durationMs: 10, method: "GET", url: `https://api.example.com${tx.path ?? "/x"}`, host: "api.example.com", path: "/x",
      responseCode: 200, mocked: false, requestBytes: 0, responseBytes: 0, requestHeaders: {}, requestBody: null,
      responseHeaders: { "Content-Type": ["application/json"] }, responseBody: { kind: "text", text: "{}", contentType: "application/json", totalBytes: 2 },
      ...tx,
    };
    this.transactions.unshift(full);
    return full;
  }

  private async handle(req: IncomingMessage, res: ServerResponse) {
    const url = new URL(req.url ?? "/", "http://x");
    const body = await readBody(req);
    res.setHeader("X-Dimock-Protocol", String(this.protocol));
    const json = (code: number, payload: unknown) => {
      res.writeHead(code, { "Content-Type": "application/json" });
      res.end(JSON.stringify(payload));
    };
    const write = () => {
      if (this.agentWriteEnabled) return true;
      json(403, { error: "agent_write_disabled" });
      return false;
    };
    const parts = url.pathname.split("/").filter(Boolean);

    if (req.method === "GET" && url.pathname === "/health") {
      this.activity.unshift({ at: Date.now(), kind: "connect", summary: "Connected", call: "GET /health" });
      return json(200, { app: this.app, version: "fake", protocol: this.protocol, activeMocks: [...this.rules.values()].filter((r) => r.enabled && !r.state.spent).length, transactions: this.transactions.length, agentWriteEnabled: this.agentWriteEnabled });
    }
    if (req.method === "GET" && url.pathname === "/events") {
      res.writeHead(200, { "Content-Type": "text/event-stream" });
      res.write(": connected\n\n");
      this.sse.push(res);
      req.on("close", () => { this.sse = this.sse.filter((r) => r !== res); });
      return;
    }
    if (url.pathname === "/transactions" && req.method === "GET") {
      let list = this.transactions;
      const mocked = url.searchParams.get("mocked");
      if (mocked) list = list.filter((t) => String(t.mocked) === mocked);
      const path = url.searchParams.get("path");
      if (path) list = list.filter((t) => pathMatches(path, t.path));
      const method = url.searchParams.get("method");
      if (method) list = list.filter((t) => t.method.toLowerCase() === method.toLowerCase());
      const limit = Number(url.searchParams.get("limit") ?? 100);
      return json(200, list.slice(0, limit).map(({ requestHeaders: _a, requestBody: _b, responseHeaders: _c, responseBody: _d, ...s }) => s));
    }
    if (url.pathname === "/transactions" && req.method === "DELETE") { if (!write()) return; this.transactions.length = 0; res.writeHead(204); return res.end(); }
    if (parts[0] === "transactions" && parts.length === 2 && req.method === "GET") {
      const tx = this.transactions.find((t) => t.id === parts[1]);
      return tx ? json(200, tx) : json(404, { error: "transaction not found" });
    }
    if (url.pathname === "/rules" && req.method === "GET") return json(200, [...this.rules.values()]);
    if (url.pathname === "/rules" && req.method === "PUT") {
      if (!write()) return;
      const rules = JSON.parse(body) as Rule[];
      if (rules.some((r) => !r.respond && !r.fail && !r.sequence?.length)) return json(400, { error: "rule needs exactly one of respond | fail, or a non-empty sequence" });
      this.rules.clear();
      for (const r of rules) this.rules.set(r.id ?? `r-${++this.seq}`, this.entry(r));
      this.activity.unshift({ at: Date.now(), kind: "write", summary: `Pushed ${rules.length} rules`, call: "PUT /rules" });
      this.emit("rules_changed", { active: this.rules.size, total: this.rules.size });
      return json(200, [...this.rules.values()]);
    }
    if (url.pathname === "/rules" && req.method === "POST") {
      if (!write()) return;
      const r = JSON.parse(body) as Rule;
      const e = this.entry(r);
      this.rules.set(e.id, e);
      this.emit("rules_changed", { active: this.rules.size, total: this.rules.size });
      return json(201, e);
    }
    if (url.pathname === "/rules" && req.method === "DELETE") { if (!write()) return; this.rules.clear(); res.writeHead(204); return res.end(); }
    if (parts[0] === "rules" && parts.length === 2) {
      const e = this.rules.get(parts[1]!);
      if (!e) return json(404, { error: "rule not found" });
      if (req.method === "GET") return json(200, e);
      if (req.method === "PATCH") {
        if (!write()) return;
        const patch = JSON.parse(body) as { enabled?: boolean; reset?: boolean };
        if (patch.reset) { e.state = { hits: 0, remaining: e.times, sequenceIndex: 0, spent: false }; e.enabled = true; }
        if (patch.enabled !== undefined) e.enabled = patch.enabled;
        return json(200, e);
      }
      if (req.method === "DELETE") { if (!write()) return; this.rules.delete(parts[1]!); res.writeHead(204); return res.end(); }
    }
    if (url.pathname === "/agent/activity" && req.method === "GET") return json(200, this.activity);
    if (url.pathname === "/agent/activity" && req.method === "DELETE") { this.activity.length = 0; res.writeHead(204); return res.end(); }
    return json(404, { error: `no route for ${req.method} ${url.pathname}` });
  }

  private entry(r: Rule): RuleEntry {
    return { ...r, id: r.id ?? `r-${++this.seq}`, enabled: r.enabled ?? true, priority: r.priority ?? 0, state: { hits: 0, remaining: r.times, sequenceIndex: 0, spent: false } };
  }
}

function readBody(req: IncomingMessage): Promise<string> {
  return new Promise((resolve) => {
    let data = "";
    req.on("data", (c) => (data += c));
    req.on("end", () => resolve(data));
  });
}

/** Same path semantics as the device: `re:` regex, else a glob where `**` crosses segments and `*` does not. */
function pathMatches(pattern: string, path: string): boolean {
  if (pattern.startsWith("re:")) return new RegExp(pattern.slice(3)).test(path);
  const source = pattern.replace(/[.+^${}()|[\]\\?]/g, "\\$&").replace(/\*\*/g, "\u0000").replace(/\*/g, "[^/]*").replace(/\u0000/g, ".*");
  return new RegExp(`^${source}$`).test(path);
}

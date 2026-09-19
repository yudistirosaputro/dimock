/** Wire types. Mirrors docs/wire-protocol.md and docs/rule-format.md; the device is the source of truth. */

export const PROTOCOL_VERSION = 1;
export const DEFAULT_PORT = 6767;

export type FailType = "timeout" | "connection_reset" | "malformed_body" | "empty_body";

export interface BodyMatch {
  path: string;
  equals: string;
}

export interface Match {
  method?: string;
  path?: string;
  host?: string;
  query?: Record<string, string>;
  headers?: Record<string, string>;
  body?: BodyMatch[];
}

export interface Respond {
  status?: number;
  headers?: Record<string, string>;
  body?: string;
  /** Client-side only: resolved into `body` before the rule leaves the machine. */
  bodyFile?: string;
  delayMs?: number;
}

export interface Fail {
  type: FailType;
  delayMs?: number;
}

export type Step = { respond: Respond } | { fail: Fail };

export interface Rule {
  id?: string;
  name?: string;
  enabled?: boolean;
  priority?: number;
  match: Match;
  times?: number;
  sequence?: Step[];
  respond?: Respond;
  fail?: Fail;
}

export interface RuleState {
  hits: number;
  remaining?: number;
  sequenceIndex: number;
  spent: boolean;
  lastHitAt?: number;
}

export interface RuleEntry extends Rule {
  id: string;
  enabled: boolean;
  priority: number;
  state: RuleState;
}

export interface Health {
  app: string;
  appVersion?: string;
  version: string;
  protocol: number;
  activeMocks: number;
  transactions: number;
  agentWriteEnabled: boolean;
}

export interface TransactionSummary {
  id: string;
  startedAt: number;
  durationMs?: number;
  method: string;
  url: string;
  host: string;
  path: string;
  responseCode?: number;
  error?: string;
  mocked: boolean;
  mockRuleId?: string;
  tag?: string;
  requestBytes: number;
  responseBytes: number;
  responseContentType?: string;
}

export type Body =
  | { kind: "text"; text: string; contentType?: string; totalBytes: number }
  | { kind: "truncated"; text: string; contentType?: string; totalBytes: number }
  | { kind: "binary"; contentType?: string; totalBytes: number };

export interface Transaction extends TransactionSummary {
  requestHeaders: Record<string, string[]>;
  requestBody: Body | null;
  responseHeaders: Record<string, string[]>;
  responseBody: Body | null;
}

export interface Activity {
  at: number;
  kind: "read" | "write" | "connect";
  summary: string;
  call: string;
}

export type EventType = "transaction" | "rule_hit" | "rules_changed" | "client_connected" | "agent_activity";

export interface WireEvent {
  type: EventType;
  data: unknown;
}

export interface TransactionFilter {
  limit?: number;
  since?: number;
  path?: string;
  method?: string;
  mocked?: boolean;
}

export type Variant = "error" | "unauthorized" | "empty" | "slow" | "timeout" | "malformed";
export const VARIANTS: readonly Variant[] = ["error", "unauthorized", "empty", "slow", "timeout", "malformed"];

/** Raised for anything the user can act on; the message is shown verbatim by the CLI and MCP tools. */
export class DimockError extends Error {
  constructor(message: string, readonly code: string = "dimock_error") {
    super(message);
    this.name = "DimockError";
  }
}

import { DimockError, type Rule, type Transaction, type Variant, VARIANTS } from "./types.js";

/**
 * Derive a mock rule from a real capture. The first "AI" tool Chucker never had: the agent picks a
 * variant, the rule targets the captured method + path, and the body is shaped from the real payload.
 */
export interface VariantOptions {
  id?: string;
  times?: number;
  delayMs?: number;
  /** Replaces the variant's status (error, unauthorized, empty, slow only). */
  status?: number;
  /** Replaces the variant's body (error, unauthorized, empty, slow only). */
  body?: string;
  /** Content-Type sent with `body`; defaults to the variant's own. */
  contentType?: string;
}

const RESPONSE_VARIANTS: readonly Variant[] = ["error", "unauthorized", "empty", "slow"];

export function ruleFromCapture(tx: Transaction, variant: Variant, options: VariantOptions = {}): Rule {
  if (!VARIANTS.includes(variant)) throw new DimockError(`unknown variant '${variant}'. Use one of: ${VARIANTS.join(", ")}`, "invalid_variant");
  const overrides = options.status !== undefined || options.body !== undefined;
  if (overrides && !RESPONSE_VARIANTS.includes(variant)) {
    throw new DimockError(`status and body apply to ${RESPONSE_VARIANTS.join(", ")}; '${variant}' sends no response`, "invalid_variant");
  }
  if (options.status !== undefined && !(Number.isInteger(options.status) && options.status >= 100 && options.status <= 599)) {
    throw new DimockError(`status: must be 100..599, got ${options.status}`, "invalid_variant");
  }
  const match = { method: tx.method, path: tx.path };
  const id = options.id ?? `${slug(tx.path)}-${variant}`;
  const name = `${tx.method} ${tx.path} → ${variant}`;
  const base = { id, name, match, ...(options.times ? { times: options.times } : {}) };
  const capturedBody = tx.responseBody && tx.responseBody.kind !== "binary" ? tx.responseBody.text : undefined;
  const contentType = contentTypeOf(tx);
  const respond = (status: number, type: string | undefined, body: string, delayMs: number): Rule => {
    const headerType = options.contentType ?? type;
    return {
      ...base,
      respond: {
        status: options.status ?? status,
        headers: headerType ? { "Content-Type": headerType } : {},
        body: options.body ?? body,
        delayMs: options.delayMs ?? delayMs,
      },
    };
  };

  switch (variant) {
    case "error":
      return respond(500, "application/json", '{"error":"internal"}', 0);
    case "unauthorized":
      return respond(401, "application/json", '{"error":"unauthorized"}', 0);
    case "empty":
      return respond(200, contentType ?? "application/json", emptyBody(capturedBody), 0);
    case "slow":
      return respond(tx.responseCode ?? 200, contentType, capturedBody ?? "", 3000);
    case "timeout":
      return { ...base, fail: { type: "timeout", ...(options.delayMs ? { delayMs: options.delayMs } : {}) } };
    case "malformed":
      return { ...base, fail: { type: "malformed_body", ...(options.delayMs ? { delayMs: options.delayMs } : {}) } };
  }
}

const ENVELOPE_KEYS = new Set(["data", "items", "results", "result", "content", "meta", "page", "pagination", "status", "success", "message"]);

/**
 * Empty-state heuristic: arrays become `[]`, envelope keys are kept and emptied recursively,
 * other object values become `null`, scalars stay. Non-JSON bodies become `""`.
 */
export function emptyBody(body: string | undefined): string {
  if (body === undefined || body.trim() === "") return "[]";
  let parsed: unknown;
  try {
    parsed = JSON.parse(body);
  } catch {
    return "";
  }
  return JSON.stringify(emptyValue(parsed, true));
}

function emptyValue(value: unknown, top: boolean): unknown {
  if (Array.isArray(value)) return [];
  if (value && typeof value === "object") {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      if (Array.isArray(v)) out[k] = [];
      else if (v && typeof v === "object") out[k] = ENVELOPE_KEYS.has(k) ? emptyValue(v, false) : null;
      else if (typeof v === "number" && /count|total|size|length/i.test(k)) out[k] = 0;
      else out[k] = top || ENVELOPE_KEYS.has(k) ? v : v;
    }
    return out;
  }
  return value;
}

function contentTypeOf(tx: Transaction): string | undefined {
  const fromBody = tx.responseBody && "contentType" in tx.responseBody ? tx.responseBody.contentType : undefined;
  if (fromBody) return fromBody;
  const header = Object.entries(tx.responseHeaders ?? {}).find(([k]) => k.toLowerCase() === "content-type");
  return header?.[1]?.[0];
}

function slug(path: string): string {
  return path.replace(/[^a-zA-Z0-9]+/g, "-").replace(/^-|-$/g, "").toLowerCase() || "root";
}

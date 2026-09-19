import { DimockError, type Rule, type Transaction, type Variant, VARIANTS } from "./types.js";

/**
 * Derive a mock rule from a real capture. The first "AI" tool Chucker never had: the agent picks a
 * variant, the rule targets the captured method + path, and the body is shaped from the real payload.
 */
export function ruleFromCapture(tx: Transaction, variant: Variant, options: { id?: string; times?: number; delayMs?: number } = {}): Rule {
  if (!VARIANTS.includes(variant)) throw new DimockError(`unknown variant '${variant}'. Use one of: ${VARIANTS.join(", ")}`, "invalid_variant");
  const match = { method: tx.method, path: tx.path };
  const id = options.id ?? `${slug(tx.path)}-${variant}`;
  const name = `${tx.method} ${tx.path} → ${variant}`;
  const base = { id, name, match, ...(options.times ? { times: options.times } : {}) };
  const capturedBody = tx.responseBody && tx.responseBody.kind !== "binary" ? tx.responseBody.text : undefined;
  const contentType = contentTypeOf(tx);

  switch (variant) {
    case "error":
      return { ...base, respond: { status: 500, headers: { "Content-Type": "application/json" }, body: '{"error":"internal"}', delayMs: options.delayMs ?? 0 } };
    case "unauthorized":
      return { ...base, respond: { status: 401, headers: { "Content-Type": "application/json" }, body: '{"error":"unauthorized"}', delayMs: options.delayMs ?? 0 } };
    case "empty":
      return {
        ...base,
        respond: {
          status: 200,
          headers: { "Content-Type": contentType ?? "application/json" },
          body: emptyBody(capturedBody),
          delayMs: options.delayMs ?? 0,
        },
      };
    case "slow":
      return {
        ...base,
        respond: {
          status: tx.responseCode ?? 200,
          headers: contentType ? { "Content-Type": contentType } : {},
          body: capturedBody ?? "",
          delayMs: options.delayMs ?? 3000,
        },
      };
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

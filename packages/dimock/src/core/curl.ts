import type { Transaction } from "./types.js";

const SKIP = new Set(["host", "content-length", "accept-encoding", "connection", "user-agent"]);

/** Reproduce a captured request as a curl command. Redacted headers stay redacted on purpose. */
export function asCurl(tx: Transaction): string {
  const parts = ["curl", "-X", tx.method, shellQuote(tx.url)];
  for (const [name, values] of Object.entries(tx.requestHeaders ?? {})) {
    if (SKIP.has(name.toLowerCase())) continue;
    for (const v of values) parts.push("-H", shellQuote(`${name}: ${v}`));
  }
  if (tx.requestBody && tx.requestBody.kind !== "binary" && tx.requestBody.text.length > 0) {
    parts.push("--data-raw", shellQuote(tx.requestBody.text));
  }
  return parts.join(" ");
}

/** Leaves only characters no shell treats specially unquoted: `?`, `*` glob and `&` backgrounds. */
function shellQuote(s: string): string {
  if (/^[A-Za-z0-9_\-./:=@%+,]+$/.test(s)) return s;
  return `'${s.replace(/'/g, `'\\''`)}'`;
}

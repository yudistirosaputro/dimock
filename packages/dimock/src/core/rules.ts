import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import YAML from "yaml";
import { DimockError, type Respond, type Rule, type Step } from "./types.js";

const FAIL_TYPES = new Set(["timeout", "connection_reset", "malformed_body", "empty_body"]);

/**
 * Load a rules file (YAML or JSON). Accepts one rule, an array, or `{ rules: [...] }`.
 * `bodyFile` entries are inlined relative to the file's directory: the device only ever sees `body`.
 */
export async function loadRulesFile(file: string): Promise<Rule[]> {
  const text = await readFile(file, "utf8").catch(() => {
    throw new DimockError(`rules file not found: ${file}`, "file_not_found");
  });
  const parsed = parseRules(text, file);
  return inlineBodyFiles(parsed, dirname(resolve(file)));
}

export function parseRules(text: string, sourceName = "<inline>"): Rule[] {
  let doc: unknown;
  try {
    doc = YAML.parse(text);
  } catch (e) {
    throw new DimockError(`${sourceName}: not valid YAML/JSON (${(e as Error).message})`, "invalid_rules");
  }
  const list = Array.isArray(doc)
    ? doc
    : doc && typeof doc === "object" && Array.isArray((doc as { rules?: unknown }).rules)
      ? (doc as { rules: unknown[] }).rules
      : doc && typeof doc === "object"
        ? [doc]
        : null;
  if (!list) throw new DimockError(`${sourceName}: expected a rule, a list of rules, or { rules: [...] }`, "invalid_rules");
  return list.map((r, i) => validateRule(r, `${sourceName}[${i}]`));
}

export function validateRule(input: unknown, where = "rule"): Rule {
  if (!input || typeof input !== "object") throw new DimockError(`${where}: must be an object`, "invalid_rules");
  const r = input as Record<string, unknown>;
  if (r.match !== undefined && (typeof r.match !== "object" || r.match === null)) throw new DimockError(`${where}: match must be an object`, "invalid_rules");
  const hasRespond = r.respond !== undefined;
  const hasFail = r.fail !== undefined;
  const hasSequence = Array.isArray(r.sequence) && r.sequence.length > 0;
  if (!hasSequence && hasRespond === hasFail) {
    throw new DimockError(`${where}: needs exactly one of respond | fail, or a non-empty sequence`, "invalid_rules");
  }
  if (hasFail) validateFail(r.fail, `${where}.fail`);
  if (hasRespond) validateRespond(r.respond, `${where}.respond`);
  if (hasSequence) {
    for (const [i, step] of (r.sequence as unknown[]).entries()) {
      const s = step as Record<string, unknown>;
      if (s.respond !== undefined) validateRespond(s.respond, `${where}.sequence[${i}].respond`);
      else if (s.fail !== undefined) validateFail(s.fail, `${where}.sequence[${i}].fail`);
      else throw new DimockError(`${where}.sequence[${i}]: needs respond or fail`, "invalid_rules");
    }
  }
  if (r.times !== undefined && (typeof r.times !== "number" || r.times < 1)) throw new DimockError(`${where}: times must be a positive integer`, "invalid_rules");
  if (r.priority !== undefined && typeof r.priority !== "number") throw new DimockError(`${where}: priority must be a number`, "invalid_rules");
  return { ...(r as unknown as Rule), match: (r.match as Rule["match"]) ?? {} };
}

function validateRespond(v: unknown, where: string) {
  if (!v || typeof v !== "object") throw new DimockError(`${where}: must be an object`, "invalid_rules");
  const status = (v as Respond).status;
  if (status !== undefined && (typeof status !== "number" || status < 100 || status > 599)) throw new DimockError(`${where}.status: must be 100..599`, "invalid_rules");
  const body = (v as Respond).body;
  if (body !== undefined && typeof body !== "string") {
    // Authors often write body as a YAML mapping; serialise it for them.
    (v as Respond).body = JSON.stringify(body);
  }
}

function validateFail(v: unknown, where: string) {
  if (!v || typeof v !== "object") throw new DimockError(`${where}: must be an object`, "invalid_rules");
  const type = (v as { type?: unknown }).type;
  if (typeof type !== "string" || !FAIL_TYPES.has(type)) {
    throw new DimockError(`${where}.type: must be one of ${[...FAIL_TYPES].join(", ")}`, "invalid_rules");
  }
}

async function inlineBodyFiles(rules: Rule[], baseDir: string): Promise<Rule[]> {
  const out: Rule[] = [];
  for (const rule of rules) {
    const copy: Rule = { ...rule };
    if (copy.respond) copy.respond = await inlineRespond(copy.respond, baseDir);
    if (copy.sequence) {
      copy.sequence = await Promise.all(
        copy.sequence.map(async (step: Step) => ("respond" in step ? { respond: await inlineRespond(step.respond, baseDir) } : step)),
      );
    }
    out.push(copy);
  }
  return out;
}

async function inlineRespond(r: Respond, baseDir: string): Promise<Respond> {
  if (!r.bodyFile) return r;
  const file = resolve(baseDir, r.bodyFile);
  const body = await readFile(file, "utf8").catch(() => {
    throw new DimockError(`bodyFile not found: ${file}`, "file_not_found");
  });
  const { bodyFile: _dropped, ...rest } = r;
  return { ...rest, body };
}

/** Serialise rules back to YAML for `dimock mock list --yaml` and for files the agent writes. */
export function rulesToYaml(rules: Rule[]): string {
  return YAML.stringify(rules.map(stripState));
}

function stripState(rule: Rule & { state?: unknown }): Rule {
  const { state: _s, ...rest } = rule;
  return rest;
}

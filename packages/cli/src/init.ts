import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";

/**
 * `dimock init`: idempotent onboarding files so any agent in this project knows the tools exist.
 * Claude Code users get the plugin path; Cursor gets a rules file + MCP config; Codex and others get an AGENTS.md block.
 */

const START = "<!-- dimock:start -->";
const END = "<!-- dimock:end -->";

export const AGENT_SNIPPET = `${START}
## dimock (Android HTTP mocking)

This project uses dimock: the debug build captures HTTP traffic and can mock responses on-device.
An MCP server named \`dimock\` exposes the tools (devices_list, captures_list, captures_get, mock_set, mock_add,
mock_from_capture, mock_toggle, mock_clear, logcat_tail, agent_activity). Without MCP, the same commands exist as
\`npx dimock <command> --json\`.

Workflow for testing a screen's states without a backend:
1. Ask the human to open the screen once, then \`captures_list\` to find the real call.
2. \`mock_from_capture\` with variant error | empty | slow | timeout | malformed (dryRun first if unsure).
3. Ask the human to reload the screen; \`logcat_tail pattern:"FATAL|Exception"\` if it misbehaves.
4. \`mock_clear\` when finished. Never leave mocks active at the end of a task.

Rules format: https://github.com/yudistirosaputro/dimock/blob/main/docs/rule-format.md
${END}`;

const CURSOR_RULE = `---
description: Android HTTP capture and mocking with dimock
alwaysApply: false
globs: ["**/*.kt", "**/*.kts"]
---
${AGENT_SNIPPET}`;

const MCP_CONFIG = { mcpServers: { dimock: { command: "npx", args: ["-y", "dimock", "mcp"] } } };

export type AgentName = "claude" | "cursor" | "codex" | "all";

export async function writeAgentFiles(dir: string, agent: string): Promise<string[]> {
  const written: string[] = [];
  const which = (agent || "all") as AgentName;
  if (!["claude", "cursor", "codex", "all"].includes(which)) throw new Error(`unknown agent '${agent}': use claude | cursor | codex | all`);

  if (which === "claude" || which === "all") {
    // Claude Code: project-scoped MCP config. The plugin (skill + MCP) is the richer path; this makes the tools available regardless.
    written.push(await mergeJson(join(dir, ".mcp.json"), MCP_CONFIG));
    written.push(...(await upsertBlock(join(dir, "CLAUDE.md"), AGENT_SNIPPET)));
  }
  if (which === "cursor" || which === "all") {
    await mkdir(join(dir, ".cursor", "rules"), { recursive: true });
    await writeFile(join(dir, ".cursor", "rules", "dimock.mdc"), CURSOR_RULE);
    written.push(join(dir, ".cursor", "rules", "dimock.mdc"));
    written.push(await mergeJson(join(dir, ".cursor", "mcp.json"), MCP_CONFIG));
  }
  if (which === "codex" || which === "all") {
    written.push(...(await upsertBlock(join(dir, "AGENTS.md"), AGENT_SNIPPET)));
  }
  return written;
}

/** Insert or replace the dimock block between markers; create the file when absent. Returns the path when changed. */
async function upsertBlock(file: string, block: string): Promise<string[]> {
  const existing = await readFile(file, "utf8").catch(() => null);
  let next: string;
  if (existing === null) next = `${block}\n`;
  else if (existing.includes(START) && existing.includes(END)) {
    next = existing.slice(0, existing.indexOf(START)) + block + existing.slice(existing.indexOf(END) + END.length);
  } else next = `${existing.replace(/\s*$/, "")}\n\n${block}\n`;
  if (next === existing) return [];
  await writeFile(file, next);
  return [file];
}

async function mergeJson(file: string, add: { mcpServers: Record<string, unknown> }): Promise<string> {
  const existing = await readFile(file, "utf8").then((t) => JSON.parse(t) as { mcpServers?: Record<string, unknown> }).catch(() => ({}) as { mcpServers?: Record<string, unknown> });
  const merged = { ...existing, mcpServers: { ...(existing.mcpServers ?? {}), ...add.mcpServers } };
  await writeFile(file, `${JSON.stringify(merged, null, 2)}\n`);
  return file;
}

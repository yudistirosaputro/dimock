import { Session, type SessionOptions } from "../core/index.js";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { describeError, tools } from "./tools.js";

export { tools, describeError, formatRule, type ToolResult } from "./tools.js";

export const SERVER_NAME = "dimock";
export const SERVER_VERSION = "0.1.0-alpha01";

/** Build the MCP server; every tool is a thin wrapper over the shared Session. */
export function createMcpServer(options: SessionOptions = {}): McpServer {
  const session = new Session({ clientName: "claude-code", ...options });
  const server = new McpServer(
    { name: SERVER_NAME, version: SERVER_VERSION },
    {
      instructions:
        "dimock controls the HTTP traffic of an Android debug build: read what the app sent, and inject mock responses to drive Loading, Success, Error and Empty states. " +
        "Typical flow: devices_list → captures_list (after the human uses the screen) → mock_from_capture with a variant → tell the human to reload the screen → logcat_tail if it crashes → mock_clear when done. " +
        "Rules are documented in docs/rule-format.md of the dimock repository. When a tool answers with candidates, ask which device or app and retry with device/app set.",
    },
  );

  for (const tool of tools) {
    server.registerTool(
      tool.name,
      {
        title: tool.title,
        description: tool.description,
        inputSchema: tool.inputSchema,
        annotations: { readOnlyHint: tool.readOnly, destructiveHint: !tool.readOnly, openWorldHint: false },
      },
      async (input: unknown) => {
        try {
          const result = await tool.run(session, (input ?? {}) as never);
          return {
            content: [{ type: "text" as const, text: result.text }],
            structuredContent: wrap(result.data),
          };
        } catch (e) {
          const err = describeError(e);
          return {
            isError: true,
            content: [{ type: "text" as const, text: err.message }],
            structuredContent: { error: err.code, message: err.message, ...(err.candidates ? { candidates: err.candidates } : {}) },
          };
        }
      },
    );
  }
  return server;
}

/** structuredContent must be an object; arrays and scalars are wrapped. */
function wrap(data: unknown): Record<string, unknown> {
  if (data && typeof data === "object" && !Array.isArray(data)) return data as Record<string, unknown>;
  return { result: data };
}

export async function runStdio(options: SessionOptions = {}): Promise<void> {
  const server = createMcpServer(options);
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

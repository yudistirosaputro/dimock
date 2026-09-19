/** Public API of the `dimock` package: the wire client and rule helpers, the MCP server, and the CLI program. */
export * from "./core/index.js";
export { createMcpServer, runStdio, SERVER_NAME, SERVER_VERSION, tools, describeError, formatRule, type ToolResult } from "./mcp/index.js";
export { buildProgram } from "./cli/cli.js";
export { writeAgentFiles, AGENT_SNIPPET } from "./cli/init.js";

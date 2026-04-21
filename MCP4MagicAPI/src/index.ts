#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { loadConfig } from "./config.js";
import {
  SnippetGetInput,
  SnippetListInput,
  getSnippet,
  listSnippets,
} from "./tools/snippets.js";
import {
  BestPracticeLookupInput,
  BestPracticeListInput,
  lookupBestPractice,
  listBestPractices,
} from "./tools/bestPractices.js";

async function main(): Promise<void> {
  const paths = loadConfig();

  const server = new McpServer({
    name: "MCP4MagicAPI",
    version: "0.1.0",
  });

  server.registerTool(
    "snippet_get",
    {
      description:
        "Return a reusable Cameo/MagicDraw script snippet by name (e.g. 'session-wrap').",
      inputSchema: SnippetGetInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const snippet = await getSnippet(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(snippet, null, 2) }],
        structuredContent: snippet,
      };
    },
  );

  server.registerTool(
    "snippet_list",
    {
      description:
        "List all available snippet keys, optionally filtered by language.",
      inputSchema: SnippetListInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const snippets = await listSnippets(paths, args);
      const summary = snippets.map((s) => ({
        name: s.name,
        language: s.language,
        description: s.description,
      }));
      return {
        content: [{ type: "text", text: JSON.stringify(summary, null, 2) }],
        structuredContent: { snippets: summary },
      };
    },
  );

  server.registerTool(
    "best_practice_lookup",
    {
      description:
        "Return a curated best-practice card for a Cameo/MagicDraw scripting topic (e.g. 'sessions', 'no-fast-strings', 'error-reporting', 'finder', 'console-logger').",
      inputSchema: BestPracticeLookupInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const card = await lookupBestPractice(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(card, null, 2) }],
        structuredContent: card,
      };
    },
  );

  server.registerTool(
    "best_practice_list",
    {
      description:
        "List all available best-practice topics with a one-line summary each.",
      inputSchema: BestPracticeListInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async () => {
      const all = await listBestPractices(paths);
      return {
        content: [{ type: "text", text: JSON.stringify(all, null, 2) }],
        structuredContent: { topics: all },
      };
    },
  );

  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((err) => {
  process.stderr.write(
    `MCP4MagicAPI fatal error: ${err instanceof Error ? err.stack : String(err)}\n`,
  );
  process.exit(1);
});

import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { existsSync } from "node:fs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(__dirname, "..", "..");
const serverEntry = path.join(projectRoot, "build", "index.js");
const fixtureExamples = path.join(
  projectRoot,
  "tests",
  "fixtures",
  "examples-mock",
);

interface JsonRpcMessage {
  jsonrpc: "2.0";
  id?: number | string;
  method?: string;
  params?: unknown;
  result?: unknown;
  error?: { code: number; message: string; data?: unknown };
}

/**
 * Minimal stdio JSON-RPC client for the MCP server.
 * Writes newline-delimited JSON (what @modelcontextprotocol/sdk StdioServerTransport speaks).
 */
class StdioClient {
  private nextId = 1;
  private buffer = "";
  private pending = new Map<number | string, (msg: JsonRpcMessage) => void>();

  constructor(private proc: ChildProcessWithoutNullStreams) {
    proc.stdout.setEncoding("utf8");
    proc.stdout.on("data", (chunk: string) => {
      this.buffer += chunk;
      let idx: number;
      while ((idx = this.buffer.indexOf("\n")) >= 0) {
        const line = this.buffer.slice(0, idx).trim();
        this.buffer = this.buffer.slice(idx + 1);
        if (!line) continue;
        try {
          const msg = JSON.parse(line) as JsonRpcMessage;
          if (msg.id != null && this.pending.has(msg.id)) {
            this.pending.get(msg.id)!(msg);
            this.pending.delete(msg.id);
          }
        } catch {
          /* ignore non-JSON noise */
        }
      }
    });
  }

  async request(method: string, params: unknown): Promise<JsonRpcMessage> {
    const id = this.nextId++;
    const payload: JsonRpcMessage = { jsonrpc: "2.0", id, method, params };
    return new Promise((resolve) => {
      this.pending.set(id, resolve);
      this.proc.stdin.write(JSON.stringify(payload) + "\n");
    });
  }

  notify(method: string, params: unknown): void {
    const payload: JsonRpcMessage = { jsonrpc: "2.0", method, params };
    this.proc.stdin.write(JSON.stringify(payload) + "\n");
  }
}

describe("MCP server (stdio)", () => {
  let proc: ChildProcessWithoutNullStreams;
  let client: StdioClient;

  beforeAll(async () => {
    if (!existsSync(serverEntry)) {
      throw new Error(
        `Built server not found at ${serverEntry}. Run 'npm run build' first.`,
      );
    }
    proc = spawn("node", [serverEntry], {
      stdio: ["pipe", "pipe", "pipe"],
      env: {
        ...process.env,
        CAMEO_EXAMPLES_PATH: fixtureExamples,
      },
    }) as ChildProcessWithoutNullStreams;
    proc.stderr.on("data", (d) => {
      process.stderr.write(`[server stderr] ${d}`);
    });
    client = new StdioClient(proc);

    await client.request("initialize", {
      protocolVersion: "2024-11-05",
      capabilities: {},
      clientInfo: { name: "integration-test", version: "0.0.0" },
    });
    client.notify("notifications/initialized", {});
  });

  afterAll(() => {
    proc.kill();
  });

  it("lists the snippet tools", async () => {
    const resp = await client.request("tools/list", {});
    expect(resp.error).toBeUndefined();
    const tools = (resp.result as { tools: Array<{ name: string }> }).tools;
    const names = tools.map((t) => t.name);
    expect(names).toContain("snippet_get");
    expect(names).toContain("snippet_list");
  });

  it("returns the session-wrap snippet via tools/call", async () => {
    const resp = await client.request("tools/call", {
      name: "snippet_get",
      arguments: { name: "session-wrap" },
    });
    expect(resp.error).toBeUndefined();
    const result = resp.result as {
      content: Array<{ type: string; text: string }>;
      structuredContent?: { name?: string; body?: string };
    };
    expect(result.content[0].type).toBe("text");
    const parsed = JSON.parse(result.content[0].text);
    expect(parsed.name).toBe("session-wrap");
    expect(parsed.body).toContain("createSession");
    expect(result.structuredContent?.name).toBe("session-wrap");
  });

  it("returns an error result for an unknown snippet", async () => {
    const resp = await client.request("tools/call", {
      name: "snippet_get",
      arguments: { name: "nope" },
    });
    const result = resp.result as { isError?: boolean; content: unknown[] };
    const errorlike = result?.isError === true || resp.error != null;
    expect(errorlike).toBe(true);
  });

  it("example_list returns fixture projects and respects tag filter", async () => {
    const resp = await client.request("tools/call", {
      name: "example_list",
      arguments: { tag: "matrix" },
    });
    expect(resp.error).toBeUndefined();
    const result = resp.result as {
      structuredContent?: { examples: Array<{ name: string }> };
    };
    const names = (result.structuredContent?.examples ?? []).map((e) => e.name);
    expect(names).toContain("dependencymatrix_sample");
    expect(names).not.toContain("plain");
  });

  it("example_list_files returns paths under the requested example", async () => {
    const resp = await client.request("tools/call", {
      name: "example_list_files",
      arguments: { example: "sampleaction" },
    });
    const result = resp.result as {
      structuredContent?: { files: Array<{ relativePath: string; kind: string }> };
    };
    const paths = (result.structuredContent?.files ?? []).map((f) => f.relativePath);
    expect(paths).toContain("plugin.xml");
    expect(paths).toContain("src/com/fake/SampleActionPlugin.java");
  });

  it("example_read_file refuses path traversal", async () => {
    const resp = await client.request("tools/call", {
      name: "example_read_file",
      arguments: {
        example: "sampleaction",
        relativePath: "../plain/src/com/fake/Plain.java",
      },
    });
    const result = resp.result as { isError?: boolean };
    const errorlike = result?.isError === true || resp.error != null;
    expect(errorlike).toBe(true);
  });

  it("example_search finds a substring and reports file+line", async () => {
    const resp = await client.request("tools/call", {
      name: "example_search",
      arguments: { query: "hello", fileType: "java" },
    });
    expect(resp.error).toBeUndefined();
    const result = resp.result as {
      structuredContent?: {
        matches: Array<{ example: string; relativePath: string; lineNumber: number; line: string }>;
      };
    };
    const matches = result.structuredContent?.matches ?? [];
    expect(matches.length).toBeGreaterThan(0);
    expect(matches[0].example).toBe("sampleaction");
    expect(matches[0].relativePath.endsWith(".java")).toBe(true);
    expect(matches[0].lineNumber).toBeGreaterThan(0);
  });

  it("exposes best_practice_lookup and returns the no-fast-strings card", async () => {
    const list = await client.request("tools/list", {});
    const tools = (list.result as { tools: Array<{ name: string }> }).tools;
    expect(tools.map((t) => t.name)).toContain("best_practice_lookup");
    expect(tools.map((t) => t.name)).toContain("best_practice_list");

    const resp = await client.request("tools/call", {
      name: "best_practice_lookup",
      arguments: { topic: "no-fast-strings" },
    });
    expect(resp.error).toBeUndefined();
    const result = resp.result as {
      content: Array<{ type: string; text: string }>;
      structuredContent?: { topic?: string };
    };
    const parsed = JSON.parse(result.content[0].text);
    expect(parsed.topic).toBe("no-fast-strings");
    expect(parsed.summary).toMatch(/GString/);
    expect(result.structuredContent?.topic).toBe("no-fast-strings");
  });
});

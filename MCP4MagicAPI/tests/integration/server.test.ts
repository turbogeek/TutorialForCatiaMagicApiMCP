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
const fixtureGuide = path.join(projectRoot, "tests", "fixtures", "guide-mock");
const fixtureJavadoc = path.join(projectRoot, "tests", "fixtures", "javadoc-mock");

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
        CAMEO_GUIDE_PATH: fixtureGuide,
        CAMEO_JAVADOC_PATH: fixtureJavadoc,
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

  it("guide_list_pages returns fixture pages with titles and labels", async () => {
    const resp = await client.request("tools/call", {
      name: "guide_list_pages",
      arguments: {},
    });
    const result = resp.result as {
      structuredContent?: { pages: Array<{ pageId: string; title: string }> };
    };
    const ids = (result.structuredContent?.pages ?? []).map((p) => p.pageId).sort();
    expect(ids).toContain("254437443");
    expect(ids).toContain("254437121");
  });

  it("guide_read_page resolves by pageId and returns body + code", async () => {
    const resp = await client.request("tools/call", {
      name: "guide_read_page",
      arguments: { page: "254437443" },
    });
    const result = resp.result as {
      structuredContent?: {
        page?: { title?: string; text?: string; codeBlocks?: Array<{ code: string }> };
      };
    };
    const page = result.structuredContent?.page;
    expect(page?.title).toBe("Session management");
    expect(page?.text).toContain("SessionManager");
    expect((page?.codeBlocks ?? []).length).toBeGreaterThan(0);
  });

  it("validate_script_syntax accepts a valid Groovy println", async () => {
    const resp = await client.request("tools/call", {
      name: "validate_script_syntax",
      arguments: { language: "groovy", code: "println 'hello'\n" },
    });
    const result = resp.result as {
      structuredContent?: { result: { ok: boolean; compiler: string | null } };
    };
    const r = result.structuredContent?.result;
    // If no compiler is installed, skip — the validator returns compiler=null.
    if (r && r.compiler != null) {
      expect(r.ok).toBe(true);
    }
  }, 60_000);

  it("javadoc_verify_fqn confirms a real FQN and returns a correction for a hallucinated one", async () => {
    const good = await client.request("tools/call", {
      name: "javadoc_verify_fqn",
      arguments: {
        fqn: "com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype",
      },
    });
    expect(good.error).toBeUndefined();
    const goodRes = good.result as { structuredContent?: { exists: boolean } };
    expect(goodRes.structuredContent?.exists).toBe(true);

    const bad = await client.request("tools/call", {
      name: "javadoc_verify_fqn",
      arguments: {
        fqn: "com.nomagic.uml2.ext.magicdraw.classes.mdprofiles.Stereotype",
      },
    });
    const badRes = bad.result as {
      structuredContent?: { exists: boolean; candidates: string[] };
    };
    expect(badRes.structuredContent?.exists).toBe(false);
    expect(badRes.structuredContent?.candidates).toContain(
      "com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype",
    );
  });

  it("javadoc_search ranks exact class match first", async () => {
    const resp = await client.request("tools/call", {
      name: "javadoc_search",
      arguments: { query: "SessionManager", kind: "class", limit: 3 },
    });
    const result = resp.result as {
      structuredContent?: { hits: Array<{ kind: string; simpleName: string }> };
    };
    const hits = result.structuredContent?.hits ?? [];
    expect(hits.length).toBeGreaterThan(0);
    expect(hits[0].simpleName).toBe("SessionManager");
    expect(hits[0].kind).toBe("class");
  });

  it("javadoc_list_packages returns the fixture package list", async () => {
    const resp = await client.request("tools/call", {
      name: "javadoc_list_packages",
      arguments: { prefix: "com.nomagic.magicdraw.openapi" },
    });
    const result = resp.result as {
      structuredContent?: { packages: string[] };
    };
    expect(result.structuredContent?.packages).toContain(
      "com.nomagic.magicdraw.openapi.uml",
    );
  });

  it("javadoc_get_class returns structured methods for SessionManager", async () => {
    const resp = await client.request("tools/call", {
      name: "javadoc_get_class",
      arguments: {
        fqn: "com.nomagic.magicdraw.openapi.uml.SessionManager",
      },
    });
    const result = resp.result as {
      structuredContent?: {
        class?: {
          simpleName?: string;
          methods?: Array<{ name: string; deprecated: boolean }>;
        };
      };
    };
    const klass = result.structuredContent?.class;
    expect(klass?.simpleName).toBe("SessionManager");
    const names = (klass?.methods ?? []).map((m) => m.name);
    expect(names).toContain("createSession");
    expect(names).toContain("closeSession");
  });

  it("guide_search ranks Session management #1 for 'SessionManager'", async () => {
    const resp = await client.request("tools/call", {
      name: "guide_search",
      arguments: { query: "SessionManager", limit: 3 },
    });
    const result = resp.result as {
      structuredContent?: { hits: Array<{ title: string; score: number }> };
    };
    const hits = result.structuredContent?.hits ?? [];
    expect(hits.length).toBeGreaterThan(0);
    expect(hits[0].title).toBe("Session management");
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

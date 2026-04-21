import { describe, it, expect, beforeEach } from "vitest";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  getSnippet,
  listSnippets,
  resetSnippetCache,
} from "../../src/tools/snippets.js";
import type { CameoPaths } from "../../src/config.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(__dirname, "..", "..");

const testPaths: CameoPaths = {
  javadocRoot: "N/A",
  guideRoot: "N/A",
  examplesRoot: "N/A",
  dataDir: path.join(projectRoot, "src", "data"),
  cacheDir: path.join(projectRoot, ".cache"),
  logsDir: path.join(projectRoot, "logs"),
};

describe("snippet store", () => {
  beforeEach(() => {
    resetSnippetCache();
  });

  it("loads the bundled session-wrap snippet with required fields", async () => {
    const snippet = await getSnippet(testPaths, { name: "session-wrap" });
    expect(snippet.name).toBe("session-wrap");
    expect(snippet.language).toBe("groovy");
    expect(snippet.body).toContain("SessionManager.getInstance()");
    expect(snippet.body).toContain("createSession");
    expect(snippet.body).toContain("closeSession");
    expect(snippet.body).toContain("cancelSession");
  });

  it("lists six seeded snippets", async () => {
    const all = await listSnippets(testPaths, {});
    expect(all.length).toBeGreaterThanOrEqual(6);
    const names = all.map((s) => s.name).sort();
    expect(names).toContain("session-wrap");
    expect(names).toContain("gui-log-error");
    expect(names).toContain("finder-by-qname");
    expect(names).toContain("finder-by-type");
    expect(names).toContain("headless-detect");
    expect(names).toContain("association-ends");
  });

  it("filters by language", async () => {
    const groovy = await listSnippets(testPaths, { language: "groovy" });
    expect(groovy.every((s) => s.language === "groovy")).toBe(true);
    const java = await listSnippets(testPaths, { language: "java" });
    expect(java).toHaveLength(0);
  });

  it("throws a helpful error for an unknown snippet name", async () => {
    await expect(
      getSnippet(testPaths, { name: "does-not-exist" }),
    ).rejects.toThrow(/Unknown snippet/);
  });
});

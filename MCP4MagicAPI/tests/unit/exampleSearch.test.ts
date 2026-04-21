import { describe, it, expect } from "vitest";
import path from "node:path";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { searchExamples } from "../../src/adapters/examples.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const fixtures = path.resolve(__dirname, "..", "fixtures", "examples-mock");

describe("searchExamples (fixtures)", () => {
  it("finds a substring case-insensitively by default", async () => {
    const hits = await searchExamples(fixtures, { query: "hello" });
    expect(hits.length).toBeGreaterThan(0);
    const hit = hits.find(
      (h) => h.relativePath === "src/com/fake/SampleActionPlugin.java",
    );
    expect(hit).toBeDefined();
    expect(hit!.line.toLowerCase()).toContain("hello");
    expect(hit!.lineNumber).toBeGreaterThan(0);
  });

  it("honors caseSensitive: true", async () => {
    const insens = await searchExamples(fixtures, { query: "HELLO" });
    const sens = await searchExamples(fixtures, {
      query: "HELLO",
      caseSensitive: true,
    });
    expect(insens.length).toBeGreaterThan(0);
    expect(sens.length).toBe(0);
  });

  it("filters by fileType", async () => {
    const all = await searchExamples(fixtures, { query: "plugin" });
    const xmlOnly = await searchExamples(fixtures, {
      query: "plugin",
      fileType: "xml",
    });
    expect(xmlOnly.length).toBeGreaterThan(0);
    expect(xmlOnly.every((h) => h.relativePath.endsWith(".xml"))).toBe(true);
    expect(all.length).toBeGreaterThanOrEqual(xmlOnly.length);
  });

  it("filters by exampleFilter", async () => {
    const hits = await searchExamples(fixtures, {
      query: "plugin",
      exampleFilter: "sampleaction",
    });
    expect(hits.length).toBeGreaterThan(0);
    expect(hits.every((h) => h.example === "sampleaction")).toBe(true);
  });

  it("caps results by maxMatchesPerFile", async () => {
    const hits = await searchExamples(fixtures, {
      query: "plugin",
      maxMatchesPerFile: 1,
    });
    const perFileCounts = new Map<string, number>();
    for (const h of hits) {
      const key = `${h.example}/${h.relativePath}`;
      perFileCounts.set(key, (perFileCounts.get(key) ?? 0) + 1);
    }
    for (const count of perFileCounts.values()) {
      expect(count).toBeLessThanOrEqual(1);
    }
  });

  it("rejects an empty query", async () => {
    await expect(searchExamples(fixtures, { query: "" })).rejects.toThrow(
      /non-empty/,
    );
  });
});

describe("searchExamples (real corpus smoke)", () => {
  const realRoot = "E:\\Magic SW\\MCSE26xR1_4_2\\openapi\\examples";
  const exists = existsSync(realRoot);

  it.skipIf(!exists)(
    "finds SessionManager.getInstance across real examples",
    async () => {
      const hits = await searchExamples(realRoot, {
        query: "SessionManager.getInstance",
        fileType: "java",
        maxTotalMatches: 20,
      });
      expect(hits.length).toBeGreaterThan(0);
      // At least one match should land in an examples directory name that mentions refactor or scenarios
      const examples = new Set(hits.map((h) => h.example));
      expect(examples.size).toBeGreaterThanOrEqual(1);
    },
    60_000,
  );
});

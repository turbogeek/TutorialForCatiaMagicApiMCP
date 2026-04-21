import { describe, it, expect, beforeEach } from "vitest";
import path from "node:path";
import fs from "node:fs/promises";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import os from "node:os";
import {
  searchGuide,
  getGuideIndex,
  resetGuideSearchIndex,
  tokenize,
} from "../../src/adapters/guideSearch.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const fixtures = path.resolve(__dirname, "..", "fixtures", "guide-mock");

async function freshCacheDir(): Promise<string> {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "mcp-guide-cache-"));
  return dir;
}

describe("tokenize", () => {
  it("lowercases, drops stop words, keeps identifiers and camelCase words", () => {
    const toks = tokenize("The SessionManager.createSession is the entry point.");
    expect(toks).toContain("sessionmanager");
    expect(toks).toContain("createsession");
    expect(toks).toContain("entry");
    expect(toks).toContain("point");
    expect(toks).not.toContain("the");
    expect(toks).not.toContain("is");
  });

  it("drops 1-char tokens", () => {
    const toks = tokenize("a b cde");
    expect(toks).toEqual(["cde"]);
  });
});

describe("searchGuide (fixtures)", () => {
  beforeEach(() => {
    resetGuideSearchIndex();
  });

  it("finds the Session-management page for the query 'SessionManager'", async () => {
    const cache = await freshCacheDir();
    const hits = await searchGuide(
      { guideRoot: fixtures, cacheDir: cache },
      "SessionManager",
    );
    expect(hits.length).toBeGreaterThan(0);
    expect(hits[0].title).toBe("Session management");
    expect(hits[0].snippet.toLowerCase()).toContain("sessionmanager");
  });

  it("title-boost ranks 'Session management' above a page that only mentions session in body", async () => {
    const cache = await freshCacheDir();
    const hits = await searchGuide(
      { guideRoot: fixtures, cacheDir: cache },
      "session",
      { limit: 5 },
    );
    expect(hits.length).toBeGreaterThan(0);
    expect(hits[0].title).toBe("Session management");
  });

  it("rejects an empty query", async () => {
    const cache = await freshCacheDir();
    await expect(
      searchGuide({ guideRoot: fixtures, cacheDir: cache }, ""),
    ).rejects.toThrow(/non-empty/);
  });

  it("writes a cache file on first build and reuses it on the second call", async () => {
    const cache = await freshCacheDir();
    await getGuideIndex({ guideRoot: fixtures, cacheDir: cache });
    const cachePath = path.join(cache, "guide-index.json");
    expect(existsSync(cachePath)).toBe(true);
    const first = await fs.stat(cachePath);
    resetGuideSearchIndex();
    // Second call must NOT rewrite (sources unchanged).
    await getGuideIndex({ guideRoot: fixtures, cacheDir: cache });
    const second = await fs.stat(cachePath);
    expect(second.mtimeMs).toBe(first.mtimeMs);
  });

  it("forceRebuild re-creates the cache even when sources are unchanged", async () => {
    const cache = await freshCacheDir();
    await getGuideIndex({ guideRoot: fixtures, cacheDir: cache });
    const cachePath = path.join(cache, "guide-index.json");
    const first = await fs.stat(cachePath);
    // wait >2ms to ensure mtime differs on filesystems with coarse resolution
    await new Promise((r) => setTimeout(r, 20));
    resetGuideSearchIndex();
    await getGuideIndex({
      guideRoot: fixtures,
      cacheDir: cache,
      forceRebuild: true,
    });
    const second = await fs.stat(cachePath);
    expect(second.mtimeMs).toBeGreaterThan(first.mtimeMs);
  });
});

describe("searchGuide (real corpus smoke)", () => {
  const realRoot = "E:\\Magic SW\\MCSE26xR1_4_2\\openapi\\guide\\guide";
  const exists = existsSync(realRoot);

  it.skipIf(!exists)(
    "finds Session-management in the top 5 for 'SessionManager'",
    async () => {
      resetGuideSearchIndex();
      const cache = await freshCacheDir();
      const hits = await searchGuide(
        { guideRoot: realRoot, cacheDir: cache },
        "SessionManager",
        { limit: 5 },
      );
      const titles = hits.map((h) => h.title);
      expect(titles).toContain("Session management");
    },
    60_000,
  );
});

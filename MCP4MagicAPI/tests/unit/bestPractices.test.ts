import { describe, it, expect, beforeEach } from "vitest";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  lookupBestPractice,
  listBestPractices,
  resetBestPracticeCache,
} from "../../src/tools/bestPractices.js";
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
  projectRoot,
  apiVersion: "26xR1",
  modelingTypes: [],
  activeProfileName: null,
};

describe("best practices", () => {
  beforeEach(() => {
    resetBestPracticeCache();
  });

  it("includes the core topics cited in the plan", async () => {
    const all = await listBestPractices(testPaths);
    const topics = all.map((t) => t.topic).sort();
    for (const required of [
      "sessions",
      "error-reporting",
      "finder",
      "no-fast-strings",
      "no-system-exit",
      "headless",
      "rest-harness",
      "collections-are-live",
      "association-ends",
      "tdd-loop",
      "console-logger",
      "dedicated-log-file",
      "batch-runner",
      "verify-fqn",
      "ask-first",
    ]) {
      expect(topics).toContain(required);
    }
  });

  it("the no-fast-strings card names the root cause and the fix", async () => {
    const card = await lookupBestPractice(testPaths, { topic: "no-fast-strings" });
    expect(card.summary).toMatch(/GString/);
    expect(card.summary).toMatch(/java\.lang\.String/);
    const allText = [card.summary, ...card.do, ...card.dont].join(" ");
    expect(allText).toMatch(/single-quoted|\.toString\(\)/);
  });

  it("the sessions card points at the session-wrap snippet", async () => {
    const card = await lookupBestPractice(testPaths, { topic: "sessions" });
    expect(card.snippet).toBe("session-wrap");
    expect(card.do.join(" ")).toMatch(/createSession/);
    expect(card.do.join(" ")).toMatch(/closeSession/);
    expect(card.do.join(" ")).toMatch(/cancelSession/);
  });

  it("the console-logger card points at logger-usage and warns about GUI spam", async () => {
    const card = await lookupBestPractice(testPaths, { topic: "console-logger" });
    expect(card.snippet).toBe("logger-usage");
    expect(card.dont.join(" ")).toMatch(/clutter|spam|GUILog/i);
  });

  it("every do/dont entry is a non-empty string", async () => {
    const all = await listBestPractices(testPaths);
    for (const { topic } of all) {
      const card = await lookupBestPractice(testPaths, { topic });
      expect(card.do.length).toBeGreaterThan(0);
      expect(card.dont.length).toBeGreaterThan(0);
      for (const s of [...card.do, ...card.dont]) {
        expect(s.trim().length).toBeGreaterThan(0);
      }
    }
  });

  it("throws a helpful error for unknown topic", async () => {
    await expect(
      lookupBestPractice(testPaths, { topic: "nope" }),
    ).rejects.toThrow(/Unknown best-practice topic/);
  });
});

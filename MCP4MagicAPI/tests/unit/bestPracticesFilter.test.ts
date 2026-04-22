import { describe, it, expect, beforeEach } from "vitest";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  listBestPractices,
  resetBestPracticeCache,
} from "../../src/tools/bestPractices.js";
import type { CameoPaths } from "../../src/config.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(__dirname, "..", "..");

function pathsWith(modelingTypes: CameoPaths["modelingTypes"]): CameoPaths {
  return {
    javadocRoot: "N/A",
    guideRoot: "N/A",
    examplesRoot: "N/A",
    dataDir: path.join(projectRoot, "src", "data"),
    cacheDir: path.join(projectRoot, ".cache"),
    logsDir: path.join(projectRoot, "logs"),
    projectRoot,
    apiVersion: "26xR1",
    modelingTypes,
    activeProfileName: "test",
  };
}

describe("listBestPractices filtering by modeling type", () => {
  beforeEach(() => resetBestPracticeCache());

  it("returns ALL cards when no modeling type is active", async () => {
    const all = await listBestPractices(pathsWith([]));
    expect(all.length).toBeGreaterThanOrEqual(14); // includes ask-first, verify-fqn, etc.
  });

  it("includes association-ends for SysMLv1 (tagged with UML/SysMLv1/UAF)", async () => {
    const sysmlv1 = await listBestPractices(pathsWith(["SysMLv1"]));
    expect(sysmlv1.some((c) => c.topic === "association-ends")).toBe(true);
  });

  it("excludes association-ends for SysMLv2 (not in its modelingTypes list)", async () => {
    const sysmlv2 = await listBestPractices(pathsWith(["SysMLv2"]));
    expect(sysmlv2.some((c) => c.topic === "association-ends")).toBe(false);
    // But universal cards (no modelingTypes field) remain.
    expect(sysmlv2.some((c) => c.topic === "sessions")).toBe(true);
    expect(sysmlv2.some((c) => c.topic === "no-fast-strings")).toBe(true);
  });

  it("ask-first is always visible (universal card)", async () => {
    const anything = await listBestPractices(pathsWith(["SysMLv2"]));
    expect(anything.some((c) => c.topic === "ask-first")).toBe(true);
  });
});

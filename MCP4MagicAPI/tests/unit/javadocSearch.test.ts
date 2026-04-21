import { describe, it, expect, beforeEach } from "vitest";
import path from "node:path";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  searchJavadoc,
  resetJavadocSearchIndex,
} from "../../src/adapters/javadocSearch.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const fixtures = path.resolve(__dirname, "..", "fixtures", "javadoc-mock");

describe("searchJavadoc (fixtures)", () => {
  beforeEach(() => {
    resetJavadocSearchIndex();
  });

  it("returns SessionManager first for an exact class-name query", async () => {
    const hits = await searchJavadoc(fixtures, "SessionManager");
    expect(hits.length).toBeGreaterThan(0);
    expect(hits[0].kind).toBe("class");
    expect(hits[0].simpleName).toBe("SessionManager");
    expect(hits[0].fqn).toBe(
      "com.nomagic.magicdraw.openapi.uml.SessionManager",
    );
  });

  it("finds methods across classes for 'getInstance'", async () => {
    const hits = await searchJavadoc(fixtures, "getInstance", { kind: "method" });
    expect(hits.length).toBeGreaterThanOrEqual(2);
    expect(hits.every((h) => h.kind === "method")).toBe(true);
    const owners = hits.map((h) => h.owner);
    expect(owners).toContain("com.nomagic.magicdraw.openapi.uml.SessionManager");
    expect(owners).toContain("com.nomagic.magicdraw.core.Application");
  });

  it("kind filter honors 'class' only", async () => {
    const hits = await searchJavadoc(fixtures, "Session", { kind: "class" });
    expect(hits.every((h) => h.kind === "class")).toBe(true);
    expect(hits.some((h) => h.simpleName === "SessionManager")).toBe(true);
  });

  it("kind filter honors 'package' only", async () => {
    const hits = await searchJavadoc(fixtures, "magicdraw", { kind: "package" });
    expect(hits.length).toBeGreaterThan(0);
    expect(hits.every((h) => h.kind === "package")).toBe(true);
  });

  it("ranks prefix matches over substring matches", async () => {
    const hits = await searchJavadoc(fixtures, "Finder");
    // 'Finder' exact-matches the class with score 1000.
    // 'Finder.ByQualifiedName' starts with 'Finder' at score 500.
    const finder = hits.find((h) => h.simpleName === "Finder");
    const nested = hits.find((h) => h.simpleName === "Finder.ByQualifiedName");
    expect(finder).toBeDefined();
    expect(nested).toBeDefined();
    expect(finder!.score).toBeGreaterThan(nested!.score);
  });

  it("respects limit", async () => {
    const hits = await searchJavadoc(fixtures, "e", { limit: 3 });
    expect(hits.length).toBeLessThanOrEqual(3);
  });

  it("rejects an empty query", async () => {
    await expect(searchJavadoc(fixtures, "")).rejects.toThrow(/non-empty/);
  });
});

describe("searchJavadoc (real corpus smoke)", () => {
  const realRoot =
    "E:\\Magic SW\\MCSE26xR1_4_2\\openapi\\docs\\md-javadoc-2026.1.0-485-c01d52da-javadoc";
  const exists = existsSync(realRoot);

  it.skipIf(!exists)(
    "finds SessionManager via the shipped 14MB member index",
    async () => {
      resetJavadocSearchIndex();
      const hits = await searchJavadoc(realRoot, "SessionManager", {
        kind: "class",
        limit: 5,
      });
      expect(hits.length).toBeGreaterThan(0);
      expect(hits[0].simpleName).toBe("SessionManager");
    },
    30_000,
  );
});

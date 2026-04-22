import { describe, it, expect, beforeEach } from "vitest";
import path from "node:path";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  verifyFqn,
  searchJavadoc,
  resetJavadocSearchIndex,
  packagePriority,
  classNamePriority,
} from "../../src/adapters/javadocSearch.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const fixtures = path.resolve(__dirname, "..", "fixtures", "javadoc-mock");

describe("packagePriority", () => {
  it("demotes ecore / impl / emfuml2xmi", () => {
    expect(packagePriority("com.nomagic.magicdraw.ecore.helpers")).toBeLessThan(0);
    expect(packagePriority("com.nomagic.uml2.impl")).toBeLessThan(0);
    expect(packagePriority("com.nomagic.magicdraw.emfuml2xmi_v5")).toBeLessThan(0);
  });

  it("boosts helpers and the main magicdraw/sysml paths", () => {
    expect(packagePriority("com.nomagic.uml2.ext.jmi.helpers")).toBeGreaterThan(0);
    expect(packagePriority("com.nomagic.magicdraw.openapi.uml")).toBeGreaterThan(0);
    expect(packagePriority("com.nomagic.magicdraw.sysml.util")).toBeGreaterThan(0);
  });
});

describe("classNamePriority", () => {
  it("rewards Helper / Factory / Finder / Manager suffixes", () => {
    expect(classNamePriority("StereotypesHelper")).toBeGreaterThan(0);
    expect(classNamePriority("ElementsFactory")).toBeGreaterThan(0);
    expect(classNamePriority("Finder")).toBeGreaterThan(0);
    expect(classNamePriority("SessionManager")).toBeGreaterThan(0);
    expect(classNamePriority("Random")).toBe(0);
  });
});

describe("searchJavadoc ranking with the new priority", () => {
  beforeEach(() => resetJavadocSearchIndex());

  it("ranks the non-ecore StereotypesHelper above the ecore one", async () => {
    const hits = await searchJavadoc(fixtures, "StereotypesHelper", { kind: "class" });
    expect(hits.length).toBeGreaterThanOrEqual(2);
    const nonEcore = hits.findIndex((h) => h.fqn === "com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper");
    const ecore = hits.findIndex((h) => h.fqn === "com.nomagic.magicdraw.ecore.helpers.StereotypesHelper");
    expect(nonEcore).toBeGreaterThanOrEqual(0);
    expect(ecore).toBeGreaterThanOrEqual(0);
    expect(nonEcore).toBeLessThan(ecore);
  });

  it("ranks the impl ElementsFactory above the ecore one (impl is canonical)", async () => {
    // Both are demoted, but ecore is demoted more than impl (per the table).
    const hits = await searchJavadoc(fixtures, "ElementsFactory", { kind: "class" });
    const impl = hits.findIndex((h) => h.fqn === "com.nomagic.uml2.impl.ElementsFactory");
    const ecore = hits.findIndex((h) => h.fqn === "com.nomagic.magicdraw.ecore.factory.ElementsFactory");
    expect(impl).toBeGreaterThanOrEqual(0);
    expect(ecore).toBeGreaterThanOrEqual(0);
    expect(impl).toBeLessThan(ecore);
  });
});

describe("verifyFqn (fixtures)", () => {
  beforeEach(() => resetJavadocSearchIndex());

  it("returns exists=true for a real FQN", async () => {
    const r = await verifyFqn(
      fixtures,
      "com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype",
    );
    expect(r.exists).toBe(true);
  });

  it("flags the classic 'classes.mdprofiles.Stereotype' hallucination with the correct candidate", async () => {
    const r = await verifyFqn(
      fixtures,
      "com.nomagic.uml2.ext.magicdraw.classes.mdprofiles.Stereotype",
    );
    expect(r.exists).toBe(false);
    expect(r.candidates).toContain(
      "com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype",
    );
  });

  it("returns similar names for typos", async () => {
    const r = await verifyFqn(
      fixtures,
      "com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotyp",  // missing 'e'
    );
    expect(r.exists).toBe(false);
    expect(r.similar.length).toBeGreaterThan(0);
    expect(r.similar).toContain("Stereotype");
  });

  it("returns exists=false with empty arrays for a wholly fictional class", async () => {
    const r = await verifyFqn(fixtures, "com.fake.NotARealClass");
    expect(r.exists).toBe(false);
    expect(r.candidates).toEqual([]);
  });
});

describe("verifyFqn (real corpus smoke)", () => {
  const realRoot =
    "E:\\Magic SW\\MCSE26xR1_4_2\\openapi\\docs\\md-javadoc-2026.1.0-485-c01d52da-javadoc";
  const exists = existsSync(realRoot);

  it.skipIf(!exists)(
    "confirms the real Stereotype FQN and rejects the classes.mdprofiles hallucination",
    async () => {
      resetJavadocSearchIndex();
      const ok = await verifyFqn(
        realRoot,
        "com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype",
      );
      expect(ok.exists).toBe(true);

      const bad = await verifyFqn(
        realRoot,
        "com.nomagic.uml2.ext.magicdraw.classes.mdprofiles.Stereotype",
      );
      expect(bad.exists).toBe(false);
      expect(bad.candidates).toContain(
        "com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype",
      );
    },
    30_000,
  );
});

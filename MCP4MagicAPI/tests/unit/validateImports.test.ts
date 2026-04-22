import { describe, it, expect } from "vitest";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  crossCheckImports,
  extractImports,
  validateScript,
} from "../../src/adapters/validate.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const javadocFixture = path.resolve(__dirname, "..", "fixtures", "javadoc-mock");

describe("extractImports", () => {
  it("pulls fully-qualified imports with line numbers; skips wildcards", () => {
    const src = [
      "package com.example",
      "",
      "import com.nomagic.magicdraw.core.Application",
      "import com.nomagic.magicdraw.uml.*", // wildcard -> skipped
      "import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype as Stereo",
      "import static com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper.getProfile",
      "",
      "// not an import line",
    ].join("\n");
    const res = extractImports(src);
    const fqns = res.map((r) => r.fqn);
    expect(fqns).toContain("com.nomagic.magicdraw.core.Application");
    expect(fqns).toContain("com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype");
    expect(fqns).toContain(
      "com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper.getProfile",
    );
    expect(fqns.some((f) => f.endsWith(".*"))).toBe(false);
    const stat = res.find((r) => r.isStatic);
    expect(stat?.fqn).toMatch(/StereotypesHelper\.getProfile/);
  });
});

describe("crossCheckImports (fixture Javadoc)", () => {
  it("flags the classic classes.mdprofiles hallucination with the correct candidate", async () => {
    const src = [
      "import com.nomagic.magicdraw.core.Application", // real
      "import com.nomagic.uml2.ext.magicdraw.classes.mdprofiles.Stereotype", // hallucinated
    ].join("\n");
    const warns = await crossCheckImports(src, javadocFixture);
    expect(warns.length).toBe(1);
    expect(warns[0].severity).toBe("warning");
    expect(warns[0].message).toMatch(/Did you mean: com\.nomagic\.uml2\.ext\.magicdraw\.mdprofiles\.Stereotype/);
    expect(warns[0].line).toBeGreaterThan(0);
  });

  it("does not flag imports outside com.nomagic.* / com.dassault_systemes.*", async () => {
    const src = [
      "import java.util.HashMap",
      "import javax.swing.JPanel",
      "import com.example.Foo",
    ].join("\n");
    const warns = await crossCheckImports(src, javadocFixture);
    expect(warns).toEqual([]);
  });

  it("passes a well-formed Block-builder header with no warnings", async () => {
    const src = [
      "import com.nomagic.magicdraw.core.Application",
      "import com.nomagic.magicdraw.core.Project",
      "import com.nomagic.magicdraw.openapi.uml.SessionManager",
      "import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper",
      "import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype",
    ].join("\n");
    const warns = await crossCheckImports(src, javadocFixture);
    expect(warns).toEqual([]);
  });

  it("static import: cross-check walks up to the owning class FQN", async () => {
    const src =
      "import static com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper.addStereotype";
    const warns = await crossCheckImports(src, javadocFixture);
    expect(warns).toEqual([]); // owner class exists
  });
});

describe("validateScript threads javadocRoot into lintWarnings", () => {
  it("surfaces bad Cameo imports as lintWarnings even when compiler is absent", async () => {
    const code = [
      "import com.nomagic.uml2.ext.magicdraw.classes.mdprofiles.Stereotype",
      "println 'hi'",
    ].join("\n");
    const r = await validateScript({
      language: "groovy",
      code,
      compilerOverride: "/nope/not/real/groovyc",
      javadocRoot: javadocFixture,
    });
    expect(r.compiler).toBeNull(); // ok: we can't compile, but we still lint.
    expect(r.lintWarnings.length).toBeGreaterThan(0);
    expect(
      r.lintWarnings.some((w) => w.message.includes("classes.mdprofiles")),
    ).toBe(true);
  });
});

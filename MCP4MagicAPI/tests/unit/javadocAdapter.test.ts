import { describe, it, expect } from "vitest";
import path from "node:path";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  classFqnToPath,
  getJavadocClass,
  listJavadocPackages,
} from "../../src/adapters/javadoc.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const fixtures = path.resolve(__dirname, "..", "fixtures", "javadoc-mock");

describe("classFqnToPath", () => {
  it("maps com.a.b.Foo to com/a/b/Foo.html under the javadoc root", () => {
    const p = classFqnToPath("/javadoc", "com.nomagic.magicdraw.openapi.uml.SessionManager");
    expect(p.replace(/\\/g, "/")).toBe(
      "/javadoc/com/nomagic/magicdraw/openapi/uml/SessionManager.html",
    );
  });
});

describe("listJavadocPackages (fixtures)", () => {
  it("reads element-list into a sorted package list", async () => {
    const pkgs = await listJavadocPackages(fixtures);
    expect(pkgs.length).toBeGreaterThan(0);
    expect(pkgs).toContain("com.nomagic.magicdraw.openapi.uml");
  });

  it("throws when the javadoc root is missing", async () => {
    await expect(listJavadocPackages(path.join(fixtures, "nope"))).rejects.toThrow(
      /Javadoc root does not exist/,
    );
  });
});

describe("getJavadocClass SessionManager (fixtures)", () => {
  it("extracts class metadata: name, package, kind, description", async () => {
    const klass = await getJavadocClass(
      fixtures,
      "com.nomagic.magicdraw.openapi.uml.SessionManager",
    );
    expect(klass.simpleName).toBe("SessionManager");
    expect(klass.packagePath).toBe("com.nomagic.magicdraw.openapi.uml");
    expect(klass.kind).toBe("class");
    expect(klass.description.toLowerCase()).toContain("singleton");
    expect(klass.modifiers).toMatch(/public class/);
  });

  it("lists core methods with signatures and flags deprecated ones", async () => {
    const klass = await getJavadocClass(
      fixtures,
      "com.nomagic.magicdraw.openapi.uml.SessionManager",
    );
    const names = klass.methods.map((m) => m.name);
    expect(names).toContain("createSession");
    expect(names).toContain("closeSession");
    expect(names).toContain("cancelSession");

    const cancelOverloads = klass.methods.filter((m) => m.name === "cancelSession");
    expect(cancelOverloads.length).toBeGreaterThanOrEqual(2);
    // Parameterless cancelSession() is deprecated; the (Project) overload isn't.
    const deprecated = cancelOverloads.find((m) => m.signature === "cancelSession()");
    const current = cancelOverloads.find((m) => m.signature.includes("Project"));
    expect(deprecated?.deprecated).toBe(true);
    expect(current?.deprecated).toBe(false);
  });

  it("preserves the return type in the returnType column", async () => {
    const klass = await getJavadocClass(
      fixtures,
      "com.nomagic.magicdraw.openapi.uml.SessionManager",
    );
    const createSession = klass.methods.find(
      (m) => m.name === "createSession" && m.signature.includes("Project"),
    );
    expect(createSession).toBeDefined();
    // cancelSession(Project) returns void; createSession(Project, String) returns void too.
    // Either way, returnType should not be empty.
    expect(createSession!.returnType.length).toBeGreaterThan(0);
  });

  it("throws a clear error for an unknown FQN", async () => {
    await expect(
      getJavadocClass(fixtures, "com.fake.NotReal"),
    ).rejects.toThrow(/Javadoc page not found/);
  });
});

describe("javadoc adapter (real corpus smoke)", () => {
  const realRoot =
    "E:\\Magic SW\\MCSE26xR1_4_2\\openapi\\docs\\md-javadoc-2026.1.0-485-c01d52da-javadoc";
  const exists = existsSync(realRoot);

  it.skipIf(!exists)(
    "lists 100+ packages and resolves Finder",
    async () => {
      const pkgs = await listJavadocPackages(realRoot);
      expect(pkgs.length).toBeGreaterThan(100);
      expect(pkgs).toContain("com.nomagic.magicdraw.uml");
      const finder = await getJavadocClass(
        realRoot,
        "com.nomagic.magicdraw.uml.Finder",
      );
      expect(finder.simpleName).toBe("Finder");
      expect(finder.methods.length).toBeGreaterThan(0);
    },
    30_000,
  );
});

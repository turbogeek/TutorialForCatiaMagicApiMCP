import { describe, it, expect } from "vitest";
import {
  lintGroovyForGStrings,
  validateScript,
} from "../../src/adapters/validate.js";

describe("lintGroovyForGStrings", () => {
  it("flags a GString near a Cameo call site", () => {
    const src = `
def name = 'X'
element.setName("Attr_\${name}")
import com.nomagic.magicdraw.core.Application
`;
    const issues = lintGroovyForGStrings(src);
    expect(issues.length).toBeGreaterThan(0);
    expect(issues[0].severity).toBe("warning");
    expect(issues[0].message.toLowerCase()).toMatch(/gstring/);
    expect(issues[0].line).toBeGreaterThan(0);
  });

  it("leaves single-quoted strings alone even when they contain a dollar sign", () => {
    const src = `
import com.nomagic.magicdraw.core.Application
def s = 'has $dollar but single-quoted is a java.lang.String'
`;
    expect(lintGroovyForGStrings(src)).toEqual([]);
  });

  it("does not warn when no Cameo API is referenced anywhere", () => {
    const src = `def n = "plain \${'x'}"`;
    expect(lintGroovyForGStrings(src)).toEqual([]);
  });
});

describe("validateScript (groovy, via groovyc on PATH)", () => {
  it("compiles a trivial valid snippet with ok=true and detects the groovyc version", async () => {
    const r = await validateScript({
      language: "groovy",
      code: "println 'hello'\n",
    });
    if (r.compiler == null) {
      // groovyc not installed — skip.
      return;
    }
    expect(r.ok).toBe(true);
    expect(r.compiler.toLowerCase()).toMatch(/groovy/);
    expect(r.issues.filter((i) => i.severity === "error")).toEqual([]);
  }, 60_000);

  it("reports line-numbered errors for an obvious syntax error", async () => {
    const r = await validateScript({
      language: "groovy",
      code: "def x = {\n",
    });
    if (r.compiler == null) return;
    expect(r.ok).toBe(false);
    const errs = r.issues.filter((i) => i.severity === "error");
    expect(errs.length).toBeGreaterThan(0);
    expect(errs[0].line).toBeGreaterThan(0);
  }, 60_000);

  it("runs the GString lint even when the compiler is absent or file is valid", async () => {
    const r = await validateScript({
      language: "groovy",
      code: `import com.nomagic.magicdraw.core.Application
def name = 'X'
Application.getInstance().getGUILog().showMessage("hi \${name}")
`,
    });
    if (r.compiler == null) return;
    expect(r.lintWarnings.length).toBeGreaterThan(0);
    expect(r.lintWarnings[0].message.toLowerCase()).toMatch(/gstring/);
  }, 60_000);
});

describe("validateScript (java, via javac on PATH)", () => {
  it("compiles a valid snippet with ok=true and detects the javac version", async () => {
    const r = await validateScript({
      language: "java",
      code: `public class Snippet {
  public static void main(String[] args) {
    System.out.println("hello");
  }
}
`,
    });
    if (r.compiler == null) return;
    expect(r.ok).toBe(true);
    expect(r.compiler.toLowerCase()).toMatch(/javac/);
  }, 60_000);

  it("reports line-numbered errors for an obvious syntax error", async () => {
    const r = await validateScript({
      language: "java",
      code: "public class Snippet { int x = ; }\n",
    });
    if (r.compiler == null) return;
    expect(r.ok).toBe(false);
    const errs = r.issues.filter((i) => i.severity === "error");
    expect(errs.length).toBeGreaterThan(0);
    expect(errs[0].line).toBe(1);
  }, 60_000);
});

describe("validateScript (missing compiler)", () => {
  it("returns compiler=null and a clear error when the compiler path does not exist", async () => {
    const r = await validateScript({
      language: "groovy",
      code: "println 'hi'",
      compilerOverride: "/nope/definitely/not/here/groovyc",
    });
    expect(r.compiler).toBeNull();
    expect(r.ok).toBe(false);
    expect(r.issues[0].message.toLowerCase()).toMatch(/groovyc not found/);
  });
});

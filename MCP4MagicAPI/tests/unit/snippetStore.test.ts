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

  it("lists all seeded snippets", async () => {
    const all = await listSnippets(testPaths, {});
    expect(all.length).toBeGreaterThanOrEqual(11);
    const names = all.map((s) => s.name).sort();
    expect(names).toContain("session-wrap");
    expect(names).toContain("gui-log-error");
    expect(names).toContain("finder-by-qname");
    expect(names).toContain("finder-by-type");
    expect(names).toContain("headless-detect");
    expect(names).toContain("association-ends");
    expect(names).toContain("console-logger-class");
    expect(names).toContain("script-load-groovy");
    expect(names).toContain("logger-usage");
    expect(names).toContain("logger-dedicated-file");
    expect(names).toContain("cameo-batch-runner");
  });

  it("logger-dedicated-file snippet clears on construction and writes into a File", async () => {
    const s = await getSnippet(testPaths, { name: "logger-dedicated-file" });
    expect(s.body).toContain("new File(");
    expect(s.body).toContain("LoggerClass.newInstance('MyScript', runLog)");
    expect(s.body.toLowerCase()).toMatch(/cleared/);
  });

  it("cameo-batch-runner snippet uses ProjectCommandLine and returns a byte", async () => {
    const s = await getSnippet(testPaths, { name: "cameo-batch-runner" });
    expect(s.language).toBe("java");
    expect(s.body).toContain("extends ProjectCommandLine");
    expect(s.body).toContain("launch(args)");
    // No CALL to System.exit — the comment warning about it is fine.
    expect(s.body).not.toMatch(/System\.exit\s*\(/);
  });

  it("script-load-groovy uses the Cameo parent classloader", async () => {
    const snippet = await getSnippet(testPaths, { name: "script-load-groovy" });
    expect(snippet.body).toContain("GroovyClassLoader(getClass().getClassLoader())");
    expect(snippet.body).toContain("parseClass");
  });

  it("console-logger-class wires log4j and GUILog and keeps both constructors", async () => {
    const snippet = await getSnippet(testPaths, {
      name: "console-logger-class",
    });
    expect(snippet.body).toContain("import org.apache.log4j.Logger");
    expect(snippet.body).toContain("Application.getInstance().getGUILog()");
    expect(snippet.body).toContain("SysMLv2Logger(Class<?> clazz)");
    expect(snippet.body).toContain("SysMLv2Logger(String name)");
    // info/debug must NOT echo to GUI; warn/error MUST.
    expect(snippet.body).toMatch(/void info[\s\S]*?log\.info/);
    expect(snippet.body).toMatch(/void error[\s\S]*?gl\.log/);
  });

  it("logger-usage always passes Throwable to error() — never swallow", async () => {
    const snippet = await getSnippet(testPaths, { name: "logger-usage" });
    expect(snippet.body).toContain("catch (Throwable t)");
    expect(snippet.body).toContain("logger.error");
    expect(snippet.body).toMatch(/logger\.error\([^)]+,\s*t\)/);
  });

  it("snippets never use GString interpolation (forbidden at Cameo API boundaries)", async () => {
    const all = await listSnippets(testPaths, { language: "groovy" });
    for (const snippet of all) {
      // Flag the classic failure mode: "..${expr}.." or ".. $name .."  inside double-quoted strings.
      // We allow $ inside single-quoted strings (those are plain java.lang.String in Groovy).
      const doubleQuotedWithInterpolation = /"[^"\n]*\$[^"\n]*"/;
      if (doubleQuotedWithInterpolation.test(snippet.body)) {
        throw new Error(
          `Snippet '${snippet.name}' uses a GString (double-quoted with $). ` +
            `Use single quotes + '+' concat or .toString() at the Cameo API boundary.`,
        );
      }
    }
  });

  it("filters by language", async () => {
    const groovy = await listSnippets(testPaths, { language: "groovy" });
    expect(groovy.every((s) => s.language === "groovy")).toBe(true);
    const java = await listSnippets(testPaths, { language: "java" });
    expect(java.every((s) => s.language === "java")).toBe(true);
    expect(java.some((s) => s.name === "cameo-batch-runner")).toBe(true);
    const klingon = await listSnippets(testPaths, { language: "klingon" });
    expect(klingon).toHaveLength(0);
  });

  it("throws a helpful error for an unknown snippet name", async () => {
    await expect(
      getSnippet(testPaths, { name: "does-not-exist" }),
    ).rejects.toThrow(/Unknown snippet/);
  });
});

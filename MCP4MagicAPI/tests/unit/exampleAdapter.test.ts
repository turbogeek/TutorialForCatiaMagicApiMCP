import { describe, it, expect } from "vitest";
import path from "node:path";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  listExamples,
  listExampleFiles,
  readExampleFile,
  parsePluginXml,
} from "../../src/adapters/examples.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const fixtures = path.resolve(
  __dirname,
  "..",
  "fixtures",
  "examples-mock",
);

describe("parsePluginXml", () => {
  it("extracts id, name, and tag hints from element names", () => {
    const xml = `<plugin id="a.b" name="Matrix Demo">
      <action/><mainMenu/><dependencyMatrix/>
    </plugin>`;
    const info = parsePluginXml(xml);
    expect(info.id).toBe("a.b");
    expect(info.name).toBe("Matrix Demo");
    expect(info.tags).toEqual(["action", "matrix", "menu"]);
  });

  it("returns empty tags when no elements match", () => {
    const xml = `<plugin id="x" name="X"><requires/></plugin>`;
    expect(parsePluginXml(xml).tags).toEqual([]);
  });
});

describe("listExamples (fixtures)", () => {
  it("lists every subdirectory and infers tags from plugin.xml + folder name", async () => {
    const all = await listExamples(fixtures);
    const names = all.map((e) => e.name).sort();
    expect(names).toEqual([
      "dependencymatrix_sample",
      "plain",
      "sampleaction",
      "validationrule",
    ]);

    const sampleAction = all.find((e) => e.name === "sampleaction")!;
    expect(sampleAction.pluginId).toBe("com.fake.sampleaction");
    expect(sampleAction.pluginName).toBe("Sample Action Example Plugin");
    expect(sampleAction.tags).toContain("action");
    expect(sampleAction.tags).toContain("menu");
    expect(sampleAction.hasSampleModel).toBe(false);

    const depMatrix = all.find((e) => e.name === "dependencymatrix_sample")!;
    expect(depMatrix.tags).toContain("matrix");
    expect(depMatrix.hasSampleModel).toBe(true);

    const plain = all.find((e) => e.name === "plain")!;
    expect(plain.pluginId).toBeUndefined();
    expect(plain.tags).toEqual([]);
  });

  it("throws a clear error when the examples root is missing", async () => {
    await expect(listExamples(path.join(fixtures, "nope"))).rejects.toThrow(
      /Examples root does not exist/,
    );
  });
});

describe("listExampleFiles (fixtures)", () => {
  it("returns relative paths with forward slashes and language kinds", async () => {
    const files = await listExampleFiles(fixtures, "sampleaction");
    const pairs = files.map((f) => [f.relativePath, f.kind]);
    expect(pairs).toContainEqual(["plugin.xml", "xml"]);
    expect(pairs).toContainEqual([
      "src/com/fake/SampleActionPlugin.java",
      "java",
    ]);
    for (const f of files) {
      expect(f.relativePath).not.toContain("\\");
    }
  });

  it("throws for an unknown example", async () => {
    await expect(listExampleFiles(fixtures, "does-not-exist")).rejects.toThrow(
      /Unknown example/,
    );
  });
});

describe("readExampleFile (fixtures)", () => {
  it("reads a Java file and classifies it as java", async () => {
    const r = await readExampleFile(
      fixtures,
      "sampleaction",
      "src/com/fake/SampleActionPlugin.java",
    );
    expect(r.language).toBe("java");
    expect(r.content).toContain("class SampleActionPlugin");
    expect(r.bytes).toBeGreaterThan(0);
  });

  it("reads the plugin.xml and classifies it as xml", async () => {
    const r = await readExampleFile(fixtures, "sampleaction", "plugin.xml");
    expect(r.language).toBe("xml");
    expect(r.content).toContain("<plugin");
  });

  it("rejects path traversal", async () => {
    await expect(
      readExampleFile(fixtures, "sampleaction", "../plain/src/com/fake/Plain.java"),
    ).rejects.toThrow(/escapes the example root/);
  });
});

describe("listExamples (real corpus smoke test)", () => {
  const realRoot = "E:\\Magic SW\\MCSE26xR1_4_2\\openapi\\examples";
  const exists = existsSync(realRoot);

  it.skipIf(!exists)(
    "sees 50+ example projects including the ones cited in best-practices",
    async () => {
      const all = await listExamples(realRoot);
      expect(all.length).toBeGreaterThanOrEqual(50);
      const names = all.map((e) => e.name);
      expect(names).toContain("commandlineplugin");
      expect(names).toContain("validation");
      expect(names).toContain("activitynode");
    },
  );
});

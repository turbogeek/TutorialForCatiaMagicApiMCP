import { describe, it, expect, beforeEach } from "vitest";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  addProfile,
  getActiveProfile,
  inspectPaths,
  listProfiles,
  removeProfile,
  switchProfile,
} from "../../src/adapters/profile.js";

async function freshRoot(): Promise<string> {
  return fs.mkdtemp(path.join(os.tmpdir(), "mcp-profile-"));
}

const basePathsA = {
  javadocRoot: "C:\\fake\\26xR1\\javadoc",
  guideRoot: "C:\\fake\\26xR1\\guide",
  examplesRoot: "C:\\fake\\26xR1\\examples",
};
const basePathsB = {
  javadocRoot: "C:\\fake\\25x\\javadoc",
  guideRoot: "C:\\fake\\25x\\guide",
  examplesRoot: "C:\\fake\\25x\\examples",
};

describe("profile adapter (round trip)", () => {
  it("returns an empty store when the config file does not exist", async () => {
    const root = await freshRoot();
    const ls = await listProfiles(root);
    expect(ls).toEqual({ active: null, profiles: [] });
    expect(await getActiveProfile(root)).toBeNull();
  });

  it("addProfile persists and first-added becomes active automatically", async () => {
    const root = await freshRoot();
    await addProfile(root, {
      name: "26xR1-sysmlv1",
      apiVersion: "26xR1",
      modelingTypes: ["SysMLv1"],
      paths: basePathsA,
    });
    const ls = await listProfiles(root);
    expect(ls.active).toBe("26xR1-sysmlv1");
    expect(ls.profiles.map((p) => p.name)).toEqual(["26xR1-sysmlv1"]);
    const active = await getActiveProfile(root);
    expect(active?.paths.javadocRoot).toBe(basePathsA.javadocRoot);
  });

  it("adding a second profile does NOT change active unless requested", async () => {
    const root = await freshRoot();
    await addProfile(root, {
      name: "a",
      apiVersion: "26xR1",
      modelingTypes: ["SysMLv1"],
      paths: basePathsA,
    });
    await addProfile(root, {
      name: "b",
      apiVersion: "25x",
      modelingTypes: ["UAF"],
      paths: basePathsB,
    });
    expect((await listProfiles(root)).active).toBe("a");

    // activate:true should switch.
    await addProfile(
      root,
      {
        name: "c",
        apiVersion: "26xR1",
        modelingTypes: ["SysMLv2"],
        paths: basePathsA,
      },
      { activate: true },
    );
    expect((await listProfiles(root)).active).toBe("c");
  });

  it("switchProfile errors on unknown name, succeeds otherwise", async () => {
    const root = await freshRoot();
    await addProfile(root, {
      name: "a",
      apiVersion: "26xR1",
      modelingTypes: ["SysMLv1"],
      paths: basePathsA,
    });
    await expect(switchProfile(root, "nope")).rejects.toThrow(/No profile named/);
    await addProfile(root, {
      name: "b",
      apiVersion: "25x",
      modelingTypes: ["UAF"],
      paths: basePathsB,
    });
    const p = await switchProfile(root, "b");
    expect(p.name).toBe("b");
    expect((await listProfiles(root)).active).toBe("b");
  });

  it("removeProfile unsets active when it was the removed one", async () => {
    const root = await freshRoot();
    await addProfile(root, {
      name: "a",
      apiVersion: "26xR1",
      modelingTypes: ["SysMLv1"],
      paths: basePathsA,
    });
    await addProfile(root, {
      name: "b",
      apiVersion: "25x",
      modelingTypes: ["UAF"],
      paths: basePathsB,
    });
    await switchProfile(root, "b");
    await removeProfile(root, "b");
    const ls = await listProfiles(root);
    expect(ls.profiles.map((p) => p.name)).toEqual(["a"]);
    // The only remaining profile becomes active.
    expect(ls.active).toBe("a");
  });

  it("schema rejects a profile with empty modelingTypes", async () => {
    const root = await freshRoot();
    await expect(
      addProfile(root, {
        name: "x",
        apiVersion: "26xR1",
        modelingTypes: [] as never,
        paths: basePathsA,
      }),
    ).rejects.toBeDefined();
  });
});

describe("inspectPaths", () => {
  it("flags missing directories with a clear reason", async () => {
    const health = await inspectPaths({
      javadocRoot: "C:\\does\\not\\exist\\javadoc",
      guideRoot: "C:\\does\\not\\exist\\guide",
      examplesRoot: "C:\\does\\not\\exist\\examples",
    });
    expect(health.javadocRoot.ok).toBe(false);
    expect(health.javadocRoot.reason).toMatch(/does not exist/);
    expect(health.guideRoot.ok).toBe(false);
    expect(health.examplesRoot.ok).toBe(false);
  });

  it("reports ok=true when markers exist in each corpus", async () => {
    const tmp = await fs.mkdtemp(path.join(os.tmpdir(), "mcp-corpus-"));
    await fs.mkdir(path.join(tmp, "jdoc"));
    await fs.writeFile(path.join(tmp, "jdoc", "element-list"), "com.a\n");
    await fs.mkdir(path.join(tmp, "guide"));
    await fs.writeFile(path.join(tmp, "guide", "index.html"), "<html></html>");
    await fs.mkdir(path.join(tmp, "ex", "one"), { recursive: true });

    const health = await inspectPaths({
      javadocRoot: path.join(tmp, "jdoc"),
      guideRoot: path.join(tmp, "guide"),
      examplesRoot: path.join(tmp, "ex"),
    });
    expect(health.javadocRoot.ok).toBe(true);
    expect(health.guideRoot.ok).toBe(true);
    expect(health.examplesRoot.ok).toBe(true);
  });

  it("flags a javadoc dir that exists but is missing the element-list marker", async () => {
    const tmp = await fs.mkdtemp(path.join(os.tmpdir(), "mcp-corpus-"));
    await fs.mkdir(path.join(tmp, "jdoc"));
    // No element-list marker.
    await fs.mkdir(path.join(tmp, "guide"));
    await fs.writeFile(path.join(tmp, "guide", "index.html"), "<html></html>");
    await fs.mkdir(path.join(tmp, "ex", "one"), { recursive: true });
    const h = await inspectPaths({
      javadocRoot: path.join(tmp, "jdoc"),
      guideRoot: path.join(tmp, "guide"),
      examplesRoot: path.join(tmp, "ex"),
    });
    expect(h.javadocRoot.ok).toBe(false);
    expect(h.javadocRoot.reason).toMatch(/element-list/);
  });
});

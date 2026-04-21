import { describe, it, expect } from "vitest";
import path from "node:path";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  listGuidePages,
  readGuidePage,
  pageIdFromFile,
} from "../../src/adapters/guide.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const fixtures = path.resolve(__dirname, "..", "fixtures", "guide-mock");

describe("pageIdFromFile", () => {
  it("extracts numeric id from '<slug>.<id>.html'", () => {
    expect(pageIdFromFile("Session-management.254437443.html")).toBe(
      "254437443",
    );
  });
  it("falls back to the basename when no dotted id is present", () => {
    expect(pageIdFromFile("some-page.html")).toBe("some-page");
  });
});

describe("listGuidePages (fixtures)", () => {
  it("lists each page with title and labels from exp-* meta tags", async () => {
    const all = await listGuidePages(fixtures);
    expect(all.length).toBe(2);
    const sm = all.find((p) => p.pageId === "254437443");
    expect(sm).toBeDefined();
    expect(sm!.title).toBe("Session management");
    expect(sm!.labels).toContain("create-session");
    expect(sm!.labels).toContain("close-session");
    expect(sm!.fileName).toBe("Session-management.254437443.html");
  });

  it("throws when the guide root is missing", async () => {
    await expect(
      listGuidePages(path.join(fixtures, "nope")),
    ).rejects.toThrow(/Guide root does not exist/);
  });
});

describe("readGuidePage (fixtures)", () => {
  it("reads the Session-management page by filename and extracts body text", async () => {
    const page = await readGuidePage(
      fixtures,
      "Session-management.254437443.html",
    );
    expect(page.pageId).toBe("254437443");
    expect(page.title).toBe("Session management");
    // The body should include the core SessionManager prose.
    expect(page.text).toContain("SessionManager");
    expect(page.text).toContain("createSession");
    // Prose should NOT still contain raw HTML tags.
    expect(page.text).not.toContain("<div");
  });

  it("accepts a bare page id as identifier", async () => {
    const page = await readGuidePage(fixtures, "254437443");
    expect(page.title).toBe("Session management");
  });

  it("extracts at least one code block with language hint", async () => {
    const page = await readGuidePage(fixtures, "254437443");
    expect(page.codeBlocks.length).toBeGreaterThan(0);
    const code = page.codeBlocks[0];
    expect(code.code).toContain("SessionManager");
    // The real page marks these blocks with data-language="java".
    expect(code.language).toBe("java");
  });

  it("collects links from the body (anchors with href + text)", async () => {
    const page = await readGuidePage(fixtures, "254437443");
    expect(page.links.length).toBeGreaterThan(0);
    for (const link of page.links) {
      expect(link.href.length).toBeGreaterThan(0);
      expect(link.text.length).toBeGreaterThan(0);
    }
  });

  it("populates prev/next navigation when present", async () => {
    const page = await readGuidePage(fixtures, "254437443");
    // The real Session-management page has prev=Working-with-UML-model and next=Checking-element-editing-permissions.
    expect(page.prev).toBeDefined();
    expect(page.next).toBeDefined();
    expect(page.next!.title.length).toBeGreaterThan(0);
  });

  it("throws for an unknown page identifier", async () => {
    await expect(readGuidePage(fixtures, "99999999")).rejects.toThrow(
      /No guide page matches/,
    );
  });
});

describe("readGuidePage (real corpus smoke)", () => {
  const realRoot = "E:\\Magic SW\\MCSE26xR1_4_2\\openapi\\guide\\guide";
  const exists = existsSync(realRoot);

  it.skipIf(!exists)(
    "has 100+ pages and can locate Session-management by id",
    async () => {
      const all = await listGuidePages(realRoot);
      expect(all.length).toBeGreaterThan(100);
      const page = await readGuidePage(realRoot, "254437443");
      expect(page.title).toBe("Session management");
    },
    30_000,
  );
});

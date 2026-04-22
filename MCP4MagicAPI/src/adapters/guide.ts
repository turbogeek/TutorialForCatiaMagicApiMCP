/**
 * HTML adapter for the Cameo Developer Guide (Confluence/Scroll export).
 * Uses cheerio to pull <meta name="exp-page-*"> metadata for listings and
 * to extract a clean plain-text body + code blocks + prev/next navigation
 * for a single page. Each page's filename carries its numeric pageId, so
 * we can resolve by either filename or bare id.
 */
import fs from "node:fs/promises";
import path from "node:path";
import * as cheerio from "cheerio";

export interface GuidePageSummary {
  pageId: string;
  title: string;
  fileName: string;
  labels: string[];
}

export interface GuideCodeBlock {
  language: string | null;
  code: string;
}

export interface GuidePageContent extends GuidePageSummary {
  text: string;
  links: Array<{ text: string; href: string }>;
  codeBlocks: GuideCodeBlock[];
  prev?: { fileName: string; title: string };
  next?: { fileName: string; title: string };
}

async function dirExists(p: string): Promise<boolean> {
  try {
    const s = await fs.stat(p);
    return s.isDirectory();
  } catch {
    return false;
  }
}

/**
 * Extract pageId from filename like "Session-management.254437443.html".
 * Falls back to the whole basename (minus extension) if no dotted id is present.
 */
export function pageIdFromFile(fileName: string): string {
  const base = fileName.replace(/\.html$/i, "");
  const m = base.match(/\.(\d+)$/);
  return m ? m[1] : base;
}

function parseHeadMeta($: cheerio.CheerioAPI): {
  title: string;
  labels: string[];
  pageId?: string;
} {
  const get = (name: string): string | undefined =>
    $(`meta[name="${name}"]`).attr("content") ?? undefined;
  const title = get("exp-page-title") ?? $("h1").first().text().trim();
  const labelsRaw = get("exp-page-labels") ?? "";
  const labels = labelsRaw
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  return { title: title.trim(), labels, pageId: get("exp-page-id") };
}

/**
 * List every .html page in the guide directory with title and labels.
 * Reads only enough of each file to get the <head> meta tags — fast even for 197 pages.
 */
export async function listGuidePages(
  guideRoot: string,
): Promise<GuidePageSummary[]> {
  if (!(await dirExists(guideRoot))) {
    throw new Error(
      `Guide root does not exist: ${guideRoot}. Set CAMEO_GUIDE_PATH to override.`,
    );
  }
  const files = await fs.readdir(guideRoot);
  const out: GuidePageSummary[] = [];
  for (const fileName of files) {
    if (!fileName.toLowerCase().endsWith(".html")) continue;
    if (fileName === "index.html") continue;
    const full = path.join(guideRoot, fileName);
    const html = await fs.readFile(full, "utf8");
    const $ = cheerio.load(html);
    const meta = parseHeadMeta($);
    out.push({
      pageId: meta.pageId ?? pageIdFromFile(fileName),
      title: meta.title,
      fileName,
      labels: meta.labels,
    });
  }
  out.sort((a, b) => a.title.localeCompare(b.title));
  return out;
}

/**
 * Read and parse a single guide page. Accepts either the fileName
 * ("Session-management.254437443.html") or the bare pageId ("254437443").
 */
export async function readGuidePage(
  guideRoot: string,
  identifier: string,
): Promise<GuidePageContent> {
  const fileName = await resolveGuideFile(guideRoot, identifier);
  const full = path.join(guideRoot, fileName);
  const html = await fs.readFile(full, "utf8");
  const $ = cheerio.load(html);
  const meta = parseHeadMeta($);

  const main = $("#main-content");
  const bodyRoot = main.length ? main : $("article").first();

  // Remove script/style noise from the body before text extraction.
  bodyRoot.find("script, style, nav, .exp-sidebar-toc").remove();

  // Collect code blocks before text extraction so we can preserve them separately.
  const codeBlocks: GuideCodeBlock[] = [];
  bodyRoot.find(".scroll-code, pre").each((_, el) => {
    const $el = $(el);
    const language = $el.attr("data-language") ?? null;
    // The guide wraps each logical line in <div class="line"><code>...</code></div> fragments.
    const lines: string[] = [];
    const lineDivs = $el.find("div.line");
    if (lineDivs.length) {
      lineDivs.each((__, lineEl) => {
        lines.push($(lineEl).text());
      });
    } else {
      lines.push($el.text());
    }
    const code = lines.join("\n").trim();
    if (code.length > 0) {
      codeBlocks.push({ language, code });
    }
  });

  // Collect anchor hrefs for the agent to follow.
  const links: Array<{ text: string; href: string }> = [];
  bodyRoot.find("a[href]").each((_, el) => {
    const $el = $(el);
    const href = $el.attr("href") ?? "";
    const text = $el.text().trim();
    if (href && text) {
      links.push({ text, href });
    }
  });

  // Flatten body to plain text. Cheerio collapses nested tags; normalize whitespace.
  const rawText = bodyRoot.text().replace(/\s+\n/g, "\n").replace(/[ \t]+/g, " ").trim();

  const prev = parseNav($, "exp-post-navigation-prev");
  const next = parseNav($, "exp-post-navigation-next");

  return {
    pageId: meta.pageId ?? pageIdFromFile(fileName),
    title: meta.title,
    fileName,
    labels: meta.labels,
    text: rawText,
    links,
    codeBlocks,
    prev,
    next,
  };
}

function parseNav(
  $: cheerio.CheerioAPI,
  className: string,
): { fileName: string; title: string } | undefined {
  const nav = $(`nav.${className} a`).first();
  if (!nav.length) return undefined;
  const href = nav.attr("href") ?? "";
  const title = nav.find("span").text().trim() || nav.text().trim();
  if (!href) return undefined;
  return { fileName: href, title };
}

async function resolveGuideFile(
  guideRoot: string,
  identifier: string,
): Promise<string> {
  // Direct filename?
  if (identifier.toLowerCase().endsWith(".html")) {
    const candidate = path.join(guideRoot, identifier);
    try {
      await fs.stat(candidate);
      return identifier;
    } catch {
      throw new Error(`Guide page file not found: ${identifier}`);
    }
  }
  // Bare pageId — scan dir once.
  const files = await fs.readdir(guideRoot);
  const hit = files.find(
    (f) => f.endsWith(`.${identifier}.html`) || pageIdFromFile(f) === identifier,
  );
  if (!hit) {
    throw new Error(`No guide page matches '${identifier}'.`);
  }
  return hit;
}

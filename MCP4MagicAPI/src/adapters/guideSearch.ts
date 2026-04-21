import fs from "node:fs/promises";
import path from "node:path";
import {
  listGuidePages,
  readGuidePage,
  type GuidePageSummary,
} from "./guide.js";

export interface GuideSearchHit {
  pageId: string;
  fileName: string;
  title: string;
  score: number;
  snippet: string;
}

interface IndexEntry {
  pageId: string;
  fileName: string;
  title: string;
  tokenCount: number;
  tokens: Record<string, number>; // token -> count in this page
  text: string; // full plain text, for snippet extraction at query time
}

interface GuideIndex {
  version: 1;
  builtAt: string;
  sources: Array<{ fileName: string; mtimeMs: number; size: number }>;
  entries: IndexEntry[];
}

const STOP_WORDS = new Set([
  "a", "an", "and", "are", "as", "at", "be", "but", "by", "do", "does",
  "for", "from", "has", "have", "in", "is", "it", "its", "of", "on", "or",
  "that", "the", "then", "this", "to", "was", "were", "will", "with",
  "can", "not", "you", "your", "we", "our", "if", "so", "also", "may",
  "e.g", "i.e", "example",
]);

const TOKEN_RE = /[A-Za-z][A-Za-z0-9_]{1,}/g;

/**
 * Split a token at camelCase boundaries: 'SessionManager' -> ['Session','Manager'].
 * Keeps the original intact.
 */
export function splitCamel(token: string): string[] {
  const parts = token.split(/(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])/);
  return parts.length > 1 ? parts : [];
}

export function tokenize(text: string): string[] {
  const matches = text.match(TOKEN_RE) ?? [];
  const out: string[] = [];
  for (const raw of matches) {
    const lower = raw.toLowerCase();
    if (lower.length > 1 && !STOP_WORDS.has(lower)) {
      out.push(lower);
    }
    for (const part of splitCamel(raw)) {
      const pLower = part.toLowerCase();
      if (pLower.length > 1 && !STOP_WORDS.has(pLower) && pLower !== lower) {
        out.push(pLower);
      }
    }
  }
  return out;
}

interface BuildContext {
  guideRoot: string;
  cacheDir: string;
  forceRebuild?: boolean;
}

async function fileSignature(
  guideRoot: string,
): Promise<Array<{ fileName: string; mtimeMs: number; size: number }>> {
  const files = await fs.readdir(guideRoot);
  const sigs: Array<{ fileName: string; mtimeMs: number; size: number }> = [];
  for (const f of files.sort()) {
    if (!f.toLowerCase().endsWith(".html") || f === "index.html") continue;
    const s = await fs.stat(path.join(guideRoot, f));
    sigs.push({ fileName: f, mtimeMs: s.mtimeMs, size: s.size });
  }
  return sigs;
}

function signaturesMatch(
  a: Array<{ fileName: string; mtimeMs: number; size: number }>,
  b: Array<{ fileName: string; mtimeMs: number; size: number }>,
): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    if (
      a[i].fileName !== b[i].fileName ||
      a[i].mtimeMs !== b[i].mtimeMs ||
      a[i].size !== b[i].size
    ) {
      return false;
    }
  }
  return true;
}

async function buildIndex(ctx: BuildContext): Promise<GuideIndex> {
  const summaries: GuidePageSummary[] = await listGuidePages(ctx.guideRoot);
  const entries: IndexEntry[] = [];
  for (const s of summaries) {
    const page = await readGuidePage(ctx.guideRoot, s.fileName);
    // Combine title + body; also include code-block text so API-symbol
    // searches (e.g. "SessionManager") hit code samples too.
    const corpusText = [page.title, page.text, ...page.codeBlocks.map((c) => c.code)].join("\n");
    const toks = tokenize(corpusText);
    const counts: Record<string, number> = {};
    for (const t of toks) {
      counts[t] = (counts[t] ?? 0) + 1;
    }
    entries.push({
      pageId: page.pageId,
      fileName: page.fileName,
      title: page.title,
      tokenCount: toks.length,
      tokens: counts,
      text: page.text,
    });
  }
  const sources = await fileSignature(ctx.guideRoot);
  return {
    version: 1,
    builtAt: new Date().toISOString(),
    sources,
    entries,
  };
}

async function loadCached(cachePath: string): Promise<GuideIndex | null> {
  try {
    const raw = await fs.readFile(cachePath, "utf8");
    const obj = JSON.parse(raw) as GuideIndex;
    if (obj.version !== 1) return null;
    return obj;
  } catch {
    return null;
  }
}

async function saveCache(cachePath: string, index: GuideIndex): Promise<void> {
  await fs.mkdir(path.dirname(cachePath), { recursive: true });
  await fs.writeFile(cachePath, JSON.stringify(index), "utf8");
}

let inMemory: GuideIndex | null = null;

export function resetGuideSearchIndex(): void {
  inMemory = null;
}

export async function getGuideIndex(ctx: BuildContext): Promise<GuideIndex> {
  if (inMemory && !ctx.forceRebuild) return inMemory;
  const cachePath = path.join(ctx.cacheDir, "guide-index.json");
  if (!ctx.forceRebuild) {
    const cached = await loadCached(cachePath);
    if (cached) {
      const currentSigs = await fileSignature(ctx.guideRoot);
      if (signaturesMatch(cached.sources, currentSigs)) {
        inMemory = cached;
        return cached;
      }
    }
  }
  const fresh = await buildIndex(ctx);
  await saveCache(cachePath, fresh);
  inMemory = fresh;
  return fresh;
}

/**
 * TF-IDF ranked search with title and exact-phrase boosts.
 * Scoring:
 *   - Per token: tf * log(N / df) summed across query tokens
 *   - +2 * queryHits if the raw query string appears (case-insensitive) in title
 *   - +0.5 * queryHits if the raw query string appears in the body
 */
export interface SearchOptions {
  limit?: number;
}

export async function searchGuide(
  ctx: BuildContext,
  rawQuery: string,
  opts: SearchOptions = {},
): Promise<GuideSearchHit[]> {
  if (!rawQuery || rawQuery.trim().length === 0) {
    throw new Error("guide search query must be a non-empty string");
  }
  const index = await getGuideIndex(ctx);
  const queryTokens = tokenize(rawQuery);
  if (queryTokens.length === 0) {
    return [];
  }

  const N = index.entries.length;
  const df: Record<string, number> = {};
  for (const t of queryTokens) {
    df[t] = 0;
    for (const e of index.entries) {
      if (e.tokens[t]) df[t]++;
    }
  }

  const rawLower = rawQuery.toLowerCase();
  const querySet = new Set(queryTokens);

  const scored: GuideSearchHit[] = [];
  for (const e of index.entries) {
    let score = 0;
    for (const t of queryTokens) {
      const tf = e.tokens[t] ?? 0;
      if (tf === 0 || df[t] === 0) continue;
      const idf = Math.log((N + 1) / df[t]);
      score += tf * idf;
    }

    // Title-token overlap: any query token appearing as a title token wins
    // decisively over TF-IDF-only matches. This is what makes "SessionManager"
    // rank the "Session management" page ahead of pages that merely mention
    // the class many times in body text.
    const titleTokens = tokenize(e.title);
    const titleTokSet = new Set(titleTokens);
    let titleHits = 0;
    for (const qt of querySet) {
      if (titleTokSet.has(qt)) titleHits++;
    }
    if (titleHits > 0) {
      // Flat per-hit + proportional coverage bonus. A page whose title tokens
      // are fully covered by the query receives the largest kick.
      const coverage = titleHits / Math.max(titleTokens.length, 1);
      score += 50 * titleHits + 200 * coverage;
    }

    if (score === 0 && !e.title.toLowerCase().includes(rawLower)) continue;

    if (e.title.toLowerCase().includes(rawLower)) score += 10;
    if (e.text.toLowerCase().includes(rawLower)) score += 2;

    scored.push({
      pageId: e.pageId,
      fileName: e.fileName,
      title: e.title,
      score,
      snippet: buildSnippet(e.text, rawLower),
    });
  }
  scored.sort((a, b) => b.score - a.score);
  const limit = opts.limit ?? 10;
  return scored.slice(0, limit);
}

function buildSnippet(text: string, rawLower: string): string {
  const idx = text.toLowerCase().indexOf(rawLower);
  if (idx < 0) {
    return text.slice(0, 200).trim() + (text.length > 200 ? "…" : "");
  }
  const start = Math.max(0, idx - 80);
  const end = Math.min(text.length, idx + rawLower.length + 120);
  const prefix = start > 0 ? "…" : "";
  const suffix = end < text.length ? "…" : "";
  return prefix + text.slice(start, end).trim() + suffix;
}

import fs from "node:fs/promises";
import path from "node:path";

export interface JavadocSearchHit {
  kind: "class" | "method" | "field" | "package";
  fqn: string;
  simpleName: string;
  packagePath: string;
  owner?: string; // for methods/fields: the owning class
  signature?: string; // for methods: "createSession(Project, String)"
  score: number;
}

interface TypeEntry {
  p: string; // package
  l: string; // simple class name (may contain nested class syntax like "Outer.Inner")
}
interface MemberEntry {
  p: string; // package
  c: string; // class
  l: string; // member signature, e.g. "createSession(Project, String)"
  u?: string; // optional URL fragment
}
interface PackageEntry {
  l: string; // package name or sentinel "All Packages"
}

interface IndexCache {
  types: TypeEntry[];
  members: MemberEntry[];
  packages: PackageEntry[];
}

let cached: IndexCache | null = null;
let cachedRoot: string | null = null;

export function resetJavadocSearchIndex(): void {
  cached = null;
  cachedRoot = null;
}

async function readSearchFile<T>(file: string, prefix: string): Promise<T[]> {
  const raw = await fs.readFile(file, "utf8");
  // The files end with trailing calls like `;updateSearchResults();`, so we
  // extract just the bracketed JSON array rather than slicing by '='.
  const start = raw.indexOf("[");
  const end = raw.lastIndexOf("]");
  if (start < 0 || end < 0 || end < start) {
    throw new Error(`Unexpected Javadoc index format: ${file}`);
  }
  const json = raw.slice(start, end + 1);
  const arr = JSON.parse(json) as T[];
  if (!Array.isArray(arr)) {
    throw new Error(`Javadoc index ${prefix} is not a JSON array`);
  }
  return arr;
}

async function loadIndex(javadocRoot: string): Promise<IndexCache> {
  if (cached && cachedRoot === javadocRoot) return cached;
  const [types, members, packages] = await Promise.all([
    readSearchFile<TypeEntry>(
      path.join(javadocRoot, "type-search-index.js"),
      "typeSearchIndex",
    ),
    readSearchFile<MemberEntry>(
      path.join(javadocRoot, "member-search-index.js"),
      "memberSearchIndex",
    ),
    readSearchFile<PackageEntry>(
      path.join(javadocRoot, "package-search-index.js"),
      "packageSearchIndex",
    ),
  ]);
  cached = { types, members, packages };
  cachedRoot = javadocRoot;
  return cached;
}

/**
 * Score a needle against a target string:
 *   - exact match (case-insensitive):             1000
 *   - target starts with needle:                   500
 *   - target contains needle as a word boundary:   200
 *   - target contains needle anywhere:             100
 *   - no match:                                    0
 * All case-insensitive.
 */
function scoreMatch(target: string, needle: string): number {
  const t = target.toLowerCase();
  const n = needle.toLowerCase();
  if (t === n) return 1000;
  if (t.startsWith(n)) return 500;
  const wordBoundaryRe = new RegExp(`(^|[^a-z0-9])${escapeRegex(n)}`, "i");
  if (wordBoundaryRe.test(target)) return 200;
  if (t.includes(n)) return 100;
  return 0;
}

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

export interface JavadocSearchOptions {
  kind?: "class" | "method" | "field" | "package" | "all";
  limit?: number;
}

/**
 * Search the three shipped indexes and merge the results.
 * Members are inferred as methods (contains '(') vs. fields otherwise.
 */
export async function searchJavadoc(
  javadocRoot: string,
  query: string,
  opts: JavadocSearchOptions = {},
): Promise<JavadocSearchHit[]> {
  if (!query || query.trim().length === 0) {
    throw new Error("javadoc search query must be a non-empty string");
  }
  const kind = opts.kind ?? "all";
  const limit = opts.limit ?? 20;
  const index = await loadIndex(javadocRoot);
  const out: JavadocSearchHit[] = [];

  if (kind === "class" || kind === "all") {
    for (const t of index.types) {
      const score = scoreMatch(t.l, query);
      if (score > 0) {
        out.push({
          kind: "class",
          fqn: `${t.p}.${t.l}`,
          simpleName: t.l,
          packagePath: t.p,
          score,
        });
      }
    }
  }

  if (kind === "method" || kind === "field" || kind === "all") {
    for (const m of index.members) {
      const score = scoreMatch(m.l, query);
      if (score > 0) {
        const isMethod = m.l.includes("(");
        const memberKind: JavadocSearchHit["kind"] = isMethod ? "method" : "field";
        if (kind !== "all" && kind !== memberKind) continue;
        out.push({
          kind: memberKind,
          fqn: `${m.p}.${m.c}#${m.l}`,
          simpleName: m.l.replace(/\(.*$/, ""),
          packagePath: m.p,
          owner: `${m.p}.${m.c}`,
          signature: isMethod ? m.l : undefined,
          score,
        });
      }
    }
  }

  if (kind === "package" || kind === "all") {
    for (const p of index.packages) {
      if (p.l === "All Packages") continue;
      const score = scoreMatch(p.l, query);
      if (score > 0) {
        out.push({
          kind: "package",
          fqn: p.l,
          simpleName: p.l.split(".").pop() ?? p.l,
          packagePath: p.l,
          score,
        });
      }
    }
  }

  out.sort((a, b) => {
    if (b.score !== a.score) return b.score - a.score;
    // Stable tie-break by kind priority then name length.
    const kindRank: Record<JavadocSearchHit["kind"], number> = {
      class: 0,
      package: 1,
      method: 2,
      field: 3,
    };
    const kr = kindRank[a.kind] - kindRank[b.kind];
    if (kr !== 0) return kr;
    return a.simpleName.length - b.simpleName.length;
  });

  return out.slice(0, limit);
}

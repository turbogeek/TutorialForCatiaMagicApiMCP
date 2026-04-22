/**
 * Fast Javadoc search + deterministic FQN verification.
 *
 * Reuses the three pre-built JSON-ish indexes that the Javadoc tool ships
 * (type-search-index.js, member-search-index.js, package-search-index.js)
 * — the same files the doc-site's search box consumes. Much faster than
 * crawling HTML.
 *
 * Ranking adds two adjustments on top of the raw match score:
 *   packagePriority()  — demotes ecore / impl / emfuml2xmi / jmi.reflect
 *                        / importexport / persistence / reportwizard /
 *                        automaton / eclipseocl / xmi (plumbing);
 *                        boosts helpers / openapi / ext.magicdraw /
 *                        sysml / uaf / kerml.
 *   classNamePriority() — rewards Helper / Factory / Finder / Manager
 *                        suffixes (what scripters want).
 *
 * verifyFqn(fqn) is the deterministic guardrail against hallucinated
 * package paths: returns {exists, candidates, similar} so callers can
 * offer "Did you mean..." corrections automatically.
 */
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
 * Ranking adjustment per package path. Positive = boost, negative = demote.
 * The raw score from scoreMatch() is added to this, so a 100-point difference
 * is very significant (substring -> start-of-name etc.).
 *
 * Rationale: the Javadoc exposes a lot of "plumbing" — ecore wrappers, EMF
 * exporters, persistence impl, the JMI reflection layer — which are rarely
 * what a scripter wants. Helpers and factories are almost always what they
 * want. Bias the search accordingly.
 */
export function packagePriority(pkg: string): number {
  let adj = 0;

  // Demote plumbing / SPI / impl.
  if (/\.ecore(\.|$)/.test(pkg)) adj -= 400;
  if (/\.impl(\.|$)/.test(pkg)) adj -= 400;
  if (/\.emfuml2xmi/.test(pkg)) adj -= 350;
  if (/\.jmi\.reflect/.test(pkg)) adj -= 300;
  if (/\.jmi\.streaming/.test(pkg)) adj -= 300;
  if (/\.ext\.jmi\.(data|event)/.test(pkg)) adj -= 200;
  if (/\.importexport(\.|$)/.test(pkg)) adj -= 200;
  if (/\.persistence(\.|$)/.test(pkg)) adj -= 200;
  if (/\.reportwizard(\.|$)/.test(pkg)) adj -= 150;
  if (/\.magicreport(\.|$)/.test(pkg)) adj -= 150;
  if (/\.automaton(\.|$)/.test(pkg)) adj -= 150;
  if (/\.eclipseocl(\.|$)/.test(pkg)) adj -= 150;
  if (/\.htmlbuilder(\.|$)/.test(pkg)) adj -= 150;
  if (/\.xmi\b/.test(pkg)) adj -= 150;

  // Boost helper and factory packages.
  if (/\.helpers(\.|$)/.test(pkg)) adj += 200;
  if (/\.openapi(\.|$)/.test(pkg)) adj += 150;

  // Boost the main UML2 + SysML / KerML metamodel surfaces.
  if (/\.uml2\.ext\.magicdraw(\.|$)/.test(pkg)) adj += 100;
  if (/\.magicdraw\.sysml(\.|$)/.test(pkg)) adj += 100;
  if (/\.magicdraw\.uaf(\.|$)/.test(pkg)) adj += 50;
  if (/\.modeler\.sysml\.model(\.|$)/.test(pkg)) adj += 100;
  if (/\.modeler\.kerml\.model(\.|$)/.test(pkg)) adj += 100;

  return adj;
}

/**
 * Class-level priority layered on top of packagePriority: boost classes whose
 * names end in 'Helper' or 'Factory' (what scripters almost always want).
 */
export function classNamePriority(simpleName: string): number {
  if (/Helper$/.test(simpleName)) return 150;
  if (/Factory$/.test(simpleName)) return 120;
  if (/Finder$/.test(simpleName)) return 100;
  if (/Manager$/.test(simpleName)) return 80;
  return 0;
}

export interface VerifyResult {
  fqn: string;
  exists: boolean;
  /** When exists=false, up to 5 candidate FQNs that share the simple name. */
  candidates: string[];
  /** When exists=false, up to 5 class names close to the given simple name. */
  similar: string[];
}

/**
 * Verify whether a fully-qualified class name exists in the Javadoc. Returns
 * the boolean plus candidates and similar classes so the caller can suggest
 * a replacement. This is the cheap guardrail: agents MUST call this for any
 * com.nomagic.* / com.dassault_systemes.* import they intend to emit.
 */
export async function verifyFqn(
  javadocRoot: string,
  fqn: string,
): Promise<VerifyResult> {
  const parts = fqn.split(".");
  const simple = parts.pop();
  if (!simple) {
    return { fqn, exists: false, candidates: [], similar: [] };
  }
  const index = await loadIndex(javadocRoot);
  const exact = index.types.filter((t) => t.l === simple);
  const exists = exact.some((t) => `${t.p}.${t.l}` === fqn);
  const candidates = exact
    .map((t) => `${t.p}.${t.l}`)
    .filter((f) => f !== fqn)
    .slice(0, 5);
  // Similar simple-name matches (startsWith / contains) to catch typos.
  const lowerSimple = simple.toLowerCase();
  const similar = Array.from(
    new Set(
      index.types
        .map((t) => t.l)
        .filter((l) => {
          const ll = l.toLowerCase();
          return (
            l !== simple &&
            (ll === lowerSimple ||
              ll.startsWith(lowerSimple) ||
              lowerSimple.startsWith(ll) ||
              ll.includes(lowerSimple))
          );
        }),
    ),
  )
    .sort((a, b) => a.length - b.length)
    .slice(0, 5);
  return { fqn, exists, candidates, similar };
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
      const base = scoreMatch(t.l, query);
      if (base <= 0) continue;
      const score = base + packagePriority(t.p) + classNamePriority(t.l);
      out.push({
        kind: "class",
        fqn: `${t.p}.${t.l}`,
        simpleName: t.l,
        packagePath: t.p,
        score,
      });
    }
  }

  if (kind === "method" || kind === "field" || kind === "all") {
    for (const m of index.members) {
      const base = scoreMatch(m.l, query);
      if (base <= 0) continue;
      const isMethod = m.l.includes("(");
      const memberKind: JavadocSearchHit["kind"] = isMethod ? "method" : "field";
      if (kind !== "all" && kind !== memberKind) continue;
      const score = base + packagePriority(m.p) + classNamePriority(m.c);
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

  if (kind === "package" || kind === "all") {
    for (const p of index.packages) {
      if (p.l === "All Packages") continue;
      const base = scoreMatch(p.l, query);
      if (base <= 0) continue;
      const score = base + packagePriority(p.l);
      out.push({
        kind: "package",
        fqn: p.l,
        simpleName: p.l.split(".").pop() ?? p.l,
        packagePath: p.l,
        score,
      });
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

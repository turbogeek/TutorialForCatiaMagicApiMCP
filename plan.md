# Plan: MCP4MagicAPI — MCP Server + Agent for Cameo/MagicDraw Open API Scripting

> **Status:** Draft for user review — no code will be written until you approve or edit this plan.

## 1. Goals

Build an MCP (Model Context Protocol) server, paired with a Claude Code subagent, that helps a developer author **Java, Groovy, and JavaScript** scripts against the Cameo/MagicDraw Open API (version 26xR1). The MCP surfaces the three existing reference corpora as callable tools; the agent encodes the domain conventions (sessions, error reporting, headless detection, etc.) that the raw docs don't spell out in one place.

**Reference corpora** (all local, read-only):

| Corpus | Path | Size |
|---|---|---|
| Javadoc (HTML) | `E:\Magic SW\MCSE26xR1_4_2\openapi\docs\md-javadoc-2026.1.0-485-c01d52da-javadoc\` | ~thousands of class pages |
| Developer Guide (HTML) | `E:\Magic SW\MCSE26xR1_4_2\openapi\guide\guide\` | 197 HTML pages |
| Examples (Java source + `plugin.xml` + sample `.mdzip`) | `E:\Magic SW\MCSE26xR1_4_2\openapi\examples\` | 57 example projects |

## 2. Non-goals (for v1)

- No modification of the reference corpora.
- No direct execution of scripts inside MagicDraw from the MCP — that remains the user's existing REST test harness (per `CLAUDE.md`).
- No network calls, no telemetry, no writes outside the project directory.
- No attempt to replace Context7 / Grounded Docs MCP — this is a **local, offline, vendored** complement.

## 3. Architecture

```
┌────────────────────────────────────────────────────────────┐
│  Claude Code (host)                                        │
│  └── Subagent: "cameo-api-scripter" (.claude/agents/*.md)  │
└───────────────────────┬────────────────────────────────────┘
                        │ stdio / JSON-RPC
                        ▼
┌────────────────────────────────────────────────────────────┐
│  MCP4MagicAPI server (Node.js + TypeScript)                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Tools (stateless; lazy-loaded indexes)              │  │
│  │  • javadoc_search          • guide_search           │  │
│  │  • javadoc_get_class       • guide_read_page        │  │
│  │  • javadoc_list_packages   • guide_list_pages       │  │
│  │  • example_search          • example_read_file      │  │
│  │  • example_list            • example_list_files     │  │
│  │  • best_practice_lookup    • snippet_get            │  │
│  │  • validate_groovy_syntax  (stretch goal)           │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Adapters (pure functions, unit-testable)            │  │
│  │  • javadocAdapter (HTML → {class, methods, sig})    │  │
│  │  • guideAdapter (HTML → {title, text, sections})    │  │
│  │  • exampleAdapter (filesystem walker, tagger)       │  │
│  │  • snippetStore (embedded JSON)                     │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
```

**Design decisions:**

- **Language:** TypeScript + Node 16+ (matches what's already scaffolded in `MCP4MagicAPI/`). Transport: stdio.
- **SDK:** `@modelcontextprotocol/sdk` (already in `package.json` intent), validated with `zod`.
- **HTML parsing:** `cheerio` (lightweight, jQuery-like, no headless browser). Avoids a heavy dependency like `jsdom` and parses the Javadoc's semantic HTML cleanly.
- **Indexing:** Build a small in-memory index on first call (lazy). Persisted to `MCP4MagicAPI/.cache/` as JSON on first build so subsequent starts are fast. Cache invalidated by reference path + file mtime.
- **No global state beyond the cache.** Tools are pure functions over the cache.
- **Paths are configurable** via env vars (`CAMEO_JAVADOC_PATH`, `CAMEO_GUIDE_PATH`, `CAMEO_EXAMPLES_PATH`) with sensible defaults matching your current install.

## 4. Tool catalog (detailed)

| Tool | Inputs | Output | Purpose |
|---|---|---|---|
| `javadoc_search` | `query: string`, `kind?: "class"\|"method"\|"field"`, `limit?: number` | `{matches: [{fqn, kind, signature, packagePath}]}` | Fuzzy search across class/method names. |
| `javadoc_get_class` | `fqn: string` (e.g. `com.nomagic.magicdraw.openapi.uml.SessionManager`) | `{fqn, summary, methods: [...], fields: [...], inheritedFrom: [...]}` | Return structured Javadoc for one class. |
| `javadoc_list_packages` | — | `[packageFqn]` | List all packages so the agent can navigate. |
| `guide_search` | `query: string`, `limit?: number` | `{matches: [{pageId, title, snippet}]}` | Full-text search across the 197 guide pages. |
| `guide_read_page` | `pageId: string` | `{title, text, links: [...]}` | Return guide page as clean text + extracted links. |
| `guide_list_pages` | `topicFilter?: string` | `[{pageId, title}]` | Browse guide by topic keywords. |
| `example_list` | `topicFilter?: string` | `[{name, path, summary, tags}]` | List of the 57 example projects with inferred tags. |
| `example_search` | `query: string` | `{matches: [{example, file, lineContext}]}` | Search inside example source code. |
| `example_read_file` | `example: string`, `relativePath: string` | `{content, language}` | Return a specific example file. |
| `example_list_files` | `example: string` | `[{relativePath, kind}]` | Tree of an example project. |
| `best_practice_lookup` | `topic: "session"\|"error-reporting"\|"finder"\|"selection"\|"headless"\|"thread"\|"undo"\|...` | `{topic, description, doAndDont, snippet}` | Curated, vetted best-practice card. Populated from `data/best-practices.json`. |
| `snippet_get` | `name: string` | `{name, language, body, sourceExamples}` | Reusable code templates (e.g. `session-wrap`, `gui-log-error`, `finder-by-name`). |
| `validate_groovy_syntax` *(stretch)* | `code: string` | `{ok: boolean, errors: [...]}` | Parse-only check via embedded Groovy. Behind a feature flag — may defer to v2. |

## 5. Agent: `cameo-api-scripter`

Location: `.claude/agents/cameo-api-scripter.md` at the project root.

**Responsibilities encoded in the agent definition:**

1. **Sessions** — wrap every model-mutating operation in `SessionManager.getInstance().createSession(project, "description")` / `closeSession(project)` inside `try/finally`. Never leave a session open on exception; always `cancelSession` on error.
2. **Error reporting** — use `Application.getInstance().getGUILog().log(msg)` / `.showError(msg)` for user-visible messages; use the project's logger (`MDLog.getPluginsLog()`, etc.) for background detail; always also write to a diagnostic file under `logs/` so Claude can read it (per `CLAUDE.md`).
3. **Headless detection** — detect `Application.getInstance().getProject() == null` or use the REST-harness-launched flag; if headless, skip GUI code paths and operate on test data.
4. **No `System.exit`** — per `CLAUDE.md`, this kills MagicDraw and loses user data. Use `dispose()` / `setVisible(false)` on JFrames, return from scripts, clean up listeners.
5. **Finder over custom traversal** — prefer `Finder.byQualifiedName()`, `Finder.byTypeRecursively()` over hand-rolled owner-walking.
6. **Collections are live** — many API getters return live collections; iterate defensively, copy before mutating.
7. **Multiplicity awareness** — check `respondsTo` / `instanceof` before casting; consult multiplicity of associations (`getEnd()[0]` vs `[1]`) rather than assuming.
8. **REST test harness** — always launch via REST if available, send a clean-shutdown REST command before re-launching to avoid stale class caches.
9. **Validation** — run the MCP's `validate_groovy_syntax` tool (when available) before handing code back; use `javadoc_get_class` to confirm any method signature you're unsure about.
10. **TDD flow** — for each change: commit current state → write/update test → make change → run unit + integration tests → run REST harness test → analyze diagnostic logs → commit.

The agent will also carry a short "common pitfalls" list (class cache staleness, listener leaks, EDT vs. background thread, transaction-required reads).

## 6. Testing strategy (TDD Flow, per your `CLAUDE.md`)

**Unit tests** (`vitest`):
- `tests/unit/javadocAdapter.test.ts` — fixture: 2–3 canned HTML pages from the real corpus; assert extraction shape.
- `tests/unit/guideAdapter.test.ts` — same pattern, fixtures from 2–3 real guide pages.
- `tests/unit/exampleAdapter.test.ts` — walk a temp mock fs tree, assert listing/tagging.
- `tests/unit/snippetStore.test.ts` — JSON round-trip + schema validation.

**Integration tests** (`vitest` + real MCP client):
- `tests/integration/server.test.ts` — spawn the built server over stdio, call every tool with realistic inputs, assert response conforms to the declared `zod` schema.
- Covers: cache cold-start, cache warm-start, invalid-input rejection, missing-file behavior.

**Test harness discipline:**
- `npm test` runs unit + integration.
- CI-style pre-commit: tests must pass before each commit (per `CLAUDE.md` "run tests after each change session").
- Diagnostic logs written to `MCP4MagicAPI/logs/test-run-{timestamp}.log` so Claude can read failure details on the next loop.
- Max 10 code-change cycles on red tests before escalating to the user.

**Fixtures:** small, vendored copies of real HTML pages go into `tests/fixtures/` — we do **not** depend on the full corpus being present in test runs.

## 7. File layout (final)

```
TutorialForCatiaMagicApiMCP/
├── plan.md                              ← this file
├── README.md                            ← updated with tutorial + features
├── .claude/
│   └── agents/
│       └── cameo-api-scripter.md        ← the subagent definition
└── MCP4MagicAPI/
    ├── package.json
    ├── tsconfig.json
    ├── vitest.config.ts
    ├── src/
    │   ├── index.ts                     ← server entry (stdio transport)
    │   ├── config.ts                    ← env-var & default paths
    │   ├── cache.ts                     ← lazy disk cache
    │   ├── adapters/
    │   │   ├── javadoc.ts
    │   │   ├── guide.ts
    │   │   └── examples.ts
    │   ├── tools/
    │   │   ├── javadoc.ts               ← 3 javadoc tools
    │   │   ├── guide.ts                 ← 3 guide tools
    │   │   ├── examples.ts              ← 4 example tools
    │   │   ├── bestPractices.ts
    │   │   └── snippets.ts
    │   └── data/
    │       ├── best-practices.json
    │       └── snippets.json
    ├── tests/
    │   ├── fixtures/
    │   │   ├── javadoc/*.html
    │   │   └── guide/*.html
    │   ├── unit/*.test.ts
    │   └── integration/server.test.ts
    └── logs/                            ← runtime diagnostics (gitignored)
```

## 8. Build order (TDD iterations)

Each iteration ends with green tests + a commit.

1. **Scaffold:** wire up `vitest`, add `dev`/`test`/`build` scripts, stub one tool (`snippet_get`) end-to-end with tests. **Proves the plumbing.**
2. **`snippets` + `best_practice_lookup`:** JSON-backed, simple. Ship a small starter set (5–6 of each), expand later.
3. **Example adapter + `example_list` / `example_list_files` / `example_read_file`:** pure filesystem, no HTML. Lowest risk.
4. **`example_search`:** ripgrep-style over example source; implemented with Node streams, no external tool.
5. **Guide adapter + `guide_list_pages` / `guide_read_page`:** cheerio extraction with fixtures.
6. **`guide_search`:** in-memory inverted index built from the 197 pages.
7. **Javadoc adapter + `javadoc_get_class` / `javadoc_list_packages`:** cheerio extraction; the HTML is more complex, so this is later.
8. **`javadoc_search`:** scans the prebuilt type-search index JS that the Javadoc ships (`type-search-index.js`) — much faster than walking HTML.
9. **Agent definition** (`.claude/agents/cameo-api-scripter.md`) — written as we learn which tools the agent actually needs to call to answer questions well.
10. **README updates** after each iteration — running tutorial + feature list.
11. *(Stretch)* `validate_groovy_syntax` behind a flag.

Each step: write test → implement → green → commit with a clear message → update README.

## 9. Open questions for the user

Please confirm or amend:

1. **Node version target** — your project mentions Node 16+. OK to require Node 20 LTS instead? (simpler `fetch`, newer `test` features). *Default: stay on 16+ for minimum friction.*
2. **Test runner** — `vitest` (fast, TS-native) vs. `jest` with `ts-jest`. *Default: vitest.*
3. **`validate_groovy_syntax`** — include in v1, or defer to v2? Embedding Groovy adds ~40 MB. *Default: defer.*
4. **Groovy/JavaScript focus** — examples are Java source; do you want the agent to default to Groovy (matches your `CLAUDE.md`), Java, or let the user choose per invocation? *Default: Groovy, with Java/JS available.*
5. **Where should the best-practice content come from?** I have a draft list from your `CLAUDE.md` and from the guide TOC. Do you want to contribute any specific "gotcha" items you've hit personally? *(Your-contribution opportunity — see §10.)*

## 10. Your contribution (learning-mode placeholder)

Once this plan is approved, there's one place where **your direct input will shape the tool quality more than code review can**: the `data/best-practices.json` seed content.

I'll prep the file with a schema and 2–3 entries drawn from your `CLAUDE.md` (sessions, `System.exit`, stale class cache). Then I'd like **you to add 2–3 of your own hard-won tips** — 5–10 lines each, in this shape:

```json
{
  "topic": "…",
  "summary": "one sentence",
  "do": ["…"],
  "dont": ["…"],
  "snippet": "…",
  "source": "personal experience | guide:<pageId> | example:<name>"
}
```

This is where your domain knowledge makes the agent meaningfully better than what I can infer from the docs alone. Placeholder location: `MCP4MagicAPI/src/data/best-practices.json`.

---

## Approval

**Review checklist:**

- [ ] Tool catalog matches what you want the agent to have access to
- [ ] Build order is acceptable (incremental TDD, not big-bang)
- [ ] Testing strategy is rigorous enough
- [ ] Agent best-practice list in §5 captures your key conventions
- [ ] Open questions in §9 are answered or deferred
- [ ] File layout in §7 is OK

When you're happy, reply with your answers to §9 and any edits to the plan, and I'll start iteration 1.

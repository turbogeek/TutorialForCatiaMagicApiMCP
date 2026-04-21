# TutorialForCatiaMagicApiMCP

A tutorial project that builds — and then uses — an **MCP (Model Context Protocol) server** purpose-built for authoring scripts against the **Cameo / MagicDraw / CATIA Magic Open API** (version 26xR1). The paired Claude Code subagent, `cameo-api-scripter`, knows the conventions (sessions, error channels, GString pitfalls, etc.) that the raw docs don't spell out in one place.

> ### WARNING
> This is based on instructions as of 2026-04-21 and subject to change.
>
> ### NOTICES
> This is a tutorial and is **not** supported by Dassault Systèmes. For educational purposes only — no support, guarantees, warranties, or assumption of liabilities for the content or your use of it.

---

## What you get

- **13 MCP tools** over three local corpora of the Cameo Open API:
  - `javadoc_search`, `javadoc_get_class`, `javadoc_list_packages`
  - `guide_search`, `guide_read_page`, `guide_list_pages`
  - `example_list`, `example_list_files`, `example_read_file`, `example_search`
  - `best_practice_lookup`, `best_practice_list`
  - `snippet_get`, `snippet_list`
  - `validate_script_syntax` (Groovy + Java, via `groovyc` / `javac`)
- A curated **snippet library** covering sessions, logging, script loading, Finder idioms, association ends, and more — all Groovy, all vetted for Cameo API compatibility.
- A **best-practices catalog** (11 topics) capturing what to do *and* what to avoid: session discipline, three-channel error reporting, the **no-GString-at-API-boundary** rule, no `System.exit`, headless detection, REST harness hygiene, and more.
- A **subagent** (`.claude/agents/cameo-api-scripter.md`) that wires these tools into a scripting protocol so you get consistent, validated output.

## Repository layout

```
TutorialForCatiaMagicApiMCP/
├── README.md                              ← this file
├── plan.md                                ← original build plan
├── .claude/
│   └── agents/
│       └── cameo-api-scripter.md          ← the subagent
├── scripts/
│   ├── SysMLv2Logger.groovy               ← reference logger class
│   └── RequirementSatisfyMatrixGraphics.groovy
└── MCP4MagicAPI/                          ← the MCP server (Node/TS)
    ├── src/
    │   ├── index.ts                       ← tool registration + stdio entry
    │   ├── config.ts                      ← env-var paths
    │   ├── adapters/                      ← javadoc/guide/examples/validate
    │   ├── tools/                         ← one file per MCP tool
    │   └── data/                          ← snippets.json, best-practices.json
    ├── tests/
    │   ├── fixtures/                      ← vendored real pages for tests
    │   ├── unit/
    │   └── integration/
    └── package.json
```

## Prerequisites

- **Node.js 20 LTS** (Node 22 also fine)
- **Cameo/MagicDraw 26xR1** installed (only needed at runtime — tests use vendored fixtures)
- Optional: **Groovy** (`groovyc` on PATH) and a **JDK** (`javac` on PATH) to enable `validate_script_syntax`. Both are present in any MagicDraw dev setup.

## Install

In PowerShell or Git Bash at the repo root:

```powershell
cd MCP4MagicAPI
npm install
npm run build
```

## Configure paths (if your install differs)

Defaults point at `E:\Magic SW\MCSE26xR1_4_2\openapi\...`. Override with env vars:

| Variable | Default |
|---|---|
| `CAMEO_JAVADOC_PATH`  | `…\openapi\docs\md-javadoc-2026.1.0-485-c01d52da-javadoc` |
| `CAMEO_GUIDE_PATH`    | `…\openapi\guide\guide` |
| `CAMEO_EXAMPLES_PATH` | `…\openapi\examples` |

## Run the tests

```powershell
cd MCP4MagicAPI
npm test
```

Expect **90+ tests green** across 10 files (unit + integration). Tests use small vendored fixtures by default; if the real corpus is installed at the expected path, additional real-corpus smoke tests run automatically.

## Register the MCP with Claude Code

```powershell
# From the repo root — use the absolute path to build/index.js:
claude mcp add MCP4MagicAPI node E:\_Documents\git\TutorialForCatiaMagicApiMCP\MCP4MagicAPI\build\index.js
```

Then restart Claude Code. Run `/mcp` — you should see MCP4MagicAPI listed with all 13 tools.

## Use the agent

In any Claude Code session inside this repo, invoke the subagent:

> *Agent, use **cameo-api-scripter**: write a Groovy script that adds a Class named 'Foo' under the model root in the current project, wrapped in a session.*

Expected behavior:

1. Agent calls `snippet_get session-wrap` and `javadoc_get_class com.nomagic.magicdraw.openapi.uml.SessionManager`.
2. Returned script uses `createSession` / `closeSession` / `cancelSession` try/catch.
3. Agent calls `validate_script_syntax` on its output before returning.
4. At least one guide page or example is cited.

## The 10 rules baked into the agent

Pulled from [.claude/agents/cameo-api-scripter.md](.claude/agents/cameo-api-scripter.md):

1. **Sessions** — every mutation inside `createSession` / `closeSession`; `cancelSession` on exception.
2. **No GStrings at API boundaries** — `"Hello ${x}"` is `GStringImpl`, not `String`. Use `'...' + x` or `.toString()`.
3. **Three error channels** — log4j for plugin logs, `GUILog` for user dialogs only, log4j2 for validation traces, plus a diagnostic file under `logs/`.
4. **No `System.exit`** — kills MagicDraw.
5. **Headless detection** — infer from `null` `Application` / `Project`.
6. **`Finder` over owner-walks** — `byQualifiedName`, `byTypeRecursively`.
7. **Collections are live** — copy before mutating.
8. **Association ends** — `getEnd()[0..1]`, not slots.
9. **REST harness discipline** — clean-shutdown before relaunch.
10. **TDD loop** — commit → test → implement → verify → repeat.

## TDD during development

Every iteration in this project (11 total) ended with `npm test` green and a commit. See `git log --oneline` for the step-by-step history. When you add a new tool or snippet:

```powershell
npm run test:watch          # red
# edit
                            # green
git add -A && git commit    # commit message explains the WHY
```

## Contributing a snippet or best practice

- Snippets live in [MCP4MagicAPI/src/data/snippets.json](MCP4MagicAPI/src/data/snippets.json). Shape: `{name, language, description, body, sourceExamples[]}`.
- Best-practice cards live in [MCP4MagicAPI/src/data/best-practices.json](MCP4MagicAPI/src/data/best-practices.json). Shape: `{topic, summary, do[], dont[], snippet|null, source}`.
- Unit tests enforce the **no-GString-in-snippets** rule automatically ([tests/unit/snippetStore.test.ts](MCP4MagicAPI/tests/unit/snippetStore.test.ts)) — don't disable it.

## References

- [Model Context Protocol](https://modelcontextprotocol.io/) — spec and TypeScript SDK.
- Cameo Open API Javadoc — ships in your MagicDraw install at `openapi/docs/...`.
- Cameo Developer Guide — ships at `openapi/guide/guide/*.html` (197 pages).
- 57 bundled example projects at `openapi/examples/`.

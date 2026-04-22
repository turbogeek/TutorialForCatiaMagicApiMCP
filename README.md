# TutorialForCatiaMagicApiMCP

A tutorial project that builds — and then uses — an **MCP (Model Context Protocol) server** purpose-built for authoring scripts against the **Cameo / MagicDraw / CATIA Magic Open API** (version 26xR1). The paired Claude Code subagent, `cameo-api-scripter`, knows the conventions (sessions, error channels, GString pitfalls, etc.) that the raw docs don't spell out in one place.

> [!WARNING]
> This is based on instructions as of 2026-04-22 and subject to change.

> [!NOTE]
> This is a tutorial and is **not** supported by Dassault Systèmes. For educational purposes only — no support, guarantees, warranties, or assumption of liabilities for the content or your use of it.

---

## Now things get a little squirrely

Here is the prompt I gave Claude Code:

> [!WARNING]
> Make sure you are in a good high-power version and you have **Plan Mode** turned on (**Opus 4.7 Extra High** was used for this step, so TL;DR YMMV). Note that you need to uncompress the Open API data in your installation and remove the zip files.

```
I have begun the setup for an MCP server that is hosted on Windows. This MCP server is to support the writing of Java or supported scripts like Groovy and JavaScript scripts for the Open API of MagicDraw/Cameo/Catia-Magic's Open API (hereafter called simply Cameo API) which is a Java-based API. I have given you a reference to the JavaDoc, Developer Guide, and Samples folders for version 26xR1 of the API: "E:\Magic SW\MCSE26xR1_4_2\openapi" The working directory of the github project is here "E:\_Documents\git\TutorialForCatiaMagicApiMCP" and the directory for the specific config and code of the MCP is here "E:\_Documents\git\TutorialForCatiaMagicApiMCP\MCP4MagicAPI". Please create the MCP and an agent to use it. In the agent, be sure to include best practices for scripting in the MagicDraw environment, and best practices for using sessions when using API to modify the model and for reporting errors to the MagicDraw console. Create a plan.md and after I review it, we will begin coding. Remember to be accurate, create and use test-first Iterative Testing (TDD Flow) methodology that always runs unit and integration tests on the server after each change, use good documentation, and update the README with tutorials and features along the way.
```

At this stage you probably get a prompt that Claude has completed its research and is ready to create the plan. If so, tell it to proceed. If it creates the plan, review it and make any changes.

> [!NOTE]
> The AI may ask you questions like where to put the agent files (I chose this project), adding Groovy/Java validation (I chose both), what versions to use (I took the recommended), etc. I usually select the default or widest scope.

When you are ready, let it execute the plan.

> [!WARNING]
> This eats a lot of context tokens and on normal accounts up to 20% or more of your available tokens!

## Along the way, heading off other errors

As I continued, I remembered that the system often forgets that, when using Groovy with the API, Fast Strings (GStrings) should not be used, and that I should remind the AI how to load dependent scripts, like the one used to log to the console. So when given an opportunity, I added this prompt:

```
I have created a scripts directory with a logger tool, "E:\_Documents\git\TutorialForCatiaMagicApiMCP\scripts\SysMLv2Logger.groovy", and an example, "E:\_Documents\git\TutorialForCatiaMagicApiMCP\scripts\RequirementSatisfyMatrixGraphics.groovy", adding snippets on the creation, setup, and usage of this console logger. Also note the sequence used in the setup for loading a script, which is the best method when in the Cameo environment. Also, forbid the use of Fast Strings in Groovy, which are generally incompatible with the strings used in the Cameo api. Then proceed with your plan.
```

## Starting the MCP locally

Here are the instructions that the AI gave for starting the server:

```powershell
cd MCP4MagicAPI
npm install
npm run build
claude mcp add MCP4MagicAPI node E:\_Documents\git\TutorialForCatiaMagicApiMCP\MCP4MagicAPI\build\index.js
```

## Testing

Next it's time to test the MCP. Here's the prompt I wrote for this task:

```
When you are done, let's try using the MCP to create the following Groovy script via this prompt: Create a Java Swing form to help the user create the parts of the system by iterating from the top block to its parts and subparts to create the Blocks for all the parts and the part attributes for subparts. When the user presses OK, the model is created with the top-level system block in a system package, the second level in a subsystems package, and all other Blocks in a library package called parts catalog. This is for SysMLv1.
```

## I could'a should'a

Sadly, LLMs are not the smartest kids on the block, and neither are users of LLMs — me.

I started to notice a trail of stupidity forming. In the Cameo API, the big thing is that the *application* is where we live. Generally we don't just run a Groovy script with Cameo's classpath — instead, we run Cameo and then run the script inside the tool. That matters. The tool does a lot to set up and control the model. By running a standalone script, none of that is possible. There are methods (e.g. running a report has very specific command-line options), but mostly it's interactive. So I interrupted the LLM while it was struggling with classpath to tell it this:

```
Are you aware of how to start Cameo from the command line and as part of this to run a script? We are running this particular installation: E:\Magic SW\MCSE26xR1_4_2, which means we need to run Cameo to run the script, not Groovy to run in the context of the Cameo classpath. If you can make this work, you have created the ultimate script testing tool. However, many scripts are used by running them after Cameo launches. In that situation, make sure to write a separate log file that gets cleared at the start of the script, which you can use to see and correct errors.
```

Here is the response, which shows that I should have included this up front.

> Understood — I was going down the wrong path with classpath. The Cameo CLI launcher approach is much better. Let me kill the background `find` and investigate the Cameo command-line runner.
>
> Ran 14 commands, read 4 files, used 2 tools
>
> I have enough understanding now. The guide page `Running-programs-in-batch-mode.254437299.html` explicitly says batch mode still needs a graphical environment, but scripts can be run two ways: extending `CommandLine` (core) or implementing `CommandLineAction` (plugin-scoped). Let me:
>
> - Add the dedicated-log-file-cleared-on-start convention to your Block Builder and to the snippet library (addresses your post-launch ask).
> - Document the command-line batch runner approach as a new best practice, citing the two guide pages.
> - Clean up the scratch validate script.

Hooray — more credits saved. Time to update the agent to ensure this does not happen again.

## User Smoke Testing

The first run of the generated script *worked* — but it was still wrong in a sneaky way. I fed Claude the following prompt to tighten the MCP. There's more fine-tuning going on than I'm documenting here (I hope to capture the conversation for posterity), but this gives the reader what's necessary to home in on the correct result.

```
Got this error, which means that you need to better ensure you need improvements to your MCP as "com.nomagic.uml2.ext.magicdraw.classes.mdprofiles.Stereotype" does not exist. The real item is "com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype," so there is an insert of a very bad bit of a package path. This also means there is a fatal flaw in the MCP: it does not ensure a hallucination, and your decisions are predictable based on the MCP's data lookup. That is not the only error, as I see 6 packages that do not exist. I suspect there are other issues. Also, you need to prioritize factories and helper classes, and lower the priority of ecore over other packages, as ecore is plumbing rather than core, as you can see in the examples.
```

```
Add that automatic cross-check. Also, the MCP should return a proper error if the pointer to the installed documentation is missing or not as expected. However, it may be better to create a fast RAG of this data and a capability to add a new version. We should also have the capability to switch versions, change modeling types like SysMLv1, UAF, SysMLv2, and others depending on configuration and thus what is in the API and the profiles we have access to in the target model. This means also that the agent needs to ask up front when this information is not provided.
```

<img width="1024" height="1024" alt="Bob_thinking" src="https://github.com/user-attachments/assets/60a0bcfd-35b8-4de4-a3f2-5df72ba5ffad" />

---

# Appendix: Reference for the MCP that got built

## What you get

- **22 MCP tools** over three local corpora of the Cameo Open API:
  - Javadoc: `javadoc_search`, `javadoc_get_class`, `javadoc_list_packages`, `javadoc_verify_fqn`
  - Guide: `guide_search`, `guide_read_page`, `guide_list_pages`
  - Examples: `example_list`, `example_list_files`, `example_read_file`, `example_search`
  - Best practices / snippets: `best_practice_lookup`, `best_practice_list`, `snippet_get`, `snippet_list`
  - Validator: `validate_script_syntax` (Groovy + Java via `groovyc` / `javac`, with automatic GString lint AND automatic FQN cross-check against the Javadoc)
  - Profiles (multi-version / multi-modeling-type): `cameo_profile_list`, `cameo_profile_active`, `cameo_profile_switch`, `cameo_profile_add`, `cameo_profile_remove`, `cameo_profile_status`
- A curated **snippet library** covering sessions, logging, script loading, Finder idioms, association ends, the dedicated-log-file pattern, the batch-runner skeleton, and more — all Groovy unless noted, all vetted for Cameo API compatibility.
- A **best-practices catalog** (14 topics) capturing what to do *and* what to avoid: the `ask-first` rule, session discipline, three-channel error reporting, the **no-GString-at-API-boundary** rule, no `System.exit`, headless detection, `Finder` over owner-walks, collections-are-live, association ends, REST harness hygiene, dedicated-log-file, batch-runner, and **verify-fqn** (mandatory FQN check before emitting any `com.nomagic.*` import).
- A **subagent** ([.claude/agents/cameo-api-scripter.md](.claude/agents/cameo-api-scripter.md)) that wires these tools into a scripting protocol: asks up front about API version and modeling types, requires FQN verification before every import, requires `validate_script_syntax` before returning code.

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
│   ├── RequirementSatisfyMatrixGraphics.groovy
│   └── SysMLv1BlockBuilder.groovy         ← Swing wizard built via the MCP
└── MCP4MagicAPI/                          ← the MCP server (Node/TS)
    ├── src/
    │   ├── index.ts                       ← tool registration + stdio entry
    │   ├── config.ts                      ← env-var + profile resolver
    │   ├── adapters/                      ← javadoc / guide / examples / validate / profile
    │   ├── tools/                         ← one file per MCP tool group
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

## Configure paths (if your install differs)

Three layers, in order of precedence:

1. **Env vars** — explicit, always win.
2. **Active profile** — `.config/profiles.json` (per-developer, gitignored).
3. **Defaults** — hard-coded 26xR1 paths.

| Variable | Default |
|---|---|
| `CAMEO_JAVADOC_PATH`  | `…\openapi\docs\md-javadoc-2026.1.0-485-c01d52da-javadoc` |
| `CAMEO_GUIDE_PATH`    | `…\openapi\guide\guide` |
| `CAMEO_EXAMPLES_PATH` | `…\openapi\examples` |

For multi-version / multi-modeling-type setups, call `cameo_profile_add` from the agent to register each environment, then `cameo_profile_switch` to activate the one matching your current project.

## Run the tests

```powershell
cd MCP4MagicAPI
npm test
```

Expect **125+ tests green** across 14 files (unit + integration). Tests use small vendored fixtures by default; if the real corpus is installed at the expected path, additional real-corpus smoke tests run automatically.

## Use the agent

In any Claude Code session inside this repo, invoke the subagent:

> *Agent, use **cameo-api-scripter**: write a Groovy script that adds a Class named 'Foo' under the model root in the current project, wrapped in a session.*

Expected behavior:

1. Agent calls `cameo_profile_status` and, if modeling type is unknown, **asks you** before doing anything else.
2. Agent calls `snippet_get session-wrap`, `javadoc_verify_fqn` on each intended import, and `javadoc_get_class` for `SessionManager`.
3. Returned script uses `createSession` / `closeSession` / `cancelSession` try/catch, never `System.exit`, no GStrings at API boundaries.
4. Agent calls `validate_script_syntax` on its output before returning — any `lintWarnings[]` (GString risk or unknown FQN) is resolved.
5. At least one guide page, example, or Javadoc class is cited.

## The 11 rules baked into the agent

Pulled from [.claude/agents/cameo-api-scripter.md](.claude/agents/cameo-api-scripter.md):

0. **Ask up front** — confirm API version and modeling types via `cameo_profile_status` before writing anything. SysMLv1 and SysMLv2 use different packages.
1. **Verify every FQN** before emitting an import via `javadoc_verify_fqn`. Hallucinations are the #1 failure mode.
2. **Sessions** — every mutation inside `createSession` / `closeSession`; `cancelSession` on exception.
3. **No GStrings at API boundaries** — `"Hello ${x}"` is `GStringImpl`, not `String`. Use `'...' + x` or `.toString()`.
4. **Three error channels** — log4j for plugin logs, `GUILog` for user dialogs only, log4j2 for validation traces, plus a diagnostic file under `logs/`.
5. **No `System.exit`** — kills MagicDraw.
6. **Headless detection** — infer from `null` `Application` / `Project`.
7. **`Finder` over owner-walks** — `byQualifiedName`, `byTypeRecursively`.
8. **Collections are live** — copy before mutating.
9. **Association ends** — `getEnd()[0..1]`, not slots.
10. **REST harness discipline** — clean-shutdown before relaunch.

## TDD during development

Every iteration in this project ended with `npm test` green and a commit. See `git log --oneline` for the step-by-step history. When you add a new tool or snippet:

```powershell
npm run test:watch          # red
# edit
                            # green
git add -A && git commit    # commit message explains the WHY
```

## Contributing a snippet or best practice

- Snippets live in [MCP4MagicAPI/src/data/snippets.json](MCP4MagicAPI/src/data/snippets.json). Shape: `{name, language, description, body, sourceExamples[]}`.
- Best-practice cards live in [MCP4MagicAPI/src/data/best-practices.json](MCP4MagicAPI/src/data/best-practices.json). Shape: `{topic, summary, do[], dont[], snippet|null, source, modelingTypes?}`.
- Unit tests enforce the **no-GString-in-snippets** rule automatically ([tests/unit/snippetStore.test.ts](MCP4MagicAPI/tests/unit/snippetStore.test.ts)) — don't disable it.

## References

- [Model Context Protocol](https://modelcontextprotocol.io/) — spec and TypeScript SDK.
- Cameo Open API Javadoc — ships in your MagicDraw install at `openapi/docs/...`.
- Cameo Developer Guide — ships at `openapi/guide/guide/*.html` (197 pages).
- 57 bundled example projects at `openapi/examples/`.

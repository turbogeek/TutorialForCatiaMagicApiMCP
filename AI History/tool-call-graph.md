# Tool call graph

Which MCP tools the `cameo-api-scripter` subagent should call at each
phase, and why. This is the executive summary of the protocol — the
canonical enforceable copy is
[`.claude/agents/cameo-api-scripter.md`](../.claude/agents/cameo-api-scripter.md).

```
Session start
    ├── cameo_profile_status          ──► resolvedPaths + health + modelingTypes
    │                                      │
    │                                      ├── if modelingTypes == []       ┐
    │                                      │   or activeProfile == null     │  ASK THE USER
    │                                      │                                │  before proceeding
    │                                      ├── if any corpus.ok == false    ┘
    │                                      │
    │                                      └── (good) continue ▼
    │
    └── best_practice_lookup ask-first   ──► card that says "ask when modelingTypes unknown"
                                             (defense in depth against the agent forgetting)

Research (parallel when independent)
    ├── best_practice_lookup <topic>     ──► sessions / no-fast-strings /
    │                                        error-reporting / finder / headless / ...
    ├── guide_search <query>             ──► ranked pages
    │       └── guide_read_page <pageId> ──► body + code blocks
    ├── example_search <query>           ──► grep hits
    │       └── example_read_file        ──► full file
    ├── javadoc_search <name>            ──► class/method hits (ecore demoted)
    └── javadoc_get_class <fqn>          ──► methods + deprecation flags

Before emitting an import
    └── javadoc_verify_fqn <fqn>         ──► {exists, candidates, similar}
                                             MANDATORY for every com.nomagic.*
                                             / com.dassault_systemes.* import

Write the script
    ├── snippet_get session-wrap         ──► for every model mutation
    ├── snippet_get script-load-groovy   ──► if loading a companion class
    ├── snippet_get console-logger-class ──► if logging is needed
    ├── snippet_get logger-dedicated-file
    ├── snippet_get finder-by-qname / -by-type
    └── snippet_get headless-detect / association-ends / ...

Validate before returning
    └── validate_script_syntax           ──► {ok, issues[], lintWarnings[]}
            │                                  │
            │                                  ├── compiler errors ──► fix
            │                                  ├── GString at API boundary ──► fix
            │                                  └── unknown import (auto-cross-check) ──► fix
            │
            └── loop until ok == true AND lintWarnings is empty

Return to user
    Cite guide pageIds, Javadoc FQNs, example folder names
```

## Which tools are mandatory vs. advisory

**Mandatory every session:**

- `cameo_profile_status` (or `cameo_profile_active`) at session start.
- `javadoc_verify_fqn` before emitting any `com.nomagic.*` / `com.dassault_systemes.*` import line.
- `validate_script_syntax` before returning any Groovy or Java to the user.

**Advisory (but usually called):**

- `best_practice_lookup` for each relevant topic.
- `javadoc_search` when a name is uncertain.
- `guide_search` + `guide_read_page` for "how do I" questions.
- `example_search` before inventing a pattern (prefer existing code).

**Rarely needed:**

- `cameo_profile_add` / `_switch` / `_remove` — only when the user changes
  environments.
- `javadoc_list_packages` — mostly for browsing / exploration.
- `guide_list_pages` — for topic-filtered browsing.
- `best_practice_list` — only when the agent doesn't know which topic to
  look up.

## Response shape

Every tool returns:

```ts
{
  content: [{ type: "text", text: "<pretty JSON>" }],
  structuredContent: { ... },  // typed payload for programmatic use
}
```

Tools return errors in `structuredContent` with `isError: true` when the
MCP conventions allow, or raise an error through the JSON-RPC layer when
the input is invalid (e.g. unknown snippet name).

## What happens when the agent skips a mandatory step

- Skipping `javadoc_verify_fqn`: user hits compile-time "unable to resolve
  class". Wastes one iteration.
- Skipping `validate_script_syntax`: a GString or unknown import slips
  through. User hits a subtle runtime failure (GString) or compile error
  (import). Wastes one or more iterations.
- Skipping `cameo_profile_status`: agent assumes SysMLv1 when the project
  is SysMLv2 (or vice versa). Generates correct-looking but fundamentally
  wrong code. Wastes one or more iterations.

These aren't paranoid belt-and-braces. Each step short-circuits a real
failure class we've hit.

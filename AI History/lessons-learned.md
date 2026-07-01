# Lessons learned

Distilled from the build of MCP4MagicAPI. Each entry tries to answer:
what did we think, what actually happened, what do we do next time?

## On designing an MCP for a large API

### 1. Prefer the data the upstream shipped

The Javadoc tool ships `type-search-index.js`, `member-search-index.js`, and
`package-search-index.js`. They are the same JSON the doc-site's search box
consumes. Parsing them is roughly 100× faster than walking HTML and gives
us the same accuracy as the official search. The Cameo Developer Guide
ships a Lunr index for the same reason.

**Takeaway:** before you write a crawler for upstream docs, look for
pre-built indexes hiding in `.js`/`.json` files alongside the HTML.

### 2. Name-only search needs camelCase splitting

First attempt at `guide_search` tokenized "SessionManager" as
`["sessionmanager"]` and "Session management" titles as
`["session","management"]` — zero overlap, so the most relevant page
ranked 20th. Fixed with `splitCamel(token)` emitting both the whole word
and the parts. All modern code-docs search tools do this.

**Takeaway:** for code identifiers, always split camelCase and snake_case
into component tokens alongside the original.

### 3. The ranker must know which packages are plumbing

First `javadoc_search` query for `StereotypesHelper` returned the ecore
wrapper first. Ecore, impl, emfuml2xmi, and reflection helpers are
**plumbing**, not the scripting surface. The fix is a small additive table:
`packagePriority(pkg)` that subtracts score for plumbing packages and adds
score for helper/factory/openapi paths. About 15 regex rules total, but it
dramatically changes the result quality for the most common queries.

**Takeaway:** spend 20 minutes curating a priority table. The agent's
quality improves more than a 10× bigger index would buy you.

### 4. Verify, don't trust

The #1 failure mode turned out to be FQN hallucination — the agent writes
`com.nomagic.uml2.ext.magicdraw.classes.mdprofiles.Stereotype` because
other classes in the tree live under `.classes.*`. The real path has no
`.classes.` in it. The model is confidently wrong.

Two layers of defense:
1. `javadoc_verify_fqn` — a cheap yes/no tool that returns the correct
   candidate when the answer is no.
2. `validate_script_syntax` parses every `import com.nomagic.*` and runs
   it through `verify_fqn` automatically. Unknowns become `lintWarnings`
   with a "Did you mean:" hint.

Either layer alone can be skipped by the agent. Together, it's almost
impossible for a bad FQN to survive to the user.

**Takeaway:** when the LLM can hallucinate a structured value, build a
**deterministic** check for it and put it in the validation pipeline, not
just in the prompt.

## On building with an LLM pair-programmer

### 5. Ask up front, not down the road

I (Claude) burned ~30% of the context budget on a classpath detour
because I assumed the Block Builder script would be compiled standalone.
The user corrected me: scripts run **inside** the Cameo JVM, not against
its classpath. Had I asked at the start ("how are these scripts normally
executed?"), I'd have gone straight to the `CommandLineAction` pattern and
the dedicated-log-file convention without the detour.

This became the `ask-first` best-practice card: the agent is now required
to call `cameo_profile_status` at session start, and if `modelingTypes`
is empty it must **stop and ask** before writing anything.

**Takeaway:** for any generative task in a complex environment, the first
question should always be "what environment is this going to run in?" —
not a research task, a direct user question.

### 6. "Best practice" is too vague; write a card

The initial agent prompt said things like "use SessionManager correctly"
and "don't use GStrings at API boundaries." That's useless — it relies on
the LLM remembering, and the LLM will forget under load.

The fix was `best_practice_lookup`: each topic gets a card with `summary`,
`do[]`, `dont[]`, `snippet|null`, and `source`. The agent is required to
call the tool for relevant topics before writing code. The cards are
version-controlled JSON — anyone can add one in a PR without touching
code.

**Takeaway:** if you find yourself repeating a guideline to the agent in
prose, convert it into a structured card the agent can look up.

### 7. The validator is the most important tool

Every other tool is read-only research. The validator is the only one
that **fails loudly when the generated code is wrong**. It's what breaks
the hallucination loop.

Specifically, adding (a) a GString lint that scans for double-quoted
strings with `$` near Cameo call sites, and (b) the automatic FQN
cross-check, turned the validator from "syntax check" into "semantic
sanity check" for the 80% case. Both run even when the compiler isn't
installed — pure text + index lookup.

**Takeaway:** invest in the validator. Every new class of mistake should
show up there before the user sees it.

## On the Windows + Node.js + Groovy/Java toolchain

### 8. `shell: true` on Windows, and quote whitespace args

Node's `child_process.spawn` with `shell: false` cannot launch `.bat`
files — and `groovyc`, `mvn`, and friends are `.bat` wrappers on Windows.
Flip `shell: true` on `win32` only.

But `shell: true` concatenates args with spaces before handing them to
cmd.exe, so `E:\Magic SW\lib\*` becomes two tokens. Quote every arg that
contains whitespace (Windows only).

**Takeaway:** if you spawn compilers from Node on Windows, the
three-liner is always the same:

```ts
const useShell = process.platform === "win32";
const safeArgs = useShell
  ? args.map((a) => (/\s/.test(a) ? `"${a}"` : a))
  : args;
spawn(cmd, safeArgs, { shell: useShell, ... });
```

### 9. `parseXxxOutput` must tolerate drive-letter colons

`javac` output: `C:\path\Snippet.java:1: error: ...`. A greedy `[^:]+`
regex for the file path stops at the drive-letter colon and the rest
doesn't match. Anchor instead on `:(\d+):\s*(error|warning):\s*(.+)$`
anywhere in the line. Same fix for `groovyc`.

**Takeaway:** any regex that parses compiler output needs to handle
Windows paths. Test on Windows.

### 10. Lazy cache; mtime-invalidate; write to disk

The guide inverted index and the Javadoc type-index are built on first
call (O(ms) for Javadoc, O(800ms) for guide across 197 HTML pages), then
persisted to `.cache/*.json`, then validated on next startup by matching
each source file's `mtimeMs` + `size`. If anything changed, rebuild. If
nothing changed, load the JSON.

**Takeaway:** three-tier caching (in-memory → disk → rebuild) with cheap
invalidation is the right pattern for corpus-sized data. Don't build at
startup (slows cold boot); don't skip the disk tier (subsequent process
starts waste time).

## On the repo pattern itself

### 11. Tests pin conventions

Every non-negotiable rule has a test. Examples:

- "No GStrings in snippets" — a unit test scans every Groovy snippet body
  and fails on any `"...$..."`. It's impossible for a future contributor
  to add a GString to the snippet library.
- "Every seeded best-practice card must have non-empty `do[]` and
  `dont[]`" — a loop asserts it for every card.
- "FQN 'classes.mdprofiles.Stereotype' resolves to the real
  `mdprofiles.Stereotype` via candidates" — a real-corpus smoke test
  reproduces the exact hallucination we hit and asserts the recovery.

**Takeaway:** once you've fixed a class of mistake, encode it as a test
so it can't come back.

### 12. The plan file stays as history

`plan.md` was written at the start, with "Open questions" and a build
order. It's now partly stale (numbers have moved, some decisions changed),
but it's preserved as-is so anyone reading the repo can see the
pre-implementation design thinking. Not every artifact needs to be
maintained forever — some documents are snapshots.

**Takeaway:** distinguish living docs (README, agent, best-practices)
from historical docs (plan.md, Claude History). Keep both.

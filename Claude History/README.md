# Claude History

This directory captures the conversation that built the MCP4MagicAPI server
and the `cameo-api-scripter` subagent. It exists so that:

1. **Anyone reading the repo can reconstruct the design decisions** by reading
   the user's prompts and the key responses that shaped each iteration.
2. **Future Claude sessions** can learn from the mistakes and course
   corrections already worked through — most of them are recorded as
   best-practice cards now, but the story of *why* each card exists is here.
3. **The tutorial stays honest**: real development is messy, with detours and
   rework. This folder shows the rework.

## Files

- **[conversation-log.md](conversation-log.md)** — Chronological log of the
  build, with every user prompt verbatim and condensed summaries of Claude's
  responses and actions. Roughly 1 section per substantive exchange.
- **[lessons-learned.md](lessons-learned.md)** — Distilled takeaways for
  anyone reproducing this pattern (MCP + subagent + real-world validation
  loop) on another domain.
- **[tool-call-graph.md](tool-call-graph.md)** — Which MCP tools the agent is
  supposed to call when, with the rationale. This is the executive summary of
  the agent's protocol — the same protocol lives canonically in
  `.claude/agents/cameo-api-scripter.md`, but this file explains *why* each
  step exists.

## Reading order

- First time through the project: start with the main [README](../README.md)
  for the user-facing narrative, then dip into
  [conversation-log.md](conversation-log.md) if you want the detail on any
  iteration.
- Debugging or extending the MCP: jump to
  [lessons-learned.md](lessons-learned.md) for the "don't step on this rake
  again" list.
- Building a similar MCP for a different codebase: start with
  [tool-call-graph.md](tool-call-graph.md) then adapt the pattern.

## What this is not

- A commit log. Use `git log --oneline` for that.
- A full transcript of every tool call Claude made. Those are thousands of
  lines of filesystem reads that don't matter in aggregate.
- A style guide. The best practices live in
  [`MCP4MagicAPI/src/data/best-practices.json`](../MCP4MagicAPI/src/data/best-practices.json)
  where the MCP can serve them to the agent at runtime.

# Cameo Test Harness

A small HTTP server that runs **inside a live Cameo/MagicDraw session** and
lets you trigger Groovy scripts over REST — with automatic resource cleanup
when a script finishes (or when a new script asks to take over).

**No external dependencies.** Pure Groovy + JDK `com.sun.net.httpserver`.

## What problem it solves

Cameo scripts run inside the application's JVM. Running the same script
twice in a row often hits two failure modes:

1. **Stale classloader cache.** Groovy caches parsed classes per
   `GroovyClassLoader`. Re-running through the same classloader serves the
   old bytecode and your edits appear to have no effect.
2. **Leaked windows / sessions.** If a prior script opened a `JDialog` and
   crashed, or opened a `SessionManager` session and never closed it, the
   next run inherits a dirty UI and a stuck model.

This harness fixes both: **every `/run` request uses a fresh
`GroovyClassLoader`, disposes any windows the previous script opened, and
cancels any active `SessionManager` session** before launching the new one.

## Endpoints (all `localhost:8765` by default, all JSON)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/run` | `{"scriptPath": "…", "args": ["…"]?, "waitMs": 0?}` | `202 { runId, state, … }` |
| POST | `/stop` | `{"reason": "…"?}` | `{ state, cleanup: { windowsDisposed, sessionCancelled } }` |
| GET  | `/status` | — | `{ runId, scriptPath, state, startedAt, finishedAt?, error?, logLength }` |
| GET  | `/log` | — (query `?since=<offset>`) | plain-text stdout+stderr from the current/last run |
| POST | `/stop-harness` | `{}` | `{ state: "harness-shutting-down" }` |
| GET  | `/health` | — | `{ harness, port, endpoints, state }` |

State values: `idle` | `running` | `done` | `failed` | `stopped`.

## How it cleans up

On a `/stop` (or when `/run` replaces a current run), the harness:

1. **Interrupts** the worker thread that's running the script.
   (Scripts that ignore `InterruptedException` won't stop here — but…)
2. **Disposes** every `java.awt.Window` that didn't exist before the script
   started. A baseline snapshot is taken with `Window.getWindows()` before
   each run; anything new on stop is `setVisible(false)` + `dispose()`.
3. **Cancels** the current `SessionManager` session on the active project if
   one is open.
4. **Drops** the script's `GroovyClassLoader` so the GC can reclaim its
   compiled classes. The next `/run` gets a fresh classloader — no stale
   cache.

It does NOT try to unregister listeners your script may have attached to
Cameo; scripts should do their own listener cleanup on window close.

## Install & start

**Prerequisite:** the project is at
`E:\_Documents\git\TutorialForCatiaMagicApiMCP\` (path is hard-coded in
`start-harness.groovy` for the logger path; edit if your tree differs).

1. Open Cameo / MagicDraw.
2. Open the Groovy script console
   (*Tools → Macros → Groovy Console*, or similar — depends on Cameo
   version).
3. Load and run
   `E:\_Documents\git\TutorialForCatiaMagicApiMCP\test harness\start-harness.groovy`.
4. A message appears in the GUI console log:
   > `Cameo Test Harness listening on http://127.0.0.1:8765  (endpoints: /run /stop /status /log /stop-harness)`

The harness now lives in Cameo's JVM until you either run
`stop-harness.groovy` or exit Cameo.

## Use

### From a shell

```bash
# Health check
curl -s http://127.0.0.1:8765/health | jq .

# Run a script; block up to 10 s waiting for it to finish
curl -s -X POST http://127.0.0.1:8765/run \
  -H 'Content-Type: application/json' \
  -d '{"scriptPath":"E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\scripts\\v2Matrix\\MatrixDialog.groovy","waitMs":10000}' | jq .

# Tail the log
curl -s http://127.0.0.1:8765/log

# Stop the current run (dispose its windows, cancel any open session)
curl -s -X POST http://127.0.0.1:8765/stop -d '{}' -H 'Content-Type: application/json' | jq .
```

### From Node / Claude via the bundled client

```bash
node "test harness/client/harness-client.mjs" health
node "test harness/client/harness-client.mjs" run "E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\scripts\\v2Matrix\\MatrixDialog.groovy" --wait 10000
node "test harness/client/harness-client.mjs" log
node "test harness/client/harness-client.mjs" stop
node "test harness/client/harness-client.mjs" stop-harness
```

Override host/port via `HARNESS_HOST` / `HARNESS_PORT`.

## Stop the harness

Option 1 — from inside Cameo's console:

```
(load "E:\_Documents\git\TutorialForCatiaMagicApiMCP\test harness\stop-harness.groovy")
```

Option 2 — from outside:

```bash
node "test harness/client/harness-client.mjs" stop-harness
```

The server does a 250 ms delay before closing so it can finish the HTTP
response, then logs `=== Test harness stopped ===` to the GUI console and
`logs/test-harness.log`. No `System.exit` anywhere (would kill Cameo).

## Log file

`logs/test-harness.log` — the harness's own diagnostic log, cleared on
start. Separate from per-script logs (which live at `logs/<scriptName>.log`
per the `dedicated-log-file` convention).

## Non-goals

- **Multi-script concurrency.** One run at a time. A new `/run` replaces
  the current one. If you need parallel scripts, run them as separate
  threads inside ONE script.
- **Authentication.** The server binds to `127.0.0.1` only; anyone who can
  run code on the same machine can already drive Cameo. If you need remote
  access, put the harness behind a reverse proxy with auth.
- **Full listener cleanup.** Scripts must dispose their own listeners on
  window close. The harness cannot introspect Cameo's internal listener
  tables without extensive reflection.
- **Graceful thread stop for non-interruptible scripts.** A script stuck
  in a tight CPU loop with no I/O won't respond to `Thread.interrupt()`.
  Its windows will still get disposed on `/stop`, but the thread lingers
  until Cameo exits.

## Troubleshooting

- **`BindException: address already in use`** on start — a previous harness
  is still running (or another process grabbed the port). Call
  `/stop-harness` on the old one, or set `-Dharness.port=…` on the new.
- **`/run` returns 500 with `Script not found`** — the path must be
  absolute and readable by the Cameo JVM. Forward slashes work too.
- **Windows I added don't close on `/stop`** — they were probably opened
  before the harness started (part of the baseline) or are Cameo's own
  windows. Baseline is computed per-run from `Window.getWindows()`.

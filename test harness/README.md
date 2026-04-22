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

## Install & start (via Tools → Macros)

Cameo's standard **Macros** plugin is the right bootstrap path for
SysMLv2 projects — the legacy Groovy *simulation console* only exists
where the simulation plugin is active, which is not the case for
SysMLv2 today.

**Prerequisite:** the project is at
`E:\_Documents\git\TutorialForCatiaMagicApiMCP\` (the logger path is
hard-coded in `start-harness.groovy` line 32; edit if your tree
differs).

### One-time: register two macros

1. **Tools → Macros → Organize Macros…** (menu path may be
   *Tools → Macros → Organize* in some versions).
2. Click **Add…** (or *New*). Fill in:
   - **Name:** `Test Harness — Start`
   - **Language:** `Groovy`
   - **File:** `E:\_Documents\git\TutorialForCatiaMagicApiMCP\test harness\start-harness.groovy`
   - *(Optional)* **Shortcut:** assign a keyboard shortcut if you like.
   - Click **OK**.
3. Click **Add…** again. Register the stop script the same way:
   - **Name:** `Test Harness — Stop`
   - **File:** `…\test harness\stop-harness.groovy`
   - Click **OK**.
4. Click **Close** on the Macros dialog.

### Each Cameo session: start the harness

- **Tools → Macros → Test Harness — Start**
- The GUI console log prints:
  > `Cameo Test Harness listening on http://127.0.0.1:8765  (endpoints: /run /stop /status /log /stop-harness)`

The harness now lives in Cameo's JVM until you either run the
*Test Harness — Stop* macro or exit Cameo. It does **not** need to be
re-started for each script run — `/run` reuses the live server and
simply loads the target script with a fresh classloader.

### Future: plugin JAR (deferred)

The next iteration will package the harness as a small Cameo plugin
(`plugin.xml` + Java wrapper + Gradle build) that auto-starts the HTTP
server when Cameo launches — zero-click after installation. For now,
the Macros route gets us functional immediately with no build step.

## Use

### Shell and batch wrappers (`bin/`)

Pre-made, no-dependency wrappers using `curl` so anyone — and Claude —
can drive the harness without writing a client:

```bash
# Git Bash / Linux / macOS
./test\ harness/bin/harness.sh health
./test\ harness/bin/harness.sh run 'E:\_Documents\git\TutorialForCatiaMagicApiMCP\scripts\v2Matrix\MatrixDialog.groovy' 10000
./test\ harness/bin/harness.sh log
./test\ harness/bin/harness.sh stop
./test\ harness/bin/harness.sh stop-harness

# Windows cmd / PowerShell
"test harness\bin\harness.bat" health
"test harness\bin\harness.bat" run "E:\_Documents\git\TutorialForCatiaMagicApiMCP\scripts\v2Matrix\MatrixDialog.groovy" 10000
"test harness\bin\harness.bat" log
"test harness\bin\harness.bat" stop
"test harness\bin\harness.bat" stop-harness
```

Commands:

| Wrapper command | HTTP |
|---|---|
| `health` | `GET /health` |
| `status` | `GET /status` |
| `log [SINCE]` | `GET /log[?since=SINCE]` |
| `run SCRIPT_PATH [WAIT_MS]` | `POST /run` |
| `stop [REASON]` | `POST /stop` |
| `stop-harness` | `POST /stop-harness` |
| `raw METHOD PATH [BODY]` | escape hatch |

Override host/port via `HARNESS_HOST` / `HARNESS_PORT`.

### Raw curl (fallback)

```bash
curl -s http://127.0.0.1:8765/health
curl -s -X POST http://127.0.0.1:8765/run \
  -H 'Content-Type: application/json' \
  -d '{"scriptPath":"E:\\path\\to\\script.groovy","waitMs":10000}'
curl -s http://127.0.0.1:8765/log
curl -s -X POST http://127.0.0.1:8765/stop -d '{}' -H 'Content-Type: application/json'
```

### Via the Node client (optional — curl wrappers above are simpler)

```bash
node "test harness/client/harness-client.mjs" health
node "test harness/client/harness-client.mjs" run "E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\scripts\\v2Matrix\\MatrixDialog.groovy" --wait 10000
node "test harness/client/harness-client.mjs" log
node "test harness/client/harness-client.mjs" stop
node "test harness/client/harness-client.mjs" stop-harness
```

Override host/port via `HARNESS_HOST` / `HARNESS_PORT`.

## Stop the harness

**Option 1 — from inside Cameo:**
*Tools → Macros → Test Harness — Stop* (assuming you registered it as above).

**Option 2 — from outside:**

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
- **curl returns HTTP 000 / "Empty reply from server"** — the harness's
  handler threw an exception. Look at `logs/test-harness.log`. Classic
  cause: using `groovy.json.JsonOutput` or `groovy.json.JsonSlurper`
  inside Cameo. These route through `FastStringUtils` whose SPI lookup
  fails in the Cameo classloader (`Unable to load FastStringService`).
  The harness uses a hand-rolled JSON encoder/decoder to avoid this.

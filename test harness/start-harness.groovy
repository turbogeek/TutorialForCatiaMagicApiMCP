// ==========================================================================
// Cameo Test Harness — start-harness.groovy
//
// Run this ONCE in Cameo's Groovy console to start a local HTTP server that
// exposes REST endpoints for running / stopping test scripts. The server
// lives in Cameo's JVM until you run stop-harness.groovy (or exit Cameo).
//
// Endpoints (all JSON, all localhost-only, default port 8765):
//
//   POST /run     { "scriptPath": "<abs path>", "args": [...], "waitMs": 0 }
//                 Stops any prior run (windows disposed, session cancelled,
//                 worker thread interrupted), then loads the script with a
//                 FRESH GroovyClassLoader (no stale class cache) and runs it
//                 on a worker thread. Returns 202 with { runId, state }.
//                 If waitMs > 0, blocks up to that many ms for the script
//                 to finish before returning.
//
//   POST /stop    {}
//                 Stops the current run: interrupts worker, disposes windows
//                 the script opened, cancels any active SessionManager
//                 session, drops the classloader. Returns { state: "stopped",
//                 cleanup: { windowsDisposed, sessionCancelled } }.
//
//   GET  /status  Returns { state, runId, scriptPath, startedAt,
//                            finishedAt?, error?, openWindows, log: {tail} }.
//
//   GET  /log     Returns the last run's captured stdout+stderr as text.
//                 Query ?since=<offset> streams from an offset.
//
//   POST /stop-harness {}
//                 Cleanly shuts down the HTTP server itself. Use before
//                 re-bootstrapping the harness after editing its source.
//
// Conventions followed (MCP4MagicAPI best-practices):
//   - No System.exit. Ever. (NFR-1)
//   - SessionManager.cancelSession on cleanup.
//   - Single-quoted strings at Cameo API boundaries (no GStrings). (no-fast-strings)
//   - Dedicated log file cleared on start: logs/test-harness.log.
// ==========================================================================

import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.awt.Window
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// ---- Load the logger ------------------------------------------------------
String scriptDir = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\scripts'
File loggerFile = new File(scriptDir, 'SysMLv2Logger.groovy')
def LoggerClass = new GroovyClassLoader(getClass().getClassLoader()).parseClass(loggerFile)
File harnessLog = new File(
    'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\logs',
    'test-harness.log'
)
def logger = LoggerClass.newInstance('TestHarness', harnessLog)
logger.info('=== Test harness starting ===')

// ---- Bounded log buffer (captures stdout/stderr during script runs) -------
class LogBuffer {
    private final StringBuilder buf = new StringBuilder()
    private final int cap
    LogBuffer(int cap = 512 * 1024) { this.cap = cap }
    synchronized void append(String s) {
        buf.append(s)
        if (buf.length() > cap) {
            // Keep the last `cap` chars; lose the earliest. Cheap ring.
            buf.delete(0, buf.length() - cap)
        }
    }
    synchronized String snapshot() { return buf.toString() }
    synchronized String since(int offset) {
        int o = Math.max(0, Math.min(offset, buf.length()))
        return buf.substring(o)
    }
    synchronized void clear() { buf.length = 0 }
    synchronized int length() { return buf.length() }
}

// ---- Run state ------------------------------------------------------------
class RunState {
    String runId
    String scriptPath
    List<String> args = []
    String state = 'idle'  // idle | running | done | failed | stopped
    long startedAt
    Long finishedAt
    String error
    Thread worker
    Set<Window> baselineWindows = new HashSet<>()
    Set<Window> openedWindows = new HashSet<>()
    GroovyClassLoader loader
    LogBuffer log = new LogBuffer()
    Map toJson() {
        [
            runId: runId,
            scriptPath: scriptPath,
            args: args,
            state: state,
            startedAt: startedAt,
            finishedAt: finishedAt,
            error: error,
            openWindowsCount: openedWindows.size(),
            logLength: log.length(),
        ]
    }
}

// ---- ScriptRunner: run, stop, clean up -----------------------------------
class ScriptRunner {
    private final AtomicReference<RunState> current = new AtomicReference<>()
    private final def logger

    ScriptRunner(def logger) { this.logger = logger }

    synchronized RunState currentState() { current.get() }

    synchronized RunState run(String scriptPath, List<String> args) {
        // If something is running, stop it first (per the user's requirement).
        stopInternal('replaced by new run')

        File f = new File(scriptPath)
        if (!f.exists() || !f.isFile()) {
            throw new IllegalArgumentException('Script not found: ' + scriptPath)
        }

        RunState state = new RunState()
        state.runId = 'run-' + System.currentTimeMillis()
        state.scriptPath = scriptPath
        state.args = args ?: []
        state.state = 'running'
        state.startedAt = System.currentTimeMillis()
        state.baselineWindows = new HashSet<>(Arrays.asList(Window.getWindows()))
        state.loader = new GroovyClassLoader(getClass().getClassLoader())

        current.set(state)
        logger.info('Launching script: ' + scriptPath + ' (runId=' + state.runId + ')')

        Runnable body = {
            // Redirect stdout/stderr into the log buffer while the script runs.
            PrintStream origOut = System.out
            PrintStream origErr = System.err
            PrintStream tapOut = new PrintStream(new TeeOut(origOut, state.log, 'OUT'), true)
            PrintStream tapErr = new PrintStream(new TeeOut(origErr, state.log, 'ERR'), true)
            System.setOut(tapOut)
            System.setErr(tapErr)
            try {
                def shell = new GroovyShell(state.loader, new Binding([args: state.args as String[]]))
                shell.evaluate(f)
                state.state = 'done'
            } catch (InterruptedException ie) {
                state.state = 'stopped'
                state.error = 'interrupted'
                Thread.currentThread().interrupt()
            } catch (Throwable t) {
                state.state = 'failed'
                state.error = t.toString()
                state.log.append('\n[HARNESS ERROR] ' + t.toString() + '\n')
                StringWriter sw = new StringWriter()
                t.printStackTrace(new PrintWriter(sw))
                state.log.append(sw.toString())
            } finally {
                state.finishedAt = System.currentTimeMillis()
                // Remember what this run opened so /status can list it.
                Set<Window> after = new HashSet<>(Arrays.asList(Window.getWindows()))
                after.removeAll(state.baselineWindows)
                state.openedWindows.addAll(after)
                System.setOut(origOut)
                System.setErr(origErr)
                logger.info('Script finished: ' + scriptPath + ' state=' + state.state)
            }
        } as Runnable

        Thread t = new Thread(body, 'harness-runner-' + state.runId)
        t.setDaemon(true)
        state.worker = t
        t.start()
        return state
    }

    synchronized Map stop(String reason = 'explicit stop') {
        return stopInternal(reason)
    }

    private Map stopInternal(String reason) {
        RunState s = current.get()
        if (s == null) return [state: 'idle', cleanup: [windowsDisposed: 0, sessionCancelled: false]]

        int windowsDisposed = 0
        boolean sessionCancelled = false

        // 1. Interrupt worker thread (best-effort; scripts that ignore
        // interrupts won't stop here, but the window dispose below will
        // usually unblock them).
        try {
            if (s.worker != null && s.worker.isAlive()) {
                s.worker.interrupt()
            }
        } catch (Throwable t) {
            logger.warn('Worker interrupt failed', t)
        }

        // 2. Dispose any windows the script opened after the baseline.
        try {
            Set<Window> baseline = s.baselineWindows ?: new HashSet<>()
            for (Window w : Window.getWindows()) {
                if (!baseline.contains(w) && w.isDisplayable()) {
                    try {
                        w.setVisible(false)
                        w.dispose()
                        windowsDisposed++
                    } catch (Throwable t) {
                        logger.warn('Window dispose failed', t)
                    }
                }
            }
        } catch (Throwable t) {
            logger.warn('Window enumeration failed', t)
        }

        // 3. Cancel any active SessionManager session on the active project.
        try {
            def sm = SessionManager.getInstance()
            def proj = Application.getInstance().getProject()
            if (proj != null && sm.isSessionCreated(proj)) {
                sm.cancelSession(proj)
                sessionCancelled = true
            }
        } catch (Throwable t) {
            logger.warn('Session cancel failed', t)
        }

        // 4. Drop the GroovyClassLoader reference so the GC can reclaim the
        // script's classes. (No explicit close() on older Groovy; nulling is
        // sufficient for GC.)
        s.loader = null

        if (s.state == 'running') s.state = 'stopped'
        if (s.finishedAt == null) s.finishedAt = System.currentTimeMillis()

        logger.info('Stop: reason="' + reason + '" windowsDisposed=' + windowsDisposed +
                    ' sessionCancelled=' + sessionCancelled)

        return [
            state: s.state,
            reason: reason,
            cleanup: [windowsDisposed: windowsDisposed, sessionCancelled: sessionCancelled],
        ]
    }
}

// Tee stream: writes to both the original stream and the log buffer.
class TeeOut extends java.io.OutputStream {
    private final PrintStream orig
    private final LogBuffer buf
    private final String prefix
    TeeOut(PrintStream orig, LogBuffer buf, String prefix) {
        this.orig = orig
        this.buf = buf
        this.prefix = prefix
    }
    void write(int b) {
        orig.write(b)
        buf.append(new String([(byte) b] as byte[], StandardCharsets.UTF_8))
    }
    void write(byte[] b, int off, int len) {
        orig.write(b, off, len)
        buf.append(new String(b, off, len, StandardCharsets.UTF_8))
    }
    void flush() { orig.flush() }
}

// ---- HTTP server ---------------------------------------------------------
def runner = new ScriptRunner(logger)
int port = System.getProperty('harness.port', '8765') as int
def slurper = new JsonSlurper()

def readJson = { HttpExchange ex ->
    def body = ex.requestBody.text
    return body ? slurper.parseText(body) : [:]
}

def send = { HttpExchange ex, int code, def obj ->
    String body = obj instanceof String ? obj : JsonOutput.toJson(obj)
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8)
    ex.responseHeaders.add('Content-Type',
        obj instanceof String ? 'text/plain; charset=utf-8' : 'application/json; charset=utf-8')
    ex.sendResponseHeaders(code, bytes.length)
    ex.responseBody.write(bytes)
    ex.responseBody.close()
}

def parseQuery = { String q ->
    Map<String, String> out = [:]
    if (!q) return out
    for (String kv : q.split('&')) {
        int eq = kv.indexOf('=')
        if (eq > 0) out[kv.substring(0, eq)] = java.net.URLDecoder.decode(kv.substring(eq + 1), 'UTF-8')
    }
    return out
}

HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName('127.0.0.1'), port), 0)
server.setExecutor(Executors.newFixedThreadPool(4))

server.createContext('/run', { HttpExchange ex ->
    try {
        if (ex.requestMethod != 'POST') { send(ex, 405, [error: 'POST only']); return }
        def body = readJson(ex)
        String path = body.scriptPath
        if (!path) { send(ex, 400, [error: 'scriptPath required']); return }
        List<String> argList = (body.args ?: []) as List<String>
        int waitMs = (body.waitMs ?: 0) as int
        def state = runner.run(path, argList)
        if (waitMs > 0 && state.worker != null) {
            state.worker.join(waitMs)
        }
        send(ex, 202, state.toJson())
    } catch (Throwable t) {
        logger.error('/run failed', t)
        send(ex, 500, [error: t.toString()])
    }
} as HttpHandler)

server.createContext('/stop', { HttpExchange ex ->
    try {
        def body = readJson(ex)
        def res = runner.stop((body.reason ?: 'explicit stop') as String)
        send(ex, 200, res)
    } catch (Throwable t) {
        logger.error('/stop failed', t)
        send(ex, 500, [error: t.toString()])
    }
} as HttpHandler)

server.createContext('/status', { HttpExchange ex ->
    try {
        def s = runner.currentState()
        send(ex, 200, s == null ? [state: 'idle'] : s.toJson())
    } catch (Throwable t) {
        logger.error('/status failed', t)
        send(ex, 500, [error: t.toString()])
    }
} as HttpHandler)

server.createContext('/log', { HttpExchange ex ->
    try {
        def s = runner.currentState()
        if (s == null) { send(ex, 200, ''); return }
        def q = parseQuery(ex.requestURI.query)
        String out = q.since ? s.log.since(q.since as int) : s.log.snapshot()
        send(ex, 200, out)
    } catch (Throwable t) {
        logger.error('/log failed', t)
        send(ex, 500, [error: t.toString()])
    }
} as HttpHandler)

server.createContext('/stop-harness', { HttpExchange ex ->
    try {
        logger.info('Shutdown requested via /stop-harness')
        runner.stop('harness shutdown')
        send(ex, 200, [state: 'harness-shutting-down'])
        new Thread({
            try { Thread.sleep(250) } catch (Throwable ignored) {}
            try { server.stop(1) } catch (Throwable ignored) {}
            logger.info('=== Test harness stopped ===')
        } as Runnable, 'harness-shutdown').start()
    } catch (Throwable t) {
        logger.error('/stop-harness failed', t)
        send(ex, 500, [error: t.toString()])
    }
} as HttpHandler)

// Health check — also useful for the user to confirm the harness is up.
server.createContext('/', { HttpExchange ex ->
    String path = ex.requestURI.path
    if (path == '/' || path == '/health') {
        send(ex, 200, [
            harness: 'Cameo Test Harness',
            port: port,
            endpoints: ['/run', '/stop', '/status', '/log', '/stop-harness', '/health'],
            state: runner.currentState()?.toJson() ?: [state: 'idle'],
        ])
    } else {
        send(ex, 404, [error: 'not found', path: path])
    }
} as HttpHandler)

server.start()
logger.info('Test harness listening on http://127.0.0.1:' + port)
// Show the URL in Cameo's GUI console so the user can copy it easily.
Application.getInstance().getGUILog().log(
    'Cameo Test Harness listening on http://127.0.0.1:' + port +
    '  (endpoints: /run /stop /status /log /stop-harness)'
)

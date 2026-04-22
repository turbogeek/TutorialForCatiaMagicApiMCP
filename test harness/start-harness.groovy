// ==========================================================================
// Cameo Test Harness — start-harness.groovy
//
// Run as a macro (Tools → Macros → Test Harness — Start). Starts a local
// HTTP server exposing REST endpoints for driving Groovy scripts with
// resource-cleanup on stop. Stays alive in Cameo's JVM until stopped.
//
// Endpoints (all localhost:8765 by default, all JSON bodies):
//
//   POST /run     { "scriptPath": "<abs path>", "args": [...], "waitMs": 0 }
//                 Stops any prior run (windows disposed, session cancelled,
//                 worker thread interrupted), then loads the script with a
//                 FRESH GroovyClassLoader (no stale class cache) and runs it
//                 on a worker thread. Returns 202 with { runId, state }.
//
//   POST /stop    {}   — stops the current run, cleans up windows + session.
//   GET  /status  returns run state + log length.
//   GET  /log     plain-text stdout+stderr from the current/last run.
//                 ?since=<offset> returns bytes from an offset.
//   POST /stop-harness {}  shut the HTTP server down (no System.exit).
//   GET  /health  liveness check.
//
// CRITICAL constraint: we cannot use groovy.json.JsonOutput /
// groovy.json.JsonSlurper inside Cameo. Those classes route through
// org.apache.groovy.json.internal.FastStringUtils whose SPI lookup fails
// with "Unable to load FastStringService" in the Cameo classloader.
// This is the same "Fast Strings" family the user's CLAUDE.md warns
// about. We ship a tiny hand-rolled JSON encoder/decoder instead.
// ==========================================================================

import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.awt.Window
import java.io.File
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

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

// ---- Hand-rolled JSON (FastStringUtils-free) ------------------------------
class Jzon {

    static String encode(Object o) {
        StringBuilder sb = new StringBuilder()
        write(sb, o)
        return sb.toString()
    }

    static void write(StringBuilder sb, Object o) {
        if (o == null)              { sb.append('null'); return }
        if (o instanceof Boolean)   { sb.append(o.toString()); return }
        if (o instanceof Number)    { sb.append(o.toString()); return }
        if (o instanceof Map) {
            sb.append('{')
            boolean first = true
            for (Map.Entry e : ((Map) o).entrySet()) {
                if (!first) sb.append(',')
                first = false
                writeString(sb, e.key.toString())
                sb.append(':')
                write(sb, e.value)
            }
            sb.append('}')
            return
        }
        if (o.getClass().isArray()) {
            sb.append('[')
            boolean first = true
            for (Object item : (Object[]) o) {
                if (!first) sb.append(',')
                first = false
                write(sb, item)
            }
            sb.append(']')
            return
        }
        if (o instanceof Iterable) {
            sb.append('[')
            boolean first = true
            for (Object item : (Iterable) o) {
                if (!first) sb.append(',')
                first = false
                write(sb, item)
            }
            sb.append(']')
            return
        }
        writeString(sb, o.toString())
    }

    static void writeString(StringBuilder sb, String s) {
        sb.append((char) 34)
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i)
            if (c == (char) 34)      { sb.append('\\').append((char) 34) }
            else if (c == (char) 92) { sb.append('\\').append((char) 92) }
            else if (c == (char) 10) { sb.append('\\n') }
            else if (c == (char) 13) { sb.append('\\r') }
            else if (c == (char) 9)  { sb.append('\\t') }
            else if (c == (char) 8)  { sb.append('\\b') }
            else if (c == (char) 12) { sb.append('\\f') }
            else if (c < (char) 32)  { sb.append(String.format('\\u%04x', (int) c)) }
            else                     { sb.append(c) }
        }
        sb.append((char) 34)
    }

    // --- Decoder — simple recursive-descent. Handles the shapes we receive:
    // top-level object/array, nested objects/arrays, strings, numbers,
    // booleans, null. Not a general-purpose parser.
    static Object decode(String s) {
        if (s == null) return null
        Parser p = new Parser(text: s, pos: 0)
        p.skipWs()
        Object v = p.value()
        p.skipWs()
        return v
    }

    static class Parser {
        String text
        int pos

        Object value() {
            skipWs()
            if (pos >= text.length()) throw new IllegalArgumentException('unexpected EOF')
            char c = text.charAt(pos)
            if (c == (char) 123) return object()      // '{'
            if (c == (char) 91)  return array()       // '['
            if (c == (char) 34)  return string()      // '"'
            if (c == 't' || c == 'f') return bool()
            if (c == 'n') { expect('null'); return null }
            return number()
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>()
            pos++           // consume '{'
            skipWs()
            if (pos < text.length() && text.charAt(pos) == (char) 125) { pos++; return m }
            while (true) {
                skipWs()
                String k = string()
                skipWs()
                if (text.charAt(pos) != (char) 58) throw new IllegalArgumentException('expected :')
                pos++       // consume ':'
                m[k] = value()
                skipWs()
                if (pos < text.length() && text.charAt(pos) == (char) 44) { pos++; continue }
                if (pos < text.length() && text.charAt(pos) == (char) 125) { pos++; return m }
                throw new IllegalArgumentException('expected , or }')
            }
        }

        List<Object> array() {
            List<Object> a = new ArrayList<>()
            pos++           // consume '['
            skipWs()
            if (pos < text.length() && text.charAt(pos) == (char) 93) { pos++; return a }
            while (true) {
                a.add(value())
                skipWs()
                if (pos < text.length() && text.charAt(pos) == (char) 44) { pos++; continue }
                if (pos < text.length() && text.charAt(pos) == (char) 93) { pos++; return a }
                throw new IllegalArgumentException('expected , or ]')
            }
        }

        String string() {
            if (text.charAt(pos) != (char) 34) throw new IllegalArgumentException('expected "')
            pos++
            StringBuilder sb = new StringBuilder()
            while (pos < text.length()) {
                char c = text.charAt(pos++)
                if (c == (char) 34) return sb.toString()
                if (c == (char) 92 && pos < text.length()) {
                    char e = text.charAt(pos++)
                    if      (e == (char) 34) sb.append((char) 34)
                    else if (e == (char) 92) sb.append((char) 92)
                    else if (e == '/')       sb.append('/')
                    else if (e == 'n') sb.append((char) 10)
                    else if (e == 'r') sb.append((char) 13)
                    else if (e == 't') sb.append((char) 9)
                    else if (e == 'b') sb.append((char) 8)
                    else if (e == 'f') sb.append((char) 12)
                    else if (e == 'u') {
                        String hex = text.substring(pos, pos + 4)
                        pos += 4
                        sb.append((char) Integer.parseInt(hex, 16))
                    }
                    else sb.append(e)
                } else {
                    sb.append(c)
                }
            }
            throw new IllegalArgumentException('unterminated string')
        }

        Object number() {
            int start = pos
            if (text.charAt(pos) == (char) 45) pos++  // '-'
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++
            boolean isFloat = false
            if (pos < text.length() && text.charAt(pos) == (char) 46) {
                isFloat = true
                pos++
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++
            }
            if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                isFloat = true
                pos++
                if (text.charAt(pos) == (char) 43 || text.charAt(pos) == (char) 45) pos++
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++
            }
            String t = text.substring(start, pos)
            return isFloat ? Double.parseDouble(t) : Long.parseLong(t)
        }

        Boolean bool() {
            if (text.startsWith('true', pos))  { pos += 4; return Boolean.TRUE }
            if (text.startsWith('false', pos)) { pos += 5; return Boolean.FALSE }
            throw new IllegalArgumentException('expected boolean')
        }

        void expect(String kw) {
            if (!text.startsWith(kw, pos)) throw new IllegalArgumentException('expected ' + kw)
            pos += kw.length()
        }

        void skipWs() {
            while (pos < text.length()) {
                char c = text.charAt(pos)
                if (c == (char) 32 || c == (char) 10 || c == (char) 13 || c == (char) 9) pos++
                else break
            }
        }
    }
}

// ---- Bounded log buffer ---------------------------------------------------
class LogBuffer {
    private final StringBuilder buf = new StringBuilder()
    private final int cap
    LogBuffer(int cap = 512 * 1024) { this.cap = cap }
    synchronized void append(String s) {
        buf.append(s)
        if (buf.length() > cap) buf.delete(0, buf.length() - cap)
    }
    synchronized String snapshot() { return buf.toString() }
    synchronized String since(int offset) {
        int o = Math.max(0, Math.min(offset, buf.length()))
        return buf.substring(o)
    }
    synchronized int length() { return buf.length() }
}

// ---- Run state ------------------------------------------------------------
class RunState {
    String runId
    String scriptPath
    List<String> args = []
    String state = 'idle'
    long startedAt
    Long finishedAt
    String error
    Thread worker
    Set<Window> baselineWindows = new HashSet<>()
    Set<Window> openedWindows = new HashSet<>()
    GroovyClassLoader loader
    LogBuffer log = new LogBuffer()
    Map toMap() {
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

// ---- ScriptRunner ---------------------------------------------------------
class ScriptRunner {
    private final AtomicReference<RunState> current = new AtomicReference<>()
    private final def logger
    ScriptRunner(def logger) { this.logger = logger }
    synchronized RunState currentState() { current.get() }

    synchronized RunState run(String scriptPath, List<String> args) {
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

    synchronized Map stop(String reason = 'explicit stop') { stopInternal(reason) }

    private Map stopInternal(String reason) {
        RunState s = current.get()
        if (s == null) return [state: 'idle', cleanup: [windowsDisposed: 0, sessionCancelled: false]]
        int windowsDisposed = 0
        boolean sessionCancelled = false
        try {
            if (s.worker != null && s.worker.isAlive()) s.worker.interrupt()
        } catch (Throwable t) { logger.warn('Worker interrupt failed', t) }
        try {
            Set<Window> baseline = s.baselineWindows ?: new HashSet<>()
            for (Window w : Window.getWindows()) {
                if (!baseline.contains(w) && w.isDisplayable()) {
                    try {
                        w.setVisible(false)
                        w.dispose()
                        windowsDisposed++
                    } catch (Throwable t) { logger.warn('Window dispose failed', t) }
                }
            }
        } catch (Throwable t) { logger.warn('Window enumeration failed', t) }
        try {
            def sm = SessionManager.getInstance()
            def proj = Application.getInstance().getProject()
            if (proj != null && sm.isSessionCreated(proj)) {
                sm.cancelSession(proj)
                sessionCancelled = true
            }
        } catch (Throwable t) { logger.warn('Session cancel failed', t) }
        s.loader = null
        if (s.state == 'running') s.state = 'stopped'
        if (s.finishedAt == null) s.finishedAt = System.currentTimeMillis()
        logger.info('Stop: reason="' + reason + '" windowsDisposed=' + windowsDisposed + ' sessionCancelled=' + sessionCancelled)
        return [
            state: s.state,
            reason: reason,
            cleanup: [windowsDisposed: windowsDisposed, sessionCancelled: sessionCancelled],
        ]
    }
}

class TeeOut extends java.io.OutputStream {
    private final PrintStream orig
    private final LogBuffer buf
    private final String prefix
    TeeOut(PrintStream orig, LogBuffer buf, String prefix) {
        this.orig = orig; this.buf = buf; this.prefix = prefix
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

// ---- HTTP helpers (class so the closure capture works reliably) -----------
class Http {
    static String readBody(HttpExchange ex) {
        return ex.requestBody.getText('UTF-8')
    }

    static void sendJson(HttpExchange ex, int code, Object obj) {
        String body = Jzon.encode(obj)
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8)
        ex.responseHeaders.add('Content-Type', 'application/json; charset=utf-8')
        ex.sendResponseHeaders(code, bytes.length)
        ex.responseBody.write(bytes)
        ex.responseBody.close()
    }

    static void sendText(HttpExchange ex, int code, String body) {
        byte[] bytes = (body ?: '').getBytes(StandardCharsets.UTF_8)
        ex.responseHeaders.add('Content-Type', 'text/plain; charset=utf-8')
        ex.sendResponseHeaders(code, bytes.length)
        ex.responseBody.write(bytes)
        ex.responseBody.close()
    }

    static Map<String, String> query(String q) {
        Map<String, String> out = [:]
        if (!q) return out
        for (String kv : q.split('&')) {
            int eq = kv.indexOf('=')
            if (eq > 0) out[kv.substring(0, eq)] = java.net.URLDecoder.decode(kv.substring(eq + 1), 'UTF-8')
        }
        return out
    }
}

// ---- HTTP server ---------------------------------------------------------
def runner = new ScriptRunner(logger)
int port = System.getProperty('harness.port', '8765') as int
HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName('127.0.0.1'), port), 0)
server.setExecutor(Executors.newFixedThreadPool(4))

server.createContext('/run', { HttpExchange ex ->
    try {
        if (ex.requestMethod != 'POST') { Http.sendJson(ex, 405, [error: 'POST only']); return }
        def body = Http.readBody(ex)
        def parsed = body ? Jzon.decode(body) : [:]
        String path = parsed.scriptPath
        if (!path) { Http.sendJson(ex, 400, [error: 'scriptPath required']); return }
        List<String> argList = (parsed.args ?: []) as List<String>
        int waitMs = (parsed.waitMs ?: 0L) as int
        def state = runner.run(path, argList)
        if (waitMs > 0 && state.worker != null) state.worker.join(waitMs)
        Http.sendJson(ex, 202, state.toMap())
    } catch (Throwable t) {
        logger.error('/run failed', t)
        try { Http.sendJson(ex, 500, [error: t.toString()]) } catch (Throwable ignored) {}
    }
} as HttpHandler)

server.createContext('/stop', { HttpExchange ex ->
    try {
        def body = Http.readBody(ex)
        def parsed = body ? Jzon.decode(body) : [:]
        def res = runner.stop((parsed.reason ?: 'explicit stop') as String)
        Http.sendJson(ex, 200, res)
    } catch (Throwable t) {
        logger.error('/stop failed', t)
        try { Http.sendJson(ex, 500, [error: t.toString()]) } catch (Throwable ignored) {}
    }
} as HttpHandler)

server.createContext('/status', { HttpExchange ex ->
    try {
        def s = runner.currentState()
        Http.sendJson(ex, 200, s == null ? [state: 'idle'] : s.toMap())
    } catch (Throwable t) {
        logger.error('/status failed', t)
        try { Http.sendJson(ex, 500, [error: t.toString()]) } catch (Throwable ignored) {}
    }
} as HttpHandler)

server.createContext('/log', { HttpExchange ex ->
    try {
        def s = runner.currentState()
        if (s == null) { Http.sendText(ex, 200, ''); return }
        def q = Http.query(ex.requestURI.query)
        String out = q.since ? s.log.since(q.since as int) : s.log.snapshot()
        Http.sendText(ex, 200, out)
    } catch (Throwable t) {
        logger.error('/log failed', t)
        try { Http.sendJson(ex, 500, [error: t.toString()]) } catch (Throwable ignored) {}
    }
} as HttpHandler)

server.createContext('/stop-harness', { HttpExchange ex ->
    try {
        logger.info('Shutdown requested via /stop-harness')
        runner.stop('harness shutdown')
        Http.sendJson(ex, 200, [state: 'harness-shutting-down'])
        new Thread({
            try { Thread.sleep(250) } catch (Throwable ignored) {}
            try { server.stop(1) } catch (Throwable ignored) {}
            logger.info('=== Test harness stopped ===')
        } as Runnable, 'harness-shutdown').start()
    } catch (Throwable t) {
        logger.error('/stop-harness failed', t)
        try { Http.sendJson(ex, 500, [error: t.toString()]) } catch (Throwable ignored) {}
    }
} as HttpHandler)

server.createContext('/', { HttpExchange ex ->
    try {
        String reqPath = ex.requestURI.path
        if (reqPath == '/' || reqPath == '/health') {
            def s = runner.currentState()
            Http.sendJson(ex, 200, [
                harness: 'Cameo Test Harness',
                port: port,
                endpoints: ['/run', '/stop', '/status', '/log', '/stop-harness', '/health'],
                state: s == null ? [state: 'idle'] : s.toMap(),
            ])
        } else {
            Http.sendJson(ex, 404, [error: 'not found', path: reqPath])
        }
    } catch (Throwable t) {
        logger.error('/ failed', t)
        try { Http.sendJson(ex, 500, [error: t.toString()]) } catch (Throwable ignored) {}
    }
} as HttpHandler)

server.start()
logger.info('Test harness listening on http://127.0.0.1:' + port)
Application.getInstance().getGUILog().log(
    'Cameo Test Harness listening on http://127.0.0.1:' + port +
    '  (endpoints: /run /stop /status /log /stop-harness)'
)

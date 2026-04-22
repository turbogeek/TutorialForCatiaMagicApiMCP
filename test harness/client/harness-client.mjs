#!/usr/bin/env node
/**
 * Node client for the Cameo Test Harness.
 *
 * Usage (from the repo root):
 *
 *   node "test harness/client/harness-client.mjs" run  <scriptPath> [--wait <ms>] [--arg <a>]...
 *   node "test harness/client/harness-client.mjs" stop
 *   node "test harness/client/harness-client.mjs" status
 *   node "test harness/client/harness-client.mjs" log   [--since <offset>]
 *   node "test harness/client/harness-client.mjs" stop-harness
 *   node "test harness/client/harness-client.mjs" health
 *
 * Env:
 *   HARNESS_PORT   — port the harness is listening on (default 8765)
 *   HARNESS_HOST   — host (default 127.0.0.1)
 *
 * All commands print JSON (or plain text for `log`) and exit 0 on success,
 * non-zero on transport / 4xx / 5xx failure.
 */

const HOST = process.env.HARNESS_HOST || "127.0.0.1";
const PORT = Number(process.env.HARNESS_PORT || 8765);

const BASE = `http://${HOST}:${PORT}`;

async function call(method, path, body) {
  const init = { method, headers: {} };
  if (body !== undefined) {
    init.headers["Content-Type"] = "application/json";
    init.body = JSON.stringify(body);
  }
  let res;
  try {
    res = await fetch(BASE + path, init);
  } catch (e) {
    throw new Error(
      `cannot reach harness at ${BASE}: ${e.message}\n` +
        `Is it running? In Cameo's Groovy console, run:\n` +
        `  (load "test harness/start-harness.groovy")`,
    );
  }
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} ${res.statusText}: ${text}`);
  }
  const contentType = res.headers.get("content-type") || "";
  return contentType.includes("application/json") ? JSON.parse(text) : text;
}

function parseArgs(argv) {
  const cmd = argv[0];
  const rest = argv.slice(1);
  const positional = [];
  const flags = {};
  for (let i = 0; i < rest.length; i++) {
    const a = rest[i];
    if (a.startsWith("--")) {
      const key = a.slice(2);
      const next = rest[i + 1];
      if (next === undefined || next.startsWith("--")) {
        flags[key] = true;
      } else {
        if (flags[key] === undefined) flags[key] = [];
        if (!Array.isArray(flags[key])) flags[key] = [flags[key]];
        flags[key].push(next);
        i++;
      }
    } else {
      positional.push(a);
    }
  }
  return { cmd, positional, flags };
}

async function main() {
  const { cmd, positional, flags } = parseArgs(process.argv.slice(2));
  if (!cmd) {
    console.error(
      "usage: harness-client.mjs <run|stop|status|log|stop-harness|health> ...",
    );
    process.exit(2);
  }

  let out;
  switch (cmd) {
    case "health":
      out = await call("GET", "/health");
      break;

    case "run": {
      const scriptPath = positional[0];
      if (!scriptPath) {
        console.error("run: scriptPath is required");
        process.exit(2);
      }
      const body = { scriptPath };
      const argFlags = flags.arg;
      if (argFlags) body.args = Array.isArray(argFlags) ? argFlags : [argFlags];
      const waitFlag = flags.wait;
      if (waitFlag) {
        const waitVal = Array.isArray(waitFlag) ? waitFlag[0] : waitFlag;
        body.waitMs = Number(waitVal);
      }
      out = await call("POST", "/run", body);
      break;
    }

    case "stop": {
      const body = {};
      const r = flags.reason;
      if (r) body.reason = Array.isArray(r) ? r[0] : r;
      out = await call("POST", "/stop", body);
      break;
    }

    case "status":
      out = await call("GET", "/status");
      break;

    case "log": {
      let url = "/log";
      if (flags.since) {
        const v = Array.isArray(flags.since) ? flags.since[0] : flags.since;
        url += `?since=${encodeURIComponent(String(v))}`;
      }
      out = await call("GET", url);
      break;
    }

    case "stop-harness":
      out = await call("POST", "/stop-harness", {});
      break;

    default:
      console.error(`unknown command: ${cmd}`);
      process.exit(2);
  }

  if (typeof out === "string") {
    process.stdout.write(out);
    if (!out.endsWith("\n")) process.stdout.write("\n");
  } else {
    console.log(JSON.stringify(out, null, 2));
  }
}

main().catch((e) => {
  console.error(String(e.message ?? e));
  process.exit(1);
});

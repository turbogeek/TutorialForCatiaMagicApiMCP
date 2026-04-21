import { spawn } from "node:child_process";
import fs from "node:fs/promises";
import path from "node:path";
import os from "node:os";

export type ValidateLanguage = "groovy" | "java";

export interface ValidationIssue {
  line?: number;
  column?: number;
  severity: "error" | "warning" | "info";
  message: string;
}

export interface ValidationResult {
  ok: boolean;
  language: ValidateLanguage;
  compiler: string | null; // "groovyc 5.0.1" | "javac 21.0.8" | null if unavailable
  issues: ValidationIssue[];
  stdout: string;
  stderr: string;
  lintWarnings: ValidationIssue[];
}

interface ExecResult {
  stdout: string;
  stderr: string;
  code: number | null;
}

async function exec(
  cmd: string,
  args: string[],
  opts: { cwd?: string; timeoutMs?: number } = {},
): Promise<ExecResult> {
  return new Promise((resolve, reject) => {
    const child = spawn(cmd, args, {
      cwd: opts.cwd,
      stdio: ["ignore", "pipe", "pipe"],
      shell: false,
    });
    let stdout = "";
    let stderr = "";
    child.stdout.on("data", (d) => (stdout += d.toString()));
    child.stderr.on("data", (d) => (stderr += d.toString()));
    child.on("error", reject);
    child.on("close", (code) => resolve({ stdout, stderr, code }));
    if (opts.timeoutMs) {
      setTimeout(() => {
        if (!child.killed) child.kill();
      }, opts.timeoutMs);
    }
  });
}

async function detectCompiler(
  cmd: string,
  versionArgs: string[],
): Promise<string | null> {
  try {
    const r = await exec(cmd, versionArgs, { timeoutMs: 5_000 });
    const raw = (r.stdout + " " + r.stderr).trim();
    if (raw.length === 0) return null;
    return raw.split(/\r?\n/)[0].trim();
  } catch {
    return null;
  }
}

/**
 * Lint rule #1: forbid GStrings ("..$..") when the snippet touches any
 * com.nomagic.* call site. Purely textual, no compiler needed.
 */
export function lintGroovyForGStrings(src: string): ValidationIssue[] {
  const out: ValidationIssue[] = [];
  const lines = src.split(/\r?\n/);
  const cameoTouchRe = /com\.nomagic|com\.dassault_systemes|getGUILog|SessionManager|Application\.getInstance/;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    // Double-quoted string containing $ (but ignore escaped \$).
    const gstring = /"(?:[^"\\]|\\.)*\$(?:\{[^}]+\}|[A-Za-z_])(?:[^"\\]|\\.)*"/;
    if (gstring.test(line)) {
      const nearCameo = cameoTouchRe.test(line) || cameoTouchRe.test(src);
      if (nearCameo) {
        out.push({
          line: i + 1,
          severity: "warning",
          message:
            "Possible GString at Cameo API boundary. GStringImpl is not java.lang.String; prefer single-quoted strings, '+' concatenation, or .toString() before passing to com.nomagic.* APIs.",
        });
      }
    }
  }
  return out;
}

function parseGroovycOutput(out: string): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  // groovyc error line format: "<file>: <line>: <message>\n @ line N, column M."
  // Also: "<file>:<line>: error: <message>"
  const lines = out.split(/\r?\n/);
  let pending: ValidationIssue | null = null;
  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;
    let m = line.match(/^[^:]+:\s*(\d+):\s*(.+)$/);
    if (m) {
      if (pending) issues.push(pending);
      pending = {
        line: Number(m[1]),
        severity: /warning/i.test(m[2]) ? "warning" : "error",
        message: m[2].trim(),
      };
      continue;
    }
    m = line.match(/@ line (\d+), column (\d+)\./);
    if (m && pending) {
      pending.line = Number(m[1]);
      pending.column = Number(m[2]);
    }
  }
  if (pending) issues.push(pending);
  return issues;
}

function parseJavacOutput(out: string): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  const lines = out.split(/\r?\n/);
  // javac format: "<file>:<line>: error: <message>"
  // The file path on Windows contains a drive-letter colon ("C:\..."), so we
  // match ":<line>:\s*(error|warning):\s*" anywhere in the line instead of
  // trying to consume the whole path.
  const re = /:(\d+):\s*(error|warning):\s*(.+)$/;
  for (const l of lines) {
    const m = l.match(re);
    if (m) {
      issues.push({
        line: Number(m[1]),
        severity: m[2] === "warning" ? "warning" : "error",
        message: m[3].trim(),
      });
    }
  }
  return issues;
}

export interface ValidateOptions {
  language: ValidateLanguage;
  code: string;
  compilerOverride?: string; // path to groovyc/javac
}

export async function validateScript(
  opts: ValidateOptions,
): Promise<ValidationResult> {
  const tempDir = await fs.mkdtemp(path.join(os.tmpdir(), "mcp-validate-"));
  const filename = opts.language === "groovy" ? "Snippet.groovy" : "Snippet.java";
  const filePath = path.join(tempDir, filename);
  await fs.writeFile(filePath, opts.code, "utf8");

  try {
    if (opts.language === "groovy") {
      const cmd = opts.compilerOverride ?? "groovyc";
      const compiler = await detectCompiler(cmd, ["--version"]);
      const lintWarnings = lintGroovyForGStrings(opts.code);
      if (!compiler) {
        return {
          ok: false,
          language: "groovy",
          compiler: null,
          issues: [
            {
              severity: "error",
              message: `groovyc not found on PATH (tried: ${cmd}). Install Groovy or set compilerOverride to the groovyc executable.`,
            },
          ],
          stdout: "",
          stderr: "",
          lintWarnings,
        };
      }
      const r = await exec(
        cmd,
        ["-d", tempDir, "--encoding=UTF-8", filePath],
        { cwd: tempDir, timeoutMs: 60_000 },
      );
      const combined = r.stdout + "\n" + r.stderr;
      const issues = parseGroovycOutput(combined);
      return {
        ok: r.code === 0 && issues.every((i) => i.severity !== "error"),
        language: "groovy",
        compiler,
        issues,
        stdout: r.stdout,
        stderr: r.stderr,
        lintWarnings,
      };
    } else {
      const cmd = opts.compilerOverride ?? "javac";
      const compiler = await detectCompiler(cmd, ["-version"]);
      if (!compiler) {
        return {
          ok: false,
          language: "java",
          compiler: null,
          issues: [
            {
              severity: "error",
              message: `javac not found on PATH (tried: ${cmd}). Install a JDK or set compilerOverride.`,
            },
          ],
          stdout: "",
          stderr: "",
          lintWarnings: [],
        };
      }
      const r = await exec(
        cmd,
        ["-proc:none", "-d", tempDir, "-implicit:none", filePath],
        { cwd: tempDir, timeoutMs: 60_000 },
      );
      const issues = parseJavacOutput(r.stdout + "\n" + r.stderr);
      return {
        ok: r.code === 0 && issues.every((i) => i.severity !== "error"),
        language: "java",
        compiler,
        issues,
        stdout: r.stdout,
        stderr: r.stderr,
        lintWarnings: [],
      };
    }
  } finally {
    // Clean up the temp directory on best-effort basis.
    try {
      await fs.rm(tempDir, { recursive: true, force: true });
    } catch {
      /* ignore */
    }
  }
}

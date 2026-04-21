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
    // On Windows, groovyc/javac are often shipped as .bat/.cmd wrappers that
    // the OS can only launch via the shell. shell:true also lets us resolve
    // bare names like 'groovyc' through PATH in the same way the terminal
    // does. Args here come from the MCP tool (language + temp file path),
    // not arbitrary user text, so shell-injection surface is minimal.
    const useShell = process.platform === "win32";
    // When shell:true, Node concatenates the args with spaces before handing
    // the whole string to the shell — so paths with whitespace split into
    // multiple tokens. Quote any arg that contains a space on Windows.
    const safeArgs = useShell
      ? args.map((a) => (/\s/.test(a) && !a.startsWith('"') ? `"${a}"` : a))
      : args;
    const safeCmd =
      useShell && /\s/.test(cmd) && !cmd.startsWith('"') ? `"${cmd}"` : cmd;
    const child = spawn(safeCmd, safeArgs, {
      cwd: opts.cwd,
      stdio: ["ignore", "pipe", "pipe"],
      shell: useShell,
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
    // A non-zero exit from --version / -version means the binary didn't even
    // launch correctly (on Windows with shell:true this is how a missing .bat
    // surfaces — cmd.exe prints "not recognized" to stderr and exits != 0).
    if (r.code !== 0) return null;
    const raw = (r.stdout + " " + r.stderr).trim();
    if (raw.length === 0) return null;
    const firstLine = raw.split(/\r?\n/)[0].trim();
    // Must look like a real compiler banner, not a shell error.
    if (!/groovy|javac|java(?:\s+version)?/i.test(firstLine)) return null;
    return firstLine;
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
  // groovyc error formats (file path may contain a drive-letter colon on
  // Windows — match ":<line>:" anywhere in the line rather than consuming
  // the whole path):
  //   "<file>: <line>: <message>"
  //   follow-up "@ line N, column M."  (refines position of the previous error)
  //   "<file>:<line>: error: <message>"  (alt)
  const lines = out.split(/\r?\n/);
  let pending: ValidationIssue | null = null;
  const primary = /\.groovy\s*:\s*(\d+)\s*:\s*(.+)$/;
  const altErr = /:\s*(\d+)\s*:\s*(error|warning)\s*:\s*(.+)$/i;
  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;
    let m = line.match(primary);
    if (m) {
      if (pending) issues.push(pending);
      pending = {
        line: Number(m[1]),
        severity: /warning/i.test(m[2]) ? "warning" : "error",
        message: m[2].trim(),
      };
      continue;
    }
    m = line.match(altErr);
    if (m) {
      if (pending) issues.push(pending);
      pending = {
        line: Number(m[1]),
        severity: /warning/i.test(m[2]) ? "warning" : "error",
        message: m[3].trim(),
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
  classpath?: string; // colon/semicolon-separated classpath for resolving imports
}

function cpSep(): string {
  return process.platform === "win32" ? ";" : ":";
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
      const groovyArgs = ["-d", tempDir, "--encoding=UTF-8"];
      if (opts.classpath && opts.classpath.length > 0) {
        groovyArgs.push("-cp", opts.classpath);
      }
      groovyArgs.push(filePath);
      const r = await exec(cmd, groovyArgs, {
        cwd: tempDir,
        timeoutMs: 120_000,
      });
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
      const javaArgs = ["-proc:none", "-d", tempDir, "-implicit:none"];
      if (opts.classpath && opts.classpath.length > 0) {
        javaArgs.push("-cp", opts.classpath);
      }
      javaArgs.push(filePath);
      const r = await exec(cmd, javaArgs, { cwd: tempDir, timeoutMs: 120_000 });
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

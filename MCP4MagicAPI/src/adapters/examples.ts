import fs from "node:fs/promises";
import path from "node:path";

export interface ExampleSummary {
  name: string;
  path: string;
  tags: string[];
  pluginId?: string;
  pluginName?: string;
  hasSampleModel: boolean;
}

export interface FileEntry {
  relativePath: string;
  kind: "java" | "groovy" | "xml" | "mdzip" | "jar" | "other";
}

const LANG_EXT: Record<string, FileEntry["kind"]> = {
  ".java": "java",
  ".groovy": "groovy",
  ".xml": "xml",
  ".mdzip": "mdzip",
  ".jar": "jar",
};

function classifyExtension(p: string): FileEntry["kind"] {
  const ext = path.extname(p).toLowerCase();
  return LANG_EXT[ext] ?? "other";
}

async function dirExists(p: string): Promise<boolean> {
  try {
    const s = await fs.stat(p);
    return s.isDirectory();
  } catch {
    return false;
  }
}

/**
 * Extract id, name, and hint tags from a plugin.xml without a full XML parse.
 * We only need three attributes and a couple of element names — regex is fine
 * and keeps the dependency surface small.
 */
export function parsePluginXml(xml: string): {
  id?: string;
  name?: string;
  tags: string[];
} {
  const idMatch = xml.match(/\bid\s*=\s*"([^"]+)"/);
  const nameMatch = xml.match(/\bname\s*=\s*"([^"]+)"/);
  const tags = new Set<string>();
  const elementHints: Array<[RegExp, string]> = [
    [/<action\b/i, "action"],
    [/<mainMenu\b/i, "menu"],
    [/<browserContextAMConfigurator\b/i, "browser"],
    [/<validationRule\b/i, "validation"],
    [/<diagramPainter\b/i, "diagram"],
    [/<customDiagramType\b/i, "diagram"],
    [/<hyperlink\b/i, "hyperlink"],
    [/<dependencyMatrix\b/i, "matrix"],
    [/<genericTable\b/i, "table"],
    [/<compareMergePlugin\b/i, "compare-merge"],
  ];
  for (const [re, tag] of elementHints) {
    if (re.test(xml)) tags.add(tag);
  }
  return {
    id: idMatch?.[1],
    name: nameMatch?.[1],
    tags: [...tags].sort(),
  };
}

/**
 * Tag an example by combining plugin.xml hints with folder-name keywords.
 */
function folderNameTags(folder: string): string[] {
  const lower = folder.toLowerCase();
  const out = new Set<string>();
  const pairs: Array<[string, string]> = [
    ["matrix", "matrix"],
    ["diagram", "diagram"],
    ["table", "table"],
    ["validation", "validation"],
    ["code", "codegen"],
    ["merge", "compare-merge"],
    ["compare", "compare-merge"],
    ["browser", "browser"],
    ["action", "action"],
    ["menu", "menu"],
    ["selection", "selection"],
    ["commandline", "commandline"],
    ["custom", "custom"],
    ["refactor", "refactor"],
    ["image", "image"],
    ["report", "report"],
    ["teamwork", "teamwork"],
    ["event", "event"],
    ["expression", "expression"],
    ["fileattachment", "file-attachment"],
  ];
  for (const [kw, tag] of pairs) {
    if (lower.includes(kw)) out.add(tag);
  }
  return [...out];
}

export async function listExamples(examplesRoot: string): Promise<ExampleSummary[]> {
  if (!(await dirExists(examplesRoot))) {
    throw new Error(
      `Examples root does not exist: ${examplesRoot}. Set CAMEO_EXAMPLES_PATH env var if your install is elsewhere.`,
    );
  }
  const entries = await fs.readdir(examplesRoot, { withFileTypes: true });
  const out: ExampleSummary[] = [];
  for (const e of entries) {
    if (!e.isDirectory()) continue;
    const dir = path.join(examplesRoot, e.name);
    const pluginPath = path.join(dir, "plugin.xml");
    let pluginInfo: ReturnType<typeof parsePluginXml> = { tags: [] };
    try {
      const xml = await fs.readFile(pluginPath, "utf8");
      pluginInfo = parsePluginXml(xml);
    } catch {
      /* plugin.xml absent — fine */
    }
    let hasSampleModel = false;
    try {
      const inner = await fs.readdir(dir);
      hasSampleModel = inner.some((f) => f.toLowerCase().endsWith(".mdzip"));
    } catch {
      /* ignore */
    }
    const tagSet = new Set<string>([...pluginInfo.tags, ...folderNameTags(e.name)]);
    out.push({
      name: e.name,
      path: dir,
      tags: [...tagSet].sort(),
      pluginId: pluginInfo.id,
      pluginName: pluginInfo.name,
      hasSampleModel,
    });
  }
  out.sort((a, b) => a.name.localeCompare(b.name));
  return out;
}

/**
 * Walk an example's directory tree and return every file relative to it.
 * Skips .git, build artifacts, and IDE junk.
 */
export async function listExampleFiles(
  examplesRoot: string,
  exampleName: string,
): Promise<FileEntry[]> {
  const root = path.join(examplesRoot, exampleName);
  if (!(await dirExists(root))) {
    throw new Error(`Unknown example: '${exampleName}'`);
  }
  const out: FileEntry[] = [];
  async function walk(dir: string): Promise<void> {
    const entries = await fs.readdir(dir, { withFileTypes: true });
    for (const e of entries) {
      if (e.name === ".git" || e.name === "node_modules" || e.name === "build")
        continue;
      const full = path.join(dir, e.name);
      if (e.isDirectory()) {
        await walk(full);
      } else if (e.isFile()) {
        out.push({
          relativePath: path
            .relative(root, full)
            .split(path.sep)
            .join("/"),
          kind: classifyExtension(full),
        });
      }
    }
  }
  await walk(root);
  out.sort((a, b) => a.relativePath.localeCompare(b.relativePath));
  return out;
}

const MAX_FILE_BYTES = 512 * 1024; // 512 KB safety ceiling

export async function readExampleFile(
  examplesRoot: string,
  exampleName: string,
  relativePath: string,
): Promise<{ content: string; language: FileEntry["kind"]; bytes: number }> {
  const root = path.join(examplesRoot, exampleName);
  const resolved = path.resolve(root, relativePath);
  // Guard against path traversal (../../.. escape).
  const normalizedRoot = path.resolve(root) + path.sep;
  if (!resolved.startsWith(normalizedRoot) && resolved !== path.resolve(root)) {
    throw new Error(`Path escapes the example root: ${relativePath}`);
  }
  const stat = await fs.stat(resolved);
  if (stat.size > MAX_FILE_BYTES) {
    throw new Error(
      `File too large (${stat.size} bytes > ${MAX_FILE_BYTES}); read a specific region instead.`,
    );
  }
  const content = await fs.readFile(resolved, "utf8");
  return {
    content,
    language: classifyExtension(resolved),
    bytes: stat.size,
  };
}

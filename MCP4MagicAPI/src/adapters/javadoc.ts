import fs from "node:fs/promises";
import path from "node:path";
import * as cheerio from "cheerio";

export interface JavadocMethod {
  name: string;
  returnType: string;
  signature: string; // e.g. "createSession(Project project, String name)"
  deprecated: boolean;
  description: string;
}

export interface JavadocField {
  name: string;
  type: string;
  description: string;
}

export interface JavadocClass {
  fqn: string;
  simpleName: string;
  packagePath: string;
  kind: "class" | "interface" | "enum" | "annotation" | "record";
  modifiers: string;
  extendsImplements: string;
  inheritance: string[];
  description: string;
  methods: JavadocMethod[];
  fields: JavadocField[];
}

async function dirExists(p: string): Promise<boolean> {
  try {
    return (await fs.stat(p)).isDirectory();
  } catch {
    return false;
  }
}

async function fileExists(p: string): Promise<boolean> {
  try {
    return (await fs.stat(p)).isFile();
  } catch {
    return false;
  }
}

/**
 * List every package shipped in the Javadoc, read from the element-list file
 * (a flat, line-per-package plaintext index that the Javadoc tool emits).
 */
export async function listJavadocPackages(javadocRoot: string): Promise<string[]> {
  if (!(await dirExists(javadocRoot))) {
    throw new Error(
      `Javadoc root does not exist: ${javadocRoot}. Set CAMEO_JAVADOC_PATH to override.`,
    );
  }
  const elementList = path.join(javadocRoot, "element-list");
  if (await fileExists(elementList)) {
    const raw = await fs.readFile(elementList, "utf8");
    return raw
      .split(/\r?\n/)
      .map((l) => l.trim())
      .filter((l) => l.length > 0 && !l.startsWith("#") && !l.startsWith("module:"));
  }
  // Fallback: walk directory tree for any dir containing package-summary.html.
  const packages: string[] = [];
  async function walk(dir: string, prefix: string): Promise<void> {
    const entries = await fs.readdir(dir, { withFileTypes: true });
    if (entries.some((e) => e.isFile() && e.name === "package-summary.html") && prefix) {
      packages.push(prefix);
    }
    for (const e of entries) {
      if (!e.isDirectory()) continue;
      if (e.name.startsWith(".") || e.name === "legal" || e.name === "META-INF" || e.name === "resources" || e.name === "script-dir" || e.name === "index-files") continue;
      await walk(path.join(dir, e.name), prefix ? `${prefix}.${e.name}` : e.name);
    }
  }
  await walk(javadocRoot, "");
  packages.sort();
  return packages;
}

/**
 * Return the absolute path to a class's Javadoc HTML file given its FQN,
 * e.g. 'com.nomagic.magicdraw.openapi.uml.SessionManager' ->
 *      '<root>/com/nomagic/magicdraw/openapi/uml/SessionManager.html'.
 */
export function classFqnToPath(javadocRoot: string, fqn: string): string {
  const parts = fqn.split(".");
  const cls = parts.pop();
  if (!cls) throw new Error(`Invalid FQN: ${fqn}`);
  return path.join(javadocRoot, ...parts, `${cls}.html`);
}

function cleanText(s: string): string {
  return s.replace(/\s+/g, " ").trim();
}

function detectKind(
  modifiers: string,
  $: cheerio.CheerioAPI,
): JavadocClass["kind"] {
  const h1 = $("h1.title").text();
  if (h1.includes("Interface")) return "interface";
  if (h1.includes("Enum")) return "enum";
  if (h1.includes("Annotation")) return "annotation";
  if (h1.includes("Record")) return "record";
  if (modifiers.includes("interface")) return "interface";
  return "class";
}

/**
 * Parse one Javadoc class-page HTML into a structured summary.
 */
export async function getJavadocClass(
  javadocRoot: string,
  fqn: string,
): Promise<JavadocClass> {
  const filePath = classFqnToPath(javadocRoot, fqn);
  if (!(await fileExists(filePath))) {
    throw new Error(`Javadoc page not found for ${fqn}`);
  }
  const html = await fs.readFile(filePath, "utf8");
  const $ = cheerio.load(html);

  const h1 = cleanText($("h1.title").text());
  const simpleName = h1.replace(/^(Class|Interface|Enum|Annotation Type|Record)\s+/, "").trim();
  const packagePath = cleanText($("div.header .sub-title a[href='package-summary.html']").text());

  const modifiers = cleanText($("div.type-signature .modifiers").text());
  const elementName = cleanText($("div.type-signature .element-name").text());
  const extendsImplements = cleanText($("div.type-signature .extends-implements").text());

  // Inheritance chain: each nested .inheritance div adds a level.
  const inheritance: string[] = [];
  $("div.inheritance").each((_, el) => {
    const own = $(el).clone();
    own.find("div.inheritance").remove();
    const txt = cleanText(own.text());
    if (txt) inheritance.push(txt);
  });

  const description = cleanText($("section.class-description > div.block").first().text());

  const methods = extractMembersFromTable($, "method-summary-table");
  const fields = extractFieldsFromTable($, "field-summary-table");

  return {
    fqn,
    simpleName: elementName || simpleName,
    packagePath,
    kind: detectKind(modifiers, $),
    modifiers,
    extendsImplements,
    inheritance,
    description,
    methods,
    fields,
  };
}

function extractMembersFromTable(
  $: cheerio.CheerioAPI,
  tableId: string,
): JavadocMethod[] {
  const root = $(`#${tableId}`);
  if (!root.length) return [];
  const out: JavadocMethod[] = [];
  const children = root.find(".summary-table.three-column-summary").first().children();
  if (!children.length) return [];

  // Columns come in triples: col-first, col-second, col-last. Skip the header row.
  const slots: Array<{ first?: cheerio.Cheerio<any>; second?: cheerio.Cheerio<any>; last?: cheerio.Cheerio<any> }> = [];
  let current: (typeof slots)[number] = {};
  children.each((_, el) => {
    const $el = $(el);
    if ($el.hasClass("table-header")) return;
    if ($el.hasClass("col-first")) {
      if (current.first || current.second || current.last) slots.push(current);
      current = { first: $el };
    } else if ($el.hasClass("col-second")) {
      current.second = $el;
    } else if ($el.hasClass("col-last")) {
      current.last = $el;
      slots.push(current);
      current = {};
    }
  });
  if (current.first || current.second || current.last) slots.push(current);

  for (const s of slots) {
    const returnType = s.first ? cleanText(s.first.text()) : "";
    const signature = s.second ? cleanText(s.second.text()) : "";
    if (!signature) continue;
    const lastText = s.last ? cleanText(s.last.text()) : "";
    const deprecated = s.last ? s.last.find(".deprecated-label").length > 0 : false;
    const description = lastText.replace(/^Deprecated\.\s*/i, "");
    const nameMatch = signature.match(/^([A-Za-z_<>,\s]+?)\s*\(/);
    const name = nameMatch ? nameMatch[1].trim() : signature.split("(")[0].trim();
    out.push({
      name,
      returnType,
      signature,
      deprecated,
      description,
    });
  }
  return out;
}

function extractFieldsFromTable(
  $: cheerio.CheerioAPI,
  tableId: string,
): JavadocField[] {
  const root = $(`#${tableId}`);
  if (!root.length) return [];
  const out: JavadocField[] = [];
  const children = root.find(".summary-table.three-column-summary").first().children();
  let current: { first?: cheerio.Cheerio<any>; second?: cheerio.Cheerio<any>; last?: cheerio.Cheerio<any> } = {};
  children.each((_, el) => {
    const $el = $(el);
    if ($el.hasClass("table-header")) return;
    if ($el.hasClass("col-first")) current = { first: $el };
    else if ($el.hasClass("col-second")) current.second = $el;
    else if ($el.hasClass("col-last")) {
      const type = current.first ? cleanText(current.first.text()) : "";
      const name = current.second ? cleanText(current.second.text()) : "";
      const description = cleanText($el.text());
      if (name) out.push({ name, type, description });
      current = {};
    }
  });
  return out;
}

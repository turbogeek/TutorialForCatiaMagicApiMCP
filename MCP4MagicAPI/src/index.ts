#!/usr/bin/env node
/**
 * MCP4MagicAPI entry point. Registers 22 tools across five families:
 *   - javadoc  : class/method/package search + fuzzy verification
 *   - guide    : page list/read + full-text search with disk-cached index
 *   - examples : project list/tree/read + streaming substring search
 *   - curated  : best_practice_lookup + snippet_get (modelingType-aware)
 *   - workflow : validate_script_syntax, cameo_profile_* (6 tools)
 * Connects over stdio. Runs a corpus health probe at startup and writes
 * warnings to stderr when a configured path is missing or incomplete.
 */
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { loadConfig } from "./config.js";
import {
  SnippetGetInput,
  SnippetListInput,
  getSnippet,
  listSnippets,
} from "./tools/snippets.js";
import {
  BestPracticeLookupInput,
  BestPracticeListInput,
  lookupBestPractice,
  listBestPractices,
} from "./tools/bestPractices.js";
import {
  ExampleListInput,
  ExampleListFilesInput,
  ExampleReadFileInput,
  toolExampleList,
  toolExampleListFiles,
  toolExampleReadFile,
} from "./tools/examples.js";
import {
  ExampleSearchInput,
  toolExampleSearch,
} from "./tools/exampleSearch.js";
import {
  GuideListPagesInput,
  GuideReadPageInput,
  toolGuideListPages,
  toolGuideReadPage,
} from "./tools/guide.js";
import { GuideSearchInput, toolGuideSearch } from "./tools/guideSearch.js";
import {
  JavadocListPackagesInput,
  JavadocGetClassInput,
  toolJavadocListPackages,
  toolJavadocGetClass,
} from "./tools/javadoc.js";
import {
  JavadocSearchInput,
  JavadocVerifyFqnInput,
  toolJavadocSearch,
  toolJavadocVerifyFqn,
} from "./tools/javadocSearch.js";
import { ValidateScriptInput, toolValidateScript } from "./tools/validate.js";
import {
  ProfileListInput,
  ProfileActiveInput,
  ProfileSwitchInput,
  ProfileAddInput,
  ProfileRemoveInput,
  ProfileStatusInput,
  toolProfileList,
  toolProfileActive,
  toolProfileSwitch,
  toolProfileAdd,
  toolProfileRemove,
  toolProfileStatus,
} from "./tools/profile.js";

async function main(): Promise<void> {
  const paths = await loadConfig();

  // Corpus health check at startup — surfaces misconfigured paths BEFORE
  // the first tool call (per user request). Does not hard-fail the server
  // (some tools don't need every corpus), but writes a clear warning to
  // stderr so users see it in their MCP log.
  const { inspectPaths } = await import("./adapters/profile.js");
  const health = await inspectPaths({
    javadocRoot: paths.javadocRoot,
    guideRoot: paths.guideRoot,
    examplesRoot: paths.examplesRoot,
  });
  for (const [key, entry] of Object.entries(health)) {
    if (!entry.ok) {
      process.stderr.write(
        `MCP4MagicAPI: WARNING — ${key} unhealthy at ${entry.path}: ${entry.reason}. ` +
          `Fix via env var (CAMEO_*_PATH) or cameo_profile_add / cameo_profile_switch.\n`,
      );
    }
  }
  if (paths.activeProfileName) {
    process.stderr.write(
      `MCP4MagicAPI: active profile '${paths.activeProfileName}' (${paths.apiVersion}; ${paths.modelingTypes.join(",") || "no modeling types"}).\n`,
    );
  }

  const server = new McpServer({
    name: "MCP4MagicAPI",
    version: "0.2.0",
  });

  server.registerTool(
    "snippet_get",
    {
      description:
        "Return a reusable Cameo/MagicDraw script snippet by name (e.g. 'session-wrap').",
      inputSchema: SnippetGetInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const snippet = await getSnippet(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(snippet, null, 2) }],
        structuredContent: snippet,
      };
    },
  );

  server.registerTool(
    "snippet_list",
    {
      description:
        "List all available snippet keys, optionally filtered by language.",
      inputSchema: SnippetListInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const snippets = await listSnippets(paths, args);
      const summary = snippets.map((s) => ({
        name: s.name,
        language: s.language,
        description: s.description,
      }));
      return {
        content: [{ type: "text", text: JSON.stringify(summary, null, 2) }],
        structuredContent: { snippets: summary },
      };
    },
  );

  server.registerTool(
    "best_practice_lookup",
    {
      description:
        "Return a curated best-practice card for a Cameo/MagicDraw scripting topic (e.g. 'sessions', 'no-fast-strings', 'error-reporting', 'finder', 'console-logger').",
      inputSchema: BestPracticeLookupInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const card = await lookupBestPractice(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(card, null, 2) }],
        structuredContent: card,
      };
    },
  );

  server.registerTool(
    "best_practice_list",
    {
      description:
        "List all available best-practice topics with a one-line summary each.",
      inputSchema: BestPracticeListInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async () => {
      const all = await listBestPractices(paths);
      return {
        content: [{ type: "text", text: JSON.stringify(all, null, 2) }],
        structuredContent: { topics: all },
      };
    },
  );

  server.registerTool(
    "example_list",
    {
      description:
        "List all Cameo API example projects bundled with the install, with inferred tags. Use tag or nameContains to filter.",
      inputSchema: ExampleListInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const rows = await toolExampleList(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(rows, null, 2) }],
        structuredContent: { examples: rows },
      };
    },
  );

  server.registerTool(
    "example_list_files",
    {
      description:
        "Return the file tree of one example project (paths relative to its root, with language kind).",
      inputSchema: ExampleListFilesInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const rows = await toolExampleListFiles(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(rows, null, 2) }],
        structuredContent: { files: rows },
      };
    },
  );

  server.registerTool(
    "example_read_file",
    {
      description:
        "Read a single file from one example project. Enforces a 512 KB ceiling and rejects path traversal.",
      inputSchema: ExampleReadFileInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const res = await toolExampleReadFile(paths, args);
      return {
        content: [{ type: "text", text: res.content }],
        structuredContent: res,
      };
    },
  );

  server.registerTool(
    "example_search",
    {
      description:
        "Literal substring search across all example project source. Filter by fileType (java/groovy/xml/all) or exampleFilter (folder name substring). Per-file and total caps keep output bounded.",
      inputSchema: ExampleSearchInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const matches = await toolExampleSearch(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(matches, null, 2) }],
        structuredContent: { matches },
      };
    },
  );

  server.registerTool(
    "guide_list_pages",
    {
      description:
        "List every Cameo Developer Guide page with title and labels. Use topicFilter for a case-insensitive substring match on title or label.",
      inputSchema: GuideListPagesInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const rows = await toolGuideListPages(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(rows, null, 2) }],
        structuredContent: { pages: rows },
      };
    },
  );

  server.registerTool(
    "guide_read_page",
    {
      description:
        "Read one Cameo Developer Guide page. Accepts either the filename or the bare pageId. Returns plain-text body, code blocks (with language), links, and prev/next navigation.",
      inputSchema: GuideReadPageInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const page = await toolGuideReadPage(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(page, null, 2) }],
        structuredContent: { page },
      };
    },
  );

  server.registerTool(
    "guide_search",
    {
      description:
        "Full-text search across all Cameo Developer Guide pages. Ranked by TF-IDF with title and exact-phrase boosts. Builds a lazy cache at .cache/guide-index.json.",
      inputSchema: GuideSearchInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const hits = await toolGuideSearch(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(hits, null, 2) }],
        structuredContent: { hits },
      };
    },
  );

  server.registerTool(
    "javadoc_list_packages",
    {
      description:
        "List every Java package shipped in the Cameo Open API Javadoc. Use prefix to filter.",
      inputSchema: JavadocListPackagesInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const pkgs = await toolJavadocListPackages(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(pkgs, null, 2) }],
        structuredContent: { packages: pkgs },
      };
    },
  );

  server.registerTool(
    "javadoc_get_class",
    {
      description:
        "Return structured Javadoc for one class by fully-qualified name — signature, inheritance, description, methods (with deprecation flags), and fields.",
      inputSchema: JavadocGetClassInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const klass = await toolJavadocGetClass(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(klass, null, 2) }],
        structuredContent: { class: klass },
      };
    },
  );

  server.registerTool(
    "javadoc_search",
    {
      description:
        "Fuzzy search across the Cameo Javadoc for classes, methods, fields, or packages. Reuses the pre-built type/member/package-search-index.js files that the Javadoc tool ships — equivalent to the doc-site search box.",
      inputSchema: JavadocSearchInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const hits = await toolJavadocSearch(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(hits, null, 2) }],
        structuredContent: { hits },
      };
    },
  );

  server.registerTool(
    "javadoc_verify_fqn",
    {
      description:
        "Cheap yes/no check: does this fully-qualified class name exist in the shipped Cameo Javadoc? Returns {exists, candidates, similar}. Candidates are other FQNs with the same simple name (the typical correction for a bad package path); similar are class names close to the simple name. MUST be called before emitting any com.nomagic.* / com.dassault_systemes.* import in generated code.",
      inputSchema: JavadocVerifyFqnInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const result = await toolJavadocVerifyFqn(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        structuredContent: {
          fqn: result.fqn,
          exists: result.exists,
          candidates: result.candidates,
          similar: result.similar,
        },
      };
    },
  );

  server.registerTool(
    "validate_script_syntax",
    {
      description:
        "Parse-check a Groovy or Java snippet via groovyc or javac. Returns {ok, compiler, issues[], lintWarnings[]}. Groovy results include a GString lint that warns when interpolated strings appear near Cameo API call sites.",
      inputSchema: ValidateScriptInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async (args) => {
      const result = await toolValidateScript(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        structuredContent: { result },
      };
    },
  );

  server.registerTool(
    "cameo_profile_list",
    {
      description:
        "List all saved Cameo profiles (name, apiVersion, modelingTypes) plus which is active. Returns {active, profiles[]}. Call this at session start to know which environment you are operating in.",
      inputSchema: ProfileListInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async () => {
      const r = await toolProfileList(paths);
      return {
        content: [{ type: "text", text: JSON.stringify(r, null, 2) }],
        structuredContent: r,
      };
    },
  );

  server.registerTool(
    "cameo_profile_active",
    {
      description:
        "Return the currently active profile in full (paths, apiVersion, modelingTypes), or null with guidance when none is set.",
      inputSchema: ProfileActiveInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async () => {
      const r = await toolProfileActive(paths);
      return {
        content: [{ type: "text", text: JSON.stringify(r, null, 2) }],
        structuredContent: r as Record<string, unknown>,
      };
    },
  );

  server.registerTool(
    "cameo_profile_switch",
    {
      description:
        "Activate a saved profile by name. Subsequent tool calls will resolve paths and modeling-type filters from this profile (unless env-var overrides are set).",
      inputSchema: ProfileSwitchInput.shape,
    },
    async (args) => {
      const r = await toolProfileSwitch(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(r, null, 2) }],
        structuredContent: r,
      };
    },
  );

  server.registerTool(
    "cameo_profile_add",
    {
      description:
        "Create or overwrite a profile and persist it to .config/profiles.json. Provide apiVersion, modelingTypes[], and the three corpus paths. Use activate:true to make it current.",
      inputSchema: ProfileAddInput.shape,
    },
    async (args) => {
      const r = await toolProfileAdd(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(r, null, 2) }],
        structuredContent: r as Record<string, unknown>,
      };
    },
  );

  server.registerTool(
    "cameo_profile_remove",
    {
      description:
        "Delete a saved profile. If it was active, the first remaining profile becomes active (or none).",
      inputSchema: ProfileRemoveInput.shape,
    },
    async (args) => {
      const r = await toolProfileRemove(paths, args);
      return {
        content: [{ type: "text", text: JSON.stringify(r, null, 2) }],
        structuredContent: r as Record<string, unknown>,
      };
    },
  );

  server.registerTool(
    "cameo_profile_status",
    {
      description:
        "Full health check: the currently-resolved paths (profile + env-var layering), apiVersion, modelingTypes, env-var overrides, and per-corpus filesystem probe. Call this at the start of every session — if any corpus is unhealthy, warn the user instead of letting downstream tools return empty results silently.",
      inputSchema: ProfileStatusInput.shape,
      annotations: { readOnlyHint: true, idempotentHint: true },
    },
    async () => {
      const r = await toolProfileStatus(paths);
      return {
        content: [{ type: "text", text: JSON.stringify(r, null, 2) }],
        structuredContent: r as Record<string, unknown>,
      };
    },
  );

  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((err) => {
  process.stderr.write(
    `MCP4MagicAPI fatal error: ${err instanceof Error ? err.stack : String(err)}\n`,
  );
  process.exit(1);
});

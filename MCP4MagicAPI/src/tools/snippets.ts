import fs from "node:fs/promises";
import path from "node:path";
import { z } from "zod";
import type { CameoPaths } from "../config.js";

export const SnippetSchema = z.object({
  name: z.string(),
  language: z.string(),
  description: z.string(),
  body: z.string(),
  sourceExamples: z.array(z.string()).default([]),
});
export type Snippet = z.infer<typeof SnippetSchema>;

export const SnippetStoreSchema = z.record(z.string(), SnippetSchema);
export type SnippetStore = z.infer<typeof SnippetStoreSchema>;

let cached: SnippetStore | null = null;

export async function loadSnippets(paths: CameoPaths): Promise<SnippetStore> {
  if (cached) return cached;
  const filePath = path.join(paths.dataDir, "snippets.json");
  const raw = await fs.readFile(filePath, "utf8");
  const parsed = SnippetStoreSchema.parse(JSON.parse(raw));
  cached = parsed;
  return parsed;
}

export function resetSnippetCache(): void {
  cached = null;
}

export const SnippetGetInput = z.object({
  name: z.string().describe("The snippet key, e.g. 'session-wrap'."),
});

export const SnippetListInput = z.object({
  language: z
    .string()
    .optional()
    .describe("Optional filter: only return snippets for this language."),
});

export async function getSnippet(
  paths: CameoPaths,
  args: z.infer<typeof SnippetGetInput>,
): Promise<Snippet> {
  const store = await loadSnippets(paths);
  const snippet = store[args.name];
  if (!snippet) {
    const available = Object.keys(store).sort().join(", ");
    throw new Error(
      `Unknown snippet '${args.name}'. Available: ${available}`,
    );
  }
  return snippet;
}

export async function listSnippets(
  paths: CameoPaths,
  args: z.infer<typeof SnippetListInput>,
): Promise<Snippet[]> {
  const store = await loadSnippets(paths);
  const all = Object.values(store);
  return args.language
    ? all.filter((s) => s.language === args.language)
    : all;
}

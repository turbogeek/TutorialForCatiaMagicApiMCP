/**
 * MCP tool: best-practice cards. Loads src/data/best-practices.json
 * (lookup + list); the list is filtered by the active profile's
 * modelingTypes so SysMLv2 sessions don't see UAF-only cards.
 */
import fs from "node:fs/promises";
import path from "node:path";
import { z } from "zod";
import type { CameoPaths } from "../config.js";

export const BestPracticeSchema = z.object({
  topic: z.string(),
  summary: z.string(),
  do: z.array(z.string()),
  dont: z.array(z.string()),
  snippet: z.string().nullable().optional(),
  source: z.string(),
  modelingTypes: z
    .array(z.string())
    .optional()
    .describe(
      "Scope this card to specific modeling types (UML/SysMLv1/SysMLv2/UAF/KerML). " +
        "Absent or empty means it applies universally.",
    ),
});
export type BestPractice = z.infer<typeof BestPracticeSchema>;

export const BestPracticeStoreSchema = z.record(z.string(), BestPracticeSchema);
export type BestPracticeStore = z.infer<typeof BestPracticeStoreSchema>;

let cached: BestPracticeStore | null = null;

export async function loadBestPractices(
  paths: CameoPaths,
): Promise<BestPracticeStore> {
  if (cached) return cached;
  const filePath = path.join(paths.dataDir, "best-practices.json");
  const raw = await fs.readFile(filePath, "utf8");
  const parsed = BestPracticeStoreSchema.parse(JSON.parse(raw));
  cached = parsed;
  return parsed;
}

export function resetBestPracticeCache(): void {
  cached = null;
}

export const BestPracticeLookupInput = z.object({
  topic: z
    .string()
    .describe(
      "The topic key, e.g. 'sessions', 'no-fast-strings', 'error-reporting', 'finder'.",
    ),
});

export const BestPracticeListInput = z.object({});

export async function lookupBestPractice(
  paths: CameoPaths,
  args: z.infer<typeof BestPracticeLookupInput>,
): Promise<BestPractice> {
  const store = await loadBestPractices(paths);
  const hit = store[args.topic];
  if (!hit) {
    const available = Object.keys(store).sort().join(", ");
    throw new Error(
      `Unknown best-practice topic '${args.topic}'. Available: ${available}`,
    );
  }
  return hit;
}

export async function listBestPractices(
  paths: CameoPaths,
): Promise<Array<Pick<BestPractice, "topic" | "summary" | "modelingTypes">>> {
  const store = await loadBestPractices(paths);
  const cards = Object.values(store);
  // Filter by the active profile's modelingTypes when set. A card without
  // a modelingTypes array is always applicable.
  const active = paths.modelingTypes;
  const visible =
    active.length === 0
      ? cards
      : cards.filter(
          (c) =>
            !c.modelingTypes ||
            c.modelingTypes.length === 0 ||
            c.modelingTypes.some((t) => active.includes(t as never)),
        );
  return visible.map(({ topic, summary, modelingTypes }) => ({
    topic,
    summary,
    modelingTypes,
  }));
}

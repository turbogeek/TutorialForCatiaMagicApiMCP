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
): Promise<Array<Pick<BestPractice, "topic" | "summary">>> {
  const store = await loadBestPractices(paths);
  return Object.values(store).map(({ topic, summary }) => ({ topic, summary }));
}

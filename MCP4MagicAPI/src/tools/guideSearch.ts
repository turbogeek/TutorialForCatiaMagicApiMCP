import { z } from "zod";
import type { CameoPaths } from "../config.js";
import { searchGuide } from "../adapters/guideSearch.js";

export const GuideSearchInput = z.object({
  query: z
    .string()
    .min(1)
    .describe(
      "Free-text query. Ranked by TF-IDF with title and exact-phrase boosts.",
    ),
  limit: z.number().int().positive().max(50).optional().default(10),
});

export async function toolGuideSearch(
  paths: CameoPaths,
  args: z.infer<typeof GuideSearchInput>,
) {
  return searchGuide(
    { guideRoot: paths.guideRoot, cacheDir: paths.cacheDir },
    args.query,
    { limit: args.limit },
  );
}

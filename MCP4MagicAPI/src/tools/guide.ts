/**
 * MCP tools: guide_list_pages / guide_read_page. Thin wrappers over the
 * guide HTML adapter. Page identifier accepts either filename or bare
 * numeric pageId.
 */
import { z } from "zod";
import type { CameoPaths } from "../config.js";
import { listGuidePages, readGuidePage } from "../adapters/guide.js";

export const GuideListPagesInput = z.object({
  topicFilter: z
    .string()
    .optional()
    .describe(
      "Case-insensitive substring filter on the page title or label.",
    ),
});

export const GuideReadPageInput = z.object({
  page: z
    .string()
    .describe(
      "The page filename (e.g. 'Session-management.254437443.html') or the bare page id (e.g. '254437443').",
    ),
});

export async function toolGuideListPages(
  paths: CameoPaths,
  args: z.infer<typeof GuideListPagesInput>,
) {
  const all = await listGuidePages(paths.guideRoot);
  if (!args.topicFilter) return all;
  const q = args.topicFilter.toLowerCase();
  return all.filter(
    (p) =>
      p.title.toLowerCase().includes(q) ||
      p.labels.some((l) => l.toLowerCase().includes(q)),
  );
}

export async function toolGuideReadPage(
  paths: CameoPaths,
  args: z.infer<typeof GuideReadPageInput>,
) {
  return readGuidePage(paths.guideRoot, args.page);
}

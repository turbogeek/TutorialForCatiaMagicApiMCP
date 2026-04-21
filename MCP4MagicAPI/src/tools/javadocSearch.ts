import { z } from "zod";
import type { CameoPaths } from "../config.js";
import { searchJavadoc } from "../adapters/javadocSearch.js";

export const JavadocSearchInput = z.object({
  query: z
    .string()
    .min(1)
    .describe("Case-insensitive class/member/package name fragment."),
  kind: z
    .enum(["class", "method", "field", "package", "all"])
    .optional()
    .default("all"),
  limit: z.number().int().positive().max(100).optional().default(20),
});

export async function toolJavadocSearch(
  paths: CameoPaths,
  args: z.infer<typeof JavadocSearchInput>,
) {
  return searchJavadoc(paths.javadocRoot, args.query, {
    kind: args.kind === "all" ? undefined : args.kind,
    limit: args.limit,
  });
}

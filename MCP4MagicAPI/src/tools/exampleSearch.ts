/**
 * MCP tool: example_search. Streaming literal-substring grep across
 * every text file in every bundled example project, with per-file
 * and total caps to keep output bounded.
 */
import { z } from "zod";
import type { CameoPaths } from "../config.js";
import { searchExamples } from "../adapters/examples.js";

export const ExampleSearchInput = z.object({
  query: z.string().min(1).describe("Literal substring to search for (not regex)."),
  caseSensitive: z.boolean().optional().default(false),
  fileType: z
    .enum(["java", "groovy", "xml", "all"])
    .optional()
    .default("all")
    .describe("Restrict to one file kind."),
  exampleFilter: z
    .string()
    .optional()
    .describe(
      "Case-insensitive substring filter on example folder name — e.g. 'matrix' to search only matrix-related examples.",
    ),
  maxMatchesPerFile: z.number().int().positive().max(50).optional().default(5),
  maxTotalMatches: z.number().int().positive().max(500).optional().default(200),
});

export async function toolExampleSearch(
  paths: CameoPaths,
  args: z.infer<typeof ExampleSearchInput>,
) {
  return searchExamples(paths.examplesRoot, args);
}

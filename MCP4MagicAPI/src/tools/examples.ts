/**
 * MCP tools: example_list / example_list_files / example_read_file.
 * Browse the 57 bundled Cameo API example projects. See
 * adapters/examples.ts for tag inference and the read safety ceiling.
 */
import { z } from "zod";
import type { CameoPaths } from "../config.js";
import {
  listExampleFiles,
  listExamples,
  readExampleFile,
} from "../adapters/examples.js";

export const ExampleListInput = z.object({
  tag: z
    .string()
    .optional()
    .describe("Filter to examples that include this inferred tag."),
  nameContains: z
    .string()
    .optional()
    .describe("Case-insensitive substring filter on the example folder name."),
});

export const ExampleListFilesInput = z.object({
  example: z.string().describe("The example folder name (see example_list)."),
});

export const ExampleReadFileInput = z.object({
  example: z.string().describe("The example folder name."),
  relativePath: z
    .string()
    .describe("Path relative to the example root, forward slashes."),
});

export async function toolExampleList(
  paths: CameoPaths,
  args: z.infer<typeof ExampleListInput>,
) {
  let all = await listExamples(paths.examplesRoot);
  if (args.tag) {
    const t = args.tag;
    all = all.filter((e) => e.tags.includes(t));
  }
  if (args.nameContains) {
    const q = args.nameContains.toLowerCase();
    all = all.filter((e) => e.name.toLowerCase().includes(q));
  }
  return all;
}

export async function toolExampleListFiles(
  paths: CameoPaths,
  args: z.infer<typeof ExampleListFilesInput>,
) {
  return listExampleFiles(paths.examplesRoot, args.example);
}

export async function toolExampleReadFile(
  paths: CameoPaths,
  args: z.infer<typeof ExampleReadFileInput>,
) {
  return readExampleFile(paths.examplesRoot, args.example, args.relativePath);
}

/**
 * MCP tools: javadoc_list_packages / javadoc_get_class. Structured
 * reads of the Cameo Javadoc HTML — package index + one class at a time.
 * For name-based search see tools/javadocSearch.ts.
 */
import { z } from "zod";
import type { CameoPaths } from "../config.js";
import {
  getJavadocClass,
  listJavadocPackages,
} from "../adapters/javadoc.js";

export const JavadocListPackagesInput = z.object({
  prefix: z
    .string()
    .optional()
    .describe(
      "Optional substring filter — e.g. 'com.nomagic.magicdraw' to scope the listing.",
    ),
});

export const JavadocGetClassInput = z.object({
  fqn: z
    .string()
    .describe(
      "Fully-qualified class name, e.g. 'com.nomagic.magicdraw.openapi.uml.SessionManager'.",
    ),
});

export async function toolJavadocListPackages(
  paths: CameoPaths,
  args: z.infer<typeof JavadocListPackagesInput>,
) {
  const all = await listJavadocPackages(paths.javadocRoot);
  return args.prefix ? all.filter((p) => p.includes(args.prefix!)) : all;
}

export async function toolJavadocGetClass(
  paths: CameoPaths,
  args: z.infer<typeof JavadocGetClassInput>,
) {
  return getJavadocClass(paths.javadocRoot, args.fqn);
}

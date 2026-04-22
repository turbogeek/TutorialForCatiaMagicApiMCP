/**
 * MCP tools: javadoc_search (ranked fuzzy) and javadoc_verify_fqn
 * (deterministic yes/no with correction candidates). Both are served
 * from the shipped type/member/package-search-index.js files — no HTML
 * parsing needed.
 */
import { z } from "zod";
import type { CameoPaths } from "../config.js";
import { searchJavadoc, verifyFqn } from "../adapters/javadocSearch.js";

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

export const JavadocVerifyFqnInput = z.object({
  fqn: z
    .string()
    .describe(
      "Fully-qualified class name to verify against the shipped type index, " +
        "e.g. 'com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype'. Returns " +
        "{exists, candidates, similar}. Call this before emitting any " +
        "com.nomagic.* / com.dassault_systemes.* import in generated code.",
    ),
});

export async function toolJavadocVerifyFqn(
  paths: CameoPaths,
  args: z.infer<typeof JavadocVerifyFqnInput>,
) {
  return verifyFqn(paths.javadocRoot, args.fqn);
}

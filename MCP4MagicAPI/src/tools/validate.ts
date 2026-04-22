import { z } from "zod";
import type { CameoPaths } from "../config.js";
import { validateScript } from "../adapters/validate.js";

export const ValidateScriptInput = z.object({
  language: z.enum(["groovy", "java"]),
  code: z.string().min(1),
  compilerOverride: z
    .string()
    .optional()
    .describe(
      "Absolute path to groovyc or javac when they are not on PATH.",
    ),
  classpath: z
    .string()
    .optional()
    .describe(
      "OS-separated classpath (';' on Windows, ':' elsewhere) for resolving imports. " +
        "For Cameo scripts, point at the MagicDraw lib/ folder, e.g. 'E:/Magic SW/.../lib/*'.",
    ),
  skipImportCheck: z
    .boolean()
    .optional()
    .default(false)
    .describe(
      "When true, do NOT cross-check com.nomagic.* / com.dassault_systemes.* " +
        "imports against the Javadoc. Default false — checking is nearly free " +
        "and catches the hallucination class automatically.",
    ),
});

export async function toolValidateScript(
  paths: CameoPaths,
  args: z.infer<typeof ValidateScriptInput>,
) {
  return validateScript({
    language: args.language,
    code: args.code,
    compilerOverride: args.compilerOverride,
    classpath: args.classpath,
    javadocRoot: args.skipImportCheck ? undefined : paths.javadocRoot,
  });
}

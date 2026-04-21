import { z } from "zod";
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
});

export async function toolValidateScript(
  args: z.infer<typeof ValidateScriptInput>,
) {
  return validateScript(args);
}

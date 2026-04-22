/**
 * MCP tools: cameo_profile_{list,active,switch,add,remove,status}.
 * Session-start and multi-version management. _status also returns a
 * per-corpus filesystem health probe so unhealthy setups surface before
 * downstream tools return silent empty results.
 */
import { z } from "zod";
import type { CameoPaths } from "../config.js";
import {
  addProfile,
  getActiveProfile,
  inspectPaths,
  listProfiles,
  ModelingTypeSchema,
  ProfileSchema,
  removeProfile,
  switchProfile,
} from "../adapters/profile.js";

export const ProfileListInput = z.object({});

export const ProfileActiveInput = z.object({});

export const ProfileSwitchInput = z.object({
  name: z.string().min(1).describe("Profile name to activate."),
});

export const ProfileAddInput = z.object({
  name: z.string().min(1),
  description: z.string().optional(),
  apiVersion: z
    .string()
    .describe("API release label, e.g. '2026x Refresh1' or '26xR1'."),
  modelingTypes: z
    .array(ModelingTypeSchema)
    .min(1)
    .describe(
      "Which modeling frameworks the target project uses. Controls which " +
        "best-practices / snippets are offered to the agent.",
    ),
  javadocRoot: z.string().describe("Absolute path to the Javadoc root."),
  guideRoot: z.string().describe("Absolute path to the Developer Guide root."),
  examplesRoot: z.string().describe("Absolute path to the examples/ root."),
  activate: z
    .boolean()
    .optional()
    .default(false)
    .describe(
      "If true, make this the active profile after saving. Default false " +
        "unless there is no active profile yet.",
    ),
});

export const ProfileRemoveInput = z.object({
  name: z.string().min(1),
});

export const ProfileStatusInput = z.object({});

export async function toolProfileList(paths: CameoPaths) {
  return listProfiles(paths.projectRoot);
}

export async function toolProfileActive(paths: CameoPaths) {
  const active = await getActiveProfile(paths.projectRoot);
  if (!active) {
    return {
      active: null,
      message:
        "No active profile. Using env-var overrides where set, otherwise the 26xR1 defaults. " +
        "Call cameo_profile_add to create one, or cameo_profile_switch to activate an existing profile.",
    };
  }
  return { active };
}

export async function toolProfileSwitch(
  paths: CameoPaths,
  args: z.infer<typeof ProfileSwitchInput>,
) {
  const p = await switchProfile(paths.projectRoot, args.name);
  return {
    active: p.name,
    apiVersion: p.apiVersion,
    modelingTypes: p.modelingTypes,
    note: "Profile switched. Env-var overrides (CAMEO_*_PATH) still take precedence at the tool level if set.",
  };
}

export async function toolProfileAdd(
  paths: CameoPaths,
  args: z.infer<typeof ProfileAddInput>,
) {
  const profile = ProfileSchema.parse({
    name: args.name,
    description: args.description,
    apiVersion: args.apiVersion,
    modelingTypes: args.modelingTypes,
    paths: {
      javadocRoot: args.javadocRoot,
      guideRoot: args.guideRoot,
      examplesRoot: args.examplesRoot,
    },
  });
  const store = await addProfile(paths.projectRoot, profile, {
    activate: args.activate,
  });
  return { saved: profile.name, active: store.active };
}

export async function toolProfileRemove(
  paths: CameoPaths,
  args: z.infer<typeof ProfileRemoveInput>,
) {
  const store = await removeProfile(paths.projectRoot, args.name);
  return { removed: args.name, active: store.active };
}

/**
 * Full health check: resolved paths + filesystem probe on each corpus.
 * This is what the agent should call at the start of every session — if
 * any corpus is unhealthy the agent knows NOT to rely on that tool family
 * and to warn the user.
 */
export async function toolProfileStatus(paths: CameoPaths) {
  const health = await inspectPaths({
    javadocRoot: paths.javadocRoot,
    guideRoot: paths.guideRoot,
    examplesRoot: paths.examplesRoot,
  });
  return {
    activeProfile: paths.activeProfileName,
    apiVersion: paths.apiVersion,
    modelingTypes: paths.modelingTypes,
    resolvedPaths: {
      javadocRoot: paths.javadocRoot,
      guideRoot: paths.guideRoot,
      examplesRoot: paths.examplesRoot,
    },
    health,
    envOverrides: {
      CAMEO_JAVADOC_PATH: process.env.CAMEO_JAVADOC_PATH ?? null,
      CAMEO_GUIDE_PATH: process.env.CAMEO_GUIDE_PATH ?? null,
      CAMEO_EXAMPLES_PATH: process.env.CAMEO_EXAMPLES_PATH ?? null,
    },
  };
}

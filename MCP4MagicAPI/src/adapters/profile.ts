/**
 * Profile store for multi-version / multi-modeling-type setups.
 *
 * A profile captures {apiVersion, modelingTypes[], paths{}} so a single
 * MCP install can serve several Cameo versions and several modeling-type
 * combinations (UML, SysMLv1, SysMLv2, UAF, KerML, Other). Profiles
 * persist to .config/profiles.json; one is marked active. Env vars in
 * config.ts override the active profile's paths when set.
 *
 * Also exposes inspectPaths(): a per-corpus filesystem probe (Javadoc
 * needs element-list; Guide needs index.html; Examples needs ≥1 subdir)
 * so misconfigured paths surface with a readable reason instead of
 * silent empty results.
 */
import fs from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";
import { z } from "zod";

export const ModelingTypeSchema = z.enum([
  "UML",
  "SysMLv1",
  "SysMLv2",
  "UAF",
  "KerML",
  "Other",
]);
export type ModelingType = z.infer<typeof ModelingTypeSchema>;

export const ProfileSchema = z.object({
  name: z.string().min(1),
  description: z.string().optional(),
  apiVersion: z
    .string()
    .describe("The Cameo/MagicDraw release, e.g. '2026x Refresh1' or '26xR1'."),
  modelingTypes: z
    .array(ModelingTypeSchema)
    .min(1)
    .describe(
      "Modeling frameworks enabled in the target project. Drives which " +
        "best-practices / snippets are offered.",
    ),
  paths: z
    .object({
      javadocRoot: z.string(),
      guideRoot: z.string(),
      examplesRoot: z.string(),
    })
    .describe(
      "Absolute filesystem paths to the three corpora for this profile.",
    ),
});
export type Profile = z.infer<typeof ProfileSchema>;

export const ProfileStoreSchema = z.object({
  active: z.string().nullable(),
  profiles: z.array(ProfileSchema),
});
export type ProfileStore = z.infer<typeof ProfileStoreSchema>;

export interface ProfileLocations {
  configDir: string;
  profilesFile: string;
}

function locate(projectRoot: string): ProfileLocations {
  const configDir = path.join(projectRoot, ".config");
  return {
    configDir,
    profilesFile: path.join(configDir, "profiles.json"),
  };
}

export async function loadProfileStore(
  projectRoot: string,
): Promise<ProfileStore> {
  const loc = locate(projectRoot);
  if (!existsSync(loc.profilesFile)) {
    return { active: null, profiles: [] };
  }
  const raw = await fs.readFile(loc.profilesFile, "utf8");
  const obj = JSON.parse(raw);
  return ProfileStoreSchema.parse(obj);
}

export async function saveProfileStore(
  projectRoot: string,
  store: ProfileStore,
): Promise<void> {
  const loc = locate(projectRoot);
  await fs.mkdir(loc.configDir, { recursive: true });
  await fs.writeFile(
    loc.profilesFile,
    JSON.stringify(store, null, 2) + "\n",
    "utf8",
  );
}

export async function listProfiles(projectRoot: string): Promise<{
  active: string | null;
  profiles: Array<{
    name: string;
    apiVersion: string;
    modelingTypes: ModelingType[];
  }>;
}> {
  const store = await loadProfileStore(projectRoot);
  return {
    active: store.active,
    profiles: store.profiles.map((p) => ({
      name: p.name,
      apiVersion: p.apiVersion,
      modelingTypes: p.modelingTypes,
    })),
  };
}

export async function getActiveProfile(
  projectRoot: string,
): Promise<Profile | null> {
  const store = await loadProfileStore(projectRoot);
  if (!store.active) return null;
  return store.profiles.find((p) => p.name === store.active) ?? null;
}

export async function switchProfile(
  projectRoot: string,
  name: string,
): Promise<Profile> {
  const store = await loadProfileStore(projectRoot);
  const hit = store.profiles.find((p) => p.name === name);
  if (!hit) {
    const available = store.profiles.map((p) => p.name).join(", ") || "<none>";
    throw new Error(
      `No profile named '${name}'. Available: ${available}. Use cameo_profile_add to create one.`,
    );
  }
  store.active = name;
  await saveProfileStore(projectRoot, store);
  return hit;
}

export async function addProfile(
  projectRoot: string,
  profile: Profile,
  options: { activate?: boolean } = {},
): Promise<ProfileStore> {
  // Runtime schema check — callers may pass a Profile shape without having
  // gone through ProfileSchema.parse (e.g. unit tests constructing objects
  // directly).
  const validated = ProfileSchema.parse(profile);
  const store = await loadProfileStore(projectRoot);
  const existing = store.profiles.findIndex((p) => p.name === validated.name);
  if (existing >= 0) {
    store.profiles[existing] = validated;
  } else {
    store.profiles.push(validated);
  }
  if (options.activate || store.active === null) {
    store.active = validated.name;
  }
  await saveProfileStore(projectRoot, store);
  return store;
}

export async function removeProfile(
  projectRoot: string,
  name: string,
): Promise<ProfileStore> {
  const store = await loadProfileStore(projectRoot);
  const before = store.profiles.length;
  store.profiles = store.profiles.filter((p) => p.name !== name);
  if (store.profiles.length === before) {
    throw new Error(`No profile named '${name}' to remove.`);
  }
  if (store.active === name) {
    store.active = store.profiles[0]?.name ?? null;
  }
  await saveProfileStore(projectRoot, store);
  return store;
}

/**
 * Per-corpus path status. `ok: true` means the directory exists. We check
 * for the specific fingerprints each adapter relies on (element-list for
 * Javadoc, index.html for Guide, presence of subdirs for Examples) so a
 * misconfigured path surfaces BEFORE a tool returns a confusing empty result.
 */
export interface PathHealth {
  javadocRoot: { path: string; ok: boolean; reason?: string };
  guideRoot: { path: string; ok: boolean; reason?: string };
  examplesRoot: { path: string; ok: boolean; reason?: string };
}

export async function inspectPaths(paths: {
  javadocRoot: string;
  guideRoot: string;
  examplesRoot: string;
}): Promise<PathHealth> {
  const javadocMarker = path.join(paths.javadocRoot, "element-list");
  const guideMarker = path.join(paths.guideRoot, "index.html");
  const examplesMarker = paths.examplesRoot;
  return {
    javadocRoot: await probe(paths.javadocRoot, javadocMarker, "element-list"),
    guideRoot: await probe(paths.guideRoot, guideMarker, "index.html"),
    examplesRoot: await probeDir(
      paths.examplesRoot,
      examplesMarker,
      "example subdirectories",
    ),
  };
}

async function probe(
  root: string,
  marker: string,
  markerName: string,
): Promise<{ path: string; ok: boolean; reason?: string }> {
  if (!existsSync(root)) {
    return { path: root, ok: false, reason: `root does not exist` };
  }
  try {
    const s = await fs.stat(root);
    if (!s.isDirectory()) {
      return { path: root, ok: false, reason: `root is not a directory` };
    }
  } catch (e) {
    return { path: root, ok: false, reason: `stat failed: ${(e as Error).message}` };
  }
  if (!existsSync(marker)) {
    return {
      path: root,
      ok: false,
      reason: `expected marker ${markerName} is missing`,
    };
  }
  return { path: root, ok: true };
}

async function probeDir(
  root: string,
  _marker: string,
  _markerName: string,
): Promise<{ path: string; ok: boolean; reason?: string }> {
  if (!existsSync(root)) {
    return { path: root, ok: false, reason: `root does not exist` };
  }
  try {
    const entries = await fs.readdir(root, { withFileTypes: true });
    const hasSubdir = entries.some((e) => e.isDirectory());
    if (!hasSubdir) {
      return { path: root, ok: false, reason: `no subdirectories found` };
    }
  } catch (e) {
    return { path: root, ok: false, reason: `readdir failed: ${(e as Error).message}` };
  }
  return { path: root, ok: true };
}

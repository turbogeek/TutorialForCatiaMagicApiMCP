/**
 * Runtime configuration resolver. Layers (highest wins):
 *   1. env vars: CAMEO_JAVADOC_PATH / CAMEO_GUIDE_PATH / CAMEO_EXAMPLES_PATH
 *   2. the active profile from .config/profiles.json
 *   3. hard-coded 26xR1 defaults
 * Exposes apiVersion + modelingTypes + activeProfileName on CameoPaths so
 * tools that filter by modeling type (e.g. best_practice_lookup) can do so
 * transparently.
 */
import path from "node:path";
import { fileURLToPath } from "node:url";
import { getActiveProfile, type ModelingType } from "./adapters/profile.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * Runtime-resolved paths and mode. Precedence:
 *   1) env vars (CAMEO_JAVADOC_PATH / CAMEO_GUIDE_PATH / CAMEO_EXAMPLES_PATH)
 *   2) active profile in .config/profiles.json
 *   3) hard-coded defaults for the 26xR1 install
 */
export interface CameoPaths {
  javadocRoot: string;
  guideRoot: string;
  examplesRoot: string;
  dataDir: string;
  cacheDir: string;
  logsDir: string;
  projectRoot: string;
  apiVersion: string | null;
  modelingTypes: ModelingType[];
  activeProfileName: string | null;
}

const DEFAULT_JAVADOC =
  "E:\\Magic SW\\MCSE26xR1_4_2\\openapi\\docs\\md-javadoc-2026.1.0-485-c01d52da-javadoc";
const DEFAULT_GUIDE = "E:\\Magic SW\\MCSE26xR1_4_2\\openapi\\guide\\guide";
const DEFAULT_EXAMPLES = "E:\\Magic SW\\MCSE26xR1_4_2\\openapi\\examples";
const DEFAULT_VERSION = "26xR1";

export async function loadConfig(): Promise<CameoPaths> {
  const projectRoot = path.resolve(__dirname, "..");
  const profile = await getActiveProfile(projectRoot).catch(() => null);

  const javadocRoot =
    process.env.CAMEO_JAVADOC_PATH ??
    profile?.paths.javadocRoot ??
    DEFAULT_JAVADOC;
  const guideRoot =
    process.env.CAMEO_GUIDE_PATH ??
    profile?.paths.guideRoot ??
    DEFAULT_GUIDE;
  const examplesRoot =
    process.env.CAMEO_EXAMPLES_PATH ??
    profile?.paths.examplesRoot ??
    DEFAULT_EXAMPLES;

  return {
    javadocRoot,
    guideRoot,
    examplesRoot,
    dataDir: path.join(projectRoot, "src", "data"),
    cacheDir: path.join(projectRoot, ".cache"),
    logsDir: path.join(projectRoot, "logs"),
    projectRoot,
    apiVersion: profile?.apiVersion ?? DEFAULT_VERSION,
    modelingTypes: profile?.modelingTypes ?? [],
    activeProfileName: profile?.name ?? null,
  };
}

/**
 * Synchronous variant for the (few) places that need paths without awaiting —
 * most callers should prefer the async one. Returns the same shape but never
 * consults the profile store.
 */
export function loadConfigSync(): CameoPaths {
  const projectRoot = path.resolve(__dirname, "..");
  return {
    javadocRoot: process.env.CAMEO_JAVADOC_PATH ?? DEFAULT_JAVADOC,
    guideRoot: process.env.CAMEO_GUIDE_PATH ?? DEFAULT_GUIDE,
    examplesRoot: process.env.CAMEO_EXAMPLES_PATH ?? DEFAULT_EXAMPLES,
    dataDir: path.join(projectRoot, "src", "data"),
    cacheDir: path.join(projectRoot, ".cache"),
    logsDir: path.join(projectRoot, "logs"),
    projectRoot,
    apiVersion: DEFAULT_VERSION,
    modelingTypes: [],
    activeProfileName: null,
  };
}

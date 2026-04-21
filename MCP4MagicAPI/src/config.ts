import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * Paths to the three Cameo API reference corpora.
 * Override via env vars when the MCP is launched outside the default install.
 */
export interface CameoPaths {
  javadocRoot: string;
  guideRoot: string;
  examplesRoot: string;
  dataDir: string;
  cacheDir: string;
  logsDir: string;
}

const DEFAULT_JAVADOC =
  "E:\\Magic SW\\MCSE26xR1_4_2\\openapi\\docs\\md-javadoc-2026.1.0-485-c01d52da-javadoc";
const DEFAULT_GUIDE = "E:\\Magic SW\\MCSE26xR1_4_2\\openapi\\guide\\guide";
const DEFAULT_EXAMPLES = "E:\\Magic SW\\MCSE26xR1_4_2\\openapi\\examples";

export function loadConfig(): CameoPaths {
  const projectRoot = path.resolve(__dirname, "..");
  return {
    javadocRoot: process.env.CAMEO_JAVADOC_PATH ?? DEFAULT_JAVADOC,
    guideRoot: process.env.CAMEO_GUIDE_PATH ?? DEFAULT_GUIDE,
    examplesRoot: process.env.CAMEO_EXAMPLES_PATH ?? DEFAULT_EXAMPLES,
    dataDir: path.join(projectRoot, "src", "data"),
    cacheDir: path.join(projectRoot, ".cache"),
    logsDir: path.join(projectRoot, "logs"),
  };
}

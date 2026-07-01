# Installation and Setup Instructions

This document provides instructions on how to install and configure the MCP server and test harness for Cameo/MagicDraw/CATIA Magic, along with a curated list of related repositories.

## Dependencies

Before starting, ensure you have the following dependencies installed on your system:

- **Node.js**: Version 20 or higher is required. You can download it from [nodejs.org](https://nodejs.org/).
- **Java Runtime Environment (JRE)**: The test harness requires Java. It is highly recommended to use the JRE bundled directly with your CATIA Magic / Cameo Systems Modeler installation.

## Automated Setup

We have provided automated installation scripts to streamline the setup process for the MCP server and test harness.

1. Open your terminal or command prompt in the root of this repository.
2. Run the installation script for your operating system:
   - **Windows**:
     ```powershell
     .\install.bat
     ```
   - **macOS / Linux**:
     ```bash
     ./install.sh
     ```

**What the script does:**
1. Verifies that Node.js is installed.
2. Prompts you for your CATIA Magic / Cameo installation directory to configure `JAVA_HOME`.
3. Installs dependencies (`npm install`) and builds the MCP server (`npm run build`).
4. Provides you with the exact command to add the MCP server to your AI assistant (e.g., Gemini, ChatGPT, Claude).

## Manual Setup

If you prefer or need to set up the MCP server manually, use the following commands:

```powershell
cd MCP4MagicAPI
npm install
npm run build
```

Then add the server to your AI assistant configuration using the path to `build/index.js`.

## Setting up the Cameo Test Harness

The Cameo Test Harness acts as a bridge, exposing a local REST API that allows your AI agent (or you) to deploy and run Groovy scripts directly inside Cameo's JVM.

To start the harness:
1. Ensure the harness is installed as a standard Cameo Macro.
2. In Cameo / CATIA Magic, navigate to `Tools -> Macros` and execute the harness.
3. This opens a modeless configuration dialog where you can set paths (Harness Path, Project Path, Log Path) and toggle the GUI Log Level.
4. The REST API runs on port `8765` by default, offering `/run`, `/stop`, `/status`, and `/log` endpoints.

For full details, please refer to the [Test Harness README](test%20harness/README.md).

---

## Related Repositories and Resources

Below is a curated list of related repositories in the MBSE-AI-Lab organization:

### Core Projects
- [MBSE-AI-Lab Organization](https://github.com/turbogeek/MBSE-AI-Lab)

### Validators and Best Practices
- [sysmlv2-validator](https://github.com/turbogeek/sysmlv2-validator)
  - SysMLv2 Grammar Checker and best practices.

### Cheatsheets and Examples
- [SysMLv2CheatSheet](https://github.com/turbogeek/SysMLv2CheatSheet)
  - SysMLv2 examples.
  - Test harness for loading v2 text into CATIA Magic.
  - Skill files for LLM.

### API and MCP Tutorials
- [TutorialForCatiaMagicApiMCP](https://github.com/turbogeek/TutorialForCatiaMagicApiMCP) (This repository)
  - Skills and MCP for writing Open API Code.
  - Test Harness for running Groovy scripts in CATIA Magic.

### Optional Plugins
- [SysMLv2-Editor-for-VSCode](https://github.com/turbogeek/SysMLv2-Editor-for-VSCode)
  - A plugin for VSCode or Antigravity for editing a `.sysml` or `.kerml` file.

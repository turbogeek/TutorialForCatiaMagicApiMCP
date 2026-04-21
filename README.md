# TutorialForCatiaMagicApiMCP
Tutorial For CatiaMagic Api MCP with examples to generate Groovy scripting code for MagicDraw/Cameo/CATIA Magic client API
### WARNING: This is based on instructions as of 4/21/26 and subject to change. 
### NOTICES: This is a tutorial and not supported by Dassault Systèmes. This is for educational purposes only and thus no support, guarantees, warranties, or assumption of liabilities for the content and your use of this content.
# Setup
This example used Claude to create and use the MCP. If you wish, you can skip to the running and using of the MCP to generate your own scripts or one of the examples based on our selection of prompts. This is based on a tutorial you can find here: https://modelcontextprotocol.io/docs/develop/build-server, but modified with the assumption this is a clean install on a Windows machine and using NodeJS and Typescript.

## Set up your node environment
First, let’s install Node.js and npm if you haven’t already. You can download them from nodejs.org. Verify your Node.js installation:
https://nodejs.org/en

### Verify your Node.js installation:
```
node --version
npm --version
```
For this tutorial, you’ll need Node.js version 16 or higher.
## Directory setup (where we will build the server)
```
# Note that this will run in PowerShell
# Create a new directory for our project
md MCP4MagicAPI
cd MCP4MagicAPI
# Initialize a new npm project
npm init -y
# Install dependencies
npm install @modelcontextprotocol/sdk zod@3
npm install -D @types/node typescript
# Create our files
md src
new-item src\index.ts
```
## Create package.json
```
{
  "type": "module",
  "bin": {
    "MCP4MagicAPI": "./build/index.js"
  },
  "scripts": {
    "build": "tsc && chmod 755 build/index.js"
  },
  "files": ["build"]
}
```
Now things get a little squirrely 

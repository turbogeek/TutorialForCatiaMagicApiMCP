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
## Now things get a little squirrely 
Here is the prompt I gave Claude Code:
> [!WARNING]
> Make sure you are in a good high-power  version and you have **Plan Mode** turned on (**Opus 4.7 Extra High** was used for this step so TL;DR YMMV). Note that you need to uncompress the Open API data in your installation and remove the zip files.
```
I have begun the setup for an MCP server that is hosted on Windows. This MCP server is to support the writing of Java or supported scripts like Groovy and JavaScript scripts for the Open API of MagicDraw/Cameo/Catia-Magic's Open API  (hereafter called simply Cameo API) which is is a Java-based API. I have given you a reference to the JavaDoc, Developer Guide, and Samples folders for version 26xR1 of the API: "E:\Magic SW\MCSE26xR1_4_2\openapi" The working directory of the the github project is here "E:\_Documents\git\TutorialForCatiaMagicApiMCP" and the directory for the specific config and code of the MCP is here "E:\_Documents\git\TutorialForCatiaMagicApiMCP\MCP4MagicAPI".  Please create the MCP and an agent to use it. In the agent, be sure to include best practices for scripting in the MagicDraw environment, and best practices for using sessions when using API to modify the model and for reporting errors to the MagicDraw console. Create a plan.md and after I review it, we will begin coding. Remember to be accurate, create and use test-first Iterative Testing (TDD Flow) methodology that always runs unit and integration tests on the server after each change, use good documentation, and update the README with tutorials and features along the way.
```
At this stage, you probably get a prompt that it has completed its research and is ready to create the plan. If so, tell it to proceede. If it creates the plan, review it and make any changes. 
> [!NOTE]
> The AI may ask you questions like where to put the agent files (I chose this project), adding Groovy/Java validation (I chose both), what versions to use (I took the recommended), etc. I usually select the default or widest scope. 
When you are read, let it execute the plan.
> [!WARNING]
> This eats a lot of context tokens and on normal accounts up to 20% or more of your available tokens!
## Along the way, heading off other errors
As I continued, I remembered that the system often forgets that, when using Groovy with the API, Fast Strings should not be used, and that I should remind the AI how to load dependent scripts, like the one used to log to the console. So when given an opportunity, I added this prompt:
```
I have created a scripts directory with a logger tool, "E:\_Documents\git\TutorialForCatiaMagicApiMCP\scripts\SysMLv2Logger.groovy", and an example, "E:\_Documents\git\TutorialForCatiaMagicApiMCP\scripts\RequirementSatisfyMatrixGraphics.groovy", adding snippets on the creation, setup, and usage of this console logger. Also note the sequence used in the setup for loading a script, which is the best method when in the Cameo environment. Also, forbid the use of Fast Strings in Groovy, which are generally incompatible with the strings used in the Cameo api. Then proceed with your plan.
```
## Starting the MCP locally.
Here are the instructions that the AI gave for starting the server:
```
cd MCP4MagicAPI
npm run build
claude mcp add MCP4MagicAPI node E:\_Documents\git\TutorialForCatiaMagicApiMCP\MCP4MagicAPI\build\index.js
```

## Testing
Next it is time for us to test the MCP. Here is the prompt I wrote for this task:
```
When you are done, let's try using the MCP to create the following Groovy script via this prompt: Create a Java Swing form to help the user create the parts of the system by iterating from the top block to its parts and subparts to create the Blocks for all the parts and the part attributes for subparts. When the user presses ok, the model is created with the top-level system block in a system package, the second level in a subsystems package, and all other Blocks in a library package called parts catalog. This is for SyMLv1.
```
## I could'a should'a
Sadly LLM's are not the smartest kids on the block, sadly, nore are users of LLM... Me.

I started to notice a trail of stupidity forming. In the Cameo API, the biggy is that the application is where we live. Generally, we don't just run a Groovy script with Cameo's classpath. Instead, we run Cameo and then run the script instide the tool. That is important. The tool does a lot to setup and control the model. By running a standalone script, none of that is possible. There are methods, but most are not what we are trying to do. For example, running a report. We have very specific command-line options for this task, but really, mostly just reporting. So, I interupted the LLM that was struggling with classpath to tell it this:
```
Are you aware of how to start Cameo from the command line and as part of this to run a script? We are running this particular installation: E:\Magic SW\MCSE26xR1_4_2, which means we need to run Cameo to run the script, not Groovy to run in the context of the Cameo classpath. If you can make this work, you have created the ultimate script testing tool. However. Many scripts are used by running them after Cameo launches. However, in that situation, make sure to write a separate log file that gets cleared at the start of the script, which you can use to see and correct errors.
```
Here is the response, which shows that I should have included this up front. 
>Understood — I was going down the wrong path with classpath. The Cameo CLI launcher approach is much better. Let me kill the background find and investigate the Cameo command-line runner.

>Ran 14 commands, read 4 files, used 2 tools
I have enough understanding now. The guide page [Running-programs-in-batch-mode.254437299.html](E:\Magic SW\MCSE26xR1_4_2\openapi\guide\guide\Running-programs-in-batch-mode.254437299.html) explicitly says batch mode still needs a graphical environment, but scripts can be run two ways: extending CommandLine (core) or implementing CommandLineAction (plugin-scoped). Let me:

>Add the dedicated-log-file-cleared-on-start convention to your Block Builder and to the snippet library (addresses your post-launch ask).
>Document the command-line batch runner approach as a new best practice, citing the two guide pages.
Clean up the scratch validate script.

Hooray, eating of more credits avoided. Time to update the agent to ensure this does not occur again with this prompt:
```
insert here
```
## User Smoke Testing
Well, the first run of the script failed. I gave Claude the following prompt to update any issues. There is a little more happening, but you can see that there is some fine-tuning of the approach over time that is taken with the LLM. At this point, I am not documenting the process (I hope to capture the conversation for prosperity), but I think this has given the reader what is necessary to hone in on the correct result.
```
Got this error, which means that you need to better ensure you need improvements to your MCP as "com.nomagic.uml2.ext.magicdraw.classes.mdprofiles.Stereotype" does not exist. The real item is "com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype," so there is an insert of a very bad bit of a package path. This also means there is a fatal flaw in the MCP: it does not ensure a hallucination, and your decisions are predictable based on the MCP's data lookup. That is not the only error, as I see 6 packages that do not exist. I suspect there are other issues. Also, you need to prioritize factories and helper classes, and lower the priority of ecore over other packages, as ecore is plumbing rather than core, as you can see in the examples.
```
```
Add that automatic cross-check.  Also, the MCP should return a proper error if the pointer to the installed documentation is missing or not as expected. However, it may be better to create a fast RAG of this data and a capability to add a new version. We should also have the capability to switch versions, change modeling types like SysMLv1, UAF, SysMLv2, and others depending on configuration and thus what is in the API and the profiles we have access to in the target model. This means also that the agent needs to ask up front when this information is not provided.
```
<img width="1024" height="1024" alt="Bob_thinking" src="https://github.com/user-attachments/assets/60a0bcfd-35b8-4de4-a3f2-5df72ba5ffad" />

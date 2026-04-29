# Tutorial Two: Notes on creating a Satisfy Matrix dialog using MCP and the Cameo API

Title: Using Cameo API with MCP to create SysMLv2 Satisfy Matrix dialog.

## Introduction

This tutorial will guide you through the process of using Cameo API with MCP to create a SysMLv2 Satisfy Matrix dialog.

### But why?

This is to create a real extention of the tool. Very often we want more controls, more features, differing rendering, or even corporate branding. But there are a lot of reasons to extend the capabilities of the base tool.

- Custom visualizations for specific standards.
- Custom metrics.
- Custom views and reports.
- Custom workflows.
- Extensions for custom profiles
- Custom integrations with other tools, for example Jira, Doors, Jama, Codebeamer, etc
- Analyze the model.
- Look for errors or violatiosn of standards
- Integrate AI tools directly into the tool to have it do more than its developers intended.

### Sounds like a job for an AI assistant?

Yes it does.  

### But, Can we trust it?

In this case, we need to be more specific and think like a user as well as a user interface designer, a systems engineer, and a programmer. Just because you can imagine a new capability does not mean you know anything about a good interface design, plus understanding the Cameo API is a whole nother issue.

## extending our toolbox from the last tutorial

### The need for more specificity

We need to create requirements for our tool extention in the same way as a real project.  How do we create a Satisfy Matrix dialog? What are its parts? How does it function? Can we change the look and feel of the dialog?  Can we use it to edit the nmodel or refresh when the model changes? Can we slip the axis? How do we find the elements?Can we save the config and reload it?

The question: What should we do?  We should create an tool that we can use over and over, and which can be a template for creating other tools.  It should also be an example of the things we should watch out for.  

Let's create a markdown file for our requirements and then use the AI to help us refine them.

## Createing a requirements Markdown file

We can use somebody's template, or create our own.  We'll create our own for this exercise to ensure we understand the process.  I have provided one example of what a Satisfy matrix looks like, for your reference:

TutorialOne\TutorialTwo\requirements.md

### What useful thing can we do?

### why use an AI assistance to extend our toolbox?

## Back to the goal

Use the following refined prompt to guide an AI agent (like the `cameo-api-scripter`) through the generation and validation of the tutorial model. This prompt incorporates hard-won lessons about the SysMLv2 API:

### The Prompt to create the Groovy script

```PROMT
Act as a SysMLv2 modeling expert for the CATIA Magic Open API with expertise in theGroovy Language, Java, the SysMLv2 standard, and the Cameo API. Your task is to develop, test, and validate a Groovy script that will show a Satisfy matrix with the given requirements and architecture in this file: "TutorialOne\TutorialTwo\requirements.md". Put the new scripts into ./Tutorials/TutorialTwo/scripts/version<version_number> folder, where <version_number> is the version number of the script.  Here are some importan things to keep in mind when writing your scripts:

- Wrap all model changes in a SessionManager transaction.
- Create a dedicated script logger targeting `Tutorials\TutorialTwo\logs\SatisfyMatrix.log` and use it for all diagnostic output. DO NOT rely on the GUI console alone.
- If the agent encounters `UnmodifiableEList` or structural errors, it must inspect the log, adapt its approach, and try again until the log confirms successful execution without errors.
- Ensure that the script defines element ownership precisely. (e.g. standard elements owned by the package, properties owned by usage blocks, satisfaction owned by usage).
- ONLY use the `ElementsFactory.get(namespace)` and avoid `LiteralReal`. Use `LiteralRational` for attribute defaults to prevent type mismatch errors.
```

> [!WARNING]
> Remember, the LLM is not perfect and will make mistakes.  Be prepared to correct it and guide it as needed.  It is not a magic bullet.
>

## A quote

There are known knowns. These are things we know that we know. There are known unknowns. That is to say, there are things that we know we don't know. But there are also unknown unknowns. There are things we don't know we don't know.

Donald Rumsfeld

> [!IMPORTANT}
> When the LLM makes mistakes (or you do):
> -Change the requirements and or the plan (the LLM can help)
> -Change the  skill/agent files when there are hallucinations (the LLM can help here too)  
>

### iterate to complete and test the script

## Project Setup

Follow these steps to replicate the AI's environment:

1. Create a blank SysMLv2 project in Cameo.
2. Select the `Data` or `Model` root namespace where you want the "Swimming Robot" package to live.
3. Open the Scripting Console (Tools -> Groovy) or use the MCP extension.
4. Run the script generated by the AI (e.g. `SwimmingRobot.groovy`).

## TODOs

- [ ] Finalize tutorial script and correct any markown errors or spelling mistakes.
- [ ] Export HTML presentation to PowerPoint (using Pandoc).
- [ ] Test with local Cameo instance.
- [ ] Improve the script and presentation.

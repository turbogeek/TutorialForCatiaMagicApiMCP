# Create a SysMLv1 Data Entry Tool for Use Cases

Act as a SysMLv1 modeling expert for the CATIA Magic Open API with expertise in the Groovy Language, Java, the SysMLv1 standard, and the Cameo API. Your task is to develop, test, and validate a Groovy script that creates a data entry GUI for generating Use Cases and Activity Diagrams.

**CRITICAL VERSIONING RULE:** Before writing any code, examine the `Tutorials\Lab3 -Create a SysMLv1 Data Entry Tool for Use Cases\scripts` directory to identify the highest existing version folder (e.g., `version1`, `version2`). You MUST create a new, incremented version folder (e.g., `version3`, `version4`) and put the new scripts into this new directory. **DO NOT overwrite existing scripts.**

## Task

I have set up aCameo test harness that allows me to run SysMLv2 scripts from a command line.

I want to use this to create a tool that will create a UseCase, with a data entry GUI, and create a sequence diagram for the Use Case.

The UseCase and SequenceDiagram should be created under a package named UseCases that exists in the project.

In general, the use case diagram consists of multiple actors (primary and secondary actors) and multiple use cases.  The actors have generalization relationships with each other.  The use cases have include and extend relationships with each other.  The use cases also have generalization relationships when a general use case is inherited by a specific use case.  The Activity diagram should show the interactions in more detail, with each action in a separate swimlane and the swimlanes should be ordered from left to right by the order of the actions, but usually the primary actors are on the left and the secondary actors are on the right with the system context (A Block which is the System of Interest or SOI) in the middle as it is where the main interaction of the use case is performed.

To create our digram, we could do it manually, but we are going to do it with a GUI tool that we will aid a user that doesn't know anything about use cases or how to create the diagram manually.  The GUI will guide the user through a series of questions that will allow us to create the use case and the activity diagram.  

For example, when started, the GUI asks for the name of the name of the roles of the users of the system, i.e. primary and secondary actors.  Make sure there is adiquate documentation for the user to understand what a primary and secondary actors are (need a good wiki reference that we can use without copyright issues).   Then the GUI asks for the name of the system context.  Make sure there is adiquate documentation for the user to understand what a system context is (need a good wiki reference that we can use without copyright issues).  Then the GUI asks for the names interactions that the SOI is involved in. Remember that the goal is that we are understanding what actors are attempting to accomplish with verb-noun phrases (actions). The SOI is performing an action on a subject. For example "Login to system" or "Login to the library system". Or, "Check out book", or return book,etc. Make sure these are primary actions at first, so warn the user when they use error, problem, or other negative actions.

Now we can create included and extending use cases. Present each of the base use cases to the user, ask them to identify if there is an action performed in the base use case that will make sense to be a separate use case.  If they say yes, ask them if it is an optional action (extends) or a mandatory action (includes). For example a mandatory action could be enter login credentials, and an optional action could be create new accound if a new user. For the extends capture the trigger (not currently a user) that will cause the action to be performed. Iterate fhough this in a way that allows for nesting of extends and includes relationships, i.e., an extends relationship may include another extends and/or includes relationship.  do the same for includes relationships. Also to reuse an existing use case, for example if the session times out, have an extends relationship that points to the login use case.
Next we can ask the user if there are any generalization relationships between use cases (generalizing from the base use case to the specific use case) or between actors (generalizing from the base actor to the specific actor). For example, can a manager do everything that a user does does plus manager actions? Can a maintinance person do everyhing a manager does plus their actions (use cases)?

Once the user is done, the GUI should create the UseCase and Activity diagram with swimlanes with use cases in the apropriate lane. The activity diagram should use the SysML v1.6 standard for use case diagrams and activity diagrams.  The primary actors should be on the left and the secondary actors should be on the right with the system context (A Block which is the System of Interest or SOI) in the middle as it is where the main interaction of the use case is performed.

When we are create a list of activities that match the names of the use cases, we will put each activity. If a extends relationship is present, we will put create an extends activity after the base activity. If an includes relationship is present, we will add a Call Activity action. If it is an extends place a decision node connected to a control flow with a guard with the name of the trigger (if one exists) that goes to the extends call Behavior action typed by the extends use case Activity.

## model structure

For now we will create a package in the root namespace called "Use Cases" to hold all the SysML v1.6 diagrams. Organize primary, secondary actors into sub-packages. Create the UseCase diagram and Activity diagram in that package. Note that the Activities should be in a package name Behavior.

## Execution Requirements

- Wrap all model changes in a SessionManager transaction.
- Use ElementsFactory from the selected Namespace in the containment tree.
- Create a dedicated script logger targeting `Tutorials\Lab3 -Create a SysMLv1 Data Entry Tool for Use Cases\logs\UseCaseTool.log` and use it for all diagnostic output. The logger script is located at `scripts\SysMLv2Logger.groovy` relative to the workspace root. Load it using its absolute path constructed from the workspace directory. DO NOT rely on the GUI console alone, and DO NOT use JFileChooser.
- **CRITICAL:** Event handlers (`actionPerformed`, `mouseClicked`) and rendering methods execute on the AWT Event Dispatch Thread. Any uncaught exceptions will be intercepted by MagicDraw and display an internal error dialog to the user. You MUST wrap the entire bodies of these methods in a `try-catch (Throwable t)` block and log errors using the dedicated logger!
- DO NOT use GStrings (e.g., `"${var}"`); use string concatenation or `.toString()`.
- Deploy and run the script via the Cameo Test Harness at <http://localhost:8765/run> to test it.

## Validation Loop

1. Generate the script and save it to the newly created version directory using the relative path (e.g., `Tutorials\Lab3 -Create a SysMLv1 Data Entry Tool for Use Cases\scripts\version<N>\UseCaseTool.groovy`).
2. Trigger the `/run` endpoint on the test harness, passing the absolute path to the generated script in the `scriptPath` JSON payload.
3. Check the /status and tail the /log.
4. If compilation or runtime errors occur, search the Javadoc via the MCP, fix the script and re-run.

## Reference

<https://en.wikipedia.org/wiki/Use_case>
<https://www.omgwiki.org/MBSE/lib/exe/fetch.php?media=mbse:incose_mbse_iw_2015:incose_iw2015_healthcare--chris_unger.pdf>
ISO/IEC/IEEE 2011
[sebokwiki](https://sebokwiki.org/wiki/Guide_to_the_Systems_Engineering_Body_of_Knowledge_(SEBoK))

## Files

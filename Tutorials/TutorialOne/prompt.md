Act as a SysMLv2 modeling expert for the CATIA Magic Open API. Your task is to develop, test, and validate a Groovy script that generates a "Swimming Robot" requirement and its satisfying architecture.

### Model Requirements (SysMLv2):
1. Resolve the selected namespace and create a new Package called "Swimming Robot" within it. All subsequent elements MUST be created inside this new package.
2. Create the Requirement "REQ-1" for a "Swimming Robot" (owned by the new package) with documentation: "The robot shall be able to swim in a pool."
3. Create a Part Definition "Swimming Robot" (owned by the new package).
4. Create a Part Usage "robot1" typed by the "Swimming Robot" definition (owned by the new package).
5. Create an Attribute Usage "cost" under "robot1" with type ScalarValue::Real and a default value of 500.0 (use LiteralRational).
6. Create a `SatisfyRequirementUsage` as a feature of the "robot1" usage. This satisfy usage MUST reference "REQ-1" as its satisfied requirement by creating a `ReferenceSubsetting` where the subsetted feature is the requirement, and subsetting feature is the satisfy usage, with the owner of the subsetting being the satisfy usage.

### Execution Requirements:
- Wrap all model changes in a SessionManager transaction.
- Use ElementsFactory from the selected Namespace in the containment tree.
- Load and use the SysMLv2Logger utility with a dedicated log file at "E:\_Documents\git\TutorialForCatiaMagicApiMCP\logs\SwimmingRobot.log".
- DO NOT use GStrings (e.g., "${var}"); use string concatenation or .toString().
- Deploy and run the script via the Cameo Test Harness at http://localhost:8765/run.

### Validation Loop:
1. Generate the script and save it to "C:\Users\DBR2\Desktop\SwimmingRobot\SwimmingRobot.groovy".
2. Trigger the /run endpoint on the test harness.
3. Check the /status and tail the /log.
4. If compilation or runtime errors occur (e.g., "unable to resolve class"), search the Javadoc via the MCP, fix the script, and re-run.
5. Report the final containment tree structure in your summary.

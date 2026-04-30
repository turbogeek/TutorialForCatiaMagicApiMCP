Act as a SysMLv2 modeling expert for the CATIA Magic Open API with expertise in the Groovy Language, Java, the SysMLv2 standard, and the Cameo API. Your task is to develop, test, and validate a Groovy script that will show a Satisfy matrix with the given requirements and architecture in this file: "Tutorials\TutorialTwo\requirements.md". Put the new scripts into ./Tutorials/TutorialTwo/scripts/version<version_number> folder, where <version_number> is the version number of the script.  Here are some important things to keep in mind when writing your scripts:

- Wrap all model changes in a SessionManager transaction (if mutating the model, though the matrix is read-only).
- Create a dedicated script logger targeting `Tutorials\TutorialTwo\logs\SatisfyMatrix.log` and use it for all diagnostic output. DO NOT rely on the GUI console alone.
- **CRITICAL:** Event handlers (`actionPerformed`, `mouseClicked`) and rendering methods (`paintComponent`) execute on the AWT Event Dispatch Thread. Any uncaught exceptions will be intercepted by MagicDraw and display an internal error dialog to the user. You MUST wrap the entire bodies of these methods in a `try-catch (Throwable t)` block and log errors using the dedicated logger!
- Deploy and run the script via the Cameo Test Harness at http://localhost:8765/run to test it.
- If the agent encounters `UnmodifiableEList` or structural errors, it must inspect the log, adapt its approach, and try again until the log confirms successful execution without errors.
- Ensure that the script defines element ownership precisely. (e.g. standard elements owned by the package, properties owned by usage blocks, satisfaction owned by usage).
- ONLY use the `ElementsFactory.get(namespace)` and avoid `LiteralReal`. Use `LiteralRational` for attribute defaults to prevent type mismatch errors.
- Optimize the rendering loop and data structures (like pre-calculating implied relationships) to ensure GUI repaints remain highly performant for large matrices.
- Ensure visual indicators for BOTH direct and implied relationships are arrows drawn at a 45-degree angle pointing up-right, differentiated by color.

### Validation Loop:
1. Generate the script and save it to the desired directory.
2. Trigger the /run endpoint on the test harness.
3. Check the /status and tail the /log.
4. If compilation or runtime errors occur, fix the script and re-run.

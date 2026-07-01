# Lab 4: Creating SysML Models Programmatically

Act as a Cameo Open API expert. Your task is to develop, test, and validate two Groovy scripts that programmatically create entirely new projects (models) for SysMLv2 and SysMLv1 using the Open API.

**CRITICAL VERSIONING RULE:** Before writing any code, examine the `Tutorials\Lab4 - Create New Models\scripts` directory to identify the highest existing version folder (e.g., `version1`, `version2`). You MUST create a new, incremented version folder (e.g., `version3`, `version4`) and put the new scripts into this new directory. **DO NOT overwrite existing scripts.**

## Requirements

You will execute this task in two distinct phases. **You MUST pause between phases.**

### Phase 1: SysMLv2 Model Creation

1. Research the Open API (using your MCP tools) to find how to create a new project/model programmatically initialized for **SysMLv2**. (Hint: This may involve specifying the correct project descriptor or template).
2. Write a Groovy script that creates and opens a new SysMLv2 project.
3. In this new model, generate a comprehensive model of a simple "Vending Machine". Include the following SysMLv2 elements:
    - **Requirement**: The Vending Machine shall be able to accept coins and dispense products.
   - **Parts/Part Definitions**: The Vending Machine itself, and sub-parts like a Coin Acceptor, Dispenser, and Selection Keypad.
   - **Ports**: Interface points between the Coin Acceptor and Dispenser.
   - **Connections/Bindings**: Connect the ports to show how the components interact.
   - **Attributes**: A `price` or `capacity` attribute on the Vending Machine.
   - **Constraints**: A constraint on the Vending Machine, for example that the price shall be a positive value and be able to make change for a dollar.
   - **Views**: Create SysMLv2 `View` and `Viewpoint` elements (or general views) that use `Expose` relationships to expose all the structural and behavioral elements in the model correctly. Include a requirement table view.
4. Save the script to the versioned directory as `CreateSysMLv2Model.groovy`.
5. Deploy and run the script via the Cameo Test Harness at `http://localhost:8765/run`.
6. Check the logs and status to verify the model was created successfully.
7. **STOP AND PAUSE.** Do not proceed to Phase 2 until the user explicitly prompts you to proceed.

### Phase 2: SysMLv1 Model Creation

1. Once the user prompts you to proceed, research how to create a new project/model initialized for **SysMLv1**.
2. Write a Groovy script that creates and opens a new SysMLv1 project.
3. In this new model, create a model of the same "Vending Machine". Include the following SysMLv1 elements:
    - **Requirement**: The Vending Machine shall be able to accept coins and dispense products.
    - **Blocks**: The main Vending Machine block, and part properties for Coin Acceptor, Dispenser, and Keypad.
   - **Ports & Interfaces**: Flow ports or proxy ports between the internal parts.
   - **Connectors**: Connect the ports inside the Vending Machine block.
   - **Value Properties**: Attributes like `price` or `capacity`.
   - **Diagrams**: Programmatically generate at least one **Block Definition Diagram (BDD)** showing the composition, and one **Internal Block Diagram (IBD)** showing the internal structure and connections. Ensure the elements are visually exposed on the diagrams.
   **Requirement tables**: Create requirement table with the scope set to the requirement package.
4. Save the script to the versioned directory as `CreateSysMLv1Model.groovy`.
5. Deploy and run the script via the Cameo Test Harness.
6. Check the logs and status to verify the model was created successfully.

## Execution Requirements

- Use the `ProjectManager` or equivalent Open API classes to create the new projects.
- Load and use the `SysMLv2Logger` utility to log the progress. The logger script is located at `scripts\SysMLv2Logger.groovy` relative to the workspace root. Load it using its absolute path constructed from the workspace directory. Set up a dedicated log file at `Tutorials\Lab4 - Create New Models\logs\ModelCreation.log`. DO NOT use JFileChooser.
- DO NOT use GStrings (e.g., "${var}"); use string concatenation or .toString().
- Wrap any relevant model mutations in a SessionManager transaction.

## Validation Loop

1. Generate the scripts and save them using the relative path in the new version directory.
2. Trigger the `/run` endpoint on the test harness.
3. Tail the `/log` to verify successful execution.
4. If compilation or runtime errors occur, search the Javadoc or Guide via the MCP, fix the script, and re-run.

## Technical Hints & Best Practices

- **SysMLv2 Project Creation**: Use `ProjectManager.getInstance().createProject(ProjectDescriptor...)` with the correct SysMLv2 descriptor or load a `.mdzip` template, and remember that `project.getPrimaryProject().getModel()` expects a `com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package`, whereas SysMLv2 root models cast to `com.dassault_systemes.modeler.sysml.sysmlv2.xmi.Project`.
- **SysMLv2 Stereotypes**: SysMLv2 relies on `Usage` and `Definition` pairs rather than standard UML Stereotypes.
- **Diagram Instantiation**: `ModelElementsManager.createDiagram` returns a `Diagram` *model element*. To interact with its UI representation via `PresentationElementsManager`, fetch the presentation element using `project.getDiagram(diagramModel)`.
- **GenericTableManager Scope**: Utility APIs like `GenericTableManager.setScope` require the `Diagram` *model element* as the target, not the `DiagramPresentationElement`.
- **Diagram Types**: When creating SysML diagrams programmatically, use standard string identifiers: `"SysML Block Definition Diagram"`, `"SysML Internal Block Diagram"`, `"SysML Activity Diagram"`, `"Requirement Table"` (SysMLv1) or `"gv"`, `"rt"` (SysMLv2).
- **Containment via setOwner**: Always define structural containment explicitly using `.setOwner(parent)` on new elements rather than manipulating the internal EMF collections (e.g. avoid `parent.getOwnedAttribute().add(child)`).
- **Connector Drawing**: `PresentationElementsManager.createPathElement` may throw casting exceptions for generic undirected `Connector`s lacking strict `client`/`supplier` definitions. Wrap these calls in a `try-catch` to allow the rest of your layout routines (like `Layouting.layout()`) to succeed gracefully.

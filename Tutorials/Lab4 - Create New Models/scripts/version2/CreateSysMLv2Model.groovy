import com.dassault_systemes.modeler.kerml.libraries.standard.ScalarValuesLibrary
import com.dassault_systemes.modeler.kerml.model.FeatureTypings
import com.dassault_systemes.modeler.sysml.model.ElementsFactory
import com.dassault_systemes.modeler.sysml.model.sysml.*
import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.nomagic.magicdraw.core.project.ProjectsManager
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Namespace
import java.io.File

String workspaceDir = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP'
File loggerFile = new File(workspaceDir + '\\scripts\\SysMLv2Logger.groovy')
def LoggerClass = new GroovyClassLoader(getClass().getClassLoader()).parseClass(loggerFile)

String logPath = workspaceDir + '\\Tutorials\\Lab4 - Create New Models\\logs\\ModelCreation.log'
def logger = LoggerClass.newInstance('CreateSysMLv2Model', new File(logPath))

logger.info("Starting SysMLv2 Vending Machine model creation...")

boolean sessionCreated = false
Project project = null

try {
    ProjectsManager projectsManager = Application.getInstance().getProjectsManager()
    
    String templatePath = 'E:\\Magic SW\\CMSoS26xR1pr\\templates\\SysML v2\\SysML v2.mdszip'
    
    logger.info("Creating project from template: " + templatePath)
    project = projectsManager.createProjectFromTemplate(templatePath)
    
    if (project == null) {
        logger.error("Failed to create SysMLv2 project.")
        return
    }

    SessionManager.getInstance().createSession(project, "Generate Vending Machine Model")
    sessionCreated = true

    ElementsFactory factory = ElementsFactory.get((com.dassault_systemes.modeler.foundation.project.ModelElementProject) project)
    
    // Find the root KerML Namespace using the browser tree
    def rootKerMLNamespace = null
    def browser = Application.getInstance().getMainFrame().getBrowser()
    if (browser != null && browser.getActiveTree() != null) {
        def rootNode = browser.getActiveTree().getRootNode()
        if (rootNode.getChildCount() > 0) {
            def child = rootNode.getChildAt(0)
            if (child.getChildCount() > 0) {
                def grandchild = child.getChildAt(0).getUserObject()
                if (grandchild instanceof com.dassault_systemes.modeler.kerml.model.kerml.Namespace) {
                    rootKerMLNamespace = grandchild
                }
            }
        }
    }
    
    if (rootKerMLNamespace == null) {
        logger.warn("Could not find root KerML Namespace in browser tree!")
    }

    // 1. Package
    def vendingPkg = factory.createPackage()
    vendingPkg.setDeclaredName("Vending Machine System")
    if (rootKerMLNamespace != null) {
        vendingPkg.setOwner(rootKerMLNamespace)
    }
    logger.info("Created Package: Vending Machine System")

    def scalarValuesLib = null
    try {
        if (rootKerMLNamespace != null && rootKerMLNamespace instanceof com.nomagic.magicdraw.uml.BaseElement) {
            scalarValuesLib = ScalarValuesLibrary.getInstance((com.nomagic.magicdraw.uml.BaseElement) rootKerMLNamespace)
        } else {
            scalarValuesLib = ScalarValuesLibrary.getInstance((com.nomagic.magicdraw.uml.BaseElement) vendingPkg)
        }
    } catch (Exception e) {
        logger.warn("Failed to get ScalarValuesLibrary: " + e.getMessage())
    }

    // 2. Requirement
    def req1 = factory.createRequirementUsage()
    req1.setOwner(vendingPkg)
    req1.setDeclaredName("REQ-1: Vending Machine shall accept coins and dispense products.")
    logger.info("Created Requirement: REQ-1")

    // 3. Part Definitions
    def vendingMachineDef = factory.createPartDefinition()
    vendingMachineDef.setOwner(vendingPkg)
    vendingMachineDef.setDeclaredName("Vending Machine")

    def coinAcceptorDef = factory.createPartDefinition()
    coinAcceptorDef.setOwner(vendingPkg)
    coinAcceptorDef.setDeclaredName("Coin Acceptor")

    def dispenserDef = factory.createPartDefinition()
    dispenserDef.setOwner(vendingPkg)
    dispenserDef.setDeclaredName("Dispenser")

    def keypadDef = factory.createPartDefinition()
    keypadDef.setOwner(vendingPkg)
    keypadDef.setDeclaredName("Selection Keypad")
    logger.info("Created Part Definitions")

    // 4. Part Usages inside Vending Machine
    def coinAcceptorUsage = factory.createPartUsage()
    coinAcceptorUsage.setOwner(vendingMachineDef)
    coinAcceptorUsage.setDeclaredName("coinAcceptor")
    FeatureTypings.addType(coinAcceptorUsage, coinAcceptorDef)

    def dispenserUsage = factory.createPartUsage()
    dispenserUsage.setOwner(vendingMachineDef)
    dispenserUsage.setDeclaredName("dispenser")
    FeatureTypings.addType(dispenserUsage, dispenserDef)

    def keypadUsage = factory.createPartUsage()
    keypadUsage.setOwner(vendingMachineDef)
    keypadUsage.setDeclaredName("keypad")
    FeatureTypings.addType(keypadUsage, keypadDef)
    logger.info("Created internal Part Usages")

    // 5. Ports
    try {
        def coinSlot = factory.createPortUsage()
        coinSlot.setOwner(coinAcceptorDef)
        coinSlot.setDeclaredName("coinSlot")

        def itemOutput = factory.createPortUsage()
        itemOutput.setOwner(dispenserDef)
        itemOutput.setDeclaredName("itemOutput")
        logger.info("Created Ports")
    } catch(Exception e) { logger.warn("Failed creating ports: " + e.getMessage()) }

    // 6. Connections (Binding)
    try {
        def binding = factory.createBindingConnectorAsUsage()
        binding.setOwner(vendingMachineDef)
        binding.setDeclaredName("paymentToDispenser")
        logger.info("Created Connector")
    } catch(Exception e) { logger.warn("Failed creating connector: " + e.getMessage()) }

    // 7. Attributes
    try {
        def priceAttr = factory.createAttributeUsage()
        priceAttr.setOwner(vendingMachineDef)
        priceAttr.setDeclaredName("price")
        if (scalarValuesLib != null && scalarValuesLib.Real() != null) {
            FeatureTypings.addType(priceAttr, scalarValuesLib.Real().getElement())
        }
        logger.info("Created Attribute: price")
    } catch(Exception e) { logger.warn("Failed creating attribute: " + e.getMessage()) }

    // 8. Constraints
    try {
        def priceConstraint = factory.createConstraintUsage()
        priceConstraint.setOwner(vendingMachineDef)
        priceConstraint.setDeclaredName("Price must be positive")
        logger.info("Created Constraint")
    } catch(Exception e) { logger.warn("Failed creating constraint: " + e.getMessage()) }

    // Helper to find an element by name recursively
    def findNamedElement = { def root, String name, def closure ->
        if (root == null) return null
        def n = null
        if (root instanceof com.dassault_systemes.modeler.kerml.model.kerml.Element) {
            n = root.getDeclaredName()
        } else if (root instanceof com.nomagic.magicdraw.uml.BaseElement) {
            n = root.getHumanName()
            if (n != null && n.contains(" ")) n = n.substring(n.lastIndexOf(" ") + 1) // Strip type prefix if any
        }
        if (n == name || (root instanceof com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement && ((com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement)root).getName() == name)) {
            return root
        }
        for (def child : root.getOwnedElement()) {
            def found = closure(child, name, closure)
            if (found != null) return found
        }
        return null
    }

    def findTypeByName = { Project p, String name ->
        // In SysML v2, the primary root is often obtained from the browser tree
        def _browser = com.nomagic.magicdraw.core.Application.getInstance().getMainFrame().getBrowser()
        if (_browser != null && _browser.getActiveTree() != null) {
            def rootNode = _browser.getActiveTree().getRootNode()
            if (rootNode.getChildCount() > 0) {
                def child = rootNode.getChildAt(0)
                if (child.getChildCount() > 0) {
                    def _rootKerMLNamespace = child.getChildAt(0).getUserObject()
                    def found = findNamedElement(_rootKerMLNamespace, name, findNamedElement)
                    if (found != null) return found
                }
            }
        }
        return null
    }

    // 9. Views & Viewpoints
    try {
        def vp = factory.createViewpointUsage()
        vp.setOwner(vendingPkg)
        vp.setDeclaredName("System Viewpoint")

        def view = factory.createViewUsage()
        view.setOwner(vendingPkg)
        view.setDeclaredName("Vending Machine Structure View")
        
        // Find gv
        def gvType = findTypeByName(project, "gv")
        if (gvType != null && gvType instanceof com.dassault_systemes.modeler.kerml.model.kerml.Type) {
            FeatureTypings.addType(view, (com.dassault_systemes.modeler.kerml.model.kerml.Type) gvType)
            logger.info("Assigned type 'gv' to Structure View")
        } else {
            logger.warn("Could not find type 'gv' for Structure View")
        }

        // Expose the vendingPkg in the view
        def expose = factory.createNamespaceExpose()
        expose.setOwner(view)
        expose.setImportedNamespace(vendingPkg)
        logger.info("Exposed Vending Machine System in Structure View")

        logger.info("Created View and Viewpoint")
    } catch(Exception e) { logger.warn("Failed creating views: " + e.getMessage(), e) }

    // 10. Requirement Table View
    try {
        def reqView = factory.createViewUsage()
        reqView.setOwner(vendingPkg)
        reqView.setDeclaredName("Requirement Table View")
        
        // Find rt
        def rtType = findTypeByName(project, "rt")
        if (rtType == null) rtType = findTypeByName(project, "Requirements Table")
        
        if (rtType != null && rtType instanceof com.dassault_systemes.modeler.kerml.model.kerml.Type) {
            FeatureTypings.addType(reqView, (com.dassault_systemes.modeler.kerml.model.kerml.Type) rtType)
            logger.info("Assigned type 'rt' to Requirement Table View")
        } else {
            logger.warn("Could not find type 'rt' for Requirement Table View")
        }

        // Expose the vendingPkg in the requirement view
        def reqExpose = factory.createNamespaceExpose()
        reqExpose.setOwner(reqView)
        reqExpose.setImportedNamespace(vendingPkg)
        logger.info("Exposed Vending Machine System in Requirement Table")

        logger.info("Created Requirement Table View")
    } catch(Exception e) { logger.warn("Failed creating requirement view: " + e.getMessage(), e) }

    SessionManager.getInstance().closeSession(project)
    logger.info("Successfully generated SysMLv2 Vending Machine model.")

} catch (Exception e) {
    logger.error("Exception occurred: " + e.toString(), e)
    if (sessionCreated && project != null) {
        SessionManager.getInstance().cancelSession(project)
    }
} finally {
    logger.info("Finished SysMLv2 model creation script execution.")
}

import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.core.project.ProjectsManager
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.Connector
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.ConnectorEnd
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdports.Port
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
import com.nomagic.uml2.impl.ElementsFactory
import com.nomagic.magicdraw.sysml.util.SysMLProfile

import java.io.File

String workspaceDir = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP'
File loggerFile = new File(workspaceDir + '\\scripts\\SysMLv2Logger.groovy')
def LoggerClass = new GroovyClassLoader(getClass().getClassLoader()).parseClass(loggerFile)

String logPath = workspaceDir + '\\Tutorials\\Lab4 - Create New Models\\logs\\SysMLv1Creation.log'
def logger = LoggerClass.newInstance('CreateSysMLv1Model', new File(logPath))

logger.info("Starting SysMLv1 Vending Machine model creation...")

try {
    ProjectsManager projectsManager = Application.getInstance().getProjectsManager()
    
    // SysML v1 template path
    String templatePath = 'E:\\Magic SW\\CMSoS26xR1pr\\templates\\SysML v1\\SysML v1.mdzip'
    File templateFile = new File(templatePath)
    if (!templateFile.exists()) {
        logger.error("SysMLv1 template not found at: " + templatePath)
        return
    }
    
    logger.info("Creating project from template: " + templatePath)
    Project project = projectsManager.createProjectFromTemplate(templatePath)
    if (project == null) {
        logger.error("Failed to create SysMLv1 project.")
        return
    }
    
    SessionManager.getInstance().createSession(project, "Generate SysMLv1 Model")
    
    ElementsFactory factory = project.getElementsFactory()
    ModelElementsManager diagramMgr = ModelElementsManager.getInstance()
    SysMLProfile sysmlProfile = SysMLProfile.getInstance(project)
    
    def root = project.getModel()
    
    // 1. Create Package
    Package vendingPkg = factory.createPackageInstance()
    vendingPkg.setName("Vending Machine System")
    vendingPkg.setOwner(root)
    logger.info("Created Package: Vending Machine System")
    
    // 2. Create Requirement
    Class req1 = factory.createClassInstance()
    req1.setName("REQ-1")
    req1.setOwner(vendingPkg)
    StereotypesHelper.addStereotype(req1, sysmlProfile.getRequirement())
    // Set text property
    StereotypesHelper.setStereotypePropertyValue(req1, sysmlProfile.getRequirement(), "Text", "The vending machine shall dispense a product when payment is received.")
    logger.info("Created Requirement: REQ-1")

    // 3. Create Blocks
    Class vendingMachineBlock = factory.createClassInstance()
    vendingMachineBlock.setName("Vending Machine")
    vendingMachineBlock.setOwner(vendingPkg)
    StereotypesHelper.addStereotype(vendingMachineBlock, sysmlProfile.getBlock())
    
    Class coinSlotBlock = factory.createClassInstance()
    coinSlotBlock.setName("Coin Slot")
    coinSlotBlock.setOwner(vendingPkg)
    StereotypesHelper.addStereotype(coinSlotBlock, sysmlProfile.getBlock())

    Class dispenserBlock = factory.createClassInstance()
    dispenserBlock.setName("Product Dispenser")
    dispenserBlock.setOwner(vendingPkg)
    StereotypesHelper.addStereotype(dispenserBlock, sysmlProfile.getBlock())
    logger.info("Created Blocks")

    // 4. Create Part Properties
    Property coinSlotPart = factory.createPropertyInstance()
    coinSlotPart.setName("coinSlot")
    coinSlotPart.setType(coinSlotBlock)
    coinSlotPart.setAggregation(com.nomagic.uml2.ext.magicdraw.classes.mdkernel.AggregationKindEnum.COMPOSITE)
    coinSlotPart.setOwner(vendingMachineBlock)

    Property dispenserPart = factory.createPropertyInstance()
    dispenserPart.setName("dispenser")
    dispenserPart.setType(dispenserBlock)
    dispenserPart.setAggregation(com.nomagic.uml2.ext.magicdraw.classes.mdkernel.AggregationKindEnum.COMPOSITE)
    dispenserPart.setOwner(vendingMachineBlock)
    logger.info("Created Part Properties")

    // 5. Create Value Properties
    // Use Real and String from standard UML/SysML primitive types if available, else just leave untyped for demo
    Property priceProp = factory.createPropertyInstance()
    priceProp.setName("price")
    priceProp.setOwner(vendingMachineBlock)
    
    // Set ValueProperty stereotype
    def valuePropertyStereo = StereotypesHelper.getStereotype(project, "ValueProperty")
    if (valuePropertyStereo != null) {
        StereotypesHelper.addStereotype(priceProp, valuePropertyStereo)
    }
    
    logger.info("Created Value Properties")

    // 6. Create Proxy Ports
    // Create an InterfaceBlock to type the ProxyPorts
    Class paymentInterface = factory.createClassInstance()
    paymentInterface.setName("PaymentInterface")
    paymentInterface.setOwner(vendingPkg)
    def interfaceBlockStereo = StereotypesHelper.getStereotype(project, "InterfaceBlock")
    if (interfaceBlockStereo != null) {
        StereotypesHelper.addStereotype(paymentInterface, interfaceBlockStereo)
    }

    Port vmPort = factory.createPortInstance()
    vmPort.setName("paymentPort")
    vmPort.setType(paymentInterface)
    vmPort.setOwner(vendingMachineBlock)
    StereotypesHelper.addStereotype(vmPort, sysmlProfile.getProxyPort())

    Port slotPort = factory.createPortInstance()
    slotPort.setName("insertPort")
    slotPort.setType(paymentInterface)
    slotPort.setOwner(coinSlotBlock)
    StereotypesHelper.addStereotype(slotPort, sysmlProfile.getProxyPort())
    logger.info("Created Proxy Ports")

    // 7. Create Connector between VM port and Coin Slot port
    Connector connector = factory.createConnectorInstance()
    connector.setOwner(vendingMachineBlock)
    logger.info("Connector ID: " + connector.getID())
    
    ConnectorEnd end1 = factory.createConnectorEndInstance()
    end1.setRole(vmPort)
    end1.set_connectorOfEnd(connector)
    
    ConnectorEnd end2 = factory.createConnectorEndInstance()
    end2.setRole(slotPort)
    end2.setPartWithPort(coinSlotPart)
    end2.set_connectorOfEnd(connector)
    logger.info("Created Connector")

    // 8. Create Satisfy Relationship
    com.nomagic.uml2.ext.magicdraw.classes.mddependencies.Abstraction satisfy = factory.createAbstractionInstance()
    satisfy.setOwner(vendingPkg)
    satisfy.getClient().add(vendingMachineBlock)
    satisfy.getSupplier().add(req1)
    StereotypesHelper.addStereotype(satisfy, sysmlProfile.getSatisfy())
    logger.info("Satisfy ID: " + satisfy.getID())

    // 9. Generate Diagrams
    try {
        com.nomagic.magicdraw.openapi.uml.PresentationElementsManager pem = com.nomagic.magicdraw.openapi.uml.PresentationElementsManager.getInstance()

        com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Diagram bddModel = ModelElementsManager.getInstance().createDiagram("SysML Block Definition Diagram", vendingPkg)
        bddModel.setName("Vending Machine BDD")
        com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement bddDiagram = project.getDiagram(bddModel)
        pem.createShapeElement(vendingMachineBlock, bddDiagram)
        pem.createShapeElement(coinSlotBlock, bddDiagram)
        pem.createShapeElement(dispenserBlock, bddDiagram)
        com.nomagic.magicdraw.uml.symbols.layout.Layouting.layout(bddDiagram)
        logger.info("Created and populated BDD")

        com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Diagram ibdModel = ModelElementsManager.getInstance().createDiagram("SysML Internal Block Diagram", vendingMachineBlock)
        ibdModel.setName("Vending Machine IBD")
        com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement ibdDiagram = project.getDiagram(ibdModel)
        def blockShape = pem.createShapeElement(vendingMachineBlock, ibdDiagram)
        def coinShape = pem.createShapeElement(coinSlotPart, blockShape)
        def dispenserShape = pem.createShapeElement(dispenserPart, blockShape)
        def vmPortShape = pem.createShapeElement(vmPort, blockShape)
        def slotPortShape = pem.createShapeElement(slotPort, coinShape)
        try {
            pem.createPathElement(connector, vmPortShape, slotPortShape)
        } catch (Exception e) {
            try {
                pem.createPathElement(connector, slotPortShape, vmPortShape)
            } catch (Exception ex) {
                logger.warn("Could not draw connector path on IBD: " + ex.getMessage())
            }
        }
        com.nomagic.magicdraw.uml.symbols.layout.Layouting.layout(ibdDiagram)
        logger.info("Created and populated IBD")

        // 9.1 Create Activity & Activity Diagram
        com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.Activity activity = factory.createActivityInstance()
        activity.setName("Operate Vending Machine")
        activity.setOwner(vendingPkg)
        com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.InitialNode initialNode = factory.createInitialNodeInstance()
        initialNode.setOwner(activity)
        com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ActivityFinalNode finalNode = factory.createActivityFinalNodeInstance()
        finalNode.setOwner(activity)
        com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Diagram actModel = ModelElementsManager.getInstance().createDiagram("SysML Activity Diagram", activity)
        actModel.setName("Operate Activity")
        com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement actDiagram = project.getDiagram(actModel)
        pem.createShapeElement(initialNode, actDiagram)
        pem.createShapeElement(finalNode, actDiagram)
        com.nomagic.magicdraw.uml.symbols.layout.Layouting.layout(actDiagram)
        logger.info("Created and populated Activity Diagram")

        // 9.2 Create Requirement Table
        com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Diagram reqTableModel = ModelElementsManager.getInstance().createDiagram("Requirement Table", vendingPkg)
        reqTableModel.setName("Requirements Table")
        com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement reqTable = project.getDiagram(reqTableModel)
        com.nomagic.generictable.GenericTableManager.setScope(reqTableModel, java.util.Arrays.asList(vendingPkg))
        logger.info("Created Requirement Table")
    } catch (Exception e) {
        logger.warn("Failed to create IBD: " + e.getMessage())
    }

    SessionManager.getInstance().closeSession(project)
    logger.info("Successfully generated SysMLv1 Vending Machine model.")
    
} catch (Exception e) {
    logger.error("Exception occurred: " + e.toString(), e)
} finally {
    logger.info("Finished SysMLv1 model creation script execution.")
}

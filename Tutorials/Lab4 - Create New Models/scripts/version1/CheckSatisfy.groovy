import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.core.project.ProjectsManager
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.Connector
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.ConnectorEnd
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdports.Port
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
import com.nomagic.uml2.impl.ElementsFactory
import com.nomagic.magicdraw.sysml.util.SysMLProfile

ProjectsManager projectsManager = Application.getInstance().getProjectsManager()
Project project = projectsManager.createProjectFromTemplate("E:\\Magic SW\\CMSoS26xR1pr\\templates\\SysML v1\\SysML v1.mdzip")
SessionManager.getInstance().createSession(project, "Generate SysMLv1 Model")

ElementsFactory factory = project.getElementsFactory()
ModelElementsManager diagramMgr = ModelElementsManager.getInstance()
SysMLProfile sysmlProfile = SysMLProfile.getInstance(project)

def root = project.getModel()

Package vendingPkg = factory.createPackageInstance()
vendingPkg.setName("Vending Machine System")
vendingPkg.setOwner(root)

Class req1 = factory.createClassInstance()
req1.setName("REQ-1")
req1.setOwner(vendingPkg)
StereotypesHelper.addStereotype(req1, sysmlProfile.getRequirement())
StereotypesHelper.setStereotypePropertyValue(req1, sysmlProfile.getRequirement(), "Text", "The vending machine shall dispense a product when payment is received.")

Class vendingMachineBlock = factory.createClassInstance()
vendingMachineBlock.setName("Vending Machine")
vendingMachineBlock.setOwner(vendingPkg)
StereotypesHelper.addStereotype(vendingMachineBlock, sysmlProfile.getBlock())

com.nomagic.uml2.ext.magicdraw.classes.mddependencies.Abstraction satisfy = factory.createAbstractionInstance()
satisfy.setOwner(vendingPkg)
com.nomagic.uml2.ext.jmi.helpers.CoreHelper.setSupplierAndClient(satisfy, req1, vendingMachineBlock)
StereotypesHelper.addStereotype(satisfy, sysmlProfile.getSatisfy())

SessionManager.getInstance().closeSession(project)

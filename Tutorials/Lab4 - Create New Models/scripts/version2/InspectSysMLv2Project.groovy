import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.core.project.ProjectsManager
import java.io.File

String workspaceDir = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP'
File loggerFile = new File(workspaceDir + '\\scripts\\SysMLv2Logger.groovy')
def LoggerClass = new GroovyClassLoader(getClass().getClassLoader()).parseClass(loggerFile)
String logPath = workspaceDir + '\\Tutorials\\Lab4 - Create New Models\\logs\\Inspect.log'
def logger = LoggerClass.newInstance('InspectScript', new File(logPath))

try {
    ProjectsManager projectsManager = Application.getInstance().getProjectsManager()
    String templatePath = 'E:\\Magic SW\\CMSoS26xR1pr\\templates\\SysML v2\\SysML v2.mdszip'
    Project project = projectsManager.createProjectFromTemplate(templatePath)
    
    // Helper to find an element by name recursively
    def findNamedElement = { def root, def closure ->
        if (root == null) return
        def n = null
        if (root instanceof com.dassault_systemes.modeler.kerml.model.kerml.Element) {
            n = root.getDeclaredName()
            def sn = root.getDeclaredShortName()
            if (n != null || sn != null) {
                if (n == "General View" || sn == "gv" || n == "Requirement Table" || sn == "rt" || n == "gv" || n == "rt" || n == "GeneralView" || n == "RequirementTable") {
                    logger.info("Found match! Name: " + n + ", ShortName: " + sn + " | Class: " + root.getClass().getName())
                }
            }
        }
        for (def child : root.getOwnedElement()) {
            closure(child, closure)
        }
    }

    def browser = com.nomagic.magicdraw.core.Application.getInstance().getMainFrame().getBrowser()
    if (browser != null && browser.getActiveTree() != null) {
        def rootNode = browser.getActiveTree().getRootNode()
        if (rootNode.getChildCount() > 0) {
            def child = rootNode.getChildAt(0)
            if (child.getChildCount() > 0) {
                def rootKerMLNamespace = child.getChildAt(0).getUserObject()
                logger.info("Starting recursive search from KerML root...")
                findNamedElement(rootKerMLNamespace, findNamedElement)
            }
        }
    }
    
} catch (Exception e) {
    logger.error("Exception: " + e.toString(), e)
} finally {
    logger.info("Inspect done.")
}

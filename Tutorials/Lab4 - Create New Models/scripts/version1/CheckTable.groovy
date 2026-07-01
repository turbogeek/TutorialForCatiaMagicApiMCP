import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Diagram

File logFile = new File("E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\Tutorials\\Lab4 - Create New Models\\logs\\DiagramTable.log")
logFile.withWriter { writer ->
    try {
        com.nomagic.magicdraw.openapi.uml.ModelElementsManager.getInstance().class.getMethods().findAll { it.getName().contains("Diagram") }.each {
            writer.println("MEM." + it.getName() + " : " + it.getParameterTypes().collect { it.getSimpleName() })
        }
    } catch (Exception e) {}
}

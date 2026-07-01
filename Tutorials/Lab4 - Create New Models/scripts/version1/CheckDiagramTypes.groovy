import com.nomagic.magicdraw.openapi.uml.ModelElementsManager

File logFile = new File("E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\Tutorials\\Lab4 - Create New Models\\logs\\DiagramTypes.log")
logFile.withWriter { writer ->
    try {
        ModelElementsManager.getInstance().getDiagramTypes().each {
            writer.println(it)
        }
    } catch (Exception e) {}
}

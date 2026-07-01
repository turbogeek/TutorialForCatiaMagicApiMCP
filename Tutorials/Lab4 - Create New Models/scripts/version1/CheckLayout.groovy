import com.nomagic.magicdraw.uml.symbols.layout.ClassDiagramLayouter
import com.nomagic.magicdraw.uml.symbols.layout.UMLGraphManager

File logFile = new File("E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\Tutorials\\Lab4 - Create New Models\\logs\\LayoutManager.log")
logFile.withWriter { writer ->
    try {
        ClassDiagramLayouter.class.getMethods().each {
            writer.println("ClassDiagramLayouter." + it.getName() + " : " + it.getParameterTypes().collect { it.getSimpleName() })
        }
    } catch (Exception e) {}
    try {
        UMLGraphManager.class.getMethods().each {
            writer.println("UMLGraphManager." + it.getName() + " : " + it.getParameterTypes().collect { it.getSimpleName() })
        }
    } catch (Exception e) {}
}

import com.nomagic.magicdraw.openapi.uml.PresentationElementsManager
import com.nomagic.magicdraw.uml.symbols.PresentationElement

File logFile = new File("E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\Tutorials\\Lab4 - Create New Models\\logs\\PresentationElementsManager.log")
logFile.withWriter { writer ->
    PresentationElementsManager.class.getMethods().each {
        writer.println(it.getName() + " : " + it.getParameterTypes().collect { it.getSimpleName() })
    }
}

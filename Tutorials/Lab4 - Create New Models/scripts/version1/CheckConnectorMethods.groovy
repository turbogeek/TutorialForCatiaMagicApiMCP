import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.Connector

File logFile = new File("E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\Tutorials\\Lab4 - Create New Models\\logs\\ConnectorMethods.log")
logFile.withWriter { writer ->
    Connector.class.getMethods().each {
        writer.println(it.getName() + " : " + it.getParameterTypes().collect { it.getSimpleName() })
    }
}

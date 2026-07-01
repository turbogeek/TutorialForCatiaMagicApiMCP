import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.ConnectorEnd

File logFile = new File("E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\Tutorials\\Lab4 - Create New Models\\logs\\ConnectorEndMethods.log")
logFile.withWriter { writer ->
    ConnectorEnd.class.getMethods().each {
        writer.println(it.getName() + " : " + it.getParameterTypes().collect { it.getSimpleName() })
    }
}

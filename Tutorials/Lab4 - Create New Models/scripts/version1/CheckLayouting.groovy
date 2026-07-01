import com.nomagic.magicdraw.uml.symbols.layout.Layouting

File logFile = new File("E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\Tutorials\\Lab4 - Create New Models\\logs\\Layouting.log")
logFile.withWriter { writer ->
    try {
        Layouting.class.getMethods().each {
            writer.println("Layouting." + it.getName() + " : " + it.getParameterTypes().collect { it.getSimpleName() })
        }
    } catch (Exception e) {}
}

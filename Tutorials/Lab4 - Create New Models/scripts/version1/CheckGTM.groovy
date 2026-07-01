import com.nomagic.generictable.GenericTableManager

File logFile = new File("E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\Tutorials\\Lab4 - Create New Models\\logs\\GenericTableManagerMethods.log")
logFile.withWriter { writer ->
    try {
        GenericTableManager.class.getMethods().each {
            writer.println("GenericTableManager." + it.getName() + " : " + it.getParameterTypes().collect { it.getSimpleName() })
        }
    } catch (Exception e) {}
}

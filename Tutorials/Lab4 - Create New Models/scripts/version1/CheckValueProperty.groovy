import com.nomagic.magicdraw.sysml.util.SysMLProfile

File logFile = new File("E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\Tutorials\\Lab4 - Create New Models\\logs\\SysMLProfileValueProperty.log")
logFile.withWriter { writer ->
    SysMLProfile.class.getDeclaredMethods().each {
        if (it.getName().toLowerCase().contains("valueproperty")) {
            writer.println(it.getName() + " : " + it.getParameterTypes().collect { it.getSimpleName() })
        }
    }
}

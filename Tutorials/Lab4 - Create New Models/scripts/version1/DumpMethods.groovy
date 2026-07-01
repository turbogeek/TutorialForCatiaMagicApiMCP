import com.nomagic.magicdraw.sysml.util.SysMLProfile

File logFile = new File("E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\Tutorials\\Lab4 - Create New Models\\logs\\SysMLProfileMethods.log")
logFile.withWriter { writer ->
    SysMLProfile.class.getDeclaredMethods().each {
        writer.println(it.getName())
    }
}

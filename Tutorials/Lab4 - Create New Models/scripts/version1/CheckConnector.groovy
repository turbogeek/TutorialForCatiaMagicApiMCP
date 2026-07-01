import com.nomagic.uml2.ext.jmi.helpers.ModelHelper

File logFile = new File("E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\Tutorials\\Lab4 - Create New Models\\logs\\ModelHelperMethods.log")
logFile.withWriter { writer ->
    ModelHelper.class.getDeclaredMethods().each {
        if (it.getName().toLowerCase().contains("connector") || it.getName().toLowerCase().contains("supplier")) {
            writer.println(it.getName() + " : " + it.getParameterTypes().collect { it.getSimpleName() })
        }
    }
}

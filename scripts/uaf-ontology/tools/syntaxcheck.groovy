// syntaxcheck.groovy — offline parse-only check (Phases.CONVERSION).
// Parses + builds the AST WITHOUT resolving imports, so com.nomagic.* / Cameo classes
// don't need to be on the classpath. Catches brace/paren/token syntax errors only.
//   usage:  groovy syntaxcheck.groovy <file.groovy> [<file2.groovy> ...]
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.CompilerConfiguration

int rc = 0
args.each { String path ->
    File f = new File(path)
    if (!f.exists()) { println "MISSING: ${path}"; rc = 2; return }
    def cu = new CompilationUnit(new CompilerConfiguration())
    cu.addSource(f)
    try {
        cu.compile(Phases.CONVERSION)
        println "SYNTAX OK  : ${f.name}"
    } catch (Throwable t) {
        rc = 1
        println "SYNTAX FAIL: ${f.name}"
        println "  " + (t.message ?: t.toString()).readLines().join("\n  ")
    }
}
System.properties['groovy.syntaxcheck.rc'] = String.valueOf(rc)

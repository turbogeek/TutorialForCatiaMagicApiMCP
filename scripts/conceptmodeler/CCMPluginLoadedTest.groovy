// CCMPluginLoadedTest.groovy
// =====================================================================================
// Run AFTER restarting Cameo with the corrected plugin (declares <required-plugin> on the Concept
// Modeler). This is the FAITHFUL end-to-end test: it resolves the plugin's OWN classes the same way
// CCM is resolvable from the harness (Class.forName via the script CL) — so the bridge runs in the
// plugin's REAL MagicDraw-wired classloader (no URLClassLoader nesting, which fought
// LocalFirstURLClassLoader). If the <required-plugin> wiring is correct, the bridge's typed CM2OWL
// call resolves and the report is produced.
// =====================================================================================

import com.nomagic.magicdraw.core.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat

final String REPO = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP'
final File   LOG  = new File(REPO + '\\logs', 'CCMPluginLoadedTest.log')
LOG.getParentFile().mkdirs()
try { LOG.bytes = new byte[0] } catch (Throwable ignored) {}
final SimpleDateFormat TS = new SimpleDateFormat('HH:mm:ss.SSS')
Closure diag  = { String m -> String l = TS.format(new Date()) + '  ' + m; println l; try { LOG << (l + '\n') } catch (Throwable ignored) {} }
Closure diagT = { String m, Throwable t -> StringWriter sw = new StringWriter(); t.printStackTrace(new PrintWriter(sw)); diag(m + '\n' + sw.toString()) }

diag('=== loaded-plugin (real CL) test START ===')
try {
    ClassLoader scl = this.getClass().getClassLoader()

    // Is the plugin actually loaded by MagicDraw? Resolve its classes the way CCM is resolvable.
    Class bridgeCls
    try {
        bridgeCls = Class.forName('com.ontologyvision.cm2english.ConceptModelEnglishBridge', true, scl)
        diag('plugin loaded; ConceptModelEnglishBridge classloader = ' + bridgeCls.getClassLoader())
    } catch (Throwable t) {
        diag('FATAL: plugin classes NOT visible — installed + Cameo restarted? (' + t + ')')
        return
    }
    // confirm the action class loads too
    try {
        def actionCls = Class.forName('com.ontologyvision.cm2english.Action_VerbalizeConceptModel', true, scl)
        diag('action class loaded by = ' + actionCls.getClassLoader())
    } catch (Throwable t) { diag('note: action class not resolvable (' + t + ')') }

    def app = Application.getInstance()
    def project = (app == null) ? null : app.getProject()
    if (project == null) { diag('FATAL: no active project (open African Wildlife).'); return }
    diag('active project: ' + project.getName())
    def pkg = new ArrayList(project.getModel().getOwnedElement()).find { el ->
        el.respondsTo('getName') && el.getName() == 'African Wildlife Ontology'
    }
    if (pkg == null) { diag('FATAL: package "African Wildlife Ontology" not found.'); return }
    diag('target package: ' + pkg.getHumanName())

    // Fire the REAL bridge (its typed CM2OWL call must resolve via the plugin's required-plugin wiring).
    def pkgIface = Class.forName('com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package', true, scl)
    try {
        def htmlFile = bridgeCls.getMethod('verbalizePackageToHtmlFile', pkgIface).invoke(null, pkg)
        long len = (htmlFile == null) ? 0L : ((File) htmlFile).length()
        diag('SUCCESS: bridge produced report -> ' + htmlFile + '  (' + len + ' bytes)')
        diag('=== loaded-plugin test DONE (PASS) ===')
    } catch (Throwable t) {
        diagT('bridge FAILED (if NoClassDefFoundError on CCM, the <required-plugin> wiring still is not granting class visibility)', t)
        diag('=== loaded-plugin test DONE (FAIL) ===')
    }
} catch (Throwable t) {
    diagT('UNCAUGHT in loaded-plugin test', t)
}

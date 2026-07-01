// CCMPluginTest.groovy
// =====================================================================================
// Tests the INSTALLED "Concept Modeler to English" plugin's COMPILED classes against the live
// model, via TestHarness2 — no Cameo restart, no GUI clicks. Loads the installed plugin jar in a
// child classloader (parent = this script's CL, so the plugin's CCM/MD references resolve from the
// live app), sets the plugin's static PLUGIN_DIR, then:
//   A) runs the real ConceptModelEnglishBridge.verbalizePackageToHtmlFile(pkg)  (export + verbalize + write)
//   B) constructs Action_VerbalizeConceptModel(pkg) and calls updateState() (confirms the action loads/enables)
// Heavy diagnostics to logs/CCMPluginTest.log; the report lands in %USERPROFILE%\cm2english\.
// =====================================================================================

import com.nomagic.magicdraw.core.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL
import java.net.URLClassLoader
import java.text.SimpleDateFormat

final String REPO = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP'
final File   LOG  = new File(REPO + '\\logs', 'CCMPluginTest.log')
LOG.getParentFile().mkdirs()
try { LOG.bytes = new byte[0] } catch (Throwable ignored) {}
final SimpleDateFormat TS = new SimpleDateFormat('HH:mm:ss.SSS')
Closure diag  = { String m -> String l = TS.format(new Date()) + '  ' + m; println l; try { LOG << (l + '\n') } catch (Throwable ignored) {} }
Closure diagT = { String m, Throwable t -> StringWriter sw = new StringWriter(); t.printStackTrace(new PrintWriter(sw)); diag(m + '\n' + sw.toString()) }

diag('=== CCM plugin (compiled artifact) test START ===')
try {
    final String PLUGIN_DIR = 'E:\\Magic SW\\MSoS26xHF1\\plugins\\com.ontologyvision.cm2english'
    File pluginJar = new File(PLUGIN_DIR, 'com.ontologyvision.cm2english.jar')
    diag('installed plugin jar: ' + pluginJar + '  exists=' + pluginJar.exists())
    if (!pluginJar.exists()) { diag('FATAL: plugin jar not installed.'); return }

    ClassLoader scl = this.getClass().getClassLoader()
    def pkgIface = Class.forName('com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package', true, scl)

    // active project + the African Wildlife Ontology package
    def app = Application.getInstance()
    def project = (app == null) ? null : app.getProject()
    if (project == null) { diag('FATAL: no active project (open African Wildlife).'); return }
    diag('active project: ' + project.getName())
    def model = project.getModel()
    def pkg = new ArrayList(model.getOwnedElement()).find { el ->
        el.respondsTo('getName') && el.getName() == 'African Wildlife Ontology'
    }
    if (pkg == null) { diag('FATAL: package "African Wildlife Ontology" not found at model root.'); return }
    diag('target package: ' + pkg.getHumanName())

    // The plugin's TYPED CCM reference must resolve via a CL that sees the Concept Modeler. A plain
    // URLClassLoader(parent=scl) does NOT delegate CCM through (MagicDraw macro-CL quirk: Class.forName(name,scl)
    // reaches CCM, but child parent-delegation does not). So parent the plugin CL on CCM's OWN classloader
    // (which sees CCM + the MD core via its own parent). This also mirrors what a <required-plugin> on CCM
    // gives the real installed plugin.
    ClassLoader ccmCL = Class.forName('com.nomagic.conceptmodeler.transform.ConceptModelOWLTransformFactory', true, scl).getClassLoader()
    diag('CCM classloader (used as plugin CL parent): ' + ccmCL)
    URLClassLoader pcl = new URLClassLoader([ pluginJar.toURI().toURL() ] as URL[], ccmCL)
    try {
        def pluginCls = Class.forName('com.ontologyvision.cm2english.ConceptToEnglishPlugin', true, pcl)
        pluginCls.getField('PLUGIN_DIR').set(null, new File(PLUGIN_DIR))
        diag('set ConceptToEnglishPlugin.PLUGIN_DIR = ' + PLUGIN_DIR)

        // A) the real bridge — export (CM2OWL) + verbalize (isolated CL) + write report files
        try {
            def bridgeCls = Class.forName('com.ontologyvision.cm2english.ConceptModelEnglishBridge', true, pcl)
            def htmlFile  = bridgeCls.getMethod('verbalizePackageToHtmlFile', pkgIface).invoke(null, pkg)
            long len = (htmlFile == null) ? 0L : ((File) htmlFile).length()
            diag('A) bridge OK -> ' + htmlFile + '  (' + len + ' bytes)')
        } catch (Throwable t) {
            diagT('A) bridge FAILED', t)
        }

        // B) action class constructs + enables (no browser fired)
        try {
            def actionCls = Class.forName('com.ontologyvision.cm2english.Action_VerbalizeConceptModel', true, pcl)
            def action    = actionCls.getConstructor(pkgIface).newInstance(pkg)
            actionCls.getMethod('updateState').invoke(action)
            def enabled = actionCls.getMethod('isEnabled').invoke(action)
            diag('B) action constructed; updateState() ran; isEnabled=' + enabled)
        } catch (Throwable t) {
            diagT('B) action FAILED', t)
        }

        diag('=== plugin test DONE ===')
    } finally {
        try { pcl.close() } catch (Throwable ignored) {}
    }
} catch (Throwable t) {
    diagT('UNCAUGHT in plugin test', t)
}

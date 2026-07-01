// CCMWindowTest.groovy
// =====================================================================================
// Phase-A test for the dockable report window. Drives the REAL loaded plugin: verbalizes the
// African Wildlife Ontology, calls VerbalizeReportWindow.show(...) to populate + activate the docked
// panel (so it pops up for visual inspection), then reflects into the panel's JEditorPane to confirm
// the report HTML actually rendered into it. No GUI clicks.
// =====================================================================================

import com.nomagic.magicdraw.core.Application
import javax.swing.SwingUtilities
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Field
import java.text.SimpleDateFormat

final String REPO = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP'
final File   LOG  = new File(REPO + '\\logs', 'CCMWindowTest.log')
LOG.getParentFile().mkdirs()
try { LOG.bytes = new byte[0] } catch (Throwable ignored) {}
final SimpleDateFormat TS = new SimpleDateFormat('HH:mm:ss.SSS')
Closure diag  = { String m -> String l = TS.format(new Date()) + '  ' + m; println l; try { LOG << (l + '\n') } catch (Throwable ignored) {} }
Closure diagT = { String m, Throwable t -> StringWriter sw = new StringWriter(); t.printStackTrace(new PrintWriter(sw)); diag(m + '\n' + sw.toString()) }

diag('=== Phase-A dockable window test START ===')
try {
    ClassLoader scl = this.getClass().getClassLoader()
    def vrw    = Class.forName('com.ontologyvision.cm2english.VerbalizeReportWindow', true, scl)
    def bridge = Class.forName('com.ontologyvision.cm2english.ConceptModelEnglishBridge', true, scl)
    def projCls = Class.forName('com.nomagic.magicdraw.core.Project', true, scl)
    def pkgIface = Class.forName('com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package', true, scl)
    diag('plugin loaded; VerbalizeReportWindow CL = ' + vrw.getClassLoader())

    def app = Application.getInstance()
    def project = (app == null) ? null : app.getProject()
    if (project == null) { diag('FATAL: no active project (open African Wildlife).'); return }
    def pkg = new ArrayList(project.getModel().getOwnedElement()).find { el ->
        el.respondsTo('getName') && el.getName() == 'African Wildlife Ontology'
    }
    if (pkg == null) { diag('FATAL: package "African Wildlife Ontology" not found.'); return }
    diag('target package: ' + pkg.getHumanName())

    // 1) verbalize (real bridge) -> html
    String html = (String) bridge.getMethod('verbalizePackageToHtml', pkgIface).invoke(null, pkg)
    diag('verbalizePackageToHtml -> html length = ' + html.length())

    // 2) populate + activate the dockable window (on the EDT)
    def showM = vrw.getMethod('show', projCls, String.class, String.class)
    SwingUtilities.invokeAndWait({ showM.invoke(null, project, html, 'African Wildlife Ontology') } as Runnable)
    diag('VerbalizeReportWindow.show(...) invoked on EDT (window should now be docked + active on the right)')

    // 3) verify the panel's JEditorPane actually received the report (reflect into private state)
    Thread.sleep(400)   // let setHtml's invokeLater flush
    Field stateF = vrw.getDeclaredField('STATE'); stateF.setAccessible(true)
    Map state = (Map) stateF.get(null)
    def holder = state.get(project)
    if (holder == null) {
        diag('WARN: no per-project window Holder — did the ProjectWindowsConfigurator.configure run on project open?')
    } else {
        Field panelF = holder.getClass().getDeclaredField('panel'); panelF.setAccessible(true)
        def panel = panelF.get(holder)
        Field editorF = panel.getClass().getDeclaredField('editor'); editorF.setAccessible(true)
        def editor = editorF.get(panel)
        final int[] docLen = [ 0 ]
        SwingUtilities.invokeAndWait({ docLen[0] = editor.getDocument().getLength() } as Runnable)
        diag('docked panel JEditorPane document length = ' + docLen[0]
                + (docLen[0] > 500 ? '   PASS — report rendered into the docked panel' : '   (too short — investigate)'))
    }
    diag('=== Phase-A test DONE — eyeball the docked "Concept Model — English" panel on the right ===')
} catch (Throwable t) {
    diagT('UNCAUGHT in window test', t)
}

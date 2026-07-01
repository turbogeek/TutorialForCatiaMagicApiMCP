// CCMDiagramNavTest.groovy
// =====================================================================================
// Phase-1 (P1) test: DiagramNavigator.navigateTo opens the element's diagram, centers + selects its symbol.
// Adaptive: lists the project's diagrams, finds a concept under "African Wildlife Ontology" that is shown on
// EXACTLY ONE diagram (so navigateTo takes the single-diagram path, no chooser dialog), navigates to it on the
// EDT, then asserts the active diagram + that the target's symbol is selected. If no concept is on any diagram,
// reports the graceful 0-diagram path. No GUI clicks.
// =====================================================================================

import com.nomagic.magicdraw.core.Application
import javax.swing.SwingUtilities
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat

final String REPO = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP'
final File   LOG  = new File(REPO + '\\logs', 'CCMDiagramNavTest.log')
LOG.getParentFile().mkdirs()
try { LOG.bytes = new byte[0] } catch (Throwable ignored) {}
final SimpleDateFormat TS = new SimpleDateFormat('HH:mm:ss.SSS')
Closure diag  = { String m -> String l = TS.format(new Date()) + '  ' + m; println l; try { LOG << (l + '\n') } catch (Throwable ignored) {} }
Closure diagT = { String m, Throwable t -> StringWriter sw = new StringWriter(); t.printStackTrace(new PrintWriter(sw)); diag(m + '\n' + sw.toString()) }

diag('=== Phase-1 diagram-nav test START ===')
try {
    ClassLoader scl = this.getClass().getClassLoader()
    def dnav     = Class.forName('com.ontologyvision.cm2english.DiagramNavigator', true, scl)
    def projCls  = Class.forName('com.nomagic.magicdraw.core.Project', true, scl)
    def elemIface = Class.forName('com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element', true, scl)

    def app = Application.getInstance()
    def project = (app == null) ? null : app.getProject()
    if (project == null) { diag('FATAL: no active project.'); return }
    def pkg = new ArrayList(project.getModel().getOwnedElement()).find { el ->
        el.respondsTo('getName') && el.getName() == 'African Wildlife Ontology'
    }
    if (pkg == null) { diag('FATAL: package "African Wildlife Ontology" not found.'); return }

    def diagrams = new ArrayList(project.getDiagrams())
    diag('project has ' + diagrams.size() + ' diagram(s): ' + diagrams.collect { try { it.getName() } catch (e) { '?' } })

    def sem = project.getSymbolElementMap()
    def target = null
    def targetPes = null
    Closure walk = null
    walk = { el ->
        if (target != null) return
        try {
            def pes = sem.getAllPresentationElements(el)
            if (pes != null && !pes.isEmpty() && el.respondsTo('getName') && el.getName()) {
                def distinct = pes.collect { it.getAbstractDiagramPresentationElement() }.findAll { it != null }.unique()
                if (distinct.size() == 1) { target = el; targetPes = pes; return }
            }
        } catch (ignored) { }
        new ArrayList(el.getOwnedElement()).each { walk(it) }
    }
    walk(pkg)

    if (target == null) {
        diag('No concept under "African Wildlife Ontology" is shown on exactly one diagram.')
        diag('=> navigateTo would take the 0-diagram path (tree reveal + status note). That graceful path is the P1 behavior here.')
        diag('=== Phase-1 test DONE (no on-diagram concept to exercise the positive path) ===')
        return
    }
    diag('navigating to "' + target.getName() + '" (' + targetPes.size() + ' symbol(s), 1 diagram)')

    def beforeName = null
    SwingUtilities.invokeAndWait({ def ad = project.getActiveDiagram(); beforeName = (ad == null) ? null : ad.getName() } as Runnable)

    def navM = dnav.getMethod('navigateTo', projCls, elemIface)
    SwingUtilities.invokeAndWait({ navM.invoke(null, project, target) } as Runnable)   // openCenterSelect uses invokeLater
    Thread.sleep(900)

    final String[] afterName = [ null ]
    final boolean[] selected = [ false ]
    SwingUtilities.invokeAndWait({
        def ad = project.getActiveDiagram()
        afterName[0] = (ad == null) ? null : ad.getName()
        targetPes.each { pe -> try { if (pe.isSelected()) selected[0] = true } catch (ignored) { } }
    } as Runnable)

    diag('active diagram: before="' + beforeName + '" after="' + afterName[0] + '";  target symbol selected=' + selected[0])
    diag((afterName[0] != null && selected[0])
            ? 'PASS — diagram opened + target symbol selected'
            : 'CHECK — diagram="' + afterName[0] + '", selected=' + selected[0] + ' (inspect Cameo)')
    diag('=== Phase-1 test DONE ===')
} catch (Throwable t) {
    diagT('UNCAUGHT in diagram-nav test', t)
}

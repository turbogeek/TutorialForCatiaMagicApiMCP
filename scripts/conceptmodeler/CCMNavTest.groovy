// CCMNavTest.groovy
// =====================================================================================
// Phase-B test: (0) confirm the report now carries href="iri:<IRI>" anchors (entityLinks), and
// (1) drive ConceptNavigator.selectInTree(project, pkg, "iri:...#lion") and verify the Containment tree
// actually selects the "lion" element. No GUI clicks — proves IRI -> element -> tree selection.
// =====================================================================================

import com.nomagic.magicdraw.core.Application
import javax.swing.SwingUtilities
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder
import java.text.SimpleDateFormat

final String REPO = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP'
final File   LOG  = new File(REPO + '\\logs', 'CCMNavTest.log')
LOG.getParentFile().mkdirs()
try { LOG.bytes = new byte[0] } catch (Throwable ignored) {}
final SimpleDateFormat TS = new SimpleDateFormat('HH:mm:ss.SSS')
Closure diag  = { String m -> String l = TS.format(new Date()) + '  ' + m; println l; try { LOG << (l + '\n') } catch (Throwable ignored) {} }
Closure diagT = { String m, Throwable t -> StringWriter sw = new StringWriter(); t.printStackTrace(new PrintWriter(sw)); diag(m + '\n' + sw.toString()) }

diag('=== Phase-B navigation test START ===')
try {
    ClassLoader scl = this.getClass().getClassLoader()
    def nav     = Class.forName('com.ontologyvision.cm2english.ConceptNavigator', true, scl)
    def bridge  = Class.forName('com.ontologyvision.cm2english.ConceptModelEnglishBridge', true, scl)
    def projCls = Class.forName('com.nomagic.magicdraw.core.Project', true, scl)
    def pkgIface = Class.forName('com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package', true, scl)
    def elemIface = Class.forName('com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element', true, scl)

    def app = Application.getInstance()
    def project = (app == null) ? null : app.getProject()
    if (project == null) { diag('FATAL: no active project (open African Wildlife).'); return }
    def pkg = new ArrayList(project.getModel().getOwnedElement()).find { el ->
        el.respondsTo('getName') && el.getName() == 'African Wildlife Ontology'
    }
    if (pkg == null) { diag('FATAL: package "African Wildlife Ontology" not found.'); return }
    diag('target package: ' + pkg.getHumanName())

    // 0) the report must now carry iri: anchors (bridge verbalizes with entityLinks=true)
    String html = (String) bridge.getMethod('verbalizePackageToHtml', pkgIface).invoke(null, pkg)
    diag('report has href="iri:" anchors: ' + html.contains('href="iri:') + '   (html len=' + html.length() + ')')

    // 1) navigate to the "lion" concept
    String iri  = 'http://www.meteck.org/teaching/OEbook/ontologies/AfricanWildlifeOntology1.owl#lion'
    String href = 'iri:' + URLEncoder.encode(iri, 'UTF-8').replace('+', '%20')
    def selectInTree = nav.getMethod('selectInTree', projCls, elemIface, String.class)
    SwingUtilities.invokeAndWait({ selectInTree.invoke(null, project, pkg, href) } as Runnable)
    diag('selectInTree(... #lion) invoked')

    // 2) verify the Containment tree selected the "lion" element
    Thread.sleep(300)
    final Object[] sel = [ null ]
    SwingUtilities.invokeAndWait({ sel[0] = project.getBrowser().getContainmentTree().getSelectedNode() } as Runnable)
    def node = sel[0]
    def uo = (node == null) ? null : node.getUserObject()
    String selName = (uo != null && uo.respondsTo('getName')) ? uo.getName() : String.valueOf(uo)
    diag('Containment tree selected node = "' + selName + '"'
            + (selName == 'lion' ? '   PASS — IRI -> element -> tree selection works' : '   (expected "lion")'))
    diag('=== Phase-B test DONE ===')
} catch (Throwable t) {
    diagT('UNCAUGHT in nav test', t)
}

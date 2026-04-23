// cleanup-fixture.groovy — clears all owned members of the selected Namespace.
// Intended for resetting TF1 between fixture-builder iterations.

import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.dassault_systemes.modeler.kerml.model.kerml.Namespace
import org.eclipse.emf.ecore.util.EcoreUtil

def logFile = new File('E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\logs\\cleanup-fixture.log')
logFile.parentFile.mkdirs()
logFile.text = ''
def log = { String msg -> logFile << "${new Date().format('HH:mm:ss.SSS')} $msg\n"; println msg }

log '=== cleanup-fixture.groovy start ==='

def app = Application.getInstance()
def project = app.getProject()
def sm = SessionManager.getInstance()

def browser = app.getMainFrame().getBrowser()
def selected = browser.getActiveTree()?.getSelectedNodes()
Namespace ns = null
if (selected && selected.length > 0) {
    def obj = selected[0].getUserObject()
    // Walk up from whatever's selected to find an outermost named Namespace
    // whose owner is NOT a Namespace (i.e. top-level in the user's content).
    while (obj != null) {
        if (obj instanceof Namespace) {
            def name = ((Namespace) obj).getDeclaredName()
            if (name != null && !name.isEmpty()) ns = (Namespace) obj
        }
        try {
            def parent = obj.respondsTo('getOwner') ? obj.getOwner() : null
            if (parent == null || !(parent instanceof Namespace)) break
            obj = parent
        } catch (Exception e) { break }
    }
}
if (ns == null) {
    log 'ERROR: select any element inside TF1 (or TF1 itself)'
    return
}

def before = ns.getOwnedMember()?.size() ?: 0
log "Target: ${ns.getDeclaredName()} — ${before} owned members to delete"

try {
    sm.createSession(project, 'cleanup-fixture')
    // Snapshot the list before mutating (EcoreUtil.delete modifies containment)
    def toDelete = new ArrayList(ns.getOwnedMember() ?: [])
    log "Deleting ${toDelete.size()} top-level members..."
    // Fast path: EcoreUtil.remove detaches from container in O(1). Skip the
    // cross-reference walk (EcoreUtil.delete(true)) which was ~22s per element
    // and makes TF1 cleanup take minutes.
    int ok = 0, fail = 0
    for (def elem : toDelete) {
        try {
            EcoreUtil.remove(elem)
            ok++
        } catch (Exception e) {
            fail++
            log "  FAILED: ${elem.getClass().getSimpleName()}: ${e.class.simpleName}: ${e.message?.take(80)}"
        }
    }
    log "  removed ${ok}, failed ${fail}"
    sm.closeSession(project)
    def after = ns.getOwnedMember()?.size() ?: 0
    log "Done. Before=${before} After=${after}"
} catch (Throwable t) {
    try { sm.cancelSession(project) } catch (Exception ignore) {}
    log "Cleanup failed: ${t.toString()}"
    throw t
}

log '=== cleanup-fixture.groovy end ==='

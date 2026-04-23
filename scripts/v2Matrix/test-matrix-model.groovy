// test-matrix-model.groovy — headless self-test for MatrixModel.
//
// Runs against the currently-selected TF1 Namespace. Builds a matrix with
// the default config (SATISFY kind, PartUsage rows, RequirementUsage cols),
// logs row/col counts and cell details. Run via REST harness.
//
// Expected on clean TF-1 fixture (one run, no duplicates):
//   rows   = 7   (P1..P6, Q)
//   cols   = 8   (R1..R8)
//   direct = 6   (distinct (row,col) pairs with satisfy edges)
//   Backing counts: (P1,R1)=2, (P2,R3)=1, (P3,R3)=1, (P1,R2)=1, (P4,R4)=1, (P5,R5)=1
//   (Q,R6)=1 direct; if showImplied=true also (P6,R6) implied

import com.nomagic.magicdraw.core.Application
import com.dassault_systemes.modeler.kerml.model.kerml.Namespace
import com.dassault_systemes.modeler.sysml.model.sysml.PartUsage
import com.dassault_systemes.modeler.sysml.model.sysml.RequirementUsage
import com.dassault_systemes.modeler.sysml.model.sysml.ViewpointUsage

// Load helper classes via GroovyClassLoader so the script can be re-run fresh.
// parseClass returns the FIRST class in the file — we call loadClass by FQN
// afterward because MatrixModel.groovy declares multiple classes (enum + several).
String scriptsDir = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\scripts\\v2Matrix'
def loader = new GroovyClassLoader(getClass().getClassLoader())
loader.parseClass(new File(scriptsDir, 'LibraryDetector.groovy'))
loader.parseClass(new File(scriptsDir, 'MatrixModel.groovy'))
def MatrixModelCls = loader.loadClass('v2Matrix.MatrixModel')

def logFile = new File('E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\logs\\test-matrix-model.log')
logFile.parentFile.mkdirs()
logFile.text = ''
def log = { String msg -> logFile << "${new Date().format('HH:mm:ss.SSS')} $msg\n"; println msg }

log '=== test-matrix-model.groovy start ==='

// Get TF1 from selection
def app = Application.getInstance()
def browser = app.getMainFrame().getBrowser()
def selected = browser.getActiveTree()?.getSelectedNodes()
Namespace tf1 = null
if (selected && selected.length > 0) {
    def obj = selected[0].getUserObject()
    if (obj instanceof Namespace) tf1 = (Namespace) obj
}
if (tf1 == null) {
    log 'ERROR: Select TF1 first'
    return
}
log "Scope: ${tf1.getDeclaredName()}"

// --- Test 1: default config (SATISFY, PartUsage rows, RequirementUsage cols) ---
log '\n--- Test 1: default SATISFY, showImplied=false ---'
def mm = MatrixModelCls.newInstance()
mm.scope = tf1
mm.rowTypes = [PartUsage]
mm.colTypes = [RequirementUsage]
// kind defaults to SATISFY
// showImplied defaults to false

def matrix = mm.build()
log "  rows (PartUsage): ${matrix.rows.size()}"
matrix.rows.each { r -> log "    - ${r.getDeclaredName()}" }
log "  cols (RequirementUsage): ${matrix.cols.size()}"
matrix.cols.each { c -> log "    - ${c.getDeclaredName()}" }
log "  cells total: ${matrix.cellCount()} (direct: ${matrix.directCount()}, implied: ${matrix.impliedCount()})"
log '  --- Cells ---'
matrix.cells.values().each { cell ->
    def srcStr = cell.sources.toString()
    log "    (${cell.row.getDeclaredName()}, ${cell.col.getDeclaredName()}) count=${cell.getCount()} sources=${srcStr}"
}

// --- Test 2: exclude ViewpointUsage from cols (FR-3) ---
log '\n--- Test 2: exclude ViewpointUsage from cols ---'
def mm2 = MatrixModelCls.newInstance()
mm2.scope = tf1
mm2.rowTypes = [PartUsage]
mm2.colTypes = [RequirementUsage]
mm2.colExclusions = [ViewpointUsage]
def matrix2 = mm2.build()
log "  cols after excluding ViewpointUsage: ${matrix2.cols.size()}"
log "  cells: ${matrix2.cellCount()}"

// --- Test 3: showImplied=true (FR-6) ---
log '\n--- Test 3: showImplied=true — expect P6→R6 implied cell ---'
def mm3 = MatrixModelCls.newInstance()
mm3.scope = tf1
mm3.rowTypes = [PartUsage]
mm3.colTypes = [RequirementUsage]
mm3.showImplied = true
def matrix3 = mm3.build()
log "  cells total: ${matrix3.cellCount()} (direct: ${matrix3.directCount()}, implied: ${matrix3.impliedCount()})"
log '  --- Cells with implied ---'
matrix3.cells.values().each { cell ->
    def tag = cell.isDirect() ? '[DIRECT]' : (cell.isImplied() ? '[IMPLIED]' : '[?]')
    log "    ${tag} (${cell.row.getDeclaredName()}, ${cell.col.getDeclaredName()}) count=${cell.getCount()}"
}

log '\n=== test-matrix-model.groovy end ==='

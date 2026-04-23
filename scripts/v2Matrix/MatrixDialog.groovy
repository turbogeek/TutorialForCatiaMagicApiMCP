// =============================================================================
// MatrixDialog.groovy — main entry point for v2Matrix.
//
// Opens a modeless JDialog showing a MatrixCanvas of the currently-selected
// Namespace. User controls swap axes, show-implied, and relationship-kind.
// Close the dialog → dispose + remove reference (FR-11, NFR-1, NFR-6).
//
// Run via REST harness (or manually from Cameo script console):
//   harness.bat run "E:\_Documents\git\TutorialForCatiaMagicApiMCP\scripts\v2Matrix\MatrixDialog.groovy"
//
// PRE-REQUISITE: select a Namespace (e.g. TF1) in the containment tree.
// The matrix queries elements within that scope.
//
// Logging: logs/v2Matrix.log (cleared per run, per FR-19).
// =============================================================================

import com.nomagic.magicdraw.core.Application
import com.dassault_systemes.modeler.kerml.model.kerml.Namespace
import com.dassault_systemes.modeler.sysml.model.sysml.PartUsage
import com.dassault_systemes.modeler.sysml.model.sysml.RequirementUsage
import com.dassault_systemes.modeler.sysml.model.sysml.ViewpointUsage
import javax.swing.*
import javax.swing.border.EmptyBorder
import java.awt.*
import java.awt.event.*

// ---- Logger -----------------------------------------------------------------
String scriptsDir = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\scripts'
def LoggerClass = new GroovyClassLoader(getClass().getClassLoader())
    .parseClass(new File(scriptsDir, 'SysMLv2Logger.groovy'))
File logFile = new File('E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\logs', 'v2Matrix.log')
def log = LoggerClass.newInstance('v2Matrix', logFile)
log.info('=== MatrixDialog run started ===')

// ---- Load helper classes ----------------------------------------------------
String v2Dir = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\scripts\\v2Matrix'
def loader = new GroovyClassLoader(getClass().getClassLoader())
loader.parseClass(new File(v2Dir, 'LibraryDetector.groovy'))
loader.parseClass(new File(v2Dir, 'MatrixModel.groovy'))
loader.parseClass(new File(v2Dir, 'MatrixCanvas.groovy'))
def MatrixModelCls       = loader.loadClass('v2Matrix.MatrixModel')
def MatrixCanvasCls      = loader.loadClass('v2Matrix.MatrixCanvas')
def RelationshipKindCls  = loader.loadClass('v2Matrix.RelationshipKind')
def PaletteCls           = loader.loadClass('v2Matrix.Palette')

// ---- Get scope from browser selection ---------------------------------------
def app = Application.getInstance()
def project = app.getProject()
if (project == null) {
    log.error('No active Cameo project')
    app.getGUILog().log('[v2Matrix] ERROR: no active project')
    return
}

def browser = app.getMainFrame().getBrowser()
def selected = browser.getActiveTree()?.getSelectedNodes()
Namespace scope = null
if (selected && selected.length > 0) {
    def obj = selected[0].getUserObject()
    if (obj instanceof Namespace) {
        def name = ((Namespace) obj).getDeclaredName()
        if (name != null && !name.isEmpty()) scope = (Namespace) obj
    }
}
if (scope == null) {
    def msg = 'Select a named Namespace (e.g. TF1) in the containment tree, then re-run.'
    log.error(msg)
    app.getGUILog().log('[v2Matrix] ERROR: ' + msg)
    return
}
log.info('Scope: ' + scope.getDeclaredName())

// ---- Build initial matrix ---------------------------------------------------
def makeModel = { boolean swap, boolean showImplied, boolean excludeViewpoint ->
    def mm = MatrixModelCls.newInstance()
    mm.scope = scope
    mm.showImplied = showImplied
    if (swap) {
        mm.rowTypes = [RequirementUsage]
        mm.colTypes = [PartUsage]
        mm.rowExclusions = excludeViewpoint ? [ViewpointUsage] : []
        // Clear default SatisfyRequirementUsage col exclusion — it's a row now
        mm.colExclusions = []
        mm.rowExclusions = mm.rowExclusions +
            [com.dassault_systemes.modeler.sysml.model.sysml.SatisfyRequirementUsage]
    } else {
        mm.rowTypes = [PartUsage]
        mm.colTypes = [RequirementUsage]
        if (excludeViewpoint) mm.colExclusions =
            mm.colExclusions + [ViewpointUsage]
    }
    mm
}

def initialModel = makeModel(false, false, false)
def initialMatrix = initialModel.build()
log.info("Initial matrix: ${initialMatrix.rows.size()} rows × ${initialMatrix.cols.size()} cols, ${initialMatrix.cellCount()} cells")

// ---- Build UI on EDT --------------------------------------------------------
SwingUtilities.invokeLater {
    try {
        def canvas = MatrixCanvasCls.newInstance(initialMatrix)

        def dialog = new JDialog(app.getMainFrame(), 'v2Matrix — ' + scope.getDeclaredName(), false)
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE)

        // --- Controls ---
        def controls = new JPanel()
        controls.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4))
        controls.setBorder(new EmptyBorder(4, 8, 4, 8))

        def swapCb = new JCheckBox('Swap axes', false)
        def impliedCb = new JCheckBox('Show implied', false)
        def hideVpCb = new JCheckBox('Hide ViewpointUsage', false)
        def paletteCombo = new JComboBox(['Standard', 'Colorblind-safe'] as String[])
        def refreshBtn = new JButton('Refresh')
        def statusLabel = new JLabel(
            "${initialMatrix.rows.size()}r × ${initialMatrix.cols.size()}c · ${initialMatrix.cellCount()} cells")

        controls.add(swapCb)
        controls.add(impliedCb)
        controls.add(hideVpCb)
        controls.add(new JLabel('  Palette:'))
        controls.add(paletteCombo)
        controls.add(refreshBtn)
        controls.add(Box.createHorizontalStrut(16))
        controls.add(statusLabel)

        // --- Refresh action ---
        def refresh = {
            boolean swap = swapCb.isSelected()
            boolean impl = impliedCb.isSelected()
            boolean hideVp = hideVpCb.isSelected()
            log.info("Refresh: swap=${swap} implied=${impl} hideViewpoint=${hideVp}")
            def mm = makeModel(swap, impl, hideVp)
            def matrix = mm.build()
            canvas.setMatrix(matrix)
            // Palette
            def palName = paletteCombo.getSelectedItem() as String
            canvas.palette = (palName == 'Colorblind-safe') ? PaletteCls.colorblindSafe() : PaletteCls.standard()
            canvas.repaint()
            statusLabel.setText(
                "${matrix.rows.size()}r × ${matrix.cols.size()}c · ${matrix.cellCount()} cells" +
                (impl ? " (${matrix.impliedCount()} implied)" : ''))
        }
        [swapCb, impliedCb, hideVpCb].each { it.addActionListener({ refresh() } as ActionListener) }
        paletteCombo.addActionListener({ refresh() } as ActionListener)
        refreshBtn.addActionListener({ refresh() } as ActionListener)

        // --- Layout ---
        def content = new JPanel(new BorderLayout())
        content.add(controls, BorderLayout.NORTH)
        content.add(new JScrollPane(canvas,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER)
        dialog.setContentPane(content)

        dialog.addWindowListener(new WindowAdapter() {
            @Override void windowClosed(WindowEvent e) {
                log.info('Dialog closed — disposed')
            }
        })

        dialog.pack()
        Dimension pref = dialog.getSize()
        dialog.setSize(Math.min(pref.width + 40, 1400) as int,
                       Math.min(pref.height + 40, 900) as int)
        dialog.setLocationRelativeTo(app.getMainFrame())
        dialog.setVisible(true)
        log.info('Dialog shown')
    } catch (Throwable t) {
        log.error('Dialog creation failed: ' + t.toString())
        app.getGUILog().log('[v2Matrix] Dialog creation failed: ' + t.message)
    }
}

log.info('=== MatrixDialog init finished (dialog runs asynchronously on EDT) ===')

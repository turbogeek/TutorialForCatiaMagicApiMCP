// =============================================================================
// MatrixDialog.groovy — main entry point for v2Matrix.
//
// Opens a modeless JDialog showing a MatrixCanvas + LegendPanel for the
// currently-selected Namespace. Controls: swap, show-implied, hide
// ViewpointUsage, 4-palette dropdown, Refresh.
// =============================================================================

import com.nomagic.magicdraw.core.Application
import com.dassault_systemes.modeler.kerml.model.kerml.Namespace
import com.dassault_systemes.modeler.sysml.model.sysml.PartUsage
import com.dassault_systemes.modeler.sysml.model.sysml.RequirementUsage
import com.dassault_systemes.modeler.sysml.model.sysml.ViewpointUsage
import com.dassault_systemes.modeler.sysml.model.sysml.SatisfyRequirementUsage
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
def MatrixModelCls   = loader.loadClass('v2Matrix.MatrixModel')
def MatrixCanvasCls  = loader.loadClass('v2Matrix.MatrixCanvas')
def PaletteCls       = loader.loadClass('v2Matrix.Palette')
def LegendPanelCls   = loader.loadClass('v2Matrix.LegendPanel')

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

// ---- Model builder ----------------------------------------------------------
// Orientation-agnostic: MatrixModel.addSatisfyCells tests both (row=feature,
// col=req) and (row=req, col=feature) so swap is just a row/col-types flip.
def makeModel = { boolean swap, boolean showImplied, boolean excludeViewpoint ->
    def mm = MatrixModelCls.newInstance()
    mm.scope = scope
    mm.showImplied = showImplied
    if (swap) {
        mm.rowTypes = [RequirementUsage]
        mm.colTypes = [PartUsage]
        // SatisfyRequirementUsage IS-A RequirementUsage; exclude from rows.
        mm.rowExclusions = (excludeViewpoint
            ? [ViewpointUsage, SatisfyRequirementUsage]
            : [SatisfyRequirementUsage])
        mm.colExclusions = []
    } else {
        mm.rowTypes = [PartUsage]
        mm.colTypes = [RequirementUsage]
        mm.colExclusions = (excludeViewpoint
            ? [SatisfyRequirementUsage, ViewpointUsage]
            : [SatisfyRequirementUsage])
        mm.rowExclusions = []
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
        def legend = LegendPanelCls.newInstance()
        legend.setShowImplied(false)
        legend.setShowSubject(false)

        def dialog = new JDialog(app.getMainFrame(), 'v2Matrix — ' + scope.getDeclaredName(), false)
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE)

        // --- Controls ---
        def controls = new JPanel()
        controls.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4))
        controls.setBorder(new EmptyBorder(4, 8, 4, 8))

        def swapCb = new JCheckBox('Swap axes', false)
        def impliedCb = new JCheckBox('Show implied', false)
        def hideVpCb = new JCheckBox('Hide ViewpointUsage', false)
        def paletteCombo = new JComboBox(PaletteCls.allNames().toArray(new String[0]))
        paletteCombo.setSelectedItem('Standard')
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
            String palName = paletteCombo.getSelectedItem() as String
            log.info("Refresh: swap=${swap} implied=${impl} hideViewpoint=${hideVp} palette=${palName}")

            def mm = makeModel(swap, impl, hideVp)
            def matrix = mm.build()
            def palette = PaletteCls.byName(palName)

            canvas.setMatrix(matrix)
            canvas.setPalette(palette)
            legend.setPalette(palette)
            legend.setShowImplied(impl && matrix.impliedCount() > 0)
            legend.setShowSubject(false) // no subject kind yet (deferred)

            statusLabel.setText(
                "${matrix.rows.size()}r × ${matrix.cols.size()}c · ${matrix.cellCount()} cells" +
                (impl ? " (${matrix.impliedCount()} implied)" : ''))
        }
        [swapCb, impliedCb, hideVpCb, paletteCombo].each {
            it.addActionListener({ refresh() } as ActionListener)
        }
        refreshBtn.addActionListener({ refresh() } as ActionListener)

        // --- Layout: controls NORTH, canvas CENTER, legend EAST ---
        def content = new JPanel(new BorderLayout())
        content.add(controls, BorderLayout.NORTH)
        content.add(new JScrollPane(canvas,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER)
        content.add(legend, BorderLayout.EAST)
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

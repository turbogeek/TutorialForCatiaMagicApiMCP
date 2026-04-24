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
loader.parseClass(new File(v2Dir, 'MatrixController.groovy'))
loader.parseClass(new File(v2Dir, 'MatrixExporter.groovy'))
loader.parseClass(new File(v2Dir, 'MatrixViewPersistence.groovy'))
def MatrixModelCls         = loader.loadClass('v2Matrix.MatrixModel')
def MatrixCanvasCls        = loader.loadClass('v2Matrix.MatrixCanvas')
def PaletteCls             = loader.loadClass('v2Matrix.Palette')
def LegendPanelCls         = loader.loadClass('v2Matrix.LegendPanel')
def MatrixControllerCls    = loader.loadClass('v2Matrix.MatrixController')
def MatrixExporterCls      = loader.loadClass('v2Matrix.MatrixExporter')
def MatrixViewPersistCls   = loader.loadClass('v2Matrix.MatrixViewPersistence')

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
Map loadedConfig = [:]
def loadedView = null

// FR-16: auto-load if selection is a v2Matrix_* ViewUsage.
if (selected && selected.length > 0) {
    def obj = selected[0].getUserObject()
    if (MatrixViewPersistCls.isMatrixView(obj)) {
        loadedView = obj
        loadedConfig = MatrixViewPersistCls.load(obj)
        // The view's owner is the matrix scope (we saved it under the same Namespace)
        try {
            def viewOwner = obj.getOwner()
            if (viewOwner instanceof Namespace) scope = (Namespace) viewOwner
        } catch (Exception e) { log.warn('View-owner resolution failed: ' + e.message) }
        log.info("Auto-loading view '${obj.getDeclaredName()}' (${loadedConfig.size()} config entries)")
    } else if (obj instanceof Namespace) {
        def name = ((Namespace) obj).getDeclaredName()
        if (name != null && !name.isEmpty()) scope = (Namespace) obj
    }
}
if (scope == null) {
    def msg = 'Select a named Namespace (e.g. TF1) or a v2Matrix_* view, then re-run.'
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

// Initial configuration — either from auto-loaded view or defaults.
boolean initSwap    = (loadedConfig['axesSwapped'] == true) || loadedConfig['axesSwapped'] == 'true'
boolean initImplied = (loadedConfig['showImplied'] == true) || loadedConfig['showImplied'] == 'true'
boolean initHideVp  = (loadedConfig['excludeViewpoint'] == true) || loadedConfig['excludeViewpoint'] == 'true'
String  initPalette = (loadedConfig['paletteName'] as String) ?: 'Standard'

def initialModel = makeModel(initSwap, initImplied, initHideVp)
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

        // MatrixController handles mouse interactions (FR-12). Created before
        // the refresh closure so the closure can update controller.matrix.
        def controller = MatrixControllerCls.newInstance()
        controller.canvas = canvas
        controller.matrix = initialMatrix
        controller.project = project
        controller.log = log
        controller.owningDialog = dialog

        // --- Controls ---
        def controls = new JPanel()
        controls.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4))
        controls.setBorder(new EmptyBorder(4, 8, 4, 8))

        def swapCb = new JCheckBox('Swap axes', initSwap)
        def impliedCb = new JCheckBox('Show implied', initImplied)
        def hideVpCb = new JCheckBox('Hide ViewpointUsage', initHideVp)
        def paletteCombo = new JComboBox(PaletteCls.allNames().toArray(new String[0]))
        paletteCombo.setSelectedItem(initPalette)
        def refreshBtn = new JButton('Refresh')
        def exportBtn  = new JButton('Export…')
        def saveViewBtn = new JButton('Save view')
        def statusLabel = new JLabel(
            "${initialMatrix.rows.size()}r × ${initialMatrix.cols.size()}c · ${initialMatrix.cellCount()} cells")

        controls.add(swapCb)
        controls.add(impliedCb)
        controls.add(hideVpCb)
        controls.add(new JLabel('  Palette:'))
        controls.add(paletteCombo)
        controls.add(refreshBtn)
        controls.add(exportBtn)
        controls.add(saveViewBtn)
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
            canvas.axesSwapped = swap             // flip arrow direction (↗ ↔ ↙)
            canvas.repaint()
            legend.setPalette(palette)
            legend.setAxesSwapped(swap)           // legend swatches match canvas
            legend.setShowImplied(impl && matrix.impliedCount() > 0)
            legend.setShowSubject(false) // no subject kind yet (deferred)
            controller?.setMatrix(matrix)          // keep controller's matrix in sync

            statusLabel.setText(
                "${matrix.rows.size()}r × ${matrix.cols.size()}c · ${matrix.cellCount()} cells" +
                (impl ? " (${matrix.impliedCount()} implied)" : ''))
        }
        [swapCb, impliedCb, hideVpCb, paletteCombo].each {
            it.addActionListener({ refresh() } as ActionListener)
        }
        refreshBtn.addActionListener({ refresh() } as ActionListener)

        // --- Export action -------------------------------------------------
        // Opens a JFileChooser with format chooser; delegates to MatrixExporter.
        // Default filename = <scope>_matrix.<ext>, e.g. TF1_matrix.png.
        exportBtn.addActionListener({
            try {
                def formats = ['PNG', 'SVG', 'HTML', 'CSV'] as String[]
                def fmt = (String) JOptionPane.showInputDialog(
                    dialog, 'Export format:', 'Export matrix',
                    JOptionPane.PLAIN_MESSAGE, null, formats, 'PNG')
                if (fmt == null) return

                def defaultName = scope.getDeclaredName() + '_matrix.' + fmt.toLowerCase()
                def chooser = new JFileChooser()
                chooser.setDialogTitle('Export as ' + fmt)
                chooser.setSelectedFile(new File(defaultName))
                int choice = chooser.showSaveDialog(dialog)
                if (choice != JFileChooser.APPROVE_OPTION) return

                File target = chooser.getSelectedFile()
                def matrix = controller?.matrix ?: initialMatrix
                def palette = PaletteCls.byName(paletteCombo.getSelectedItem() as String)
                boolean swap = swapCb.isSelected()

                log.info("Export ${fmt} → ${target.absolutePath}")
                switch (fmt) {
                    case 'PNG':  MatrixExporterCls.exportPng(canvas, target); break
                    case 'SVG':  MatrixExporterCls.exportSvg(matrix, palette, target, swap); break
                    case 'HTML': MatrixExporterCls.exportHtml(matrix, palette, target, swap,
                                    'v2Matrix — ' + scope.getDeclaredName()); break
                    case 'CSV':  MatrixExporterCls.exportCsv(matrix, target); break
                }
                JOptionPane.showMessageDialog(dialog,
                    "Exported ${fmt}:\n${target.absolutePath}",
                    'Export complete', JOptionPane.INFORMATION_MESSAGE)
            } catch (Throwable t) {
                log.error('Export failed: ' + t.toString())
                JOptionPane.showMessageDialog(dialog,
                    "Export failed: ${t.message}", 'Error',
                    JOptionPane.ERROR_MESSAGE)
            }
        } as ActionListener)

        // --- Layout: controls NORTH, canvas CENTER, legend EAST ---
        def content = new JPanel(new BorderLayout())
        content.add(controls, BorderLayout.NORTH)
        content.add(new JScrollPane(canvas,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER)
        content.add(legend, BorderLayout.EAST)
        dialog.setContentPane(content)

        // --- Save view action (FR-15) --------------------------------------
        saveViewBtn.addActionListener({
            try {
                def cfg = [
                    axesSwapped:      swapCb.isSelected(),
                    showImplied:      impliedCb.isSelected(),
                    excludeViewpoint: hideVpCb.isSelected(),
                    paletteName:      paletteCombo.getSelectedItem() as String,
                    scopeId:          scope.getDeclaredName(),
                    kind:             'SATISFY',
                    rowType:          swapCb.isSelected()
                                        ? 'com.dassault_systemes.modeler.sysml.model.sysml.RequirementUsage'
                                        : 'com.dassault_systemes.modeler.sysml.model.sysml.PartUsage',
                    colType:          swapCb.isSelected()
                                        ? 'com.dassault_systemes.modeler.sysml.model.sysml.PartUsage'
                                        : 'com.dassault_systemes.modeler.sysml.model.sysml.RequirementUsage',
                ]
                def view = MatrixViewPersistCls.save(scope, project, cfg)
                def msg = "Saved as ViewUsage '${view?.getDeclaredName()}'\n" +
                          "Location: under '${scope.getDeclaredName()}'\n" +
                          "Re-open: select the view in the tree and relaunch MatrixDialog."
                log.info("Saved view ${view?.getDeclaredName()} with ${cfg.size()} keys")
                JOptionPane.showMessageDialog(dialog, msg, 'View saved',
                    JOptionPane.INFORMATION_MESSAGE)
            } catch (Throwable t) {
                log.error('Save view failed: ' + t.toString())
                JOptionPane.showMessageDialog(dialog,
                    "Save failed: ${t.message}", 'Error',
                    JOptionPane.ERROR_MESSAGE)
            }
        } as ActionListener)

        dialog.addWindowListener(new WindowAdapter() {
            @Override void windowClosed(WindowEvent e) {
                log.info('Dialog closed — disposed')
            }
        })

        // Wire the controller: refresh closure rebuilds matrix on every
        // mutation (delete/create). It also re-applies palette and swap state.
        controller.refreshCallback = { refresh() }
        controller.attach()

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

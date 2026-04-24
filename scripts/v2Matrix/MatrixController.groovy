// =============================================================================
// MatrixController.groovy — mouse-event handler for MatrixCanvas (FR-12).
//
// Wires mouse events on the canvas to model mutations and browser-tree
// navigation. Deliberately separate from MatrixCanvas so the canvas can be
// reused in export contexts (iteration 6) where there is no mouse at all.
//
// Interactions (FR-12):
//   - Single-click filled cell   → open backing element in containment tree
//   - Double-click filled cell   → confirm + delete the backing SatisfyRequirementUsage
//   - Double-click empty cell    → confirm + create a SatisfyRequirementUsage
//                                   wired via ReferenceSubsetting (:> operator)
//   - Right-click anywhere       → popup menu: Open / Copy IDs / Delete / Close
//
// Session discipline: every mutation wraps in ONE SessionManager session.
// Open sessions are detected and reused to avoid nesting. Exceptions cancel
// the session; success closes it and triggers refreshCallback() to rebuild.
// =============================================================================

package v2Matrix

import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.dassault_systemes.modeler.kerml.model.kerml.Element
import com.dassault_systemes.modeler.kerml.model.kerml.KerMLFactory
import com.dassault_systemes.modeler.sysml.model.sysml.PartUsage
import com.dassault_systemes.modeler.sysml.model.sysml.RequirementUsage
import com.dassault_systemes.modeler.sysml.model.sysml.SatisfyRequirementUsage
import com.dassault_systemes.modeler.sysml.model.sysml.SysMLFactory
import org.eclipse.emf.ecore.util.EcoreUtil

import javax.swing.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class MatrixController {
    // Dependencies injected by MatrixDialog ------------------------------------
    def canvas              // MatrixCanvas
    Matrix matrix           // current Matrix snapshot
    Project project
    def log                 // SysMLv2Logger instance
    Closure refreshCallback // () → void, rebuilds matrix after mutations
    JDialog owningDialog    // for Close-matrix menu item

    private final SysMLFactory factory = SysMLFactory.eINSTANCE
    private final KerMLFactory kermlFactory = KerMLFactory.eINSTANCE
    private final SessionManager sm = SessionManager.getInstance()

    // ---- Public: install listeners ------------------------------------------
    void attach() {
        canvas.addMouseListener(new MouseAdapter() {
            @Override void mouseClicked(MouseEvent e) { handle(e) }
            @Override void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e)   // cross-platform
            }
            @Override void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e)
            }
        })
    }

    // Called after refreshCallback rebuilds — swap the Matrix reference so
    // subsequent clicks see the fresh data.
    void setMatrix(Matrix m) { this.matrix = m }

    // ---- Mouse dispatch -----------------------------------------------------
    private void handle(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) { showPopup(e); return }
        int[] idx = canvas.cellAt(e.x as int, e.y as int)
        if (idx == null) return
        Element row = (Element) matrix.rows[idx[0]]
        Element col = (Element) matrix.cols[idx[1]]
        Cell cell = matrix.get(row, col)

        if (e.clickCount == 2) {
            if (cell == null) handleDoubleClickEmpty(row, col)
            else handleDoubleClickFilled(cell)
        } else if (e.clickCount == 1 && cell != null) {
            handleSingleClickFilled(cell)
        }
    }

    // ---- Single-click: open backing element in containment tree -------------
    private void handleSingleClickFilled(Cell cell) {
        def elem = pickBackingElement(cell)
        if (elem != null) openInTree(elem)
    }

    // ---- Double-click filled: confirm + delete -------------------------------
    private void handleDoubleClickFilled(Cell cell) {
        def elem = pickBackingElement(cell)
        if (elem == null) return
        def rowN = labelOf(cell.row)
        def colN = labelOf(cell.col)
        int choice = JOptionPane.showConfirmDialog(
            owningDialog,
            "Delete satisfy link (${rowN}, ${colN})?\nBacking: ${labelOf(elem)}",
            'Delete satisfy',
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
        if (choice != JOptionPane.YES_OPTION) return

        inSession('delete satisfy') {
            EcoreUtil.remove(elem as org.eclipse.emf.ecore.EObject)
            log.info("Deleted satisfy ${labelOf(elem)} at (${rowN}, ${colN})")
        }
        refreshCallback?.call()
    }

    // ---- Double-click empty: create a new SatisfyRequirementUsage -----------
    //
    // Naming convention: s_<rowName>_<colName>. Owner = the satisfying feature
    // (whichever side is a PartUsage; falls back to row). ReferenceSubsetting
    // points at the requirement — same pattern the fixture and drone builder use.
    private void handleDoubleClickEmpty(Element row, Element col) {
        // Figure out which side is the satisfier and which is the requirement
        Element satisfier = null, requirement = null
        if (row instanceof PartUsage && col instanceof RequirementUsage) {
            satisfier = row; requirement = col
        } else if (col instanceof PartUsage && row instanceof RequirementUsage) {
            satisfier = col; requirement = row
        } else {
            JOptionPane.showMessageDialog(owningDialog,
                "Cannot create satisfy: need one PartUsage + one RequirementUsage\n" +
                "at (${labelOf(row)}, ${labelOf(col)}).",
                'Create satisfy', JOptionPane.INFORMATION_MESSAGE)
            return
        }

        def satisfyName = 's_' + labelOf(satisfier) + '_' + labelOf(requirement)
        int choice = JOptionPane.showConfirmDialog(
            owningDialog,
            "Create new satisfy '${satisfyName}'?\n" +
            "  owner     = ${labelOf(satisfier)} (satisfying feature)\n" +
            "  satisfied = ${labelOf(requirement)}",
            'Create satisfy',
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)
        if (choice != JOptionPane.YES_OPTION) return

        inSession('create satisfy') {
            def s = factory.createSatisfyRequirementUsage()
            s.setDeclaredName(satisfyName)
            s.setOwner(satisfier)
            def rs = kermlFactory.createReferenceSubsetting()
            rs.setOwner(s)
            rs.setSubsettingFeature(s)
            rs.setSubsettedFeature(requirement)
            log.info("Created ${satisfyName} (owner=${labelOf(satisfier)}, req=${labelOf(requirement)})")
        }
        refreshCallback?.call()
    }

    // ---- Right-click context menu -------------------------------------------
    private void showPopup(MouseEvent e) {
        int[] idx = canvas.cellAt(e.x as int, e.y as int)
        Element row = null, col = null
        Cell cell = null
        if (idx != null) {
            row = (Element) matrix.rows[idx[0]]
            col = (Element) matrix.cols[idx[1]]
            cell = matrix.get(row, col)
        }

        JPopupMenu menu = new JPopupMenu()

        if (cell != null) {
            def openItem = new JMenuItem('Open backing element in tree')
            openItem.addActionListener({ openInTree(pickBackingElement(cell)) })
            menu.add(openItem)

            def copyItem = new JMenuItem('Copy element IDs')
            copyItem.addActionListener({ copyCellIds(cell) })
            menu.add(copyItem)

            menu.addSeparator()

            def delItem = new JMenuItem('Delete satisfy…')
            delItem.addActionListener({ handleDoubleClickFilled(cell) })
            menu.add(delItem)
        } else if (row != null && col != null) {
            def createItem = new JMenuItem("Create satisfy (${labelOf(row)}, ${labelOf(col)})…")
            createItem.addActionListener({ handleDoubleClickEmpty(row, col) })
            menu.add(createItem)
        }

        if (menu.getComponentCount() > 0) menu.addSeparator()

        def closeItem = new JMenuItem('Close matrix')
        closeItem.addActionListener({ owningDialog?.dispose() })
        menu.add(closeItem)

        menu.show(canvas, e.x as int, e.y as int)
    }

    // ---- Helpers ------------------------------------------------------------
    private Element pickBackingElement(Cell cell) {
        if (cell?.backing == null || cell.backing.isEmpty()) return null
        // Default policy: first backing. Cells with count > 1 have their other
        // backings reachable via right-click → Copy Element IDs.
        cell.backing[0]
    }

    private void openInTree(Element elem) {
        if (elem == null) return
        try {
            def browser = Application.getInstance().getMainFrame().getBrowser()
            def tree = browser.getContainmentTree()
            if (tree.respondsTo('selectNode')) {
                tree.selectNode(elem)
            } else if (tree.respondsTo('select')) {
                tree.select(elem)
            } else {
                log.warn("No known select API on ${tree.getClass().getSimpleName()}")
            }
            log.info("Opened in tree: ${labelOf(elem)}")
        } catch (Throwable t) {
            log.warn('Open-in-tree failed: ' + t.message)
        }
    }

    private void copyCellIds(Cell cell) {
        def ids = cell.backing.collect { e ->
            (e.respondsTo('getElementID') ? e.getElementID() : null) ?:
            (e.respondsTo('getLocalID')   ? e.getLocalID()   : null) ?:
            labelOf(e)
        }.join('\n')
        def ss = new StringSelection(ids)
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, ss)
        log.info("Copied ${cell.backing.size()} id(s) to clipboard")
    }

    // Shared session wrapper. Reuses an open session instead of nesting.
    private void inSession(String name, Closure body) {
        boolean opened = false
        try {
            if (!sm.isSessionCreated(project)) {
                sm.createSession(project, name)
                opened = true
            }
            body.call()
            if (opened) sm.closeSession(project)
        } catch (Throwable t) {
            log.error("Session '${name}' failed: " + t.toString())
            try { if (opened) sm.cancelSession(project) } catch (Exception ignored) {}
            JOptionPane.showMessageDialog(owningDialog,
                "Operation failed: ${t.message}", 'Error',
                JOptionPane.ERROR_MESSAGE)
        }
    }

    private static String labelOf(Element e) {
        if (e == null) return '(null)'
        try {
            def n = e.getDeclaredName()
            if (n != null && !n.isEmpty()) return n
        } catch (Exception ignored) {}
        try { return e.getHumanName() ?: e.getClass().getSimpleName() } catch (Exception ignored) {}
        e.getClass().getSimpleName()
    }
}

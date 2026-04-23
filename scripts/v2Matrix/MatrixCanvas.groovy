// =============================================================================
// MatrixCanvas.groovy — Java2D renderer for a v2Matrix.Matrix.
//
// A JPanel that paints a traceability matrix: column header row at top,
// row header column at left, and filled cells in the grid. Pure view —
// MatrixController (iteration 4) will handle mouse events and wire to
// MatrixModel updates.
//
// Rendering (FR-8/9/10):
// - Filled cell: one 45° arrow from lower-left → upper-right in the cell.
//   (FR-9 direction is derived from axis orientation set by the controller.)
// - Badge count: if cell.count > 1, draw the number in the upper-left of cell.
// - Implied cells (FR-6): dashed outline + different fill color.
// - Subject cells (FR-7): small 'S' marker in lower-right of cell.
// - Hover highlight drawn by the controller, not here.
//
// Palette (FR-10):
//   default: direct=blue arrow, implied=gray dashed outline
//   colorblindSafe: direct=orange arrow, implied=purple dashed outline
//
// Sizing:
//   cellSize default 32x32; colHeader height = row header width = configurable.
//   Labels rotated 90° for column headers if they would overflow.
// =============================================================================

package v2Matrix

import com.dassault_systemes.modeler.kerml.model.kerml.Element
import java.awt.*
import java.awt.geom.*
import javax.swing.JPanel
import javax.swing.SwingConstants

class Palette {
    Color direct
    Color implied
    Color subject
    Color grid
    Color header
    Color headerText
    Color cellText

    static Palette standard() {
        new Palette(
            direct: new Color(0x1976D2),   // blue
            implied: new Color(0x9E9E9E),   // gray
            subject: new Color(0x7B1FA2),   // purple
            grid: new Color(0xCCCCCC),
            header: new Color(0xF5F5F5),
            headerText: new Color(0x212121),
            cellText: new Color(0x212121)
        )
    }

    static Palette colorblindSafe() {
        new Palette(
            direct: new Color(0xE67E22),   // orange
            implied: new Color(0x8E44AD),   // purple (dashed)
            subject: new Color(0x27AE60),   // green
            grid: new Color(0xCCCCCC),
            header: new Color(0xECEFF1),
            headerText: new Color(0x212121),
            cellText: new Color(0x212121)
        )
    }
}

class MatrixCanvas extends JPanel {
    Matrix matrix
    Palette palette = Palette.standard()

    int cellSize = 32
    int rowHeaderWidth = 140
    int colHeaderHeight = 120
    Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11)
    Font badgeFont = new Font(Font.SANS_SERIF, Font.BOLD, 10)

    // Set by controller
    boolean showAnnotations = true
    boolean showBadges = true

    MatrixCanvas(Matrix m = null) {
        this.matrix = m
        setBackground(Color.WHITE)
        if (matrix != null) updatePreferredSize()
    }

    void setMatrix(Matrix m) {
        this.matrix = m
        updatePreferredSize()
        revalidate()
        repaint()
    }

    private void updatePreferredSize() {
        int w = rowHeaderWidth + (matrix?.cols?.size() ?: 0) * cellSize + 2
        int h = colHeaderHeight + (matrix?.rows?.size() ?: 0) * cellSize + 2
        setPreferredSize(new Dimension(Math.max(w, 400), Math.max(h, 300)))
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g)
        if (matrix == null || matrix.rows.isEmpty() || matrix.cols.isEmpty()) {
            paintEmpty((Graphics2D) g)
            return
        }
        Graphics2D g2 = (Graphics2D) g.create()
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            paintColumnHeaders(g2)
            paintRowHeaders(g2)
            paintGrid(g2)
            paintCells(g2)
        } finally {
            g2.dispose()
        }
    }

    private void paintEmpty(Graphics2D g2) {
        g2.setColor(Color.GRAY)
        g2.setFont(labelFont.deriveFont(Font.ITALIC, 14f))
        String msg = 'Matrix is empty — no rows or columns found in scope.'
        FontMetrics fm = g2.getFontMetrics()
        int x = (getWidth() - fm.stringWidth(msg)) / 2 as int
        int y = getHeight() / 2 as int
        g2.drawString(msg, x, y)
    }

    private void paintColumnHeaders(Graphics2D g2) {
        g2.setColor(palette.header)
        g2.fillRect(rowHeaderWidth, 0, matrix.cols.size() * cellSize, colHeaderHeight)
        g2.setColor(palette.headerText)
        g2.setFont(labelFont)
        FontMetrics fm = g2.getFontMetrics()
        for (int i = 0; i < matrix.cols.size(); i++) {
            def col = matrix.cols[i]
            String name = labelFor(col)
            int cx = rowHeaderWidth + i * cellSize + cellSize / 2 as int
            int cy = colHeaderHeight - 6
            // Rotate 90° counter-clockwise so the text reads bottom-to-top
            def old = g2.getTransform()
            g2.translate(cx, cy)
            g2.rotate(-Math.PI / 2)
            int maxLen = colHeaderHeight - 10
            String shown = ellipsize(name, fm, maxLen)
            g2.drawString(shown, 0, fm.getAscent() / 3 as int)
            g2.setTransform(old)
        }
    }

    private void paintRowHeaders(Graphics2D g2) {
        g2.setColor(palette.header)
        g2.fillRect(0, colHeaderHeight, rowHeaderWidth, matrix.rows.size() * cellSize)
        g2.setColor(palette.headerText)
        g2.setFont(labelFont)
        FontMetrics fm = g2.getFontMetrics()
        for (int i = 0; i < matrix.rows.size(); i++) {
            def row = matrix.rows[i]
            String name = labelFor(row)
            int y = colHeaderHeight + i * cellSize + (cellSize + fm.getAscent()) / 2 - 2 as int
            String shown = ellipsize(name, fm, rowHeaderWidth - 8)
            g2.drawString(shown, 4, y)
        }
    }

    private void paintGrid(Graphics2D g2) {
        g2.setColor(palette.grid)
        int x0 = rowHeaderWidth
        int y0 = colHeaderHeight
        int w = matrix.cols.size() * cellSize
        int h = matrix.rows.size() * cellSize
        for (int i = 0; i <= matrix.cols.size(); i++) {
            int x = x0 + i * cellSize
            g2.drawLine(x, y0, x, y0 + h)
        }
        for (int i = 0; i <= matrix.rows.size(); i++) {
            int y = y0 + i * cellSize
            g2.drawLine(x0, y, x0 + w, y)
        }
        // Outer border
        g2.drawLine(0, colHeaderHeight, x0 + w, colHeaderHeight)
        g2.drawLine(rowHeaderWidth, 0, rowHeaderWidth, y0 + h)
    }

    private void paintCells(Graphics2D g2) {
        def rowIdx = matrix.rows.withIndex().collectEntries { e, i -> [(e): i] }
        def colIdx = matrix.cols.withIndex().collectEntries { e, i -> [(e): i] }

        for (Cell cell : matrix.cells.values()) {
            Integer ri = rowIdx[cell.row]
            Integer ci = colIdx[cell.col]
            if (ri == null || ci == null) continue
            int x = rowHeaderWidth + ci * cellSize
            int y = colHeaderHeight + ri * cellSize
            paintCell(g2, cell, x, y, cellSize)
        }
    }

    private void paintCell(Graphics2D g2, Cell cell, int x, int y, int s) {
        boolean direct = cell.isDirect()
        boolean implied = cell.isImplied()
        boolean subject = cell.isSubject()

        // Background tint
        if (direct) {
            g2.setColor(new Color(palette.direct.red, palette.direct.green, palette.direct.blue, 24))
            g2.fillRect(x + 1, y + 1, s - 1, s - 1)
        } else if (implied) {
            g2.setColor(new Color(palette.implied.red, palette.implied.green, palette.implied.blue, 18))
            g2.fillRect(x + 1, y + 1, s - 1, s - 1)
        }

        // Arrow lower-left → upper-right (FR-9 default direction; swap-axes flips it)
        Stroke saved = g2.getStroke()
        Color arrowColor = direct ? palette.direct : (implied ? palette.implied : palette.subject)
        g2.setColor(arrowColor)
        if (implied && !direct) {
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, [4f, 3f] as float[], 0f))
        } else {
            g2.setStroke(new BasicStroke(2f))
        }
        int pad = 6
        int x1 = x + pad
        int y1 = y + s - pad
        int x2 = x + s - pad
        int y2 = y + pad
        g2.drawLine(x1, y1, x2, y2)
        drawArrowhead(g2, x2, y2, x1, y1)
        g2.setStroke(saved)

        // Badge count (FR-8)
        if (showBadges && cell.count > 1) {
            g2.setColor(new Color(0xFFFFFF))
            int r = 10
            g2.fillOval(x + 1, y + 1, r, r)
            g2.setColor(arrowColor)
            g2.drawOval(x + 1, y + 1, r, r)
            g2.setFont(badgeFont)
            FontMetrics fm = g2.getFontMetrics()
            String txt = cell.count.toString()
            int tx = x + 1 + (r - fm.stringWidth(txt)) / 2 as int
            int ty = y + 1 + (r + fm.getAscent()) / 2 - 1 as int
            g2.drawString(txt, tx, ty)
        }

        // Subject marker (FR-7)
        if (subject && showAnnotations) {
            g2.setColor(palette.subject)
            g2.setFont(badgeFont)
            g2.drawString('S', x + s - 10, y + s - 4)
        }
    }

    private static void drawArrowhead(Graphics2D g2, int tipX, int tipY, int fromX, int fromY) {
        double angle = Math.atan2(tipY - fromY, tipX - fromX)
        int size = 6
        int ax = (int) (tipX - size * Math.cos(angle - Math.PI / 6))
        int ay = (int) (tipY - size * Math.sin(angle - Math.PI / 6))
        int bx = (int) (tipX - size * Math.cos(angle + Math.PI / 6))
        int by = (int) (tipY - size * Math.sin(angle + Math.PI / 6))
        Path2D.Double p = new Path2D.Double()
        p.moveTo(tipX, tipY); p.lineTo(ax, ay); p.lineTo(bx, by); p.closePath()
        g2.fill(p)
    }

    // Translate a mouse point into a (row, col) index — used by controller.
    int[] cellAt(int px, int py) {
        if (matrix == null) return null
        if (px < rowHeaderWidth || py < colHeaderHeight) return null
        int ci = (px - rowHeaderWidth) / cellSize as int
        int ri = (py - colHeaderHeight) / cellSize as int
        if (ri < 0 || ri >= matrix.rows.size()) return null
        if (ci < 0 || ci >= matrix.cols.size()) return null
        [ri, ci] as int[]
    }

    private static String labelFor(Element e) {
        if (e == null) return '(null)'
        String name = null
        try { name = e.getDeclaredName() } catch (Exception ignored) {}
        if (name != null && !name.isEmpty()) return name
        try { return e.getHumanName() ?: e.getClass().getSimpleName() } catch (Exception ignored) {}
        e.getClass().getSimpleName()
    }

    private static String ellipsize(String s, FontMetrics fm, int maxPx) {
        if (fm.stringWidth(s) <= maxPx) return s
        String ell = '…'
        int ew = fm.stringWidth(ell)
        for (int i = s.length() - 1; i > 0; i--) {
            String sub = s.substring(0, i)
            if (fm.stringWidth(sub) + ew <= maxPx) return sub + ell
        }
        return ell
    }
}

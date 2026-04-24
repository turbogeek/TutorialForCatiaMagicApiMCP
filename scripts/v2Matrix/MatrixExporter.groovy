// =============================================================================
// MatrixExporter.groovy — four export formats for a Matrix (FR-17).
//
//   PNG  : raster dump of the live MatrixCanvas (canvas.paint to BufferedImage)
//   SVG  : hand-rolled vector text; mirrors MatrixCanvas.paintCell output.
//          No Apache Batik required (Cameo classpath is fragile).
//   HTML : thin self-contained wrapper around the SVG.
//   CSV  : one row per matrix row, one col per matrix col. Cells contain
//          semicolon-joined declared names of backing elements — round-trip
//          friendly since re-reading the .mdzip restores element identity.
//
// All four honor the current Palette and the axesSwapped flag (arrow direction
// flips in SVG/HTML just like it does on screen).
// =============================================================================

package v2Matrix

import com.dassault_systemes.modeler.kerml.model.kerml.Element
import javax.imageio.ImageIO
import javax.swing.JComponent
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.image.BufferedImage

class MatrixExporter {

    // ---- PNG ------------------------------------------------------------------
    // Uses JComponent.paint() (public) which dispatches to paintComponent().
    // We force a setBounds before painting so the component's layout is valid.
    static void exportPng(JComponent canvas, File out) {
        Dimension sz = canvas.getPreferredSize()
        int w = Math.max(sz.width, 400)
        int h = Math.max(sz.height, 300)
        def img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        Graphics2D g2 = img.createGraphics()
        try {
            canvas.setBounds(0, 0, w, h)
            canvas.doLayout()
            // Paint the canvas background first (setBackground might not fill ARGB)
            g2.setColor(canvas.getBackground() ?: Color.WHITE)
            g2.fillRect(0, 0, w, h)
            canvas.paint(g2)
        } finally {
            g2.dispose()
        }
        out.parentFile?.mkdirs()
        ImageIO.write(img, 'PNG', out)
    }

    // ---- CSV ------------------------------------------------------------------
    // Layout:
    //   ""        , col1name , col2name , ...
    //   row1name  , s1;s2    ,          , ...
    //   row2name  ,          , s3       , ...
    // Cell content = semicolon-joined backing declared names (empty for no cell).
    // Quoting: values with commas, quotes, or newlines are wrapped in double
    // quotes with inner quotes doubled (standard RFC-4180).
    static void exportCsv(Matrix m, File out) {
        StringBuilder sb = new StringBuilder()
        // Header row
        sb.append('')
        m.cols.each { c -> sb.append(',').append(csvEscape(labelOf(c))) }
        sb.append('\n')
        // Body rows
        m.rows.each { r ->
            sb.append(csvEscape(labelOf(r)))
            m.cols.each { c ->
                sb.append(',')
                def cell = m.get(r as Element, c as Element)
                if (cell != null) {
                    def ids = cell.backing.collect { labelOf(it as Element) }.join(';')
                    sb.append(csvEscape(ids))
                }
            }
            sb.append('\n')
        }
        out.parentFile?.mkdirs()
        out.setText(sb.toString(), 'UTF-8')
    }

    // ---- SVG ------------------------------------------------------------------
    // Hand-rolled, mirrors MatrixCanvas.paintCell. Geometry echoes the canvas:
    // rowHeaderWidth, colHeaderHeight, cellSize identical to the on-screen layout.
    static void exportSvg(Matrix m, Palette palette, File out, boolean swapped,
                          int cellSize = 32, int rowHeaderWidth = 140) {
        // Compute colHeaderHeight from longest label (same formula as canvas)
        int maxLabelPx = 0
        m.cols.each { c ->
            int w = labelOf(c as Element).length() * 7  // rough approximation, no FontMetrics
            if (w > maxLabelPx) maxLabelPx = w
        }
        int colHeaderHeight = Math.max(60, Math.min(((maxLabelPx * 0.708) as int) + 16, 260))

        int totalW = rowHeaderWidth + m.cols.size() * cellSize + 2
        int totalH = colHeaderHeight + m.rows.size() * cellSize + 2

        StringBuilder sb = new StringBuilder()
        sb.append('<?xml version="1.0" encoding="UTF-8" standalone="no"?>\n')
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${totalW}\" height=\"${totalH}\">\n")
        sb.append("  <!-- Palette: ${palette.name}; axesSwapped=${swapped} -->\n")

        // Background
        svgRect(sb, 0, 0, totalW, totalH, colorHex(palette.canvasBackground), null, 0)

        // Column headers (45° rotation)
        svgRect(sb, rowHeaderWidth, 0, m.cols.size() * cellSize, colHeaderHeight,
                colorHex(palette.header), null, 0)
        m.cols.eachWithIndex { c, i ->
            def name = labelOf(c as Element)
            int anchorX = rowHeaderWidth + i * cellSize + (cellSize.intdiv(2))
            int anchorY = colHeaderHeight - 4
            sb.append("  <text x=\"${anchorX + 2}\" y=\"${anchorY + 4}\"")
              .append(" font-family=\"sans-serif\" font-size=\"11\"")
              .append(" fill=\"${colorHex(palette.headerText)}\"")
              .append(" transform=\"rotate(-45 ${anchorX} ${anchorY})\">")
              .append(svgEscape(name))
              .append("</text>\n")
        }

        // Row headers
        svgRect(sb, 0, colHeaderHeight, rowHeaderWidth, m.rows.size() * cellSize,
                colorHex(palette.header), null, 0)
        m.rows.eachWithIndex { r, i ->
            def name = labelOf(r as Element)
            int y = colHeaderHeight + i * cellSize + (cellSize.intdiv(2)) + 4
            sb.append("  <text x=\"4\" y=\"${y}\"")
              .append(" font-family=\"sans-serif\" font-size=\"11\"")
              .append(" fill=\"${colorHex(palette.headerText)}\">")
              .append(svgEscape(name))
              .append("</text>\n")
        }

        // Grid
        int x0 = rowHeaderWidth, y0 = colHeaderHeight
        int w = m.cols.size() * cellSize, h = m.rows.size() * cellSize
        (0..m.cols.size()).each { i ->
            int x = x0 + i * cellSize
            svgLine(sb, x, y0, x, y0 + h, colorHex(palette.grid), 1)
        }
        (0..m.rows.size()).each { i ->
            int y = y0 + i * cellSize
            svgLine(sb, x0, y, x0 + w, y, colorHex(palette.grid), 1)
        }

        // Cells
        def rowIdx = [:] ; m.rows.eachWithIndex { r, i -> rowIdx[r] = i }
        def colIdx = [:] ; m.cols.eachWithIndex { c, i -> colIdx[c] = i }
        m.cells.values().each { cell ->
            Integer ri = rowIdx[cell.row]
            Integer ci = colIdx[cell.col]
            if (ri == null || ci == null) return
            int x = rowHeaderWidth + ci * cellSize
            int y = colHeaderHeight + ri * cellSize
            svgCell(sb, cell, x, y, cellSize, palette, swapped)
        }

        sb.append('</svg>\n')
        out.parentFile?.mkdirs()
        out.setText(sb.toString(), 'UTF-8')
    }

    // ---- HTML -----------------------------------------------------------------
    static void exportHtml(Matrix m, Palette palette, File out, boolean swapped,
                           String title = 'v2Matrix export') {
        File tmpSvg = File.createTempFile('v2matrix_', '.svg')
        try {
            exportSvg(m, palette, tmpSvg, swapped)
            String svgContent = tmpSvg.getText('UTF-8')
            // Strip <?xml ... ?> prolog when embedding in HTML
            int idx = svgContent.indexOf('<svg')
            if (idx > 0) svgContent = svgContent.substring(idx)

            StringBuilder sb = new StringBuilder()
            sb.append('<!DOCTYPE html>\n<html lang="en"><head>\n')
            sb.append('  <meta charset="UTF-8">\n')
            sb.append("  <title>${htmlEscape(title)}</title>\n")
            sb.append('  <style>body{font-family:sans-serif;background:' )
              .append(colorHex(palette.canvasBackground))
              .append(';color:').append(colorHex(palette.headerText)).append(';margin:24px;}\n')
              .append('  h1{font-size:18px;} .meta{font-size:12px;color:#888;}</style>\n')
            sb.append('</head><body>\n')
            sb.append("<h1>${htmlEscape(title)}</h1>\n")
            sb.append("<p class=\"meta\">rows=${m.rows.size()} cols=${m.cols.size()} ")
              .append("cells=${m.cellCount()} direct=${m.directCount()} ")
              .append("implied=${m.impliedCount()} palette=${htmlEscape(palette.name)} ")
              .append("swapped=${swapped}</p>\n")
            sb.append(svgContent)
            sb.append('\n</body></html>\n')
            out.parentFile?.mkdirs()
            out.setText(sb.toString(), 'UTF-8')
        } finally {
            tmpSvg.delete()
        }
    }

    // ---- Low-level SVG primitives --------------------------------------------
    private static void svgRect(StringBuilder sb, int x, int y, int w, int h,
                                String fill, String stroke, int strokeW) {
        sb.append("  <rect x=\"${x}\" y=\"${y}\" width=\"${w}\" height=\"${h}\"")
        if (fill) sb.append(" fill=\"${fill}\"")
        if (stroke) sb.append(" stroke=\"${stroke}\" stroke-width=\"${strokeW}\"")
        sb.append("/>\n")
    }

    private static void svgLine(StringBuilder sb, int x1, int y1, int x2, int y2,
                                String stroke, int strokeW, String dash = null) {
        sb.append("  <line x1=\"${x1}\" y1=\"${y1}\" x2=\"${x2}\" y2=\"${y2}\"")
          .append(" stroke=\"${stroke}\" stroke-width=\"${strokeW}\"")
        if (dash) sb.append(" stroke-dasharray=\"${dash}\"")
        sb.append("/>\n")
    }

    private static void svgCell(StringBuilder sb, Cell cell, int x, int y, int s,
                                 Palette palette, boolean swapped) {
        boolean direct = cell.isDirect()
        boolean implied = cell.isImplied()
        boolean subject = cell.isSubject()
        String arrowColor = colorHex(
            direct ? palette.direct : (implied ? palette.implied : palette.subject))

        // Light background tint
        if (direct) {
            svgRect(sb, x + 1, y + 1, s - 1, s - 1,
                    tintHex(palette.direct, 24), null, 0)
        } else if (implied) {
            svgRect(sb, x + 1, y + 1, s - 1, s - 1,
                    tintHex(palette.implied, 18), null, 0)
        }

        // Arrow endpoints (flip on swap — same as MatrixCanvas)
        int pad = 6
        int tailX, tailY, tipX, tipY
        if (swapped) {
            tailX = x + s - pad; tailY = y + pad
            tipX  = x + pad;     tipY  = y + s - pad
        } else {
            tailX = x + pad;     tailY = y + s - pad
            tipX  = x + s - pad; tipY  = y + pad
        }
        svgLine(sb, tailX, tailY, tipX, tipY, arrowColor, 2,
                (implied && !direct) ? '4,3' : null)

        // Arrowhead as a small polygon
        double angle = Math.atan2(tipY - tailY, tipX - tailX)
        int size = 6
        int ax = (tipX - size * Math.cos(angle - Math.PI / 6)) as int
        int ay = (tipY - size * Math.sin(angle - Math.PI / 6)) as int
        int bx = (tipX - size * Math.cos(angle + Math.PI / 6)) as int
        int by = (tipY - size * Math.sin(angle + Math.PI / 6)) as int
        sb.append("  <polygon points=\"${tipX},${tipY} ${ax},${ay} ${bx},${by}\"")
          .append(" fill=\"${arrowColor}\"/>\n")

        // Badge count
        if (cell.count > 1) {
            int cx = x + 6, cy = y + 6, r = 6
            sb.append("  <circle cx=\"${cx}\" cy=\"${cy}\" r=\"${r}\"")
              .append(" fill=\"#ffffff\" stroke=\"${arrowColor}\" stroke-width=\"1\"/>\n")
            sb.append("  <text x=\"${cx}\" y=\"${cy + 3}\" font-family=\"sans-serif\"")
              .append(" font-size=\"9\" font-weight=\"bold\" text-anchor=\"middle\"")
              .append(" fill=\"${arrowColor}\">${cell.count}</text>\n")
        }

        // Subject marker
        if (subject) {
            sb.append("  <text x=\"${x + s - 8}\" y=\"${y + s - 4}\" font-family=\"sans-serif\"")
              .append(" font-size=\"10\" font-weight=\"bold\" fill=\"${colorHex(palette.subject)}\">S</text>\n")
        }
    }

    // ---- Helpers --------------------------------------------------------------
    private static String colorHex(Color c) {
        if (c == null) return '#000000'
        String.format('#%02x%02x%02x', c.red, c.green, c.blue)
    }

    private static String tintHex(Color c, int alphaOpacityApprox) {
        // Flatten alpha against white for SVG (simpler than emitting rgba)
        double a = alphaOpacityApprox / 255.0
        int r = (c.red   * a + 255 * (1 - a)) as int
        int g = (c.green * a + 255 * (1 - a)) as int
        int b = (c.blue  * a + 255 * (1 - a)) as int
        String.format('#%02x%02x%02x', r, g, b)
    }

    private static String csvEscape(String v) {
        if (v == null) return ''
        if (v.contains(',') || v.contains('"') || v.contains('\n') || v.contains('\r')) {
            return '"' + v.replace('"', '""') + '"'
        }
        v
    }

    private static String svgEscape(String v) {
        if (v == null) return ''
        v.replace('&','&amp;').replace('<','&lt;').replace('>','&gt;')
         .replace('"','&quot;').replace("'",'&apos;')
    }

    private static String htmlEscape(String v) { svgEscape(v) }

    private static String labelOf(Element e) {
        if (e == null) return ''
        try {
            def n = e.getDeclaredName()
            if (n != null && !n.isEmpty()) return n
        } catch (Exception ignored) {}
        try { return e.getHumanName() ?: e.getClass().getSimpleName() } catch (Exception ignored) {}
        e.getClass().getSimpleName()
    }
}

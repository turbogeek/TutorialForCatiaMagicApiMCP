import com.dassault_systemes.modeler.kerml.model.kerml.Namespace
import com.dassault_systemes.modeler.kerml.model.kerml.Element
import com.dassault_systemes.modeler.sysml.model.sysml.RequirementUsage
import com.dassault_systemes.modeler.sysml.model.sysml.SatisfyRequirementUsage
import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project

import javax.swing.*
import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.util.List

// -----------------------------------------------------------------------------------
// TUTORIAL: Requirement Satisfy Matrix (Graphics2D)
// 
// This script demonstrates generating a GUI Traceability Matrix finding all 
// requirements and elements, crossing their SatisfyRequirementUsage relationships,
// using custom Java2D Graphics rendering on a JPanel canvas.
// -----------------------------------------------------------------------------------

String scriptDir = "E:\\_Documents\\git\\SysMLv2ClientAPI\\scripts"
File loggerFile = new File(scriptDir, "SysMLv2Logger.groovy")
def SysMLv2Logger = new GroovyClassLoader(getClass().getClassLoader()).parseClass(loggerFile)
def logger = SysMLv2Logger.newInstance("ReqMatrixGUI_Graphics")

try {
    Project project = Application.getInstance().getProject()
    if (project == null) {
        logger.error("No active MagicDraw project!")
        return
    }

    def browser = Application.getInstance().getMainFrame().getBrowser()
    def selectedNodes = browser.getActiveTree()?.getSelectedNodes()
    Namespace namespace = null
    
    if (selectedNodes != null && selectedNodes.length > 0) {
        def element = selectedNodes[0].getUserObject()
        if (element instanceof Namespace) {
            namespace = (Namespace) element
        }
    }
    
    if (namespace == null) {
        logger.error("Please select a Namespace in the Containment Tree before running this script.")
        return
    }

    logger.info("--- Generating GUI satisfy matrix (Graphics Canvas) for : " + namespace.getHumanName() + " ---")

    Set<RequirementUsage> allRequirements = new HashSet<RequirementUsage>()
    Set<Element> satisfyingElements = new HashSet<Element>()
    Map<Element, Map<RequirementUsage, SatisfyRequirementUsage>> satisfyMap = new HashMap<>()

    def analyzeElements
    analyzeElements = { Element currentElement ->
        if (currentElement instanceof RequirementUsage && !(currentElement instanceof SatisfyRequirementUsage)) {
            allRequirements.add((RequirementUsage) currentElement)
        }
        
        if (currentElement instanceof SatisfyRequirementUsage) {
            SatisfyRequirementUsage satisfy = (SatisfyRequirementUsage) currentElement
            def satisfiedReq = satisfy.getSatisfiedRequirement()
            
            Element satisfyingFeature = satisfy.getSatisfyingFeature()
            if (satisfyingFeature == null) {
                satisfyingFeature = satisfy.getOwner()
            }
            
            if (satisfyingFeature != null && satisfiedReq != null) {
                satisfyingElements.add(satisfyingFeature)
                
                Map<RequirementUsage, SatisfyRequirementUsage> reqs = satisfyMap.get(satisfyingFeature)
                if (reqs == null) {
                    reqs = new HashMap<RequirementUsage, SatisfyRequirementUsage>()
                    satisfyMap.put(satisfyingFeature, reqs)
                }
                reqs.put(satisfiedReq, satisfy)
            }
        }
        for (Element child : currentElement.getOwnedElement()) {
            analyzeElements(child)
        }
    }

    // Start analyzing from the selected namespace
    analyzeElements(namespace)
    
    List<RequirementUsage> reqList = new ArrayList<>(allRequirements)
    reqList.sort { a, b -> (a.getName() ?: "Unnamed").compareToIgnoreCase(b.getName() ?: "Unnamed") }
    
    List<Element> elemList = new ArrayList<>(satisfyingElements)
    elemList.sort { a, b -> (a.getName() ?: "Unnamed").compareToIgnoreCase(b.getName() ?: "Unnamed") }
    
    if (reqList.isEmpty()) {
        logger.info("No Requirements found in the selected Namespace.")
        return
    }

    // Setup the Canvas GUI
    SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            String title = "Satisfy Matrix (Graphics): " + namespace.getHumanName()
            JDialog dialog = new JDialog(Application.getInstance().getMainFrame(), title, false)
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE)
            dialog.setLayout(new BorderLayout())

            JPanel canvas = new JPanel() {
                
                int cellWidth = 30
                int rowHeight = 25
                int reqNameColWidth = 250
                int headerHeight = 150 // Enough space for 45 deg rotated text
                
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g)
                    Graphics2D g2 = (Graphics2D) g
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 12))
                    FontMetrics fm = g2.getFontMetrics()
                    
                    int startX = reqNameColWidth
                    int startY = headerHeight
                    
                    // 1. Draw Column Headers (Top row, 45 degree angle)
                    g2.setColor(Color.BLACK)
                    for (int c = 0; c < elemList.size(); c++) {
                        String colName = elemList.get(c).getName() ?: "Unnamed"
                        
                        // Push transform
                        AffineTransform orig = g2.getTransform()
                        
                        // Translate to center bottom of header cell
                        int cellCenterX = startX + (c * cellWidth) + (cellWidth / 2)
                        g2.translate(cellCenterX, startY - 5)
                        
                        // Rotate -45 degrees
                        g2.rotate(-Math.PI / 4.0)
                        
                        g2.drawString(colName, 0, 0)
                        
                        // Pop transform
                        g2.setTransform(orig)
                        
                        // Draw column line
                        g2.setColor(Color.LIGHT_GRAY)
                        g2.drawLine(startX + (c * cellWidth), startY, startX + (c * cellWidth), startY + (reqList.size() * rowHeight))
                        g2.setColor(Color.BLACK)
                    }
                    
                    // Draw last right vertical line
                    g2.setColor(Color.LIGHT_GRAY)
                    g2.drawLine(startX + (elemList.size() * cellWidth), startY, startX + (elemList.size() * cellWidth), startY + (reqList.size() * rowHeight))
                    g2.setColor(Color.BLACK)
                    
                    // 2. Draw Rows (Requirement Names + Symbols)
                    for (int r = 0; r < reqList.size(); r++) {
                        RequirementUsage req = reqList.get(r)
                        String rowName = req.getName() ?: "Unnamed"
                        
                        int textY = startY + (r * rowHeight) + ((rowHeight - fm.getHeight()) / 2) + fm.getAscent()
                        
                        // Draw Requirement Name
                        g2.setColor(Color.BLACK)
                        // truncate if too long
                        String drawName = rowName
                        if (fm.stringWidth(drawName) > reqNameColWidth - 10) {
                            while(drawName.length() > 3 && fm.stringWidth(drawName + "...") > reqNameColWidth - 10) {
                                drawName = drawName.substring(0, drawName.length() - 1)
                            }
                            drawName += "..."
                        }
                        g2.drawString(drawName, 5, textY)
                        
                        // Draw row top line
                        g2.setColor(Color.LIGHT_GRAY)
                        g2.drawLine(0, startY + (r * rowHeight), startX + (elemList.size() * cellWidth), startY + (r * rowHeight))
                        
                        // Draw cells
                        for (int c = 0; c < elemList.size(); c++) {
                            Element feat = elemList.get(c)
                            Map<RequirementUsage, SatisfyRequirementUsage> satisfied = satisfyMap.get(feat)
                            
                            int cellX = startX + (c * cellWidth)
                            int cellY = startY + (r * rowHeight)
                            
                            if (satisfied != null && satisfied.containsKey(req)) {
                                // Draw Green Circle
                                g2.setColor(Color.GREEN.darker())
                                int ovalSize = Math.min(cellWidth, rowHeight) - 10
                                g2.fillOval(cellX + (int)((cellWidth - ovalSize)/2), cellY + (int)((rowHeight - ovalSize)/2), ovalSize, ovalSize)
                            } else {
                                // Draw Red X
                                g2.setColor(Color.RED)
                                g2.setStroke(new BasicStroke(2))
                                int padding = 8
                                g2.drawLine(cellX + padding, cellY + padding, cellX + cellWidth - padding, cellY + rowHeight - padding)
                                g2.drawLine(cellX + cellWidth - padding, cellY + padding, cellX + padding, cellY + rowHeight - padding)
                                g2.setStroke(new BasicStroke(1))
                            }
                        }
                    }
                    
                    // Draw bottom line
                    g2.setColor(Color.LIGHT_GRAY)
                    g2.drawLine(0, startY + (reqList.size() * rowHeight), startX + (elemList.size() * cellWidth), startY + (reqList.size() * rowHeight))
                }
                
                @Override
                public Dimension getPreferredSize() {
                    // Added a 30px margin to the right and bottom to avoid scrollbars occluding the matrix
                    return new Dimension(reqNameColWidth + (elemList.size() * cellWidth) + 80, headerHeight + (reqList.size() * rowHeight) + 80)
                }
            }
            
            canvas.setBackground(Color.WHITE)
            
            canvas.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    int x = e.getX()
                    int y = e.getY()
                    
                    int startX = canvas.reqNameColWidth
                    int startY = canvas.headerHeight
                    int cWidth = canvas.cellWidth
                    int rHeight = canvas.rowHeight
                    
                    def mainBrowser = Application.getInstance().getMainFrame().getBrowser()
                    
                    if (x >= startX && x <= startX + (elemList.size() * cWidth) && y >= startY && y <= startY + (reqList.size() * rHeight)) {
                        // Intersection
                        int c = (x - startX) / cWidth
                        int r = (y - startY) / rHeight
                        if (c >= 0 && c < elemList.size() && r >= 0 && r < reqList.size()) {
                            Element feat = elemList.get(c)
                            RequirementUsage req = reqList.get(r)
                            Map<RequirementUsage, SatisfyRequirementUsage> satisfied = satisfyMap.get(feat)
                            if (satisfied != null && satisfied.containsKey(req)) {
                                mainBrowser.getContainmentTree().openNode(satisfied.get(req))
                            }
                        }
                    } else if (x >= 0 && x < startX && y >= startY && y <= startY + (reqList.size() * rHeight)) {
                        // Row Label (Requirement)
                        int r = (y - startY) / rHeight
                        if (r >= 0 && r < reqList.size()) {
                            mainBrowser.getContainmentTree().openNode(reqList.get(r))
                        }
                    } else if (x >= startX && x <= startX + (elemList.size() * cWidth) && y >= 0 && y < startY) {
                        // Column Label (Feature)
                        int c = (x - startX) / cWidth
                        if (c >= 0 && c < elemList.size()) {
                            mainBrowser.getContainmentTree().openNode(elemList.get(c))
                        }
                    }
                }
            })

            JScrollPane scrollPane = new JScrollPane(canvas)
            dialog.add(scrollPane, BorderLayout.CENTER)

            dialog.setSize(800, 600)
            dialog.setLocationRelativeTo(Application.getInstance().getMainFrame())
            dialog.setVisible(true)
        }
    })

    logger.info("--- GUI Generation Complete ---")

} catch (Throwable t) {
    logger.error("Critical failure during analysis", t)
}

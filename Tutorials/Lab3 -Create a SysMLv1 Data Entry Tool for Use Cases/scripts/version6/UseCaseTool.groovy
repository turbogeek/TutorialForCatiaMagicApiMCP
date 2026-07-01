import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager
import com.nomagic.magicdraw.openapi.uml.PresentationElementsManager
import com.nomagic.magicdraw.uml.Finder
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*
import com.nomagic.uml2.ext.magicdraw.mdusecases.*
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.*
import com.nomagic.uml2.ext.magicdraw.activities.mdintermediateactivities.*
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.*
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.*
import com.nomagic.uml2.ext.magicdraw.interactions.mdbasicinteractions.*
import com.nomagic.magicdraw.ui.dialogs.MDDialogParentProvider
import com.nomagic.magicdraw.sysml.util.SysMLProfile

import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.filechooser.FileNameExtensionFilter
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.File
import java.util.List
import java.util.Map

// 1. Logger Setup
def projectDir = new File("e:/_Documents/git/TutorialForCatiaMagicApiMCP")
def logsDir = new File(projectDir, "Tutorials/Lab3 -Create a SysMLv1 Data Entry Tool for Use Cases/logs")
logsDir.mkdirs() 

def loggerScript = new File(projectDir, "test harness/SysMLv2Logger.groovy")
def loggerClass = new GroovyClassLoader(this.class.classLoader).parseClass(loggerScript)
def logFile = new File(logsDir, "UseCaseTool.log")
def log = loggerClass.newInstance("UseCaseTool", logFile)

log.info("Starting SysML Data Entry Tool for Use Cases (Version 6)")

def extractVerbNoun = { String phrase ->
    def parts = phrase.trim().split(" ", 2)
    if (parts.length > 1) {
        return [verb: parts[0], noun: parts[1]]
    }
    return [verb: phrase.trim(), noun: ""]
}

def runTool = { boolean isTestMode ->
    def dialog = new JDialog((Frame)null, "SysML Data Entry (v6)", true) // Modal
    dialog.setSize(800, 600)
    dialog.setLayout(new BorderLayout())

    def tabbedPane = new JTabbedPane()

    // --- Tab 1: Elements ---
    def elementsPanel = new JPanel(new BorderLayout(5, 5))
    elementsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
    def elementsTop = new JPanel(new GridLayout(3, 2, 5, 5))
    elementsTop.add(new JLabel("Primary Actors (comma separated):"))
    def txtPrimary = new JTextField()
    elementsTop.add(txtPrimary)
    elementsTop.add(new JLabel("Secondary Actors (comma separated):"))
    def txtSecondary = new JTextField()
    elementsTop.add(txtSecondary)
    elementsTop.add(new JLabel("System Context:"))
    def txtContext = new JTextField()
    elementsTop.add(txtContext)
    elementsPanel.add(elementsTop, BorderLayout.NORTH)
    
    def ucPanel = new JPanel(new BorderLayout(2, 2))
    ucPanel.add(new JLabel("Use Cases (one per line):"), BorderLayout.NORTH)
    def txtUseCases = new JTextArea()
    ucPanel.add(new JScrollPane(txtUseCases), BorderLayout.CENTER)
    
    def btnRefresh = new JButton("Refresh Matrices")
    def refreshPanel = new JPanel(new FlowLayout(FlowLayout.CENTER))
    refreshPanel.add(btnRefresh)
    ucPanel.add(refreshPanel, BorderLayout.SOUTH)
    
    elementsPanel.add(ucPanel, BorderLayout.CENTER)

    // --- Tab Setup Helpers ---
    def createMatrixTab = { String title ->
        def panel = new JPanel(new BorderLayout(5, 5))
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
        panel.add(new JLabel(title), BorderLayout.NORTH)
        def table = new JTable()
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF)
        panel.add(new JScrollPane(table), BorderLayout.CENTER)
        return [panel: panel, table: table]
    }

    def assocTab = createMatrixTab("Associations (Check boxes to link Actor and Use Case)")
    def incTab = createMatrixTab("Includes (Check boxes to include Use Case)")
    def extTab = createMatrixTab("Extends (Type trigger name, or 'yes' to extend)")
    def genTab = createMatrixTab("Generalizations (Check boxes to set Parent <- Child)")

    tabbedPane.addTab("Elements", elementsPanel)
    tabbedPane.addTab("Associations", assocTab.panel)
    tabbedPane.addTab("Includes", incTab.panel)
    tabbedPane.addTab("Extends", extTab.panel)
    tabbedPane.addTab("Generalizations", genTab.panel)
    
    dialog.add(tabbedPane, BorderLayout.CENTER)

    // Bottom Panel Layout Fix
    def bottomPanel = new JPanel(new BorderLayout())
    bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5))
    
    def versionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT))
    versionPanel.add(new JLabel("Target Version:"))
    def versionCombo = new JComboBox<String>(["SysML v1", "SysML v2"] as String[])
    versionPanel.add(versionCombo)
    
    def actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT))
    def btnLoad = new JButton("Load Markdown")
    def btnSave = new JButton("Save Markdown")
    def btnGenerate = new JButton("Generate Model")
    def btnCancel = new JButton("Cancel")
    
    actionPanel.add(btnLoad)
    actionPanel.add(btnSave)
    actionPanel.add(btnGenerate)
    actionPanel.add(btnCancel)
    
    bottomPanel.add(versionPanel, BorderLayout.WEST)
    bottomPanel.add(actionPanel, BorderLayout.EAST)
    dialog.add(bottomPanel, BorderLayout.SOUTH)

    // --- Matrix Logic ---
    def getList = { String text -> text.split(",").collect{ it.trim() }.findAll{ it.length() > 0 } }
    def getUcList = { String text -> text.split("\n").collect{ it.trim() }.findAll{ it.length() > 0 } }
    
    def updateBooleanMatrix = { JTable table, List<String> rows, List<String> cols ->
        def oldModel = table.getModel()
        def oldData = [:]
        if (oldModel instanceof DefaultTableModel) {
            for (int r = 0; r < oldModel.getRowCount(); r++) {
                String rowName = oldModel.getValueAt(r, 0)
                for (int c = 1; c < oldModel.getColumnCount(); c++) {
                    if (oldModel.getValueAt(r, c) == true) {
                        oldData[rowName + "||" + oldModel.getColumnName(c)] = true
                    }
                }
            }
        }
        
        def colNames = [""] + cols
        def newModel = new DefaultTableModel(colNames as Object[], 0) {
            @Override java.lang.Class<?> getColumnClass(int col) { return col == 0 ? String.class : Boolean.class }
            @Override boolean isCellEditable(int r, int c) { return c > 0 }
        }
        
        rows.each { rowName ->
            def rowData = new Object[colNames.size()]
            rowData[0] = rowName
            for (int c = 1; c < colNames.size(); c++) {
                rowData[c] = oldData[rowName + "||" + colNames[c]] ?: false
            }
            newModel.addRow(rowData)
        }
        table.setModel(newModel)
    }

    def updateStringMatrix = { JTable table, List<String> rows, List<String> cols ->
        def oldModel = table.getModel()
        def oldData = [:]
        if (oldModel instanceof DefaultTableModel) {
            for (int r = 0; r < oldModel.getRowCount(); r++) {
                String rowName = oldModel.getValueAt(r, 0)
                for (int c = 1; c < oldModel.getColumnCount(); c++) {
                    def val = oldModel.getValueAt(r, c)
                    if (val != null && val.toString().trim().length() > 0) {
                        oldData[rowName + "||" + oldModel.getColumnName(c)] = val.toString().trim()
                    }
                }
            }
        }
        
        def colNames = [""] + cols
        def newModel = new DefaultTableModel(colNames as Object[], 0) {
            @Override java.lang.Class<?> getColumnClass(int col) { return String.class }
            @Override boolean isCellEditable(int r, int c) { return c > 0 }
        }
        
        rows.each { rowName ->
            def rowData = new Object[colNames.size()]
            rowData[0] = rowName
            for (int c = 1; c < colNames.size(); c++) {
                rowData[c] = oldData[rowName + "||" + colNames[c]] ?: ""
            }
            newModel.addRow(rowData)
        }
        table.setModel(newModel)
    }

    def refreshMatrices = {
        def actors = getList(txtPrimary.getText()) + getList(txtSecondary.getText())
        def ucs = getUcList(txtUseCases.getText())
        def allElements = actors + ucs
        
        updateBooleanMatrix(assocTab.table, actors, ucs)
        updateBooleanMatrix(incTab.table, ucs, ucs)
        updateStringMatrix(extTab.table, ucs, ucs) // Extending vs Base
        updateBooleanMatrix(genTab.table, allElements, allElements) // Child vs Parent
    }
    
    btnRefresh.addActionListener({ ActionEvent e -> refreshMatrices() } as ActionListener)

    // --- Save/Load Markdown Logic ---
    def getBooleanPairs = { JTable table ->
        def pairs = []
        def model = table.getModel()
        if (!(model instanceof DefaultTableModel)) return pairs
        for (int r = 0; r < model.getRowCount(); r++) {
            String rowName = model.getValueAt(r, 0)
            for (int c = 1; c < model.getColumnCount(); c++) {
                if (model.getValueAt(r, c) == true) {
                    pairs.add([rowName, model.getColumnName(c)])
                }
            }
        }
        return pairs
    }

    def getStringPairs = { JTable table ->
        def pairs = []
        def model = table.getModel()
        if (!(model instanceof DefaultTableModel)) return pairs
        for (int r = 0; r < model.getRowCount(); r++) {
            String rowName = model.getValueAt(r, 0)
            for (int c = 1; c < model.getColumnCount(); c++) {
                def val = model.getValueAt(r, c)
                if (val != null && val.toString().trim().length() > 0) {
                    pairs.add([rowName, model.getColumnName(c), val.toString().trim()])
                }
            }
        }
        return pairs
    }

    def setBooleanPairs = { JTable table, List pairs ->
        def model = table.getModel()
        if (!(model instanceof DefaultTableModel)) return
        for (def pair : pairs) {
            String rowName = pair[0], colName = pair[1]
            for (int r = 0; r < model.getRowCount(); r++) {
                if (model.getValueAt(r, 0) == rowName) {
                    for (int c = 1; c < model.getColumnCount(); c++) {
                        if (model.getColumnName(c) == colName) {
                            model.setValueAt(true, r, c)
                        }
                    }
                }
            }
        }
    }

    def setStringPairs = { JTable table, List pairs ->
        def model = table.getModel()
        if (!(model instanceof DefaultTableModel)) return
        for (def pair : pairs) {
            String rowName = pair[0], colName = pair[1], val = pair[2]
            for (int r = 0; r < model.getRowCount(); r++) {
                if (model.getValueAt(r, 0) == rowName) {
                    for (int c = 1; c < model.getColumnCount(); c++) {
                        if (model.getColumnName(c) == colName) {
                            model.setValueAt(val, r, c)
                        }
                    }
                }
            }
        }
    }

    def parseMarkdown = { File file ->
        def lines = file.readLines()
        def currentSection = ""
        def pri = [], sec = [], ucs = [], ext = [], inc = [], gen = [], asc = []
        def ctx = ""
        
        int i = 0
        while (i < lines.size()) {
            def line = lines[i].trim()
            if (line.startsWith("#")) {
                currentSection = line.replaceAll("^#+\\s*", "").trim()
            } else if (line.length() > 0) {
                def val = line
                if (val.startsWith("- ")) val = val.substring(2).trim()
                
                if (currentSection == "Primary Actors") pri.add(val)
                else if (currentSection == "Secondary Actors") sec.add(val)
                else if (currentSection == "System Context") { if (ctx.length() == 0) ctx = val }
                else if (currentSection == "Use Cases") ucs.add(val)
                else if (currentSection == "Extends") {
                    if (val.contains("<-")) {
                        def p = val.split("<-")
                        if (p.length == 2) {
                            def base = p[0].trim()
                            def rest = p[1].trim()
                            def extending = rest, trigger = "yes"
                            if (rest.contains("[")) {
                                int i1 = rest.indexOf("["), i2 = rest.indexOf("]")
                                if (i1 != -1 && i2 > i1) {
                                    extending = rest.substring(0, i1).trim()
                                    trigger = rest.substring(i1+1, i2).trim()
                                }
                            }
                            ext.add([extending, base, trigger])
                        }
                    }
                }
                else if (currentSection == "Includes") {
                    if (val.contains("->")) {
                        def p = val.split("->")
                        if (p.length == 2) inc.add([p[0].trim(), p[1].trim()])
                    }
                }
                else if (currentSection == "Generalizations") {
                    if (val.contains("<-")) {
                        def p = val.split("<-")
                        if (p.length == 2) gen.add([p[1].trim(), p[0].trim()]) // child <- parent
                    }
                }
                else if (currentSection == "Associations") {
                    if (val.contains("->")) {
                        def p = val.split("->")
                        if (p.length == 2) asc.add([p[0].trim(), p[1].trim()])
                    } else if (val.contains("<->")) {
                        def p = val.split("<->")
                        if (p.length == 2) asc.add([p[0].trim(), p[1].trim()])
                    }
                }
            }
            i++
        }
        txtPrimary.setText(pri.join(", "))
        txtSecondary.setText(sec.join(", "))
        txtContext.setText(ctx)
        txtUseCases.setText(ucs.join("\n"))
        
        refreshMatrices()
        
        setBooleanPairs(assocTab.table, asc)
        setBooleanPairs(incTab.table, inc)
        setStringPairs(extTab.table, ext)
        setBooleanPairs(genTab.table, gen)
    }

    def getMarkdownContent = {
        StringBuilder sb = new StringBuilder()
        sb.append("### Primary Actors\n").append(txtPrimary.getText()).append("\n\n")
        sb.append("### Secondary Actors\n").append(txtSecondary.getText()).append("\n\n")
        sb.append("### System Context\n").append(txtContext.getText()).append("\n\n")
        sb.append("### Use Cases\n")
        getUcList(txtUseCases.getText()).each { sb.append("- ").append(it).append("\n") }
        sb.append("\n### Associations\n")
        getBooleanPairs(assocTab.table).each { p -> sb.append(p[0]).append(" <-> ").append(p[1]).append("\n") }
        sb.append("\n### Includes\n")
        getBooleanPairs(incTab.table).each { p -> sb.append(p[0]).append(" -> ").append(p[1]).append("\n") }
        sb.append("\n### Extends\n")
        getStringPairs(extTab.table).each { p -> 
            sb.append(p[1]).append(" <- ").append(p[0])
            if (p[2] != "yes") sb.append(" [").append(p[2]).append("]")
            sb.append("\n") 
        }
        sb.append("\n### Generalizations\n")
        getBooleanPairs(genTab.table).each { p -> sb.append(p[1]).append(" <- ").append(p[0]).append("\n") }
        return sb.toString()
    }

    btnLoad.addActionListener({ ActionEvent e ->
        JFileChooser chooser = new JFileChooser(projectDir)
        chooser.setFileFilter(new FileNameExtensionFilter("Markdown files", "md"))
        if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
            try {
                parseMarkdown(chooser.getSelectedFile())
                if (!isTestMode) JOptionPane.showMessageDialog(dialog, "Loaded successfully.")
            } catch (Throwable ex) {
                if (!isTestMode) JOptionPane.showMessageDialog(dialog, "Error loading: " + ex.getMessage())
            }
        }
    } as ActionListener)

    btnSave.addActionListener({ ActionEvent e ->
        JFileChooser chooser = new JFileChooser(projectDir)
        chooser.setFileFilter(new FileNameExtensionFilter("Markdown files", "md"))
        if (chooser.showSaveDialog(dialog) == JFileChooser.APPROVE_OPTION) {
            try {
                def f = chooser.getSelectedFile()
                if (!f.getName().endsWith(".md")) f = new File(f.getAbsolutePath() + ".md")
                f.write(getMarkdownContent())
                if (!isTestMode) JOptionPane.showMessageDialog(dialog, "Saved successfully.")
            } catch (Throwable ex) {
                if (!isTestMode) JOptionPane.showMessageDialog(dialog, "Error saving: " + ex.getMessage())
            }
        }
    } as ActionListener)

    btnCancel.addActionListener({ ActionEvent e -> dialog.dispose() } as ActionListener)

    def generateModel = {
        log.info("Starting model generation...")
        def project = Application.getInstance().getProject()
        if (project == null) {
            log.error("No active project.")
            return
        }

        def isV2 = versionCombo.getSelectedItem() == "SysML v2"
        SessionManager.getInstance().createSession(project, "Create " + (isV2 ? "SysMLv2" : "SysMLv1") + " Data Entry Use Cases")
        
        try {
            def factory = project.getElementsFactory()
            def root = project.getPrimaryModel()
            
            def mainPkg = factory.createPackageInstance()
            mainPkg.setName((isV2 ? "SysMLv2 Wizard Output" : "SysMLv1 Wizard Output"))
            mainPkg.setOwner(root)

            def useCasesPkg = factory.createPackageInstance()
            useCasesPkg.setName("Use Cases")
            useCasesPkg.setOwner(mainPkg)

            def blocksPkg = factory.createPackageInstance()
            blocksPkg.setName("Blocks (Nouns)")
            blocksPkg.setOwner(mainPkg)

            def activitiesPkg = factory.createPackageInstance()
            activitiesPkg.setName("Activities (Verbs)")
            activitiesPkg.setOwner(mainPkg)

            def behaviorPkg = factory.createPackageInstance()
            behaviorPkg.setName("Behavior (Interactions)")
            behaviorPkg.setOwner(mainPkg)

            def actorMap = [:]
            def ucMap = [:]
            def blockMap = [:]
            def activityMap = [:]
            def cbaMap = [:]

            def paList = getList(txtPrimary.getText())
            int pi = 0
            while (pi < paList.size()) {
                def name = paList[pi]
                def actor = factory.createActorInstance()
                actor.setName(name)
                actor.setOwner(useCasesPkg)
                actorMap[name] = actor
                pi++
            }

            def saList = getList(txtSecondary.getText())
            int si = 0
            while (si < saList.size()) {
                def name = saList[si]
                def actor = factory.createActorInstance()
                actor.setName(name)
                actor.setOwner(useCasesPkg)
                actorMap[name] = actor
                si++
            }

            def ctxName = txtContext.getText().trim()
            if (ctxName.length() > 0) {
                def sysContext = factory.createClassInstance()
                sysContext.setName(ctxName)
                sysContext.setOwner(useCasesPkg)
                def sysmlProfile = com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper.getProfile(project, 'SysML')
                if (sysmlProfile != null) {
                    def blockStereo = com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper.getStereotype(project, 'Block', sysmlProfile)
                    if (blockStereo != null) StereotypesHelper.addStereotype(sysContext, blockStereo)
                }
            }

            def ucLines = getUcList(txtUseCases.getText())
            int ui = 0
            while (ui < ucLines.size()) {
                def name = ucLines[ui]
                def uc = factory.createUseCaseInstance()
                uc.setName(name)
                uc.setOwner(useCasesPkg)
                ucMap[name] = uc

                def vn = extractVerbNoun(name)
                def blockName = vn.noun
                if (blockName.length() > 0 && !blockMap.containsKey(blockName)) {
                    def blk = factory.createClassInstance()
                    blk.setName(blockName)
                    blk.setOwner(blocksPkg)
                    def sysmlProfile = com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper.getProfile(project, 'SysML')
                    if (sysmlProfile != null) {
                        def blockStereo = com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper.getStereotype(project, 'Block', sysmlProfile)
                        if (blockStereo != null) StereotypesHelper.addStereotype(blk, blockStereo)
                    }
                    blockMap[blockName] = blk
                }

                def actName = vn.verb
                if (actName.length() > 0 && !activityMap.containsKey(actName)) {
                    def act = factory.createActivityInstance()
                    act.setName(actName)
                    act.setOwner(activitiesPkg)
                    activityMap[actName] = act
                }
                ui++
            }

            def mainAct = factory.createActivityInstance()
            mainAct.setName("Use Case System Interactions")
            mainAct.setOwner(behaviorPkg)

            def createPartition = { String pName ->
                def part = factory.createActivityPartitionInstance()
                part.setName(pName)
                part.setOwner(mainAct)
                part.setInActivity(mainAct)
                return part
            }

            def primaryPart = createPartition("Primary Actors")
            def sysPart = createPartition("System Context")
            def secondaryPart = createPartition("Secondary Actors")

            def ascList = getBooleanPairs(assocTab.table)
            int asi = 0
            while (asi < ascList.size()) {
                def p = ascList[asi]
                def aName = p[0]
                def bName = p[1]
                def elA = actorMap[aName] ?: ucMap[aName]
                def elB = ucMap[bName] ?: actorMap[bName]
                if (elA != null && elB != null && elA instanceof Classifier && elB instanceof Classifier) {
                    def assoc = factory.createAssociationInstance()
                    assoc.setOwner(useCasesPkg)
                    def p1 = factory.createPropertyInstance()
                    p1.setType((Classifier)elA)
                    p1.setOwner(assoc)
                    assoc.getMemberEnd().add(p1)
                    def p2 = factory.createPropertyInstance()
                    p2.setType((Classifier)elB)
                    p2.setOwner(assoc)
                    assoc.getMemberEnd().add(p2)
                    assoc.getOwnedEnd().add(p1)
                    assoc.getOwnedEnd().add(p2)
                }
                asi++
            }

            def incList = getBooleanPairs(incTab.table)
            int ici = 0
            while (ici < incList.size()) {
                def p = incList[ici]
                def base = p[0]
                def included = p[1]
                if (ucMap[base] != null && ucMap[included] != null) {
                    def include = factory.createIncludeInstance()
                    include.setIncludingCase(ucMap[base])
                    include.setAddition(ucMap[included])
                    include.setOwner(ucMap[base])
                }
                ici++
            }

            def extList = getStringPairs(extTab.table)
            int exi = 0
            while (exi < extList.size()) {
                def p = extList[exi]
                def extending = p[0]
                def base = p[1]
                def trigger = p[2] == "yes" ? "" : p[2]
                if (ucMap[base] != null && ucMap[extending] != null) {
                    def extend = factory.createExtendInstance()
                    extend.setExtendedCase(ucMap[base])
                    extend.setExtension(ucMap[extending])
                    extend.setOwner(ucMap[extending])
                    if (trigger.length() > 0) {
                        def comment = factory.createCommentInstance()
                        comment.setBody("Trigger: " + trigger)
                        comment.setOwner(extend.getOwner())
                        comment.getAnnotatedElement().add(extend)
                    }
                }
                exi++
            }

            def genList = getBooleanPairs(genTab.table)
            int gi = 0
            while (gi < genList.size()) {
                def p = genList[gi]
                def childStr = p[0]
                def parentStr = p[1]
                def parent = ucMap[parentStr] ?: actorMap[parentStr]
                def child = ucMap[childStr] ?: actorMap[childStr]
                if (parent != null && child != null) {
                    def gen = factory.createGeneralizationInstance()
                    gen.setGeneral(parent)
                    gen.setSpecific(child)
                    gen.setOwner(child)
                }
                gi++
            }

            ucMap.entrySet().each { entry ->
                def ucName = entry.key
                def cba = factory.createCallBehaviorActionInstance()
                cba.setName("Call " + ucName)
                cba.setOwner(mainAct)
                cba.setActivity(mainAct)
                
                // CORRECT Activity Partition logic:
                cba.getInPartition().add(sysPart)
                cbaMap[ucName] = cba
                
                def actName = extractVerbNoun(ucName).verb
                if (activityMap[actName] != null) cba.setBehavior(activityMap[actName])
            }

            int exi2 = 0
            while (exi2 < extList.size()) {
                def p = extList[exi2]
                def baseCba = cbaMap[p[1]]
                def extCba = cbaMap[p[0]]
                def trigger = p[2] == "yes" ? "" : p[2]
                if (baseCba != null && extCba != null) {
                    def decNode = factory.createDecisionNodeInstance()
                    decNode.setName("Extend Check for " + p[0])
                    decNode.setOwner(mainAct)
                    decNode.setActivity(mainAct)
                    decNode.getInPartition().add(sysPart)
                    
                    def flow1 = factory.createControlFlowInstance()
                    flow1.setSource(baseCba)
                    flow1.setTarget(decNode)
                    flow1.setOwner(mainAct)
                    flow1.setActivity(mainAct)
                    
                    def flow2 = factory.createControlFlowInstance()
                    flow2.setSource(decNode)
                    flow2.setTarget(extCba)
                    flow2.setOwner(mainAct)
                    flow2.setActivity(mainAct)
                    
                    if (trigger.length() > 0) {
                        def guardValue = factory.createOpaqueExpressionInstance()
                        guardValue.getBody().add(trigger)
                        flow2.setGuard(guardValue)
                    }
                }
                exi2++
            }

            int ici2 = 0
            while (ici2 < incList.size()) {
                def p = incList[ici2]
                def baseCba = cbaMap[p[0]]
                def incCba = cbaMap[p[1]]
                if (baseCba != null && incCba != null) {
                    def flow = factory.createControlFlowInstance()
                    flow.setSource(baseCba)
                    flow.setTarget(incCba)
                    flow.setOwner(mainAct)
                    flow.setActivity(mainAct)
                }
                ici2++
            }

            def diagramMgr = ModelElementsManager.getInstance()
            try {
                def mat1 = diagramMgr.createDiagram("Dependency Matrix", mainPkg)
                if (mat1 != null) mat1.setName("Use Case Relationships Matrix")
                def mat2 = diagramMgr.createDiagram("Dependency Matrix", mainPkg)
                if (mat2 != null) mat2.setName("Actor Relationships Matrix")
            } catch (Throwable t) { log.warn("Could not create dependency matrices", t) }

            try {
                def actDiag = diagramMgr.createDiagram("SysML Activity Diagram", mainAct)
                if (actDiag != null) actDiag.setName("Overview Interactions")
                else {
                    def fallbackDiag = diagramMgr.createDiagram("Activity Diagram", mainAct)
                    if (fallbackDiag != null) fallbackDiag.setName("Overview Interactions")
                }
            } catch (Throwable t) { log.warn("Could not create Activity diagram", t) }

            try {
                def ucDiag = diagramMgr.createDiagram("SysML Use Case Diagram", useCasesPkg)
                if (ucDiag != null) ucDiag.setName("System Use Cases")
                else {
                    def fallbackDiag = diagramMgr.createDiagram("Use Case Diagram", useCasesPkg)
                    if (fallbackDiag != null) fallbackDiag.setName("System Use Cases")
                }
            } catch (Throwable t) { log.warn("Could not create Use Case diagram", t) }

            SessionManager.getInstance().closeSession(project)
            log.info("Model generated successfully!")
            dialog.setAlwaysOnTop(false)
            if (!isTestMode) {
                JOptionPane.showMessageDialog(dialog, "SysML model successfully generated!", "Success", JOptionPane.INFORMATION_MESSAGE)
            }
            dialog.dispose()
        } catch (Throwable innerEx) {
            SessionManager.getInstance().cancelSession(project)
            log.error("Failed to create model: " + innerEx.getMessage(), innerEx)
            if (!isTestMode) {
                JOptionPane.showMessageDialog(dialog, "Failed to create model: " + innerEx.getMessage(), "Error", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    btnGenerate.addActionListener({ ActionEvent e -> generateModel() } as ActionListener)

    dialog.setLocationRelativeTo(null)
    
    if (isTestMode) {
        def testFile = new File(projectDir, "Tutorials/Lab3 -Create a SysMLv1 Data Entry Tool for Use Cases/scripts/version6/test_data.md")
        if (testFile.exists()) {
            parseMarkdown(testFile)
            generateModel()
        }
    } else {
        dialog.setVisible(true)
    }
}

boolean isTestMode = args != null && args.contains("test")

SwingUtilities.invokeLater({
    try {
        runTool(isTestMode)
    } catch (Throwable t) {
        log.error("Unhandled exception starting UI", t)
    }
} as Runnable)

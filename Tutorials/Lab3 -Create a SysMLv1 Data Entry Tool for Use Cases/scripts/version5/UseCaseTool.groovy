import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager
import com.nomagic.magicdraw.openapi.uml.PresentationElementsManager
import com.nomagic.magicdraw.uml.Finder
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
import javax.swing.filechooser.FileNameExtensionFilter
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.File

// 1. Logger Setup
def projectDir = new File("e:/_Documents/git/TutorialForCatiaMagicApiMCP")
def logsDir = new File(projectDir, "Tutorials/Lab3 -Create a SysMLv1 Data Entry Tool for Use Cases/logs")
logsDir.mkdirs() 

def loggerScript = new File(projectDir, "test harness/SysMLv2Logger.groovy")
def loggerClass = new GroovyClassLoader(this.class.classLoader).parseClass(loggerScript)
def log = loggerClass.newInstance(new File(logsDir, "UseCaseTool.log").absolutePath)

log.info("Starting SysML Data Entry Tool for Use Cases (Version 5)")

def extractVerbNoun = { String phrase ->
    def parts = phrase.trim().split(" ", 2)
    if (parts.length > 1) {
        return [verb: parts[0], noun: parts[1]]
    }
    return [verb: phrase.trim(), noun: ""]
}

def runTool = { boolean isTestMode ->
    // UI Setup
    def dialog = new JDialog((Frame)null, "SysML Data Entry (v5)", true) // Modal
    dialog.setSize(700, 600)
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
    elementsPanel.add(ucPanel, BorderLayout.CENTER)

    // --- Tab 2: Associations ---
    def assocPanel = new JPanel(new BorderLayout(5, 5))
    assocPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
    assocPanel.add(new JLabel("Associations (e.g. Actor -> Use Case or Actor <-> Use Case):"), BorderLayout.NORTH)
    def txtAssoc = new JTextArea()
    assocPanel.add(new JScrollPane(txtAssoc), BorderLayout.CENTER)

    // --- Tab 3: Includes ---
    def incPanel = new JPanel(new BorderLayout(5, 5))
    incPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
    incPanel.add(new JLabel("Includes (e.g. Base -> Included):"), BorderLayout.NORTH)
    def txtIncludes = new JTextArea()
    incPanel.add(new JScrollPane(txtIncludes), BorderLayout.CENTER)

    // --- Tab 4: Extends ---
    def extPanel = new JPanel(new BorderLayout(5, 5))
    extPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
    extPanel.add(new JLabel("Extends (e.g. Base <- Extending [Trigger]):"), BorderLayout.NORTH)
    def txtExtends = new JTextArea()
    extPanel.add(new JScrollPane(txtExtends), BorderLayout.CENTER)

    // --- Tab 5: Generalizations ---
    def genPanel = new JPanel(new BorderLayout(5, 5))
    genPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
    genPanel.add(new JLabel("Generalizations (e.g. Parent <- Child):"), BorderLayout.NORTH)
    def txtGen = new JTextArea()
    genPanel.add(new JScrollPane(txtGen), BorderLayout.CENTER)

    tabbedPane.addTab("Elements", elementsPanel)
    tabbedPane.addTab("Associations", assocPanel)
    tabbedPane.addTab("Includes", incPanel)
    tabbedPane.addTab("Extends", extPanel)
    tabbedPane.addTab("Generalizations", genPanel)
    
    dialog.add(tabbedPane, BorderLayout.CENTER)

    // Bottom Panel
    def bottomPanel = new JPanel(new BorderLayout())
    
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

    btnCancel.addActionListener({ ActionEvent e ->
        dialog.dispose()
    } as ActionListener)

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
                else if (currentSection == "System Context") {
                    if (ctx.length() == 0) ctx = val
                }
                else if (currentSection == "Use Cases") ucs.add(val)
                else if (currentSection == "Extends") ext.add(val)
                else if (currentSection == "Includes") inc.add(val)
                else if (currentSection == "Generalizations") gen.add(val)
                else if (currentSection == "Associations") asc.add(val)
            }
            i++
        }
        txtPrimary.setText(pri.join(", "))
        txtSecondary.setText(sec.join(", "))
        txtContext.setText(ctx)
        txtUseCases.setText(ucs.join("\n"))
        txtExtends.setText(ext.join("\n"))
        txtIncludes.setText(inc.join("\n"))
        txtGen.setText(gen.join("\n"))
        txtAssoc.setText(asc.join("\n"))
    }

    def getMarkdownContent = {
        StringBuilder sb = new StringBuilder()
        sb.append("# Primary Actors\n").append(txtPrimary.getText()).append("\n\n")
        sb.append("# Secondary Actors\n").append(txtSecondary.getText()).append("\n\n")
        sb.append("# System Context\n").append(txtContext.getText()).append("\n\n")
        sb.append("# Use Cases\n").append(txtUseCases.getText()).append("\n\n")
        sb.append("# Associations\n").append(txtAssoc.getText()).append("\n\n")
        sb.append("# Extends\n").append(txtExtends.getText()).append("\n\n")
        sb.append("# Includes\n").append(txtIncludes.getText()).append("\n\n")
        sb.append("# Generalizations\n").append(txtGen.getText()).append("\n")
        return sb.toString()
    }

    btnLoad.addActionListener({ ActionEvent e ->
        JFileChooser chooser = new JFileChooser(projectDir)
        chooser.setFileFilter(new FileNameExtensionFilter("Markdown files", "md"))
        if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
            try {
                parseMarkdown(chooser.getSelectedFile())
                JOptionPane.showMessageDialog(dialog, "Loaded successfully.")
            } catch (Throwable ex) {
                JOptionPane.showMessageDialog(dialog, "Error loading: " + ex.getMessage())
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
                JOptionPane.showMessageDialog(dialog, "Saved successfully.")
            } catch (Throwable ex) {
                JOptionPane.showMessageDialog(dialog, "Error saving: " + ex.getMessage())
            }
        }
    } as ActionListener)

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
            
            // Create Package Structure
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

            // Maps
            def actorMap = [:]
            def ucMap = [:]
            def blockMap = [:]
            def activityMap = [:]
            def cbaMap = [:] // maps uc name to its CallBehaviorAction in the main activity

            // Parse inputs
            def paList = txtPrimary.getText().split(",")
            int pi = 0
            while (pi < paList.length) {
                def name = paList[pi].trim()
                if (name.length() > 0) {
                    def actor = factory.createActorInstance()
                    actor.setName(name)
                    actor.setOwner(useCasesPkg)
                    if (isV2) {
                        def sysmlProfile = SysMLProfile.getInstance(project)
                        if (sysmlProfile != null) {
                            def partStereo = sysmlProfile.partProperty()
                            // Note: V2 exact stereotypes might differ, but we apply what we can
                        }
                    }
                    actorMap[name] = actor
                }
                pi++
            }

            def saList = txtSecondary.getText().split(",")
            int si = 0
            while (si < saList.length) {
                def name = saList[si].trim()
                if (name.length() > 0) {
                    def actor = factory.createActorInstance()
                    actor.setName(name)
                    actor.setOwner(useCasesPkg)
                    actorMap[name] = actor
                }
                si++
            }

            def ctxName = txtContext.getText().trim()
            def sysContext = factory.createClassInstance()
            if (ctxName.length() > 0) {
                sysContext.setName(ctxName)
                sysContext.setOwner(useCasesPkg)
                // Add SysML Block stereotype
                def sysmlProfile = SysMLProfile.getInstance(project)
                if (sysmlProfile != null) {
                    com.nomagic.magicdraw.uml.BaseElement baseEl = sysContext
                    def blockStereo = sysmlProfile.block()
                    if (blockStereo != null) {
                        com.nomagic.magicdraw.openapi.uml.ModelElementsManager.getInstance().addStereotype(sysContext, blockStereo)
                    }
                }
            }

            // Create Use Cases and Verb/Noun elements
            def ucLines = txtUseCases.getText().split("\n")
            int ui = 0
            while (ui < ucLines.length) {
                def name = ucLines[ui].trim()
                if (name.length() > 0) {
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
                        def sysmlProfile = SysMLProfile.getInstance(project)
                        if (sysmlProfile != null) {
                            def blockStereo = sysmlProfile.block()
                            if (blockStereo != null) com.nomagic.magicdraw.openapi.uml.ModelElementsManager.getInstance().addStereotype(blk, blockStereo)
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
                }
                ui++
            }

            // Central Activity Diagram
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

            // Parse Associations
            def assocLines = txtAssoc.getText().split("\n")
            int asi = 0
            while (asi < assocLines.length) {
                try {
                    def line = assocLines[asi].trim()
                    if (line.contains("->") || line.contains("<->")) {
                        def parts = line.split("(<->|->)")
                        if (parts.length == 2) {
                            def aName = parts[0].trim()
                            def bName = parts[1].trim()
                            
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
                        }
                    }
                } catch (Throwable e) { log.warn("Error parsing association line: " + assocLines[asi], e) }
                asi++
            }

            // Parse Includes
            def incList = []
            def incLines = txtIncludes.getText().split("\n")
            int ici = 0
            while (ici < incLines.length) {
                try {
                    def line = incLines[ici].trim()
                    if (line.contains("->")) {
                        def parts = line.split("->")
                        if (parts.length == 2) {
                            def base = parts[0].trim()
                            def included = parts[1].trim()
                            if (ucMap[base] != null && ucMap[included] != null) {
                                def include = factory.createIncludeInstance()
                                include.setIncludingCase(ucMap[base])
                                include.setAddition(ucMap[included])
                                include.setOwner(ucMap[base])
                                incList.add([base: base, included: included])
                            }
                        }
                    }
                } catch (Throwable e) { log.warn("Error parsing include line: " + incLines[ici], e) }
                ici++
            }

            // Parse Extends
            def extList = []
            def extLines = txtExtends.getText().split("\n")
            int exi = 0
            while (exi < extLines.length) {
                try {
                    def line = extLines[exi].trim()
                    if (line.contains("<-")) {
                        def parts = line.split("<-")
                        if (parts.length == 2) {
                            def base = parts[0].trim()
                            def rest = parts[1].trim()
                            def extending = rest
                            def trigger = ""
                            if (rest.contains("[")) {
                                int idx1 = rest.indexOf("[")
                                int idx2 = rest.indexOf("]")
                                if (idx1 != -1 && idx2 != -1 && idx2 > idx1) {
                                    extending = rest.substring(0, idx1).trim()
                                    trigger = rest.substring(idx1+1, idx2).trim()
                                }
                            }
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
                                
                                extList.add([base: base, extending: extending, trigger: trigger])
                            }
                        }
                    }
                } catch (Throwable e) { log.warn("Error parsing extend line: " + extLines[exi], e) }
                exi++
            }

            // Parse Generalizations
            def genLines = txtGen.getText().split("\n")
            int gi = 0
            while (gi < genLines.length) {
                try {
                    def line = genLines[gi].trim()
                    if (line.contains("<-")) {
                        def parts = line.split("<-")
                        if (parts.length == 2) {
                            def parentStr = parts[0].trim()
                            def childStr = parts[1].trim()
                            def parent = ucMap[parentStr] ?: actorMap[parentStr]
                            def child = ucMap[childStr] ?: actorMap[childStr]
                            if (parent != null && child != null) {
                                def gen = factory.createGeneralizationInstance()
                                gen.setGeneral(parent)
                                gen.setSpecific(child)
                                gen.setOwner(child)
                            }
                        }
                    }
                } catch (Throwable e) { log.warn("Error parsing generalization line: " + genLines[gi], e) }
                gi++
            }

            // Setup Activity Diagram Nodes
            ucMap.entrySet().each { entry ->
                def ucName = entry.key
                
                // Create CallBehaviorAction for Base Use Case
                def cba = factory.createCallBehaviorActionInstance()
                cba.setName("Call " + ucName)
                cba.setOwner(mainAct)
                cba.setActivity(mainAct)
                sysPart.getNode().add(cba)
                cbaMap[ucName] = cba
                
                // If it has a matching Verb Activity, set it
                def actName = extractVerbNoun(ucName).verb
                if (activityMap[actName] != null) {
                    cba.setBehavior(activityMap[actName])
                }
            }

            // Add Control Flows for Extends
            int exi2 = 0
            while (exi2 < extList.size()) {
                def extData = extList[exi2]
                def baseCba = cbaMap[extData.base]
                def extCba = cbaMap[extData.extending]
                
                if (baseCba != null && extCba != null) {
                    // Create Decision Node
                    def decNode = factory.createDecisionNodeInstance()
                    decNode.setName("Extend Check for " + extData.extending)
                    decNode.setOwner(mainAct)
                    decNode.setActivity(mainAct)
                    sysPart.getNode().add(decNode)
                    
                    // Flow from Base -> Decision
                    def flow1 = factory.createControlFlowInstance()
                    flow1.setSource(baseCba)
                    flow1.setTarget(decNode)
                    flow1.setOwner(mainAct)
                    flow1.setActivity(mainAct)
                    
                    // Flow from Decision -> Extending
                    def flow2 = factory.createControlFlowInstance()
                    flow2.setSource(decNode)
                    flow2.setTarget(extCba)
                    flow2.setOwner(mainAct)
                    flow2.setActivity(mainAct)
                    
                    // Add Guard
                    if (extData.trigger.length() > 0) {
                        def guardValue = factory.createOpaqueExpressionInstance()
                        guardValue.getBody().add(extData.trigger)
                        flow2.setGuard(guardValue)
                    }
                }
                exi2++
            }

            // Add Control Flows for Includes
            int ici2 = 0
            while (ici2 < incList.size()) {
                def incData = incList[ici2]
                def baseCba = cbaMap[incData.base]
                def incCba = cbaMap[incData.included]
                
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

            // Try to create Dependency Matrix diagrams
            try {
                def mat1 = diagramMgr.createDiagram("Dependency Matrix", mainPkg)
                if (mat1 != null) mat1.setName("Use Case Relationships Matrix")
                
                def mat2 = diagramMgr.createDiagram("Dependency Matrix", mainPkg)
                if (mat2 != null) mat2.setName("Actor Relationships Matrix")
            } catch (Throwable t) {
                log.warn("Could not create dependency matrices", t)
            }

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

    btnGenerate.addActionListener({ ActionEvent e ->
        generateModel()
    } as ActionListener)

    dialog.setLocationRelativeTo(null)
    
    if (isTestMode) {
        def testFile = new File(projectDir, "Tutorials/Lab3 -Create a SysMLv1 Data Entry Tool for Use Cases/scripts/version5/test_data.md")
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

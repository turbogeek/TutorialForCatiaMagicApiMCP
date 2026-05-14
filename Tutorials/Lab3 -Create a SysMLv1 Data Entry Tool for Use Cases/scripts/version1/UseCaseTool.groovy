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

import javax.swing.*
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener

// 1. Logger Setup
def projectDir = new File("e:/_Documents/git/TutorialForCatiaMagicApiMCP")
def loggerScript = new File(projectDir, "test harness/SysMLv2Logger.groovy")
def loggerClass = new GroovyClassLoader(this.class.classLoader).parseClass(loggerScript)
def log = loggerClass.newInstance(new File(projectDir, "Tutorials/Lab3 -Create a SysMLv1 Data Entry Tool for Use Cases/logs/UseCaseTool.log").absolutePath)

log.info("Starting SysMLv1 Data Entry Tool for Use Cases")

def runTool = {
    def data = [
        primaryActors: [],
        secondaryActors: [],
        systemContext: "",
        baseUseCases: [],
        includes: [],
        extendsRel: [],
        generalizations: []
    ]

    def dialog = new JDialog((Frame)null, "SysMLv1 Use Case Data Entry", false)
    dialog.setSize(600, 600)
    dialog.setLayout(new BorderLayout())

    def tabbedPane = new JTabbedPane()

    // --- TAB 1: Actors & Context ---
    def tab1 = new JPanel(new BorderLayout())
    def form1 = new JPanel(new GridLayout(6, 1))
    form1.add(new JLabel("Primary Actors (comma separated):"))
    def txtPrimary = new JTextField()
    form1.add(txtPrimary)
    form1.add(new JLabel("Secondary Actors (comma separated):"))
    def txtSecondary = new JTextField()
    form1.add(txtSecondary)
    form1.add(new JLabel("System Context (System of Interest):"))
    def txtContext = new JTextField()
    form1.add(txtContext)
    tab1.add(form1, BorderLayout.NORTH)
    def help1 = new JTextArea("Primary actors use the system to achieve a goal. Secondary actors support the system.\nSystem Context is the boundary of the system.\n\nReference: ISO/IEC/IEEE 2011, SEBoK Guide.")
    help1.setEditable(false)
    help1.setLineWrap(true)
    tab1.add(new JScrollPane(help1), BorderLayout.CENTER)
    tabbedPane.addTab("1. Actors & Context", tab1)

    // --- TAB 2: Use Cases ---
    def tab2 = new JPanel(new BorderLayout())
    tab2.add(new JLabel("Base Actions / Interactions (one per line, verb-noun format):"), BorderLayout.NORTH)
    def txtUseCases = new JTextArea()
    tab2.add(new JScrollPane(txtUseCases), BorderLayout.CENTER)
    def help2 = new JTextArea("Use verb-noun phrases like 'Login to system' or 'Check out book'.\nAvoid error, problem, or negative actions initially.")
    help2.setEditable(false)
    help2.setLineWrap(true)
    tab2.add(help2, BorderLayout.SOUTH)
    tabbedPane.addTab("2. Use Cases", tab2)

    // --- TAB 3: Relationships ---
    def tab3 = new JPanel(new BorderLayout())
    def relPanel = new JPanel(new GridLayout(6, 2))
    relPanel.add(new JLabel("Relationship Type:"))
    def cmbType = new JComboBox(["Include (Mandatory)", "Extend (Optional)", "Generalization"] as String[])
    relPanel.add(cmbType)
    
    relPanel.add(new JLabel("Base Use Case/Actor:"))
    def cmbSource = new JComboBox()
    relPanel.add(cmbSource)

    relPanel.add(new JLabel("Target Use Case/Actor (can be new name):"))
    def txtTarget = new JTextField()
    relPanel.add(txtTarget)

    relPanel.add(new JLabel("Trigger (for Extend only):"))
    def txtTrigger = new JTextField()
    relPanel.add(txtTrigger)

    def btnAddRel = new JButton("Add Relationship")
    relPanel.add(btnAddRel)
    
    def btnRefresh = new JButton("Refresh Dropdowns")
    relPanel.add(btnRefresh)

    tab3.add(relPanel, BorderLayout.NORTH)

    def listModel = new DefaultListModel()
    def listRels = new JList(listModel)
    tab3.add(new JScrollPane(listRels), BorderLayout.CENTER)
    tabbedPane.addTab("3. Relationships", tab3)

    // Refresh Dropdowns Action
    btnRefresh.addActionListener({ ActionEvent e ->
        cmbSource.removeAllItems()
        def ucs = txtUseCases.getText().split("\n")
        int i = 0
        while (i < ucs.length) {
            def uc = ucs[i].trim()
            if (uc.length() > 0) cmbSource.addItem(uc)
            i++
        }
        int j = 0
        while (j < data.includes.size()) {
            cmbSource.addItem(data.includes[j].included)
            j++
        }
        int k = 0
        while (k < data.extendsRel.size()) {
            cmbSource.addItem(data.extendsRel[k].extending)
            k++
        }
        
        // Also add actors for generalization
        def pas = txtPrimary.getText().split(",")
        int p = 0
        while (p < pas.length) {
            def a = pas[p].trim()
            if (a.length() > 0) cmbSource.addItem(a)
            p++
        }
        def sas = txtSecondary.getText().split(",")
        int s = 0
        while (s < sas.length) {
            def a = sas[s].trim()
            if (a.length() > 0) cmbSource.addItem(a)
            s++
        }
    } as ActionListener)

    btnAddRel.addActionListener({ ActionEvent e ->
        def type = cmbType.getSelectedItem().toString()
        def source = cmbSource.getSelectedItem()?.toString()
        def target = txtTarget.getText().trim()
        def trigger = txtTrigger.getText().trim()

        if (source == null || target.length() == 0) return

        if (type.contains("Include")) {
            data.includes.add([base: source, included: target])
            listModel.addElement("Include: " + source + " -> " + target)
        } else if (type.contains("Extend")) {
            data.extendsRel.add([base: source, extending: target, trigger: trigger])
            listModel.addElement("Extend: " + source + " <- " + target + " [Trigger: " + trigger + "]")
        } else if (type.contains("Generalization")) {
            data.generalizations.add([parent: source, child: target])
            listModel.addElement("Generalization: " + source + " is a " + target)
        }
        txtTarget.setText("")
        txtTrigger.setText("")
    } as ActionListener)

    // --- Bottom Panel ---
    def bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT))
    def btnGenerate = new JButton("Generate Model")
    bottomPanel.add(btnGenerate)
    dialog.add(tabbedPane, BorderLayout.CENTER)
    dialog.add(bottomPanel, BorderLayout.SOUTH)

    def generateModel = null
    generateModel = {
        try {
            // Collect Data
            def pas = txtPrimary.getText().split(",")
            int i = 0
            while (i < pas.length) {
                def a = pas[i].trim()
                if (a.length() > 0) data.primaryActors.add(a)
                i++
            }
            def sas = txtSecondary.getText().split(",")
            int j = 0
            while (j < sas.length) {
                def a = sas[j].trim()
                if (a.length() > 0) data.secondaryActors.add(a)
                j++
            }
            data.systemContext = txtContext.getText().trim()

            def ucs = txtUseCases.getText().split("\n")
            int k = 0
            while (k < ucs.length) {
                def u = ucs[k].trim()
                if (u.length() > 0) data.baseUseCases.add(u)
                k++
            }

            log.info("Data collected. Primary Actors: " + data.primaryActors.size() + ", Base Use Cases: " + data.baseUseCases.size())

            // Create Model
            def project = Application.getInstance().getProject()
            if (project == null) {
                log.error("No active project.")
                return
            }

            SessionManager.getInstance().createSession(project, "Create Use Cases")
            
            try {
                def factory = project.getElementsFactory()
                def root = project.getPrimaryModel()

                def useCasesPkg = factory.createPackageInstance()
                useCasesPkg.setName("Use Cases")
                useCasesPkg.setOwner(root)

                def primaryPkg = factory.createPackageInstance()
                primaryPkg.setName("Primary Actors")
                primaryPkg.setOwner(useCasesPkg)

                def secondaryPkg = factory.createPackageInstance()
                secondaryPkg.setName("Secondary Actors")
                secondaryPkg.setOwner(useCasesPkg)

                def behaviorPkg = factory.createPackageInstance()
                behaviorPkg.setName("Behavior")
                behaviorPkg.setOwner(useCasesPkg)

                // Maps to hold elements
                def actorMap = [:]
                def ucMap = [:]

                // Create Primary Actors
                int p = 0
                while (p < data.primaryActors.size()) {
                    def aName = data.primaryActors[p]
                    def actor = factory.createActorInstance()
                    actor.setName(aName)
                    actor.setOwner(primaryPkg)
                    actorMap[aName] = actor
                    p++
                }

                // Create Secondary Actors
                int s = 0
                while (s < data.secondaryActors.size()) {
                    def aName = data.secondaryActors[s]
                    def actor = factory.createActorInstance()
                    actor.setName(aName)
                    actor.setOwner(secondaryPkg)
                    actorMap[aName] = actor
                    s++
                }

                // System Context
                def sysContext = factory.createClassInstance()
                sysContext.setName(data.systemContext)
                sysContext.setOwner(useCasesPkg)
                
                // Base Use Cases
                int u = 0
                while (u < data.baseUseCases.size()) {
                    def uName = data.baseUseCases[u]
                    def uc = factory.createUseCaseInstance()
                    uc.setName(uName)
                    uc.setOwner(useCasesPkg)
                    ucMap[uName] = uc
                    u++
                }

                // Included and Extending Use Cases
                def createUcIfMissing = null
                createUcIfMissing = { String name ->
                    if (!ucMap.containsKey(name)) {
                        def newUc = factory.createUseCaseInstance()
                        newUc.setName(name)
                        newUc.setOwner(useCasesPkg)
                        ucMap[name] = newUc
                    }
                    return ucMap[name]
                }

                // Includes
                int m = 0
                while (m < data.includes.size()) {
                    def incData = data.includes[m]
                    def baseUc = createUcIfMissing(incData.base)
                    def incUc = createUcIfMissing(incData.included)
                    
                    def include = factory.createIncludeInstance()
                    include.setIncludingCase(baseUc)
                    include.setAddition(incUc)
                    include.setOwner(baseUc)
                    m++
                }

                // Extends
                int n = 0
                while (n < data.extendsRel.size()) {
                    def extData = data.extendsRel[n]
                    def baseUc = createUcIfMissing(extData.base)
                    def extUc = createUcIfMissing(extData.extending)
                    
                    def extend = factory.createExtendInstance()
                    extend.setExtendedCase(baseUc)
                    extend.setExtension(extUc)
                    extend.setOwner(extUc)
                    n++
                }

                // Generalizations
                int g = 0
                while (g < data.generalizations.size()) {
                    def genData = data.generalizations[g]
                    // Could be actor or UC
                    def parent = ucMap[genData.parent]
                    if (parent == null) parent = actorMap[genData.parent]
                    def child = ucMap[genData.child]
                    if (child == null) child = actorMap[genData.child]
                    
                    if (parent != null && child != null) {
                        def generalization = factory.createGeneralizationInstance()
                        generalization.setGeneral(parent)
                        generalization.setSpecific(child)
                        generalization.setOwner(child)
                    }
                    g++
                }

                // Activity Diagram
                def mainAct = factory.createActivityInstance()
                mainAct.setName("Use Case Interactions")
                mainAct.setOwner(behaviorPkg)

                // Swimlanes (ActivityPartitions)
                def actMap = [:]
                def createPartition = null
                createPartition = { String name ->
                    def part = factory.createActivityPartitionInstance()
                    part.setName(name)
                    part.setOwner(mainAct)
                    part.setInActivity(mainAct)
                    return part
                }

                // Create partition for Primary Actors
                int ap1 = 0
                while (ap1 < data.primaryActors.size()) {
                    def name = data.primaryActors[ap1]
                    actMap[name] = createPartition(name)
                    ap1++
                }

                // System Context Partition
                actMap[data.systemContext] = createPartition(data.systemContext)

                // Create partition for Secondary Actors
                int ap2 = 0
                while (ap2 < data.secondaryActors.size()) {
                    def name = data.secondaryActors[ap2]
                    actMap[name] = createPartition(name)
                    ap2++
                }

                // Activities for each Use Case
                def ucActMap = [:]
                ucMap.entrySet().each { entry ->
                    def ucName = entry.key
                    def ucElement = entry.value
                    
                    def act = factory.createActivityInstance()
                    act.setName(ucName + " Activity")
                    act.setOwner(behaviorPkg)
                    ucActMap[ucName] = act
                }

                // We won't perfectly link all ControlFlows to map the exact includes/extends 
                // in the Activity, but we can put the CallBehaviorActions in the System Context swimlane.
                def sysPartition = actMap[data.systemContext]
                ucActMap.entrySet().each { entry ->
                    def cba = factory.createCallBehaviorActionInstance()
                    cba.setName("Call " + entry.key)
                    cba.setBehavior(entry.value)
                    cba.setOwner(mainAct)
                    cba.setActivity(mainAct)
                    if (sysPartition != null) {
                        sysPartition.getNode().add(cba)
                    }
                }

                // Create Diagrams
                def diagramMgr = ModelElementsManager.getInstance()
                def useCaseDiag = diagramMgr.createDiagram("SysML Use Case Diagram", useCasesPkg)
                useCaseDiag.setName("Overview Use Cases")
                
                def actDiag = diagramMgr.createDiagram("SysML Activity Diagram", mainAct)
                actDiag.setName("Overview Activity")

                SessionManager.getInstance().closeSession(project)
                log.info("Successfully created Use Cases and Activities!")
                
                JOptionPane.showMessageDialog(dialog, "SysMLv1 Use Case model successfully generated!", "Success", JOptionPane.INFORMATION_MESSAGE)
                dialog.dispose()

            } catch (Throwable innerEx) {
                SessionManager.getInstance().cancelSession(project)
                log.error("Failed to create model: " + innerEx.getMessage(), innerEx)
                JOptionPane.showMessageDialog(dialog, "Failed to create model: " + innerEx.getMessage(), "Error", JOptionPane.ERROR_MESSAGE)
            }
        } catch (Throwable ex) {
            log.error("Execution error: " + ex.getMessage(), ex)
            JOptionPane.showMessageDialog(dialog, "Execution error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE)
        }
    } // end generateModel closure

    btnGenerate.addActionListener({ ActionEvent e ->
        generateModel()
    } as ActionListener)

    dialog.setLocationRelativeTo(null)
    dialog.setVisible(true)
}

SwingUtilities.invokeLater({
    try {
        runTool()
    } catch (Throwable t) {
        log.error("Unhandled exception starting UI", t)
    }
} as Runnable)

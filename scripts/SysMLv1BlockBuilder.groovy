// -----------------------------------------------------------------------------
// SysMLv1 Block Builder — Swing wizard
//
// A JDialog lets the user build a tree of parts:
//   root node          -> becomes the top-level system Block
//   level-1 children   -> become subsystem Blocks
//   level-2+ children  -> become catalog (leaf) Blocks
//
// On OK the script creates (under the primary model root):
//   Package 'system'        containing the top Block
//   Package 'subsystems'    containing level-1 Blocks
//   Package 'parts catalog' containing level-2+ Blocks
//
// For every parent/child pair in the tree, a COMPOSITE Property (SysMLv1 part
// property) is added to the parent Block, typed to the child Block.
//
// Conventions followed (see MCP best-practice cards):
//   - sessions          : SessionManager wrap; cancelSession on exception
//   - no-fast-strings   : single-quoted strings and '+' concatenation at ALL
//                         Cameo API boundaries (GStringImpl is not
//                         java.lang.String and will misbehave)
//   - console-logger    : SysMLv2Logger wrapper loaded from scripts/
//   - error-reporting   : Throwable always passed to logger.error(msg, t)
//   - no System.exit    : GUI disposes cleanly
// -----------------------------------------------------------------------------

import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class as UmlClass
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package as UmlPackage
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.AggregationKindEnum
import com.nomagic.uml2.ext.magicdraw.classes.mdprofiles.Stereotype
import com.nomagic.uml2.ext.magicdraw.classes.mdprofiles.Profile

import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout

// -- 1. Load the SysMLv2Logger via the script-load-groovy pattern ----------
// Preserves the Cameo classloader so com.nomagic.* imports resolve in the
// loaded class. Use '+' concat — no GStrings — for the path build.
String scriptDir = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\scripts'
File loggerFile = new File(scriptDir, 'SysMLv2Logger.groovy')
def LoggerClass = new GroovyClassLoader(getClass().getClassLoader()).parseClass(loggerFile)

// A dedicated log file cleared on each run — lets Claude (or any observer)
// tail this single file after invocation and see ONLY this run's output.
File runLog = new File(
    'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\logs',
    'SysMLv1BlockBuilder.log'
)
def logger = LoggerClass.newInstance('SysMLv1BlockBuilder', runLog)
logger.info('=== SysMLv1BlockBuilder run started ===')
logger.info('Log file (cleared on start): ' + runLog.getAbsolutePath())

try {
    Project project = Application.getInstance().getProject()
    if (project == null) {
        logger.error('No active MagicDraw project — open a SysMLv1 project and retry.')
        return
    }

    // Resolve the SysML profile + Block stereotype ONCE, before the dialog is
    // shown. If the profile is missing we fail fast with a user-visible error
    // and never open the GUI.
    Profile sysmlProfile = StereotypesHelper.getProfile(project, 'SysML')
    if (sysmlProfile == null) {
        logger.error('SysML profile not found in this project. Is SysMLv1 enabled?')
        return
    }
    Stereotype blockStereotype = StereotypesHelper.getStereotype(project, 'Block', sysmlProfile)
    if (blockStereotype == null) {
        logger.error('Block stereotype not found in the SysML profile.')
        return
    }

    // -- 2. Build the Swing wizard --------------------------------------
    SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            JDialog dialog = new JDialog(
                Application.getInstance().getMainFrame(),
                'SysMLv1 Block Builder',
                true  // modal
            )
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE)
            dialog.setLayout(new BorderLayout())

            // Tree model: root is the top block; children are parts; grand-
            // children are subparts.
            DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode('System')
            DefaultTreeModel treeModel = new DefaultTreeModel(rootNode)
            JTree tree = new JTree(treeModel)
            tree.setEditable(false)  // we edit via a prompt to avoid in-place
                                      // editing surprises on some LAFs
            tree.getSelectionModel().setSelectionMode(
                javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION
            )

            JScrollPane treeScroll = new JScrollPane(tree)
            treeScroll.setPreferredSize(new Dimension(420, 360))

            // Buttons operating on the selected node.
            JButton addChildBtn = new JButton('Add child')
            JButton renameBtn   = new JButton('Rename')
            JButton removeBtn   = new JButton('Remove')

            addChildBtn.addActionListener({
                DefaultMutableTreeNode sel = selectedNode(tree, rootNode)
                String name = JOptionPane.showInputDialog(
                    dialog, 'Name of new part:', 'Part' + (sel.getChildCount() + 1)
                )
                if (name != null && name.trim().length() > 0) {
                    DefaultMutableTreeNode child = new DefaultMutableTreeNode(name.trim())
                    treeModel.insertNodeInto(child, sel, sel.getChildCount())
                    tree.expandPath(new TreePath(sel.getPath()))
                    tree.setSelectionPath(new TreePath(child.getPath()))
                }
            } as java.awt.event.ActionListener)

            renameBtn.addActionListener({
                DefaultMutableTreeNode sel = selectedNode(tree, rootNode)
                String current = sel.getUserObject() as String
                String name = JOptionPane.showInputDialog(
                    dialog, 'Rename to:', current
                )
                if (name != null && name.trim().length() > 0) {
                    sel.setUserObject(name.trim())
                    treeModel.nodeChanged(sel)
                }
            } as java.awt.event.ActionListener)

            removeBtn.addActionListener({
                DefaultMutableTreeNode sel = selectedNode(tree, rootNode)
                if (sel == rootNode) {
                    JOptionPane.showMessageDialog(
                        dialog, 'Cannot remove the top-level System node.',
                        'Not allowed', JOptionPane.WARNING_MESSAGE
                    )
                    return
                }
                treeModel.removeNodeFromParent(sel)
            } as java.awt.event.ActionListener)

            JPanel editRow = new JPanel(new FlowLayout(FlowLayout.LEFT))
            editRow.add(addChildBtn)
            editRow.add(renameBtn)
            editRow.add(removeBtn)

            // OK / Cancel.
            JButton okBtn     = new JButton('OK')
            JButton cancelBtn = new JButton('Cancel')

            okBtn.addActionListener({
                dialog.setVisible(false)
                try {
                    BuildResult result = applyTreeToModel(
                        project, rootNode, blockStereotype, logger
                    )
                    String summary =
                        'Created ' + result.blocksCreated + ' Block(s) and ' +
                        result.partsCreated + ' part property(ies).\nPackages: ' +
                        result.systemPkg.getName() + ', ' +
                        result.subsystemsPkg.getName() + ', ' +
                        result.catalogPkg.getName() + '.'
                    JOptionPane.showMessageDialog(
                        Application.getInstance().getMainFrame(),
                        summary, 'Block Builder — done',
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } catch (Throwable t) {
                    logger.error('Block Builder failed while writing to the model', t)
                    JOptionPane.showMessageDialog(
                        Application.getInstance().getMainFrame(),
                        'Block Builder failed: ' + t.getMessage() +
                            '\n(see the MagicDraw notification/console for a stack trace)',
                        'Block Builder — error',
                        JOptionPane.ERROR_MESSAGE
                    )
                } finally {
                    dialog.dispose()
                }
            } as java.awt.event.ActionListener)

            cancelBtn.addActionListener({
                logger.info('Block Builder cancelled by user.')
                dialog.dispose()
            } as java.awt.event.ActionListener)

            JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.RIGHT))
            bottomRow.add(okBtn)
            bottomRow.add(cancelBtn)

            JPanel south = new JPanel(new BorderLayout())
            south.add(editRow,   BorderLayout.WEST)
            south.add(bottomRow, BorderLayout.EAST)

            dialog.add(treeScroll, BorderLayout.CENTER)
            dialog.add(south,      BorderLayout.SOUTH)

            dialog.pack()
            dialog.setLocationRelativeTo(Application.getInstance().getMainFrame())
            dialog.setVisible(true)
        }
    })
} catch (Throwable t) {
    // Any failure during setup (before the dialog opens) lands here.
    logger.error('Block Builder startup failure', t)
}

// -- 3. Helpers --------------------------------------------------------

/**
 * Return the selected tree node, or the root if nothing is selected.
 */
static DefaultMutableTreeNode selectedNode(JTree tree, DefaultMutableTreeNode fallback) {
    TreePath path = tree.getSelectionPath()
    if (path == null) return fallback
    Object last = path.getLastPathComponent()
    return last instanceof DefaultMutableTreeNode ? (DefaultMutableTreeNode) last : fallback
}

/**
 * Immutable record of what the builder produced so the caller can display a
 * summary to the user.
 */
class BuildResult {
    UmlPackage systemPkg
    UmlPackage subsystemsPkg
    UmlPackage catalogPkg
    int blocksCreated = 0
    int partsCreated  = 0
}

/**
 * Walk the JTree model and mutate the UML model inside ONE SessionManager
 * session, rolling back (cancelSession) on any exception.
 */
static BuildResult applyTreeToModel(
    Project project,
    DefaultMutableTreeNode rootNode,
    Stereotype blockStereotype,
    def logger
) {
    SessionManager sm = SessionManager.getInstance()
    // Short, descriptive label — appears in the Undo history.
    sm.createSession(project, 'SysMLv1 Block Builder')
    try {
        BuildResult result = new BuildResult()
        def factory = project.getElementsFactory()
        def modelMgr = ModelElementsManager.getInstance()

        // Root container for our three packages — the primary model Package.
        def modelRoot = project.getPrimaryModel()
        if (modelRoot == null) {
            throw new IllegalStateException('Project has no primary model root')
        }

        result.systemPkg     = ensurePackage(project, modelRoot, 'system',        factory, modelMgr)
        result.subsystemsPkg = ensurePackage(project, modelRoot, 'subsystems',    factory, modelMgr)
        result.catalogPkg    = ensurePackage(project, modelRoot, 'parts catalog', factory, modelMgr)

        // First pass: create every Block in the appropriate package.
        // We walk the tree BFS so level is determined by DFS depth.
        Map<DefaultMutableTreeNode, UmlClass> blockFor = new HashMap<>()
        createBlocksRecursively(
            rootNode, 0,
            result.systemPkg, result.subsystemsPkg, result.catalogPkg,
            factory, modelMgr, blockStereotype, blockFor, result, logger
        )

        // Second pass: for each parent with children, add a composite
        // Property to the parent Block typed to each child Block.
        addPartPropertiesRecursively(rootNode, blockFor, factory, modelMgr, result, logger)

        sm.closeSession(project)
        logger.info('Block Builder committed: ' + result.blocksCreated + ' block(s), ' +
                    result.partsCreated + ' part prop(s).')
        return result
    } catch (Throwable t) {
        sm.cancelSession(project)
        throw t
    }
}

static UmlPackage ensurePackage(
    Project project, def owner, String name, def factory, def modelMgr
) {
    // Reuse an existing child Package with the same name if present, so that
    // repeated runs of the Builder are idempotent on the package layer.
    for (def child : owner.getOwnedElement()) {
        if (child instanceof UmlPackage && name.equals(child.getName())) {
            return (UmlPackage) child
        }
    }
    UmlPackage pkg = factory.createPackageInstance()
    pkg.setName(name)  // 'name' is a plain java.lang.String — no GString
    modelMgr.addElement(pkg, owner)
    return pkg
}

/**
 * level==0 -> systemPkg, level==1 -> subsystemsPkg, level>=2 -> catalogPkg.
 * Each node becomes a Class stereotyped «Block» under the right Package.
 */
static void createBlocksRecursively(
    DefaultMutableTreeNode node,
    int level,
    UmlPackage systemPkg,
    UmlPackage subsystemsPkg,
    UmlPackage catalogPkg,
    def factory,
    def modelMgr,
    Stereotype blockStereotype,
    Map<DefaultMutableTreeNode, UmlClass> blockFor,
    BuildResult result,
    def logger
) {
    UmlPackage targetPkg =
        level == 0 ? systemPkg :
        level == 1 ? subsystemsPkg : catalogPkg

    String blockName = ((node.getUserObject() ?: 'Unnamed') as String).trim()
    if (blockName.length() == 0) blockName = 'Unnamed'

    UmlClass block = factory.createClassInstance()
    block.setName(blockName)
    modelMgr.addElement(block, targetPkg)
    StereotypesHelper.addStereotype(block, blockStereotype)
    blockFor.put(node, block)
    result.blocksCreated++
    logger.info('Created Block "' + blockName + '" in package "' + targetPkg.getName() + '" (level ' + level + ')')

    for (int i = 0; i < node.getChildCount(); i++) {
        DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i)
        createBlocksRecursively(
            child, level + 1,
            systemPkg, subsystemsPkg, catalogPkg,
            factory, modelMgr, blockStereotype, blockFor, result, logger
        )
    }
}

/**
 * For each parent-child pair in the source tree: add a composite-aggregation
 * Property to the parent Block with the child Block as its type. In SysMLv1,
 * a composite Property whose type is a Block is the part-property pattern.
 */
static void addPartPropertiesRecursively(
    DefaultMutableTreeNode node,
    Map<DefaultMutableTreeNode, UmlClass> blockFor,
    def factory,
    def modelMgr,
    BuildResult result,
    def logger
) {
    UmlClass parentBlock = blockFor.get(node)
    if (parentBlock == null) return

    for (int i = 0; i < node.getChildCount(); i++) {
        DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(i)
        UmlClass childBlock = blockFor.get(childNode)
        if (childBlock == null) continue

        Property part = factory.createPropertyInstance()
        // Lowercase-first-letter convention for property names — makes the
        // resulting model read like typical UML.
        String propName = lowerFirst(childBlock.getName())
        part.setName(propName)
        part.setType(childBlock)
        part.setAggregation(AggregationKindEnum.COMPOSITE)
        modelMgr.addElement(part, parentBlock)
        result.partsCreated++
        logger.info('  + part "' + propName + '" : ' + childBlock.getName() +
                    ' on Block "' + parentBlock.getName() + '"')

        addPartPropertiesRecursively(
            childNode, blockFor, factory, modelMgr, result, logger
        )
    }
}

static String lowerFirst(String s) {
    if (s == null || s.length() == 0) return s
    return s.substring(0, 1).toLowerCase() + s.substring(1)
}

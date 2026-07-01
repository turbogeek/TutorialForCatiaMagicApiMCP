/*
 * CCMOntologyLocatorProbe — lists every package in the open project with its applied stereotypes and tag
 * values, flagging which ones are verbalizable concept models (stereotyped «Concept Model» / «Model» /
 * «Information Model»). Two purposes:
 *   1) Tells the user exactly which package to right-click → Verbalize (vs. a plain container like
 *      "Imported Ontologies", which has no ontology of its own).
 *   2) Cross-validates the plugin's ConceptModelLocator: the packages this probe flags VERBALIZABLE must be
 *      the same set the locator's conceptModel().is(p) detection finds.
 *
 * Uses ONLY core MagicDraw (StereotypesHelper) so it runs in the harness regardless of CCM's own-classloader
 * isolation. Writes a diagnostic file Claude can read back; no groovy.json (FastStringService breaks in Cameo).
 */
import com.nomagic.magicdraw.core.Application
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package as UmlPackage

def ONTOLOGY_STEREOTYPES = ['Concept Model', 'Model', 'Information Model']
def out = new StringBuilder()
def verbalizable = []
def authored = []        // non-library «Concept Model» (what the plugin auto-prefers)
def anyNonLibrary = []   // any non-library ontology (the fallback set)

// Mirror ConceptModelLocator.isLibrary: «auxiliaryResource», or a «File Export Path» under <install.root>.
def libraryMarker = { pkg, sts ->
    if (sts.any { it.getName() == 'auxiliaryResource' }) return true
    def fep = sts.find { it.getName() == 'File Export Path' }
    if (fep) {
        def vals = StereotypesHelper.getStereotypePropertyValue(pkg, fep, 'fileExportPath')
        if (vals && vals[0]?.toString()?.contains('<install.root>')) return true
    }
    return false
}

def project = Application.getInstance().getProject()
if (project == null) {
    out << "NO PROJECT OPEN\n"
} else {
    out << "Project: " << project.getName() << "\n"
    out << "(packages flagged <== VERBALIZABLE carry a Concept-Modeling ontology stereotype; [LIB]=shipped library)\n\n"

    def describe = { pkg ->
        def sts = StereotypesHelper.getStereotypes(pkg)
        def stNames = sts.collect { it.getName() }
        def tagLine = new StringBuilder()
        sts.each { st ->
            def tagDefs = []
            try { tagDefs = st.getOwnedAttribute() } catch (ignore) { }
            tagDefs.each { td ->
                try {
                    def vals = StereotypesHelper.getStereotypePropertyValue(pkg, st, td.getName())
                    if (vals != null && !vals.isEmpty()) {
                        tagLine << "  «${st.getName()}».${td.getName()}=${vals}"
                    }
                } catch (ignore) { }
            }
        }
        [names: stNames, tags: tagLine.toString()]
    }

    def visit
    visit = { el, depth ->
        el.getOwnedElement().each { c ->
            if (c instanceof UmlPackage) {
                def d = describe(c)
                def name = c.getName() ?: '(unnamed)'
                def isOnt = d.names.any { ONTOLOGY_STEREOTYPES.contains(it) }
                def isLib = isOnt && libraryMarker(c, StereotypesHelper.getStereotypes(c))
                out << ('  ' * depth) << '[PKG] ' << name
                out << '  stereotypes=' << d.names
                if (d.tags) out << d.tags
                if (isOnt) {
                    verbalizable << name
                    out << (isLib ? '  <== VERBALIZABLE [LIB]' : '  <== VERBALIZABLE')
                    if (!isLib) {
                        anyNonLibrary << name
                        if (d.names.contains('Concept Model')) authored << name
                    }
                }
                out << '\n'
                visit(c, depth + 1)
            }
        }
    }
    visit(project.getPrimaryModel(), 0)

    out << "\nVERBALIZABLE PACKAGES (" << verbalizable.size() << "): " << verbalizable << "\n"
    out << "Authored «Concept Model», non-library (" << authored.size() << "): " << authored << "\n"
    out << "Any non-library ontology (" << anyNonLibrary.size() << "): " << anyNonLibrary << "\n"

    // The plugin's resolve(): prefer authored; else any non-library; auto-pick if exactly one, else chooser.
    def pick = !authored.isEmpty() ? authored : anyNonLibrary
    out << "\n>>> PLUGIN DECISION: "
    if (pick.size() == 1) {
        out << "auto-verbalize \"" << pick[0] << "\" (no prompt)\n"
    } else if (pick.size() > 1) {
        out << "show chooser with " << pick.size() << " options: " << pick << "\n"
    } else {
        out << "nothing auto-resolvable; fall back to the user's chosen package\n"
    }
    if (verbalizable.isEmpty()) {
        out << "\n** None found. The project has no «Concept Model»/«Model»/«Information Model» package. **\n"
    }
}

def f = new File('E:/_Documents/git/TutorialForCatiaMagicApiMCP/scripts/conceptmodeler/diag/ontology-locator-probe.txt')
f.getParentFile().mkdirs()
f.text = out.toString()
println 'CCMOntologyLocatorProbe wrote: ' + f.getAbsolutePath()
println out.toString()

// =============================================================================
// MatrixModel.groovy — headless query/data layer for v2Matrix.
//
// Pure data layer: takes a scope (Namespace), axis config, and relationship
// kind; returns an in-memory Matrix structure with rows, cols, and backed
// cells. No Swing, no Java2D here — testable against any open Cameo project.
//
// Design notes (iteration 2, derived from iteration-1 API discoveries):
//
// 1. The derived accessors SatisfyRequirementUsage.getSatisfiedRequirement()
//    and .getSatisfyingFeature() do NOT reliably return wired values when the
//    fixture was created programmatically. MatrixModel uses a two-step
//    fallback (see resolveSatisfiedRequirement / resolveSatisfyingFeature
//    below). This same pattern is in scripts/RequirementSatisfyMatrixGraphics.
//
// 2. Scope filtering excludes standard-library elements by default
//    (via scripts/v2Matrix/LibraryDetector.isInStandardLibrary).
//
// 3. "Implied" relationships (FR-6) are computed by walking owner chains —
//    when a sub-feature satisfies R, the parent gets an implied cell at R.
//
// 4. The matrix is a snapshot. Re-query to refresh.
//
// Usage:
//   def mm = new MatrixModel(scope: rootNamespace, rowTypes: [PartUsage],
//                            colTypes: [RequirementUsage],
//                            kind: RelationshipKind.SATISFY,
//                            showImplied: true)
//   def matrix = mm.build()   // Matrix { rows, cols, cells }
// =============================================================================

package v2Matrix

import com.dassault_systemes.modeler.kerml.model.kerml.Element
import com.dassault_systemes.modeler.kerml.model.kerml.Feature
import com.dassault_systemes.modeler.kerml.model.kerml.FeatureTyping
import com.dassault_systemes.modeler.kerml.model.kerml.Namespace
import com.dassault_systemes.modeler.kerml.model.kerml.Type
import com.dassault_systemes.modeler.sysml.model.sysml.RequirementUsage
import com.dassault_systemes.modeler.sysml.model.sysml.SatisfyRequirementUsage
import com.dassault_systemes.modeler.sysml.model.sysml.ViewpointUsage

// ---- Supporting types -------------------------------------------------------
enum CellSource { DIRECT, IMPLIED, SUBJECT }

class Cell {
    Element row
    Element col
    List<Element> backing = []   // the relationship elements producing this cell
    Set<CellSource> sources = EnumSet.noneOf(CellSource)
    int getCount()    { backing.size() }
    boolean isDirect()  { sources.contains(CellSource.DIRECT) }
    boolean isImplied() { sources.contains(CellSource.IMPLIED) && !isDirect() }
    boolean isSubject() { sources.contains(CellSource.SUBJECT) }
}

class Matrix {
    List<Element> rows = []
    List<Element> cols = []
    Map<String, Cell> cells = [:]   // key "rowId|colId" → Cell

    String cellKey(Element r, Element c) { r.hashCode() + '|' + c.hashCode() }

    Cell getOrCreate(Element r, Element c) {
        def key = cellKey(r, c)
        def cell = cells[key]
        if (cell == null) {
            cell = new Cell(row: r, col: c)
            cells[key] = cell
        }
        cell
    }

    Cell get(Element r, Element c) { cells[cellKey(r, c)] }

    int cellCount() { cells.size() }
    int directCount()  { cells.values().count { it.isDirect() }  as int }
    int impliedCount() { cells.values().count { it.isImplied() } as int }
}

enum RelationshipKind { SATISFY, SUBJECT, ANY }

// ---- MatrixModel -----------------------------------------------------------
class MatrixModel {
    Namespace scope                        // required — root of the query
    List<Class<? extends Element>> rowTypes = [com.dassault_systemes.modeler.sysml.model.sysml.PartUsage]
    List<Class<? extends Element>> colTypes = [RequirementUsage]
    RelationshipKind kind = RelationshipKind.SATISFY
    boolean showImplied = false
    boolean excludeLibraries = true        // FR-4 default
    // Optional type exclusions (FR-3). Any col element whose class is in this
    // set is dropped — e.g. to exclude ViewpointUsage while keeping RequirementUsage.
    //
    // SatisfyRequirementUsage IS-A RequirementUsage in the metamodel, so it would
    // otherwise leak into the cols list. We exclude it by default because the
    // satisfy element is the *relationship*, not a requirement-to-satisfy.
    List<Class<? extends Element>> colExclusions = [SatisfyRequirementUsage]
    List<Class<? extends Element>> rowExclusions = []

    // --- Build -----------------------------------------------------------------
    Matrix build() {
        if (scope == null) throw new IllegalStateException('MatrixModel.scope is required')
        def m = new Matrix()

        // 1. Collect candidate elements in scope
        def allInScope = collectInScope(scope)
        m.rows = filterByTypes(allInScope, rowTypes, rowExclusions)
        m.cols = filterByTypes(allInScope, colTypes, colExclusions)

        // 2. Find all SatisfyRequirementUsage instances in scope
        def allSatisfies = allInScope.findAll { it instanceof SatisfyRequirementUsage }

        // 3. Build cells per kind
        if (kind == RelationshipKind.SATISFY || kind == RelationshipKind.ANY) {
            addSatisfyCells(m, allSatisfies)
        }
        if (kind == RelationshipKind.SUBJECT || kind == RelationshipKind.ANY) {
            addSubjectCells(m, allInScope)
        }
        m
    }

    // --- Cell population: satisfy relationships ------------------------------
    //
    // We place satisfy edges by testing both orientations of (feature, req) against
    // the matrix's (rows, cols). This makes the method orientation-agnostic — the
    // controller can configure rows=PartUsage/cols=RequirementUsage (default) or
    // rows=RequirementUsage/cols=PartUsage (swapped) and get correct cells either
    // way without duplicating addSatisfyCells logic.
    private void addSatisfyCells(Matrix m, Collection<SatisfyRequirementUsage> satisfies) {
        for (SatisfyRequirementUsage s : satisfies) {
            def req = resolveSatisfiedRequirement(s)
            def feature = resolveSatisfyingFeature(s)
            if (req == null || feature == null) continue

            // Direct cell: try both orientations
            Element row = null, col = null
            if (m.rows.contains(feature) && m.cols.contains(req)) {
                row = feature; col = req
            } else if (m.rows.contains(req) && m.cols.contains(feature)) {
                row = req; col = feature
            }
            if (row != null) {
                def cell = m.getOrCreate(row, col)
                cell.backing << s
                cell.sources << CellSource.DIRECT
            }

            // Implied: walk owner chain from `feature` upward. For each ancestor,
            // try both orientations against (rows, cols).
            if (showImplied) {
                def ancestor = feature.getOwner()
                while (ancestor != null) {
                    Element aRow = null, aCol = null
                    if (m.rows.contains(ancestor) && m.cols.contains(req)) {
                        aRow = ancestor; aCol = req
                    } else if (m.rows.contains(req) && m.cols.contains(ancestor)) {
                        aRow = req; aCol = ancestor
                    }
                    if (aRow != null) {
                        def cell = m.getOrCreate(aRow, aCol)
                        cell.backing << s
                        cell.sources << CellSource.IMPLIED
                    }
                    ancestor = ancestor.respondsTo('getOwner') ? ancestor.getOwner() : null
                }
            }
        }
    }

    // --- Cell population: subject-based cells (FR-7) -------------------------
    private void addSubjectCells(Matrix m, Collection<Element> elements) {
        for (Element e : elements) {
            if (!(e instanceof RequirementUsage)) continue
            def req = (RequirementUsage) e
            def subject = null
            try { subject = req.getSubjectParameter() } catch (Exception ignored) {}
            if (subject == null) continue
            if (m.rows.contains(subject) && m.cols.contains(req)) {
                def cell = m.getOrCreate(subject, req)
                cell.backing << req
                cell.sources << CellSource.SUBJECT
            }
        }
    }

    // --- Scope collection -----------------------------------------------------
    List<Element> collectInScope(Namespace ns) {
        def result = []
        def visited = new HashSet<Element>()
        def walker
        walker = { Element e ->
            if (e == null || !visited.add(e)) return
            if (excludeLibraries && LibraryDetector.isInStandardLibrary(e)) return
            result << e
            // Walk owned members (SysMLv2 idiomatic: every namespace has getOwnedMember)
            if (e.respondsTo('getOwnedMember')) {
                e.getOwnedMember()?.each { walker.call(it) }
            }
        }
        walker.call(ns as Element)
        result
    }

    // --- Type/exclusion filters ----------------------------------------------
    private List<Element> filterByTypes(List<Element> all,
                                         List<Class<? extends Element>> types,
                                         List<Class<? extends Element>> exclusions) {
        all.findAll { e ->
            types.any { t -> t.isInstance(e) } &&
            !exclusions.any { x -> x.isInstance(e) }
        }
    }

    // --- Derivation fallbacks ------------------------------------------------
    //
    // Cameo's SatisfyRequirementUsage.getSatisfiedRequirement() returns the
    // element itself when no library-wired FeatureTyping pins a RequirementUsage.
    // We fall back to reading the first RequirementUsage from getOwnedTyping().

    static RequirementUsage resolveSatisfiedRequirement(SatisfyRequirementUsage s) {
        // Step 1: ask Cameo's derived getter. Use `.is()` for reference equality —
        // Groovy's `!=` would call compareTo() which Kerml elements throw on.
        def r = null
        try { r = s.getSatisfiedRequirement() } catch (Exception ignored) {}
        if (r != null && !r.is(s) && r instanceof RequirementUsage) return (RequirementUsage) r
        // Step 2: fallback — first RequirementUsage reachable via owned typings
        // (legacy fixture with FeatureTyping wiring) OR owned subsettings (current
        // fixture with ReferenceSubsetting wiring).
        try {
            for (ft in (s.getOwnedTyping() ?: [])) {
                def t = ft.getType()
                if (t instanceof RequirementUsage) return (RequirementUsage) t
            }
        } catch (Exception ignored) {}
        try {
            for (sub in (s.getOwnedSubsetting() ?: [])) {
                def sf = sub.getSubsettedFeature()
                if (sf instanceof RequirementUsage) return (RequirementUsage) sf
            }
        } catch (Exception ignored) {}
        null
    }

    static Element resolveSatisfyingFeature(SatisfyRequirementUsage s) {
        def f = null
        try { f = s.getSatisfyingFeature() } catch (Exception ignored) {}
        if (f != null) return f
        // Fallback: owner is the satisfying feature when derivation fails.
        try { return s.getOwner() } catch (Exception ignored) {}
        null
    }
}

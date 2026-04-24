// =============================================================================
// MatrixViewPersistence.groovy — save/load matrix configuration as a
// SysMLv2 ViewUsage (FR-15, FR-16).
//
// Save model: a ViewUsage named "v2Matrix_<scope>_<timestamp>" owned in the
// same Namespace as the matrix scope. The view owns N AttributeUsages — one
// per config key — each with a FeatureValue → LiteralString carrying the
// value. This is idiomatic SysMLv2 "decorator" config that round-trips
// through mdzip save/reload and through TWC commits.
//
// Config keys captured (MVP):
//   axesSwapped       boolean
//   showImplied       boolean
//   excludeViewpoint  boolean
//   paletteName       string  (Standard | Colorblind-safe | Dark mode | Hello Kitty)
//   rowType           string  (fqn of the primary row metaclass)
//   colType           string  (fqn of the primary col metaclass)
//   kind              string  (SATISFY | SUBJECT | ANY)
//   scopeId           string  (declared name of the scope Namespace)
//
// Extending: add a new key in the save() map; the load() parser reads any
// AttributeUsage/LiteralString pair it finds, unknown keys are ignored.
// =============================================================================

package v2Matrix

import com.dassault_systemes.modeler.kerml.model.kerml.Element
import com.dassault_systemes.modeler.kerml.model.kerml.KerMLFactory
import com.dassault_systemes.modeler.kerml.model.kerml.Namespace
import com.dassault_systemes.modeler.sysml.model.sysml.AttributeUsage
import com.dassault_systemes.modeler.sysml.model.sysml.SysMLFactory
import com.dassault_systemes.modeler.sysml.model.sysml.ViewUsage
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.openapi.uml.SessionManager

class MatrixViewPersistence {
    static final String NAME_PREFIX = 'v2Matrix_'

    // ---- Save ----------------------------------------------------------------
    // Creates a ViewUsage inside `scope` with config AttributeUsages.
    // Returns the created ViewUsage (or null on failure).
    static ViewUsage save(Namespace scope, Project project, Map<String, Object> config) {
        def factory      = SysMLFactory.eINSTANCE
        def kermlFactory = KerMLFactory.eINSTANCE
        def sm           = SessionManager.getInstance()
        boolean openedSession = false
        ViewUsage view = null
        try {
            if (!sm.isSessionCreated(project)) {
                sm.createSession(project, 'Save v2Matrix view')
                openedSession = true
            }
            view = factory.createViewUsage()
            String scopeName = safeName(scope) ?: 'scope'
            String ts = new Date().format('yyyyMMdd_HHmmss')
            view.setDeclaredName(NAME_PREFIX + scopeName + '_' + ts)
            view.setOwner(scope)

            config.each { key, value ->
                def attr = factory.createAttributeUsage()
                attr.setDeclaredName(key as String)
                attr.setOwner(view)

                def fv = kermlFactory.createFeatureValue()
                fv.setOwner(attr)

                def lit = kermlFactory.createLiteralString()
                lit.setOwner(fv)
                if (lit.respondsTo('setValue')) {
                    lit.setValue(String.valueOf(value))
                }
                // Wire fv.value → lit if there's an explicit setter
                if (fv.respondsTo('setValue')) {
                    try { fv.setValue(lit) } catch (Exception ignored) {}
                }
            }

            if (openedSession) sm.closeSession(project)
            return view
        } catch (Throwable t) {
            try { if (openedSession) sm.cancelSession(project) } catch (Exception ignored) {}
            throw t
        }
    }

    // ---- Load ---------------------------------------------------------------
    // Reads all AttributeUsage children of `view`; returns key→value map.
    // Booleans are re-parsed ("true"/"false"). Unknown keys pass through as Strings.
    static Map<String, Object> load(ViewUsage view) {
        Map<String, Object> out = [:]
        if (view == null) return out
        def members = []
        try { members = view.getOwnedMember() ?: [] } catch (Exception ignored) {}
        for (m in members) {
            if (!(m instanceof AttributeUsage)) continue
            def attr = (AttributeUsage) m
            String key = safeName(attr)
            if (key == null || key.isEmpty()) continue
            String val = readAttributeStringValue(attr)
            if (val == null) continue
            // Coerce obvious booleans; callers re-coerce specific keys as needed.
            if (val == 'true')  out[key] = Boolean.TRUE
            else if (val == 'false') out[key] = Boolean.FALSE
            else out[key] = val
        }
        out
    }

    // ---- Discovery: is this element one of ours? ----------------------------
    static boolean isMatrixView(def element) {
        if (!(element instanceof ViewUsage)) return false
        String n = safeName(element)
        n != null && n.startsWith(NAME_PREFIX)
    }

    // ---- Helpers ------------------------------------------------------------
    private static String readAttributeStringValue(AttributeUsage attr) {
        try {
            def children = attr.getOwnedMember() ?: []
            for (c in children) {
                // FeatureValue wraps the real LiteralString
                def ownedInValue = []
                try { ownedInValue = c.getOwnedMember() ?: [] } catch (Exception ignored) {}
                for (inner in ownedInValue) {
                    if (inner.respondsTo('getValue')) {
                        def v = inner.getValue()
                        if (v instanceof String) return v
                    }
                }
                // Direct LiteralString child fallback
                if (c.respondsTo('getValue')) {
                    def v = c.getValue()
                    if (v instanceof String) return v
                }
            }
        } catch (Exception ignored) {}
        null
    }

    private static String safeName(def e) {
        if (e == null) return null
        try {
            def n = e.getDeclaredName()
            if (n != null && !n.isEmpty()) return n
        } catch (Exception ignored) {}
        null
    }
}

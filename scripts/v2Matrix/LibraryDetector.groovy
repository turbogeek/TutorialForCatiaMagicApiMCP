// LibraryDetector — FR-4 default-scope heuristic.
//
// SysMLv2 / KerML marks standard-library content with a
// com.dassault_systemes.modeler.kerml.model.kerml.LibraryPackage owner that
// has isStandard() == true. The default matrix scope hides any element
// transitively contained by such a package; the user can opt in to showing
// libraries via the per-axis scope control.
//
// All FQNs here were verified against the shipped Javadoc during iteration 0
// (see v2Matrix/research-notes.md). Strings are single-quoted; no GStrings
// at the API boundary.

package v2Matrix

import com.dassault_systemes.modeler.kerml.model.kerml.Element
import com.dassault_systemes.modeler.kerml.model.kerml.LibraryPackage

class LibraryDetector {

    /**
     * Return true when the element lives under a standard LibraryPackage.
     * Null-safe: null element returns false (we cannot classify nothing).
     * Walks up through Element.getOwner() until it hits a LibraryPackage
     * with isStandard() == true, or runs out of owners.
     */
    static boolean isInStandardLibrary(Element element) {
        if (element == null) return false
        Element cursor = element
        while (cursor != null) {
            if (cursor instanceof LibraryPackage && ((LibraryPackage) cursor).isStandard()) {
                return true
            }
            cursor = cursor.getOwner()
        }
        return false
    }

    /**
     * Return the nearest LibraryPackage ancestor, or null if the element is
     * not under one. Useful for diagnostics / logging — we can report which
     * library is excluding an element.
     */
    static LibraryPackage findEnclosingLibraryPackage(Element element) {
        if (element == null) return null
        Element cursor = element
        while (cursor != null) {
            if (cursor instanceof LibraryPackage) return (LibraryPackage) cursor
            cursor = cursor.getOwner()
        }
        return null
    }

    /**
     * Partition a collection into {inUserContent, inStandardLib}. Single
     * pass; safe on any Iterable of Elements.
     */
    static Map<String, List<Element>> partition(Iterable<? extends Element> elements) {
        List<Element> user = new ArrayList<>()
        List<Element> std  = new ArrayList<>()
        for (Element e : elements) {
            if (isInStandardLibrary(e)) std.add(e)
            else user.add(e)
        }
        return [inUserContent: user, inStandardLib: std]
    }
}

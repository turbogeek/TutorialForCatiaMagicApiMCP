/**
 * SysMLv2 Element Counter Script for Cameo API
 * Counts all elements in the model by their type and displays a formatted table
 *
 * ======================================================================
 * SysMLv2 Element Type Count Report
 * ======================================================================
 * Element Type                                             Count
 * ----------------------------------------------------------------------
 * Class                                                      145
 * Block                                                       89
 * Port                                                        34
 * ...
 * ----------------------------------------------------------------------
 * TOTAL ELEMENTS                                          1,250
 * ======================================================================
 */

import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Model
import org.eclipse.emf.ecore.EObject

// Get the current project
Project project = Application.getInstance().getProject()
if (!project) {
    println "No project is currently open"
    return
}

// Map to store element type counts
Map<String, Integer> typeCounts = [:]

/**
 * Recursively traverse all elements in the model and count them by type
 */
def countElements(Element element) {
    if (element) {
        String elementType = element.getClass().getSimpleName()
        typeCounts[elementType] = (typeCounts[elementType] ?: 0) + 1
    }
    
    // Recursively process all owned elements
    element.getOwnedElements()?.each { ownedElement ->
        countElements(ownedElement as Element)
    }
}

// Get the model root and start counting
Model rootModel = project.getModel()
if (rootModel) {
    countElements(rootModel as Element)
}

// Sort the results by count (descending) then by type name
List<Map.Entry<String, Integer>> sortedCounts = typeCounts.entrySet()
    .sort { a, b -> b.value <=> a.value ?: a.key <=> b.key }

// Display results
println "\n" + "=" * 70
println "SysMLv2 Element Type Count Report"
println "=" * 70
println String.format("%-50s %15s", "Element Type", "Count")
println "-" * 70

int totalElements = 0
sortedCounts.each { entry ->
    println String.format("%-50s %15,d", entry.key, entry.value)
    totalElements += entry.value
}

println "-" * 70
println String.format("%-50s %15,d", "TOTAL ELEMENTS", totalElements)
println "=" * 70 + "\n"

// Return summary
"Element count complete: $totalElements total elements found in ${typeCounts.size()} different types"

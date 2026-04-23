// inspect-fixture.groovy — read-only diagnostic: what did build-fixture actually
// attach to the TF1 package, and what do the derivations return?

import com.nomagic.magicdraw.core.Application
import com.dassault_systemes.modeler.kerml.model.kerml.Namespace
import com.dassault_systemes.modeler.sysml.model.sysml.RequirementUsage
import com.dassault_systemes.modeler.sysml.model.sysml.PartUsage
import com.dassault_systemes.modeler.sysml.model.sysml.SatisfyRequirementUsage

def logFile = new File('E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\logs\\inspect-fixture.log')
logFile.parentFile.mkdirs()
logFile.text = ''
def log = { String msg -> logFile << "${new Date().format('HH:mm:ss.SSS')} $msg\n"; println msg }

log '=== inspect-fixture.groovy start ==='

def app = Application.getInstance()
def project = app.getProject()

// Get selected namespace (TF1)
def browser = app.getMainFrame().getBrowser()
def selected = browser.getActiveTree()?.getSelectedNodes()
Namespace tf1 = null
if (selected && selected.length > 0) {
    def obj = selected[0].getUserObject()
    if (obj instanceof Namespace) tf1 = (Namespace) obj
}
if (tf1 == null) {
    log 'Select TF1 in containment tree first'
    return
}
log "TF1 [${tf1.getDeclaredName()}]"
log "  getOwnedMember() count: ${tf1.getOwnedMember()?.size()}"
log "  getOwnedFeature() count: ${tf1.respondsTo('getOwnedFeature') ? tf1.getOwnedFeature()?.size() : 'N/A'}"
log "  getOwnedMembership() count: ${tf1.getOwnedMembership()?.size()}"

// List owned members
log '\n--- Owned members of TF1 ---'
tf1.getOwnedMember()?.each { m ->
    log "  ${m.getClass().getSimpleName()} [${m.getDeclaredName()}]"
}

// Find a SatisfyRequirementUsage and inspect its wiring
log '\n--- Inspect first SatisfyRequirementUsage ---'
def allSatisfies = []
tf1.getOwnedMember()?.each { m ->
    if (m instanceof SatisfyRequirementUsage) allSatisfies.add(m)
    // Also check nested members (satisfy are often owned by parts)
    if (m.respondsTo('getOwnedMember')) {
        m.getOwnedMember()?.each { n ->
            if (n instanceof SatisfyRequirementUsage) allSatisfies.add(n)
        }
    }
}
log "Total SatisfyRequirementUsage found: ${allSatisfies.size()}"

def first = allSatisfies ? allSatisfies[0] : null
if (first != null) {
    log "\nFirst satisfy: ${first.getDeclaredName()}"
    log "  getOwner() = ${first.getOwner()?.getClass()?.getSimpleName()}: ${first.getOwner()?.getDeclaredName()}"
    log "  getSatisfyingFeature() = ${first.getSatisfyingFeature()?.getClass()?.getSimpleName()}: ${first.getSatisfyingFeature()?.getDeclaredName()}"
    log "  getSatisfiedRequirement() = ${first.getSatisfiedRequirement()?.getClass()?.getSimpleName()}: ${first.getSatisfiedRequirement()?.getDeclaredName()}"

    // Inspect FeatureTyping / typing relations
    log '\n  --- Typing relations ---'
    if (first.respondsTo('getOwnedTyping')) {
        def ownedTypings = first.getOwnedTyping()
        log "  getOwnedTyping() count: ${ownedTypings?.size()}"
        ownedTypings?.each { ft ->
            log "    FT: typedFeature=${ft.getTypedFeature()?.getDeclaredName()} type=${ft.getType()?.getDeclaredName()}"
        }
    }
    if (first.respondsTo('getTyping')) {
        def typings = first.getTyping()
        log "  getTyping() count: ${typings?.size()}"
    }
    if (first.respondsTo('getType')) {
        def types = first.getType()
        log "  getType() count: ${types?.size()}"
        types?.each { t -> log "    Type: ${t?.getClass()?.getSimpleName()} ${t?.getDeclaredName()}" }
    }
    // Inspect owned relationships
    log "  getOwnedRelationship() count: ${first.respondsTo('getOwnedRelationship') ? first.getOwnedRelationship()?.size() : 'N/A'}"
    first.respondsTo('getOwnedRelationship') && first.getOwnedRelationship()?.each { r ->
        log "    ownedRelationship: ${r.getClass().getSimpleName()}"
    }
}

// Inspect one Part to see if setOwner worked
log '\n--- Inspect a PartUsage ---'
def firstPart = tf1.getOwnedMember()?.find { it instanceof PartUsage && it.getDeclaredName() == 'P1' }
if (firstPart) {
    log "P1.getOwner() = ${firstPart.getOwner()?.getClass()?.getSimpleName()}: ${firstPart.getOwner()?.getDeclaredName()}"
    log "P1.getOwningMembership() = ${firstPart.getOwningMembership()?.getClass()?.getSimpleName()}"
    log "P1.getOwningNamespace() = ${firstPart.getOwningNamespace()?.getClass()?.getSimpleName()}: ${firstPart.getOwningNamespace()?.getDeclaredName()}"
}

log '\n=== inspect-fixture.groovy end ==='

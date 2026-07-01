// UAFOntologyProbe.groovy   (v1)
// =====================================================================================
// PHASE-0 recon for the "UAF standard -> OWL ontology" converter.
//
// PURPOSE (scientific-method step 1 — OBSERVE before designing):
//   Before we write the UAF metamodel -> OWL transform, we must learn *exactly* how the
//   installed UAF Profile encodes its element taxonomy and, crucially, its RELATIONSHIPS
//   (e.g. how an EnterpriseGoal is connected to Challenge / Opportunity / Risk /
//   EnterpriseObjective). This probe reads the live profile READ-ONLY and dumps a
//   structured JSON + human log that Claude can read back and design the converter from.
//
// WHAT IT CAPTURES, per UAF Stereotype (filtered to the Strategic slice by default):
//   - qualified name + owning package path  (domain grouping lives in the package tree)
//   - generalizations  (general stereotype names)            -> future rdfs:subClassOf
//   - extended UML metaclasses (from the base_<MC> props)    -> tells us Class vs Relationship
//   - tagged-value properties (name / type / multiplicity)   -> future owl:ObjectProperty/DataProperty
//   - documentation/comment body                             -> future rdfs:comment
//   Plus a global list of RELATIONSHIP stereotypes (those extending Dependency/Association/
//   DirectedRelationship/...) because those are the cross-element connectors, and a BOUNDED
//   scan of Constraints that reference our seed elements (the "required connection" rules).
//
// SAFETY / HOUSE RULES (from CLAUDE.md):
//   - READ-ONLY: opens nothing, creates no session, mutates no model.
//   - Headless-safe: if run outside MagicDraw (no Application / no project) it logs that and
//     returns cleanly with test-mode notes — NEVER throws on the missing host.
//   - No System.exit. No groovy.json (Cameo FastStringUtils trap) — hand-rolled JSON.
//   - Heavy diagnostics to logs/ that Claude reads back (experimenter == observer).
//   - respondsTo()/try-catch introspection so version differences across UAF 1.x don't break it.
//
// RUN (via the Cameo Test Harness):
//   POST http://127.0.0.1:8765/run  { "scriptPath": "<this file>", "args": [] }
//   Optional args:  args[0] = comma-separated filter tokens (name/package substrings),
//                   args[1] = "all" to also dump a one-line summary of EVERY stereotype.
//   Then read:  logs/uaf-probe.json  and  logs/uaf-probe.log
// =====================================================================================

import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat

// ---- self-contained diagnostics -----------------------------------------------------
final String REPO    = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP'
final File   LOG_DIR = new File(REPO, 'logs')
LOG_DIR.mkdirs()
final File   LOG     = new File(LOG_DIR, 'uaf-probe.log')
final File   JSON    = new File(LOG_DIR, 'uaf-probe.json')
try { LOG.bytes = new byte[0] } catch (Throwable ignored) {}
final SimpleDateFormat TS = new SimpleDateFormat('HH:mm:ss.SSS')

Closure diag = { String msg ->
    String line = TS.format(new Date()) + '  ' + msg
    println line
    try { LOG << (line + '\n') } catch (Throwable ignored) {}
}
Closure diagT = { String msg, Throwable t ->
    StringWriter sw = new StringWriter(); t.printStackTrace(new PrintWriter(sw))
    diag(msg + '\n' + sw.toString())
}

// ---- tiny hand-rolled JSON writer (NO groovy.json — FastStringUtils trap) -------------
def jesc
jesc = { String s ->
    if (s == null) return 'null'
    StringBuilder b = new StringBuilder('"')
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i)
        if      (c == (char) 34) b.append('\\"')
        else if (c == (char) 92) b.append('\\\\')
        else if (c == (char) 10) b.append('\\n')
        else if (c == (char) 13) b.append('\\r')
        else if (c == (char) 9)  b.append('\\t')
        else if (c < (char) 32)  b.append(String.format('\\u%04x', (int) c))
        else                     b.append(c)
    }
    b.append('"'); return b.toString()
}
def jval
jval = { Object o ->
    if (o == null)            return 'null'
    if (o instanceof Boolean) return o.toString()
    if (o instanceof Number)  return o.toString()
    if (o instanceof Map) {
        StringBuilder b = new StringBuilder('{'); boolean first = true
        o.each { k, v -> if (!first) b.append(','); first = false; b.append(jesc(String.valueOf(k))).append(':').append(jval(v)) }
        b.append('}'); return b.toString()
    }
    if (o instanceof Iterable) {
        StringBuilder b = new StringBuilder('['); boolean first = true
        o.each { v -> if (!first) b.append(','); first = false; b.append(jval(v)) }
        b.append(']'); return b.toString()
    }
    return jesc(o.toString())
}

// ---- introspection helpers (version-tolerant) ----------------------------------------
Closure safe = { Closure c, def dflt = null -> try { return c.call() } catch (Throwable t) { return dflt } }
Closure nameOf = { el -> safe({ el.getName() }, null) }
Closure qpathOf = { el ->
    def parts = []
    def cur = el
    int guard = 0
    while (cur != null && guard++ < 64) {
        def nm = safe({ cur.getName() }, null)
        if (nm) parts.add(0, nm)
        cur = safe({ cur.getOwner() }, null)
    }
    return parts.join('::')
}
Closure docOf = { el ->
    // try ModelHelper.getComment(el); fall back to owned comment bodies
    String d = safe({
        Class mh = Class.forName('com.nomagic.uml2.ext.jmi.helpers.ModelHelper')
        return (String) mh.getMethod('getComment', Class.forName('com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element')).invoke(null, el)
    }, null)
    if (d) return d
    return safe({ el.getOwnedComment().collect { it.getBody() }.findAll { it }.join('\n') ?: null }, null)
}

final Set RELATIONSHIP_MC = [
    'Relationship','DirectedRelationship','Dependency','Abstraction','Realization','Usage',
    'Association','InformationFlow','Connector','Trace','Manifestation','Deployment','Generalization'
] as Set

diag('=== UAF Ontology Probe v1 START ===')

try {
    def a = (binding.hasVariable('args') ? binding.getVariable('args') : null)
    String tokenArg = (a != null && a.length > 0 && a[0]) ? String.valueOf(a[0]) : null
    boolean dumpAll = (a != null && a.length > 1 && 'all'.equalsIgnoreCase(String.valueOf(a[1])))

    // default Strategic-slice filter tokens (lower-cased substring match on name OR package path)
    List<String> tokens = (tokenArg ? tokenArg.split(',').collect { it.trim().toLowerCase() }.findAll { it } :
        ['strategic','motivation','enterprisegoal','challenge','opportunity','risk',
         'enterpriseobjective','enterprisevision','enterprisephase','wholelifeenterprise'])
    diag('filter tokens = ' + tokens + '   dumpAll=' + dumpAll)

    // ---- STEP 1: host detection (headless-safe) --------------------------------------
    def app = safe({ Application.getInstance() }, null)
    if (app == null) {
        diag('HEADLESS: Application.getInstance()==null. Running outside MagicDraw — nothing to probe.')
        diag('TEST-MODE NOTE: start MagicDraw/Cameo, open a UAF project, start the harness, then re-run.')
        Map outH = [mode: 'headless', generatedAt: new Date().toString(), note: 'no MagicDraw host', stereotypes: []]
        JSON.text = jval(outH); diag('wrote ' + JSON.getAbsolutePath()); diag('=== probe DONE (headless) ==='); return
    }
    Project project = safe({ app.getProject() }, null)
    if (project == null) {
        diag('NO PROJECT: open a UAF project (e.g. samples\\UAF\\UAF sample.mdzip) so the UAF Profile loads, then re-run.')
        Map outNP = [mode: 'no-project', generatedAt: new Date().toString(), stereotypes: []]
        JSON.text = jval(outNP); diag('=== probe DONE (no project) ==='); return
    }
    diag('Active project: ' + nameOf(project))

    // ---- STEP 2: enumerate all stereotypes -------------------------------------------
    def SH = Class.forName('com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper')
    def allSt = safe({ SH.getMethod('getAllStereotypes', Project).invoke(null, project) }, null)
    if (allSt == null) { diag('FATAL: StereotypesHelper.getAllStereotypes returned null'); return }
    def stList = new ArrayList(allSt)
    diag('total stereotypes visible in project: ' + stList.size())

    // distinct top-level profile names (sanity: is UAF actually loaded?)
    Set profileNames = new LinkedHashSet()
    stList.each { st ->
        def top = st; def owner = safe({ st.getOwner() }, null); int g = 0
        while (owner != null && g++ < 64) { top = owner; owner = safe({ owner.getOwner() }, null) }
        def tn = nameOf(top); if (tn) profileNames.add(tn)
    }
    diag('top-level model/profile roots: ' + profileNames)

    // ---- STEP 3: per-stereotype extraction -------------------------------------------
    Closure extractProps = { st ->
        def base = []      // extended metaclasses (base_<MC>)
        def tags = []      // tagged-value props
        def own = safe({ new ArrayList(st.getOwnedAttribute()) }, [])
        own.each { p ->
            String pn = nameOf(p)
            if (pn != null && pn.startsWith('base_')) {
                String mc = safe({ p.getType()?.getName() }, null) ?: pn.substring(5)
                base.add(mc)
            } else {
                tags.add([
                    name : pn,
                    type : safe({ p.getType()?.getName() }, null),
                    typeQName: safe({ qpathOf(p.getType()) }, null),
                    lower: safe({ p.getLower() }, null),
                    upper: safe({ int u = p.getUpper(); return (u < 0 ? '*' : String.valueOf(u)) }, null)
                ])
            }
        }
        return [base: base, tags: tags]
    }
    Closure generalsOf = { st ->
        safe({ st.getGeneralization().collect { nameOf(it.getGeneral()) }.findAll { it } }, [])
    }
    Closure isRelationship = { base -> base.any { RELATIONSHIP_MC.contains(it) } }

    Closure matches = { String nm, String qp ->
        String n = (nm ?: '').toLowerCase(); String q = (qp ?: '').toLowerCase()
        return tokens.any { t -> n.contains(t) || q.contains(t) }
    }

    List detailed = []          // full detail for filtered (Strategic) stereotypes
    List relationships = []     // every relationship-stereotype (cross-domain connectors)
    List summaryAll = []        // optional one-liners for everything
    int relCount = 0

    stList.each { st ->
        String nm = nameOf(st)
        String qp = qpathOf(st)
        def props = extractProps(st)
        boolean rel = isRelationship(props.base)
        if (rel) {
            relCount++
            relationships.add([name: nm, qname: qp, base: props.base,
                               generals: generalsOf(st), tagCount: props.tags.size()])
        }
        if (dumpAll) summaryAll.add([name: nm, base: props.base, rel: rel])
        if (matches(nm, qp)) {
            detailed.add([
                name      : nm,
                qname     : qp,
                isRelationship: rel,
                base      : props.base,
                generals  : generalsOf(st),
                doc       : docOf(st),
                tags      : props.tags
            ])
        }
    }
    diag('relationship-stereotypes: ' + relCount)
    diag('detailed (filtered) stereotypes: ' + detailed.size())
    detailed.each { d -> diag('   * ' + d.name + '   base=' + d.base + '   generals=' + d.generals + '   tags=' + d.tags.size()) }

    // ---- STEP 4: bounded constraint scan (the "required connection" rules) ------------
    // UAF validation rules live as Constraints (often <<validationRule>>) carrying OCL/binary
    // specs. We dump, bounded, those whose name or constrained-element references a seed token.
    List constraints = []
    int CONS_CAP = 400
    def cFinder = safe({
        def pm = Class.forName('com.nomagic.uml2.impl.PersistenceManager')   // not always present
        return null
    }, null)
    // Generic walk: iterate the project's element collection via Project.getAllConstraints if present,
    // else scan via the UML model tree. We use a defensive reflection approach.
    def constraintList = safe({
        // many MD versions: project.getElementsByClass(...) not public; use ModelHelper or walk model
        def model = project.getModel()
        def acc = []
        Closure walk
        walk = { el, depth ->
            if (acc.size() >= CONS_CAP * 6 || depth > 40) return
            def cn = safe({ el.getClass().getSimpleName() }, '')
            if (cn != null && cn.startsWith('Constraint')) acc.add(el)
            def kids = safe({ new ArrayList(el.getOwnedElement()) }, [])
            kids.each { k -> walk(k, depth + 1) }
        }
        walk(model, 0)
        return acc
    }, [])
    diag('constraints discovered (raw, capped at ' + (CONS_CAP*6) + '): ' + constraintList.size())
    constraintList.each { c ->
        if (constraints.size() >= CONS_CAP) return
        String cn = nameOf(c)
        String body = safe({
            def spec = c.getSpecification()
            return safe({ spec.getBody().join('\n') }, null) ?: safe({ spec.getValue()?.toString() }, null)
        }, null)
        def constrained = safe({ c.getConstrainedElement().collect { nameOf(it) }.findAll { it } }, [])
        String blob = ((cn ?: '') + ' ' + (constrained.join(' ')) + ' ' + (body ?: '')).toLowerCase()
        if (tokens.any { blob.contains(it) }) {
            constraints.add([name: cn, qname: qpathOf(c), constrained: constrained, body: body])
        }
    }
    diag('constraints matching seed tokens: ' + constraints.size())

    // ---- STEP 5: write structured dump -----------------------------------------------
    Map out = [
        mode          : 'live',
        generatedAt   : new Date().toString(),
        project       : nameOf(project),
        profileRoots  : new ArrayList(profileNames),
        filterTokens  : tokens,
        counts        : [totalStereotypes: stList.size(), relationshipStereotypes: relCount,
                         detailed: detailed.size(), constraintsMatched: constraints.size()],
        relationships : relationships,
        stereotypes   : detailed,
        constraints   : constraints,
        allStereotypes: (dumpAll ? summaryAll : null)
    ]
    JSON.text = jval(out)
    diag('wrote structured dump -> ' + JSON.getAbsolutePath() + '   (' + JSON.length() + ' bytes)')
    diag('=== probe DONE (live) ===')
} catch (Throwable t) {
    diagT('UNCAUGHT in probe', t)
}

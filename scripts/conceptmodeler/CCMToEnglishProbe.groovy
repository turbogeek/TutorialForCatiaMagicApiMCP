// CCMToEnglishProbe.groovy   (v2)
// =====================================================================================
// Phase-0 de-risk probe for the "Concept Modeler -> English" MagicDraw plugin.
//
// Pipeline proven here against the REAL Cameo Concept Modeler classes:
//     CCM  ConceptModelOWLTransformFactory.createOWLExporter()
//          -> CM2OWL.exportFromModel(Package, OutputStream)   ->  OWL bytes
//     OntologyForMuggles  OntologyVerbalizer.verbalizeOntology(OWLOntology, title)
//          -> self-contained English HTML
//
// v2 changes (after v1 proved class visibility + found the root-model-has-no-URI issue):
//   * Targets a concept-model PACKAGE that carries an ontology URI, not the root model.
//     arg[0] = exact package name to export; with no arg it auto-scans top-level packages
//     (skipping profiles) and exports the first that succeeds.
//   * STEP 4 sets MissingImportHandlingStrategy.SILENT so a fragment with unresolved
//     owl:imports (e.g. BFO) still verbalizes.
//
// READ-ONLY: opens nothing, mutates no model. Reflection-only for CCM + owlapi + verbalizer
// (the harness compiles this with a fresh classloader that has none of those jars). Isolated
// parent=null URLClassLoader for the verbalizer (its own owlapi 5.5.1). No System.exit. No
// groovy.json (Cameo FastString trap). Heavy diagnostics to logs/ that Claude can read.
// =====================================================================================

import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL
import java.net.URLClassLoader
import java.text.SimpleDateFormat

// ---- self-contained diagnostics ------------------------------------------------------
final String REPO     = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP'
final File   LOG_DIR  = new File(REPO, 'logs')
LOG_DIR.mkdirs()
final File   LOG      = new File(LOG_DIR, 'CCMToEnglishProbe.log')
final File   OWL_OUT  = new File(LOG_DIR, 'ccm-export.owl')
final File   HTML_OUT = new File(LOG_DIR, 'ccm-english.html')
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
Closure rootMsg = { Throwable t ->
    Throwable r = t
    while (r.getCause() != null && r.getCause() != r) r = r.getCause()
    return r.getClass().getSimpleName() + ': ' + String.valueOf(r.getMessage())
}
Closure nameOf = { el -> try { return el.getName() } catch (Throwable t) { return null } }
Closure humanOf = { el -> try { return el.getHumanName() } catch (Throwable t) { return el.getClass().getName() } }

diag('=== CCM -> English probe v2 START ===')

try {
    def a = (binding.hasVariable('args') ? binding.getVariable('args') : null)
    String wantName = (a != null && a.length > 0) ? String.valueOf(a[0]) : null
    diag('arg[0] target package name = ' + wantName + '   (null => auto-scan)')

    // ---- STEP 1: environment recon + target selection --------------------------------
    diag('--- STEP 1: environment recon ---')
    def app = Application.getInstance()
    if (app == null) { diag('FATAL: Application.getInstance() == null (headless) — abort.'); return }
    Project project = app.getProject()
    if (project == null) { diag('FATAL: no active project. Open a Concept Model, then re-run.'); return }
    diag('Active project: ' + project.getName())
    def model = project.getModel()
    diag('Root model: ' + model.getName())

    ClassLoader scl = this.getClass().getClassLoader()
    def pkgCls = Class.forName('com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package', true, scl)

    def tops = new ArrayList(model.getOwnedElement())   // copy: live collection
    diag('Root model owns ' + tops.size() + ' top-level element(s):')
    tops.each { el -> diag('   - ' + humanOf(el)) }

    // candidate concept-model packages = top-level Packages that are NOT profiles
    def candidates = tops.findAll { pkgCls.isInstance(it) && !humanOf(it).startsWith('Profile') }
    diag('candidate package(s): ' + candidates.collect { nameOf(it) })

    def targets
    if (wantName != null) {
        targets = tops.findAll { pkgCls.isInstance(it) && nameOf(it) == wantName }
        diag('targeting "' + wantName + '": ' + targets.size() + ' match(es)')
        if (targets.isEmpty()) { diag('FATAL: no top-level package named "' + wantName + '".'); return }
    } else {
        targets = candidates
        diag('no arg -> will try ' + targets.size() + ' candidate package(s), stop at first success')
    }

    // ---- STEP 2: classloader visibility ----------------------------------------------
    diag('--- STEP 2: classloader visibility ---')
    ['com.nomagic.conceptmodeler.transform.ConceptModelOWLTransformFactory',
     'com.nomagic.conceptmodeler.transform.CM2OWL',
     'org.semanticweb.owlapi.apibinding.OWLManager'].each { String cn ->
        try { Class.forName(cn, false, scl); diag('  VISIBLE     : ' + cn) }
        catch (Throwable t) { diag('  NOT visible : ' + cn + '   (' + t.getClass().getSimpleName() + ')') }
    }

    // ---- STEP 3: CM2OWL export (per candidate package) -------------------------------
    diag('--- STEP 3: CM2OWL.exportFromModel ---')
    def factoryCls = Class.forName('com.nomagic.conceptmodeler.transform.ConceptModelOWLTransformFactory', true, scl)
    byte[] owlBytes = null
    String chosenName = null
    for (pkg in targets) {
        String nm = nameOf(pkg)
        try {
            def exporter  = factoryCls.getMethod('createOWLExporter').invoke(null)
            def expMethod = exporter.getClass().getMethod('exportFromModel', pkgCls, OutputStream.class)
            ByteArrayOutputStream baos = new ByteArrayOutputStream()
            def ok = expMethod.invoke(exporter, pkg, baos)
            byte[] b = baos.toByteArray()
            diag('  export "' + nm + '": ok=' + ok + ', bytes=' + b.length)
            if (owlBytes == null && b.length > 0) { owlBytes = b; chosenName = nm; break }
        } catch (Throwable t) {
            diag('  export "' + nm + '": FAILED  ' + rootMsg(t))
        }
    }
    if (owlBytes == null || owlBytes.length == 0) {
        diag('No OWL produced from any candidate — cannot verbalize. Stopping.')
        diag('=== probe DONE (partial) ===')
        return
    }
    diag('CHOSEN package for verbalization: "' + chosenName + '"  (' + owlBytes.length + ' bytes)')
    OWL_OUT.bytes = owlBytes
    diag('wrote raw OWL -> ' + OWL_OUT.getAbsolutePath())
    int peek = Math.min(owlBytes.length, 500)
    diag('OWL head: ' + new String(owlBytes, 0, peek, 'UTF-8').replace('\n', ' ').replace('\r', ' '))

    // ---- STEP 4: verbalize via OntologyForMuggles (isolated classloader) --------------
    diag('--- STEP 4: verbalize via OntologyVerbalizer ---')
    File libsDir = new File('E:\\_Documents\\git\\OntologyForMuggles\\build\\libs')
    File jar = null
    if (libsDir.isDirectory()) {
        def all = libsDir.listFiles().toList().findAll { it.name.endsWith('.jar') && !it.name.contains('sources') }
        jar = all.find { it.name ==~ /ontology-to-english-.*-cli\.jar/ }
        if (jar == null) jar = all.find { it.name ==~ /ontology-to-english-[0-9].*\.jar/ }
    }
    diag('  verbalizer jar: ' + (jar == null ? 'NONE in ' + libsDir : jar.getAbsolutePath()))
    if (jar == null) { diag('FATAL: build OntologyForMuggles (gradlew jar cliJar).'); return }

    URL[] urls = [ jar.toURI().toURL() ] as URL[]
    URLClassLoader vcl = new URLClassLoader(urls, (ClassLoader) null)
    ClassLoader prevCtx = Thread.currentThread().getContextClassLoader()
    Thread.currentThread().setContextClassLoader(vcl)
    try {
        def OWLManager = Class.forName('org.semanticweb.owlapi.apibinding.OWLManager', true, vcl)
        def mgr = OWLManager.getMethod('createOWLOntologyManager').invoke(null)

        // tolerate unresolved imports (BFO etc.) so a fragment still loads
        try {
            def cfgCls   = Class.forName('org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration', true, vcl)
            def cfg      = cfgCls.getDeclaredConstructor().newInstance()
            def stratCls = Class.forName('org.semanticweb.owlapi.model.MissingImportHandlingStrategy', true, vcl)
            def silent   = stratCls.getMethod('valueOf', String.class).invoke(null, 'SILENT')
            cfg = cfgCls.getMethod('setMissingImportHandlingStrategy', stratCls).invoke(cfg, silent)
            def setCfg = mgr.getClass().getMethods().find {
                it.getName() == 'setOntologyLoaderConfiguration' && it.getParameterTypes().length == 1
            }
            if (setCfg != null) { setCfg.invoke(mgr, cfg); diag('  missing-import handling = SILENT') }
        } catch (Throwable t) {
            diag('  could not set SILENT import handling (' + t.getClass().getSimpleName() + '); using defaults')
        }

        def loadM = mgr.getClass().getMethods().find {
            it.getName() == 'loadOntologyFromOntologyDocument' &&
            it.getParameterTypes().length == 1 &&
            it.getParameterTypes()[0].getName() == 'java.io.InputStream'
        }
        def ont = loadM.invoke(mgr, new ByteArrayInputStream(owlBytes))
        diag('  loaded OWLOntology: ' + ont)

        def Verb    = Class.forName('com.ontologyvision.verbalizer.OntologyVerbalizer', true, vcl)
        def verb    = Verb.getDeclaredConstructor().newInstance()
        def ontFace = Class.forName('org.semanticweb.owlapi.model.OWLOntology', true, vcl)
        def vm      = Verb.getMethod('verbalizeOntology', ontFace, String.class)
        String html = (String) vm.invoke(verb, ont, 'Concept Model — English: ' + chosenName)
        HTML_OUT.bytes = html.getBytes('UTF-8')
        diag('  verbalized OK: html length = ' + html.length() + '  -> ' + HTML_OUT.getAbsolutePath())
    } catch (Throwable t) {
        diagT('  STEP 4 FAILED (verbalize)', t)
    } finally {
        Thread.currentThread().setContextClassLoader(prevCtx)
        try { vcl.close() } catch (Throwable ignored) {}
    }

    diag('=== probe DONE ===')
} catch (Throwable t) {
    diagT('UNCAUGHT in probe', t)
}

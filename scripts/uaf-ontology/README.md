# UAF → OWL Ontology Toolkit

Convert the **UAF standard** (as installed in Cameo/MagicDraw) into an **OWL 2 ontology**
that can be imported into **CCM** or **VOM**, then use that ontology to drive
*model-completeness* reasoning: given some UAF elements, what related elements does the
standard say should exist, and which are missing?

Motivating example: an `EnterpriseGoal` is connected (per the UAF Strategic Motivation
viewpoint) to `Challenge`, `Opportunity`, `Risk`, and `EnterpriseObjective`. The ontology
makes those expected relationships *queryable*, so an LLM-assisted UAF tool can detect gaps
and either fill them or ask the user targeted questions.

## The key idea: TBox (rulebook) vs ABox (a model)

| Layer | What it is | Built by | Used for |
|---|---|---|---|
| **TBox** (schema) | UAF element *types* → `owl:Class`; UAF *relationships* → `owl:ObjectProperty`; "needs ≥1 X" → `owl:Restriction` | `UAFToOwl.groovy` (converts the **UAF Profile + Constraints**) | The importable `uaf.owl` — "the standard, as an ontology" |
| **ABox** (instances) | A specific UAF model's elements → `owl:NamedIndividual`; their links → property assertions | `UAFModelToIndividuals.groovy` | Checking one real model against the rulebook |
| **Gap query** | SPARQL/reasoner join of ABox against TBox restrictions | `queries/*.rq` | "This EnterpriseGoal has no linked Risk → gap" |

"Convert the UAF standard to OWL" = build the **TBox**. The gap-analysis skill = TBox +
ABox + queries.

## Pipeline

```
 UAF Profile.mdzip   \
 UAF Constraints      >-- (Phase 0)  UAFOntologyProbe.groovy --> logs/uaf-probe.json   (OBSERVE structure)
 UAF_Customization   /                       |
                                             |  design from observed structure
                                             v
                       (Phase 1)  UAFToOwl.groovy --> out/uaf.owl   (OWL 2 RDF/XML, CCM/VOM-importable)
                                             |
                       (Phase 2)  UAFModelToIndividuals.groovy --> out/<model>-abox.owl
                                             |
                       (Phase 3)  queries/gaps-*.rq   (SPARQL over uaf.owl + abox)  --> gap report
```

All scripts run **inside Cameo** via the REST test harness (`test harness\start-harness.groovy`,
`http://127.0.0.1:8765`) and are **headless-safe**, **read-only** where possible, and write
diagnostics to `logs/` that Claude reads back. No `System.exit`, no `groovy.json`.

## Planned UAF → OWL mapping (to be confirmed by the Phase-0 probe)

| UAF / UML profile construct | OWL construct |
|---|---|
| Stereotype (element type, e.g. `EnterpriseGoal`) | `owl:Class` |
| Generalization between stereotypes | `rdfs:subClassOf` |
| Stereotype that extends a *relationship* metaclass (Dependency/Association/…) | `owl:ObjectProperty` (a connector type) |
| Tagged-value property typed by another stereotype | `owl:ObjectProperty` with `rdfs:domain`/`rdfs:range` |
| Tagged-value property typed by a primitive/enum | `owl:DatatypeProperty` (enum → `owl:oneOf` or class) |
| Multiplicity `1..*` / `1` on a required end | `owl:Restriction` `owl:minCardinality`/`someValuesFrom` |
| UAF **Constraint / validation rule** ("must connect to …") | `owl:Restriction` driving gap detection (the high-value part) |
| Stereotype documentation | `rdfs:comment` / `skos:definition` |
| Domain package (Strategic, Operational, …) | ontology module / `rdfs:isDefinedBy` annotation |

Target dialect matches what your CCM `CM2OWL` already emits (see `logs/ccm-export.owl`):
**OWL 2, RDF/XML, OWL-API 5.1-style** — so the result round-trips into CCM and loads in VOM.

## Example gap query (Strategic Motivation)

> For every EnterpriseGoal, which expected related elements are missing?

```sparql
PREFIX uaf: <https://www.omg.org/spec/UAF/ontology#>
SELECT ?goal ?missingKind WHERE {
  ?goal a uaf:EnterpriseGoal .
  VALUES ?missingKind { uaf:Challenge uaf:Opportunity uaf:Risk uaf:EnterpriseObjective }
  FILTER NOT EXISTS {
    ?goal ?rel ?related . ?related a ?missingKind .
  }
}
```

(The exact property/class IRIs come out of Phase 0 — this is the shape, not the final names.)

## Status

- [x] **Phase 0** — `UAFOntologyProbe.groovy` (recon). Run it; then we design Phase 1 from `logs/uaf-probe.json`.
- [ ] Phase 1 — `UAFToOwl.groovy` (TBox converter)
- [ ] Phase 2 — `UAFModelToIndividuals.groovy` (ABox exporter)
- [ ] Phase 3 — `queries/*.rq` + a small gap-report runner

## How to run the Phase-0 probe

1. Start Cameo, **open a UAF project** so the UAF Profile loads — e.g.
   `E:\Magic SW\CameoEA22xUAF1_3\samples\UAF\UAF sample.mdzip`.
2. Start the test harness macro (Tools → Macros → *Test Harness — Start*).
3. Trigger the run (Claude does this over REST):
   ```
   POST http://127.0.0.1:8765/run
   { "scriptPath": "E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\scripts\\uaf-ontology\\UAFOntologyProbe.groovy", "args": [] }
   ```
   Optional `args`: `["strategic,operational"]` to change filter tokens, or `["", "all"]`
   to also dump a one-liner for every stereotype.
4. Read `logs/uaf-probe.json` + `logs/uaf-probe.log`.

# Aircraft Range & Endurance — SysMLv2 Analysis Library

A reusable **SysMLv2 textual** library that translates the classical aircraft
range/endurance **symbology into English** part attributes and captures the
governing equations as executable **`calc def`** elements.

Source of the physics: <https://en.wikipedia.org/wiki/Range_(aeronautics)>

| File | Purpose |
|------|---------|
| [`AircraftRangePerformance.sysml`](AircraftRangePerformance.sysml) | The SysMLv2 model (parts + calc defs + worked analyses + **110-pax trade studies** + requirements + views). |
| [`reference_calc.py`](reference_calc.py) | Python oracle — a 1:1 mirror of every `calc def`. Verifies the math and generates the expected numbers embedded in the model. |
| [`trade_study_calc.py`](trade_study_calc.py) | Trade-study oracle — weight budget + payload/fuel/cargo scenarios; generates the trade-study numbers. |
| [`mission_calc.py`](mission_calc.py) | Mission-sizing oracle — segmented DFW→LAX fuel-fraction / energy sizing, feasibility, and the engineering studies. |

---

## 1. Symbology → English (the parameter dictionary)

Every classical symbol becomes a named `attribute` on a part def, typed with an
ISQ quantity type and SI unit.

| Symbol | English attribute | ISQ type | Unit |
|--------|-------------------|----------|------|
| ρ | `cruiseAirDensity` | `MassDensityValue` | `kg⋅m⁻³` |
| V | `airspeed` / `cruiseAirspeed` | `SpeedValue` | `m/s` |
| S | `referenceWingArea` | `AreaValue` | `m²` |
| b | `wingSpan` | `LengthValue` | `m` |
| AR | `aspectRatio` | `Real` | – |
| e | `oswaldEfficiency` | `Real` | – |
| C_D0 | `zeroLiftDragCoefficient` | `Real` | – |
| K | `inducedDragFactor` = 1/(π·e·AR) | `Real` | – |
| C_L | `liftCoefficient` | `Real` | – |
| C_D | `dragCoefficient` = C_D0 + K·C_L² | `Real` | – |
| L/D | `liftToDrag`, `maxLiftToDragRatio` | `Real` | – |
| m / W | `mass` / `weight` | `MassValue` / `ForceValue` | `kg` / `N` |
| g | `gravity` | `AccelerationValue` | `m⋅s⁻²` |
| P_req | `powerRequired` | `Real` (`PowerValue` at part level) | `W` |
| η_p | `propulsiveEfficiency` | `Real` | – |
| c_p | `powerSpecificFuelConsumption` | `Real` | `kg/(W·s)` |
| c_T | `thrustSpecificFuelConsumption` | `Real` | `kg/(N·s)` |
| E* | `specificEnergy` | `SpecificEnergyValue` | `J/kg` |
| η_struc | `structuralEfficiency` = ln(W_i/W_f) | `Real` | – |
| R | `range` | `Real` (`LengthValue` at part level) | `m` |
| E | `endurance` | `Real` (`DurationValue` at part level) | `s` |

> **`calc def`s use `Real` (SI base units, documented per parameter)** so the
> arithmetic — including `sqrt` and fractional powers — validates against the
> SysMLv2 standard library with zero dimensional-analysis risk. The **part defs**
> carry the typed ISQ attributes with units (the engineering spec).
>
> **Natural log:** `ln` is absent from the SysMLv2 standard library, so
> `MathExtensions::ln` supplies it as a **Groovy textual representation**
> (`rep lnGroovy language "Groovy" /* result = Math.log(x) */`) that a
> scripting-capable tool (e.g. the Cameo simulation engine) executes. The worked
> examples also pass `structuralEfficiency = ln(W_i/W_f)` precomputed, so they
> stay evaluable with stdlib functions alone.

---

## 2. Five propulsion types → three physics families

| Propulsion type | Part def | Family | Max **range** at | Max **endurance** at |
|---|---|---|---|---|
| Piston-propeller | `Cessna172` | A power-producing | (L/D)max | min power required |
| Turboprop | `KingAir350` | A power-producing | (L/D)max | min power required |
| Open fan (propfan) | `RiseNarrowbody` | A power-producing | (L/D)max | min power required |
| Turbofan / turbojet | `A320neoClass` | B thrust-producing | C_L = √(C_D0/3K) (higher V) | (L/D)max |
| Electric (battery) | `VelisClass` | C electric (no log) | (L/D)max | min power required |

**Range equations**

- Family A: `R = (η_p / (g·c_p)) · (L/D) · ln(W_i/W_f)`
- Family B: `R = (V / (g·c_T)) · (L/D) · ln(W_i/W_f)`
- Family C: `R = η_total · E* · (m_batt/m_total) · (L/D) / g`  *(no logarithm — battery mass is constant)*

**Universal characteristic-airspeed ratios** (parabolic drag polar):

```
V(min power)     / V(L/Dmax) = 3^(-1/4) = 0.7598
V(jet max range) / V(L/Dmax) = 3^(+1/4) = 1.3161
```

---

## 3. Worked-example results (from `reference_calc.py`)

| Aircraft | (L/D)max | V@(L/D)max | Max range | Max endurance |
|---|---|---|---|---|
| Cessna 172 (prop) | 10.83 | 41.5 m/s (81 kt) | 1,443 km (779 nm) | 11.0 h* |
| King Air 350 (turboprop) | 16.49 | 101 m/s (196 kt) | 4,361 km (2,355 nm) | 13.7 h* |
| RISE narrowbody (open fan) | 18.12 | 207 m/s | 9,290 km (5,016 nm) | 14.2 h* |
| A320neo-class (turbofan) | 18.12 | 207 m/s | 6,024 km (3,253 nm) | 8.4 h |
| Velis-class (electric) | 17.95 | 35 m/s (69 kt) | 123 km | 1.10 h (66 min) |

\* Theoretical maxima at the absolute min-power speed with full fuel — longer/slower than any practical mission profile. Parameter values are illustrative-but-realistic; see the part defs.

---

## 3b. Payload–range trade studies (110 pax + 5 crew)

A trade study is a **weight-conservation** exercise. For a fixed MTOW:

```
MTOW = OEW + crew + (passengers × (body + luggage)) + cargo + fuel
usefulLoad = MTOW − OEW          # the pie shared by crew, pax, cargo, fuel
```

Every kg of fuel is a kg you can't sell as payload, and fuel buys range through the
Breguet log term — so **passengers, cargo, and fuel compete** for the useful load.

**Standard weights:** passenger body 84 kg (incl. carry-on) + 23 kg checked bag = **107 kg/pax**;
crew **95 kg** each. 110 pax = 11,770 kg; 5 crew = 475 kg.

**The fleet** (one reasonable design per type at the 110-seat class):

| Aircraft (part def) | Family | MTOW | OEW | Useful load | (L/D)max | Cruise |
|---|---|---|---|---|---|---|
| `JetLiner110` (A220-class turbofan) | thrust | 63,100 | 35,300 | 27,800 | 19.28 | 230 m/s |
| `OpenFan110` (RISE open fan) | power | 63,100 | 35,800 | 27,300 | 19.28 | 215 m/s |
| `TurboProp110` (large regional) | power | 36,500 | 20,000 | 16,500 | 16.54 | 160 m/s |
| `PistonLiner110` (DC-6/7 era) | power | 48,500 | 25,000 | 23,500 | 14.11 | 145 m/s |
| `HybridElectric110` (solid-state + turbogenerator) | hybrid | 72,000 | 34,000 | 38,000 | 18.80 | 210 m/s |

*(kg unless noted)*

**Scenario results** — idealized still-air range at (L/D)max, **no reserves/climb** (optimistic
upper bound; subtract ~25–40 % for a realistic block range):

| Aircraft | Scenario | Pax | Cargo (kg) | Fuel (kg) | Range |
|---|---|---:|---:|---:|---|
| **JetLiner110** | Full pax / max fuel | 110 | 0 | 15,555 | 8,828 km (4,767 nm) |
| | Full pax / max cargo | 110 | 6,000 | 9,555 | 5,122 km (2,765 nm) |
| | Max range / 70 pax | 70 | 2,535 | 17,300 | 9,995 km (5,397 nm) |
| | Balanced | 110 | 3,000 | 12,555 | 6,920 km (3,736 nm) |
| **OpenFan110** | Full pax / max fuel | 110 | ~0 | 15,000 | 11,743 km (6,341 nm) |
| | Full pax / max cargo | 110 | 6,000 | 9,055 | 6,701 km (3,618 nm) |
| | Balanced | 110 | 3,000 | 12,055 | 9,172 km (4,952 nm) |
| **TurboProp110** | Full pax / max fuel | 110 | 0 | 4,255 | 1,974 km (1,066 nm) |
| | Full pax / max cargo | 110 | 4,000 | 255 | 112 km (60 nm) |
| | Max range / 70 pax | 70 | 3,535 | 5,000 | 2,346 km (1,267 nm) |
| **PistonLiner110** | Full pax / max fuel | 110 | 0 | 11,255 | 3,666 km (1,980 nm) |
| | Max range / 70 pax | 70 | 3,535 | 12,000 | 3,947 km (2,131 nm) |
| | Balanced | 110 | 2,500 | 8,755 | 2,764 km (1,493 nm) |
| **HybridElectric110** | Battery-only (zero-emission) | 110 | 3,755 | 0 (+22 t batt) | 1,613 km (871 nm) |
| | Hybrid balanced | 110 | 3,755 | 8,000 (+14 t batt) | 4,577 km (2,471 nm) |
| | Max range / 70 pax | 70 | 0 | 8,000 (+14 t batt) | 5,191 km (2,803 nm) |

**What the trade study shows**

- **Fuel ⇄ cargo at full pax:** filling the belly (turbofan max-cargo) roughly *halves* range
  vs. trading that 6 t for fuel — the core revenue decision.
- **Pax ⇄ range:** dropping to 70 pax frees weight for fuel/lighter flight, extending range
  (turbofan 8,828 → 9,995 km; hybrid 4,577 → 5,191 km).
- **Open fan vs turbofan:** same airframe, but the power-producing open rotor's high η_p ≈ 0.88
  yields ~30 % more idealized range than the turbofan on similar fuel.
- **Turboprop is payload-limited:** its small useful load means "full pax + max cargo" leaves
  almost no fuel (112 km) — it's a short-haul, high-density tool, not a long-hauler.
- **The hybrid:** battery mass is *constant dead weight* (no log term), so battery-only range is
  modest (1,613 km) even with an aspirational **850 Wh/kg solid-state** 22-tonne pack; the
  jet-assist turbine — a **series-hybrid generator** in cruise — nearly triples it (4,577 km) by
  adding fuel energy that *burns off*. Hybrid range model:
  `R = η_drive·(E_battery + η_gen·fuel·LHV)·(L/D) / W_avg`.

### Payload-range diagram break points

Each conventional `…_TradeStudy` package adds the three canonical corners of the payload-range
diagram (`pointB_maxPayload`, `pointC_maxFuel`, `pointD_ferry`). `maxPayload` = MZFW − OEW.

| Aircraft | B — max payload (design range) | C — full-tank knee | D — ferry (0 payload) |
|---|---|---|---|
| JetLiner110 | 15,000 kg → 6,778 km | 10,025 kg → 9,995 km | 0 kg → 12,304 km |
| OpenFan110 | 15,000 kg → 8,977 km | 11,825 kg → 11,743 km | 0 kg → 14,971 km |
| TurboProp110 | 13,000 kg → 1,378 km | 11,025 kg → 2,346 km | 0 kg → 3,479 km |
| PistonLiner110 | 16,000 kg → 2,173 km | 11,025 kg → 3,947 km | 0 kg → 5,359 km |

The flat top (range 0 → B) holds max payload while fuel fills to MTOW; the **B→C** segment is the
constant-MTOW *payload-for-fuel* trade (every kg of fuel = a kg of payload); **C→D** burns the rest
of the payload for range at full tanks. The hybrid is omitted here — its battery makes the envelope
two-dimensional (battery ⇄ fuel ⇄ payload).

---

## 3c. The airplane as a whole system (architecture)

Beyond the analysis layer, §13 of the model is a **system architecture**: the airplane decomposed
into subsystems, wired by ports and connections, sequenced by a flight-phase state machine, with
subsystem masses rolled up to OEW and subsystems satisfying capability requirements.

**Base + specializations.** An `abstract part def AirplaneSystem :> TransportAircraft` holds the
common decomposition; each type is a specialization that **inherits its performance parameters from
the §6 analysis part def** and **redefines the propulsion & energy subsystems** (no parameter
duplication, via the shared `TransportAircraft` ancestor):

```
part def HybridSystem :> HybridElectric110, AirplaneSystem {
    part redefines propulsion : HybridPropulsion { … }
    part redefines energyStorage : HybridEnergyStorage { … }
    connection generatorToBus connect propulsion.generatorOutput to electrical.powerBus;
}
```

| Layer | Elements |
|---|---|
| Subsystems | `Airframe`, `PropulsionSystem`*, `EnergyStorageSystem`*, `Avionics`, `FlightControlSystem`, `ElectricalSystem`, `CabinPayloadSystem` |
| Propulsion (per type) | `TurbofanPropulsion`, `OpenFanPropulsion`, `TurbopropPropulsion`, `PistonPropulsion`, `HybridPropulsion` (motors + ducted fans + turbogenerator) |
| Ports | `EnergyPort`, `ThrustPort`, `ElectricalPort`, `DataPort` |
| Connections | energy→propulsion, propulsion→airframe (thrust), FCS→surfaces, FCS→throttle, avionics→FCS, bus→avionics/FCS; (hybrid) generator→bus |
| Behavior | `FlightPhases` state machine (Parked→Taxi→Takeoff→Climb→Cruise→Descent→Approach→Landing) + a `missionProfile` action sequence |
| Mass rollup | `structuralMassRollup = Σ subsystem.mass`, with `constraint { rollup <= operatingEmptyWeight }` |
| Requirements | subsystems `satisfy` `ProvidePropulsion`, `StoreFlightEnergy`, `CarryPayload`, `ControlFlight`, `DistributeElectrical`, `ProvideStructure` |

\* abstract — redefined per propulsion type. The five system part defs are `JetLinerSystem`,
`OpenFanSystem`, `TurboPropSystem`, `PistonSystem`, `HybridSystem`; see `systemArchitectureView`.

> The mass rollup is the bridge between layers: subsystem masses (illustrative, summing to each
> type's OEW) feed the same `operatingEmptyWeight` that the §8 weight budget and §10 range trade
> studies depend on.

---

## 3d. Mission sizing — required fuel for a defined flight (§14)

§14 turns the model around: instead of *fuel → range*, it computes *mission + payload → required
fuel*, then checks feasibility. The standard flight is **Dallas DFW → Los Angeles LAX**
(~1,990 km / 1,074 nm great circle, FL370 cruise, 250 nm reserve), parameterized via a `Mission`
part def so any city pair / aircraft works (`mission_calc.py` is the oracle).

**Method — fuel-fraction sizing (Roskam/Raymer).** The 7 phases each carry a weight fraction
`W_end/W_start`. Terminal phases use fixed fractions (taxi-out 0.990, takeoff 0.995, climb 0.980,
descent 0.990, approach/land 0.992, taxi-in 0.997); **cruise and reserve invert the Breguet range
equation** (which needs `exp` — a second Groovy `rep` alongside `ln`):

```
f_cruise = exp(−d·g·c_T / (V·L/D))                  [jet]   /  …c_p/(η_p·L/D)  [prop]
requiredBlockFuel = ZFW · (1/∏ fractions − 1),   ZFW = OEW + crew + pax + cargo
```

The series-hybrid instead solves an **energy balance** for generator fuel (battery mass is constant):
`k·(Z+½·fuel)·g·R/(L/D) = η_drive·(E_batt + η_gen·fuel·LHV)`. Feasibility is a `constraint def
MissionFeasibility` (`fuel ≤ tank ∧ TOW ≤ MTOW ∧ ZFW ≤ MZFW`), `assert`ed per aircraft.

**DFW→LAX at full pax (110) + 5 crew + 2 t cargo:**

| Aircraft | Block fuel | Tank | TOW / MTOW | Verdict |
|---|---|---|---|---|
| JetLiner110 (turbofan) | 7,074 kg | 17,300 | 56,619 / 63,100 | ✅ feasible |
| OpenFan110 (open fan) | 5,513 kg | 15,000 | 55,558 / 63,100 | ✅ feasible, **lowest burn (−22 %)** |
| TurboProp110 | 7,048 kg | 5,000 | — | ❌ **infeasible — fuel > tank** |
| PistonLiner110 (piston) | 8,995 kg | 12,000 | 48,240 / 48,500 | ⚠️ feasible but **260 kg under MTOW** |
| HybridElectric110 | 3,045 kg + 14 t battery | 8,000 | 65,290 / 72,000 | ✅ feasible (~45 % electric) |

**Engineering decisions the trade study exposes** (`MissionTradeStudy`):
- **Open fan vs turbofan** — same mission, ~22 % less fuel → fleet fuel-cost driver. (The
  turbofan cruises at its max-range L/D ≈ 0.87·(L/D)max; the power-producing open fan cruises at
  (L/D)max — part of the gap.)
- **Turboprop** — cannot serve DFW→LAX nonstop at full pax (regional-only) → route-network decision.
- **Piston** — feasible only with no MTOW margin → not certifiable with real reserves today.
- **Hybrid** — battery-only reaches just **1,187 km of the 1,990 km route (60 %)**; the turbogenerator
  is what closes the mission. A battery-mass sweep shows ~14 t is the sweet spot — bigger batteries
  hit **MZFW (18 t)** then **MTOW (22 t)** before the fuel saving pays off. Powertrain-sizing decision.
- **Max cargo on the mission** is **MZFW-limited** for the jets/hybrid (~3.2 t) and **MTOW-limited**
  for the piston (~2.2 t) — which structural limit binds is itself a design output.

> Ranges/fuels are conceptual-design estimates (best-range cruise — (L/D)max for power-producing
> families, ~0.87·(L/D)max for the turbofan — simplified reserve); subtract margin for a certified
> flight plan. An adversarial review verified every embedded number and the hybrid algebra, and
> flagged that the jet cruise should use its max-range L/D (≈16.70, not (L/D)max) — applied here,
> raising the jet from 6,492 → 7,074 kg.

---

## 4. Validation status

| Check | Tool | Result |
|---|---|---|
| Math self-consistency & expected numbers | `reference_calc.py` (Python 3) | ✅ pass — realistic, internally consistent |
| ISQ value-type names exist | SysML v2 release library (`ISQ*`) | ✅ all confirmed |
| SI unit tokens (`m²`,`kg⋅m⁻³`,`m⋅s⁻²`,`m/s`,`J/kg`,…) | SysML v2 release `SI.sysml` | ✅ byte-exact |
| Available math fns (`sqrt`,`**`,`^`; **no `ln`**) | `RealFunctions.kerml` | ✅ confirmed → `ln` via Groovy `rep` + precomputed input |
| Brackets / quotes balanced | static script | ✅ `{} () []` and `'` all balanced |
| `calc` usage bindings ⊆ `calc def` params | static script | ✅ all usages, 0 problems |
| **Grammar + semantic parse** | **`sysml-validator` CLI (ANTLR4 + semantic engine)** | ✅ **0 errors, 0 warnings** |
| **Production plugin parse + load** | **Cameo SysML v2 plugin** (`SysMLTransientModelBuilder` via `/load-sysml`) | ✅ **full model incl. system architecture loaded, 0 diagnostics** |

> Both validators are used because they are not equivalent: the Cameo/Dassault
> builder is stricter on semantics. It flagged `satisfy <requirement def>` (you
> must `satisfy` a requirement **usage**, not a definition) — an error the
> standalone CLI passed. Run both; treat a clean Cameo `/load-sysml` (which
> type-checks a transient model and only commits if clean) as authoritative.

### How to run the validator

The model was validated with the local **`sysml-validator`** (ANTLR4 grammar +
semantic engine with full standard-library import resolution):

```bash
# run from a dir where ../SysML-v2-Release/sysml.library resolves; the
# -Dds.views.library.path flag enables the bundled DS_Views stub so the
# rendered views (§ Diagrams) resolve with 0 warnings.
cd E:/_Documents/git/TutorialForCatiaMagicApiMCP
java -Dds.views.library.path=E:/_Documents/git/sysml-validator/validator-core/src/main/resources/ds_views_stub \
     -jar E:/_Documents/git/sysml-validator/validator-cli/target/sysml-validator.jar \
     advancedExamples/aircraftRange/version1/AircraftRangePerformance.sysml
```

Result: `Semantic validation enabled with 3370 library symbols loaded` →
`0 errors, 0 warnings` → `VALIDATION PASSED` (exit 0). Without the DS_Views flag
the model still passes with 0 errors but reports DS_Views-not-configured warnings
on the rendered views. If the relative `sysml.library` path doesn't resolve, the
run falls back to grammar-only ("Semantic validation disabled"); run from the
directory above so semantic checks (imports, types) are exercised.

Optionally also importable into **Cameo Systems Modeler** (`E:/Magic SW/CMSoS26xR1pr`,
SysML v2 plugin) via *File ▸ Import ▸ SysML v2 (textual)*.

### Diagrams (rendered views)

The model is structured as **one root package** (`AircraftRangePerformance`, with
`MathExtensions` nested) so CATIA Magic names the imported namespace cleanly. Its
`Views` package types every view by a **DS_Views rendering viewpoint** so they render
as actual diagrams in Cameo (a bare textual `view` is not a diagram):

| View | Viewpoint | Diagram |
|---|---|---|
| `parameterDictionaryView`, `fleetDefinitionView`, `equationsView`, `systemDecompositionView` | `SymbolicViews::gv` | definition (BDD-like) |
| `systemInternalView`, `hybridInternalView` | `SymbolicViews::iv` | internal (IBD: ports & connections) |
| `flightPhasesStateView` | `SymbolicViews::stv` | state machine |
| `missionActionView` | `SymbolicViews::afv` | action flow |
| `requirementsTableView` | `TabularViews::rt` | requirement table |
| `tradeStudyTableView` | `TabularViews::gt` | generic table |

Every view also carries `filter not KerML::Root::Element::isLibraryElement;` so the
imported standard-library / scope elements pulled in by `expose` are **hidden** from the
rendered diagram — without it a diagram is cluttered with library elements that are
worthless to the reader.

> When iterating against a live Cameo session, each `/load-sysml` commits into the open
> project; **undo the prior load (Cameo has full undo) or use a fresh project** before
> re-loading, rather than letting duplicate root packages accumulate.

### Regenerate / verify the numbers

```bash
cd advancedExamples/aircraftRange/version1
python reference_calc.py
```

---

## 5. Rigorous propeller endurance

`calc def EnduranceProp_Rigorous` implements the classical form (Anderson) using
only stdlib `sqrt` and `**`:

```
E = (η_p/(g·c_p)) · (C_L^1.5 / C_D) · √(2·ρ·S) · (W_f^(-1/2) − W_i^(-1/2))
```

This is the form that makes "max endurance = max C_L^1.5/C_D = minimum power
required" mathematically explicit (vs. the simpler constant-velocity
`EnduranceCombustionPowerProducing`).

## 6. Comment conventions (SysML v2)

- Multi-line comments are **one** `/* … */` block (never a stack of one-liners).
- A comment **about an element** is `doc /* … */` placed **inside** that element
  (package, part def, attribute, calc def, calc usage).
- A comment about a **set of lines** is a plain `/* … */` above the first line.
- Calcs carry the **math in the expression**, not in a comment; the natural log
  uses a Groovy textual representation (see §1).

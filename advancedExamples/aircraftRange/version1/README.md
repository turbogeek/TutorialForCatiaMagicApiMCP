# Aircraft Range & Endurance — SysMLv2 Analysis Library

A reusable **SysMLv2 textual** library that translates the classical aircraft
range/endurance **symbology into English** part attributes and captures the
governing equations as executable **`calc def`** elements.

Source of the physics: <https://en.wikipedia.org/wiki/Range_(aeronautics)>

| File | Purpose |
|------|---------|
| [`AircraftRangePerformance.sysml`](AircraftRangePerformance.sysml) | The SysMLv2 model (parts + calc defs + worked analyses + requirements + views). |
| [`reference_calc.py`](reference_calc.py) | Python oracle — a 1:1 mirror of every `calc def`. Verifies the math and generates the expected numbers embedded in the model. |

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

## 4. Validation status

| Check | Tool | Result |
|---|---|---|
| Math self-consistency & expected numbers | `reference_calc.py` (Python 3) | ✅ pass — realistic, internally consistent |
| ISQ value-type names exist | SysML v2 release library (`ISQ*`) | ✅ all confirmed |
| SI unit tokens (`m²`,`kg⋅m⁻³`,`m⋅s⁻²`,`m/s`,`J/kg`,…) | SysML v2 release `SI.sysml` | ✅ byte-exact |
| Available math fns (`sqrt`,`**`,`^`; **no `ln`**) | `RealFunctions.kerml` | ✅ confirmed → `ln` handled as input |
| Brackets / quotes balanced | static script | ✅ `{} () []` and `'` all balanced |
| `calc` usage bindings ⊆ `calc def` params | static script | ✅ 16/16 usages, 0 problems |
| **Grammar + semantic parse** | **`sysml-validator` CLI (ANTLR4 + semantic engine)** | ✅ **0 errors, 0 warnings** |

### How to run the validator

The model was validated with the local **`sysml-validator`** (ANTLR4 grammar +
semantic engine with full standard-library import resolution):

```bash
# run from a dir where ../SysML-v2-Release/sysml.library resolves
cd E:/_Documents/git/TutorialForCatiaMagicApiMCP
java -jar E:/_Documents/git/sysml-validator/validator-cli/target/sysml-validator.jar \
     advancedExamples/aircraftRange/version1/AircraftRangePerformance.sysml
```

Result: `Semantic validation enabled with 3345 library symbols loaded` →
`0 errors, 0 warnings` → `VALIDATION PASSED` (exit 0). If the relative library
path doesn't resolve, the run falls back to grammar-only ("Semantic validation
disabled"); run from the directory above so semantic checks (imports, types) are
exercised.

Optionally also importable into **Cameo Systems Modeler** (`E:/Magic SW/CMSoS26xR1pr`,
SysML v2 plugin) via *File ▸ Import ▸ SysML v2 (textual)*.

### Regenerate / verify the numbers

```bash
cd advancedExamples/aircraftRange/version1
python reference_calc.py
```

---

## 5. Your turn (learning contribution)

`AircraftRangePerformance.sysml` contains an **abstract** `calc def
EnduranceProp_Rigorous` with a `// TODO(you)` block. It asks you to implement the
classical rigorous propeller-endurance expression

```
E = (η_p/(g·c_p)) · (C_L^1.5 / C_D) · √(2·ρ·S) · (W_f^(-1/2) − W_i^(-1/2))
```

using only `sqrt(...)` and `**`. This is the form that makes "max endurance =
max C_L^1.5/C_D = minimum power required" mathematically explicit. Verify your
result by adding the same equation to `reference_calc.py` and comparing against
the Cessna 172 numbers.

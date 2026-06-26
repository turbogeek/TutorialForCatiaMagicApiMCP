#!/usr/bin/env python3
"""
Mission-sizing oracle for the AircraftRangePerformance SysMLv2 model
(deepening: 7-segment mission + DFW->LAX scenario matrix + engineering studies).

This is the *experiment* (project methodology) that proves the segmented-mission
equations are self-consistent and produces the authoritative numbers embedded as
doc comments on the new SysMLv2 Mission + MissionTradeStudy elements. It mirrors
1:1 the new calc defs added to AircraftRangePerformance.sysml.

METHOD (combustion -- fuel-fraction sizing, Roskam/Raymer):
  Walk the aircraft mass down through the 7 segments, each a weight fraction
  W_end/W_start. Terminal segments use standard fixed fractions; the cruise (and
  the reserve leg) use the inverted Breguet range equation:
      jet : f = exp(-d * g * c_T / (V * (L/D)))
      prop: f = exp(-d * g * c_p / (eta_p * (L/D)))
  f_total = f_taxiOut*f_takeoff*f_climb*f_cruise*f_descent*f_approach*f_taxiIn*f_reserve
  Required block+reserve fuel = ZFW*(1/f_total - 1),  ZFW = OEW + crew + pax + cargo.
  Cruise distance = mission - terminal ground coverage (climb/descent fly ~370 km).
  Reserve = a Breguet leg of R_reserve (alternate + 45-min-hold equivalent, 463 km
  / 250 nm by default; adjust per operator).
  Feasible iff fuel <= tankCapacity AND TOW <= MTOW AND ZFW <= MZFW.

METHOD (series-hybrid -- energy balance, battery mass constant):
  Propulsive energy for the whole mission+reserve, with a terminal-power uplift:
      E_need = k_term * W_avg * (R_mission + R_reserve) / (L/D)
      available = eta_drive * (E_battery + eta_gen * fuelMass * LHV)
  Solve for fuelMass (battery is fixed, part of zero-fuel weight). Pure-electric
  reach = eta_drive * E_battery * (L/D) / W.

All masses kg, distances m, energy J, speeds m/s.
"""
import math

G = 9.80665
LHV = 43.0e6
NM = 1852.0
KM = 1000.0

# cabin standard loads
PAX, CREW = 110, 5
PER_PAX = 84.0 + 23.0          # 107 kg (body incl carry-on + checked bag)
CREW_WT = 95.0
PAX_PAYLOAD = PAX * PER_PAX     # 11,770 kg
CREW_PAYLOAD = CREW * CREW_WT   # 475 kg
NOMINAL_CARGO = 2000.0          # belly cargo for the standard flight

# terminal-segment weight fractions W_end/W_start (Roskam transport, realistic)
F_TAXI_OUT     = 0.990
F_TAKEOFF      = 0.995
F_CLIMB        = 0.980
F_DESCENT      = 0.990
F_APPROACH     = 0.992
F_TAXI_IN      = 0.997
F_TERMINALS = (F_TAXI_OUT, F_TAKEOFF, F_CLIMB, F_DESCENT, F_APPROACH, F_TAXI_IN)

# mission geometry: DFW -> LAX great circle ~ 1074 nm = 1990 km
DFW_LAX = 1990.0 * KM
RESERVE_RANGE = 463.0 * KM      # 250 nm: alternate (100 nm) + 45-min hold equivalent
TERMINAL_GROUND = 370.0 * KM    # climb+descent+approach forward progress (not cruise)
K_TERMINAL = 1.05               # hybrid terminal-power uplift

# fleet (mirrors the SysMLv2 part defs)
FLEET = {
 "JetLiner110 (turbofan)": dict(fam="jet",
    OEW=35300, MTOW=63100, MZFW=50775, tank=17300, maxPayload=15000,
    V=230.0, LD=19.284, cT=1.45e-5),
 "OpenFan110 (open fan)": dict(fam="prop",
    OEW=35800, MTOW=63100, MZFW=51275, tank=15000, maxPayload=15000,
    V=215.0, LD=19.284, eta_p=0.88, cp=4.0e-8),
 "TurboProp110 (turboprop)": dict(fam="prop",
    OEW=20000, MTOW=36500, MZFW=33475, tank=5000, maxPayload=13000,
    V=160.0, LD=16.536, eta_p=0.85, cp=9.0e-8),
 "PistonLiner110 (piston)": dict(fam="prop",
    OEW=25000, MTOW=48500, MZFW=41475, tank=12000, maxPayload=16000,
    V=145.0, LD=14.115, eta_p=0.82, cp=8.5e-8),
 "HybridElectric110 (series hybrid)": dict(fam="hybrid",
    OEW=34000, MTOW=72000, MZFW=63475, tank=8000, maxPayload=15000,
    V=210.0, LD=18.796, batt=14000.0, battE=3.06e6, eta_drive=0.90, eta_gen=0.40),
}

def cruise_fraction(ac, distance_m):
    """Breguet weight fraction W_end/W_start to fly `distance_m` in cruise."""
    if ac["fam"] == "jet":
        return math.exp(-distance_m * G * ac["cT"] / (ac["V"] * ac["LD"]))
    return math.exp(-distance_m * G * ac["cp"] / (ac["eta_p"] * ac["LD"]))

def zfw(ac, cargo):
    z = ac["OEW"] + CREW_PAYLOAD + PAX_PAYLOAD + cargo
    if ac["fam"] == "hybrid":
        z += ac["batt"]            # battery is installed -> zero-fuel weight
    return z

# -------------------------------------------------- combustion fuel-fraction sizing
def size_combustion(ac, cargo, mission=DFW_LAX, reserve=RESERVE_RANGE):
    cruise_d = mission - TERMINAL_GROUND
    f_cruise = cruise_fraction(ac, cruise_d)
    f_reserve = cruise_fraction(ac, reserve)
    f_total = (F_TAXI_OUT*F_TAKEOFF*F_CLIMB*f_cruise*F_DESCENT*F_APPROACH*F_TAXI_IN*f_reserve)
    Z = zfw(ac, cargo)
    TOW = Z / f_total
    fuel = TOW - Z
    # segment breakdown by walking the mass down
    m = TOW; seg = {}
    for name, f in [("taxiOut",F_TAXI_OUT),("takeoff",F_TAKEOFF),("climb",F_CLIMB),
                    ("cruise",f_cruise),("descent",F_DESCENT),("approach",F_APPROACH),
                    ("taxiIn",F_TAXI_IN),("reserve",f_reserve)]:
        m2 = m * f; seg[name] = m - m2; m = m2
    return dict(fuel=fuel, TOW=TOW, ZFW=Z, f_total=f_total, seg=seg)

# -------------------------------------------------- hybrid energy-balance sizing
def size_hybrid(ac, cargo, batt=None, mission=DFW_LAX, reserve=RESERVE_RANGE):
    batt = ac["batt"] if batt is None else batt
    Z = ac["OEW"] + CREW_PAYLOAD + PAX_PAYLOAD + cargo + batt
    E_batt = batt * ac["battE"]
    R = mission + reserve
    a, eg, LD = ac["eta_drive"], ac["eta_gen"], ac["LD"]
    # K_TERMINAL*(Z+0.5 fuel)*g*R/LD = a*(E_batt + eg*fuel*LHV)
    C = K_TERMINAL * G * R / LD
    fuel = (a*E_batt - Z*C) / (0.5*C - a*eg*LHV)
    TOW = Z + max(0.0, fuel)
    # pure-electric reach at this Z (no fuel)
    reach = a * E_batt * LD / (Z * G)
    return dict(fuel=fuel, TOW=TOW, ZFW=Z, E_batt=E_batt, reach=reach,
                batt=batt, batt_E_prop=a*E_batt,
                fuel_E_prop=a*eg*max(0.0,fuel)*LHV)

def size(ac, cargo, **kw):
    return size_hybrid(ac, cargo, **kw) if ac["fam"]=="hybrid" else size_combustion(ac, cargo, **kw)

def feasible(ac, r):
    return (0 <= r["fuel"] <= ac["tank"]) and (r["TOW"] <= ac["MTOW"]+1) and (r["ZFW"] <= ac["MZFW"]+1)

def binding(ac, r):
    if r["fuel"] > ac["tank"]:  return "fuel>tank"
    if r["TOW"]  > ac["MTOW"]:  return "TOW>MTOW"
    if r["ZFW"]  > ac["MZFW"]:  return "ZFW>MZFW"
    return "-"

def km(m): return m/KM
def nm(m): return m/NM

# ================================================================= report
print("="*100)
print("MISSION: Dallas DFW -> Los Angeles LAX")
print(f"  great-circle {km(DFW_LAX):,.0f} km ({nm(DFW_LAX):,.0f} nm); cruise {km(DFW_LAX-TERMINAL_GROUND):,.0f} km; "
      f"reserve {km(RESERVE_RANGE):.0f} km ({nm(RESERVE_RANGE):.0f} nm)")
print(f"  cabin: {PAX} pax x {PER_PAX:.0f} = {PAX_PAYLOAD:,.0f} kg; crew {CREW} x {CREW_WT:.0f} = {CREW_PAYLOAD:.0f} kg; "
      f"nominal cargo {NOMINAL_CARGO:,.0f} kg")
print("="*100)
print("SCENARIO MATRIX  (full 110 pax + 5 crew + 2,000 kg cargo)")
print(f"  {'aircraft':<34}{'fuel kg':>9}{'tank':>7}{'margin':>8}{'TOW kg':>9}{'MTOW':>8}  feasible  binding")
mat = {}
for name, ac in FLEET.items():
    r = size(ac, NOMINAL_CARGO); mat[name] = r
    print(f"  {name:<34}{r['fuel']:>9,.0f}{ac['tank']:>7,.0f}{ac['tank']-r['fuel']:>8,.0f}"
          f"{r['TOW']:>9,.0f}{ac['MTOW']:>8,.0f}  {'YES' if feasible(ac,r) else 'NO ':<8}  {binding(ac,r)}")

print("\nSEGMENT FUEL BREAKDOWN (kg) -- combustion aircraft")
for name, ac in FLEET.items():
    if ac["fam"]=="hybrid": continue
    s = mat[name]["seg"]
    print(f"  {name:<26} taxiOut {s['taxiOut']:.0f} | takeoff {s['takeoff']:.0f} | climb {s['climb']:.0f} | "
          f"CRUISE {s['cruise']:.0f} | descent {s['descent']:.0f} | approach {s['approach']:.0f} | "
          f"taxiIn {s['taxiIn']:.0f} | reserve {s['reserve']:.0f}")

print("\nMAX CARGO that still closes DFW->LAX at full pax (binary search)")
for name, ac in FLEET.items():
    r0 = size(ac, 0.0)
    if not feasible(ac, r0):
        print(f"  {name:<34} INFEASIBLE even at zero cargo ({binding(ac,r0)})"); continue
    lo, hi = 0.0, ac["maxPayload"]
    for _ in range(50):
        mid=(lo+hi)/2
        if feasible(ac, size(ac, mid)): lo=mid
        else: hi=mid
    rr = size(ac, lo)
    print(f"  {name:<34} max cargo {lo:>7,.0f} kg  (then {binding(ac, size(ac, lo+1))}); fuel {rr['fuel']:,.0f} kg")

print("\nHYBRID battery vs generator-fuel split (DFW->LAX, full pax + 2t cargo)")
ac = FLEET["HybridElectric110 (series hybrid)"]; r = mat["HybridElectric110 (series hybrid)"]
tot = r["batt_E_prop"]+r["fuel_E_prop"]
print(f"  battery {r['batt']:.0f} kg -> {r['batt_E_prop']:.3e} J ({100*r['batt_E_prop']/tot:.0f}%); "
      f"gen fuel {r['fuel']:.0f} kg -> {r['fuel_E_prop']:.3e} J ({100*r['fuel_E_prop']/tot:.0f}%)")
print(f"  pure-electric reach (battery only) ~ {km(r['reach']):,.0f} km of {km(DFW_LAX):,.0f} km "
      f"-> {'FEASIBLE' if r['reach']>=DFW_LAX else 'INFEASIBLE'} ({100*r['reach']/DFW_LAX:.0f}%)")

print("\nSTUDY (i) PAYLOAD vs ACHIEVABLE RANGE (full tank, TOW<=MTOW)")
def max_range(ac, cargo):
    # largest mission range whose required fuel == tank (or TOW==MTOW)
    lo, hi = 0.0, 12000e3
    for _ in range(60):
        mid=(lo+hi)/2
        r = size(ac, cargo, mission=mid)
        if feasible(ac, r): lo=mid
        else: hi=mid
    return lo
for name, ac in FLEET.items():
    cargo_cap = max(0.0, ac["maxPayload"] - PAX_PAYLOAD)   # cargo on top of 110 pax, MZFW/maxPayload limited
    cells = []
    for tag, cargo in [("0 cargo",0.0),("half",0.5*cargo_cap),("max cargo",cargo_cap)]:
        cells.append(f"{tag} {km(max_range(ac,cargo)):,.0f} km")
    print(f"  {name:<34} " + " | ".join(cells))

print("\nSTUDY (ii) HYBRID battery-mass sweep (DFW->LAX, full pax + 2t cargo)")
for batt in [8000,12000,14000,18000,22000]:
    r = size_hybrid(ac, NOMINAL_CARGO, batt=float(batt))
    print(f"  battery {batt:>6,} kg -> gen fuel {r['fuel']:>6,.0f} kg | TOW {r['TOW']:>7,.0f} | "
          f"{'feasible' if feasible(ac,r) else 'INFEASIBLE ('+binding(ac,r)+')'}")
print("="*100)

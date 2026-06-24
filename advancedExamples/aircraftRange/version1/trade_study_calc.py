#!/usr/bin/env python3
"""
Trade-study oracle for the AircraftRangePerformance SysMLv2 library.

Extends reference_calc.py with a WEIGHT BUDGET and PAYLOAD-RANGE TRADE STUDY:
for 110-passenger / 5-crew aircraft of each propulsion type, sweep scenarios
that trade passengers / fuel / cargo within a fixed MTOW and report the
resulting range. Produces the authoritative numbers embedded in the SysMLv2
trade-study packages.

Weight law (the heart of every scenario):
    MTOW = OEW + crewWeight + paxPayload + cargoMass + fuelMass     [kg]
    usefulLoad = MTOW - OEW                                          [kg]
    (paxPayload + crewWeight) + cargoMass + fuelMass <= usefulLoad

Range uses the same family equations as reference_calc.py (1:1 with the
`calc def`s). The hybrid adds a combined battery + fuel-generator energy model.

All masses kg, forces N, energies J, speeds m/s, ranges m (reported in km/nm).
"""
import math

G = 9.80665
LHV_JET = 43.0e6        # jet/avgas lower heating value [J/kg]

# ---- core aero (mirror of reference_calc.py / the calc defs) ----
def induced_drag_factor(e, AR):           return 1.0/(math.pi*e*AR)
def max_lift_to_drag(cd0, k):             return 1.0/(2.0*math.sqrt(cd0*k))
def cl_for_max_LD(cd0, k):                return math.sqrt(cd0/k)
def drag_coefficient(cd0, k, cl):         return cd0 + k*cl**2
def structural_efficiency(Wi, Wf):        return math.log(Wi/Wf)

# ---- range families (mirror of the calc defs) ----
def range_power_producing(eta_p, cp, LD, eta_struc):
    return (eta_p/(G*cp))*LD*eta_struc
def range_jet(V, cT, LD, eta_struc):
    return (V/(G*cT))*LD*eta_struc
def range_electric(eta_tot, Estar, batt_frac, LD):
    return eta_tot*Estar*batt_frac*LD/G

# ---- hybrid: constant battery mass (no log) + burned generator fuel ----
def range_hybrid(eta_drive, eta_gen, E_batt_J, fuelMass, LD, M_takeoff):
    """Energy method. Total propulsive energy = drivetrain*(battery + gen*fuel*LHV).
       Battery mass is constant; fuel burns, so use the average cruise WEIGHT (N).
       M_takeoff is a MASS [kg]; convert to weight here."""
    W_avg_N = (M_takeoff - 0.5*fuelMass)*G
    E_prop = eta_drive*(E_batt_J + eta_gen*fuelMass*LHV_JET)
    return E_prop*LD/W_avg_N            # J*(L/D)/N = m

# ===================================================================== cabin
PAX = 110
CREW = 5
PAX_BODY = 84.0          # kg incl. carry-on (modern standard ~ 84-88)
LUGGAGE = 23.0           # kg checked bag per pax
CREW_WT = 95.0           # kg per crew incl. bag
PER_PAX = PAX_BODY + LUGGAGE
PAX_PAYLOAD_FULL = PAX*PER_PAX
CREW_PAYLOAD = CREW*CREW_WT
print(f"Cabin: {PAX} pax x {PER_PAX:.0f} kg = {PAX_PAYLOAD_FULL:,.0f} kg ; "
      f"crew {CREW} x {CREW_WT:.0f} = {CREW_PAYLOAD:,.0f} kg")

# ===================================================================== fleet
# Each aircraft: 110-seat class. cargo capped by belly structural limit.
FLEET = {
 "Turbofan (JetLiner110, A220-class)": dict(
    fam="jet", MTOW=63100, OEW=35300, tankFuel=17300, cargoMax=6000,
    S=112.3, b=35.1, e=0.82, cd0=0.019, rho=0.38, V=230.0, cT=1.45e-5, LD=None),
 "Open fan (OpenFan110, RISE narrowbody)": dict(
    fam="power", MTOW=63100, OEW=35800, tankFuel=15000, cargoMax=6000,
    S=112.3, b=35.1, e=0.82, cd0=0.019, rho=0.40, V=215.0, eta_p=0.88, cp=4.0e-8, LD=None),
 "Turboprop (TurboProp110, large regional)": dict(
    fam="power", MTOW=36500, OEW=20000, tankFuel=5000, cargoMax=4000,
    S=95.0, b=33.0, e=0.82, cd0=0.027, rho=0.55, V=160.0, eta_p=0.85, cp=9.0e-8, LD=None),
 "Piston-prop (PistonLiner110, DC-6/7 era)": dict(
    fam="power", MTOW=48500, OEW=25000, tankFuel=12000, cargoMax=5000,
    S=135.9, b=35.8, e=0.78, cd0=0.029, rho=0.66, V=145.0, eta_p=0.82, cp=8.5e-8, LD=None),
 "Hybrid-electric (HybridElectric110, solid-state + turbogenerator)": dict(
    fam="hybrid", MTOW=72000, OEW=34000, tankFuel=8000, cargoMax=5000,
    S=112.3, b=35.1, e=0.82, cd0=0.020, rho=0.40, V=210.0,
    eta_drive=0.90, eta_gen=0.40, batt_Wh_kg=850.0, LD=None),
}
for ac in FLEET.values():
    AR = ac["b"]**2/ac["S"]
    ac["AR"] = AR
    ac["K"] = induced_drag_factor(ac["e"], AR)
    ac["LD"] = max_lift_to_drag(ac["cd0"], ac["K"])

def km(m):  return m/1000.0
def nm(m):  return m/1852.0

def cruise_range(ac, M_takeoff, fuelMass, battMass=0.0):
    """Range for given takeoff MASS [kg] & fuel load [kg], at (L/D)max cruise.
       Weight ratio uses masses (dimensionless); only the hybrid term needs N."""
    LD = ac["LD"]
    fam = ac["fam"]
    if fam == "jet":
        Mf = M_takeoff - fuelMass
        return range_jet(ac["V"], ac["cT"], LD, structural_efficiency(M_takeoff, Mf))
    if fam == "power":
        Mf = M_takeoff - fuelMass
        return range_power_producing(ac["eta_p"], ac["cp"], LD, structural_efficiency(M_takeoff, Mf))
    if fam == "hybrid":
        E_batt = battMass*ac["batt_Wh_kg"]*3600.0
        return range_hybrid(ac["eta_drive"], ac["eta_gen"], E_batt, fuelMass, LD, M_takeoff)

# ===================================================================== scenarios
def scenarios_for(name, ac):
    rows = []
    OEW, MTOW = ac["OEW"], ac["MTOW"]
    useful = MTOW - OEW
    base = CREW_PAYLOAD                       # crew always aboard
    if ac["fam"] != "hybrid":
        # avail for pax+cargo+fuel after crew
        avail = useful - base
        # S1 Full pax / max fuel (cargo=0)
        paxP = PAX_PAYLOAD_FULL
        fuel = min(ac["tankFuel"], avail - paxP)
        cargo = avail - paxP - fuel
        rows.append(("Full pax / max fuel", PAX, cargo, fuel,
                     cruise_range(ac, OEW+base+paxP+cargo+fuel, fuel)))
        # S2 Full pax / max cargo (fuel = remainder)
        cargo = min(ac["cargoMax"], avail - paxP)
        fuel = min(ac["tankFuel"], avail - paxP - cargo)
        rows.append(("Full pax / max cargo", PAX, cargo, fuel,
                     cruise_range(ac, OEW+base+paxP+cargo+fuel, fuel)))
        # S3 Max range / reduced pax (70 pax, no cargo, fill tank)
        pax2 = 70
        paxP2 = pax2*PER_PAX
        fuel = min(ac["tankFuel"], avail - paxP2)
        cargo = avail - paxP2 - fuel
        rows.append(("Max range / 70 pax", pax2, cargo, fuel,
                     cruise_range(ac, OEW+base+paxP2+cargo+fuel, fuel)))
        # S4 Balanced revenue: full pax + half cargoMax + fuel remainder
        paxP = PAX_PAYLOAD_FULL
        cargo = min(ac["cargoMax"]*0.5, avail - paxP)
        fuel = min(ac["tankFuel"], avail - paxP - cargo)
        rows.append(("Balanced (pax+half cargo)", PAX, cargo, fuel,
                     cruise_range(ac, OEW+base+paxP+cargo+fuel, fuel)))
    else:
        avail = useful - base                # pax + cargo + battery + fuel
        paxP = PAX_PAYLOAD_FULL
        # H1 Battery-only (zero-emission), big battery, no gen fuel
        batt = avail - paxP                  # all remaining to battery (cargo=0,fuel=0)
        batt = min(batt, 22000)              # cap battery pack size
        cargo = avail - paxP - batt
        W = OEW+base+paxP+cargo+batt
        rows.append(("Battery-only (green)", PAX, cargo, 0.0, batt,
                     cruise_range(ac, W, 0.0, batt)))
        # H2 Hybrid balanced: 14t battery + gen fuel + full pax
        batt = 14000.0
        fuel = min(ac["tankFuel"], avail - paxP - batt)
        cargo = avail - paxP - batt - fuel
        W = OEW+base+paxP+cargo+batt+fuel
        rows.append(("Hybrid balanced", PAX, cargo, fuel, batt,
                     cruise_range(ac, W, fuel, batt)))
        # H3 Max range hybrid: 14t battery + max fuel + 70 pax, NO cargo (fly light)
        pax2 = 70; paxP2 = pax2*PER_PAX
        batt = 14000.0
        fuel = ac["tankFuel"]
        cargo = 0.0
        W = OEW+base+paxP2+cargo+batt+fuel    # below MTOW -> lighter -> longer range
        rows.append(("Max range hybrid / 70 pax", pax2, cargo, fuel, batt,
                     cruise_range(ac, W, fuel, batt)))
    return useful, rows

def dump_literals(name, ac):
    """Emit the exact literal inputs the SysMLv2 calc usages need to reproduce range."""
    print(f"--- SysML literals: {name}")
    OEW, MTOW = ac["OEW"], ac["MTOW"]; useful = MTOW-OEW; base = CREW_PAYLOAD
    _, rows = scenarios_for(name, ac)
    for r in rows:
        if ac["fam"] != "hybrid":
            sc, pax, cargo, fuel, R = r
            Mto = OEW+base+pax*PER_PAX+cargo+fuel
            es = structural_efficiency(Mto, Mto-fuel) if fuel > 0 else 0.0
            print(f"    {sc:<26} Mto={Mto:,.0f}kg eta_struc={es:.4f} LD={ac['LD']:.3f} "
                  f"V={ac.get('V'):.0f} -> {km(R):,.0f} km")
        else:
            sc, pax, cargo, fuel, batt, R = r
            Mto = OEW+base+pax*PER_PAX+cargo+batt+fuel
            Ebatt = batt*ac["batt_Wh_kg"]*3600.0
            Wavg = (Mto-0.5*fuel)*G
            print(f"    {sc:<26} Mto={Mto:,.0f}kg Ebatt={Ebatt:.4e}J fuel={fuel:.0f}kg "
                  f"Wavg={Wavg:.4e}N LD={ac['LD']:.3f} -> {km(R):,.0f} km")

for name, ac in FLEET.items():
    print("="*92)
    print(f"{name}")
    print(f"  MTOW {ac['MTOW']:,} kg | OEW {ac['OEW']:,} kg | AR {ac['AR']:.2f} "
          f"| (L/D)max {ac['LD']:.2f} | cruise {ac['V']:.0f} m/s")
    useful, rows = scenarios_for(name, ac)
    print(f"  useful load (MTOW-OEW) = {useful:,.0f} kg")
    if ac["fam"] != "hybrid":
        print(f"  {'scenario':<28}{'pax':>4}{'cargo kg':>10}{'fuel kg':>10}{'range km':>11}{'range nm':>10}")
        for sc, pax, cargo, fuel, R in rows:
            print(f"  {sc:<28}{pax:>4}{cargo:>10,.0f}{fuel:>10,.0f}{km(R):>11,.0f}{nm(R):>10,.0f}")
    else:
        print(f"  battery {ac['batt_Wh_kg']:.0f} Wh/kg | eta_drive {ac['eta_drive']} | eta_gen {ac['eta_gen']}")
        print(f"  {'scenario':<28}{'pax':>4}{'cargo':>8}{'fuel':>7}{'batt kg':>9}{'range km':>11}{'range nm':>10}")
        for sc, pax, cargo, fuel, batt, R in rows:
            print(f"  {sc:<28}{pax:>4}{cargo:>8,.0f}{fuel:>7,.0f}{batt:>9,.0f}{km(R):>11,.0f}{nm(R):>10,.0f}")
print("="*92)

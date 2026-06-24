#!/usr/bin/env python3
"""
Reference implementation of the AircraftRangePerformance SysMLv2 library.

Purpose (per project methodology): this is the *experiment* that proves the
equations in AircraftRangePerformance.sysml are self-consistent and produces the
authoritative "expected value" numbers embedded as comments in the model so the
worked examples are independently verifiable.

Every function here mirrors one `calc def` in the .sysml file 1:1, using the same
English parameter names. All quantities are SI base units (documented per arg).

Symbology -> English (from https://en.wikipedia.org/wiki/Range_(aeronautics)):
  rho   air density            [kg/m^3]   -> airDensity
  V     true airspeed          [m/s]      -> airspeed
  S     reference wing area    [m^2]      -> referenceWingArea
  b     wing span              [m]        -> wingSpan
  AR    aspect ratio           [-]        -> aspectRatio
  e     Oswald efficiency      [-]        -> oswaldEfficiency
  C_D0  zero-lift (parasite)   [-]        -> zeroLiftDragCoefficient
  K     induced-drag factor    [-]        -> inducedDragFactor = 1/(pi e AR)
  C_L   lift coefficient       [-]        -> liftCoefficient
  C_D   drag coefficient       [-]        -> dragCoefficient
  L/D   lift-to-drag ratio     [-]        -> liftToDrag
  W     weight                 [N]        -> weight = mass*g
  g     gravity                [m/s^2]    -> gravity
  P     power required         [W]        -> powerRequired
  c_p   power-spec fuel cons.  [kg/(W*s)] -> powerSpecificFuelConsumption
  c_T   thrust-spec fuel cons. [kg/(N*s)] -> thrustSpecificFuelConsumption
  eta_p propulsive efficiency  [-]        -> propulsiveEfficiency
  E*    battery specific energy[J/kg]     -> specificEnergy
  eta_struc = ln(W_i/W_f)      [-]        -> structuralEfficiency
"""
import math

PI = math.pi

# ---------------------------------------------------------------- aero core
def induced_drag_factor(oswaldEfficiency, aspectRatio):
    return 1.0 / (PI * oswaldEfficiency * aspectRatio)

def aspect_ratio(wingSpan, referenceWingArea):
    return wingSpan**2 / referenceWingArea

def lift_coefficient_level_flight(weight, airDensity, airspeed, referenceWingArea):
    return (2.0 * weight) / (airDensity * airspeed**2 * referenceWingArea)

def drag_coefficient(zeroLiftDragCoefficient, inducedDragFactor, liftCoefficient):
    return zeroLiftDragCoefficient + inducedDragFactor * liftCoefficient**2

def lift_to_drag(liftCoefficient, dragCoefficient):
    return liftCoefficient / dragCoefficient

def max_lift_to_drag(zeroLiftDragCoefficient, inducedDragFactor):
    return 1.0 / (2.0 * math.sqrt(zeroLiftDragCoefficient * inducedDragFactor))

def power_required(airDensity, airspeed, referenceWingArea,
                   zeroLiftDragCoefficient, inducedDragFactor, weight):
    parasite = 0.5 * airDensity * airspeed**3 * referenceWingArea * zeroLiftDragCoefficient
    induced  = (2.0 * inducedDragFactor * weight**2) / (airDensity * airspeed * referenceWingArea)
    return parasite + induced

# ------------------------------------------------- characteristic airspeeds
def _speed_scale(weight, airDensity, referenceWingArea):
    # 2W/(rho S) has units of speed^2
    return 2.0 * weight / (airDensity * referenceWingArea)

def speed_for_max_LD(weight, airDensity, referenceWingArea, cd0, k):
    # C_L = sqrt(C_D0/K)  ->  V = sqrt( (2W/rhoS) * sqrt(K/C_D0) )
    return math.sqrt(_speed_scale(weight, airDensity, referenceWingArea) * math.sqrt(k / cd0))

def speed_for_min_power(weight, airDensity, referenceWingArea, cd0, k):
    # C_L = sqrt(3 C_D0/K)  ->  V = sqrt( (2W/rhoS) * sqrt(K/(3 C_D0)) )
    return math.sqrt(_speed_scale(weight, airDensity, referenceWingArea) * math.sqrt(k / (3.0 * cd0)))

def speed_for_max_range_jet(weight, airDensity, referenceWingArea, cd0, k):
    # C_L = sqrt(C_D0/(3K))  ->  V = sqrt( (2W/rhoS) * sqrt(3K/C_D0) )
    return math.sqrt(_speed_scale(weight, airDensity, referenceWingArea) * math.sqrt(3.0 * k / cd0))

# ------------------------------------------------------ structural efficiency
def structural_efficiency(weight_initial, weight_final):
    return math.log(weight_initial / weight_final)   # ln

# ---------------------------------------- range & endurance by physics family
# Family A: power-producing combustion (piston-prop, turboprop, open fan)
def range_power_producing(propulsiveEfficiency, gravity, powerSpecificFuelConsumption,
                          liftToDrag, structuralEfficiency):
    return (propulsiveEfficiency / (gravity * powerSpecificFuelConsumption)) \
           * liftToDrag * structuralEfficiency

def endurance_power_producing(propulsiveEfficiency, gravity, powerSpecificFuelConsumption,
                              liftToDrag, airspeed, structuralEfficiency):
    return (propulsiveEfficiency / (gravity * powerSpecificFuelConsumption)) \
           * liftToDrag * (1.0 / airspeed) * structuralEfficiency

# Family B: thrust-producing combustion (turbojet / turbofan)
def range_jet(airspeed, gravity, thrustSpecificFuelConsumption, liftToDrag, structuralEfficiency):
    return (airspeed / (gravity * thrustSpecificFuelConsumption)) * liftToDrag * structuralEfficiency

def endurance_jet(gravity, thrustSpecificFuelConsumption, liftToDrag, structuralEfficiency):
    return (1.0 / (gravity * thrustSpecificFuelConsumption)) * liftToDrag * structuralEfficiency

# Family C: electric (battery mass constant -> NO logarithm)
def range_electric(totalEfficiency, specificEnergy, batteryMassFraction, liftToDrag, gravity):
    return totalEfficiency * specificEnergy * batteryMassFraction * liftToDrag / gravity

def endurance_electric(totalEfficiency, specificEnergy, batteryMass, powerRequired):
    return totalEfficiency * specificEnergy * batteryMass / powerRequired

# ============================================================ worked examples
G = 9.80665

EXAMPLES = [
    # name, family, params
    dict(name="Cessna 172  (piston-propeller)", family="powerA",
         mass=1111.0, S=16.2, b=11.0, e=0.72, cd0=0.036, rho=1.0,
         eta_p=0.80, c_p=8.5e-8, fuel_mass=144.0),
    dict(name="King Air 350 (turboprop)", family="powerA",
         mass=6800.0, S=28.8, b=17.65, e=0.80, cd0=0.025, rho=0.55,
         eta_p=0.85, c_p=9.0e-8, fuel_mass=1633.0),
    dict(name="RISE narrowbody (open fan)", family="powerA",
         mass=73500.0, S=122.6, b=35.8, e=0.80, cd0=0.020, rho=0.38,
         eta_p=0.86, c_p=4.2e-8, fuel_mass=16000.0, cruise_V=225.0),
    dict(name="A320neo-class (turbofan / jet)", family="jet",
         mass=73500.0, S=122.6, b=35.8, e=0.80, cd0=0.020, rho=0.38,
         c_T=1.5e-5, fuel_mass=16000.0, cruise_V=230.0),
    dict(name="Velis-class (electric)", family="electric",
         mass=600.0, S=9.51, b=10.71, e=0.85, cd0=0.025, rho=1.1,
         eta_total=0.78, E_specific_Wh_kg=180.0, batt_mass=80.0),
]

def fmt(x, u):
    return f"{x:,.4g} {u}"

for ex in EXAMPLES:
    print("=" * 78)
    print(ex["name"])
    print("-" * 78)
    W = ex["mass"] * G
    AR = aspect_ratio(ex["b"], ex["S"])
    K = induced_drag_factor(ex["e"], AR)
    ldmax = max_lift_to_drag(ex["cd0"], K)
    print(f"  weight W                 = {fmt(W,'N')}   (mass {ex['mass']} kg)")
    print(f"  aspect ratio AR          = {AR:.3f}")
    print(f"  induced-drag factor K    = {K:.5f}")
    print(f"  (L/D)max                 = {ldmax:.3f}")

    v_ldmax = speed_for_max_LD(W, ex["rho"], ex["S"], ex["cd0"], K)
    v_minp  = speed_for_min_power(W, ex["rho"], ex["S"], ex["cd0"], K)
    v_jet   = speed_for_max_range_jet(W, ex["rho"], ex["S"], ex["cd0"], K)
    print(f"  V @ (L/D)max             = {v_ldmax:.2f} m/s  ({v_ldmax*1.94384:.1f} kt)")
    print(f"  V @ min power required   = {v_minp:.2f} m/s  ({v_minp*1.94384:.1f} kt)   ratio {v_minp/v_ldmax:.4f}")
    print(f"  V @ jet max range        = {v_jet:.2f} m/s  ({v_jet*1.94384:.1f} kt)   ratio {v_jet/v_ldmax:.4f}")

    if ex["family"] in ("powerA", "jet"):
        Wf = W - ex["fuel_mass"] * G
        eta_struc = structural_efficiency(W, Wf)
        print(f"  weight ratio W_i/W_f     = {W/Wf:.4f}")
        print(f"  structural eff ln(Wi/Wf) = {eta_struc:.4f}")

    if ex["family"] == "powerA":
        # Max RANGE at (L/D)max
        R = range_power_producing(ex["eta_p"], G, ex["c_p"], ldmax, eta_struc)
        # Max ENDURANCE at min-power condition: L/D there = (sqrt3/2)*ldmax
        cl_mp = math.sqrt(3.0 * ex["cd0"] / K)
        cd_mp = drag_coefficient(ex["cd0"], K, cl_mp)
        ld_mp = lift_to_drag(cl_mp, cd_mp)
        # open fan cruises transonic: report range at its given cruise too if provided
        E = endurance_power_producing(ex["eta_p"], G, ex["c_p"], ld_mp, v_minp, eta_struc)
        print(f"  L/D @ min-power          = {ld_mp:.3f}")
        print(f"  >> MAX RANGE  (@L/Dmax)  = {R/1000:,.1f} km  ({R/1852:,.0f} nm)")
        print(f"  >> MAX ENDURANCE (@minP) = {E/3600:,.2f} h")

    elif ex["family"] == "jet":
        ld_at_jet_range = lift_to_drag(
            math.sqrt(ex["cd0"]/(3*K)),
            drag_coefficient(ex["cd0"], K, math.sqrt(ex["cd0"]/(3*K))))
        R = range_jet(ex["cruise_V"], G, ex["c_T"], ld_at_jet_range, eta_struc)
        E = endurance_jet(G, ex["c_T"], ldmax, eta_struc)
        print(f"  L/D @ jet max-range cond = {ld_at_jet_range:.3f}")
        print(f"  >> MAX RANGE (cruise V)  = {R/1000:,.1f} km  ({R/1852:,.0f} nm)")
        print(f"  >> MAX ENDURANCE (@L/Dmax)= {E/3600:,.2f} h")

    elif ex["family"] == "electric":
        Espec = ex["E_specific_Wh_kg"] * 3600.0  # Wh/kg -> J/kg
        massfrac = ex["batt_mass"] / ex["mass"]
        R = range_electric(ex["eta_total"], Espec, massfrac, ldmax, G)
        # endurance at min power
        cl_mp = math.sqrt(3.0 * ex["cd0"] / K)
        cd_mp = drag_coefficient(ex["cd0"], K, cl_mp)
        ld_mp = lift_to_drag(cl_mp, cd_mp)
        P_mp = power_required(ex["rho"], v_minp, ex["S"], ex["cd0"], K, W)
        E = endurance_electric(ex["eta_total"], Espec, ex["batt_mass"], P_mp)
        print(f"  battery specific energy  = {Espec:,.0f} J/kg ({ex['E_specific_Wh_kg']} Wh/kg)")
        print(f"  battery mass fraction    = {massfrac:.4f}")
        print(f"  P_required @ min-power    = {P_mp/1000:.2f} kW")
        print(f"  >> MAX RANGE  (@L/Dmax)  = {R/1000:,.1f} km")
        print(f"  >> MAX ENDURANCE (@minP) = {E/3600:,.2f} h ({E/60:.0f} min)")

print("=" * 78)
print("Characteristic-speed ratios (universal, parabolic polar):")
print(f"  V(min power) / V(L/Dmax)  = 3**-0.25 = {3**-0.25:.4f}")
print(f"  V(jet max range)/V(L/Dmax)= 3**+0.25 = {3**0.25:.4f}")

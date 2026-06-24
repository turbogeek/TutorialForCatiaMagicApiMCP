/* ======================================================================== */
/* AircraftRangePerformance.sysml */
/* ------------------------------------------------------------------------ */
/* A reusable SysMLv2 library for aircraft RANGE and ENDURANCE analysis, */
/* with the aerodynamic parameters translated from classical symbology into */
/* English attribute names, and the governing equations captured as `calc def` */
/* elements so the model is executable, not merely descriptive. */
/*  */
/* Source of the physics:  https://en.wikipedia.org/wiki/Range_(aeronautics) */
/*  */
/* Headline conditions (Wikipedia, propeller case): */
/*   - MAXIMUM ENDURANCE occurs at the point of MINIMUM POWER REQUIRED */
/*     (lowest fuel/energy flow to hold steady level flight). */
/*   - MAXIMUM RANGE occurs where speed / power-required is greatest, */
/*     i.e. at MAXIMUM LIFT-TO-DRAG RATIO (L/D)max. */
/*  */
/* The five propulsion types requested (piston-prop, turboprop, open fan, */
/* turbofan/jet, electric) collapse onto THREE energy-accounting families: */
/*   FAMILY A  Power-producing combustion (piston-prop, turboprop, open fan) */
/*             fuel flow ~ SHAFT POWER. R = (eta_p/(g*c_p))*(L/D)*ln(W_i/W_f) */
/*             Max endurance @ min power required;  max range @ (L/D)max. */
/*   FAMILY B  Thrust-producing combustion (turbojet / turbofan) */
/*             fuel flow ~ THRUST.      R = (V/(g*c_T))*(L/D)*ln(W_i/W_f) */
/*             Max endurance @ (L/D)max; max range @ higher speed. */
/*   FAMILY C  Electric (battery) -- mass is CONSTANT, so the log DISAPPEARS: */
/*             R = eta_total*E*(m_batt/m_total)*(L/D)/g  (Hepperle). */
/*             Max endurance @ min power required;  max range @ (L/D)max. */
/*  */
/* NOTE ON ln(): the SysMLv2 standard Kernel Function Library has sqrt, **, */
/*   ^, trig, abs, min/max -- but NOT a natural logarithm. The logarithmic */
/*   Breguet term is therefore exposed as an explicit Real input */
/*   `structuralEfficiency = ln(W_i/W_f)` (Wikipedia's "structural efficiency" */
/*   factor eta_struc), so every worked example stays computable with stdlib */
/*   functions only. A signature-only `ln` calc def is also declared (the way */
/*   the stdlib declares `sqrt`) to show the symbolic path. */
/*  */
/* Expected values in the worked examples come from the companion oracle */
/* `reference_calc.py` (run it to regenerate / verify). */
/* ======================================================================== */

package MathExtensions {
    private import ScalarValues::*;
    doc /* Math primitives missing from the SysMLv2 standard Kernel Function Library. */

    /* Natural logarithm. Declared signature-only, exactly as RealFunctions */
    /* declares `sqrt` (no body) -- an analysis tool supplies the implementation. */
    /* Needed for the Breguet structural-efficiency term ln(W_initial/W_final). */
    calc def ln { in x : Real[1]; return : Real[1]; }
}

package AircraftRangePerformance {
    private import ScalarValues::*;      /* Real, Boolean, ... */
    private import ISQ::*;               /* MassValue, ForceValue, AreaValue, ... */
    private import SI::*;                /* units: kg, m, s, N, W, J, ... */
    private import RealFunctions::*;     /* sqrt  (and operators **, ^) */
    private import MathExtensions::*;    /* ln */

    /* ====================================================================
     * 1.  SYMBOLOGY -> ENGLISH  (the parameter dictionary, with ISQ units)
     * ==================================================================== */

    abstract part def Aircraft {
        doc /* Common airframe + aerodynamic parameters shared by every aircraft.
                Each attribute is the English name for one classical symbol. */

        attribute mass : MassValue;                 /* m   : all-up mass */
        attribute referenceWingArea : AreaValue;    /* S   : reference wing area */
        attribute wingSpan : LengthValue;           /* b   : wing span */
        attribute aspectRatio : Real;               /* AR  = b^2 / S */
        attribute oswaldEfficiency : Real;          /* e   : span (Oswald) efficiency */
        attribute zeroLiftDragCoefficient : Real;   /* C_D0: parasite drag coefficient */
        attribute inducedDragFactor : Real;         /* K   = 1 / (pi * e * AR) */
        attribute cruiseAirDensity : MassDensityValue; /* rho : air density at cruise altitude */
        attribute maxLiftToDragRatio : Real;        /* (L/D)max */
        attribute gravity : AccelerationValue = 9.80665 ['m⋅s⁻²']; /* g : standard gravity */
    }

    /* ---- Family A: power-producing combustion (prop / turboprop / open fan) ---- */
    abstract part def PowerProducingAircraft :> Aircraft {
        doc /* Shaft-power propulsor burning fuel. Fuel flow is proportional to power. */
        attribute propulsiveEfficiency : Real;             /* eta_p : propeller/fan efficiency */
        attribute powerSpecificFuelConsumption : Real;     /* c_p  [kg/(W*s)] */
        attribute usableFuelMass : MassValue;              /* burnable fuel */
    }

    /* ---- Family B: thrust-producing combustion (turbojet / turbofan) ---- */
    abstract part def ThrustProducingAircraft :> Aircraft {
        doc /* Direct-thrust engine. Fuel flow is proportional to thrust. */
        attribute thrustSpecificFuelConsumption : Real;    /* c_T  [kg/(N*s)] */
        attribute cruiseAirspeed : SpeedValue;             /* V    : transonic cruise speed */
        attribute usableFuelMass : MassValue;
    }

    /* ---- Family C: electric (battery) ---- */
    abstract part def ElectricAircraft :> Aircraft {
        doc /* Battery-electric powertrain. Battery mass does NOT change in flight. */
        attribute totalEfficiency : Real;          /* eta_total = eta_motor * eta_esc * eta_prop */
        attribute specificEnergy : SpecificEnergyValue; /* E*  : usable battery specific energy */
        attribute batteryMass : MassValue;         /* m_batt */
    }

    /* ====================================================================
     * 2.  AERODYNAMIC CORE  (calc def = one governing equation each)
     *     All parameters are Real in SI base units (documented per arg) so
     *     the math validates with stdlib functions only.
     * ==================================================================== */

    calc def AspectRatio {
        doc /* AR = b^2 / S.  [dimensionless] */
        in wingSpan : Real;          /* b [m] */
        in referenceWingArea : Real; /* S [m^2] */
        return aspectRatio : Real = wingSpan ** 2 / referenceWingArea;
    }

    calc def InducedDragFactor {
        doc /* K = 1 / (pi * e * AR).  Couples induced drag to lift^2. [dimensionless] */
        in oswaldEfficiency : Real;  /* e */
        in aspectRatio : Real;       /* AR */
        return inducedDragFactor : Real =
            1.0 / (3.141592653589793 * oswaldEfficiency * aspectRatio);
    }

    calc def LiftCoefficientLevelFlight {
        doc /* Steady level flight (L = W):  C_L = 2W / (rho * V^2 * S). [dimensionless] */
        in weight : Real;            /* W   [N] */
        in airDensity : Real;        /* rho [kg/m^3] */
        in airspeed : Real;          /* V   [m/s] */
        in referenceWingArea : Real; /* S   [m^2] */
        return liftCoefficient : Real =
            (2.0 * weight) / (airDensity * airspeed ** 2 * referenceWingArea);
    }

    calc def DragCoefficient {
        doc /* Drag polar:  C_D = C_D0 + K * C_L^2. [dimensionless] */
        in zeroLiftDragCoefficient : Real; /* C_D0 */
        in inducedDragFactor : Real;       /* K */
        in liftCoefficient : Real;         /* C_L */
        return dragCoefficient : Real =
            zeroLiftDragCoefficient + inducedDragFactor * liftCoefficient ** 2;
    }

    calc def LiftToDragRatio {
        doc /* L/D = C_L / C_D. [dimensionless] */
        in liftCoefficient : Real;
        in dragCoefficient : Real;
        return liftToDrag : Real = liftCoefficient / dragCoefficient;
    }

    calc def MaxLiftToDragRatio {
        doc /* (L/D)max = 1 / (2 * sqrt(C_D0 * K)).
                Occurs where parasite drag = induced drag. [dimensionless] */
        in zeroLiftDragCoefficient : Real;
        in inducedDragFactor : Real;
        return maxLiftToDrag : Real =
            1.0 / (2.0 * sqrt(zeroLiftDragCoefficient * inducedDragFactor));
    }

    calc def DragForce {
        doc /* D = 0.5 * rho * V^2 * S * C_D.  [N] */
        in airDensity : Real; in airspeed : Real;
        in referenceWingArea : Real; in dragCoefficient : Real;
        return drag : Real =
            0.5 * airDensity * airspeed ** 2 * referenceWingArea * dragCoefficient;
    }

    calc def PowerRequired {
        doc /* P_req(V) = 0.5*rho*V^3*S*C_D0  +  2*K*W^2 / (rho*V*S)   [W]
                term 1 = PARASITE power (rises as V^3)
                term 2 = INDUCED  power (falls as 1/V)
                Their sum has a MINIMUM -> the max-endurance airspeed. */
        in airDensity : Real;        /* rho [kg/m^3] */
        in airspeed : Real;          /* V   [m/s] */
        in referenceWingArea : Real; /* S   [m^2] */
        in zeroLiftDragCoefficient : Real; /* C_D0 */
        in inducedDragFactor : Real; /* K */
        in weight : Real;            /* W   [N] */
        return powerRequired : Real =
            0.5 * airDensity * airspeed ** 3 * referenceWingArea * zeroLiftDragCoefficient
            + (2.0 * inducedDragFactor * weight ** 2) / (airDensity * airspeed * referenceWingArea);
    }

    /* ====================================================================
     * 3.  CHARACTERISTIC AIRSPEEDS
     *     All from  V = sqrt( 2W/(rho*S*C_L) )  at the optimum C_L.
     *     Universal ratios (parabolic polar):
     *        V(min power)    / V(L/Dmax) = 3^(-1/4) = 0.7598
     *        V(jet max range)/ V(L/Dmax) = 3^(+1/4) = 1.3161
     * ==================================================================== */

    calc def SpeedForMaxRange_PowerProducing {
        doc /* Max-range airspeed for power-producing & electric aircraft = V at (L/D)max.
                C_L = sqrt(C_D0/K).  V = sqrt( (2W/(rho*S)) * sqrt(K/C_D0) ).  [m/s] */
        in weight : Real; in airDensity : Real; in referenceWingArea : Real;
        in zeroLiftDragCoefficient : Real; in inducedDragFactor : Real;
        return airspeed : Real =
            sqrt( (2.0 * weight / (airDensity * referenceWingArea))
                  * sqrt(inducedDragFactor / zeroLiftDragCoefficient) );
    }

    calc def SpeedForMaxEndurance_PowerProducing {
        doc /* Max-endurance airspeed (power-producing & electric) = V at MIN POWER REQUIRED.
                C_L = sqrt(3*C_D0/K).  V = sqrt( (2W/(rho*S)) * sqrt(K/(3*C_D0)) ).  [m/s]
                ~ 0.76 * V(L/Dmax). */
        in weight : Real; in airDensity : Real; in referenceWingArea : Real;
        in zeroLiftDragCoefficient : Real; in inducedDragFactor : Real;
        return airspeed : Real =
            sqrt( (2.0 * weight / (airDensity * referenceWingArea))
                  * sqrt(inducedDragFactor / (3.0 * zeroLiftDragCoefficient)) );
    }

    calc def SpeedForMaxRange_Jet {
        doc /* Max-range airspeed for a JET = V at max(V*L/D).
                C_L = sqrt(C_D0/(3K)).  V = sqrt( (2W/(rho*S)) * sqrt(3K/C_D0) ).  [m/s]
                ~ 1.32 * V(L/Dmax). */
        in weight : Real; in airDensity : Real; in referenceWingArea : Real;
        in zeroLiftDragCoefficient : Real; in inducedDragFactor : Real;
        return airspeed : Real =
            sqrt( (2.0 * weight / (airDensity * referenceWingArea))
                  * sqrt(3.0 * inducedDragFactor / zeroLiftDragCoefficient) );
    }

    calc def SpeedForMaxEndurance_Jet {
        doc /* Max-endurance airspeed for a JET = V at (L/D)max (minimum drag).
                Same speed as SpeedForMaxRange_PowerProducing. [m/s] */
        in weight : Real; in airDensity : Real; in referenceWingArea : Real;
        in zeroLiftDragCoefficient : Real; in inducedDragFactor : Real;
        return airspeed : Real =
            sqrt( (2.0 * weight / (airDensity * referenceWingArea))
                  * sqrt(inducedDragFactor / zeroLiftDragCoefficient) );
    }

    /* ====================================================================
     * 4.  STRUCTURAL EFFICIENCY  (the logarithmic Breguet term)
     * ==================================================================== */

    calc def StructuralEfficiency {
        doc /* eta_struc = ln(W_initial / W_final).  Wikipedia's "structural efficiency"
                factor. Requires MathExtensions::ln (not in the SysMLv2 stdlib).
                For computable examples, pass the precomputed value as a Real input
                to the Range/Endurance calcs instead. [dimensionless] */
        in weightInitial : Real;  /* W_i [N]  (heavier, full fuel) */
        in weightFinal : Real;    /* W_f [N]  (lighter, fuel burned) */
        return structuralEfficiency : Real = ln(weightInitial / weightFinal);
    }

    /* ====================================================================
     * 5.  RANGE & ENDURANCE  (one pair of calc defs per physics family)
     * ==================================================================== */

    /* ---- FAMILY A : power-producing combustion ---- */
    calc def RangeCombustionPowerProducing {
        doc /* Breguet range, power-producing propulsion (prop / turboprop / open fan).
                R = (eta_p / (g * c_p)) * (L/D) * ln(W_i/W_f).
                Evaluate L/D at (L/D)max for MAX RANGE.  Returns range [m]. */
        in propulsiveEfficiency : Real;            /* eta_p */
        in gravity : Real;                         /* g   [m/s^2] */
        in powerSpecificFuelConsumption : Real;    /* c_p [kg/(W*s)] */
        in liftToDrag : Real;                      /* L/D */
        in structuralEfficiency : Real;            /* ln(W_i/W_f) */
        return range : Real =
            (propulsiveEfficiency / (gravity * powerSpecificFuelConsumption))
            * liftToDrag * structuralEfficiency;
    }

    calc def EnduranceCombustionPowerProducing {
        doc /* Endurance, power-producing.
                E = (eta_p / (g * c_p)) * (L/D) * (1/V) * ln(W_i/W_f).
                Evaluate V and L/D at the MIN-POWER condition for MAX ENDURANCE.
                Returns endurance [s].  (Assumes ~constant V & L/D over the cruise.) */
        in propulsiveEfficiency : Real;
        in gravity : Real;
        in powerSpecificFuelConsumption : Real;
        in liftToDrag : Real;
        in airspeed : Real;                        /* V [m/s] at min-power condition */
        in structuralEfficiency : Real;
        return endurance : Real =
            (propulsiveEfficiency / (gravity * powerSpecificFuelConsumption))
            * liftToDrag * (1.0 / airspeed) * structuralEfficiency;
    }

    /* ==================================================================== */
    /* LEARNING CONTRIBUTION (your turn) -- rigorous propeller endurance. */
    /* The calc above uses the simplified constant-velocity form. The classical */
    /* RIGOROUS propeller endurance (Anderson, "Aircraft Performance & Design") */
    /* integrates as fuel burns at constant C_L & altitude (so V drops): */
    /*  */
    /*   E = (eta_p / (g*c_p)) * (C_L^1.5 / C_D) * sqrt(2*rho*S) */
    /*         * ( W_final^(-1/2) - W_initial^(-1/2) ) */
    /*  */
    /* This form makes explicit WHY max endurance is at max(C_L^1.5 / C_D), */
    /* i.e. minimum power required. Implement the return EXPRESSION below using */
    /* only stdlib `sqrt(...)` and the `**` operator (negative/fractional */
    /* exponents are allowed). The def is abstract (no body) until you do -- */
    /* which is legal SysML v2, so the model still validates as-is. */
    /*  */
    /* TODO(you): replace `return endurance : Real;` with the bound expression: */
    /*   return endurance : Real = */
    /*       (propulsiveEfficiency / (gravity * powerSpecificFuelConsumption)) */
    /*       * (liftCoefficient ** 1.5 / dragCoefficient) */
    /*       * sqrt(2.0 * airDensity * referenceWingArea) */
    /*       * (weightFinal ** (-0.5) - weightInitial ** (-0.5)); */
    /* (Verify your result against reference_calc.py: add the same equation */
    /*  there and compare to the Cessna 172 worked numbers.) */
    /* ==================================================================== */
    calc def EnduranceProp_Rigorous {
        in propulsiveEfficiency : Real;          /* eta_p */
        in gravity : Real;                       /* g   [m/s^2] */
        in powerSpecificFuelConsumption : Real;  /* c_p [kg/(W*s)] */
        in liftCoefficient : Real;               /* C_L at the endurance condition */
        in dragCoefficient : Real;               /* C_D at the endurance condition */
        in airDensity : Real;                    /* rho [kg/m^3] */
        in referenceWingArea : Real;             /* S   [m^2] */
        in weightInitial : Real;                 /* W_i [N] */
        in weightFinal : Real;                   /* W_f [N] */
        return endurance : Real;                 /* [s]  <-- abstract; you implement */
    }

    /* ---- FAMILY B : thrust-producing combustion (jet) ---- */
    calc def RangeJet {
        doc /* Breguet range, thrust-producing (turbojet / turbofan).
                R = (V / (g * c_T)) * (L/D) * ln(W_i/W_f).
                Evaluate V & L/D at the jet max-range condition.  Returns range [m]. */
        in airspeed : Real;                        /* V   [m/s] */
        in gravity : Real;                         /* g   [m/s^2] */
        in thrustSpecificFuelConsumption : Real;   /* c_T [kg/(N*s)] */
        in liftToDrag : Real;
        in structuralEfficiency : Real;
        return range : Real =
            (airspeed / (gravity * thrustSpecificFuelConsumption))
            * liftToDrag * structuralEfficiency;
    }

    calc def EnduranceJet {
        doc /* Endurance, jet.  E = (1 / (g * c_T)) * (L/D) * ln(W_i/W_f).
                MAX ENDURANCE @ (L/D)max.  Returns endurance [s]. */
        in gravity : Real;
        in thrustSpecificFuelConsumption : Real;
        in liftToDrag : Real;
        in structuralEfficiency : Real;
        return endurance : Real =
            (1.0 / (gravity * thrustSpecificFuelConsumption))
            * liftToDrag * structuralEfficiency;
    }

    /* ---- FAMILY C : electric (no logarithm) ---- */
    calc def RangeElectric {
        doc /* Electric range (Hepperle). Battery mass constant => NO logarithm.
                R = eta_total * E* * (m_batt/m_total) * (L/D) / g.
                Evaluate L/D at (L/D)max for MAX RANGE.  Returns range [m]. */
        in totalEfficiency : Real;        /* eta_total */
        in specificEnergy : Real;         /* E* [J/kg] */
        in batteryMassFraction : Real;    /* m_batt / m_total */
        in liftToDrag : Real;
        in gravity : Real;
        return range : Real =
            totalEfficiency * specificEnergy * batteryMassFraction * liftToDrag / gravity;
    }

    calc def EnduranceElectric {
        doc /* Electric endurance.  E = eta_total * (E* * m_batt) / P_required.
                Evaluate P_required at the MIN-POWER condition for MAX ENDURANCE.
                Returns endurance [s]. */
        in totalEfficiency : Real;
        in specificEnergy : Real;     /* E* [J/kg] */
        in batteryMass : Real;        /* m_batt [kg] */
        in powerRequired : Real;      /* P_req [W] at min-power condition */
        return endurance : Real =
            totalEfficiency * specificEnergy * batteryMass / powerRequired;
    }

    /* ====================================================================
     * 6.  CONCRETE LIBRARY AIRCRAFT  (symbology -> English, with real values)
     *     Values are illustrative-but-realistic. See reference_calc.py.
     * ==================================================================== */

    part def Cessna172 :> PowerProducingAircraft {
        doc /* Light piston-propeller trainer. Family A. */
        attribute redefines mass = 1111.0 [kg];
        attribute redefines referenceWingArea = 16.2 ['m²'];
        attribute redefines wingSpan = 11.0 [m];
        attribute redefines aspectRatio = 7.469;
        attribute redefines oswaldEfficiency = 0.72;
        attribute redefines zeroLiftDragCoefficient = 0.036;
        attribute redefines inducedDragFactor = 0.05919;
        attribute redefines cruiseAirDensity = 1.0 ['kg⋅m⁻³'];
        attribute redefines maxLiftToDragRatio = 10.832;
        attribute redefines propulsiveEfficiency = 0.80;
        attribute redefines powerSpecificFuelConsumption = 8.5e-8; /* [kg/(W*s)] ~306 g/kWh */
        attribute redefines usableFuelMass = 144.0 [kg];
    }

    part def KingAir350 :> PowerProducingAircraft {
        doc /* Twin turboprop. Family A (shaft power). */
        attribute redefines mass = 6800.0 [kg];
        attribute redefines referenceWingArea = 28.8 ['m²'];
        attribute redefines wingSpan = 17.65 [m];
        attribute redefines aspectRatio = 10.817;
        attribute redefines oswaldEfficiency = 0.80;
        attribute redefines zeroLiftDragCoefficient = 0.025;
        attribute redefines inducedDragFactor = 0.03678;
        attribute redefines cruiseAirDensity = 0.55 ['kg⋅m⁻³'];
        attribute redefines maxLiftToDragRatio = 16.488;
        attribute redefines propulsiveEfficiency = 0.85;
        attribute redefines powerSpecificFuelConsumption = 9.0e-8; /* [kg/(W*s)] */
        attribute redefines usableFuelMass = 1633.0 [kg];
    }

    part def RiseNarrowbody :> PowerProducingAircraft {
        doc /* Notional CFM RISE open-fan (unducted/propfan) narrowbody.
                Modeled as power-producing with very high propulsive efficiency --
                the open-rotor selling point. Cruises transonic, so the low-speed
                optimum-airspeed calcs are indicative only (compressibility ignored). */
        attribute redefines mass = 73500.0 [kg];
        attribute redefines referenceWingArea = 122.6 ['m²'];
        attribute redefines wingSpan = 35.8 [m];
        attribute redefines aspectRatio = 10.454;
        attribute redefines oswaldEfficiency = 0.80;
        attribute redefines zeroLiftDragCoefficient = 0.020;
        attribute redefines inducedDragFactor = 0.03806;
        attribute redefines cruiseAirDensity = 0.38 ['kg⋅m⁻³'];
        attribute redefines maxLiftToDragRatio = 18.122;
        attribute redefines propulsiveEfficiency = 0.86;          /* open rotor advantage */
        attribute redefines powerSpecificFuelConsumption = 4.2e-8; /* [kg/(W*s)] */
        attribute redefines usableFuelMass = 16000.0 [kg];
    }

    part def A320neoClass :> ThrustProducingAircraft {
        doc /* Modern single-aisle turbofan. Family B (thrust). */
        attribute redefines mass = 73500.0 [kg];
        attribute redefines referenceWingArea = 122.6 ['m²'];
        attribute redefines wingSpan = 35.8 [m];
        attribute redefines aspectRatio = 10.454;
        attribute redefines oswaldEfficiency = 0.80;
        attribute redefines zeroLiftDragCoefficient = 0.020;
        attribute redefines inducedDragFactor = 0.03806;
        attribute redefines cruiseAirDensity = 0.38 ['kg⋅m⁻³'];
        attribute redefines maxLiftToDragRatio = 18.122;
        attribute redefines thrustSpecificFuelConsumption = 1.5e-5; /* [kg/(N*s)] ~0.53 lb/lbf/h */
        attribute redefines cruiseAirspeed = 230.0 ['m/s'];         /* ~M0.78 at 37,000 ft */
        attribute redefines usableFuelMass = 16000.0 [kg];
    }

    part def VelisClass :> ElectricAircraft {
        doc /* Light battery-electric trainer (Pipistrel Velis-class). Family C. */
        attribute redefines mass = 600.0 [kg];
        attribute redefines referenceWingArea = 9.51 ['m²'];
        attribute redefines wingSpan = 10.71 [m];
        attribute redefines aspectRatio = 12.061;
        attribute redefines oswaldEfficiency = 0.85;
        attribute redefines zeroLiftDragCoefficient = 0.025;
        attribute redefines inducedDragFactor = 0.03105;
        attribute redefines cruiseAirDensity = 1.1 ['kg⋅m⁻³'];
        attribute redefines maxLiftToDragRatio = 17.947;
        attribute redefines totalEfficiency = 0.78;                 /* motor*esc*prop */
        attribute redefines specificEnergy = 648000.0 ['J/kg'];     /* 180 Wh/kg usable */
        attribute redefines batteryMass = 80.0 [kg];
    }

    /* ====================================================================
     * 7.  WORKED ANALYSES  (calc usages = the calc elements "calculating")
     *     One full analysis per physics family. Turboprop & open fan reuse
     *     the FAMILY A pattern shown for the Cessna 172.
     *     Expected results (from reference_calc.py) are in the doc comments.
     * ==================================================================== */

    /* ---- FAMILY A worked example: Cessna 172 (piston-prop) ---- */
    package Cessna172_Analysis {
        private import AircraftRangePerformance::*;
        doc /* Inputs: W=10898 N (1111 kg * g), S=16.2 m^2, b=11.0 m,
                e=0.72, C_D0=0.036, rho=1.0 kg/m^3, eta_p=0.80,
                c_p=8.5e-8 kg/(W*s), fuel=144 kg. */

        calc aspectRatio : AspectRatio {
            in wingSpan = 11.0;  in referenceWingArea = 16.2;   /* expect 7.469 */
        }
        calc inducedDragFactor : InducedDragFactor {
            in oswaldEfficiency = 0.72;  in aspectRatio = 7.469; /* expect 0.05919 */
        }
        calc maxLD : MaxLiftToDragRatio {
            in zeroLiftDragCoefficient = 0.036;  in inducedDragFactor = 0.05919; /* expect 10.832 */
        }
        calc vMaxRange : SpeedForMaxRange_PowerProducing {       /* expect 41.53 m/s (80.7 kt) */
            in weight = 10898.0;  in airDensity = 1.0;  in referenceWingArea = 16.2;
            in zeroLiftDragCoefficient = 0.036;  in inducedDragFactor = 0.05919;
        }
        calc vMaxEndurance : SpeedForMaxEndurance_PowerProducing { /* expect 31.56 m/s (61.3 kt) */
            in weight = 10898.0;  in airDensity = 1.0;  in referenceWingArea = 16.2;
            in zeroLiftDragCoefficient = 0.036;  in inducedDragFactor = 0.05919;
        }
        /* structuralEfficiency = ln(10898 / (10898 - 144*9.80665)) = ln(1.1489) = 0.1388 */
        calc maxRange : RangeCombustionPowerProducing {          /* expect 1.443e6 m = 1443 km (779 nm) */
            in propulsiveEfficiency = 0.80;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 8.5e-8;
            in liftToDrag = 10.832;          /* at (L/D)max */
            in structuralEfficiency = 0.1388;
        }
        calc maxEndurance : EnduranceCombustionPowerProducing {  /* expect 3.96e4 s = 11.0 h */
            in propulsiveEfficiency = 0.80;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 8.5e-8;
            in liftToDrag = 9.380;           /* L/D at the min-power condition */
            in airspeed = 31.56;             /* min-power speed */
            in structuralEfficiency = 0.1388;
        }
    }

    /* ---- FAMILY B worked example: A320neo-class (turbofan) ---- */
    package A320neo_Analysis {
        private import AircraftRangePerformance::*;
        doc /* Inputs: W=720789 N (73500 kg * g), S=122.6 m^2, b=35.8 m,
                e=0.80, C_D0=0.020, rho=0.38 kg/m^3, c_T=1.5e-5 kg/(N*s),
                V_cruise=230 m/s, fuel=16000 kg.  structuralEfficiency=ln(1.2783)=0.2455. */

        calc maxLD : MaxLiftToDragRatio {                        /* expect 18.122 */
            in zeroLiftDragCoefficient = 0.020;  in inducedDragFactor = 0.03806;
        }
        calc vMaxRangeJet : SpeedForMaxRange_Jet {               /* expect 271.9 m/s */
            in weight = 720789.0;  in airDensity = 0.38;  in referenceWingArea = 122.6;
            in zeroLiftDragCoefficient = 0.020;  in inducedDragFactor = 0.03806;
        }
        /* L/D at the jet max-range condition (C_L=sqrt(C_D0/3K)) = 15.694 */
        calc maxRange : RangeJet {                               /* expect 6.024e6 m = 6024 km (3253 nm) */
            in airspeed = 230.0;             /* actual transonic cruise speed */
            in gravity = 9.80665;
            in thrustSpecificFuelConsumption = 1.5e-5;
            in liftToDrag = 15.694;
            in structuralEfficiency = 0.2455;
        }
        calc maxEndurance : EnduranceJet {                       /* expect 3.02e4 s = 8.40 h */
            in gravity = 9.80665;
            in thrustSpecificFuelConsumption = 1.5e-5;
            in liftToDrag = 18.122;          /* (L/D)max */
            in structuralEfficiency = 0.2455;
        }
    }

    /* ---- FAMILY C worked example: Velis-class (electric) ---- */
    package Velis_Analysis {
        private import AircraftRangePerformance::*;
        doc /* Inputs: W=5884 N (600 kg * g), S=9.51 m^2, b=10.71 m,
                e=0.85, C_D0=0.025, rho=1.1 kg/m^3, eta_total=0.78,
                E*=648000 J/kg, m_batt=80 kg (fraction 0.1333). */

        calc maxLD : MaxLiftToDragRatio {                        /* expect 17.947 */
            in zeroLiftDragCoefficient = 0.025;  in inducedDragFactor = 0.03105;
        }
        calc vMaxEndurance : SpeedForMaxEndurance_PowerProducing { /* expect 26.90 m/s */
            in weight = 5884.0;  in airDensity = 1.1;  in referenceWingArea = 9.51;
            in zeroLiftDragCoefficient = 0.025;  in inducedDragFactor = 0.03105;
        }
        calc powerAtMinPower : PowerRequired {                   /* expect 10193 W = 10.19 kW */
            in airDensity = 1.1;  in airspeed = 26.90;  in referenceWingArea = 9.51;
            in zeroLiftDragCoefficient = 0.025;  in inducedDragFactor = 0.03105;
            in weight = 5884.0;
        }
        calc maxRange : RangeElectric {                          /* expect 1.233e5 m = 123.3 km */
            in totalEfficiency = 0.78;  in specificEnergy = 648000.0;
            in batteryMassFraction = 0.13333;  in liftToDrag = 17.947;  in gravity = 9.80665;
        }
        calc maxEndurance : EnduranceElectric {                  /* expect 3973 s = 1.10 h (66 min) */
            in totalEfficiency = 0.78;  in specificEnergy = 648000.0;
            in batteryMass = 80.0;  in powerRequired = 10193.0;
        }
    }

    /* ====================================================================
     * 8.  WEIGHT BUDGET & PAYLOAD-RANGE  (the trade-study foundation)
     *     Weight law:  MTOW = OEW + crew + paxPayload + cargo + fuel
     *     usefulLoad = MTOW - OEW. Every kg of fuel is a kg NOT sold as
     *     payload -> the passengers/cargo/fuel trade. Masses [kg],
     *     energies [J], weights (where needed) [N].
     * ==================================================================== */

    part def CabinStandardLoads {
        doc /* Standard per-occupant weights used across the trade studies. */
        attribute passengerBodyWeight : MassValue = 84.0 [kg];        /* incl. carry-on */
        attribute checkedLuggagePerPassenger : MassValue = 23.0 [kg]; /* one checked bag */
        attribute crewMemberWeight : MassValue = 95.0 [kg];           /* incl. bag */
        /* => per-passenger payload = 84 + 23 = 107 kg ; 110 pax = 11,770 kg ;
              5 crew * 95 = 475 kg. */
    }

    calc def PassengerPayload {
        doc /* paxPayload = passengerCount * (body + luggage). [kg] */
        in passengerCount : Real;
        in perPassengerWeight : Real;   /* body + luggage [kg] */
        return passengerPayload : Real = passengerCount * perPassengerWeight;
    }

    calc def UsefulLoad {
        doc /* usefulLoad = MTOW - OEW. The pie shared by crew + pax + cargo + fuel. [kg] */
        in maxTakeoffWeight : Real;       /* MTOW [kg] */
        in operatingEmptyWeight : Real;   /* OEW  [kg] */
        return usefulLoad : Real = maxTakeoffWeight - operatingEmptyWeight;
    }

    calc def TakeoffMass {
        doc /* Sum the weight budget; should be <= MTOW. [kg] */
        in operatingEmptyWeight : Real;
        in crewWeight : Real;
        in passengerPayload : Real;
        in cargoMass : Real;
        in fuelMass : Real;
        return takeoffMass : Real =
            operatingEmptyWeight + crewWeight + passengerPayload + cargoMass + fuelMass;
    }

    calc def MassRatio {
        doc /* Breguet weight ratio W_i/W_f = takeoffMass / (takeoffMass - fuelMass).
                structuralEfficiency = ln(this); supply that ln value to the Range calcs. */
        in takeoffMass : Real;
        in fuelMass : Real;
        return massRatio : Real = takeoffMass / (takeoffMass - fuelMass);
    }

    calc def RangeHybridElectric {
        doc /* Series-hybrid range (energy method). Battery mass is CONSTANT (no log term,
                dead weight all flight); the turbogenerator burns fuel into electricity,
                extending range. Fuel burns off -> use the mid-cruise average weight.
                R = eta_drive * (E_battery + eta_gen * fuelMass * fuelHeatingValue)
                      * (L/D) / W_average
                E_battery [J], fuelHeatingValue [J/kg], W_average [N].  Returns range [m]. */
        in drivetrainEfficiency : Real;    /* eta_drive = motor * esc * fan */
        in generatorEfficiency : Real;     /* eta_gen  = fuel -> electric */
        in batteryEnergy : Real;           /* E_battery [J] = batteryMass * specificEnergy */
        in fuelMass : Real;                /* generator fuel [kg] */
        in fuelHeatingValue : Real;        /* LHV [J/kg] */
        in liftToDrag : Real;
        in averageWeight : Real;           /* W_avg [N] = (takeoffMass - 0.5*fuelMass)*g */
        return range : Real =
            drivetrainEfficiency * (batteryEnergy + generatorEfficiency * fuelMass * fuelHeatingValue)
            * liftToDrag / averageWeight;
    }

    /* ====================================================================
     * 9.  TRANSPORT AIRCRAFT  (110 passengers + 5 crew, one per type)
     *     Parallel hierarchy to section 1: TransportAircraft :> Aircraft
     *     adds the weight-budget attributes; family mixins add consumption.
     * ==================================================================== */

    abstract part def TransportAircraft :> Aircraft {
        doc /* A commercial transport sized by a weight budget. */
        attribute maxTakeoffWeight : MassValue;       /* MTOW */
        attribute operatingEmptyWeight : MassValue;   /* OEW (excludes crew & payload here) */
        attribute maxFuelCapacity : MassValue;        /* tank limit */
        attribute maxCargoMass : MassValue;           /* belly structural limit */
        attribute designPassengers : Real = 110.0;
        attribute designCrew : Real = 5.0;
        attribute cruiseAirspeed : SpeedValue;
    }

    abstract part def PowerTransport :> TransportAircraft {
        doc /* Power-producing transport (turboprop, open fan, piston). */
        attribute propulsiveEfficiency : Real;            /* eta_p */
        attribute powerSpecificFuelConsumption : Real;    /* c_p [kg/(W*s)] */
    }
    abstract part def JetTransport :> TransportAircraft {
        doc /* Thrust-producing transport (turbofan). */
        attribute thrustSpecificFuelConsumption : Real;   /* c_T [kg/(N*s)] */
    }
    abstract part def HybridTransport :> TransportAircraft {
        doc /* Series-hybrid: solid-state battery + turbogenerator (jet-assist at takeoff). */
        attribute batterySpecificEnergy : SpecificEnergyValue;  /* solid-state E* */
        attribute installedBatteryMass : MassValue;             /* nominal pack */
        attribute drivetrainEfficiency : Real;
        attribute generatorEfficiency : Real;
        attribute fuelHeatingValue : SpecificEnergyValue = 43000000.0 ['J/kg'];
    }

    part def JetLiner110 :> JetTransport {
        doc /* A220-100-class turbofan. */
        attribute redefines maxTakeoffWeight = 63100.0 [kg];
        attribute redefines operatingEmptyWeight = 35300.0 [kg];
        attribute redefines maxFuelCapacity = 17300.0 [kg];
        attribute redefines maxCargoMass = 6000.0 [kg];
        attribute redefines referenceWingArea = 112.3 ['m²'];
        attribute redefines wingSpan = 35.1 [m];
        attribute redefines aspectRatio = 10.97;
        attribute redefines oswaldEfficiency = 0.82;
        attribute redefines zeroLiftDragCoefficient = 0.019;
        attribute redefines inducedDragFactor = 0.03539;
        attribute redefines maxLiftToDragRatio = 19.284;
        attribute redefines cruiseAirDensity = 0.38 ['kg⋅m⁻³'];
        attribute redefines cruiseAirspeed = 230.0 ['m/s'];
        attribute redefines thrustSpecificFuelConsumption = 1.45e-5;
    }

    part def OpenFan110 :> PowerTransport {
        doc /* CFM RISE-class open-fan (propfan) narrowbody; ~20% better fuel than turbofan. */
        attribute redefines maxTakeoffWeight = 63100.0 [kg];
        attribute redefines operatingEmptyWeight = 35800.0 [kg];
        attribute redefines maxFuelCapacity = 15000.0 [kg];
        attribute redefines maxCargoMass = 6000.0 [kg];
        attribute redefines referenceWingArea = 112.3 ['m²'];
        attribute redefines wingSpan = 35.1 [m];
        attribute redefines aspectRatio = 10.97;
        attribute redefines oswaldEfficiency = 0.82;
        attribute redefines zeroLiftDragCoefficient = 0.019;
        attribute redefines inducedDragFactor = 0.03539;
        attribute redefines maxLiftToDragRatio = 19.284;
        attribute redefines cruiseAirDensity = 0.40 ['kg⋅m⁻³'];
        attribute redefines cruiseAirspeed = 215.0 ['m/s'];
        attribute redefines propulsiveEfficiency = 0.88;
        attribute redefines powerSpecificFuelConsumption = 4.0e-8;
    }

    part def TurboProp110 :> PowerTransport {
        doc /* Notional large regional turboprop (110 seats). Payload-limited, short-haul. */
        attribute redefines maxTakeoffWeight = 36500.0 [kg];
        attribute redefines operatingEmptyWeight = 20000.0 [kg];
        attribute redefines maxFuelCapacity = 5000.0 [kg];
        attribute redefines maxCargoMass = 4000.0 [kg];
        attribute redefines referenceWingArea = 95.0 ['m²'];
        attribute redefines wingSpan = 33.0 [m];
        attribute redefines aspectRatio = 11.463;
        attribute redefines oswaldEfficiency = 0.82;
        attribute redefines zeroLiftDragCoefficient = 0.027;
        attribute redefines inducedDragFactor = 0.03387;
        attribute redefines maxLiftToDragRatio = 16.536;
        attribute redefines cruiseAirDensity = 0.55 ['kg⋅m⁻³'];
        attribute redefines cruiseAirspeed = 160.0 ['m/s'];
        attribute redefines propulsiveEfficiency = 0.85;
        attribute redefines powerSpecificFuelConsumption = 9.0e-8;
    }

    part def PistonLiner110 :> PowerTransport {
        doc /* Historical recip airliner (DC-6/DC-7 era) for contrast -- slow, draggy. */
        attribute redefines maxTakeoffWeight = 48500.0 [kg];
        attribute redefines operatingEmptyWeight = 25000.0 [kg];
        attribute redefines maxFuelCapacity = 12000.0 [kg];
        attribute redefines maxCargoMass = 5000.0 [kg];
        attribute redefines referenceWingArea = 135.9 ['m²'];
        attribute redefines wingSpan = 35.8 [m];
        attribute redefines aspectRatio = 9.430;
        attribute redefines oswaldEfficiency = 0.78;
        attribute redefines zeroLiftDragCoefficient = 0.029;
        attribute redefines inducedDragFactor = 0.04329;
        attribute redefines maxLiftToDragRatio = 14.115;
        attribute redefines cruiseAirDensity = 0.66 ['kg⋅m⁻³'];
        attribute redefines cruiseAirspeed = 145.0 ['m/s'];
        attribute redefines propulsiveEfficiency = 0.82;
        attribute redefines powerSpecificFuelConsumption = 8.5e-8;
    }

    part def HybridElectric110 :> HybridTransport {
        doc /* "Think big" battery: aspirational 850 Wh/kg solid-state pack, plus a small
                turbine that gives jet-assist at takeoff and becomes a cruise generator
                (series hybrid / range extender). */
        attribute redefines maxTakeoffWeight = 72000.0 [kg];
        attribute redefines operatingEmptyWeight = 34000.0 [kg]; /* airframe+motors+gen, excl. battery */
        attribute redefines maxFuelCapacity = 8000.0 [kg];       /* generator fuel */
        attribute redefines maxCargoMass = 5000.0 [kg];
        attribute redefines referenceWingArea = 112.3 ['m²'];
        attribute redefines wingSpan = 35.1 [m];
        attribute redefines aspectRatio = 10.97;
        attribute redefines oswaldEfficiency = 0.82;
        attribute redefines zeroLiftDragCoefficient = 0.020;
        attribute redefines inducedDragFactor = 0.03539;
        attribute redefines maxLiftToDragRatio = 18.796;
        attribute redefines cruiseAirDensity = 0.40 ['kg⋅m⁻³'];
        attribute redefines cruiseAirspeed = 210.0 ['m/s'];
        attribute redefines batterySpecificEnergy = 3060000.0 ['J/kg']; /* 850 Wh/kg */
        attribute redefines installedBatteryMass = 14000.0 [kg];
        attribute redefines drivetrainEfficiency = 0.90;
        attribute redefines generatorEfficiency = 0.40;
    }

    /* ====================================================================
     * 10. TRADE STUDIES  (multiple scenarios per aircraft)
     *     Each scenario fixes the weight budget (pax / cargo / fuel) and the
     *     calc usages compute the resulting idealized still-air range.
     *     ** Ranges are Breguet upper bounds at (L/D)max cruise, NO reserves
     *        or climb -- subtract ~25-40% for a realistic block range. **
     *     Numbers from trade_study_calc.py. Cabin: 110 pax @ 107 kg + 5 crew @ 95 kg.
     * ==================================================================== */

    /* ---- Turbofan JetLiner110 (useful load 27,800 kg) ---- */
    package JetLiner110_TradeStudy {
        private import AircraftRangePerformance::*;
        calc usefulLoad : UsefulLoad {                          /* expect 27,800 kg */
            in maxTakeoffWeight = 63100.0;  in operatingEmptyWeight = 35300.0;
        }
        calc paxPayloadFull : PassengerPayload {                /* expect 11,770 kg */
            in passengerCount = 110.0;  in perPassengerWeight = 107.0;
        }
        calc s1_fullPax_maxFuel : RangeJet {     /* 110 pax, 0 cargo, 15,555 kg fuel -> 8,828 km */
            in airspeed = 230.0;  in gravity = 9.80665;  in thrustSpecificFuelConsumption = 1.45e-5;
            in liftToDrag = 19.284;  in structuralEfficiency = 0.2830;
        }
        calc s2_fullPax_maxCargo : RangeJet {    /* 110 pax, 6,000 kg cargo, 9,555 kg fuel -> 5,122 km */
            in airspeed = 230.0;  in gravity = 9.80665;  in thrustSpecificFuelConsumption = 1.45e-5;
            in liftToDrag = 19.284;  in structuralEfficiency = 0.1642;
        }
        calc s3_maxRange_70pax : RangeJet {       /* 70 pax, 2,535 kg cargo, 17,300 kg fuel -> 9,995 km */
            in airspeed = 230.0;  in gravity = 9.80665;  in thrustSpecificFuelConsumption = 1.45e-5;
            in liftToDrag = 19.284;  in structuralEfficiency = 0.3204;
        }
        calc s4_balanced : RangeJet {             /* 110 pax, 3,000 kg cargo, 12,555 kg fuel -> 6,920 km */
            in airspeed = 230.0;  in gravity = 9.80665;  in thrustSpecificFuelConsumption = 1.45e-5;
            in liftToDrag = 19.284;  in structuralEfficiency = 0.2219;
        }
    }

    /* ---- Open fan OpenFan110 (useful load 27,300 kg) ---- */
    package OpenFan110_TradeStudy {
        private import AircraftRangePerformance::*;
        calc usefulLoad : UsefulLoad {                          /* expect 27,300 kg */
            in maxTakeoffWeight = 63100.0;  in operatingEmptyWeight = 35800.0;
        }
        calc s1_fullPax_maxFuel : RangeCombustionPowerProducing { /* 110 pax, ~0 cargo, 15,000 kg fuel -> 11,743 km */
            in propulsiveEfficiency = 0.88;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 4.0e-8;  in liftToDrag = 19.284;  in structuralEfficiency = 0.2714;
        }
        calc s2_fullPax_maxCargo : RangeCombustionPowerProducing { /* 110 pax, 6,000 kg cargo, 9,055 kg fuel -> 6,701 km */
            in propulsiveEfficiency = 0.88;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 4.0e-8;  in liftToDrag = 19.284;  in structuralEfficiency = 0.1549;
        }
        calc s4_balanced : RangeCombustionPowerProducing {        /* 110 pax, 3,000 kg cargo, 12,055 kg fuel -> 9,172 km */
            in propulsiveEfficiency = 0.88;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 4.0e-8;  in liftToDrag = 19.284;  in structuralEfficiency = 0.2120;
        }
    }

    /* ---- Turboprop TurboProp110 (useful load 16,500 kg; payload-limited) ---- */
    package TurboProp110_TradeStudy {
        private import AircraftRangePerformance::*;
        calc usefulLoad : UsefulLoad {                          /* expect 16,500 kg */
            in maxTakeoffWeight = 36500.0;  in operatingEmptyWeight = 20000.0;
        }
        calc s1_fullPax_maxFuel : RangeCombustionPowerProducing { /* 110 pax, 0 cargo, 4,255 kg fuel -> 1,974 km */
            in propulsiveEfficiency = 0.85;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 9.0e-8;  in liftToDrag = 16.536;  in structuralEfficiency = 0.1239;
        }
        calc s2_fullPax_maxCargo : RangeCombustionPowerProducing { /* 110 pax, 4,000 kg cargo, 255 kg fuel -> 112 km */
            in propulsiveEfficiency = 0.85;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 9.0e-8;  in liftToDrag = 16.536;  in structuralEfficiency = 0.0070;
        }
        calc s3_maxRange_70pax : RangeCombustionPowerProducing {  /* 70 pax, 3,535 kg cargo, 5,000 kg fuel -> 2,346 km */
            in propulsiveEfficiency = 0.85;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 9.0e-8;  in liftToDrag = 16.536;  in structuralEfficiency = 0.1473;
        }
    }

    /* ---- Piston PistonLiner110 (useful load 23,500 kg) ---- */
    package PistonLiner110_TradeStudy {
        private import AircraftRangePerformance::*;
        calc usefulLoad : UsefulLoad {                          /* expect 23,500 kg */
            in maxTakeoffWeight = 48500.0;  in operatingEmptyWeight = 25000.0;
        }
        calc s1_fullPax_maxFuel : RangeCombustionPowerProducing { /* 110 pax, 0 cargo, 11,255 kg fuel -> 3,666 km */
            in propulsiveEfficiency = 0.82;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 8.5e-8;  in liftToDrag = 14.115;  in structuralEfficiency = 0.2640;
        }
        calc s3_maxRange_70pax : RangeCombustionPowerProducing {  /* 70 pax, 3,535 kg cargo, 12,000 kg fuel -> 3,947 km */
            in propulsiveEfficiency = 0.82;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 8.5e-8;  in liftToDrag = 14.115;  in structuralEfficiency = 0.2843;
        }
        calc s4_balanced : RangeCombustionPowerProducing {        /* 110 pax, 2,500 kg cargo, 8,755 kg fuel -> 2,764 km */
            in propulsiveEfficiency = 0.82;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 8.5e-8;  in liftToDrag = 14.115;  in structuralEfficiency = 0.1991;
        }
    }

    /* ---- Hybrid HybridElectric110 (useful load 38,000 kg) ---- */
    package HybridElectric110_TradeStudy {
        private import AircraftRangePerformance::*;
        doc /* fuelHeatingValue = 43e6 J/kg; eta_drive=0.90; eta_gen=0.40; 850 Wh/kg battery.
                batteryEnergy = batteryMass * 3.06e6 J/kg. */
        calc usefulLoad : UsefulLoad {                          /* expect 38,000 kg */
            in maxTakeoffWeight = 72000.0;  in operatingEmptyWeight = 34000.0;
        }
        calc h1_batteryOnly : RangeHybridElectric {  /* 110 pax, 22,000 kg battery, 0 fuel (zero-emission) -> 1,613 km */
            in drivetrainEfficiency = 0.90;  in generatorEfficiency = 0.40;
            in batteryEnergy = 6.7320e10;    /* 22,000 kg * 3.06e6 */
            in fuelMass = 0.0;  in fuelHeatingValue = 43000000.0;
            in liftToDrag = 18.796;  in averageWeight = 7.0608e5;
        }
        calc h2_hybridBalanced : RangeHybridElectric { /* 110 pax, 14,000 kg battery + 8,000 kg fuel -> 4,577 km */
            in drivetrainEfficiency = 0.90;  in generatorEfficiency = 0.40;
            in batteryEnergy = 4.2840e10;    /* 14,000 kg * 3.06e6 */
            in fuelMass = 8000.0;  in fuelHeatingValue = 43000000.0;
            in liftToDrag = 18.796;  in averageWeight = 6.6685e5;
        }
        calc h3_maxRange_70pax : RangeHybridElectric { /* 70 pax, 14,000 kg battery + 8,000 kg fuel, no cargo -> 5,191 km */
            in drivetrainEfficiency = 0.90;  in generatorEfficiency = 0.40;
            in batteryEnergy = 4.2840e10;
            in fuelMass = 8000.0;  in fuelHeatingValue = 43000000.0;
            in liftToDrag = 18.796;  in averageWeight = 5.8806e5;
        }
    }

    /* ====================================================================
     * 11. DESIGN-TARGET REQUIREMENTS  (optional; threshold form)
     * ==================================================================== */
    package Requirements {
        private import ISQ::*;
        private import SI::*;

        requirement minRange {
            doc /* A transport-category design shall achieve at least the target still-air
                    range at the max-range cruise condition. */
            attribute targetRange : LengthValue = 3000.0 [km];
            constraint { targetRange >= 3000.0 [km] }
        }

        requirement minEndurance {
            doc /* A surveillance design shall achieve at least the target endurance at the
                    max-endurance (min-power) condition. */
            attribute targetEndurance : DurationValue = 8.0 [h];
            constraint { targetEndurance >= 8.0 [h] }
        }
    }

    /* ====================================================================
     * 12. VIEWS
     * ==================================================================== */
    package Views {
        private import AircraftRangePerformance::*;

        view parameterDictionaryView {
            doc /* The symbology -> English parameter definitions. */
            expose Aircraft;
            expose PowerProducingAircraft;
            expose ThrustProducingAircraft;
            expose ElectricAircraft;
            expose TransportAircraft;
        }

        view equationsView {
            doc /* All governing calc defs (aero core, characteristic speeds, range/endurance, weight budget). */
            expose AircraftRangePerformance::*;
        }

        view fleetView {
            doc /* The 110-pax / 5-crew transport fleet, one per propulsion type. */
            expose JetLiner110;
            expose OpenFan110;
            expose TurboProp110;
            expose PistonLiner110;
            expose HybridElectric110;
        }

        view tradeStudyView {
            doc /* The payload-range trade-study scenarios across the fleet. */
            expose JetLiner110_TradeStudy;
            expose OpenFan110_TradeStudy;
            expose TurboProp110_TradeStudy;
            expose PistonLiner110_TradeStudy;
            expose HybridElectric110_TradeStudy;
        }
    }
}

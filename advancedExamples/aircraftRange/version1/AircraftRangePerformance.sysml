package AircraftRangePerformance {
    doc /*
        AircraftRangePerformance.sysml -- a reusable SysMLv2 library for aircraft
        RANGE and ENDURANCE analysis, with the aerodynamic parameters translated
        from classical symbology into English attribute names, and the governing
        equations captured as calc def elements so the model is executable, not
        merely descriptive.

        Source of the physics:  https://en.wikipedia.org/wiki/Range_(aeronautics)

        Headline conditions (Wikipedia, propeller case):
          - MAXIMUM ENDURANCE occurs at the point of MINIMUM POWER REQUIRED
            (lowest fuel/energy flow to hold steady level flight).
          - MAXIMUM RANGE occurs where speed / power-required is greatest, i.e.
            at MAXIMUM LIFT-TO-DRAG RATIO (L/D)max.

        The five propulsion types (piston-prop, turboprop, open fan, turbofan/jet,
        electric) collapse onto THREE energy-accounting families:
          FAMILY A  Power-producing combustion (piston-prop, turboprop, open fan)
                    fuel flow ~ SHAFT POWER.  R = (eta_p/(g*c_p))*(L/D)*ln(W_i/W_f)
                    Max endurance @ min power required;  max range @ (L/D)max.
          FAMILY B  Thrust-producing combustion (turbojet / turbofan)
                    fuel flow ~ THRUST.       R = (V/(g*c_T))*(L/D)*ln(W_i/W_f)
                    Max endurance @ (L/D)max; max range @ a higher speed.
          FAMILY C  Electric (battery) -- mass is CONSTANT, so the log DISAPPEARS:
                    R = eta_total*E*(m_batt/m_total)*(L/D)/g  (Hepperle).
                    Max endurance @ min power required;  max range @ (L/D)max.

        ln()/exp() are not in the SysMLv2 standard Kernel Function Library (which
        has sqrt, the power operators, trig, abs, min/max); they are provided in
        the nested MathExtensions package as Groovy textual representations a
        scripting-capable tool can execute. The worked examples also pass
        structuralEfficiency = ln(W_i/W_f) as a precomputed Real so they stay
        evaluable with stdlib functions alone.

        Expected values in the worked examples come from the companion oracles
        reference_calc.py, trade_study_calc.py, and mission_calc.py.
        */

    /* MathExtensions is NESTED so this file has exactly ONE package in the root
       namespace -- CATIA Magic names the namespace after the first root element. */
    package MathExtensions {
        doc /* Math primitives missing from the SysMLv2 standard Kernel Function Library. */
        private import ScalarValues::*;

        calc def ln {
            doc /* Natural logarithm. Not in the SysML v2 standard library, so the
                    implementation is supplied as a Groovy textual representation
                    (below) that a scripting-capable tool can execute. */
            in x : Real[1];
            return result : Real[1];
            rep lnGroovy language "Groovy" /* result = Math.log(x) */
        }

        calc def exp {
            doc /* Exponential e^x. Like ln, not in the SysML v2 standard Kernel
                    Function Library, so the implementation is supplied as a Groovy
                    textual representation a scripting-capable tool can execute. Used
                    by the segmented-mission cruise-fuel calc (inverts Breguet). */
            in x : Real[1];
            return result : Real[1];
            rep expGroovy language "Groovy" /* result = Math.exp(x) */
        }
    }

    /* Standard-library imports: scalar values, ISQ quantity types, SI units,
       real math functions (sqrt, ** , ^), and the local natural log. */
    private import ScalarValues::*;
    private import ISQ::*;
    private import SI::*;
    private import RealFunctions::*;
    private import MathExtensions::*;

    /* ====================================================================
     * 1.  SYMBOLOGY -> ENGLISH  (the parameter dictionary, with ISQ units)
     * ==================================================================== */

    abstract part def Aircraft {
        doc /* Common airframe + aerodynamic parameters shared by every aircraft.
                Each attribute is the English name for one classical symbol. */
        attribute mass : MassValue {
            doc /* m -- all-up aircraft mass */
        }
        attribute referenceWingArea : AreaValue {
            doc /* S -- reference wing area */
        }
        attribute wingSpan : LengthValue {
            doc /* b -- wing span */
        }
        attribute aspectRatio : Real {
            doc /* AR = b^2 / S */
        }
        attribute oswaldEfficiency : Real {
            doc /* e -- span (Oswald) efficiency */
        }
        attribute zeroLiftDragCoefficient : Real {
            doc /* C_D0 -- parasite (zero-lift) drag coefficient */
        }
        attribute inducedDragFactor : Real {
            doc /* K = 1 / (pi * e * AR) -- induced-drag factor */
        }
        attribute cruiseAirDensity : MassDensityValue {
            doc /* rho -- air density at cruise altitude */
        }
        attribute maxLiftToDragRatio : Real {
            doc /* (L/D)max */
        }
        attribute gravity : AccelerationValue = 9.80665 ['m⋅s⁻²'] {
            doc /* g -- standard gravity */
        }
    }

    /* Family A airframe: a shaft-power propulsor that burns fuel. */
    abstract part def PowerProducingAircraft :> Aircraft {
        doc /* Power-producing combustion (prop / turboprop / open fan).
                Fuel flow is proportional to shaft power. */
        attribute propulsiveEfficiency : Real {
            doc /* eta_p -- propeller / fan efficiency */
        }
        attribute powerSpecificFuelConsumption : Real {
            doc /* c_p -- power-specific fuel consumption [kg/(W*s)] */
        }
        attribute usableFuelMass : MassValue {
            doc /* burnable fuel */
        }
    }

    /* Family B airframe: a direct-thrust engine. */
    abstract part def ThrustProducingAircraft :> Aircraft {
        doc /* Thrust-producing combustion (turbojet / turbofan).
                Fuel flow is proportional to thrust. */
        attribute thrustSpecificFuelConsumption : Real {
            doc /* c_T -- thrust-specific fuel consumption [kg/(N*s)] */
        }
        attribute cruiseAirspeed : SpeedValue {
            doc /* V -- transonic cruise speed */
        }
        attribute usableFuelMass : MassValue {
            doc /* burnable fuel */
        }
    }

    /* Family C airframe: battery-electric. */
    abstract part def ElectricAircraft :> Aircraft {
        doc /* Battery-electric powertrain. Battery mass does NOT change in flight. */
        attribute totalEfficiency : Real {
            doc /* eta_total = eta_motor * eta_esc * eta_prop */
        }
        attribute specificEnergy : SpecificEnergyValue {
            doc /* E* -- usable battery specific energy */
        }
        attribute batteryMass : MassValue {
            doc /* m_batt */
        }
    }

    /* ====================================================================
     * 2.  AERODYNAMIC CORE  (calc def = one governing equation each)
     *     Parameters are Real in SI base units (named in each doc) so the
     *     math validates with stdlib functions only.
     * ==================================================================== */

    calc def AspectRatio {
        doc /* AR = b^2 / S.  Inputs: wingSpan b [m], referenceWingArea S [m^2].
                Returns aspect ratio [dimensionless]. */
        in wingSpan : Real;
        in referenceWingArea : Real;
        return aspectRatio : Real = wingSpan ** 2 / referenceWingArea;
    }

    calc def InducedDragFactor {
        doc /* K = 1 / (pi * e * AR) -- couples induced drag to lift^2.
                Inputs: oswaldEfficiency e, aspectRatio AR. Returns K [dimensionless]. */
        in oswaldEfficiency : Real;
        in aspectRatio : Real;
        return inducedDragFactor : Real =
            1.0 / (3.141592653589793 * oswaldEfficiency * aspectRatio);
    }

    calc def LiftCoefficientLevelFlight {
        doc /* Steady level flight (L = W): C_L = 2W / (rho * V^2 * S).
                Inputs SI: weight W [N], airDensity rho [kg/m^3], airspeed V [m/s],
                referenceWingArea S [m^2]. Returns C_L [dimensionless]. */
        in weight : Real;
        in airDensity : Real;
        in airspeed : Real;
        in referenceWingArea : Real;
        return liftCoefficient : Real =
            (2.0 * weight) / (airDensity * airspeed ** 2 * referenceWingArea);
    }

    calc def DragCoefficient {
        doc /* Drag polar: C_D = C_D0 + K * C_L^2.
                Inputs: zeroLiftDragCoefficient C_D0, inducedDragFactor K,
                liftCoefficient C_L. Returns C_D [dimensionless]. */
        in zeroLiftDragCoefficient : Real;
        in inducedDragFactor : Real;
        in liftCoefficient : Real;
        return dragCoefficient : Real =
            zeroLiftDragCoefficient + inducedDragFactor * liftCoefficient ** 2;
    }

    calc def LiftToDragRatio {
        doc /* L/D = C_L / C_D. Inputs: liftCoefficient, dragCoefficient. [dimensionless] */
        in liftCoefficient : Real;
        in dragCoefficient : Real;
        return liftToDrag : Real = liftCoefficient / dragCoefficient;
    }

    calc def MaxLiftToDragRatio {
        doc /* (L/D)max = 1 / (2 * sqrt(C_D0 * K)), where parasite drag = induced drag.
                Inputs: zeroLiftDragCoefficient C_D0, inducedDragFactor K. [dimensionless] */
        in zeroLiftDragCoefficient : Real;
        in inducedDragFactor : Real;
        return maxLiftToDrag : Real =
            1.0 / (2.0 * sqrt(zeroLiftDragCoefficient * inducedDragFactor));
    }

    calc def DragForce {
        doc /* D = 0.5 * rho * V^2 * S * C_D.
                Inputs SI: airDensity rho [kg/m^3], airspeed V [m/s],
                referenceWingArea S [m^2], dragCoefficient C_D. Returns drag [N]. */
        in airDensity : Real;
        in airspeed : Real;
        in referenceWingArea : Real;
        in dragCoefficient : Real;
        return drag : Real =
            0.5 * airDensity * airspeed ** 2 * referenceWingArea * dragCoefficient;
    }

    calc def PowerRequired {
        doc /* P_req(V) = 0.5*rho*V^3*S*C_D0 + 2*K*W^2 / (rho*V*S)  [W].
                Term 1 = PARASITE power (rises as V^3); term 2 = INDUCED power
                (falls as 1/V); their sum has a MINIMUM -> the max-endurance airspeed.
                Inputs SI: airDensity rho, airspeed V, referenceWingArea S,
                zeroLiftDragCoefficient C_D0, inducedDragFactor K, weight W [N]. */
        in airDensity : Real;
        in airspeed : Real;
        in referenceWingArea : Real;
        in zeroLiftDragCoefficient : Real;
        in inducedDragFactor : Real;
        in weight : Real;
        return powerRequired : Real =
            0.5 * airDensity * airspeed ** 3 * referenceWingArea * zeroLiftDragCoefficient
            + (2.0 * inducedDragFactor * weight ** 2) / (airDensity * airspeed * referenceWingArea);
    }

    /* ====================================================================
     * 3.  CHARACTERISTIC AIRSPEEDS
     *     All from V = sqrt( 2W/(rho*S*C_L) ) at the optimum C_L.
     *     Universal ratios (parabolic polar):
     *        V(min power)     / V(L/Dmax) = 3^(-1/4) = 0.7598
     *        V(jet max range) / V(L/Dmax) = 3^(+1/4) = 1.3161
     * ==================================================================== */

    calc def SpeedForMaxRange_PowerProducing {
        doc /* Max-range airspeed for power-producing & electric aircraft = V at (L/D)max.
                C_L = sqrt(C_D0/K).  V = sqrt( (2W/(rho*S)) * sqrt(K/C_D0) ) [m/s].
                Inputs SI: weight W [N], airDensity rho, referenceWingArea S,
                zeroLiftDragCoefficient C_D0, inducedDragFactor K. */
        in weight : Real;
        in airDensity : Real;
        in referenceWingArea : Real;
        in zeroLiftDragCoefficient : Real;
        in inducedDragFactor : Real;
        return airspeed : Real =
            sqrt( (2.0 * weight / (airDensity * referenceWingArea))
                  * sqrt(inducedDragFactor / zeroLiftDragCoefficient) );
    }

    calc def SpeedForMaxEndurance_PowerProducing {
        doc /* Max-endurance airspeed (power-producing & electric) = V at MIN POWER REQUIRED.
                C_L = sqrt(3*C_D0/K).  V = sqrt( (2W/(rho*S)) * sqrt(K/(3*C_D0)) ) [m/s],
                ~ 0.76 * V(L/Dmax). Inputs as SpeedForMaxRange_PowerProducing. */
        in weight : Real;
        in airDensity : Real;
        in referenceWingArea : Real;
        in zeroLiftDragCoefficient : Real;
        in inducedDragFactor : Real;
        return airspeed : Real =
            sqrt( (2.0 * weight / (airDensity * referenceWingArea))
                  * sqrt(inducedDragFactor / (3.0 * zeroLiftDragCoefficient)) );
    }

    calc def SpeedForMaxRange_Jet {
        doc /* Max-range airspeed for a JET = V at max(V*L/D).
                C_L = sqrt(C_D0/(3K)).  V = sqrt( (2W/(rho*S)) * sqrt(3K/C_D0) ) [m/s],
                ~ 1.32 * V(L/Dmax). Inputs as SpeedForMaxRange_PowerProducing. */
        in weight : Real;
        in airDensity : Real;
        in referenceWingArea : Real;
        in zeroLiftDragCoefficient : Real;
        in inducedDragFactor : Real;
        return airspeed : Real =
            sqrt( (2.0 * weight / (airDensity * referenceWingArea))
                  * sqrt(3.0 * inducedDragFactor / zeroLiftDragCoefficient) );
    }

    calc def SpeedForMaxEndurance_Jet {
        doc /* Max-endurance airspeed for a JET = V at (L/D)max (minimum drag).
                Same speed as SpeedForMaxRange_PowerProducing [m/s].
                Inputs as SpeedForMaxRange_PowerProducing. */
        in weight : Real;
        in airDensity : Real;
        in referenceWingArea : Real;
        in zeroLiftDragCoefficient : Real;
        in inducedDragFactor : Real;
        return airspeed : Real =
            sqrt( (2.0 * weight / (airDensity * referenceWingArea))
                  * sqrt(inducedDragFactor / zeroLiftDragCoefficient) );
    }

    /* ====================================================================
     * 4.  STRUCTURAL EFFICIENCY  (the logarithmic Breguet term)
     * ==================================================================== */

    calc def StructuralEfficiency {
        doc /* eta_struc = ln(W_initial / W_final) -- Wikipedia's structural-efficiency
                factor. Uses MathExtensions::ln (Groovy-backed). For stdlib-only
                evaluation, pass the precomputed value to the Range/Endurance calcs.
                Inputs SI: weightInitial W_i [N] (full fuel), weightFinal W_f [N]. */
        in weightInitial : Real;
        in weightFinal : Real;
        return structuralEfficiency : Real = ln(weightInitial / weightFinal);
    }

    /* ====================================================================
     * 5.  RANGE & ENDURANCE  (one pair of calc defs per physics family)
     * ==================================================================== */

    /* Family A: power-producing combustion. */
    calc def RangeCombustionPowerProducing {
        doc /* Breguet range, power-producing propulsion (prop / turboprop / open fan).
                R = (eta_p / (g * c_p)) * (L/D) * ln(W_i/W_f). Evaluate L/D at (L/D)max
                for MAX RANGE. Inputs SI: propulsiveEfficiency eta_p, gravity g [m/s^2],
                powerSpecificFuelConsumption c_p [kg/(W*s)], liftToDrag, structuralEfficiency
                = ln(W_i/W_f). Returns range [m]. */
        in propulsiveEfficiency : Real;
        in gravity : Real;
        in powerSpecificFuelConsumption : Real;
        in liftToDrag : Real;
        in structuralEfficiency : Real;
        return range : Real =
            (propulsiveEfficiency / (gravity * powerSpecificFuelConsumption))
            * liftToDrag * structuralEfficiency;
    }

    calc def EnduranceCombustionPowerProducing {
        doc /* Endurance, power-producing.
                E = (eta_p / (g * c_p)) * (L/D) * (1/V) * ln(W_i/W_f). Evaluate V and L/D
                at the MIN-POWER condition for MAX ENDURANCE (assumes ~constant V & L/D).
                Inputs as RangeCombustionPowerProducing plus airspeed V [m/s] at the
                min-power condition. Returns endurance [s]. */
        in propulsiveEfficiency : Real;
        in gravity : Real;
        in powerSpecificFuelConsumption : Real;
        in liftToDrag : Real;
        in airspeed : Real;
        in structuralEfficiency : Real;
        return endurance : Real =
            (propulsiveEfficiency / (gravity * powerSpecificFuelConsumption))
            * liftToDrag * (1.0 / airspeed) * structuralEfficiency;
    }

    calc def EnduranceProp_Rigorous {
        doc /* Rigorous propeller endurance (Anderson, "Aircraft Performance & Design"),
                integrating as fuel burns at constant C_L & altitude (so V drops):
                  E = (eta_p/(g*c_p)) * (C_L^1.5 / C_D) * sqrt(2*rho*S)
                        * (W_final^-0.5 - W_initial^-0.5).
                This makes explicit WHY max endurance is at max(C_L^1.5 / C_D), i.e.
                minimum power required. Inputs SI: propulsiveEfficiency eta_p, gravity g,
                powerSpecificFuelConsumption c_p [kg/(W*s)], liftCoefficient C_L and
                dragCoefficient C_D at the endurance condition, airDensity rho [kg/m^3],
                referenceWingArea S [m^2], weightInitial W_i [N], weightFinal W_f [N].
                Returns endurance [s]. */
        in propulsiveEfficiency : Real;
        in gravity : Real;
        in powerSpecificFuelConsumption : Real;
        in liftCoefficient : Real;
        in dragCoefficient : Real;
        in airDensity : Real;
        in referenceWingArea : Real;
        in weightInitial : Real;
        in weightFinal : Real;
        return endurance : Real =
            (propulsiveEfficiency / (gravity * powerSpecificFuelConsumption))
            * (liftCoefficient ** 1.5 / dragCoefficient)
            * sqrt(2.0 * airDensity * referenceWingArea)
            * (weightFinal ** (-0.5) - weightInitial ** (-0.5));
    }

    /* Family B: thrust-producing combustion (jet). */
    calc def RangeJet {
        doc /* Breguet range, thrust-producing (turbojet / turbofan).
                R = (V / (g * c_T)) * (L/D) * ln(W_i/W_f). Evaluate V & L/D at the jet
                max-range condition. Inputs SI: airspeed V [m/s], gravity g [m/s^2],
                thrustSpecificFuelConsumption c_T [kg/(N*s)], liftToDrag,
                structuralEfficiency = ln(W_i/W_f). Returns range [m]. */
        in airspeed : Real;
        in gravity : Real;
        in thrustSpecificFuelConsumption : Real;
        in liftToDrag : Real;
        in structuralEfficiency : Real;
        return range : Real =
            (airspeed / (gravity * thrustSpecificFuelConsumption))
            * liftToDrag * structuralEfficiency;
    }

    calc def EnduranceJet {
        doc /* Endurance, jet. E = (1 / (g * c_T)) * (L/D) * ln(W_i/W_f).
                MAX ENDURANCE @ (L/D)max. Inputs SI: gravity g [m/s^2],
                thrustSpecificFuelConsumption c_T [kg/(N*s)], liftToDrag,
                structuralEfficiency = ln(W_i/W_f). Returns endurance [s]. */
        in gravity : Real;
        in thrustSpecificFuelConsumption : Real;
        in liftToDrag : Real;
        in structuralEfficiency : Real;
        return endurance : Real =
            (1.0 / (gravity * thrustSpecificFuelConsumption))
            * liftToDrag * structuralEfficiency;
    }

    /* Family C: electric (no logarithm). */
    calc def RangeElectric {
        doc /* Electric range (Hepperle). Battery mass constant => NO logarithm.
                R = eta_total * E* * (m_batt/m_total) * (L/D) / g. Evaluate L/D at (L/D)max
                for MAX RANGE. Inputs SI: totalEfficiency eta_total, specificEnergy E* [J/kg],
                batteryMassFraction m_batt/m_total, liftToDrag, gravity g [m/s^2].
                Returns range [m]. */
        in totalEfficiency : Real;
        in specificEnergy : Real;
        in batteryMassFraction : Real;
        in liftToDrag : Real;
        in gravity : Real;
        return range : Real =
            totalEfficiency * specificEnergy * batteryMassFraction * liftToDrag / gravity;
    }

    calc def EnduranceElectric {
        doc /* Electric endurance. E = eta_total * (E* * m_batt) / P_required. Evaluate
                P_required at the MIN-POWER condition for MAX ENDURANCE. Inputs SI:
                totalEfficiency eta_total, specificEnergy E* [J/kg], batteryMass m_batt [kg],
                powerRequired P_req [W] at the min-power condition. Returns endurance [s]. */
        in totalEfficiency : Real;
        in specificEnergy : Real;
        in batteryMass : Real;
        in powerRequired : Real;
        return endurance : Real =
            totalEfficiency * specificEnergy * batteryMass / powerRequired;
    }

    /* ====================================================================
     * 6.  CONCRETE LIBRARY AIRCRAFT  (symbology -> English, with real values)
     *     Values are illustrative-but-realistic. See reference_calc.py.
     * ==================================================================== */

    part def Cessna172 :> PowerProducingAircraft {
        doc /* Light piston-propeller trainer. Family A.
                c_p = 8.5e-8 kg/(W*s) ~ 306 g/kWh. */
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
        attribute redefines powerSpecificFuelConsumption = 8.5e-8;
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
        attribute redefines powerSpecificFuelConsumption = 9.0e-8;
        attribute redefines usableFuelMass = 1633.0 [kg];
    }

    part def RiseNarrowbody :> PowerProducingAircraft {
        doc /* Notional CFM RISE open-fan (unducted/propfan) narrowbody. Modeled as
                power-producing with very high propulsive efficiency -- the open-rotor
                selling point. Cruises transonic, so the low-speed optimum-airspeed
                calcs are indicative only (compressibility ignored). */
        attribute redefines mass = 73500.0 [kg];
        attribute redefines referenceWingArea = 122.6 ['m²'];
        attribute redefines wingSpan = 35.8 [m];
        attribute redefines aspectRatio = 10.454;
        attribute redefines oswaldEfficiency = 0.80;
        attribute redefines zeroLiftDragCoefficient = 0.020;
        attribute redefines inducedDragFactor = 0.03806;
        attribute redefines cruiseAirDensity = 0.38 ['kg⋅m⁻³'];
        attribute redefines maxLiftToDragRatio = 18.122;
        attribute redefines propulsiveEfficiency = 0.86;
        attribute redefines powerSpecificFuelConsumption = 4.2e-8;
        attribute redefines usableFuelMass = 16000.0 [kg];
    }

    part def A320neoClass :> ThrustProducingAircraft {
        doc /* Modern single-aisle turbofan. Family B (thrust).
                c_T = 1.5e-5 kg/(N*s) ~ 0.53 lb/lbf/h; cruise ~ M0.78 at 37,000 ft. */
        attribute redefines mass = 73500.0 [kg];
        attribute redefines referenceWingArea = 122.6 ['m²'];
        attribute redefines wingSpan = 35.8 [m];
        attribute redefines aspectRatio = 10.454;
        attribute redefines oswaldEfficiency = 0.80;
        attribute redefines zeroLiftDragCoefficient = 0.020;
        attribute redefines inducedDragFactor = 0.03806;
        attribute redefines cruiseAirDensity = 0.38 ['kg⋅m⁻³'];
        attribute redefines maxLiftToDragRatio = 18.122;
        attribute redefines thrustSpecificFuelConsumption = 1.5e-5;
        attribute redefines cruiseAirspeed = 230.0 ['m/s'];
        attribute redefines usableFuelMass = 16000.0 [kg];
    }

    part def VelisClass :> ElectricAircraft {
        doc /* Light battery-electric trainer (Pipistrel Velis-class). Family C.
                E* = 648000 J/kg = 180 Wh/kg usable. */
        attribute redefines mass = 600.0 [kg];
        attribute redefines referenceWingArea = 9.51 ['m²'];
        attribute redefines wingSpan = 10.71 [m];
        attribute redefines aspectRatio = 12.061;
        attribute redefines oswaldEfficiency = 0.85;
        attribute redefines zeroLiftDragCoefficient = 0.025;
        attribute redefines inducedDragFactor = 0.03105;
        attribute redefines cruiseAirDensity = 1.1 ['kg⋅m⁻³'];
        attribute redefines maxLiftToDragRatio = 17.947;
        attribute redefines totalEfficiency = 0.78;
        attribute redefines specificEnergy = 648000.0 ['J/kg'];
        attribute redefines batteryMass = 80.0 [kg];
    }

    /* ====================================================================
     * 7.  WORKED ANALYSES  (calc usages = the calc elements "calculating")
     *     One full analysis per physics family. Turboprop & open fan reuse
     *     the FAMILY A pattern shown for the Cessna 172. Expected results
     *     (from reference_calc.py) are documented on each calc usage.
     * ==================================================================== */

    /* Family A worked example: Cessna 172 (piston-prop). */
    package Cessna172_Analysis {
        doc /* Inputs: W=10898 N (1111 kg * g), S=16.2 m^2, b=11.0 m, e=0.72,
                C_D0=0.036, rho=1.0 kg/m^3, eta_p=0.80, c_p=8.5e-8 kg/(W*s), fuel=144 kg.
                structuralEfficiency = ln(10898 / (10898 - 144*9.80665)) = ln(1.1489) = 0.1388. */
        private import AircraftRangePerformance::*;

        calc aspectRatio : AspectRatio {
            doc /* expect 7.469 */
            in wingSpan = 11.0;  in referenceWingArea = 16.2;
        }
        calc inducedDragFactor : InducedDragFactor {
            doc /* expect 0.05919 */
            in oswaldEfficiency = 0.72;  in aspectRatio = 7.469;
        }
        calc maxLD : MaxLiftToDragRatio {
            doc /* expect 10.832 */
            in zeroLiftDragCoefficient = 0.036;  in inducedDragFactor = 0.05919;
        }
        calc vMaxRange : SpeedForMaxRange_PowerProducing {
            doc /* expect 41.53 m/s (80.7 kt) */
            in weight = 10898.0;  in airDensity = 1.0;  in referenceWingArea = 16.2;
            in zeroLiftDragCoefficient = 0.036;  in inducedDragFactor = 0.05919;
        }
        calc vMaxEndurance : SpeedForMaxEndurance_PowerProducing {
            doc /* expect 31.56 m/s (61.3 kt) */
            in weight = 10898.0;  in airDensity = 1.0;  in referenceWingArea = 16.2;
            in zeroLiftDragCoefficient = 0.036;  in inducedDragFactor = 0.05919;
        }
        calc maxRange : RangeCombustionPowerProducing {
            doc /* L/D at (L/D)max -> expect 1.443e6 m = 1443 km (779 nm) */
            in propulsiveEfficiency = 0.80;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 8.5e-8;
            in liftToDrag = 10.832;
            in structuralEfficiency = 0.1388;
        }
        calc maxEndurance : EnduranceCombustionPowerProducing {
            doc /* L/D & V at the min-power condition -> expect 3.96e4 s = 11.0 h */
            in propulsiveEfficiency = 0.80;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 8.5e-8;
            in liftToDrag = 9.380;
            in airspeed = 31.56;
            in structuralEfficiency = 0.1388;
        }
    }

    /* Family B worked example: A320neo-class (turbofan). */
    package A320neo_Analysis {
        doc /* Inputs: W=720789 N (73500 kg * g), S=122.6 m^2, b=35.8 m, e=0.80,
                C_D0=0.020, rho=0.38 kg/m^3, c_T=1.5e-5 kg/(N*s), V_cruise=230 m/s,
                fuel=16000 kg. structuralEfficiency = ln(1.2783) = 0.2455. */
        private import AircraftRangePerformance::*;

        calc maxLD : MaxLiftToDragRatio {
            doc /* expect 18.122 */
            in zeroLiftDragCoefficient = 0.020;  in inducedDragFactor = 0.03806;
        }
        calc vMaxRangeJet : SpeedForMaxRange_Jet {
            doc /* expect 271.9 m/s */
            in weight = 720789.0;  in airDensity = 0.38;  in referenceWingArea = 122.6;
            in zeroLiftDragCoefficient = 0.020;  in inducedDragFactor = 0.03806;
        }
        calc maxRange : RangeJet {
            doc /* L/D at jet max-range condition (C_L=sqrt(C_D0/3K)) = 15.694; actual
                    transonic cruise V -> expect 6.024e6 m = 6024 km (3253 nm) */
            in airspeed = 230.0;
            in gravity = 9.80665;
            in thrustSpecificFuelConsumption = 1.5e-5;
            in liftToDrag = 15.694;
            in structuralEfficiency = 0.2455;
        }
        calc maxEndurance : EnduranceJet {
            doc /* L/D at (L/D)max -> expect 3.02e4 s = 8.40 h */
            in gravity = 9.80665;
            in thrustSpecificFuelConsumption = 1.5e-5;
            in liftToDrag = 18.122;
            in structuralEfficiency = 0.2455;
        }
    }

    /* Family C worked example: Velis-class (electric). */
    package Velis_Analysis {
        doc /* Inputs: W=5884 N (600 kg * g), S=9.51 m^2, b=10.71 m, e=0.85, C_D0=0.025,
                rho=1.1 kg/m^3, eta_total=0.78, E*=648000 J/kg, m_batt=80 kg (fraction 0.1333). */
        private import AircraftRangePerformance::*;

        calc maxLD : MaxLiftToDragRatio {
            doc /* expect 17.947 */
            in zeroLiftDragCoefficient = 0.025;  in inducedDragFactor = 0.03105;
        }
        calc vMaxEndurance : SpeedForMaxEndurance_PowerProducing {
            doc /* expect 26.90 m/s */
            in weight = 5884.0;  in airDensity = 1.1;  in referenceWingArea = 9.51;
            in zeroLiftDragCoefficient = 0.025;  in inducedDragFactor = 0.03105;
        }
        calc powerAtMinPower : PowerRequired {
            doc /* expect 10193 W = 10.19 kW */
            in airDensity = 1.1;  in airspeed = 26.90;  in referenceWingArea = 9.51;
            in zeroLiftDragCoefficient = 0.025;  in inducedDragFactor = 0.03105;
            in weight = 5884.0;
        }
        calc maxRange : RangeElectric {
            doc /* expect 1.233e5 m = 123.3 km */
            in totalEfficiency = 0.78;  in specificEnergy = 648000.0;
            in batteryMassFraction = 0.13333;  in liftToDrag = 17.947;  in gravity = 9.80665;
        }
        calc maxEndurance : EnduranceElectric {
            doc /* expect 3973 s = 1.10 h (66 min) */
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
        doc /* Standard per-occupant weights used across the trade studies:
                per-passenger payload = 84 + 23 = 107 kg; 110 pax = 11,770 kg;
                5 crew * 95 = 475 kg. */
        attribute passengerBodyWeight : MassValue = 84.0 [kg] {
            doc /* passenger body, incl. carry-on */
        }
        attribute checkedLuggagePerPassenger : MassValue = 23.0 [kg] {
            doc /* one checked bag per passenger */
        }
        attribute crewMemberWeight : MassValue = 95.0 [kg] {
            doc /* crew member, incl. bag */
        }
    }

    calc def PassengerPayload {
        doc /* paxPayload = passengerCount * (body + luggage). Inputs: passengerCount,
                perPassengerWeight [kg]. Returns payload [kg]. */
        in passengerCount : Real;
        in perPassengerWeight : Real;
        return passengerPayload : Real = passengerCount * perPassengerWeight;
    }

    calc def UsefulLoad {
        doc /* usefulLoad = MTOW - OEW -- the pie shared by crew + pax + cargo + fuel.
                Inputs: maxTakeoffWeight MTOW [kg], operatingEmptyWeight OEW [kg].
                Returns useful load [kg]. */
        in maxTakeoffWeight : Real;
        in operatingEmptyWeight : Real;
        return usefulLoad : Real = maxTakeoffWeight - operatingEmptyWeight;
    }

    calc def TakeoffMass {
        doc /* Sum the weight budget (should be <= MTOW). Inputs [kg]:
                operatingEmptyWeight, crewWeight, passengerPayload, cargoMass, fuelMass.
                Returns takeoff mass [kg]. */
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
                structuralEfficiency = ln(this); supply that ln value to the Range calcs.
                Inputs [kg]: takeoffMass, fuelMass. Returns ratio [dimensionless]. */
        in takeoffMass : Real;
        in fuelMass : Real;
        return massRatio : Real = takeoffMass / (takeoffMass - fuelMass);
    }

    calc def RangeHybridElectric {
        doc /* Series-hybrid range (energy method). Battery mass is CONSTANT (no log term,
                dead weight all flight); the turbogenerator burns fuel into electricity,
                extending range. Fuel burns off -> use the mid-cruise average weight.
                R = eta_drive * (E_battery + eta_gen * fuelMass * fuelHeatingValue)
                      * (L/D) / W_average.
                Inputs SI: drivetrainEfficiency eta_drive (motor*esc*fan), generatorEfficiency
                eta_gen (fuel->electric), batteryEnergy E_battery [J], fuelMass [kg],
                fuelHeatingValue LHV [J/kg], liftToDrag, averageWeight W_avg [N] =
                (takeoffMass - 0.5*fuelMass)*g. Returns range [m]. */
        in drivetrainEfficiency : Real;
        in generatorEfficiency : Real;
        in batteryEnergy : Real;
        in fuelMass : Real;
        in fuelHeatingValue : Real;
        in liftToDrag : Real;
        in averageWeight : Real;
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
        attribute maxTakeoffWeight : MassValue {
            doc /* MTOW -- max takeoff weight */
        }
        attribute operatingEmptyWeight : MassValue {
            doc /* OEW -- operating empty weight (excludes crew & payload here) */
        }
        attribute maxFuelCapacity : MassValue {
            doc /* tank limit */
        }
        attribute maxCargoMass : MassValue {
            doc /* belly structural cargo limit */
        }
        attribute maxPayload : MassValue {
            doc /* max revenue payload = MZFW - OEW - crew */
        }
        attribute maxZeroFuelWeight : MassValue {
            doc /* MZFW -- structural limit on (OEW + crew + payload); fuel must keep the
                    landing weight feasible. Reached at max payload. */
        }
        attribute designPassengers : Real = 110.0 {
            doc /* design passenger count */
        }
        attribute designCrew : Real = 5.0 {
            doc /* design crew count */
        }
        attribute cruiseAirspeed : SpeedValue {
            doc /* V -- cruise speed */
        }
    }

    abstract part def PowerTransport :> TransportAircraft {
        doc /* Power-producing transport (turboprop, open fan, piston). */
        attribute propulsiveEfficiency : Real {
            doc /* eta_p */
        }
        attribute powerSpecificFuelConsumption : Real {
            doc /* c_p [kg/(W*s)] */
        }
    }

    abstract part def JetTransport :> TransportAircraft {
        doc /* Thrust-producing transport (turbofan). */
        attribute thrustSpecificFuelConsumption : Real {
            doc /* c_T [kg/(N*s)] */
        }
    }

    abstract part def HybridTransport :> TransportAircraft {
        doc /* Series-hybrid: solid-state battery + turbogenerator (jet-assist at takeoff). */
        attribute batterySpecificEnergy : SpecificEnergyValue {
            doc /* solid-state E* */
        }
        attribute installedBatteryMass : MassValue {
            doc /* nominal battery pack mass */
        }
        attribute drivetrainEfficiency : Real {
            doc /* eta_drive = motor * esc * fan */
        }
        attribute generatorEfficiency : Real {
            doc /* eta_gen = fuel -> electric */
        }
        attribute fuelHeatingValue : SpecificEnergyValue = 43000000.0 ['J/kg'] {
            doc /* LHV of jet fuel */
        }
    }

    part def JetLiner110 :> JetTransport {
        doc /* A220-100-class turbofan. */
        attribute redefines maxTakeoffWeight = 63100.0 [kg];
        attribute redefines operatingEmptyWeight = 35300.0 [kg];
        attribute redefines maxFuelCapacity = 17300.0 [kg];
        attribute redefines maxCargoMass = 6000.0 [kg];
        attribute redefines maxPayload = 15000.0 [kg];
        attribute redefines maxZeroFuelWeight = 50775.0 [kg];
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
        attribute redefines maxPayload = 15000.0 [kg];
        attribute redefines maxZeroFuelWeight = 51275.0 [kg];
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
        attribute redefines maxPayload = 13000.0 [kg];
        attribute redefines maxZeroFuelWeight = 33475.0 [kg];
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
        attribute redefines maxPayload = 16000.0 [kg];
        attribute redefines maxZeroFuelWeight = 41475.0 [kg];
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
        doc /* "Think big" battery: aspirational 850 Wh/kg solid-state pack
                (batterySpecificEnergy = 3.06e6 J/kg), plus a small turbine that gives
                jet-assist at takeoff and becomes a cruise generator (series hybrid /
                range extender). operatingEmptyWeight excludes the battery and fuel. */
        attribute redefines maxTakeoffWeight = 72000.0 [kg];
        attribute redefines operatingEmptyWeight = 34000.0 [kg];
        attribute redefines maxFuelCapacity = 8000.0 [kg];
        attribute redefines maxCargoMass = 5000.0 [kg];
        attribute redefines maxPayload = 15000.0 [kg];
        attribute redefines maxZeroFuelWeight = 63475.0 [kg];
        attribute redefines referenceWingArea = 112.3 ['m²'];
        attribute redefines wingSpan = 35.1 [m];
        attribute redefines aspectRatio = 10.97;
        attribute redefines oswaldEfficiency = 0.82;
        attribute redefines zeroLiftDragCoefficient = 0.020;
        attribute redefines inducedDragFactor = 0.03539;
        attribute redefines maxLiftToDragRatio = 18.796;
        attribute redefines cruiseAirDensity = 0.40 ['kg⋅m⁻³'];
        attribute redefines cruiseAirspeed = 210.0 ['m/s'];
        attribute redefines batterySpecificEnergy = 3060000.0 ['J/kg'];
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

    /* Turbofan JetLiner110 (useful load 27,800 kg). */
    package JetLiner110_TradeStudy {
        private import AircraftRangePerformance::*;
        calc usefulLoad : UsefulLoad {
            doc /* expect 27,800 kg */
            in maxTakeoffWeight = 63100.0;  in operatingEmptyWeight = 35300.0;
        }
        calc paxPayloadFull : PassengerPayload {
            doc /* expect 11,770 kg */
            in passengerCount = 110.0;  in perPassengerWeight = 107.0;
        }
        calc s1_fullPax_maxFuel : RangeJet {
            doc /* 110 pax, 0 cargo, 15,555 kg fuel -> 8,828 km */
            in airspeed = 230.0;  in gravity = 9.80665;  in thrustSpecificFuelConsumption = 1.45e-5;
            in liftToDrag = 19.284;  in structuralEfficiency = 0.2830;
        }
        calc s2_fullPax_maxCargo : RangeJet {
            doc /* 110 pax, 6,000 kg cargo, 9,555 kg fuel -> 5,122 km */
            in airspeed = 230.0;  in gravity = 9.80665;  in thrustSpecificFuelConsumption = 1.45e-5;
            in liftToDrag = 19.284;  in structuralEfficiency = 0.1642;
        }
        calc s3_maxRange_70pax : RangeJet {
            doc /* 70 pax, 2,535 kg cargo, 17,300 kg fuel -> 9,995 km */
            in airspeed = 230.0;  in gravity = 9.80665;  in thrustSpecificFuelConsumption = 1.45e-5;
            in liftToDrag = 19.284;  in structuralEfficiency = 0.3204;
        }
        calc s4_balanced : RangeJet {
            doc /* 110 pax, 3,000 kg cargo, 12,555 kg fuel -> 6,920 km */
            in airspeed = 230.0;  in gravity = 9.80665;  in thrustSpecificFuelConsumption = 1.45e-5;
            in liftToDrag = 19.284;  in structuralEfficiency = 0.2219;
        }
        /* Payload-range diagram break points (maxPayload 15,000 kg, tank 17,300 kg). */
        calc pointB_maxPayload : RangeJet {
            doc /* payload 15,000 / fuel 12,325 @ MTOW -> 6,778 km (design range) */
            in airspeed = 230.0;  in gravity = 9.80665;  in thrustSpecificFuelConsumption = 1.45e-5;
            in liftToDrag = 19.284;  in structuralEfficiency = 0.2173;
        }
        calc pointC_maxFuel : RangeJet {
            doc /* payload 10,025 / fuel 17,300 @ MTOW -> 9,995 km (knee) */
            in airspeed = 230.0;  in gravity = 9.80665;  in thrustSpecificFuelConsumption = 1.45e-5;
            in liftToDrag = 19.284;  in structuralEfficiency = 0.3204;
        }
        calc pointD_ferry : RangeJet {
            doc /* payload 0 / fuel 17,300 -> 12,304 km (ferry / max range) */
            in airspeed = 230.0;  in gravity = 9.80665;  in thrustSpecificFuelConsumption = 1.45e-5;
            in liftToDrag = 19.284;  in structuralEfficiency = 0.3945;
        }
    }

    /* Open fan OpenFan110 (useful load 27,300 kg). */
    package OpenFan110_TradeStudy {
        private import AircraftRangePerformance::*;
        calc usefulLoad : UsefulLoad {
            doc /* expect 27,300 kg */
            in maxTakeoffWeight = 63100.0;  in operatingEmptyWeight = 35800.0;
        }
        calc s1_fullPax_maxFuel : RangeCombustionPowerProducing {
            doc /* 110 pax, ~0 cargo, 15,000 kg fuel -> 11,743 km */
            in propulsiveEfficiency = 0.88;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 4.0e-8;  in liftToDrag = 19.284;  in structuralEfficiency = 0.2714;
        }
        calc s2_fullPax_maxCargo : RangeCombustionPowerProducing {
            doc /* 110 pax, 6,000 kg cargo, 9,055 kg fuel -> 6,701 km */
            in propulsiveEfficiency = 0.88;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 4.0e-8;  in liftToDrag = 19.284;  in structuralEfficiency = 0.1549;
        }
        calc s4_balanced : RangeCombustionPowerProducing {
            doc /* 110 pax, 3,000 kg cargo, 12,055 kg fuel -> 9,172 km */
            in propulsiveEfficiency = 0.88;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 4.0e-8;  in liftToDrag = 19.284;  in structuralEfficiency = 0.2120;
        }
        /* Payload-range diagram break points (maxPayload 15,000 kg, tank 15,000 kg). */
        calc pointB_maxPayload : RangeCombustionPowerProducing {
            doc /* payload 15,000 / fuel 11,825 @ MTOW -> 8,977 km */
            in propulsiveEfficiency = 0.88;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 4.0e-8;  in liftToDrag = 19.284;  in structuralEfficiency = 0.2075;
        }
        calc pointC_maxFuel : RangeCombustionPowerProducing {
            doc /* payload 11,825 / fuel 15,000 @ MTOW -> 11,743 km */
            in propulsiveEfficiency = 0.88;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 4.0e-8;  in liftToDrag = 19.284;  in structuralEfficiency = 0.2714;
        }
        calc pointD_ferry : RangeCombustionPowerProducing {
            doc /* payload 0 / fuel 15,000 -> 14,971 km (ferry) */
            in propulsiveEfficiency = 0.88;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 4.0e-8;  in liftToDrag = 19.284;  in structuralEfficiency = 0.3461;
        }
    }

    /* Turboprop TurboProp110 (useful load 16,500 kg; payload-limited). */
    package TurboProp110_TradeStudy {
        private import AircraftRangePerformance::*;
        calc usefulLoad : UsefulLoad {
            doc /* expect 16,500 kg */
            in maxTakeoffWeight = 36500.0;  in operatingEmptyWeight = 20000.0;
        }
        calc s1_fullPax_maxFuel : RangeCombustionPowerProducing {
            doc /* 110 pax, 0 cargo, 4,255 kg fuel -> 1,974 km */
            in propulsiveEfficiency = 0.85;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 9.0e-8;  in liftToDrag = 16.536;  in structuralEfficiency = 0.1239;
        }
        calc s2_fullPax_maxCargo : RangeCombustionPowerProducing {
            doc /* 110 pax, 4,000 kg cargo, 255 kg fuel -> 112 km (payload-limited) */
            in propulsiveEfficiency = 0.85;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 9.0e-8;  in liftToDrag = 16.536;  in structuralEfficiency = 0.0070;
        }
        calc s3_maxRange_70pax : RangeCombustionPowerProducing {
            doc /* 70 pax, 3,535 kg cargo, 5,000 kg fuel -> 2,346 km */
            in propulsiveEfficiency = 0.85;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 9.0e-8;  in liftToDrag = 16.536;  in structuralEfficiency = 0.1473;
        }
        /* Payload-range diagram break points (maxPayload 13,000 kg, tank 5,000 kg). */
        calc pointB_maxPayload : RangeCombustionPowerProducing {
            doc /* payload 13,000 / fuel 3,025 @ MTOW -> 1,378 km */
            in propulsiveEfficiency = 0.85;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 9.0e-8;  in liftToDrag = 16.536;  in structuralEfficiency = 0.0865;
        }
        calc pointC_maxFuel : RangeCombustionPowerProducing {
            doc /* payload 11,025 / fuel 5,000 @ MTOW -> 2,346 km */
            in propulsiveEfficiency = 0.85;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 9.0e-8;  in liftToDrag = 16.536;  in structuralEfficiency = 0.1473;
        }
        calc pointD_ferry : RangeCombustionPowerProducing {
            doc /* payload 0 / fuel 5,000 -> 3,479 km (ferry) */
            in propulsiveEfficiency = 0.85;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 9.0e-8;  in liftToDrag = 16.536;  in structuralEfficiency = 0.2185;
        }
    }

    /* Piston PistonLiner110 (useful load 23,500 kg). */
    package PistonLiner110_TradeStudy {
        private import AircraftRangePerformance::*;
        calc usefulLoad : UsefulLoad {
            doc /* expect 23,500 kg */
            in maxTakeoffWeight = 48500.0;  in operatingEmptyWeight = 25000.0;
        }
        calc s1_fullPax_maxFuel : RangeCombustionPowerProducing {
            doc /* 110 pax, 0 cargo, 11,255 kg fuel -> 3,666 km */
            in propulsiveEfficiency = 0.82;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 8.5e-8;  in liftToDrag = 14.115;  in structuralEfficiency = 0.2640;
        }
        calc s3_maxRange_70pax : RangeCombustionPowerProducing {
            doc /* 70 pax, 3,535 kg cargo, 12,000 kg fuel -> 3,947 km */
            in propulsiveEfficiency = 0.82;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 8.5e-8;  in liftToDrag = 14.115;  in structuralEfficiency = 0.2843;
        }
        calc s4_balanced : RangeCombustionPowerProducing {
            doc /* 110 pax, 2,500 kg cargo, 8,755 kg fuel -> 2,764 km */
            in propulsiveEfficiency = 0.82;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 8.5e-8;  in liftToDrag = 14.115;  in structuralEfficiency = 0.1991;
        }
        /* Payload-range diagram break points (maxPayload 16,000 kg, tank 12,000 kg). */
        calc pointB_maxPayload : RangeCombustionPowerProducing {
            doc /* payload 16,000 / fuel 7,025 @ MTOW -> 2,173 km */
            in propulsiveEfficiency = 0.82;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 8.5e-8;  in liftToDrag = 14.115;  in structuralEfficiency = 0.1565;
        }
        calc pointC_maxFuel : RangeCombustionPowerProducing {
            doc /* payload 11,025 / fuel 12,000 @ MTOW -> 3,947 km */
            in propulsiveEfficiency = 0.82;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 8.5e-8;  in liftToDrag = 14.115;  in structuralEfficiency = 0.2843;
        }
        calc pointD_ferry : RangeCombustionPowerProducing {
            doc /* payload 0 / fuel 12,000 -> 5,359 km (ferry) */
            in propulsiveEfficiency = 0.82;  in gravity = 9.80665;
            in powerSpecificFuelConsumption = 8.5e-8;  in liftToDrag = 14.115;  in structuralEfficiency = 0.3860;
        }
    }

    /* Hybrid HybridElectric110 (useful load 38,000 kg). */
    package HybridElectric110_TradeStudy {
        doc /* fuelHeatingValue = 43e6 J/kg; eta_drive=0.90; eta_gen=0.40; 850 Wh/kg battery.
                batteryEnergy = batteryMass * 3.06e6 J/kg. */
        private import AircraftRangePerformance::*;
        calc usefulLoad : UsefulLoad {
            doc /* expect 38,000 kg */
            in maxTakeoffWeight = 72000.0;  in operatingEmptyWeight = 34000.0;
        }
        calc h1_batteryOnly : RangeHybridElectric {
            doc /* 110 pax, 22,000 kg battery (E=6.732e10 J), 0 fuel (zero-emission) -> 1,613 km */
            in drivetrainEfficiency = 0.90;  in generatorEfficiency = 0.40;
            in batteryEnergy = 6.7320e10;
            in fuelMass = 0.0;  in fuelHeatingValue = 43000000.0;
            in liftToDrag = 18.796;  in averageWeight = 7.0608e5;
        }
        calc h2_hybridBalanced : RangeHybridElectric {
            doc /* 110 pax, 14,000 kg battery (E=4.284e10 J) + 8,000 kg fuel -> 4,577 km */
            in drivetrainEfficiency = 0.90;  in generatorEfficiency = 0.40;
            in batteryEnergy = 4.2840e10;
            in fuelMass = 8000.0;  in fuelHeatingValue = 43000000.0;
            in liftToDrag = 18.796;  in averageWeight = 6.6685e5;
        }
        calc h3_maxRange_70pax : RangeHybridElectric {
            doc /* 70 pax, 14,000 kg battery + 8,000 kg fuel, no cargo (fly light) -> 5,191 km */
            in drivetrainEfficiency = 0.90;  in generatorEfficiency = 0.40;
            in batteryEnergy = 4.2840e10;
            in fuelMass = 8000.0;  in fuelHeatingValue = 43000000.0;
            in liftToDrag = 18.796;  in averageWeight = 5.8806e5;
        }
    }

    /* ====================================================================
     * 13. SYSTEM ARCHITECTURE  (the airplane AS a whole system)
     *     A base AirplaneSystem decomposes the airplane into subsystems wired
     *     by ports & connections, sequenced by a flight-phase state machine,
     *     with subsystem masses rolled up to OEW and subsystems satisfying
     *     system requirements. Each propulsion type is a specialization that
     *     (a) inherits its performance parameters from the section-6 analysis
     *     part def and (b) redefines the propulsion & energy subsystems.
     * ==================================================================== */

    /* ---- interface port kinds (flow carried is in the connection name) ---- */
    port def EnergyPort {
        doc /* Fuel or electrical energy flow from storage to propulsion. */
    }
    port def ThrustPort {
        doc /* Propulsive force transmitted to the airframe. */
    }
    port def ElectricalPort {
        doc /* Electrical power on the distribution bus. */
    }
    port def DataPort {
        doc /* Control / data signals. */
    }

    /* ---- system-level requirements satisfied by the architecture ---- */
    package SystemRequirements {
        doc /* Capability requirements the airplane subsystems must satisfy. */
        requirement ProvidePropulsion {
            doc /* The airplane shall produce the thrust required for all flight phases. */
        }
        requirement StoreFlightEnergy {
            doc /* The airplane shall store the energy (fuel and/or battery) for the mission. */
        }
        requirement CarryPayload {
            doc /* The airplane shall carry 110 passengers and 5 crew plus baggage. */
        }
        requirement ControlFlight {
            doc /* The airplane shall provide stability, control, and navigation. */
        }
        requirement DistributeElectrical {
            doc /* The airplane shall generate and distribute electrical power to the systems. */
        }
        requirement ProvideStructure {
            doc /* The airframe shall carry all flight and ground loads. */
        }
    }

    /* ---- subsystem definitions (each carries a mass and its interface ports) ---- */
    part def Airframe {
        doc /* Structure: wing, fuselage, empennage, landing gear. Reacts thrust,
                mounts control surfaces. */
        attribute mass : MassValue;
        part wing;
        part fuselage;
        part empennage;
        part landingGear;
        port thrustMount : ThrustPort;
        port controlSurfaces : DataPort;
    }

    abstract part def PropulsionSystem {
        doc /* Converts stored energy into thrust. Specialized per propulsion type. */
        attribute mass : MassValue;
        port energySupply : EnergyPort;
        port thrust : ThrustPort;
        port throttle : DataPort;
        port generatorOutput : ElectricalPort[0..1];
    }

    abstract part def EnergyStorageSystem {
        doc /* Stores mission energy. Its DRY mass is OEW; the fuel/battery mass it
                holds is tracked by usableFuelMass / installedBatteryMass. */
        attribute mass : MassValue;
        port energyDelivery : EnergyPort;
    }

    part def Avionics {
        doc /* Flight management, navigation, sensing. */
        attribute mass : MassValue;
        port powerSupply : ElectricalPort;
        port dataOut : DataPort;
    }

    part def FlightControlSystem {
        doc /* Control laws and actuation: commands control surfaces and engine throttle. */
        attribute mass : MassValue;
        port powerSupply : ElectricalPort;
        port dataIn : DataPort;
        port surfaceCommands : DataPort;
        port throttleCommands : DataPort;
    }

    part def ElectricalSystem {
        doc /* Generation and distribution bus feeding avionics and flight control. */
        attribute mass : MassValue;
        port powerBus : ElectricalPort;
    }

    part def CabinPayloadSystem {
        doc /* The revenue payload: passengers + crew + cargo (NOT part of OEW). */
        attribute mass : MassValue;
        attribute passengerCount : Real = 110.0;
        attribute crewCount : Real = 5.0;
    }

    /* ---- concrete propulsion subsystems (one per family) ---- */
    part def TurbofanPropulsion :> PropulsionSystem {
        doc /* Two high-bypass turbofans. */
        part engineLeft;
        part engineRight;
    }
    part def OpenFanPropulsion :> PropulsionSystem {
        doc /* Two open-rotor (unducted fan) units: turbine core + counter-rotating fan. */
        part rotorLeft;
        part rotorRight;
    }
    part def TurbopropPropulsion :> PropulsionSystem {
        doc /* Two turboprops: gas-turbine core + reduction gearbox + propeller. */
        part engineLeft;
        part engineRight;
    }
    part def PistonPropulsion :> PropulsionSystem {
        doc /* Four reciprocating engines + propellers (historical airliner). */
        part engine1;
        part engine2;
        part engine3;
        part engine4;
    }
    part def HybridPropulsion :> PropulsionSystem {
        doc /* Series hybrid: electric motors drive ducted fans; a turbogenerator gives
                jet-assist at takeoff and recharges/feeds the bus in cruise. */
        part electricMotorLeft;
        part electricMotorRight;
        part ductedFanLeft;
        part ductedFanRight;
        part powerElectronics;
        part turboGenerator;
        attribute redefines generatorOutput : ElectricalPort;
    }

    /* ---- concrete energy storage subsystems ---- */
    part def FuelTanks :> EnergyStorageSystem {
        doc /* Integral wing (and centre) fuel tanks. */
        part wingTankLeft;
        part wingTankRight;
    }
    part def HybridEnergyStorage :> EnergyStorageSystem {
        doc /* Solid-state battery pack plus a generator fuel tank. */
        part batteryPack;
        part generatorFuelTank;
    }

    /* ---- base airplane system ---- */
    abstract part def AirplaneSystem :> TransportAircraft {
        doc /* The airplane as a whole system: subsystems wired by ports/connections,
                a flight-phase state machine, a mission action sequence, subsystem-mass
                rollup to OEW, and subsystem-to-requirement satisfaction. */

        /* subsystems */
        part airframe : Airframe {
            satisfy SystemRequirements::ProvideStructure;
        }
        part propulsion : PropulsionSystem {
            satisfy SystemRequirements::ProvidePropulsion;
        }
        part energyStorage : EnergyStorageSystem {
            satisfy SystemRequirements::StoreFlightEnergy;
        }
        part avionics : Avionics;
        part flightControl : FlightControlSystem {
            satisfy SystemRequirements::ControlFlight;
        }
        part electrical : ElectricalSystem {
            satisfy SystemRequirements::DistributeElectrical;
        }
        part cabin : CabinPayloadSystem {
            satisfy SystemRequirements::CarryPayload;
        }

        /* connections (the system wiring) */
        connection energyToPropulsion connect energyStorage.energyDelivery to propulsion.energySupply;
        connection propulsionToAirframe connect propulsion.thrust to airframe.thrustMount;
        connection fcsToSurfaces connect flightControl.surfaceCommands to airframe.controlSurfaces;
        connection fcsToThrottle connect flightControl.throttleCommands to propulsion.throttle;
        connection avionicsToFcs connect avionics.dataOut to flightControl.dataIn;
        connection busToAvionics connect electrical.powerBus to avionics.powerSupply;
        connection busToFcs connect electrical.powerBus to flightControl.powerSupply;

        /* behaviour: flight-phase state machine */
        state def FlightPhases {
            doc /* Nominal mission phase progression. */
            entry; then Parked;
            state Parked;
            state Taxi;
            state Takeoff;
            state Climb;
            state Cruise;
            state Descent;
            state Approach;
            state Landing;
            transition first Parked then Taxi;
            transition first Taxi then Takeoff;
            transition first Takeoff then Climb;
            transition first Climb then Cruise;
            transition first Cruise then Descent;
            transition first Descent then Approach;
            transition first Approach then Landing;
            transition first Landing then Parked;
        }
        exhibit state flightPhases : FlightPhases;

        /* behaviour: mission action sequence */
        action def StartUp;
        action def TaxiOut;
        action def TakeoffRoll;
        action def ClimbToCruise;
        action def CruiseLeg;
        action def DescendApproach;
        action def Land;
        action def ShutDown;
        action missionProfile {
            doc /* End-to-end nominal mission thread. */
            action startUp : StartUp;
            action taxiOut : TaxiOut;
            action takeoffRoll : TakeoffRoll;
            action climbToCruise : ClimbToCruise;
            action cruiseLeg : CruiseLeg;
            action descendApproach : DescendApproach;
            action land : Land;
            action shutDown : ShutDown;
            first startUp then taxiOut;
            first taxiOut then takeoffRoll;
            first takeoffRoll then climbToCruise;
            first climbToCruise then cruiseLeg;
            first cruiseLeg then descendApproach;
            first descendApproach then land;
            first land then shutDown;
        }

        /* mass rollup: structural (OEW) subsystems sum to <= OEW */
        attribute structuralMassRollup : MassValue =
            airframe.mass + propulsion.mass + energyStorage.mass
            + avionics.mass + electrical.mass + flightControl.mass;
        constraint oewConsistency {
            doc /* The rolled-up subsystem mass shall not exceed the operating empty weight. */
            structuralMassRollup <= operatingEmptyWeight
        }
    }

    /* ---- the fleet as full systems (params inherited from section 6; subsystems redefined) ---- */
    part def JetLinerSystem :> JetLiner110, AirplaneSystem {
        doc /* Turbofan airliner system. Subsystem masses (illustrative) sum to OEW 35,300 kg. */
        part redefines propulsion : TurbofanPropulsion {
            attribute redefines mass = 7000.0 [kg];
        }
        part redefines energyStorage : FuelTanks {
            attribute redefines mass = 1800.0 [kg];
        }
        part redefines airframe {
            attribute redefines mass = 19500.0 [kg];
        }
        part redefines avionics {
            attribute redefines mass = 1400.0 [kg];
        }
        part redefines electrical {
            attribute redefines mass = 2100.0 [kg];
        }
        part redefines flightControl {
            attribute redefines mass = 3500.0 [kg];
        }
    }

    part def OpenFanSystem :> OpenFan110, AirplaneSystem {
        doc /* Open-fan airliner system. Subsystem masses sum to OEW 35,800 kg. */
        part redefines propulsion : OpenFanPropulsion {
            attribute redefines mass = 8000.0 [kg];
        }
        part redefines energyStorage : FuelTanks {
            attribute redefines mass = 1700.0 [kg];
        }
        part redefines airframe {
            attribute redefines mass = 19100.0 [kg];
        }
        part redefines avionics {
            attribute redefines mass = 1400.0 [kg];
        }
        part redefines electrical {
            attribute redefines mass = 2100.0 [kg];
        }
        part redefines flightControl {
            attribute redefines mass = 3500.0 [kg];
        }
    }

    part def TurboPropSystem :> TurboProp110, AirplaneSystem {
        doc /* Turboprop airliner system. Subsystem masses sum to OEW 20,000 kg. */
        part redefines propulsion : TurbopropPropulsion {
            attribute redefines mass = 4500.0 [kg];
        }
        part redefines energyStorage : FuelTanks {
            attribute redefines mass = 900.0 [kg];
        }
        part redefines airframe {
            attribute redefines mass = 10400.0 [kg];
        }
        part redefines avionics {
            attribute redefines mass = 900.0 [kg];
        }
        part redefines electrical {
            attribute redefines mass = 1300.0 [kg];
        }
        part redefines flightControl {
            attribute redefines mass = 2000.0 [kg];
        }
    }

    part def PistonSystem :> PistonLiner110, AirplaneSystem {
        doc /* Piston airliner system (4 engines). Subsystem masses sum to OEW 25,000 kg. */
        part redefines propulsion : PistonPropulsion {
            attribute redefines mass = 6000.0 [kg];
        }
        part redefines energyStorage : FuelTanks {
            attribute redefines mass = 1200.0 [kg];
        }
        part redefines airframe {
            attribute redefines mass = 14000.0 [kg];
        }
        part redefines avionics {
            attribute redefines mass = 600.0 [kg];
        }
        part redefines electrical {
            attribute redefines mass = 1400.0 [kg];
        }
        part redefines flightControl {
            attribute redefines mass = 1800.0 [kg];
        }
    }

    part def HybridSystem :> HybridElectric110, AirplaneSystem {
        doc /* Series-hybrid airliner system. Subsystem masses sum to OEW 34,000 kg
                (battery cells and fuel are tracked separately, not in OEW). The
                turbogenerator output feeds the electrical bus -- the extra wire below. */
        part redefines propulsion : HybridPropulsion {
            attribute redefines mass = 9000.0 [kg];
        }
        part redefines energyStorage : HybridEnergyStorage {
            attribute redefines mass = 1500.0 [kg];
        }
        part redefines airframe {
            attribute redefines mass = 16500.0 [kg];
        }
        part redefines avionics {
            attribute redefines mass = 1400.0 [kg];
        }
        part redefines electrical {
            attribute redefines mass = 2600.0 [kg];
        }
        part redefines flightControl {
            attribute redefines mass = 3000.0 [kg];
        }
        connection generatorToBus connect propulsion.generatorOutput to electrical.powerBus;
    }

    /* ====================================================================
     * 14. MISSION ANALYSIS & FUEL SIZING  (taxi-takeoff-climb-cruise-descend-
     *     land: from a payload on a defined mission, compute the REQUIRED fuel)
     *     Method: fuel-fraction sizing (Roskam/Raymer) for combustion; energy
     *     balance for the series-hybrid. Cruise & reserve invert the Breguet
     *     range equation (need MathExtensions::exp). Numbers from mission_calc.py.
     *     Standard mission = Dallas DFW -> Los Angeles LAX (~1990 km / 1074 nm),
     *     parameterized so other city pairs / aircraft work.
     * ==================================================================== */

    /* The 6 terminal-phase weight fractions W_end/W_start (cruise & reserve are
       computed per aircraft from the Breguet inversion). Product = 0.94521. */
    part def StandardSegmentFractions {
        doc /* Roskam transport-jet terminal fuel fractions. */
        attribute taxiOut : Real = 0.990 {
            doc /* engine start + taxi-out */
        }
        attribute takeoff : Real = 0.995 {
            doc /* takeoff roll + initial climb */
        }
        attribute climb : Real = 0.980 {
            doc /* climb to cruise altitude */
        }
        attribute descent : Real = 0.990 {
            doc /* descent from cruise */
        }
        attribute approachLand : Real = 0.992 {
            doc /* approach + landing */
        }
        attribute taxiIn : Real = 0.997 {
            doc /* taxi-in + shutdown */
        }
    }

    calc def CruiseWeightFractionJet {
        doc /* Weight fraction W_end/W_start to fly `distance` in jet cruise --
                the inverted Breguet range equation:
                  f = exp( -distance * g * c_T / (V * (L/D)) ).
                Inputs SI: distance [m], gravity g [m/s^2], thrustSpecificFuelConsumption
                c_T [kg/(N*s)], airspeed V [m/s], liftToDrag. Returns [dimensionless].
                Pass the JET max-range L/D (~0.866*(L/D)max), not (L/D)max -- a
                thrust-producing engine maximizes range at the higher speed where
                C_L = sqrt(C_D0/3K); see SpeedForMaxRange_Jet. */
        in distance : Real;
        in gravity : Real;
        in thrustSpecificFuelConsumption : Real;
        in airspeed : Real;
        in liftToDrag : Real;
        return fraction : Real =
            exp(-distance * gravity * thrustSpecificFuelConsumption / (airspeed * liftToDrag));
    }

    calc def CruiseWeightFractionProp {
        doc /* Weight fraction for power-producing cruise (prop / turboprop / open fan):
                  f = exp( -distance * g * c_p / (eta_p * (L/D)) ).
                Inputs SI: distance [m], gravity, powerSpecificFuelConsumption c_p [kg/(W*s)],
                propulsiveEfficiency eta_p, liftToDrag. Returns [dimensionless]. */
        in distance : Real;
        in gravity : Real;
        in powerSpecificFuelConsumption : Real;
        in propulsiveEfficiency : Real;
        in liftToDrag : Real;
        return fraction : Real =
            exp(-distance * gravity * powerSpecificFuelConsumption / (propulsiveEfficiency * liftToDrag));
    }

    calc def MissionFuelFraction {
        doc /* Product of the eight segment weight fractions (6 terminal + cruise + reserve).
                W_land+reserve / W_takeoff. [dimensionless] */
        in fTaxiOut : Real;
        in fTakeoff : Real;
        in fClimb : Real;
        in fCruise : Real;
        in fDescent : Real;
        in fApproach : Real;
        in fTaxiIn : Real;
        in fReserve : Real;
        return fraction : Real =
            fTaxiOut * fTakeoff * fClimb * fCruise * fDescent * fApproach * fTaxiIn * fReserve;
    }

    calc def ZeroFuelMass {
        doc /* ZFW = OEW + crew + passengers + cargo (for the hybrid, OEW already
                includes the installed battery via the analysis). Inputs [kg]. Returns [kg]. */
        in operatingEmptyWeight : Real;
        in crewPayload : Real;
        in passengerPayload : Real;
        in cargoMass : Real;
        return zeroFuelMass : Real =
            operatingEmptyWeight + crewPayload + passengerPayload + cargoMass;
    }

    calc def RequiredBlockFuel {
        doc /* Fuel-fraction sizing inverse: the block+reserve fuel needed to fly the
                mission at a given zero-fuel weight:
                  fuel = zeroFuelMass * (1/missionFuelFraction - 1).  [kg] */
        in zeroFuelMass : Real;
        in missionFuelFraction : Real;
        return blockFuel : Real = zeroFuelMass * (1.0 / missionFuelFraction - 1.0);
    }

    calc def MissionTakeoffMass {
        doc /* Takeoff weight = zero-fuel weight + block fuel. [kg] */
        in zeroFuelMass : Real;
        in blockFuel : Real;
        return takeoffMass : Real = zeroFuelMass + blockFuel;
    }

    calc def HybridMissionFuel {
        doc /* Series-hybrid generator fuel for a mission, from the energy balance
                (battery mass constant, fuel burns off; terminal-power uplift k):
                  k*(Z + 0.5*fuel)*g*R/(L/D) = eta_drive*(E_batt + eta_gen*fuel*LHV)
                solved for fuel, with C = k*g*R/(L/D):
                  fuel = (eta_drive*E_batt - Z*C) / (0.5*C - eta_drive*eta_gen*LHV).
                Inputs SI: zeroFuelMass Z [kg] (incl. battery), batteryEnergy E_batt [J],
                driveEfficiency, generatorEfficiency, fuelHeatingValue LHV [J/kg],
                gravity, totalRange R [m] (mission+reserve), liftToDrag, terminalUplift k.
                Returns generator fuel [kg]. */
        in zeroFuelMass : Real;
        in batteryEnergy : Real;
        in driveEfficiency : Real;
        in generatorEfficiency : Real;
        in fuelHeatingValue : Real;
        in gravity : Real;
        in totalRange : Real;
        in liftToDrag : Real;
        in terminalUplift : Real;
        return fuelMass : Real =
            (driveEfficiency * batteryEnergy
             - zeroFuelMass * (terminalUplift * gravity * totalRange / liftToDrag))
            / (0.5 * (terminalUplift * gravity * totalRange / liftToDrag)
               - driveEfficiency * generatorEfficiency * fuelHeatingValue);
    }

    calc def PureElectricReach {
        doc /* Distance reachable on the battery alone (no generator fuel):
                  reach = eta_drive * E_batt * (L/D) / (Z * g).  [m] */
        in driveEfficiency : Real;
        in batteryEnergy : Real;
        in liftToDrag : Real;
        in zeroFuelMass : Real;
        in gravity : Real;
        return reach : Real =
            driveEfficiency * batteryEnergy * liftToDrag / (zeroFuelMass * gravity);
    }

    constraint def MissionFeasibility {
        doc /* The mission closes iff the required fuel fits the tank and the weights
                stay within structural limits. */
        in requiredFuel : Real;
        in fuelCapacity : Real;
        in takeoffMass : Real;
        in maxTakeoffWeight : Real;
        in zeroFuelMass : Real;
        in maxZeroFuelWeight : Real;
        requiredFuel <= fuelCapacity
            and takeoffMass <= maxTakeoffWeight
            and zeroFuelMass <= maxZeroFuelWeight
    }

    /* ---- parameterized mission (reusable for any city pair / aircraft) ---- */
    part def FlightSegment {
        doc /* One mission phase, with the distance it covers and (for terminal
                phases) its standard weight fraction. */
        attribute segmentName : String;
        attribute groundDistance : LengthValue;
        attribute weightFraction : Real;
    }

    part def Mission {
        doc /* A point-to-point mission as the ordered set of 7 flight phases plus a
                reserve allowance. missionRange is the great-circle distance; cruise
                covers it minus the terminal ground coverage. */
        attribute missionRange : LengthValue;
        attribute reserveRange : LengthValue;
        attribute cruiseDistance : LengthValue;
        part taxiOut : FlightSegment;
        part takeoff : FlightSegment;
        part climb : FlightSegment;
        part cruise : FlightSegment;
        part descent : FlightSegment;
        part approachLand : FlightSegment;
        part taxiIn : FlightSegment;
    }

    part DallasToLosAngeles : Mission {
        doc /* Standard flight DFW -> LAX: 1990 km / 1074 nm great circle, FL370 cruise,
                250 nm reserve. All 7 segments populated; the segment groundDistances
                (climb 220 + cruise 1620 + descent 110 + approachLand 40 = 1990 km) sum
                to missionRange, and the terminal weightFractions match
                StandardSegmentFractions -- the same constants the per-aircraft calc
                usages bind as literals (the cruise fraction is computed per aircraft). */
        attribute redefines missionRange = 1990.0 [km];
        attribute redefines reserveRange = 463.0 [km];
        attribute redefines cruiseDistance = 1620.0 [km];
        part redefines taxiOut {
            attribute redefines segmentName = "taxi-out";
            attribute redefines groundDistance = 0.0 [km];
            attribute redefines weightFraction = 0.990;
        }
        part redefines takeoff {
            attribute redefines segmentName = "takeoff";
            attribute redefines groundDistance = 0.0 [km];
            attribute redefines weightFraction = 0.995;
        }
        part redefines climb {
            attribute redefines segmentName = "climb";
            attribute redefines groundDistance = 220.0 [km];
            attribute redefines weightFraction = 0.980;
        }
        part redefines cruise {
            attribute redefines segmentName = "cruise";
            attribute redefines groundDistance = 1620.0 [km];
        }
        part redefines descent {
            attribute redefines segmentName = "descent";
            attribute redefines groundDistance = 110.0 [km];
            attribute redefines weightFraction = 0.990;
        }
        part redefines approachLand {
            attribute redefines segmentName = "approach-land";
            attribute redefines groundDistance = 40.0 [km];
            attribute redefines weightFraction = 0.992;
        }
        part redefines taxiIn {
            attribute redefines segmentName = "taxi-in";
            attribute redefines groundDistance = 0.0 [km];
            attribute redefines weightFraction = 0.997;
        }
    }

    /* ---- per-aircraft DFW->LAX sizing (calc usages = the calculation) ----
       Cabin: 110 pax (11,770 kg) + 5 crew (475 kg) + 2,000 kg nominal cargo. */

    package JetLiner110_DFWLAX {
        doc /* Turbofan on DFW->LAX. FEASIBLE: fuel 7,074 kg << tank 17,300 kg;
                TOW 56,619 << MTOW 63,100; ZFW 49,545 <= MZFW 50,775.
                Cruise L/D = 16.701 = (sqrt(3)/2)*(L/D)max -- a turbofan's max-range
                condition is at the HIGHER speed where L/D ~ 0.866*(L/D)max (see
                SpeedForMaxRange_Jet), NOT at (L/D)max (which the power-producing
                families use). Using (L/D)max here would understate jet fuel ~9%. */
        private import AircraftRangePerformance::*;
        calc cruiseFraction : CruiseWeightFractionJet {
            doc /* cruise 1,620 km at jet max-range L/D 16.701 -> expect 0.94179 */
            in distance = 1620000.0;  in gravity = 9.80665;  in thrustSpecificFuelConsumption = 1.45e-5;
            in airspeed = 230.0;  in liftToDrag = 16.701;
        }
        calc reserveFraction : CruiseWeightFractionJet {
            doc /* reserve 463 km -> expect 0.98301 */
            in distance = 463000.0;  in gravity = 9.80665;  in thrustSpecificFuelConsumption = 1.45e-5;
            in airspeed = 230.0;  in liftToDrag = 16.701;
        }
        calc missionFraction : MissionFuelFraction {
            doc /* expect 0.87506 */
            in fTaxiOut = 0.990;  in fTakeoff = 0.995;  in fClimb = 0.980;  in fCruise = 0.94179;
            in fDescent = 0.990;  in fApproach = 0.992;  in fTaxiIn = 0.997;  in fReserve = 0.98301;
        }
        calc zeroFuel : ZeroFuelMass {
            doc /* expect 49,545 kg */
            in operatingEmptyWeight = 35300.0;  in crewPayload = 475.0;
            in passengerPayload = 11770.0;  in cargoMass = 2000.0;
        }
        calc blockFuel : RequiredBlockFuel {
            doc /* expect 7,074 kg */
            in zeroFuelMass = 49545.0;  in missionFuelFraction = 0.87506;
        }
        calc takeoffMass : MissionTakeoffMass {
            doc /* expect 56,619 kg */
            in zeroFuelMass = 49545.0;  in blockFuel = 7074.0;
        }
        assert constraint feasible : MissionFeasibility {
            in requiredFuel = 7074.0;  in fuelCapacity = 17300.0;
            in takeoffMass = 56619.0;  in maxTakeoffWeight = 63100.0;
            in zeroFuelMass = 49545.0;  in maxZeroFuelWeight = 50775.0;
        }
    }

    package OpenFan110_DFWLAX {
        doc /* Open fan on DFW->LAX. FEASIBLE and most efficient: fuel 5,513 kg
                (~22% below the turbofan); TOW 55,558 << MTOW 63,100. As a
                power-producing propulsor it cruises at (L/D)max -- one reason it
                beats the thrust-producing turbofan, which cruises at ~0.87*(L/D)max. */
        private import AircraftRangePerformance::*;
        calc cruiseFraction : CruiseWeightFractionProp {
            doc /* cruise 1,620 km -> expect 0.96325 */
            in distance = 1620000.0;  in gravity = 9.80665;  in powerSpecificFuelConsumption = 4.0e-8;
            in propulsiveEfficiency = 0.88;  in liftToDrag = 19.284;
        }
        calc blockFuel : RequiredBlockFuel {
            doc /* f_total 0.90077 -> expect 5,513 kg */
            in zeroFuelMass = 50045.0;  in missionFuelFraction = 0.90077;
        }
        assert constraint feasible : MissionFeasibility {
            in requiredFuel = 5513.0;  in fuelCapacity = 15000.0;
            in takeoffMass = 55558.0;  in maxTakeoffWeight = 63100.0;
            in zeroFuelMass = 50045.0;  in maxZeroFuelWeight = 51275.0;
        }
    }

    package TurboProp110_DFWLAX {
        doc /* Turboprop on DFW->LAX: INFEASIBLE at full pax. Required fuel 7,048 kg
                exceeds the 5,000 kg tank (and at 2 t cargo TOW 41,293 > MTOW 36,500,
                ZFW 34,245 > MZFW 33,475). ENGINEERING DECISION: a 110-seat turboprop
                is a regional aircraft -- DFW->LAX needs reduced pax/cargo or a fuel
                stop. (No feasibility assertion: it does not close.) */
        private import AircraftRangePerformance::*;
        calc cruiseFraction : CruiseWeightFractionProp {
            doc /* cruise 1,620 km -> expect 0.90328 */
            in distance = 1620000.0;  in gravity = 9.80665;  in powerSpecificFuelConsumption = 9.0e-8;
            in propulsiveEfficiency = 0.85;  in liftToDrag = 16.536;
        }
        calc blockFuel : RequiredBlockFuel {
            doc /* f_total 0.82932 -> expect 7,048 kg (> 5,000 kg tank => INFEASIBLE) */
            in zeroFuelMass = 34245.0;  in missionFuelFraction = 0.82932;
        }
    }

    package PistonLiner110_DFWLAX {
        doc /* Piston airliner on DFW->LAX: FEASIBLE but tight -- fuel 8,995 kg <= tank
                12,000; TOW 48,240 only 260 kg under MTOW 48,500. Slow (~3.8 h cruise). */
        private import AircraftRangePerformance::*;
        calc cruiseFraction : CruiseWeightFractionProp {
            doc /* cruise 1,620 km -> expect 0.88988 */
            in distance = 1620000.0;  in gravity = 9.80665;  in powerSpecificFuelConsumption = 8.5e-8;
            in propulsiveEfficiency = 0.82;  in liftToDrag = 14.115;
        }
        calc blockFuel : RequiredBlockFuel {
            doc /* f_total 0.81353 -> expect 8,995 kg */
            in zeroFuelMass = 39245.0;  in missionFuelFraction = 0.81353;
        }
        assert constraint feasible : MissionFeasibility {
            in requiredFuel = 8995.0;  in fuelCapacity = 12000.0;
            in takeoffMass = 48240.0;  in maxTakeoffWeight = 48500.0;
            in zeroFuelMass = 39245.0;  in maxZeroFuelWeight = 41475.0;
        }
    }

    package HybridElectric110_DFWLAX {
        doc /* Series-hybrid on DFW->LAX. FEASIBLE: 14,000 kg battery + 3,045 kg generator
                fuel; TOW 65,290 << MTOW 72,000. Energy ~45% battery / 55% fuel.
                Pure-electric reach is only 1,187 km of the 1,990 km route (~60%) ->
                a battery-only 110-seat DFW->LAX is INFEASIBLE; the turbogenerator is
                what closes the mission. ENGINEERING DECISION: size battery vs generator. */
        private import AircraftRangePerformance::*;
        calc generatorFuel : HybridMissionFuel {
            doc /* expect 3,045 kg (battery 14,000 kg, E_batt 4.284e10 J, R 2,453 km) */
            in zeroFuelMass = 62245.0;  in batteryEnergy = 4.2840e10;
            in driveEfficiency = 0.90;  in generatorEfficiency = 0.40;
            in fuelHeatingValue = 43000000.0;  in gravity = 9.80665;
            in totalRange = 2453000.0;  in liftToDrag = 18.796;  in terminalUplift = 1.05;
        }
        calc batteryReach : PureElectricReach {
            doc /* expect 1.187e6 m = 1,187 km (< 1,990 km mission) */
            in driveEfficiency = 0.90;  in batteryEnergy = 4.2840e10;
            in liftToDrag = 18.796;  in zeroFuelMass = 62245.0;  in gravity = 9.80665;
        }
        assert constraint feasible : MissionFeasibility {
            in requiredFuel = 3045.0;  in fuelCapacity = 8000.0;
            in takeoffMass = 65290.0;  in maxTakeoffWeight = 72000.0;
            in zeroFuelMass = 62245.0;  in maxZeroFuelWeight = 63475.0;
        }
    }

    /* ---- mission trade study: which configuration for DFW->LAX, and why ---- */
    package MissionTradeStudy {
        doc /* DFW->LAX (110 pax + 5 crew + 2,000 kg cargo) -- block fuel / feasibility:
                  JetLiner110     7,074 kg   FEASIBLE (10,226 kg fuel margin)
                  OpenFan110      5,513 kg   FEASIBLE, lowest burn (-22% vs jet)
                  TurboProp110    7,048 kg   INFEASIBLE (tank 5,000 kg) -> regional only
                  PistonLiner110  8,995 kg   FEASIBLE but tight (260 kg under MTOW), slow
                  HybridElectric  3,045 kg + 14 t battery  FEASIBLE (~45% electric)
                (Jet cruises at its max-range L/D ~0.87*(L/D)max; the power-producing
                 families cruise at (L/D)max -- part of why the open fan wins.)

                MAX CARGO that still closes DFW->LAX (full pax): Jet & OpenFan & Hybrid
                ~3,231 kg (MZFW-limited); Piston ~2,212 kg (MTOW-limited); TurboProp none.

                STUDY (i) payload vs achievable range (full tank): e.g. JetLiner110
                0 cargo 6,031 km / +max cargo 4,256 km; OpenFan 9,224 / 6,447 km;
                TurboProp 984 / 388 km (confirms regional); Hybrid 4,151 / 3,930 km.

                STUDY (ii) hybrid battery-mass sweep (DFW->LAX): 8 t batt -> 3,616 kg
                fuel; 14 t -> 3,045 kg; 18 t -> INFEASIBLE (ZFW>MZFW); 22 t -> TOW>MTOW.
                DECISION: ~14 t battery is the sweet spot -- larger batteries hit the
                weight limits before the fuel saving pays off.

                RESERVE: modeled as an equivalent 463 km (250 nm) cruise leg standing in
                for alternate + 45-min hold; a true hold is flown slower at holding
                fuel-flow, so this is a conservative sizing reserve (adjust per operator).

                ENGINEERING DECISIONS exposed:
                  - Open fan vs turbofan: same mission, ~22% less fuel -> fleet fuel cost.
                  - Turboprop: cannot serve DFW->LAX nonstop at full pax -> route network.
                  - Piston: feasible only with no reserve margin -> not certifiable today.
                  - Hybrid: needs the generator (battery-only infeasible); battery sized
                    by MZFW/MTOW, not by range. */
        private import AircraftRangePerformance::*;

        calc jetMaxCargoZfw : ZeroFuelMass {
            doc /* max-cargo ZFW at the MZFW limit -> 50,775 kg (cargo 3,231 kg) */
            in operatingEmptyWeight = 35300.0;  in crewPayload = 475.0;
            in passengerPayload = 11770.0;  in cargoMass = 3231.0;
        }
        calc hybridBatterySweep_18t : HybridMissionFuel {
            doc /* 18 t battery -> 2,664 kg fuel but ZFW 66,245 > MZFW 63,475 => INFEASIBLE */
            in zeroFuelMass = 66245.0;  in batteryEnergy = 5.5080e10;
            in driveEfficiency = 0.90;  in generatorEfficiency = 0.40;
            in fuelHeatingValue = 43000000.0;  in gravity = 9.80665;
            in totalRange = 2453000.0;  in liftToDrag = 18.796;  in terminalUplift = 1.05;
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
    /* Views are typed by DS_Views rendering viewpoints so they render as ACTUAL
       diagrams when loaded into CATIA Magic / Cameo (a textual `view` with no
       rendering viewpoint is not a diagram):
         gv = definition (BDD-like)   iv = internal (IBD-like: ports & connections)
         stv = state machine          afv = action flow
         rt = requirement table       gt = generic table
       DS_Views is Cameo's built-in library; the standalone validator resolves it
       from its bundled stub via -Dds.views.library.path. */
    package Views {
        private import AircraftRangePerformance::*;

        view parameterDictionaryView : DS_Views::SymbolicViews::gv {
            doc /* Symbology -> English parameter definitions (definition diagram). */
            expose Aircraft;
            expose PowerProducingAircraft;
            expose ThrustProducingAircraft;
            expose ElectricAircraft;
            expose TransportAircraft;
            filter not KerML::Root::Element::isLibraryElement;
        }

        view fleetDefinitionView : DS_Views::SymbolicViews::gv {
            doc /* The 110-pax / 5-crew fleet, one per propulsion type (BDD-style). */
            expose JetLiner110;
            expose OpenFan110;
            expose TurboProp110;
            expose PistonLiner110;
            expose HybridElectric110;
            filter not KerML::Root::Element::isLibraryElement;
        }

        view equationsView : DS_Views::SymbolicViews::gv {
            doc /* All governing calc defs (aero core, speeds, range/endurance, mission). */
            expose AircraftRangePerformance::*;
            filter not KerML::Root::Element::isLibraryElement;
        }

        view systemDecompositionView : DS_Views::SymbolicViews::gv {
            doc /* Airplane system subsystem decomposition (definition diagram). */
            expose AirplaneSystem;
            expose Airframe;
            expose PropulsionSystem;
            expose EnergyStorageSystem;
            expose Avionics;
            expose FlightControlSystem;
            expose ElectricalSystem;
            expose CabinPayloadSystem;
            filter not KerML::Root::Element::isLibraryElement;
        }

        view systemInternalView : DS_Views::SymbolicViews::iv {
            doc /* Airplane system internal structure: subsystems wired by ports and
                    connections (IBD-style). */
            expose AirplaneSystem;
            filter not KerML::Root::Element::isLibraryElement;
        }

        view hybridInternalView : DS_Views::SymbolicViews::iv {
            doc /* Hybrid system internals, including the turbogenerator -> electrical
                    bus connection. */
            expose HybridSystem;
            filter not KerML::Root::Element::isLibraryElement;
        }

        view flightPhasesStateView : DS_Views::SymbolicViews::stv {
            doc /* Flight-phase state machine: parked -> taxi -> takeoff -> climb ->
                    cruise -> descent -> approach -> landing. */
            expose AirplaneSystem;
            filter not KerML::Root::Element::isLibraryElement;
        }

        view missionActionView : DS_Views::SymbolicViews::afv {
            doc /* Mission action sequence: startUp -> taxiOut -> ... -> shutDown. */
            expose AirplaneSystem;
            filter not KerML::Root::Element::isLibraryElement;
        }

        view requirementsTableView : DS_Views::TabularViews::rt {
            doc /* Requirement table: design targets + system capability requirements. */
            expose Requirements;
            expose SystemRequirements;
            filter not KerML::Root::Element::isLibraryElement;
        }

        view tradeStudyTableView : DS_Views::TabularViews::gt {
            doc /* Payload-range + mission trade-study scenarios as a table. */
            expose JetLiner110_TradeStudy;
            expose OpenFan110_TradeStudy;
            expose TurboProp110_TradeStudy;
            expose PistonLiner110_TradeStudy;
            expose HybridElectric110_TradeStudy;
            expose MissionTradeStudy;
            filter not KerML::Root::Element::isLibraryElement;
        }
    }
}

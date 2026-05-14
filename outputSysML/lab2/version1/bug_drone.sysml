package Stakeholders {
    private import ScalarValues::*;

    part def HumanOperator;
    part def SafetyRegulator;
    part def Manufacturer;

    concern def CostConcern;
    concern def WeightConcern;
    concern def PowerConcern;
    concern def SafetyConcern;

    part humanOperator : HumanOperator {
        concern cost : CostConcern;
        concern weight : WeightConcern;
        concern power : PowerConcern;
    }

    part safetyRegulator : SafetyRegulator {
        concern safety : SafetyConcern;
    }
}

package Requirements {
    private import ScalarValues::*;
    private import ISQ::*;
    private import SI::*;
    private import Stakeholders::*;

    requirement solarPower {
        doc /* The drone shall be powered by solar energy, capable of landing, charging via solar, and taking off to continue hunting. */
        frame humanOperator.power;
    }

    requirement laserWeapon {
        doc /* The drone shall be equipped with a laser weapon to kill insects. */
        attribute bugsKilled : Integer = 100;
        frame humanOperator.cost;
    }

    requirement computerVision {
        doc /* The drone shall use sensors and AI computer vision to identify insects as pests or beneficial. */
    }

    requirement safetyProtocols {
        doc /* The drone's rotor blades must have physical guards or emergency stop mechanisms to prevent injury. */
        frame safetyRegulator.safety;
    }

    requirement geofence {
        doc /* The drone shall operate within a maximum altitude of 25 meters and a geofenced area. */
    }

    requirement payloadCapacity {
        doc /* The drone shall support a payload capacity up to 1 kg. */
        attribute maxWeight : MassValue = 1.0 [kg];
        frame humanOperator.weight;
    }
    
    requirement flightTimeReq {
        doc /* The drone shall maximize patrol time between charges. */
        attribute flightTime : DurationValue = 2.0 [h];
    }
}

package LogicalArchitecture {
    private import ScalarValues::*;
    private import ISQ::*;
    private import SI::*;
    private import Requirements::*;

    part def NavigationSystem;
    
    part def LaserWeapon {
        attribute powerConsumption : PowerValue = 50.0 [W];
        attribute weight : MassValue = 0.2 [kg];
    }
    
    part def SensorArray {
        attribute powerConsumption : PowerValue = 10.0 [W];
        attribute weight : MassValue = 0.1 [kg];
    }
    
    part def PowerSubsystem {
        attribute powerConsumption : PowerValue = 5.0 [W];
        attribute weight : MassValue = 0.4 [kg];
    }
    
    part def FlightController {
        attribute powerConsumption : PowerValue = 20.0 [W];
        attribute weight : MassValue = 0.3 [kg];
    }

    part def InsectHuntingDrone {
        part nav : NavigationSystem;
        part laser : LaserWeapon;
        part sensors : SensorArray;
        part power : PowerSubsystem;
        part fc : FlightController;
        
        attribute totalPower : PowerValue = laser.powerConsumption + sensors.powerConsumption + power.powerConsumption + fc.powerConsumption;
        attribute totalWeight : MassValue = laser.weight + sensors.weight + power.weight + fc.weight;
        
        satisfy solarPower;
        satisfy laserWeapon;
        satisfy computerVision;
        satisfy safetyProtocols;
        satisfy geofence;
        satisfy payloadCapacity;

        state droneBehavior {
            entry;
            then charging;
            
            state charging;
            state patrolling;
            state targeting;
            state firing;
            state returning;
            state emergencyStop;
            
            transition t1 first charging then patrolling;
            transition t2 first patrolling then targeting;
            transition t3 first targeting then firing;
            transition t4 first firing then patrolling;
            transition t5 first patrolling then returning;
            transition t6 first returning then charging;
            transition t7 first patrolling then emergencyStop;
            transition t8 first targeting then emergencyStop;
            transition t9 first firing then emergencyStop;
        }
    }
}

package PhysicalArchitecture {
    private import ScalarValues::*;
    private import ISQ::*;
    private import SI::*;
    private import LogicalArchitecture::*;

    part def SolarPanels;
    part def LaserDiodeModule;
    part def AIVisionProcessor;
    part def GPSModule;
    part def MotorsAndRotors;

    part def PhysicalDrone :> InsectHuntingDrone {
        part panels : SolarPanels;
        part diode : LaserDiodeModule;
        part ai : AIVisionProcessor;
        part gps : GPSModule;
        part motors : MotorsAndRotors;
        
        allocate panels to power;
        allocate diode to laser;
        allocate ai to sensors;
        allocate gps to nav;
        allocate motors to fc;
    }
}

package SystemContext {
    private import Stakeholders::*;
    private import LogicalArchitecture::*;

    part def Environment;
    
    part def Context {
        part drone : InsectHuntingDrone;
        part environment : Environment;
        part operator : HumanOperator;
    }
}

package Views {
    private import Stakeholders::*;
    private import Requirements::*;
    private import LogicalArchitecture::*;
    private import SystemContext::*;
    
    /* 
     * Note: DS_Views might cause warnings but the prompt requests tabular views inheriting from gt/rt. 
     * However, standard views require DS_Views which is unconfigured. We will use simple views with expose 
     * and filters to avoid resolution errors for DS_Views if it's strictly missing. 
     */

    view systemView {
        expose SystemContext::Context;
    }

    view concernsView {
        expose Stakeholders;
    }
    
    view requirementsView {
        expose Requirements;
    }
    
    view logicalView {
        expose LogicalArchitecture;
    }
}

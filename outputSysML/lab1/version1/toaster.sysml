package Stakeholders {
    part def EndUser;
    part def SafetyRegulator;
    part def Manufacturer;

    concern def SafetyConcern;
    concern def PerformanceConcern;
    concern def UsabilityConcern;

    part endUser : EndUser {
        concern usability : UsabilityConcern;
        concern performance : PerformanceConcern;
    }

    part safetyRegulator : SafetyRegulator {
        concern safety : SafetyConcern;
    }
}

package Requirements {
    private import Stakeholders::*;

    requirement accommodateSlices {
        doc /* The toaster shall accommodate two standard slices of bread simultaneously. */
        frame endUser.usability;
    }

    requirement adjustableHeating {
        doc /* The toaster shall provide a user-adjustable heating level. */
        frame endUser.usability;
    }

    requirement autoEject {
        doc /* The toaster shall automatically eject the bread when the toasting cycle completes. */
        frame endUser.usability;
    }

    requirement manualCancel {
        doc /* The toaster shall allow the user to manually cancel the toasting cycle at any time. */
        frame endUser.usability;
    }

    requirement safeTemp {
        doc /* The exterior surface temperature shall not exceed 60 degrees Celsius during operation. */
        frame safetyRegulator.safety;
    }

    requirement powerReq {
        doc /* The toaster shall operate on standard 120V AC household power. */
        frame endUser.performance;
    }

    requirement safetyStandard {
        doc /* The toaster must comply with UL safety standards for household appliances. */
        frame safetyRegulator.safety;
    }
}

package LogicalArchitecture {
    private import Requirements::*;

    part def PowerSubsystem;
    part def UserInterfaceSubsystem;
    part def HeatingSubsystem;
    part def ControlSubsystem;
    part def EjectionSubsystem;

    part def Toaster {
        part power : PowerSubsystem {
            satisfy powerReq;
        }
        part ui : UserInterfaceSubsystem {
            satisfy adjustableHeating;
            satisfy manualCancel;
        }
        part heating : HeatingSubsystem {
            satisfy safeTemp;
        }
        part control : ControlSubsystem {
            satisfy autoEject;
            satisfy safetyStandard;
        }
        part ejection : EjectionSubsystem {
            satisfy accommodateSlices;
        }
    }
}

package PhysicalArchitecture {
    private import LogicalArchitecture::*;

    part def NichromeWireHeaters;
    part def ElectronicTimerCircuit;
    part def SpringLoadedCarriageLever;
    part def Casing;
    part def PowerCord;

    part def PhysicalToaster :> Toaster {
        part physicalHeaters : NichromeWireHeaters;
        part electronicTimer : ElectronicTimerCircuit;
        part springCarriage : SpringLoadedCarriageLever;
        part outerCasing : Casing;
        part cord : PowerCord;
        
        allocate physicalHeaters to heating;
        allocate electronicTimer to control;
        allocate springCarriage to ejection;
        allocate outerCasing to ui;
        allocate cord to power;
    }
}

package SystemContext {
    private import Stakeholders::*;
    private import LogicalArchitecture::*;

    part def ToasterContext;
    part context : ToasterContext {
        part toaster : Toaster;
        part user : EndUser;
    }
}

package Views {
    private import SystemContext::*;
    private import LogicalArchitecture::*;
    
    view systemView {
        expose context;
    }
}

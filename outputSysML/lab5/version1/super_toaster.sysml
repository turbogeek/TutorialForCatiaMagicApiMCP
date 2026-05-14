package SuperToaster_SoS {
    private import ScalarValues::*;
    private import ISQ::*;
    private import SI::*;

    /* --- CONCEPT REQUIREMENTS --- */
    package ConceptRequirements {
        requirement smartToasting {
            doc /* The toaster must connect to user devices to enable smart toasting profiles. */
        }
        requirement safeOperations {
            doc /* The toaster must ensure absolute safety, including emergency shutoff and reporting. */
        }
        requirement premiumQuality {
            doc /* The toaster must deliver a premium experience with high reliability and low weight. */
        }
    }

    /* --- LOGICAL REQUIREMENTS --- */
    package LogicalRequirements {
        private import ConceptRequirements::*;
        private import ScalarValues::*;
        private import ISQ::*;
        private import SI::*;

        requirement networkConn {
            doc /* The system shall have IoT connectivity via Wi-Fi. */
        }

        requirement emergencyAlerts {
            doc /* The system shall trigger emergency APIs if a thermal event occurs. */
        }

        requirement tempThreshold {
            doc /* The system's internal temperature shall not exceed 400 Celsius. */
            attribute maxTemp : ThermodynamicTemperatureValue = 400.0 ['°C'];
            constraint { maxTemp <= 400.0 ['°C'] }
        }

        requirement mtbfTarget {
            doc /* The system shall achieve an MTBF of 10,000 hours. */
            attribute mtbf : DurationValue = 10000.0 [h];
            constraint { mtbf >= 10000.0 [h] }
        }
    }

    /* --- PHYSICAL REQUIREMENTS --- */
    package PhysicalRequirements {
        private import LogicalRequirements::*;
        private import ScalarValues::*;
        private import ISQ::*;
        private import SI::*;

        requirement wifiAntenna {
            doc /* The physical device must include a 2.4GHz/5GHz Wi-Fi antenna module. */
        }

        requirement peakPower {
            doc /* Peak power consumption must not exceed 1500W. */
            attribute powerLimit : PowerValue = 1500.0 [W];
            constraint { powerLimit <= 1500.0 [W] }
        }

        requirement maxMass {
            doc /* The total mass of the toaster assembly shall be less than 3.0 kg. */
            attribute massLimit : MassValue = 3.0 [kg];
            constraint { massLimit <= 3.0 [kg] }
        }
    }

    /* --- LOGICAL ARCHITECTURE & BEHAVIOR --- */
    package LogicalArchitecture {
        private import ScalarValues::*;
        private import ISQ::*;
        private import SI::*;
        private import LogicalRequirements::*;

        /* Signals and Events */
        action def StartToasting;
        action def TempAlert;
        action def TimerExpired;

        /* Parts */
        part def SensorSubsystem {
            attribute temp : ThermodynamicTemperatureValue;
        }

        part def ToasterController {
            port wifiPort;
            port emergencyPort;

            state def ToasterStates {
                entry;
                then Standby;

                state Standby;
                state Heating;
                state Cooling;
                state MaintenanceRequired;
                state EmergencyShutoff;

                transition t1
                    first Standby
                    accept StartToasting
                    then Heating;

                transition t2
                    first Heating
                    accept TimerExpired
                    then Cooling;

                transition t3
                    first Heating
                    accept TempAlert
                    then EmergencyShutoff;
            }

            exhibit state toasterState : ToasterStates;
        }
        
        part def LogicalToaster {
            part controller : ToasterController;
            part sensors : SensorSubsystem;
            satisfy LogicalRequirements::networkConn;
            satisfy LogicalRequirements::emergencyAlerts;
        }
    }

    /* --- PHYSICAL ARCHITECTURE & VARIANTS --- */
    package PhysicalArchitecture {
        private import ScalarValues::*;
        private import ISQ::*;
        private import SI::*;
        private import PhysicalRequirements::*;

        variation part def HeatingElement {
            attribute cost : Real;
            attribute efficiency : Real;
            attribute mass : MassValue;
        }

        part def NichromeCoil :> HeatingElement {
            attribute redefines cost = 15.0;
            attribute redefines efficiency = 0.8;
            attribute redefines mass = 0.5 [kg];
        }

        part def InductionCoil :> HeatingElement {
            attribute redefines cost = 45.0;
            attribute redefines efficiency = 0.95;
            attribute redefines mass = 1.0 [kg];
        }

        variation part def ChassisColor;
        part def StainlessSteel :> ChassisColor;
        part def MatteBlack :> ChassisColor;

        part def PhysicalToaster {
            part heatingElem : HeatingElement;
            part chassis : ChassisColor;
            
            attribute totalMass : MassValue = heatingElem.mass + 1.5 [kg]; /* chassis + base mass */
            
            satisfy maxMass;
            satisfy peakPower;
        }
    }

    /* --- SYSTEM OF SYSTEMS (SoS) --- */
    package SystemOfSystems {
        private import LogicalArchitecture::*;
        private import PhysicalArchitecture::*;

        part def SmartPhoneApp {
            perform action userConfigures;
            port appPort;
        }

        part def IoTCloudService {
            perform action cloudLogs;
            port cloudPort;
        }

        part def FireDepartmentAPI {
            perform action fireDeptNotified;
            port emergencyRecvPort;
        }

        part def SuperToasterSoS {
            part toaster : LogicalToaster;
            part app : SmartPhoneApp;
            part cloud : IoTCloudService;
            part fireDept : FireDepartmentAPI;

            /* Connections */
            connection wifiConn connect toaster.controller.wifiPort to app.appPort;
            connection telemetryConn connect toaster.controller.wifiPort to cloud.cloudPort;
            connection emergConn connect toaster.controller.emergencyPort to fireDept.emergencyRecvPort;

            /* Swimlane Actions mapped to Parts */
            action smartToastingProcess {
                action userSelectsProfile {
                    perform app.userConfigures;
                }
                action toasterHeats {
                    /* performed by toaster internally */
                }
                action cloudLogsData {
                    perform cloud.cloudLogs;
                }
                action alertsFireDept {
                    perform fireDept.fireDeptNotified;
                }

                first userSelectsProfile then toasterHeats;
                first toasterHeats then cloudLogsData;
            }
        }
    }

    /* --- VIEWS --- */
    package Views {
        private import ConceptRequirements::*;
        private import LogicalArchitecture::*;
        private import PhysicalArchitecture::*;
        private import SystemOfSystems::*;

        view requirementsView {
            expose ConceptRequirements;
        }

        view logicalView {
            expose LogicalArchitecture;
        }

        view physicalView {
            expose PhysicalArchitecture;
        }

        view sosView {
            expose SystemOfSystems;
        }
    }
}

# INCOSE Systems Engineering Template - Insect Hunting Drone

## 1. Business or Mission Analysis
### 1.1 Problem Statement
Insects and pests cause significant agricultural and residential damage. Chemical pesticides have negative environmental and health impacts. There is a need for a targeted, chemical-free, autonomous pest control solution.

### 1.2 Mission Objectives
Provide a solar-powered autonomous drone capable of identifying, tracking, and neutralizing pest insects using a laser, while operating safely in a geofenced area.

### 1.3 Key Stakeholders (Business Level)
- Human Operator (Farmer / Homeowner)
- Safety Regulator
- Manufacturer

## 2. Stakeholder Needs and Requirements Definition
### 2.1 Use Cases / Operational Scenarios
- **Patrol and Hunt:** Drone takes off, patrols area, identifies insects via CV, fires laser at pests, and spares beneficial insects.
- **Solar Charging:** Drone lands to recharge its batteries using solar energy.
- **Reporting:** Drone communicates pest control findings to the human operator.

### 2.2 Measures of Effectiveness (MOEs)
- **Overall Performance:** High success rate in neutralizing pests without harming beneficial insects.
- **Weight:** Drone mass must be low enough to fly efficiently.
- **Flight Time:** Maximize patrol time between charges.
- **Bugs Killed:** Number of pests neutralized per charge cycle.
- **Estimated Cost:** Keep the drone affordable for mass adoption.

## 3. System Requirements Definition
### 3.1 Functional Requirements
- **REQ_01 (Solar Power):** The drone shall be powered by solar energy, capable of landing, charging via solar, and taking off to continue hunting.
- **REQ_02 (Laser Weapon):** The drone shall be equipped with a laser weapon to kill insects.
- **REQ_03 (Computer Vision):** The drone shall use sensors and AI computer vision to identify insects as pests or beneficial.
- **REQ_04 (Navigation):** The drone shall use a GPS system for navigation.
- **REQ_05 (Communication):** The drone shall report findings to a human operator.
- **REQ_06 (Autonomy):** The drone shall be capable of autonomous operation.
- **REQ_07 (Weather):** The drone shall be capable of operating in varied weather, including moderate rain.
- **REQ_08 (Payload):** The drone shall support a payload capacity up to 1 kg.
- **REQ_09 (Geofence):** The drone shall operate within a maximum altitude of 25 meters and a geofenced area (1 mile x 1 mile) around the base.

### 3.2 Non-Functional / Quality Requirements (Safety Protocols)
- **REQ_10 (Rotor Safety):** Rotor blades must have physical guards or emergency stop mechanisms.
- **REQ_11 (Battery Safety):** The battery subsystem must include thermal monitoring to prevent fires.
- **REQ_12 (Laser Safety):** The laser weapon must have safety interlocks (e.g., eye-safe compliance, disabling fire when tilted toward humans).

### 3.3 System Constraints
- Safety regulations for commercial drones.

## 4. Architecture Definition
### 4.1 Logical Architecture / Subsystems
- **NavigationSystem:** Manages GPS and flight path.
- **LaserWeapon:** Delivers lethal energy to pests.
- **SensorArray:** Cameras and sensors for AI processing.
- **PowerSubsystem:** Solar panels, battery, and distribution.
- **FlightController:** Manages motors, autonomy, and state transitions.

### 4.2 Interfaces and Interactions
- FlightController requests position from NavigationSystem.
- SensorArray provides targets to FlightController.
- FlightController arms and fires LaserWeapon.
- PowerSubsystem monitors battery and triggers RTB (Return to Base).

### 4.3 System Behavior
- **States:** Charging, Patrolling, Targeting, Firing, Returning, EmergencyStop.
- **Actions:** Target Acquisition, Safety Interlock Check, Laser Discharge.

## 5. Design Definition
### 5.1 Physical Components
- Solar Panels
- Laser Diode Module
- AI Vision Processor
- GPS Module
- Motors and Rotors

### 5.2 Allocation Matrix
- Solar Panels -> PowerSubsystem
- Laser Diode Module -> LaserWeapon
- AI Vision Processor -> SensorArray
- GPS Module -> NavigationSystem
- Motors -> FlightController

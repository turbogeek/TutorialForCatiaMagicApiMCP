# INCOSE Systems Engineering Template - Super Toaster SoS

## 1. Business or Mission Analysis

### 1.1 Problem Statement

Modern kitchens require highly advanced, reliable, and safe appliances. Traditional toasters lack connectivity, quantitative performance tracking, and safety integrations (e.g., automatic fire department dispatch). There is a market need for a "Super Toaster" that acts as a System of Systems to deliver perfect toast, track telemetry, and ensure absolute safety.

### 1.2 Mission Objectives

Provide a premium, IoT-connected "Super Toaster" capable of perfect toasting in under 2 minutes, interacting with user smartphones for custom profiles, logging maintenance data to the manufacturer cloud, and communicating with local emergency services during thermal runaways.

### 1.3 Key Stakeholders (Business Level)

- Consumer / End User
- Software Architect
- Reliability Engineer
- Manufacturer
- Local Fire Department

## 2. Stakeholder Needs and Requirements Definition

### 2.1 Use Cases / Operational Scenarios

- **Smart Toasting:** User configures a toast profile via the Smartphone App. The app sends the configuration to the toaster. The toaster heats the bread. Upon completion, the toaster sends a notification to the app and logs the cycle to the Manufacturer Cloud.
- **Maintenance & Diagnostics:** Manufacturer Cloud aggregates usage statistics to calculate MTBF and push firmware updates to the toaster.
- **Emergency Shutoff:** If the toaster's internal temperature exceeds critical safe limits, it automatically shuts off power and triggers the Fire Department API.

### 2.2 Measures of Effectiveness (MOEs) & MOPs

- **MOE 1:** 99.9% success rate for desired toast doneness.
- **MOE 2:** Zero uncontained thermal incidents.
- **MOP 1 (Toasting Speed):** Toast 2 slices in under 120 seconds.
- **MOP 2 (Power Limits):** Peak power consumption must not exceed 1500W.
- **TPM (Mass):** Total system mass < 3.0 kg.
- **TPM (Reliability):** MTBF > 10,000 hours.

## 3. System Requirements Definition

### 3.1 Functional Requirements

- **REQ_01 (App Control):** The toaster shall accept configuration profiles via Wi-Fi from a smartphone application.
- **REQ_02 (Cloud Logging):** The toaster shall securely log usage cycles to the Manufacturer Cloud.
- **REQ_03 (Emergency API):** The toaster shall dispatch an emergency signal to the Fire Department API if temperatures exceed 400°C.
- **REQ_04 (Heating Efficiency):** The toaster shall complete a standard toast cycle in <= 120 seconds.
- **REQ_05 (Peak Power):** The toaster shall draw a peak power of <= 1500W.

### 3.2 Non-Functional / Quality Requirements

- **REQ_06 (Variant Architecture):** The system shall support variant configurations for heating elements (Nichrome Coil vs Induction) and chassis color (Stainless Steel vs Matte Black).
- **REQ_07 (Reliability):** The system shall have a minimum Mean Time Between Failures (MTBF) of 10,000 hours.
- **REQ_08 (Mass Constraint):** The physical toaster assembly shall not exceed 3.0 kg.

## 4. Architecture Definition

### 4.1 Logical Architecture / Subsystems

- **LogicalController:** Manages Wi-Fi communications, state transitions, and safety limits.
- **HeatingSubsystem:** Provides the thermal energy required for toasting.
- **SensorSubsystem:** Monitors internal temperature and smoke.
- **PowerSubsystem:** Distributes mains power to logic and heating elements.

### 4.2 Interfaces and Interactions

- **Wi-Fi Interface:** Between the Toaster and Smartphone App/Manufacturer Cloud.
- **Emergency API Interface:** Between the Toaster and Fire Department.
- **Internal Control Flows:** Controller activates HeatingSubsystem and reads from SensorSubsystem.

### 4.3 System Behavior (State Machine)

- **States:** Standby, Heating, Cooling, MaintenanceRequired, EmergencyShutoff.
- **Transitions:**
  - Standby -> Heating (`accept StartToasting`)
  - Heating -> Cooling (`after 2 [min]`)
  - Heating -> EmergencyShutoff (`when temp > 400 [C]`)

## 5. Design Definition

### 5.1 Physical Components & Variants

- **Heating Element Variations:**
  - Nichrome Coil (Lower Cost, Lower Efficiency)
  - Induction Coil (Higher Cost, Higher Efficiency)
- **Chassis Variations:**
  - Stainless Steel
  - Matte Black

### 5.2 Trade Studies & Analysis

- Compare Nichrome Coil against Induction Coil evaluating power draw vs. cost vs. repairability.

# INCOSE Systems Engineering Template - 2-Slot Toaster

## 1. Business or Mission Analysis
### 1.1 Problem Statement
People need a convenient, reliable, and safe way to toast standard slices of bread, bagels, or pastries in a household kitchen environment without constant manual supervision.

### 1.2 Mission Objectives
Provide an affordable, 2-slot electric toaster that consistently toasts bread products to user-selected levels while ensuring safety (preventing burns/fires).

### 1.3 Key Stakeholders (Business Level)
- End User (Household Consumer)
- Safety Regulator
- Manufacturer / Support Team

## 2. Stakeholder Needs and Requirements Definition
### 2.1 Use Cases / Operational Scenarios
- **Toast Bread:** User inserts bread, sets browning level, presses lever, and toaster automatically heats the bread and ejects it when done.
- **Cancel Toasting:** User stops the toasting mid-cycle.
- **Adjust Browning Level:** User selects desired toastiness.

### 2.2 Measures of Effectiveness (MOEs)
- Time to toast: Under 3 minutes for max setting.
- Surface temperature: Exterior remains safe to touch during operation.

## 3. System Requirements Definition
### 3.1 Functional Requirements
- **REQ_01 (Accommodate Slices):** The toaster shall accommodate two standard slices of bread simultaneously.
- **REQ_02 (Adjustable Heating):** The toaster shall provide a user-adjustable heating level.
- **REQ_03 (Auto Eject):** The toaster shall automatically eject the bread when the toasting cycle completes.
- **REQ_04 (Manual Cancel):** The toaster shall allow the user to manually cancel the toasting cycle at any time.

### 3.2 Non-Functional / Quality Requirements
- **REQ_05 (Safe Temp):** The exterior surface temperature shall not exceed 60 degrees Celsius during operation.
- **REQ_06 (Power):** The toaster shall operate on standard 120V AC household power.

### 3.3 System Constraints
- **REQ_07 (Safety Standard):** The toaster must comply with UL safety standards for household appliances.

## 4. Architecture Definition
### 4.1 Logical Architecture / Subsystems
- **PowerSubsystem:** Distributes electrical power to internal components.
- **UserInterfaceSubsystem:** Allows the user to set browning levels, start, and cancel the process.
- **HeatingSubsystem:** Converts electrical power into thermal energy to toast the bread.
- **ControlSubsystem:** Monitors the toasting time based on user settings and controls the heating and ejection.
- **EjectionSubsystem:** Holds the bread and ejects it when commanded.

### 4.2 Interfaces and Interactions
- Power cable connects to a standard wall outlet.
- User interacts with the dial, cancel button, and carriage lever.
- UI subsystem sends target toasting duration to Control subsystem.
- Control subsystem enables power to Heating subsystem.
- Control subsystem releases Ejection subsystem spring mechanism to eject bread.

### 4.3 System Behavior
- **States:** Off, Toasting.
- **Actions:** Insert Bread -> Set Timer -> Apply Heat -> Monitor Time -> Stop Heat -> Eject Bread.

## 5. Design Definition
### 5.1 Physical Components
- Nichrome Wire Heaters
- Electronic Timer Circuit
- Spring-loaded Carriage Lever
- Plastic/Metal Casing
- Power Cord

### 5.2 Allocation Matrix
- Nichrome Wire Heaters -> HeatingSubsystem
- Electronic Timer Circuit -> ControlSubsystem
- Spring-loaded Carriage Lever -> EjectionSubsystem
- Plastic/Metal Casing -> UserInterfaceSubsystem / Structural Shell

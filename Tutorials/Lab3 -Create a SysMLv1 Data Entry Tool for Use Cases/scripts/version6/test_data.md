### Primary Actors

User, Dog,  Waste Processor

### Secondary Actors

External Waste Management System, Child, Robot Monitoring,  Repair Technician, WiFi Router,Cat, Power Grid,solar panel

### System Context

Smart Dog Waste Collector System

### Use Cases

- Initialize Pooper Scooper
- Enrole Dog
- Enrole users
- Learn schedule
- Learn Yard
- Self Test
- Detect Tampering
- Detect Wifi Signal Loss
- Detect power loss
- Detect Degradation of Performance
- Charge Batteries
- Avoid Obstruction
- Report Obstruction
- Locate Poop
- Avoid Obstruction
- Report Obstruction
- Avoid Child
- Detect Tampering
- Emergency Shutdown
- Dump Poop into Digester
- Detect Digester Full
- Report Digester Full
- Report Fauld
- Enter Diagnostic Mode
- Send Diagnostics
- Update Software
- Detect Cat
- Bark at Cat

### Generalizations

User <- Manager
Manager <- Maintenance Person

### Includes

Initialize Pooper Scooper -> Self Test
Dump Poop into Digester -> Locate Poop
Enter Diagnostic Mode -> Send Diagnostics

### Extends

Initialize Pooper Scooper <- Charge Batteries [Battery Low]
Locate Poop <- Avoid Obstruction [Obstacle Detected]
Locate Poop <- Avoid Child [Child Detected]
Dump Poop into Digester <- Report Digester Full [Digester Full]
Self Test <- Report Fauld [Fault Detected]

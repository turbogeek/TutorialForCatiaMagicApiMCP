// =============================================================================
// create-drone-model.groovy — SysMLv2 model: autonomous fly-hunting drone
//
// Demonstrates a layered SysMLv2 model organized as:
//   DroneFlyHunter/
//     Concept/          — stakeholder-facing mission + high-level system
//     UseCases/         — mission threads, nominal and corner-case
//     Requirements/     — Functional, Performance, Safety, CornerCases
//     Structure/        — Drone system + subsystems (logical design)
//     Behavior/         — States + Actions (logical design)
//     Traceability/     — where SatisfyRequirementUsage links to requirements
//
// SysMLv2 API patterns used (learned from v2Matrix iteration-1 probes):
//   * SysMLFactory.eINSTANCE for Usage/Definition elements
//   * KerMLFactory.eINSTANCE for Package, ReferenceSubsetting, Documentation,
//     FeatureValue, LiteralString (not all exposed on SysMLFactoryImpl).
//   * element.setOwner(namespace) inside a SessionManager session.
//   * element.setDeclaredName(String) — getHumanName() reflects it.
//   * Satisfy = SatisfyRequirementUsage owned by the satisfying feature + a
//     ReferenceSubsetting pointing to the requirement (the ':>' semantic).
//
// Run via REST test harness after selecting the target folder in Cameo's
// containment tree:
//   bin/harness.bat run "E:\_Documents\git\TutorialForCatiaMagicApiMCP\DroneModelCreation\create-drone-model.groovy"
// =============================================================================

import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.dassault_systemes.modeler.sysml.model.sysml.SysMLFactory
import com.dassault_systemes.modeler.kerml.model.kerml.KerMLFactory
import com.dassault_systemes.modeler.kerml.model.kerml.Namespace
import com.dassault_systemes.modeler.kerml.model.kerml.Element
import com.dassault_systemes.modeler.kerml.model.kerml.Feature

// ---- Logger -----------------------------------------------------------------
String scriptsDir = 'E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\scripts'
def LoggerClass = new GroovyClassLoader(getClass().getClassLoader())
    .parseClass(new File(scriptsDir, 'SysMLv2Logger.groovy'))
File logFile = new File('E:\\_Documents\\git\\TutorialForCatiaMagicApiMCP\\logs', 'create-drone-model.log')
def log = LoggerClass.newInstance('DroneModel', logFile)
log.info('=== create-drone-model.groovy start ===')

// ---- Cameo / factory handles ------------------------------------------------
def app          = Application.getInstance()
def project      = app.getProject()
def factory      = SysMLFactory.eINSTANCE
def kermlFactory = KerMLFactory.eINSTANCE
def sm           = SessionManager.getInstance()

if (project == null) {
    log.error('No active Cameo project — open a SysMLv2 project first')
    app.getGUILog().log('[DroneModel] ERROR: no active project')
    return
}

// ---- Locate target container from browser selection --------------------------
// Use the FIRST named Namespace found — either the selected element itself, or
// its nearest named ancestor. We do NOT walk up to the outermost package, so
// the user can target a sub-package for the model.
Namespace targetContainer = null
try {
    def browser = app.getMainFrame().getBrowser()
    def selected = browser.getActiveTree()?.getSelectedNodes()
    if (selected && selected.length > 0) {
        def obj = selected[0].getUserObject()
        while (obj != null) {
            if (obj instanceof Namespace) {
                def nm = ((Namespace) obj).getDeclaredName()
                if (nm != null && !nm.isEmpty()) {
                    targetContainer = (Namespace) obj
                    break
                }
            }
            try {
                def parent = obj.respondsTo('getOwner') ? obj.getOwner() : null
                if (parent == null) break
                obj = parent
            } catch (Exception e) { break }
        }
    }
} catch (Exception e) {
    log.error('Browser selection probe failed: ' + e.message)
}

if (targetContainer == null) {
    log.error('Select a named Package in the containment tree first')
    app.getGUILog().log('[DroneModel] ERROR: select a Package first')
    return
}
log.info('Target container: ' + targetContainer.getDeclaredName())

// ---- Element-building helpers ------------------------------------------------
// Closures capture `factory`, `kermlFactory`, and a shared `elements` map so
// later code can look up any element by its declaredName for satisfy wiring.
def elements = [:] as Map

def documentFor = { def owner, String body ->
    if (!body) return
    try {
        def d = kermlFactory.createDocumentation()
        d.setOwner(owner)
        if (d.respondsTo('setBody')) d.setBody(body)
    } catch (Exception e) {
        // Documentation is best-effort; never fail the model over it.
    }
}

def pkg = { String name, Namespace owner, String doc = null ->
    def p = kermlFactory.createPackage()
    p.setDeclaredName(name)
    p.setOwner(owner)
    documentFor(p, doc)
    elements[name] = p
    p
}

def req = { String id, Namespace owner, String doc ->
    def r = factory.createRequirementUsage()
    r.setDeclaredName(id)
    r.setOwner(owner)
    documentFor(r, doc)
    elements[id] = r
    r
}

def useCase = { String name, Namespace owner, String doc ->
    def u = factory.createUseCaseUsage()
    u.setDeclaredName(name)
    u.setOwner(owner)
    documentFor(u, doc)
    elements[name] = u
    u
}

def part = { String name, def owner, String doc = null ->
    def p = factory.createPartUsage()
    p.setDeclaredName(name)
    p.setOwner(owner)
    documentFor(p, doc)
    elements[name] = p
    p
}

def action = { String name, def owner, String doc = null ->
    def a = factory.createActionUsage()
    a.setDeclaredName(name)
    a.setOwner(owner)
    documentFor(a, doc)
    elements[name] = a
    a
}

def stateU = { String name, def owner, String doc = null ->
    def s = factory.createStateUsage()
    s.setDeclaredName(name)
    s.setOwner(owner)
    documentFor(s, doc)
    elements[name] = s
    s
}

def attr = { String name, def owner, String doc = null ->
    def a = factory.createAttributeUsage()
    a.setDeclaredName(name)
    a.setOwner(owner)
    documentFor(a, doc)
    elements[name] = a
    a
}

// SatisfyRequirementUsage owned by the satisfier, with a ReferenceSubsetting
// pointing to the requirement — the textual ':>' pattern.
def satisfy = { String name, def satisfier, def requirement ->
    def s = factory.createSatisfyRequirementUsage()
    s.setDeclaredName(name)
    s.setOwner(satisfier)
    def rs = kermlFactory.createReferenceSubsetting()
    rs.setOwner(s)
    rs.setSubsettingFeature(s)
    rs.setSubsettedFeature(requirement)
    elements[name] = s
    s
}

// ---- Build the model ---------------------------------------------------------
try {
    sm.createSession(project, 'Create Drone Fly-Hunter Model')

    // ======= Top-level =======
    def droneFH = pkg.call('DroneFlyHunter', targetContainer,
        'Autonomous drone that patrols a user-defined area, detects flies, ' +
        'and eliminates them with a low-power eye-safe laser. SysMLv2 model ' +
        'organized by Concept / UseCases / Requirements / Structure / Behavior.')

    // ======= 1. CONCEPT =======
    def conceptPkg = pkg.call('Concept', droneFH,
        'Stakeholder-facing view: mission statement, high-level system as a ' +
        'black box, key concept attributes.')

    def mission = part.call('Mission', conceptPkg,
        'Automate fly elimination in enclosed spaces (homes, restaurants, ' +
        'food processing) with minimal human intervention and zero chemical use.')

    def droneConcept = part.call('DroneSystem_Concept', conceptPkg,
        'The Drone system as viewed from outside: a thing that patrols and hunts.')

    attr.call('patrolArea_m2',       droneConcept, 'User-configured patrol area in square meters (max 100).')
    attr.call('maxFlightTime_min',   droneConcept, 'Minimum guaranteed flight time per charge.')
    attr.call('laserRange_m',        droneConcept, 'Effective elimination range.')
    attr.call('detectionAccuracy',   droneConcept, 'Fly-detection accuracy (fraction).')
    attr.call('rechargeTime_min',    droneConcept, 'Time from empty to full charge.')
    attr.call('eyeSafetyClass',      droneConcept, 'Laser eye-safety class (Class 1M default).')

    // ======= 2. USE CASES =======
    def ucPkg = pkg.call('UseCases', droneFH, 'Mission threads — nominal and corner cases.')
    useCase.call('UC_PatrolArea',         ucPkg, 'Autonomously patrol the configured rectangular area.')
    useCase.call('UC_DetectFly',          ucPkg, 'Identify a fly in the camera frame with >=95% confidence.')
    useCase.call('UC_EngageFly',          ucPkg, 'Track and eliminate a detected fly with the laser.')
    useCase.call('UC_ReturnToBase',       ucPkg, 'Navigate to the charging station on command or low battery.')
    useCase.call('UC_Recharge',           ucPkg, 'Dock, negotiate power, charge, and resume.')
    useCase.call('UC_ConfigurePatrolArea',ucPkg, 'Operator defines a rectangular 2D patrol boundary.')
    useCase.call('UC_ConfigureLaser',     ucPkg, 'Operator sets laser power/duration within safety limits.')
    useCase.call('UC_EmergencyAbort',     ucPkg, 'Immediate land-and-disarm on operator kill-switch.')
    useCase.call('UC_HumanSafety',        ucPkg, 'Abort laser firing when a human is within 5 m.')
    useCase.call('UC_ObstacleAvoidance',  ucPkg, 'Deflect path to maintain >=1 m from obstacles.')
    useCase.call('UC_LowBatteryResponse', ucPkg, 'Return to base when battery <=20%.')
    useCase.call('UC_MultipleTargets',    ucPkg, 'Prioritize closest fly when several are in view.')
    useCase.call('UC_GPSLoss',            ucPkg, 'Hover at last known position on GPS fix loss.')
    useCase.call('UC_PropellerFailure',   ucPkg, 'Safe descent on rotor/propeller fault.')
    useCase.call('UC_WeatherAbort',       ucPkg, 'Refuse takeoff / return home when wind > 20 km/h.')

    // ======= 3. REQUIREMENTS =======
    def reqPkg = pkg.call('Requirements', droneFH, 'All requirements, bucketed.')

    def reqFunc = pkg.call('Functional', reqPkg, 'What the drone does.')
    req.call('R_DetectFly',      reqFunc, 'The drone shall detect flies with >=95% accuracy within 3 m.')
    req.call('R_TrackFly',       reqFunc, 'The drone shall track a moving fly up to 5 m/s with the laser.')
    req.call('R_EliminateFly',   reqFunc, 'The drone shall eliminate a targeted fly within 0.5 s of lock.')
    req.call('R_PatrolArea',     reqFunc, 'The drone shall patrol a user-defined rectangular area.')
    req.call('R_HoverAccuracy',  reqFunc, 'The drone shall maintain hover within 10 cm of a waypoint.')
    req.call('R_MultiTarget',    reqFunc, 'When multiple flies are visible, the drone shall engage the closest first.')
    req.call('R_ConfigArea',     reqFunc, 'The operator shall configure the patrol area via a bounded-rectangle input.')
    req.call('R_KillSwitch',     reqFunc, 'The drone shall accept an operator emergency-stop via a dedicated radio command.')

    def reqPerf = pkg.call('Performance', reqPkg, 'How well and how fast.')
    req.call('R_FlightTime',       reqPerf, 'The drone shall operate >=30 minutes per full charge.')
    req.call('R_RechargeTime',     reqPerf, 'The drone shall fully recharge in <=60 minutes.')
    req.call('R_LaserRange',       reqPerf, 'Laser effective range shall be 0.5 m to 5 m.')
    req.call('R_MaxPatrolArea',    reqPerf, 'The drone shall cover a patrol area up to 100 m^2.')
    req.call('R_PositioningAcc',   reqPerf, 'Positioning accuracy shall be <=10 cm (indoor) / <=1 m (outdoor).')
    req.call('R_ResponseTime',     reqPerf, 'From fly detection to laser fire: <=1.0 s.')

    def reqSafety = pkg.call('Safety', reqPkg, 'Non-negotiable constraints.')
    req.call('R_HumanDetection',  reqSafety, 'The drone shall detect humans within 5 m and inhibit laser firing.')
    req.call('R_LowBatteryRTB',   reqSafety, 'The drone shall return to base when battery <=20%.')
    req.call('R_EyeSafeLaser',    reqSafety, 'Laser output shall be Class 1M eye-safe when not aimed at a target.')
    req.call('R_ObstacleDistance',reqSafety, 'The drone shall maintain >=1 m from walls and static obstacles.')
    req.call('R_Geofence',        reqSafety, 'The drone shall not operate outside the configured patrol area.')
    req.call('R_WindLimit',       reqSafety, 'The drone shall not operate when sustained wind exceeds 20 km/h.')
    req.call('R_DisarmOnLand',    reqSafety, 'The drone shall disarm the laser within 0.2 s of landing.')

    def reqCorner = pkg.call('CornerCases', reqPkg, 'Off-nominal conditions.')
    req.call('R_GPSLoss',          reqCorner, 'On GPS fix loss, hover at last known position and request operator.')
    req.call('R_PropellerFailure', reqCorner, 'On propeller/motor fault, enter safe-descent mode to nearest clear floor.')
    req.call('R_TempLimit',        reqCorner, 'Above 40 °C ambient, reduce flight time by 20% and warn operator.')
    req.call('R_Darkness',         reqCorner, 'In ambient light <10 lux, enable onboard LED illumination for detection.')
    req.call('R_SwarmConflict',    reqCorner, 'When another drone is detected within 2 m, yield and re-plan path.')
    req.call('R_SurfaceReflect',   reqCorner, 'On a reflective surface (mirror/glass) within 2 m of target, inhibit laser.')
    req.call('R_UnresponsiveOp',   reqCorner, 'If operator heartbeat is lost for >=30 s, return to base.')

    // ======= 4. STRUCTURE (logical design) =======
    def strPkg = pkg.call('Structure', droneFH, 'Parts and their decomposition (logical design).')

    // Top-level system
    def drone = part.call('Drone', strPkg, 'The fly-hunting drone as a logical part; aggregates all subsystems.')

    // Subsystems — owned by Drone so containment tree reflects system decomposition
    def flightCtl   = part.call('FlightController',       drone, 'Runs flight control loops, path planning, telemetry.')
    def propulsion  = part.call('PropulsionSystem',       drone, '4 motor+propeller assemblies; thrust and yaw control.')
    def power       = part.call('PowerSystem',            drone, 'Battery, power management, recharge interface.')
    def vision      = part.call('VisionSystem',           drone, 'Camera + image-processing pipeline for detection.')
    def laserTarget = part.call('LaserTargetingSystem',   drone, 'Laser, gimbal, range-finder; eye-safe default.')
    def safety      = part.call('SafetySystem',           drone, 'Human/obstacle detection, geofence, emergency stop.')
    def comms       = part.call('CommunicationSystem',    drone, 'Radio link to operator; GPS receiver.')
    def chassis     = part.call('ChassisFrame',           drone, 'Structural frame, mounting points, landing gear.')

    // 2nd-level decomposition
    part.call('Motor_1',    propulsion); part.call('Motor_2', propulsion)
    part.call('Motor_3',    propulsion); part.call('Motor_4', propulsion)
    part.call('Propeller_1',propulsion); part.call('Propeller_2', propulsion)
    part.call('Propeller_3',propulsion); part.call('Propeller_4', propulsion)
    part.call('ESC_Board',  propulsion, 'Electronic speed controller board.')

    def battery     = part.call('Battery',          power, 'LiPo or Li-ion pack; primary energy store.')
    part.call('PowerManagement',    power, 'Voltage regulation, cell balancing, charge control.')
    part.call('ChargePort',         power, 'Dock-based contactless or pin charging interface.')

    def camera      = part.call('Camera',           vision, 'RGB + IR for low-light detection.')
    def flyDetector = part.call('FlyDetectorAlgo',  vision, 'ML model identifying Musca domestica class targets.')
    part.call('ImageProcessor',  vision, 'Preprocessing: deinterlace, stabilize, threshold.')

    def laser       = part.call('Laser',            laserTarget, 'Low-power continuous-wave laser, <=1 W.')
    def gimbal      = part.call('Gimbal',           laserTarget, '2-axis gimbal for laser pointing.')
    def rangeFinder = part.call('LaserRangeFinder', laserTarget, 'Time-of-flight rangefinder for target distance.')

    def humanDet    = part.call('HumanDetector',    safety, 'Separate ML model flagging human silhouettes.')
    def obstacleDet = part.call('ObstacleDetector', safety, 'Ultrasonic + optical-flow obstacle sensing.')
    part.call('EmergencyStopReceiver', safety, 'Dedicated 433 MHz receiver for operator kill switch.')
    part.call('GeofenceModule',        safety, 'Compares current position to configured boundary.')

    def radio       = part.call('RadioModule',      comms, '2.4 GHz bi-directional telemetry + command.')
    def gps         = part.call('GPSReceiver',      comms, 'GNSS receiver — disabled indoors.')

    // Key attributes on specific parts (typed-usage stand-ins for parametric values)
    attr.call('capacity_Wh',       battery, 'Nominal battery capacity in watt-hours.')
    attr.call('cycles_remaining',  battery, 'Remaining charge cycles before capacity fade threshold.')
    attr.call('outputPower_W',     laser,   'Laser output power (max 1.0 W in Class 1M default).')
    attr.call('wavelength_nm',     laser,   'Laser wavelength in nm.')
    attr.call('maxSlewRate_deg_s', gimbal,  'Maximum gimbal slew rate.')

    // ======= 5. BEHAVIOR (logical design) =======
    def behPkg    = pkg.call('Behavior', droneFH, 'States + Actions (logical design).')
    def statesPkg = pkg.call('States',  behPkg, 'Lifecycle + mission states.')
    stateU.call('S_Off',             statesPkg, 'Power off, idle on charger.')
    stateU.call('S_BootingUp',       statesPkg, 'Firmware POST, sensor init.')
    stateU.call('S_Idle',            statesPkg, 'Ready on pad awaiting command.')
    stateU.call('S_Patrolling',      statesPkg, 'Flying patrol waypoints, scanning.')
    stateU.call('S_Detecting',       statesPkg, 'Fly candidate identified; verifying.')
    stateU.call('S_Tracking',        statesPkg, 'Target locked; maintaining aim.')
    stateU.call('S_Engaging',        statesPkg, 'Laser firing — bounded duration.')
    stateU.call('S_ReturningToBase', statesPkg, 'Navigating back to charger.')
    stateU.call('S_Charging',        statesPkg, 'Docked and charging.')
    stateU.call('S_EmergencyAbort',  statesPkg, 'Kill-switch received; descending.')
    stateU.call('S_SafeDescent',     statesPkg, 'Propeller fault; controlled fall.')
    stateU.call('S_Error',           statesPkg, 'Unrecoverable fault; operator attention required.')

    def actionsPkg = pkg.call('Actions', behPkg, 'Mission actions performed by subsystems.')
    action.call('A_BootUp',            actionsPkg)
    action.call('A_ScanArea',          actionsPkg, 'Fly the patrol pattern while the camera scans for flies.')
    action.call('A_DetectFly',         actionsPkg, 'Run the fly-detector algorithm on each camera frame.')
    action.call('A_TrackFly',          actionsPkg, 'Keep the fly centered in frame; update gimbal.')
    action.call('A_CheckHumanSafety',  actionsPkg, 'Run human-detector; if positive, inhibit laser.')
    action.call('A_CheckRange',        actionsPkg, 'Query the range-finder; proceed only within 0.5–5 m.')
    action.call('A_AimLaser',          actionsPkg)
    action.call('A_FireLaser',         actionsPkg, 'Emit for bounded duration; cease on loss of lock.')
    action.call('A_CheckBattery',      actionsPkg)
    action.call('A_AvoidObstacle',     actionsPkg)
    action.call('A_ReturnHome',        actionsPkg)
    action.call('A_LandOnCharger',     actionsPkg)
    action.call('A_Recharge',          actionsPkg)
    action.call('A_EmergencyShutdown', actionsPkg)
    action.call('A_SafeDescent',       actionsPkg)

    // ======= 6. TRACEABILITY — satisfy links =======
    // Each satisfy is owned by the subsystem (satisfying feature) and has
    // a ReferenceSubsetting pointing to the requirement.
    def trc = [:]  // key-> SatisfyRequirementUsage (so we can see counts)
    def S = { String satisfyName, def satPart, String reqName ->
        def r = elements[reqName]
        if (r == null) { log.warn('Unknown req: ' + reqName); return }
        def s = satisfy.call(satisfyName, satPart, r)
        trc[satisfyName] = s
    }

    // Functional
    S('s_vision_DetectFly',           vision,      'R_DetectFly')
    S('s_vision_TrackFly',            vision,      'R_TrackFly')
    S('s_laser_EliminateFly',         laserTarget, 'R_EliminateFly')
    S('s_flightCtl_PatrolArea',       flightCtl,   'R_PatrolArea')
    S('s_flightCtl_HoverAccuracy',    flightCtl,   'R_HoverAccuracy')
    S('s_vision_MultiTarget',         vision,      'R_MultiTarget')
    S('s_comms_ConfigArea',           comms,       'R_ConfigArea')
    S('s_safety_KillSwitch',          safety,      'R_KillSwitch')

    // Performance
    S('s_power_FlightTime',           power,       'R_FlightTime')
    S('s_power_RechargeTime',         power,       'R_RechargeTime')
    S('s_laser_LaserRange',           laserTarget, 'R_LaserRange')
    S('s_flightCtl_MaxPatrolArea',    flightCtl,   'R_MaxPatrolArea')
    S('s_flightCtl_PositioningAcc',   flightCtl,   'R_PositioningAcc')
    S('s_vision_ResponseTime',        vision,      'R_ResponseTime')

    // Safety
    S('s_safety_HumanDetection',      safety,      'R_HumanDetection')
    S('s_power_LowBatteryRTB',        power,       'R_LowBatteryRTB')
    S('s_laser_EyeSafe',              laserTarget, 'R_EyeSafeLaser')
    S('s_safety_ObstacleDistance',    safety,      'R_ObstacleDistance')
    S('s_safety_Geofence',            safety,      'R_Geofence')
    S('s_safety_WindLimit',           safety,      'R_WindLimit')
    S('s_laser_DisarmOnLand',         laserTarget, 'R_DisarmOnLand')

    // Corner cases
    S('s_comms_GPSLoss',              comms,       'R_GPSLoss')
    S('s_propulsion_PropellerFailure',propulsion,  'R_PropellerFailure')
    S('s_power_TempLimit',            power,       'R_TempLimit')
    S('s_vision_Darkness',            vision,      'R_Darkness')
    S('s_safety_SwarmConflict',       safety,      'R_SwarmConflict')
    S('s_laser_SurfaceReflect',       laserTarget, 'R_SurfaceReflect')
    S('s_safety_UnresponsiveOp',      safety,      'R_UnresponsiveOp')

    // ======= Commit =======
    sm.closeSession(project)

    // Summary
    def packages  = elements.values().findAll { it.getClass().getSimpleName() == 'PackageImpl' }.size()
    def reqs      = elements.values().findAll { it.getClass().getSimpleName() == 'RequirementUsageImpl' }.size()
    def ucs       = elements.values().findAll { it.getClass().getSimpleName() == 'UseCaseUsageImpl' }.size()
    def parts     = elements.values().findAll { it.getClass().getSimpleName() == 'PartUsageImpl' }.size()
    def actions   = elements.values().findAll { it.getClass().getSimpleName() == 'ActionUsageImpl' }.size()
    def states    = elements.values().findAll { it.getClass().getSimpleName() == 'StateUsageImpl' }.size()
    def attrs     = elements.values().findAll { it.getClass().getSimpleName() == 'AttributeUsageImpl' }.size()
    def satisfies = elements.values().findAll { it.getClass().getSimpleName() == 'SatisfyRequirementUsageImpl' }.size()

    log.info("Model committed. Summary:")
    log.info("  packages  = ${packages}")
    log.info("  use cases = ${ucs}")
    log.info("  requirements = ${reqs}")
    log.info("  parts     = ${parts}")
    log.info("  attributes= ${attrs}")
    log.info("  actions   = ${actions}")
    log.info("  states    = ${states}")
    log.info("  satisfies = ${satisfies}")
    log.info("  total elements = ${elements.size()}")

    // Verify one satisfy wiring (same quick check as the fixture builder)
    def sample = elements['s_laser_EliminateFly']
    if (sample != null) {
        log.info("Verify s_laser_EliminateFly: satisfiedReq=${sample.getSatisfiedRequirement()?.getDeclaredName()}")
    }

    app.getGUILog().log(
        "[DroneModel] SUCCESS: ${packages} packages, ${ucs} UCs, ${reqs} reqs, " +
        "${parts} parts, ${states} states, ${actions} actions, ${satisfies} satisfies.")
} catch (Throwable t) {
    log.error('Model creation FAILED: ' + t.toString())
    try { sm.cancelSession(project) } catch (Exception ignored) {}
    app.getGUILog().log('[DroneModel] FAILED: ' + t.message)
    throw t
}

log.info('=== create-drone-model.groovy end ===')

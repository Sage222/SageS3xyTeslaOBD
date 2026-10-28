# S3XY Telemetry Cockpit (2023 Tesla Model 3)

**S3XY Telemetry Cockpit** is a high-precision Android instrument cluster, CAN-bus diagnostic interrogator, and telemetry session recorder engineered for the 2023 Tesla Model 3 connected via the Enhance **S3XY Commander** (`S3XY_OBD` Wi-Fi access point). It transforms any Android phone or tablet—in portrait or landscape—into a customizable motorsport and engineering cockpit with live CAN frame decoding, recorded CAN log replay, subsystem fault interrogation, and exportable session logs.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> All core functional, connectivity, diagnostic, and persistence preferences have been confirmed during Phase 1 clarification and are locked into this specification.

- **Confirmed Decision 1 — Starter Telemetry Dashboards**: Pre-configured with all four 2023 Model 3 telemetry views alongside custom user layouts:
  1. **Performance & Inverter Power**: Net pack power (kW), Front & Rear drive unit power/torque/RPM, inverter MOSFET & stator temperatures, 0–60 mph / 0–100 km/h sprint timer, and pedal maps.
  2. **Battery Pack Health, Cell Balance & Charging**: Usable vs. BMS State of Charge (%), pack voltage (V) & current (A), cell voltage min/max/delta (mV), brick temperature distribution, nominal full pack capacity vs. initial (degradation %), and AC/DC fast-charge curve metrics.
  3. **Thermal Loop & HVAC Status**: Octovalve position, radiator bypass, coolant flow rates (L/min) for powertrain and battery loops, chiller/supermanifold temperatures, cabin & ambient temps, and compressor power.
  4. **Braking, Regen & G-Force Dynamics**: Combined longitudinal/lateral G-ball accelerometer plot, brake line pressure (bar), regen vs. friction split bar, steering angle (°), and individual wheel speeds.
- **Confirmed Decision 2 — Connectivity & Recorded CAN Log Replay**: Connects directly to the `S3XY_OBD` Wi-Fi access point (`192.168.4.1`, UDP Port `1338` Panda/CANserver binary stream and TCP Port `35000` ELM327/STN bridge) using Android `ConnectivityManager` network binding so cellular data stays active while streaming local CAN frames. When away from the car, a built-in **Recorded CAN Log Replay Engine** plays back authentic 2023 Model 3 multi-bus CAN captures (including Supercharging, Canyon Spirited Drive, Cold-Soak Thermal Warmup, and Fault Injection traces) or user-imported `.log` / `.csv` files with play/pause, scrub bar, and `0.5x`–`4x` playback speed.
- **Confirmed Decision 3 — Decoded Tesla Alerts with Subsystem Filter & Freeze-Frame**: Interrogates Tesla CAN alert frames across BMS, Drive Inverter (`DI_a`), Thermal/VCFront (`VCFRONT_a`), High Voltage Controller (`HVP_a`), and Autopilot/ESP subsystems. Each alert displays its Tesla code, human-readable root cause, severity level, active vs. historical status, and a complete **Freeze-Frame Snapshot** (pack voltage, min/max cell voltage, coolant temps, vehicle speed, and raw hex payload at the moment of trigger) plus one-tap **Clear / Acknowledge** commands.
- **Confirmed Decision 4 — On-Device Room Persistence with CSV & JSON Export**: All custom dashboard configurations, widget layouts, recorded telemetry sessions, and alert history snapshots are stored locally in an on-device **Room SQLite database** with instant export via the Android system share sheet in standard `.csv` (time-series telemetry compatible with spreadsheet/MegaLogViewer tools) and `.json` (full dashboard presets, CAN frames, and alert freeze-frames) formats.

---

## 1. Overview & Core Concept

- **What It Does**: Establishes a low-latency socket link over the `S3XY_OBD` Wi-Fi network to decode 2023 Model 3 Vehicle/Chassis CAN frames into 60 fps radial gauges, horizontal bar meters, sparkline graphs, G-force vector plots, and decoded diagnostic alerts. Users can build and rearrange custom dashboards, record high-frequency telemetry sessions, clear/acknowledge logs, and replay captured CAN traces anywhere.
- **Target Audience / Persona**: 2023 Tesla Model 3 owners using an Enhance S3XY Commander who want deeper visibility into battery degradation, cell balance, thermal preconditioning, inverter power delivery, and hidden Tesla subsystem alerts on a phone mounted behind the steering wheel or a tablet docked on the console.
- **Key Value**: Bridges the gap between raw CAN-bus tools and modern automotive UI—delivering effortless `S3XY_OBD` Wi-Fi binding, zero-setup starter dashboards for all four Model 3 subsystems, deep freeze-frame fault diagnostics, and offline CAN log replay.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Connection & Replay Bar (Always Accessible)**:
   - Top status bar displays live link state: **S3XY_OBD Live (`192.168.4.1:1338`)**, frame rate (`fps` / `msg/s`), bus load, and active recording indicator.
   - Tapping the connection pill opens the **S3XY Link & Replay Drawer**, allowing the user to:
     - Bind directly to the `S3XY_OBD` Wi-Fi SSID (using `WifiNetworkSpecifier` / socket binding to `192.168.4.1` with customizable IP/port and protocol selector: Panda UDP `1338` or ELM327 TCP `35000`).
     - Switch to **Recorded CAN Replay Mode**, select from bundled 2023 Model 3 CAN logs (*Spirited Canyon Run*, *250kW V3 Supercharge Session*, *Sub-Zero Octovalve Preconditioning*, or *BMS/Inverter Fault Diagnostic Trace*) or load a custom recorded session, and control playback with play/pause, timeline scrubbing, and speed multipliers (`0.5x`, `1x`, `2x`, `4x`).
2. **Adaptive Multi-Dashboard Cockpit**:
   - Users switch seamlessly between the 4 pre-configured 2023 Model 3 dashboards (*Performance & Inverters*, *Battery & Charging*, *Thermal & Octovalve*, *Braking & G-Dynamics*) and any user-created custom dashboards via a quick-select pill bar.
   - Entering **Customize Layout Mode** lets users add, remove, resize (Compact `1x1` Digital Tile, Wide `2x1` Sparkline/Bar, or Large `2x2` Radial Dial / G-Plot), reorder, and configure min/max warning thresholds for over 28 decoded 2023 Model 3 PIDs, or create a brand-new dashboard preset.
3. **Diagnostic Alert Interrogator & Freeze-Frame Inspector**:
   - Dedicated **Diagnostics / DTC** screen filters decoded Tesla alerts by subsystem (`All`, `BMS`, `DI / Inverters`, `VCFront / Thermal`, `HVP / Charging`, `ESP / Chassis`) and status (`Active`, `Historical`, `Cleared`).
   - Selecting any alert opens a **Freeze-Frame Detail Sheet** (or side-by-side detail pane on tablets/landscape) showing the exact Tesla alert code (e.g., `BMS_a063`, `DI_a138`, `VCFRONT_a192`), severity badge, recommended service action, raw CAN arbitration ID & 8-byte hex payload, and the captured vehicle state at fault onset.
   - Includes **Interrogate Bus Now** (active UDS/CAN scan), **Clear Historical Codes & Acknowledge Alerts**, and **Export Diagnostic Report**.
4. **Telemetry Session Logger & Clear/Export Manager**:
   - One-tap **REC** button in the cockpit header starts/stops high-rate Room database logging of all active metrics and raw CAN snapshots.
   - The **Logs & Replay** tab displays saved sessions with duration, peak kW, min/max cell delta, distance, and interactive time-series charts. Users can replay any recorded session back through the live dashboard gauges, export to **CSV** or **JSON**, or **Clear Logs** (single session or bulk purge with confirmation).

### Visual Identity & Theme

- **Aesthetic Direction**: **Precision Motorsport Telemetry & Stealth Avionics** — inspired by Tesla Track Mode, Porsche GT telemetry displays, and modern race-engineering HUDs. Built Dark-First for glare-free night driving while supporting a high-contrast Daylight Cockpit mode for bright sun.
- **Color Palette & Mood**:
  - **Dark Mode (Default — Stealth Carbon)**:
    - Background (`background` / `surface`): Deep Obsidian Carbon `#090C10` and Elevated Slate `#11161F`
    - Surface Containers (`surfaceVariant`): Matte Graphite `#18202C` with subtle `1.dp` hairline borders `#263244`
    - Primary Accent (`primary`): Cyber Electric Cyan `#00E5FF` (active telemetry needles, speed, connection lock)
    - Secondary Accent (`secondary`): Regen Emerald `#00E676` (regenerative braking, balanced cells, healthy thermal states)
    - Tertiary / Power (`tertiary`): Inverter Amber-Gold `#FFB300` (peak discharge power, thermal preconditioning)
    - Critical / Fault (`error`): Track Crimson `#FF334B` (high cell delta warnings, thermal redline, active DTC faults)
  - **Light Mode (Daylight Track Mode)**:
    - Crisp Alabaster `#F4F6F9` background, Pure White `#FFFFFF` instrument cards, Deep Navy `#0B1320` numerals, Cobalt Blue `#0066FF` primary telemetry accent, and Crimson `#D91B36` alert highlights.
- **Typography & Hierarchy**:
  - Bundled locally via `font-util` into `res/font/`:
    - **Display / Speed & Peak Readouts**: `Orbitron` or `Rajdhani` (bold geometric automotive display numerals that stay rock-solid during rapid value updates).
    - **UI & Subsystem Labels**: `Space Grotesk` (crisp technical sans-serif for gauge titles, subsystem tags, and navigation).
    - **Monospace Telemetry & Hex Frames**: `JetBrains Mono` (tabular numerals for cell voltages down to `0.001 V`, CAN arbitration IDs, 8-byte hex payloads, and timestamps).
- **Component Styling & Layout (Phone vs. Tablet, Portrait vs. Landscape)**:
  - **Responsive Adaptive Grid (`BoxWithConstraints`)**:
    - **Phone Portrait**: Top S3XY connection & recording bar, horizontal dashboard preset selector, 2-column adaptive gauge grid (supporting `1x1`, `2x1`, and `2x2` span tiles), and bottom Material 3 `NavigationBar` respecting `WindowInsets.navigationBars`.
    - **Phone Landscape (Steering-Column HUD Mode)**: Left-side compact `NavigationRail`, ultra-condensed top telemetry strip, and a 4-column wide cockpit layout placing twin primary radial dials (Speed/Power & SOC/Regen) flanking central thermal/cell-balance cards.
    - **Tablet Portrait & Landscape (`Medium` / `Expanded` Window Classes)**: Permanent left `NavigationRail`, multi-column Bento telemetry grid (up to 6 columns in landscape) with an integrated live CAN frame ticker / freeze-frame inspector side-pane (Canonical List-Detail layout on the Diagnostics and Logs screens).
- **Interactive Feedback & Motion**:
  - Custom `Canvas`-rendered radial arc gauges with glowing sweep gradients, peak-hold tick markers, and smooth `animateFloatAsState` spring physics.
  - Real-time 2D G-Force Friction Circle (`Canvas`) plotting live lateral and longitudinal acceleration with a fading historical trail and peak-G readout.
  - Pulsing emerald/cyan status LED indicator when streaming CAN frames or replaying logs.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Dual-Mode S3XY Transport (Live Wi-Fi Socket + Authentic CAN Log Replay Engine)**
  - *Chosen Approach*: Implement a real UDP (`192.168.4.1:1338` Panda/CANserver frame format) and TCP (`192.168.4.1:35000` ELM327/STN format) socket client paired with Android's `ConnectivityManager.requestNetwork` (`WifiNetworkSpecifier` for `S3XY_OBD`), plus a deterministic CAN Log Replay Engine loaded with realistic 2023 Model 3 CAN frame sequences.
  - *Why*: Android phones frequently drop Wi-Fi networks that lack internet routing unless bound via `ConnectivityManager.bindProcessToNetwork` or explicit socket binding. Furthermore, when the user is indoors or configuring dashboards away from their Model 3, the Recorded CAN Log Replay Engine feeds the exact same CAN parser with real frame streams so every gauge, alert decoder, and session logger can be tested and customized at 100% fidelity.
  - *Alternatives Considered*: Static mock numbers (rejected—violates real telemetry architecture and doesn't exercise the CAN frame parser or timeline replay controls).
- **Decision 2: Local Room SQLite Database with Share-Sheet CSV/JSON Export**
  - *Chosen Approach*: Store all custom dashboard layouts, widget configurations, recorded telemetry sessions, and captured fault freeze-frames in Room (`@Entity` tables with `Flow` queries), coupled with `FileProvider` share-sheet export to `.csv` and `.json`.
  - *Why*: In-car usage on `S3XY_OBD` Wi-Fi often operates in underground garages, track paddocks, or canyons without reliable internet. Local Room storage guarantees zero-latency persistence and full privacy without requiring a cloud sign-in gate.
- **Decision 3: Hardware-Accelerated Compose `Canvas` Gauges**
  - *Chosen Approach*: Build bespoke, zero-allocation Compose `Canvas` components for radial arc gauges, cell-brick voltage histograms, Octovalve thermal flow diagrams, and the 2D G-force friction circle.
  - *Why*: Third-party charting libraries struggle with 30–60 Hz real-time CAN updates and lack automotive instrument styling (redline zones, regen/power bi-directional zero-center arcs, peak-hold needles).

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│                         UI LAYER (Jetpack Compose + M3)                           │
│  ┌─────────────────────────────────────────────────────────────────────────────┐  │
│  │ Adaptive Cockpit Shell (NavigationBar on Portrait / NavigationRail on Wide) │  │
│  └───────┬─────────────────────────┬────────────────────────┬──────────────────┘  │
│          ▼                         ▼                        ▼                     │
│  ┌──────────────────────┐ ┌──────────────────────┐ ┌───────────────────────────┐  │
│  │  Live Telemetry HUD  │ │ Alert Interrogator   │ │ Session Logger & Replay   │  │
│  │ • 4 Model 3 Presets  │ │ • Subsystem Filter   │ │ • High-Rate Recorder      │  │
│  │ • Custom Builder     │ │ • Freeze-Frame Pane  │ │ • Timeline Scrub & Speed  │  │
│  │ • Radial/Spark/G-Plot│ │ • Clear/Acknowledge  │ │ • CSV/JSON Export & Clear │  │
│  └──────────┬───────────┘ └──────────┬───────────┘ └─────────────┬─────────────┘  │
└─────────────┼────────────────────────┼───────────────────────────┼────────────────┘
              │                        │                           │
              ▼                        ▼                           ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│                     VIEWMODEL & STATE LAYER (StateFlow / MVVM)                    │
│  • TelemetryCockpitViewModel (60fps UI State, Active Dashboard, Layout Editor)    │
│  • ConnectionAndReplayState (Link Status, Bus FPS, Replay Scrub/Speed Controls)   │
│  • DiagnosticsState (Filtered Alerts, Selected Freeze-Frame, Clear/Scan Actions)  │
└─────────────┬────────────────────────────────────────────────────┬────────────────┘
              │                                                    │
              ▼                                                    ▼
┌──────────────────────────────────────────┐ ┌──────────────────────────────────────┐
│    S3XY CAN-BUS TRANSPORT & DECODER      │ │   ROOM PERSISTENCE & EXPORT LAYER    │
│  ┌────────────────────────────────────┐  │ │  ┌────────────────────────────────┐  │
│  │ S3xyWifiSocketClient               │  │ │  │ AppDatabase (Room / KSP)       │  │
│  │ • SSID: S3XY_OBD (192.168.4.1)     │  │ │  │ • DashboardPresetEntity        │  │
│  │ • UDP :1338 (Panda/CANserver)      │  │ │  │ • TelemetrySessionEntity       │  │
│  │ • TCP :35000 (ELM327 / STN)        │  │ │  │ • TelemetrySampleEntity        │  │
│  └─────────────────┬──────────────────┘  │ │  │ • VehicleAlertEntity           │  │
│  ┌─────────────────▼──────────────────┐  │ │  └───────────────┬────────────────┘  │
│  │ RecordedCanReplayEngine            │  │ │  ┌───────────────▼────────────────┐  │
│  │ • 4 Bundled 2023 Model 3 Traces    │  │ │  │ TelemetryExportManager         │  │
│  │ • User Session & CSV/LOG Importer  │  │ │  │ • CSV Time-Series Generator    │  │
│  └─────────────────┬──────────────────┘  │ │  │ • JSON Preset & Alert Exporter │  │
│  ┌─────────────────▼──────────────────┐  │ │  └────────────────────────────────┘  │
│  │ Model3CanFrameDecoder (DBC Parser) │  │ └──────────────────────────────────────┘
│  │ • IDs: 0x132 (BMS), 0x108/0x118    │  │
│  │   (Torque/RPM), 0x257 (Speed),     │  │
│  │   0x212 (HMS/Thermal), 0x401 (Cell)│  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

### Data Model & State

1. **2023 Model 3 Real-Time Telemetry State (`VehicleTelemetrySnapshot`)**:
   - **Powertrain & Performance**: `speedKph`, `netPowerKw`, `rearMotorKw`, `frontMotorKw`, `rearTorqueNm`, `frontTorqueNm`, `rearRpm`, `frontRpm`, `throttlePct`, `brakePct`, `rearInverterTempC`, `frontInverterTempC`, `statorTempC`, `zeroToSixtySec`, `currentGear` (`P`, `R`, `N`, `D`).
   - **Battery & Charging**: `usableSocPct`, `bmsSocPct`, `packVoltageV`, `packCurrentA`, `cellMinV`, `cellMaxV`, `cellDeltaMv`, `packTempMinC`, `packTempMaxC`, `nominalFullPackKwh`, `initialPackKwh`, `degradationPct`, `chargePowerKw`, `targetChargeLimitPct`.
   - **Thermal & HVAC**: `octovalvePosition`, `coolantBatteryInletC`, `coolantPowertrainInletC`, `batteryCoolantLpm`, `powertrainCoolantLpm`, `cabinTempC`, `ambientTempC`, `hvacPowerKw`, `chillerActive`.
   - **Dynamics & Braking**: `lateralG`, `longitudinalG`, `brakePressureBar`, `regenPowerKw`, `frictionBrakePct`, `steeringAngleDeg`, `flWheelKph`, `frWheelKph`, `rlWheelKph`, `rrWheelKph`.
2. **Room Database Entities**:
   - `DashboardPresetEntity`: `id`, `name`, `iconName`, `isBuiltIn`, `widgetsJson` (serialized list of widget PIDs, display style `RADIAL_GAUGE` / `BAR_METER` / `SPARKLINE_CARD` / `DIGITAL_READOUT` / `G_FORCE_PLOT` / `CELL_BRICK_MAP`, grid span, and custom warning threshold).
   - `VehicleAlertEntity`: `id`, `alertCode` (e.g., `BMS_a063`), `subsystem` (`BMS`, `DI`, `VCFRONT`, `HVP`, `ESP`), `severity` (`INFO`, `WARNING`, `CRITICAL`), `title`, `description`, `recommendedAction`, `rawCanIdHex`, `rawPayloadHex`, `freezeFrameJson`, `timestamp`, `isActive`, `isAcknowledged`.
   - `TelemetrySessionEntity` & `TelemetrySampleEntity`: Header metadata (`id`, `title`, `startTime`, `endTime`, `sampleCount`, `peakPowerKw`, `maxSpeedKph`, `maxCellDeltaMv`, `minSocPct`, `maxSocPct`, `notes`) and indexed time-series samples for instant charting, replay, and CSV/JSON export.

### Interactive Component & State Mapping

- **S3XY Wi-Fi & Replay Controller**:
  - Tapping *Connect S3XY_OBD* invokes `S3xyWifiSocketClient.connect(ip, port, protocol)`, binds the socket to the active Wi-Fi interface, and streams binary CAN frames into `Model3CanFrameDecoder`. If the user is not on `S3XY_OBD`, it surfaces a clear connection diagnostic banner with one-tap Wi-Fi network request (`WifiNetworkSpecifier`) or instant switch to **Recorded CAN Log Replay**.
  - In Replay Mode, adjusting the timeline slider or speed pills (`0.5x`, `1x`, `2x`, `4x`) immediately updates the frame emission rate and seeks to the exact timestamp in the active CAN trace.
- **Custom Dashboard Builder**:
  - Tapping *Edit Dashboard* or *+ New Dashboard* opens the interactive Widget Configurator where users can toggle any of the 28+ Model 3 metrics, pick widget visualization styles, reorder tiles, adjust warning redlines, and persist changes to Room. A *Reset to Factory Presets* action restores the 4 starter Model 3 dashboards at any time.
- **Alert Interrogation & Clear Workflow**:
  - Tapping *Scan Subsystems* sends UDS/CAN interrogation frames and updates the active fault list.
  - Filtering by subsystem or severity dynamically updates the list; selecting an alert populates the Freeze-Frame Inspector with exact pack/thermal/speed values at fault trigger.
  - Tapping *Clear & Acknowledge Faults* transmits the OBD/UDS clear request (`0x14 FF FF FF` / S3XY command), marks active historical faults as cleared in Room, and logs a confirmation toast.
- **Session Recording, Replay & Clear Logging**:
  - Tapping *REC* starts capturing decoded snapshots into Room every 200ms with a live elapsed timer and sample counter.
  - In the *Logs* screen, users can inspect session summary cards, load any saved session into the **CAN Replay Engine** to watch the gauges re-enact the drive, export `.csv` or `.json` via Android's `Intent.ACTION_SEND` (`FileProvider`), or tap *Clear Log / Purge All Logs* with confirmation.

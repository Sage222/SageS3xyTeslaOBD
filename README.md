# S3XY Cockpit — 2023 Tesla Model 3 Telemetry & Diagnostics (`S3XY_OBD`)

**S3XY Cockpit** is a real-time Android instrument cluster, CAN-bus diagnostic interrogator, and telemetry session logger built for the **2023 Tesla Model 3** using the **Enhance S3XY Commander** smart OBD-II Wi-Fi access point (`S3XY_OBD`).

It supports both **phones and tablets** in **portrait and landscape** orientations, featuring a glare-free **Stealth Carbon Dark Mode** and high-contrast **Daylight Track Mode**.

---

## Pre-Compiled APK

A pre-compiled installable APK is included directly in this repository:
- **APK Path:** [`releases/s3xy-cockpit.apk`](releases/s3xy-cockpit.apk)

You can also generate a fresh APK or App Bundle (AAB) at any time via the AI Studio **Settings -> Export / Build APK** menu or by running:
```bash
gradle assembleDebug
```

---

## Key Features

### 1. Live `S3XY_OBD` Wi-Fi Socket Connectivity
- **Default SSID**: `S3XY_OBD` (WPA2 Password: `12345678`)
- **Commander Host IP**: `192.168.4.1`
- **Protocols Supported**:
  - **Panda / CANserver Binary UDP (`:1338`)** — 500 kbps multi-bus 11-bit Tesla CAN frame streaming with `0x0F` keepalive heartbeat.
  - **ELM327 / STN TCP Bridge (`:35000`)** — ASCII hex frame streaming (`ATSP6` / `ATMA`).
- **Cellular Data Preservation**: Uses Android `ConnectivityManager` + `WifiNetworkSpecifier` network binding so your phone or tablet stays connected to mobile internet while streaming local CAN sockets over `192.168.4.1`.
- **Zero Demo / Mock Data**: Exclusively displays legitimate live telemetry, CAN frames, and alerts received from your vehicle's S3XY Commander.

### 2. Four Pre-Configured 2023 Model 3 Dashboards + Custom Layout Builder
- **Performance & Inverters**: Vehicle speed, bi-directional net pack power (`kW`), rear & front drive unit power/torque/RPM, SiC MOSFET inverter & rear stator temperatures, 0–100 km/h sprint timer, and throttle pedal map.
- **Battery, Cell Balance & Charge**: Usable vs. raw BMS State of Charge (`%`), 12-brick cell voltage histogram (`CELL MIN`, `CELL MAX`, and `mV` imbalance delta), HV pack voltage (`V`) & current (`A`), nominal full pack capacity (`kWh`), estimated degradation (`%`), and fast-charge power.
- **Thermal Loop & Octovalve**: Battery and powertrain coolant inlet temperatures (`°C`), coolant flow rates (`L/min`), 8-step Octovalve position and mode description, cabin & ambient temperatures, and heat-pump compressor power.
- **Braking, Regen & G-Force Dynamics**: 2D G-force friction circle accelerometer plot with historical trail, master cylinder brake line pressure (`bar`), regenerative braking split ratio (`%`), and steering rack angle (`°`).
- **Custom Dashboard Editor**: Create custom dashboard presets, add/remove any of the 28+ decoded 2023 Model 3 PIDs, cycle between 7 hardware-accelerated `Canvas` gauge styles, resize tiles (`1x1`, `2x1`, `2x2`), reorder widgets, or restore factory defaults.

### 3. Interactive 2023 Model 3 X-Ray Flow Visualizer
- Top-down technical blueprint of the 2023 Model 3 chassis animating real-time high-voltage power flow between the 96-group HV battery pack and dual drive units, alongside the 8-step Octovalve supermanifold coolant loop and individual corner wheel speeds.

### 4. Decoded Tesla Alert Interrogator & Freeze-Frame Inspector
- Interrogates and filters Tesla subsystem alerts across **BMS (`BMS_a`)**, **Drive Inverters (`DI_a`)**, **Thermal / VCFront (`VCFRONT_a`)**, **High Voltage / Charge (`HVP_a`)**, and **Braking / Stability (`ESP_a`)**.
- Inspects complete **Freeze-Frame Telemetry Snapshots** (speed, gear, pack V/A, min/max cell voltage, cell delta, stator/inverter temps, and Octovalve step at fault trigger) plus raw 11-bit CAN arbitration IDs and 8-byte hex payloads.
- Includes **Interrogate CAN Bus**, **Clear & Acknowledge Faults** (`0x14 FF FF FF`), and **Export Diagnostic Report**.

### 5. On-Device Room Session Logger with CSV & JSON Export
- One-tap **REC** button captures time-series vehicle telemetry to a local **Room SQLite database**.
- Review multi-channel session charts (Speed, Net Power, Stator Temp, Cell Delta), replay any recorded drive directly through the cockpit gauges, export `.csv` or `.json` via the Android Share Sheet, or purge logs.

---

## Connecting to Your Enhance S3XY Commander in the Car

1. Open the **S3XY App** on your phone, navigate to **Commander Settings**, and enable **OBD Wi-Fi / ScanMyTesla Support**.
2. Open **S3XY Cockpit** and tap the **Connection Status Pill** in the top bar.
3. Verify the SSID (`S3XY_OBD`), Password (`12345678`), IP (`192.168.4.1`), and Protocol (`Panda / CANserver UDP :1338`).
4. Tap **Bind S3XY_OBD Wi-Fi** (or **Open CAN Socket** if already connected to `S3XY_OBD` in Android Wi-Fi settings).

---

## Tech Stack
- **UI Framework**: Jetpack Compose (Material 3, Adaptive `BoxWithConstraints` for Phone/Tablet & Portrait/Landscape, Custom `Canvas` Gauges)
- **Architecture**: MVVM (`AndroidViewModel`, Kotlin Coroutines & `StateFlow`, Repository Pattern)
- **Persistence**: AndroidX Room SQLite Database (KSP) + `FileProvider` CSV/JSON Exporter
- **Networking**: Java NIO / `DatagramSocket` (UDP `:1338`), `Socket` (TCP `:35000`), and Android `ConnectivityManager` (`WifiNetworkSpecifier`)

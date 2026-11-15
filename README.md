# F-150 Head Unit

A fullscreen Android cluster for a **9-inch 1280×720 Android 14 head unit** installed in a
Ford F-150. The screen is split into two fixed panels:

| Left third (1/3) | Right two-thirds (2/3) |
|---|---|
| **Truck information**, fed from the CAN bus: top bar with time / compass heading / outside temperature, a large speed readout over a dark truck backdrop, door/hood/tailgate ajar alerts with a top-down truck schematic, and a bottom strip with fuel level, remaining range and coolant temperature. | **Projection panel placeholder** for the unit's CarPlay/Android Auto app. The panel is fully configurable — pick in Settings which installed app it represents; tapping the panel (or auto-launch on boot) opens it. When nothing is configured it shows the "CarPlay / Android Auto — Not Connected" placeholder. |

## Screens

Not connected (default):

```
┌─ 1/3 ────────┬─ 2/3 ────────────────────────────┐
│ 10:34 AM  NW  72°F │                            │
│      Speed              │        📱  ···  ▲       │
│        45               │                            │
│       MPH               │  CarPlay / Android Auto    │
│                    │      Not Connected        │
│ ┌───┐ Driver Door  │                            │
│ │🛻 │ Open              │ Please connect your phone  │
│ └───┘                    │     to get started.      │
│ ⛽E▮▮▮▮▯F  RANGE  ⚙C▮▮▯H │       [ ⚙ Settings ]     │
└────────────────────┴────────────────────────────┘
```

## Build

Requirements: **JDK 17+**, Android SDK with Platform **34**, Gradle 8.9 (wrapper included).

```
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in **Android Studio** (Ladybug or newer) and press *Run*.

The app has **zero third-party dependencies** — only the Android framework and the Kotlin
standard library — so builds work offline apart from the Gradle/AGP plugins.

## Install on the head unit

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app declares `HOME` + `LAUNCHER`, so it can be chosen as the head unit's launcher and
boot straight into this UI. It forces landscape, stays fullscreen/immersive, and keeps the
screen on.

## CAN bus integration

```
CanDataSource ──(CanFrame)──▶ VehicleRepository ──▶ TruckInfoController ──▶ views
     ▲                            ▲
     │                     CanSignalDecoder (ExampleHsCanDecoder)
     └── SimulatedCanDataSource (bench default) · SlcanCanDataSource (real bus)
```

**Working hardware path.** `SlcanCanDataSource` streams from a USB **SLCAN/Lawicel**
adapter (CANable in `slcan` mode, CH341/CP210x/FTDI serial CAN dongles, …):

- `UsbAcmTransport` — CDC-ACM link over Android USB host (endpoint discovery, interface
  claim, SET_LINE_CODING / SET_CONTROL_LINE_STATE handshake, bulk reads/writes)
- `SlcanCodec` — opens the channel at **500 kbit/s** (`C` → `S6` → `O`) and parses
  standard/extended frames (`t…CR` / `T…CR`)
- `SlcanCanDataSource` — adapter discovery, runtime USB permission prompt, reader thread,
  and a reconnect loop so replugging (or momentary bus errors) self-heals. Plugging a
  known adapter also offers to open this app via a `USB_DEVICE_ATTACHED` filter.

Live link status ("waiting for adapter", "permission", "connected", error) is reported in
**Settings → Vehicle data → CAN data source**.

**Signal decoding.** Frames are translated by `ExampleHsCanDecoder`. Its IDs are a clean,
documented mapping — **remap them to your truck's DBC** (or OBD-II proxy) when wiring the
real bus:

| ID | Contents |
|---|---|
| `0x201` | byte0 — speed, 1 km/h per bit |
| `0x214` | byte0 — ambient °C + 40 · byte1 — coolant °C + 40 |
| `0x2A0` | byte0 bitfield — driver/passenger/rear L/R door, hood, tailgate |
| `0x2F0` | byte0 — fuel % · bytes1-2 LE — range, miles |
| `0x300` | bytes0-1 LE — compass heading, 0.5° per bit |

Different adapter protocol? Implement `CanTransport` and swap one line in
`SlcanCanDataSource.sessionLoop()` — discovery, permission and reconnect logic all stand.

**Bench mode.** `SimulatedCanDataSource` synthesizes the same frames a real adapter would,
so the whole decode→UI pipeline runs without hardware (a small `DEMO` badge appears on the
speed readout). It is the default until you switch to the USB source.

## Configuring the projection panel

1. Open the panel's **Settings** chip → **Projection app**.
2. Pick any launchable app installed on the unit (the CarPlay/Android Auto client,
   navigation, media, …) — its icon and name then appear on the panel, and tapping the
   panel launches it. Select *None* to restore the "Not Connected" placeholder.
3. Optional: enable **Auto-open projection app on start** — the app also opens
   automatically the moment a projection device connects.

**Connection awareness.** `ConnectionMonitor` watches USB accessory events: wired Android
Auto presents the phone as a USB accessory to the head unit, so the panel switches between
*Waiting for device…* and *Device connected* live (and auto-opens the configured app on
the connect edge when enabled). CarPlay dongle boxes manage the phone session inside their
own app, so there the panel stays a tap-to-open tile.

> Note: stock Android can't embed another app's UI inside ours, so the panel is a launcher
> tied to connection state by design. App visibility is handled via a `<queries>` LAUNCHER
> declaration — no `QUERY_ALL_PACKAGES` permission needed.

Other settings: speed unit (MPH/KM/H), temperature unit (°F/°C).

## Project layout

```
app/src/main/java/com/f150headunit/app/
├── F150Application.kt          # boots the CAN → state pipeline
├── MainActivity.kt             # fullscreen split, immersive, keep-screen-on
├── can/                        # CanTransport · UsbAcmTransport (CDC-ACM) · SlcanCodec
│                               # SlcanCanDataSource (real bus) · SimulatedCanDataSource
│                               # ExampleHsCanDecoder
├── data/VehicleRepository.kt   # live VehicleState, fan-out to UI
├── model/                      # VehicleState · DoorState · Compass · Units
├── projection/ConnectionMonitor.kt  # USB-accessory connection detection
├── settings/                   # Settings (prefs), SettingsActivity, AppPickerActivity
└── ui/
    ├── TruckInfoController.kt      # left panel logic
    ├── ProjectionPanelController.kt# right panel logic (connection-aware launcher)
    └── views/                      # SegmentGaugeView · TruckOutlineView (custom-drawn)
```

Target: `minSdk 29`, `target/compileSdk 34` (Android 14).

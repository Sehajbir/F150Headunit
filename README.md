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
     └── SimulatedCanDataSource (default) · UsbCanDataSource (hardware hook)
```

- **Default: simulation.** `SimulatedCanDataSource` synthesizes the same frames a real
  adapter would, so the whole decode→UI pipeline runs on the bench (a small `DEMO` badge
  appears on the speed readout).
- **Real hardware:** implement `UsbCanDataSource` (there's a step-by-step TODO in the
  file: permission → open endpoints → set 500 kbit/s → read loop emitting `CanFrame`s),
  then remap the IDs in `ExampleHsCanDecoder` to your actual bus/DBC:

| ID | Contents |
|---|---|
| `0x201` | byte0 — speed, 1 km/h per bit |
| `0x214` | byte0 — ambient °C + 40 · byte1 — coolant °C + 40 |
| `0x2A0` | byte0 bitfield — driver/passenger/rear L/R door, hood, tailgate |
| `0x2F0` | byte0 — fuel % · bytes1-2 LE — range, miles |
| `0x300` | bytes0-1 LE — compass heading, 0.5° per bit |

Choose the source at runtime under **Settings → Vehicle data → CAN data source**.

## Configuring the projection panel

1. Open the panel's **Settings** chip → **Projection app**.
2. Pick any launchable app installed on the unit (the CarPlay/Android Auto client,
   navigation, media, …) — its icon and name then appear on the panel, and tapping the
   panel launches it. Select *None* to restore the "Not Connected" placeholder.
3. Optional: enable **Auto-open projection app on start**.

> Note: stock Android can't embed another app's UI inside ours, so the panel is a
> launcher/placeholder by design. App visibility is handled via a `<queries>` LAUNCHER
> declaration — no `QUERY_ALL_PACKAGES` permission needed.

Other settings: speed unit (MPH/KM/H), temperature unit (°F/°C).

## Project layout

```
app/src/main/java/com/f150headunit/app/
├── F150Application.kt          # boots the CAN → state pipeline
├── MainActivity.kt             # fullscreen split, immersive, keep-screen-on
├── can/                        # CanFrame · CanDataSource · Simulated · USB skeleton · decoder
├── data/VehicleRepository.kt   # live VehicleState, fan-out to UI
├── model/                      # VehicleState · DoorState · Compass · Units
├── settings/                   # Settings (prefs), SettingsActivity, AppPickerActivity
└── ui/
    ├── TruckInfoController.kt      # left panel logic
    ├── ProjectionPanelController.kt# right panel logic
    └── views/                      # SegmentGaugeView · TruckOutlineView (custom-drawn)
```

Target: `minSdk 29`, `target/compileSdk 34` (Android 14).

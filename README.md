<div align="center">

# ⚡ VoltHalt

### Smart Battery Charge & Low Alarm for Android

**Protect battery health. Stop overcharging. 100% Offline & Private.**

[![Release](https://img.shields.io/github/v/release/im-atp/VoltHalt?color=F5A623&label=Download&logo=android)](https://github.com/im-atp/VoltHalt/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen?logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blueviolet?logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Privacy: 100% Offline](https://img.shields.io/badge/Privacy-100%25%20Offline%20(No%20Internet)-success?logo=googlechrome)](https://github.com/im-atp/VoltHalt)
[![Stars](https://img.shields.io/github/stars/im-atp/VoltHalt?style=social)](https://github.com/im-atp/VoltHalt/stargazers)

</div>

---

##  What is VoltHalt?

**VoltHalt** is a light-weight, open-source Android app that monitors your battery level in the background and alerts you with a loud alarm or a custom **Text-to-Speech voice message** when your battery reaches a target charge or drops too low. 

Unlike built-in manufacturer limits, VoltHalt works on **any Android 8.0+ device** without root, features a convenient **Quick Settings (QS) Tile**, and operates **100% offline** with zero data collection or internet permission.

>  **Battery Health Tip:** Keeping lithium-ion batteries between **20% and 80%** significantly extends their overall lifespan. VoltHalt automates this for both charging and discharging.

---

##  100% Offline & Privacy First (Zero Internet Access)

VoltHalt is built with absolute privacy in mind:

-  **NO `android.permission.INTERNET` requested or required.**
-  **Zero Data Collection**: No telemetry, no analytics, no ad networks, no tracking scripts.
-  **Zero External Network Calls**: Everything runs completely locally on your device.
-  **Maximum Battery & RAM Efficiency**: No background network wake-ups or server polling.

---

##  Full Feature List

VoltHalt is packed with essential features to keep your battery healthy while giving you total control:

| Feature | Description |
|---------|-------------|
|  **Max Battery Charge Alarm** | Set any target percentage (1–100%) to trigger an alarm while charging. |
|  **Low Battery Alarm** | Set a low battery threshold (5–50%) to alert you when unplugged and battery drops low. |
|  **Quick Settings (QS) Tile** | Toggle monitoring directly from your Android notification shade with a single tap. |
|  **Text-to-Speech (TTS) Voices** | Pick between system alarm ringtones OR custom spoken voice messages (e.g. *"Battery charged to 80%, please unplug!"*). |
|  **Full-Screen Lock Overlay** | Full-screen alert screen (`AlarmActivity`) wakes your device even when locked. |
|  **Smart Auto-Dismiss** | Max charge alarm automatically stops the moment you unplug the charger. |
|  **Interactive 5s Alarm Preview** | Test your saved ringtone, TTS speech message, volume, and vibration with a single tap before activating. |
|  **Custom Volume & Vibration** | Independent volume sliders (0–100%) and vibration toggles for both max and low alarms. |
|  **Material 3 Theme Support** | Switch effortlessly between **System Default**, **Light Theme**, and **Dark Theme**. |
|  **Boot Persistence** | Automatically resumes battery monitoring after device reboots (`RECEIVE_BOOT_COMPLETED`). |
|  **Direct In-App APK Sharing** | Share the app APK file directly with friends via `FileProvider` (`content://` URI) without external links. |
|  **Onboarding Setup Wizard** | Guided setup for battery optimization exemptions, notification permissions, and full-screen intent access. |

---

##  Quick Settings (QS) Tile Feature

VoltHalt includes a native **Android Quick Settings (QS) Tile** for fast access without needing to launch the app UI:

-  **One-Tap Toggle**: Swipe down your Android notification shade and tap the **VoltHalt** tile to instantly turn battery monitoring on or off.
-  **Real-Time Sync**: Tile state updates automatically to reflect whether active background monitoring is running (`"Alarm On"` / `"Alarm Off"`).
-  **Long-Press Shortcut**: Long-pressing the VoltHalt Quick Settings tile directly opens the app settings screen.
-  **How to Add**:
  1. Swipe down twice from the top of your screen to expand Quick Settings.
  2. Tap the **Edit (pencil)** icon.
  3. Scroll down to find **VoltHalt**, drag it into your active tiles, and tap Done.

---

##  App Showcase

<table border="0">
  <tr>
    <!-- Left Column: 3 Standard Screens Stacked Vertically -->
    <td width="40%" align="center" valign="top">
      <img src="https://github.com/user-attachments/assets/f488f14a-6bcf-4a84-988b-7b0889a0d21f" width="90%" alt="Screen 1" />
      <br/><br/>
      <img src="https://github.com/user-attachments/assets/09da40cc-bd1b-4e1f-8d8c-450a40e568c3" width="90%" alt="Screen 2" />
      <br/><br/>
      <img src="https://github.com/user-attachments/assets/3364be52-2187-4d4d-81a2-cb0a22ff7800" width="90%" alt="Screen 3" />
    </td>
    <!-- Right Column: 1 Long Scrolling Screen -->
    <td width="60%" align="center" valign="top">
      <img src="https://github.com/user-attachments/assets/ae11a7da-b821-4655-9a00-8705b3eb3da6" width="95%" alt="Full Feature Walkthrough" />
    </td>
  </tr>
</table>

##  Download

<div align="center">

### [ Download Latest APK](https://github.com/im-atp/VoltHalt/releases/latest)

</div>

> **Note:** Since this APK is distributed directly on GitHub, enable **"Install from unknown sources"** in your Android settings if prompted.
>
> **Settings → Security → Install unknown apps** → Allow for your browser or file manager.

---

##  Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) + [Material 3](https://m3.material.io/)
- **Architecture**: Single-Activity Compose with DataStore state management
- **Persistence**: [Preferences DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- **Background Service**: Android Foreground Service (`BatteryService`) with `BroadcastReceiver`
- **System Integration**: `TileService` for Quick Settings & `TextToSpeech` engine for voice alerts
- **Minimum SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 14 (API 34)

---

##  Build from Source

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 / JDK 8+
- Android SDK 34

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/im-atp/VoltHalt.git

# 2. Open in Android Studio
# File → Open → select the VoltHalt folder

# 3. Let Gradle sync complete, then build
# Build → Make Project   (Ctrl+F9)

# 4. Run on your device or emulator
# Run → Run 'app'   (Shift+F10)
```

### Build release APK
```bash
# In Android Studio:
# Build → Generate Signed Bundle / APK → APK → follow the wizard
```

---

##  Project Structure

```
VoltHalt/
├── app/
│   └── src/main/
│       ├── java/com/im_atp/volthalt/
│       │   ├── MainActivity.kt          # App entry point & setup routing
│       │   ├── AlarmActivity.kt         # Full-screen lock screen alarm overlay
│       │   ├── BatteryService.kt        # Background sticky broadcast monitoring service
│       │   ├── AlarmPlayer.kt           # Ringtone audio & Text-to-Speech playback manager
│       │   ├── AlarmTileService.kt      # Android Quick Settings (QS) Tile handler
│       │   ├── PreferencesManager.kt    # DataStore Preferences for settings & TTS phrases
│       │   └── ui/
│       │       ├── screens/
│       │       │   ├── MainScreen.kt    # Home dashboard, battery level, alarm cards & preview
│       │       │   ├── SettingsScreen.kt# Max/Low alarm targets, volume, TTS, themes & setup
│       │       │   └── SetupScreen.kt   # Permission onboarding wizard
│       │       └── theme/
│       │           └── Theme.kt         # Material 3 dark/light design system
│       ├── res/                         # Vector icons, string resources & XML configurations
│       └── AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

##  Permissions Explained

VoltHalt requests only what is essential for reliable alarm functionality:

| Permission | Why it's needed |
|-----------|----------------|
| `FOREGROUND_SERVICE` | Keeps monitoring service alive in background without Android killing it |
| `VIBRATE` | Provides haptic vibration during alarm playback |
| `POST_NOTIFICATIONS` | Shows persistent monitoring status in notification shade (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Restores battery alarm monitoring automatically after phone restarts |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ensures timely alarm triggers on aggressive OEM battery savers |
| `WAKE_LOCK` | Wakes the CPU briefly so the alarm fires instantly at target level |
| `USE_FULL_SCREEN_INTENT` | Displays the full-screen alarm screen over the lock screen (Android 14+) |

>  **Zero Internet Permission:** `android.permission.INTERNET` is **NOT** included in the app manifest. VoltHalt cannot send data anywhere.

---

##  Contributing

Contributions, feature requests, and bug reports are welcome!

1. **Fork** this repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit your changes: `git commit -m 'feat: add amazing feature'`
4. Push to your branch: `git push origin feature/amazing-feature`
5. Open a **Pull Request**

---

##  Found a Bug?

[Open an Issue](https://github.com/im-atp/VoltHalt/issues/new) and include:
- Your Android version & Device model
- Detailed steps to reproduce
- Expected vs actual behavior

---

##  License

```
MIT License

Copyright (c) 2026 im_atp

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<div align="center">

**If VoltHalt helped protect your battery, give it a ⭐ — it helps the project grow!**

</div>

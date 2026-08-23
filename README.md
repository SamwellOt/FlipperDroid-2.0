# FlipperDroid

**FlipperDroid** is an Android application developed in Kotlin (Jetpack Compose), inspired by the well-known **Flipper Zero** device. It brings a broad set of wireless and hardware security-testing tools to Android smartphones, leveraging their native radios (NFC, BLE, Wi-Fi, IR) and, where available, root.

<p align="center">
  <img src="https://github.com/Jeremiznoo/FlipperDroid/blob/main/src/main/ic_launcher-playstore.png">
</p>

---

## Overview

FlipperDroid turns a modern Android phone into a flexible, portable alternative to a Flipper Zero for the protocols a phone *can* actually speak. It follows one guiding principle: **be honest about capability** — features that need root, an IR blaster, monitor mode or external hardware check for it and say so, instead of silently doing nothing.

---

## Features

### NFC / RFID (13.56 MHz)
- **Mifare Classic** read with UID + full dump, and a **key dictionary attack** (common keys).
- **NDEF** read and **write** (text / URL), tag formatting.
- **UID cloning** to "magic" cards.
- Export dumps as plain text or as a Flipper-compatible **`.nfc`** file.
- **EMV** contactless bank-card reading (PAN masked) and **host-card emulation (HCE)**.

### Bluetooth LE
- **BLE Spam** — Apple Continuity, Samsung EasySetup, **Microsoft SwiftPair**, **Google Fast Pair**.
- **BLE Scanner + GATT Explorer** — discover services/characteristics, read / write / subscribe.
- **BLE Beacon** emitter — iBeacon, Eddystone-URL, custom manufacturer data.
- **BLE Keyboard (HID)** — the phone acts as a wireless Bluetooth keyboard to a paired PC/tablet.

### Wi-Fi
- Network scanning with signal / channel / security.
- **WiFi Analyzer** — 2.4/5 GHz channel-occupancy graph, plus **MAC spoofing** (root).
- **Wardriving** — combines Wi-Fi scan + GPS and exports a **Wigle/WiGLE CSV**.
- **WiFi Deauther** — real deauth needs root + a monitor-mode/injection-capable chipset (rare on phones); the app reports the missing prerequisite honestly.
- **Evil Portal** — a local hotspot + built-in HTTP server serving a captive login page and capturing submitted credentials (authorized testing only).

### Network tools
- Bundled **`nmap`** (root), plus pure-socket **ping / port scan / DNS / traceroute**, **Wake-on-LAN**, **ARP table**, and **subnet ping sweep**.

### Infrared
- A simple universal TV remote (NEC) with a numeric keypad.
- An **`.ir` code-database remote**: 13 bundled remotes (LG, Samsung, Sony, TCL, Toshiba, Vizio, Philips, JVC, Panasonic, Roku, Apple TV, Epson projector, generic Coolix A/C), support for **importing Flipper-IRDB `.ir` files**, and a "power sweep". Encoders: NEC, NECext, Samsung32, Sony SIRC, RC5, JVC, Kaseikyo (Panasonic), Apple, Coolix. *(Requires a device with an IR blaster.)*

### BadUSB
- A legacy USB-Host attempt, and a real **USB Gadget** implementation (**root** + kernel HID gadget at `/dev/hidgX`) driven by a **DuckyScript** parser.

### Utilities
- **Skimmer Detector** — flags cheap serial Bluetooth modules (HC-05/HC-06, etc.) used in card skimmers.
- **QR / Barcode Scanner** (camera).
- **Password Generator** (cryptographically secure) with QR output.
- **2FA / TOTP Vault** — RFC 6238 codes, `otpauth://` import, secrets stay on-device.
- **Flipper File Viewer** — parse and inspect `.nfc`, `.sub`, `.ir` files.
- **Logs** — central, exportable activity log.
- Customizable settings (theme, keep-screen-on, …).

> Sub-GHz (300–900 MHz), 125 kHz LF RFID, iButton/1-Wire and GPIO are **not** possible on a phone's internal radios — they need external hardware. See [`ANDROID_CAPABILITIES.md`](ANDROID_CAPABILITIES.md).

---

## Requirements

- Android 10 or newer recommended (`minSdk` 24; some modules gate themselves to a higher API).
- **Root** for: `nmap`, Wi-Fi deauth, MAC spoofing, and USB-Gadget BadUSB.
- Specific hardware for some modules: an **IR blaster** (Infrared), USB Host (legacy BadUSB), BLE, NFC.
- Tested with LineageOS on a rooted Google Pixel 7.

---

## Usage and Legal Disclaimer

**This application is intended strictly for educational, research, and legal penetration-testing purposes.**
Misuse of this software for unauthorized access, emulation, or attacks on third-party devices or systems is strictly prohibited. The developer is not responsible for any consequences resulting from illegal use. The app shows a legal-acceptance gate on first launch.

---

## Building

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew installDebug           # build + install on a connected device
./gradlew testDebugUnitTest      # run JVM unit tests (no device needed)
```

`local.properties` must point at your Android SDK (`sdk.dir`). See [`CLAUDE.md`](CLAUDE.md) for architecture notes and [`ROADMAP.md`](ROADMAP.md) for what's implemented and what's next.

---

## Project Structure

```
FlipperDroid/
├── src/main/
│   ├── java/com/example/flipperdroid/
│   │   ├── Main.kt          # MainActivity + Compose NavHost
│   │   ├── view/            # one *Screen.kt per feature
│   │   ├── viewmodel/       # one ViewModel per feature (StateFlow)
│   │   ├── nfc/             # EMV reader, Mifare utils, HCE service
│   │   ├── model/           # BLE advertisement engine
│   │   ├── infrared/        # IR protocol encoders + .ir parser
│   │   ├── flipper/         # .nfc / .sub file readers-writers
│   │   ├── security/        # TOTP generator
│   │   ├── util/            # AppLog
│   │   └── ui/theme/        # Compose theme
│   ├── assets/              # nmap binaries, infrared/*.ir, legal texts
│   └── AndroidManifest.xml
├── src/test/                # JVM unit tests (LogicUnitTest)
├── build.gradle.kts         # Kotlin DSL build configuration
└── gradlew / gradlew.bat    # Gradle wrapper
```

## License

This project is released under the **MIT License**. See the [`LICENSE`](LICENSE) file for details.

---

## Acknowledgments

- The Flipper Zero project for functional inspiration, and the community **Flipper-IRDB** for IR code references.
- LineageOS and the Android open-source ecosystem.
- F-Droid for supporting free and open software distribution.

---

## Screenshots

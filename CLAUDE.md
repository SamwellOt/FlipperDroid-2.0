# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

FlipperDroid is a single-module Android app (Kotlin + Jetpack Compose) that turns a phone into a Flipper Zero–style wireless/hardware security toolkit. Modules (all reachable as tiles on `HomeScreen`):

- **NFC** — Mifare Classic read, key **dictionary attack**, NDEF read/write, UID clone (magic cards), dump export + save as Flipper `.nfc`.
- **EMV** — contactless bank-card read (IsoDep) + host-card emulation (HCE).
- **BLE** — advertisement **spam** (Apple/Samsung/Microsoft SwiftPair/Google Fast Pair), **scanner + GATT explorer** (read/write/notify), **beacon** emitter (iBeacon/Eddystone/custom), **HID keyboard** (phone as a wireless BLE keyboard).
- **Wi-Fi** — scan, **deauther** (honest root/hardware capability check), **analyzer** (channel graph) + MAC spoof (root), **wardriving** (GPS → Wigle CSV), **Evil Portal** (LocalOnlyHotspot + credential-capturing HTTP server).
- **Network tools** — bundled `nmap` (root), plus pure-socket ping/portscan/DNS/traceroute, Wake-on-LAN, ARP table, ping sweep.
- **Infrared** — a simple universal remote **and** an `.ir` code-database remote (NEC/NECext/Samsung32/SIRC/RC5/JVC/Kaseikyo/Apple/Coolix), importing Flipper-IRDB `.ir` files + power sweep.
- **BadUSB** — legacy USB-Host attempt **and** a real **USB Gadget** version (root, writes HID reports to `/dev/hidgX`) with a DuckyScript parser.
- **Skimmer detector**, **QR scanner** (camera), **password generator** (SecureRandom), **2FA/TOTP vault** (RFC 6238), **Flipper file viewer** (`.nfc`/`.sub`/`.ir`), central **Logs**, **Settings**, About.

Several modules require **root** and specific hardware (rooted Pixel 7 on LineageOS is the reference target). The guiding principle when extending: **be honest about capability** — most modules check for root/emitter/tooling and report clearly instead of silently doing nothing (see the Wi-Fi deauther and BadUSB-root as the pattern).

## Build & run

Gradle Kotlin DSL, single module rooted at the repo root (there is **no** `app/` subdirectory — `build.gradle.kts` and `src/` live at the top level). Use the wrapper:

```bash
./gradlew testDebugUnitTest      # JVM unit tests (src/test) — no device needed
./gradlew assembleDebug          # build debug APK -> build/outputs/apk/debug/
./gradlew installDebug           # build + install on connected device/emulator
./gradlew connectedAndroidTest   # instrumented tests, needs a device (src/androidTest)
./gradlew lint                   # Android lint
./gradlew dokkaHtml              # generate API docs (Dokka is configured)
```

Run a single unit test class/method:

```bash
./gradlew testDebugUnitTest --tests "com.example.flipperdroid.LogicUnitTest.totp_rfc6238_vectors"
```

- **`LogicUnitTest`** (`src/test`) covers the pure-logic cores (IR encoders, `.ir`/`.nfc`/`.sub` parsers, TOTP, DuckyScript keycodes, Mifare hex, Wi-Fi channel math, skimmer heuristic). Prefer adding tests here — it runs without a device or the full Android SDK.
- SDK: `compileSdk`/`targetSdk` 35, `minSdk` 24, JVM target 11. `applicationId`/`namespace` = `com.example.flipperdroid` (still the default — change before shipping).
- `local.properties` must point at a local Android SDK (`sdk.dir`).
- Dependency versions are split: some come from the version catalog (`gradle/libs.versions.toml`, `libs.*`) and some are hardcoded inline in `build.gradle.kts` (lifecycle, navigation-compose, material-icons-extended, `com.google.zxing:core`, `com.journeyapps:zxing-android-embedded`). When bumping versions, check both places.

## Architecture

MVVM with Compose. Single Activity, string-route navigation.

- **`Main.kt` (`MainActivity`)** — the single entry point + `NavHost` inside `AppNavigation()`. To add a screen: create the `view/` composable (+ a `viewmodel/` if it has state), and add a `composable("route")` block, plus a `FeatureItem` tile in `HomeScreen`.
- **Two ViewModel-ownership patterns coexist** (don't "fix" this into one):
  - ViewModels that must receive **hardware intents** or be shared are declared in `MainActivity` via `by viewModels()`: `nfcViewModel`, `emvReaderViewModel`, `badUsbViewModel`, `bluetoothViewModel`, `bleSpamViewModel`, `themeViewModel`. `MainActivity` routes NFC foreground-dispatch tags to `NfcViewModel.onTagScanned` **and** `EmvReaderViewModel.readCard` (each ignores tags of the wrong tech), and USB-attach intents to `BadUsbViewModel.connectUsb`.
  - Everything else constructs its ViewModel with the default Compose `viewModel()` **inside** the `composable(...)` block (WiFi analyzer/deauther, wardriving, skimmer, beacon, TOTP, IR remote, Evil Portal, HID keyboard, root BadUSB, …).
- **API-gated screens**: `HidKeyboardScreen` (BluetoothHidDevice, API 28+) and `EvilPortalScreen` (LocalOnlyHotspot, API 26+) check `Build.VERSION.SDK_INT` first and render `UnsupportedScreen` (defined in `HidKeyboardScreen.kt`) below their floor, because `minSdk` is 24. Follow this pattern for any newer-API-only class referenced in a ViewModel field.

Package layout under `src/main/java/com/example/flipperdroid/`:

- `view/` — one Compose screen per feature (`*Screen.kt`).
- `viewmodel/` — one `ViewModel`/`AndroidViewModel` per feature; state via `StateFlow`. This is where the real work happens (root shells, hardware APIs, background coroutines).
- `nfc/` — `EmvCardReader`, `MifareClassicUtils` (incl. `COMMON_KEYS` dictionary), `EmvCardEmulationService` (a manifest-registered `HostApduService`).
- `model/` — the **BLE advertisement engine** (see below). Subpackages use backtick-escaped names: `` `class` ``, `` `interface` ``, `` `object` ``, `enums`.
- `infrared/` — protocol-agnostic IR: `IrProtocols` (encoders → µs patterns for `ConsumerIrManager`), `IrFile` (Flipper `.ir` parser, parsed + raw). Bundled remotes live in `assets/infrared/*.ir`.
- `flipper/` — `FlipperNfc` / `FlipperSub` readers-writers for Flipper `.nfc` / `.sub` files.
- `security/` — `TotpGenerator` (RFC 6238 TOTP + Base32).
- `util/` — `AppLog`, the app-wide log surfaced by `LogsScreen`.
- `ui/theme/` — `FlipperDroidTheme`; dark mode is a runtime toggle driven by `ThemeViewModel.isDarkMode` (persisted).

### BLE advertisement engine (`model/`)
`IAdvertisementService` has two implementations chosen at runtime by API level via `BluetoothHelpers.getAdvertisementService`: `ModernAdvertisementService` (extended advertising, API 26+) and `LegacyAdvertisementService`. `AdvertisementSetQueueHandler` cycles through `AdvertisementSet`s and notifies `IAdvertisementServiceCallback`s. Payloads come from the `*AdvertisementSetGenerator` objects — Apple Continuity, Samsung EasySetup, **SwiftPair (Microsoft)**, **FastPair (Google)** — selected in `BleSpamViewModel` by the `BleSpamBrand` enum. `AdvertiseData.build()` supports both manufacturer data and service data (FastPair uses service data UUID 0xFE2C).

### Root & native code
Root-dependent modules shell out through `su -c` and **report honestly when root/tooling is missing**:
- **Network tools** ship a prebuilt `nmap` + `.so`s in `assets/nmap/`; at runtime copied to `/data/local/tmp/`, `chmod 755`, then run with `LD_LIBRARY_PATH`/`RESOLV_CONF`/`NSSWITCH_CONF`.
- **Wi-Fi deauth** checks for root + an injection tool (`aireplay-ng`/`mdk4`) and explains that most phone Wi-Fi chipsets can't do monitor-mode/injection.
- **MAC spoof** uses `ip link set … address …`.
- **BadUSB (root gadget)** requires a kernel HID gadget at `/dev/hidgX`; it opens `su -c "cat > /dev/hidgX"` and streams 8-byte HID reports built by the DuckyScript engine (`RootBadUsbViewModel.charToKeycode` is the shared US-keyboard map, reused by the BLE HID keyboard).

### IR code database
`IrProtocols.encode(protocol, address, command)` returns `(frequencyHz, IntArray µs pattern)`. Timing constants were verified against SB-Projects / IRremoteESP8266 (see `ROADMAP.md`). `.ir` files store `address`/`command` as little-endian hex bytes; `IrFile.parse` handles both `parsed` (encoded via `IrProtocols`) and `raw` (transmitted directly). NEC-family brands and the added Roku/Epson use `NECext` (16-bit address + command + inverted command — **not** a 16-bit command).

## Conventions & gotchas

- **Doc/inline comments are largely in French**; identifiers and log tags are English. Match the surrounding language when editing a file.
- BroadcastReceivers register for **protected system broadcasts** (Wi-Fi scan results, Bluetooth `ACTION_FOUND`), so they don't need the `RECEIVER_EXPORTED` flag even on target 35.
- Assets under `src/main/assets/` (`nmap/` binaries, `infrared/*.ir`, `legacy/*.txt` legal texts) are loaded by path at runtime — keep paths in sync with the code.
- `NfcViewModel` keeps the last `Tag` so dictionary-attack / NDEF-write / UID-clone work after a scan; NFC I/O runs on `Dispatchers.IO` (blocking calls would ANR on the main thread).
- This is offensive-security tooling for **authorized testing/research only** (see README + in-app legal gate). Keep that framing, and keep the honest-capability-check pattern when adding radio features a phone can't really do.

# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

Offensive-capability accuracy, continuing the `AUDIT_REPORT.md` pass.

### BLE spam
- BLE advertising now stays in **legacy** mode whenever the payload fits in the 31-byte limit, instead of always using extended advertising on capable chips (e.g. Pixel 7). Most targets (Windows Swift Pair, iOS/macOS Continuity, Android Fast Pair) only listen to legacy advertising by default, so short spam frames sent as extended were effectively invisible — this restores their reach. Extended advertising is used only when a frame genuinely overflows 31 bytes, and that fallback is logged. Payload size is computed by a pure, unit-tested estimator (`model/object/AdvertisementPayload`).

### Wi-Fi recon
- Network security classification recognises **WPA3 (SAE)**, **WPA2/WPA3 transition**, **Enhanced Open (OWE)**, **enterprise (EAP/802.1X)** and **WPS**, instead of only matching the literal "WPA3". A WPA3-only AP (`[RSN-SAE-CCMP]`) was previously reported as *Open*; OWE was reported as unencrypted *Open*. Logic extracted to a pure, unit-tested `util/WifiSecurity` classifier; the deauther list shows the enriched label with Enterprise/WPS flags.

### Tests
- New JVM suites `BleLogicTest` (advertising size / legacy-vs-extended) and Wi-Fi security cases in `NetLogicTest`.

## [2.2.0] - 2026-11-05

Quality, reliability and security pass driven by `AUDIT_REPORT.md`.

### Security & privacy
- Root shell commands (nmap, tcpdump, MAC spoof) are built with `util/ShellArgs`: user input is validated and quoted, which closes a root command-injection path. nmap options that read/write files or run scripts are refused.
- TOTP secrets and captured HCE APDU responses are encrypted with an Android Keystore key (`util/SecureStore`); plaintext data is migrated.
- Backups disabled (`allowBackup="false"`) with explicit exclusions; `FLAG_SECURE` on the 2FA and password screens; sensitive clipboard flag.
- Netcat HTTP server only serves a dedicated `served/` folder, streams files, enforces timeouts and a strict path check.
- Evil Portal: bounded request size and header count, client timeouts, passwords no longer written to the app log.
- Full PAN no longer exposed by `EmvCardData.toString()`; per-APDU logging removed from the HCE service.

### Fixes
- `java.time` now works on Android 7 (core-library desugaring); several screens crashed there.
- CI no longer republishes the `v2.0.0` tag on every push; signed releases only on `v*` tags from repository secrets; lint runs and reports are uploaded.
- ONVIF HTTP calls are no longer blocked by the cleartext policy; WS-Discovery probe uses the correct ONVIF network namespace.
- IR: repeat bursts keep mark/space alignment (Sony SIRC, RC5); "Sent" is only shown when an IR emitter exists; power sweep deduplicates toggles.
- NFC: launch intent processed once (no ghost clone after rotation), Ultralight/NTAG clone no longer writes lock/config/password pages, partial dumps stay aligned, accurate `.nfc` export (ATQA/SAK, 4K SAK, `??`, NTAG layout), stricter NDEF Type-4 responder.
- BadUSB (root): keys are released when a script is stopped; legacy USB-host mode is labelled honestly.
- BLE: BLE Spam screen works on Android 8–11; crash-safe Bluetooth service lookups; tracker/skimmer false positives reduced; GATT fuzzer no longer crashes and no longer claims analysis it does not do.
- Network tools: nmap timeout actually enforced, installed once into a private folder; "traceroute" relabelled as a TCP reachability probe; port-range validation; tcpdump output parsed correctly and missing tcpdump reported.
- Wi-Fi: 6 GHz channels, single channel table, MAC spoof always brings the interface back up, wardriving keeps first-seen time and exports safely.
- IoT scanner: real CIDR/range parsing, working Stop, MQTT topic listing and CoAP probe implemented properly.
- TOTP: SHA-256/512, digits/period from `otpauth://`, HOTP generator, Base32 validation. Reports: Markdown escaping, unique finding IDs, timeline in every format.
- Settings: keep-screen-on applies app-wide; non-functional toggles removed. About shows the real version.

### Removed
- "NFC Relay Attack" module: it never relayed anything and was unreachable. APDU capture/replay remains in Card Emulation.

### Tests
- New JVM test suites: `IrLogicTest`, `NfcLogicTest`, `NetLogicTest`, `TotpReportTest`, `ShellArgsTest`.

## [2.0.0] - 2026-09-11

### 🎯 Major Release - Professional Red Team Edition

This release introduces a comprehensive red team toolkit with 5 new attack/analysis modules designed for authorized penetration testing and security research.

#### Added

**BLE GATT Fuzzer**
- Discover vulnerabilities in Bluetooth Low Energy devices
- 16+ professional fuzzing payloads (overflow, SQL injection, format strings, etc.)
- Automated GATT service/characteristic discovery
- Real-time vulnerability detection
- Detailed logging with payload-response correlation

**NFC Relay Attack**
- Advanced NFC/EMV relay attack implementation
- APDU exchange capture from NFC cards
- Remote relay server support
- Sequence replay for testing
- Exportable capture logs

**Packet Sniffer**
- Network traffic capture and analysis
- tcpdump integration with BPF filter support
- Protocol-specific filtering (HTTP, HTTPS, DNS, TCP, UDP, ICMP)
- Real-time packet statistics
- CSV export for analysis
- Requires: Root access + tcpdump binary

**IoT Protocol Scanner**
- Automated discovery of IoT services
- MQTT broker detection (port 1883)
- CoAP server discovery (port 5683)
- Common IoT service scanning
- Topic enumeration (MQTT)
- Resource discovery (CoAP)
- Configurable IP range scanning

**Report Generator**
- Professional penetration testing report generation
- Finding management with severity levels (CRITICAL, HIGH, MEDIUM, LOW, INFO)
- CVSS scoring support
- Recommendation tracking
- Timeline documentation
- Multiple export formats: Markdown, HTML, JSON
- Statistical summaries

#### Changed

- HomeScreen now includes 5 new red team tiles
- Main.kt navigation updated with new routes
- Enhanced architectural integration

#### Technical Details

- **Files Added:** 17 new files
- **Lines of Code:** 2,849 LOC
- **ViewModels:** 5 new (BleGattFuzzer, NfcRelayAttack, PacketSniffer, IotProtocolScanner, ReportGenerator)
- **UI Screens:** 5 new Compose screens
- **Commit:** 529ec322a5c08bd54635d7bd506153cfbf388e01

### ⚠️ Legal Notice

All features in this release are designed for:
- ✅ Authorized penetration testing
- ✅ Security research and vulnerability assessment
- ✅ Red team exercises under rules of engagement

Prohibited uses include:
- ❌ Unauthorized network access
- ❌ Interception of private communications without consent
- ❌ Testing without written authorization
- ❌ Malicious use of vulnerability information

Users must comply with all applicable laws including CFAA (US), GDPR (EU), and local data protection laws.

---

## [1.0.9] - Previous Releases

See git history for detailed changelog of earlier releases.

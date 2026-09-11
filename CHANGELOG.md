# Changelog

All notable changes to this project will be documented in this file.

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

package com.example.flipperdroid.view

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.NfcViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

/**
 * Donnee representant une fonctionnalite de l application a afficher dans l ecran d accueil
 *
 * @param title Nom de la fonctionnalite
 * @param icon Icone a afficher pour representer la fonctionnalite
 * @param route Nom de la route de navigation pour acceder a l ecran correspondant
 * @param category Groupe thématique (en-tête de section sur l'accueil)
 * @param description Aide affichée sur appui long
 * @param enabled Indique si la fonctionnalite est activee ou non
 */
data class FeatureItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val category: String = "Tools & Utilities",
    val description: String = "",
    val enabled: Boolean = true
)

/** Ordre d'affichage des sections sur l'écran d'accueil. */
private val CATEGORY_ORDER = listOf(
    "NFC & RFID",
    "Bluetooth & BLE",
    "Wi-Fi & Network",
    "USB / HID",
    "Infrared",
    "Tools & Utilities",
    "System"
)

/**
 * Composable affichant l ecran principal de l application FlipperDroid
 *
 * Les fonctionnalites sont regroupees par categorie sous forme de grille.
 * Chaque carte represente une fonctionnalite et permet de naviguer vers l ecran associe.
 *
 * @param navController Controleur de navigation
 * @param nfcViewModel ViewModel NFC injecte mais non utilise ici
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    nfcViewModel: NfcViewModel
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("legal_prefs", Context.MODE_PRIVATE) }
    var legalAccepted by remember { mutableStateOf(prefs.getBoolean("legalAccepted", false)) }

    if (!legalAccepted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Legal information required") },
            text = {
                Column {
                    Text("You must accept the legal terms to use the application. Please read the following documents:")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { navController.navigate("legal_cgu") }, modifier = Modifier.fillMaxWidth()) { Text("View Terms of Use") }
                    Button(onClick = { navController.navigate("legal_mit") }, modifier = Modifier.fillMaxWidth()) { Text("View MIT License") }
                    Button(onClick = { navController.navigate("legal_mentions") }, modifier = Modifier.fillMaxWidth()) { Text("View Legal Notice") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    prefs.edit { putBoolean("legalAccepted", true) }
                    legalAccepted = true
                }) {
                    Text("Accept and continue")
                }
            },
            dismissButton = {}
        )
    }
    // Boîte de dialogue d'aide (affichée sur appui long d'une tuile).
    var infoFeature by remember { mutableStateOf<FeatureItem?>(null) }
    infoFeature?.let { f ->
        AlertDialog(
            onDismissRequest = { infoFeature = null },
            confirmButton = { TextButton(onClick = { infoFeature = null }) { Text("OK") } },
            icon = { Icon(f.icon, contentDescription = null) },
            title = { Text(f.title) },
            text = { Text(f.description) }
        )
    }

    val features = listOf(
        // --- NFC & RFID ---
        FeatureItem("NFC Reader", Icons.Default.Nfc, "nfc", "NFC & RFID",
            "Read NFC tags: UID and type, NDEF text/URI, Mifare Classic dump with a key dictionary attack, and UID cloning to magic cards. Non-Mifare tags are still logged."),
        FeatureItem("EMV Reader", Icons.Default.CreditCard, "emv_reader", "NFC & RFID",
            "Read a contactless bank card (PPSE → AID → GPO → records) to show card brand, PAN and expiry. Authorized testing only."),
        FeatureItem("NDEF Emulator", Icons.Default.Nfc, "ndef_emulator", "NFC & RFID",
            "Emulate an NFC NDEF tag (HCE Type 4): the phone serves a URL/text when read by an NFC reader or a phone in reader mode."),
        FeatureItem("Card Emulation", Icons.Default.Contactless, "emv_emulation", "NFC & RFID",
            "Emulate a captured ISO-DEP card via HCE (replays its APDU responses). Capture from NFC Reader. Mifare Classic can't be emulated; EMV can't be replayed to pay."),
        FeatureItem("NFC Relay Attack", Icons.Default.Nfc, "nfc_relay", "NFC & RFID",
            "Capture and relay NFC/EMV exchanges. Advanced testing for NFC card security."),

        // --- Bluetooth & BLE ---
        FeatureItem("BLE Spam", Icons.Default.Bluetooth, "bluetooth", "Bluetooth & BLE",
            "Broadcast fake pairing adverts (Apple pop-ups + Nearby Actions, Samsung, Microsoft SwiftPair, Google Fast Pair) to trigger pop-ups nearby. Uses extended advertising when payloads exceed 31 bytes, with an adjustable flood speed."),
        FeatureItem("BLE Scanner", Icons.Default.BluetoothSearching, "ble_scanner", "Bluetooth & BLE",
            "Scan BLE devices and explore their GATT services and characteristics (read / write / notify)."),
        FeatureItem("BLE Beacon", Icons.Default.BluetoothAudio, "ble_beacon", "Bluetooth & BLE",
            "Broadcast BLE beacons: iBeacon, Eddystone-URL, or custom manufacturer data."),
        FeatureItem("BLE Keyboard", Icons.Default.KeyboardAlt, "ble_keyboard", "Bluetooth & BLE",
            "Act as a Bluetooth HID keyboard and type on a paired PC or tablet — no cable (Android 9+)."),
        FeatureItem("Bluetooth Classic", Icons.Default.Bluetooth, "bt_classic", "Bluetooth & BLE",
            "Bluetooth Classic (BR/EDR): scan devices (name/MAC/RSSI/class/paired), discover services via SDP, and an SPP/RFCOMM serial terminal (HC-05/06, Arduino BT). No root."),
        FeatureItem("Tracker Detector", Icons.Default.BluetoothSearching, "tracker_detector", "Bluetooth & BLE",
            "Scan BLE for nearby trackers (Apple Find My/AirTag, Samsung SmartTag, Tile) — anti-stalking."),
        FeatureItem("BLE Fuzzer", Icons.Default.BugReport, "ble_fuzzer", "Bluetooth & BLE",
            "BLE GATT Fuzzer: discover vulnerabilities by writing malformed payloads to BLE characteristics."),
        FeatureItem("Skimmer Detector", Icons.Default.CreditCardOff, "skimmer", "Bluetooth & BLE",
            "Scan Bluetooth/BLE for cheap serial modules (HC-05/06, JDY…) often used in card skimmers. Heuristic hint, not proof."),

        // --- Wi-Fi & Network ---
        FeatureItem("Wifi Deauther", Icons.Default.WifiOff, "wifi_deauther", "Wi-Fi & Network",
            "Scan Wi-Fi networks and attempt a deauth. Honest check: it needs root plus monitor-mode/packet injection, which most phone chipsets can't do."),
        FeatureItem("WiFi Analyzer", Icons.Default.NetworkWifi, "wifi_analyzer", "Wi-Fi & Network",
            "Scan Wi-Fi and view channel usage on 2.4 and 5 GHz. MAC spoofing requires root."),
        FeatureItem("Evil Portal", Icons.Default.Router, "evil_portal", "Wi-Fi & Network",
            "Open a local hotspot with a captive login page (Generic/Google/Facebook/Free-WiFi templates) that captures submitted credentials, with a live request counter. Clients open the portal URL manually (no auto pop-up without root). Authorized testing only."),
        FeatureItem("Wardriving", Icons.Default.Map, "wardriving", "Wi-Fi & Network",
            "Map nearby Wi-Fi networks with GPS coordinates and export them to Wigle CSV."),
        FeatureItem("LAN Discovery", Icons.Default.Router, "lan_discovery", "Wi-Fi & Network",
            "Discover LAN services via mDNS/Bonjour + SSDP/UPnP (Chromecast, printers, NAS, IoT) and grab HTTP/SSH banners."),
        FeatureItem("Network Tools", Icons.Default.Router, "network", "Wi-Fi & Network",
            "Ping, port scan, DNS lookup, traceroute, Wake-on-LAN, ARP table, ping sweep, and bundled nmap (root)."),
        FeatureItem("Netcat / HTTP", Icons.Default.NetworkWifi, "netcat", "Wi-Fi & Network",
            "TCP/UDP client & TCP listener, plus an HTTP file server to deliver payloads on the LAN."),
        FeatureItem("Packet Sniffer", Icons.Default.Router, "packet_sniffer", "Wi-Fi & Network",
            "Capture and analyze network packets (requires tcpdump + root). Export to CSV."),
        FeatureItem("IoT Scanner", Icons.Default.Scanner, "iot_scanner", "Wi-Fi & Network",
            "Discover MQTT brokers, CoAP servers, and common IoT services on a network."),
        FeatureItem("GPS Spoof", Icons.Default.Map, "gps_spoof", "Wi-Fi & Network",
            "Set a fake GPS location via mock location (enable this app in Developer Options → mock location app)."),

        // --- USB / HID ---
        FeatureItem("BadUSB", Icons.Default.Usb, "badusb", "USB / HID",
            "USB-Host keystroke injection: when a PC is connected, the phone types a script as a USB keyboard."),
        FeatureItem("BadUSB (root)", Icons.Default.Keyboard, "badusb_root", "USB / HID",
            "Real USB-gadget BadUSB (root): streams HID reports to /dev/hidgX using a DuckyScript engine."),

        // --- Infrared ---
        FeatureItem("Infrared", Icons.Default.SettingsRemote, "ir", "Infrared",
            "Universal IR remote with per-brand protocols (Samsung, LG, Sony, Panasonic, Philips…). Requires an IR emitter."),
        FeatureItem("IR Remotes", Icons.Default.Tv, "ir_remotes", "Infrared",
            "Play Flipper .ir remotes from a code database, import single files or a whole folder (Flipper-IRDB)."),
        FeatureItem("IR Tools", Icons.Default.FlashOn, "ir_tools", "Infrared",
            "TV-B-Gone power-code blaster and command brute-forcer for mapping unknown remotes. Authorized testing only."),
        FeatureItem("AC Control", Icons.Default.AcUnit, "ac_control", "Infrared",
            "Full-state air-conditioner remote: power, mode, temperature, fan and swing in one frame. Brands: Gree (verified) + Coolix, LG, Midea, Samsung, Fujitsu, Kelvinator, Haier, Daikin, Mitsubishi, Panasonic (reference — verify on your unit)."),

        // --- Tools & Utilities ---
        FeatureItem("QR Scanner", Icons.Default.QrCodeScanner, "qr", "Tools & Utilities",
            "Scan QR codes and barcodes with the camera; copy or open the result."),
        FeatureItem("Password Generator", Icons.Default.Key, "password_generator", "Tools & Utilities",
            "Generate strong passwords with SecureRandom, and show a QR code to transfer them."),
        FeatureItem("Crypto Tools", Icons.Default.Lock, "crypto_tools", "Tools & Utilities",
            "Offline crypto/encoding multitool: hashes (MD5/SHA/CRC/HMAC), Base64/Hex/URL, ROT13, JWT decode, hash identifier. CTF-friendly."),
        FeatureItem("Wi-Fi QR", Icons.Default.QrCodeScanner, "wifi_qr", "Tools & Utilities",
            "Generate a Wi-Fi QR code (SSID + password) that phones scan to join the network."),
        FeatureItem("Default Creds", Icons.Default.Key, "default_creds", "Tools & Utilities",
            "Offline reference of common default credentials for routers, IoT and services. Authorized testing only."),
        FeatureItem("2FA Vault", Icons.Default.Pin, "totp", "Tools & Utilities",
            "Store TOTP (RFC 6238) accounts locally and generate 2FA codes. Secrets never leave the device."),
        FeatureItem("Flipper Files", Icons.Default.Folder, "flipper_files", "Tools & Utilities",
            "Open and inspect Flipper .nfc, .sub and .ir files."),
        FeatureItem("Report Generator", Icons.Default.Article, "report_generator", "Tools & Utilities",
            "Build professional penetration test reports with findings, recommendations, and multiple export formats."),

        // --- System ---
        FeatureItem("Logs", Icons.Default.Article, "logs", "System",
            "Central activity log for all modules."),
        FeatureItem("Settings", Icons.Default.Settings, "settings", "System",
            "App settings, including the dark-mode toggle."),
        FeatureItem("About", Icons.Default.Info, "about", "System",
            "About FlipperDroid, credits and legal information.")
    )

    val grouped = remember(features) { features.groupBy { it.category } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "FlipperDroid",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            "Wireless & hardware toolkit",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Astuce d'utilisation, pleine largeur.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Tap to open · long-press a tile for a description",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            CATEGORY_ORDER.forEach { category ->
                val items = grouped[category] ?: return@forEach
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(category, items.size)
                }
                items(items) { feature ->
                    FeatureCard(
                        feature = feature,
                        onClick = { if (feature.enabled) navController.navigate(feature.route) },
                        onLongClick = { infoFeature = feature }
                    )
                }
            }
        }
    }
}

/** En-tête de section : petit trait de couleur + titre en majuscules espacées + compteur. */
@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Tuile d'une fonctionnalité : icône dans une pastille teintée + titre. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeatureCard(
    feature: FeatureItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val alpha = if (feature.enabled) 1f else 0.45f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f * alpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = feature.title,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
    }
}

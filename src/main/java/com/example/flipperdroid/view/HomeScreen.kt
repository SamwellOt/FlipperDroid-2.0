package com.example.flipperdroid.view

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
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
 * @param enabled Indique si la fonctionnalite est activee ou non
 */
data class FeatureItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val description: String = "",
    val enabled: Boolean = true
)

/**
 * Composable affichant l ecran principal de l application FlipperDroid
 *
 * Il affiche les fonctionnalites disponibles sous forme de grille
 * Chaque carte represente une fonctionnalite et permet de naviguer vers l ecran associe
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
        FeatureItem("NFC Reader", Icons.Default.Nfc, "nfc",
            "Read NFC tags: UID and type, NDEF text/URI, Mifare Classic dump with a key dictionary attack, and UID cloning to magic cards. Non-Mifare tags are still logged."),
        FeatureItem("EMV Reader", Icons.Default.CreditCard, "emv_reader",
            "Read a contactless bank card (PPSE → AID → GPO → records) to show card brand, PAN and expiry. Authorized testing only."),
        FeatureItem("Card Emulation", Icons.Default.Contactless, "emv_emulation",
            "Emulate a captured ISO-DEP card via HCE (replays its APDU responses). Capture from NFC Reader. Mifare Classic can't be emulated; EMV can't be replayed to pay."),
        FeatureItem("BadUSB", Icons.Default.Usb, "badusb",
            "USB-Host keystroke injection: when a PC is connected, the phone types a script as a USB keyboard."),
        FeatureItem("BadUSB (root)", Icons.Default.Keyboard, "badusb_root",
            "Real USB-gadget BadUSB (root): streams HID reports to /dev/hidgX using a DuckyScript engine."),
        FeatureItem("BLE Spam", Icons.Default.Bluetooth, "bluetooth",
            "Broadcast fake pairing adverts (Apple pop-ups + Nearby Actions, Samsung, Microsoft SwiftPair, Google Fast Pair) to trigger pop-ups nearby. Uses extended advertising when payloads exceed 31 bytes, with an adjustable flood speed."),
        FeatureItem("BLE Scanner", Icons.Default.BluetoothSearching, "ble_scanner",
            "Scan BLE devices and explore their GATT services and characteristics (read / write / notify)."),
        FeatureItem("BLE Beacon", Icons.Default.BluetoothAudio, "ble_beacon",
            "Broadcast BLE beacons: iBeacon, Eddystone-URL, or custom manufacturer data."),
        FeatureItem("BLE Keyboard", Icons.Default.KeyboardAlt, "ble_keyboard",
            "Act as a Bluetooth HID keyboard and type on a paired PC or tablet — no cable (Android 9+)."),
        FeatureItem("Network Tools", Icons.Default.Router, "network",
            "Ping, port scan, DNS lookup, traceroute, Wake-on-LAN, ARP table, ping sweep, and bundled nmap (root)."),
        FeatureItem("Wifi Deauther", Icons.Default.WifiOff, "wifi_deauther",
            "Scan Wi-Fi networks and attempt a deauth. Honest check: it needs root plus monitor-mode/packet injection, which most phone chipsets can't do."),
        FeatureItem("WiFi Analyzer", Icons.Default.NetworkWifi, "wifi_analyzer",
            "Scan Wi-Fi and view channel usage on 2.4 and 5 GHz. MAC spoofing requires root."),
        FeatureItem("Evil Portal", Icons.Default.Router, "evil_portal",
            "Open a local hotspot with a captive login page (Generic/Google/Facebook/Free-WiFi templates) that captures submitted credentials, with a live request counter. Clients open the portal URL manually (no auto pop-up without root). Authorized testing only."),
        FeatureItem("Wardriving", Icons.Default.Map, "wardriving",
            "Map nearby Wi-Fi networks with GPS coordinates and export them to Wigle CSV."),
        FeatureItem("Skimmer Detector", Icons.Default.CreditCardOff, "skimmer",
            "Scan Bluetooth/BLE for cheap serial modules (HC-05/06, JDY…) often used in card skimmers. Heuristic hint, not proof."),
        FeatureItem("Infrared", Icons.Default.SettingsRemote, "ir",
            "Simple universal IR remote (NEC) for TV, AC, audio and projector. Requires an IR emitter."),
        FeatureItem("IR Remotes", Icons.Default.Tv, "ir_remotes",
            "Play Flipper .ir remotes from a code database, import your own, and run a TV-B-Gone style power sweep."),
        FeatureItem("QR Scanner", Icons.Default.QrCodeScanner, "qr",
            "Scan QR codes and barcodes with the camera; copy or open the result."),
        FeatureItem("Password Generator", Icons.Default.Key, "password_generator",
            "Generate strong passwords with SecureRandom, and show a QR code to transfer them."),
        FeatureItem("2FA Vault", Icons.Default.Pin, "totp",
            "Store TOTP (RFC 6238) accounts locally and generate 2FA codes. Secrets never leave the device."),
        FeatureItem("Flipper Files", Icons.Default.Folder, "flipper_files",
            "Open and inspect Flipper .nfc, .sub and .ir files."),
        FeatureItem("Logs", Icons.Default.Article, "logs",
            "Central activity log for all modules."),
        FeatureItem("Settings", Icons.Default.Settings, "settings",
            "App settings, including the dark-mode toggle."),
        FeatureItem("About", Icons.Default.Info, "about",
            "About FlipperDroid, credits and legal information.")
    )

    Scaffold(
        topBar = {
            /**
             * Barre superieure avec le nom de l application
             */
            TopAppBar(
                title = { Text("FlipperDroid") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        /**
         * Colonne contenant la grille de fonctionnalites
         */
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Text(
                "Tap to open · long-press a tile for a description",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(features) { feature ->
                    /**
                     * Carte individuelle representant une fonctionnalite
                     * Non clickable si la fonctionnalite est desactivee
                     */
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .combinedClickable(
                                onClick = {
                                    if (feature.enabled) navController.navigate(feature.route)
                                },
                                onLongClick = { infoFeature = feature }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (feature.enabled)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = feature.icon,
                                contentDescription = feature.title,
                                modifier = Modifier.size(48.dp),
                                tint = if (feature.enabled)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = feature.title,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                color = if (feature.enabled)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
            /*
            Button(
                onClick = {
                    prefs.edit().clear().apply()
                    legalAccepted = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Reset CGU (debug)")
            }*/
        }
    }
}

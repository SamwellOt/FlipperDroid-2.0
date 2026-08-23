package com.example.flipperdroid
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.activity.viewModels
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.flipperdroid.viewmodel.*
import com.example.flipperdroid.view.*
import com.example.flipperdroid.ui.theme.FlipperDroidTheme
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import com.example.flipperdroid.viewmodel.EmvCardEmulationViewModel
import com.example.flipperdroid.view.EmvCardEmulationScreen
import com.example.flipperdroid.viewmodel.EmvReaderViewModel
import com.example.flipperdroid.view.EmvReaderScreen
import com.example.flipperdroid.viewmodel.BleSpamViewModel
import com.example.flipperdroid.view.BleSpamScreen
import com.example.flipperdroid.viewmodel.BluetoothViewModel
import com.example.flipperdroid.view.LegalTextScreen
import com.example.flipperdroid.viewmodel.ThemeViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {

    private val nfcViewModel: NfcViewModel by viewModels()
    private val badUsbViewModel: BadUsbViewModel by viewModels()
    private val bluetoothViewModel: BluetoothViewModel by viewModels()
    private val networkToolsViewModel: NetworkToolsViewModel by viewModels()
    private val wifiDeautherViewModel: WifiDeautherViewModel by viewModels()
    private val emvCardEmulationViewModel: EmvCardEmulationViewModel by viewModels()
    private val emvReaderViewModel: EmvReaderViewModel by viewModels()
    private val bleSpamViewModel: BleSpamViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private lateinit var intentFiltersArray: Array<IntentFilter>
    private lateinit var techListsArray: Array<Array<String>>

    /**
     * Initialise l'activité principale.
     *
     * - Configure le ViewModel pour la gestion NFC.
     * - Initialise le NFC Adapter et le PendingIntent pour la lecture NFC en premier plan.
     * - Définit le contenu Compose avec la navigation de l'application.
     *
     * @param savedInstanceState état sauvegardé de l'activité (peut être null).
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize NFC adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Initialize NFC only if available
        if (nfcAdapter != null) {
            // Initialize PendingIntent for NFC
            val intent = Intent(this, javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            // Setup intent filters for NFC
            intentFiltersArray = arrayOf(
                IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
                IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
            )

            // Setup tech lists for all NFC tech types
            techListsArray = arrayOf(
                arrayOf(
                    NfcA::class.java.name,
                    NfcB::class.java.name,
                    NfcF::class.java.name,
                    NfcV::class.java.name
                )
            )
        }

        // Initialize BadUSB
        badUsbViewModel.initialize(this)

        // Initialize Bluetooth
        bluetoothViewModel.initialize(this)

        setContent {
            val isDarkMode by themeViewModel.isDarkMode.collectAsState()
            FlipperDroidTheme(darkTheme = isDarkMode) {
                AppNavigation(themeViewModel)
            }
        }
        handleNfcIntent(intent)
    }

    /**
     * Méthode appelée lorsqu'une nouvelle Intent est reçue par l'activité.
     *
     * Intercepte l'intent NFC, récupère le tag détecté, extrait son identifiant,
     * puis le transmet au ViewModel pour traitement.
     *
     * @param intent l'Intent reçue par l'activité (peut être null).
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            handleNfcIntent(intent)

            // Handle USB connection
            if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                val device: UsbDevice? =
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                val usbManager = getSystemService(USB_SERVICE) as UsbManager
                device?.let { dev ->
                    if (usbManager.hasPermission(dev)) {
                        val connection = usbManager.openDevice(dev)
                        if (connection != null) {
                            badUsbViewModel.connectUsb(dev, connection)
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun handleNfcIntent(intent: Intent?) {
        if (intent?.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            intent?.action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED
        ) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            tag?.let {
                // Un tag Mifare Classic sera lu par nfcViewModel ; une carte EMV (IsoDep)
                // sera lue par emvReaderViewModel. Chaque ViewModel ignore proprement
                // les tags qui ne correspondent pas à sa technologie, donc un seul
                // des deux se connecte réellement à la carte présentée.
                nfcViewModel.onTagScanned(it)
                emvReaderViewModel.readCard(it)
            }
        }
    }

    /**
     * Méthode appelée lorsque l'activité reprend le premier plan.
     *
     * Active la lecture NFC en mode premier plan pour capturer les tags NFC
     * lorsque l'application est au premier plan.
     */
    override fun onResume() {
        super.onResume()
        // Enable foreground dispatch only if NFC is available and enabled
        nfcAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                adapter.enableForegroundDispatch(
                    this,
                    pendingIntent,
                    intentFiltersArray,
                    techListsArray
                )
            }
        }
    }

    /**
     * Méthode appelée lorsque l'activité n'est plus au premier plan.
     *
     * Désactive la lecture NFC en mode premier plan pour éviter les lectures
     * hors contexte ou conflits avec d'autres applications.
     */
    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
        badUsbViewModel.disconnect()
    }

    /**
     * permet la navigation
     *
     * Les routes :
     * - "home" : écran d'accueil
     * - "nfc" : écran de la fonctionnalité NFC
     * - "badusb" : écran de la fonctionnalité BadUSB
     */
    @SuppressLint("ComposableDestinationInComposeScope")
    @Composable
    fun AppNavigation(themeViewModel: ThemeViewModel) {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeScreen(navController = navController, nfcViewModel = nfcViewModel)
            }
            composable("nfc") {
                NfcScreen(navController = navController, nfcViewModel = nfcViewModel)
            }
            composable("badusb") {
                BadUsbScreen(navController = navController, viewModel = badUsbViewModel)
            }
            composable("bluetooth") {
                BleSpamScreen(navController = navController, viewModel = bleSpamViewModel)
            }
            composable("network") {
                NetworkToolsScreen(navController = navController, viewModel = networkToolsViewModel)
            }
            composable("wifi_deauther") {
                WifiDeautherScreen(navController = navController, viewModel = wifiDeautherViewModel)
            }
            composable("ir") {
                InfraredScreen(navController = navController)
            }
            composable("ir_remotes") {
                IrRemoteScreen(navController = navController)
            }
            composable("password_generator") {
                PasswordGeneratorScreen(navController = navController)
            }
            composable("qr") {
                QrScannerScreen(navController = navController)
            }
            composable("ble_scanner") {
                BleScannerScreen(navController = navController, viewModel = bluetoothViewModel)
            }
            composable("wardriving") {
                WardrivingScreen(navController = navController)
            }
            composable("skimmer") {
                SkimmerDetectorScreen(navController = navController)
            }
            composable("wifi_analyzer") {
                WifiAnalyzerScreen(navController = navController)
            }
            composable("badusb_root") {
                RootBadUsbScreen(navController = navController)
            }
            composable("ble_beacon") {
                BeaconScreen(navController = navController)
            }
            composable("ble_keyboard") {
                HidKeyboardScreen(navController = navController)
            }
            composable("evil_portal") {
                EvilPortalScreen(navController = navController)
            }
            composable("totp") {
                TotpScreen(navController = navController)
            }
            composable("flipper_files") {
                FlipperFilesScreen(navController = navController)
            }
            composable("logs") {
                LogsScreen(navController = navController)
            }
            composable("settings") {
                SettingsScreen(navController = navController, themeViewModel = themeViewModel)
            }
            composable("about") {
                AboutScreen(navController = navController)
            }
            composable("emv_emulation") {
                EmvCardEmulationScreen(
                    navController = navController,
                    viewModel = emvCardEmulationViewModel
                )
            }
            composable("emv_reader") {
                EmvReaderScreen(navController = navController, viewModel = emvReaderViewModel)
            }
            composable("legal_mit") {
                LegalTextScreen(
                    navController = navController,
                    assetPath = "legacy/mit.txt",
                    title = "MIT License"
                )
            }
            composable("legal_cgu") {
                LegalTextScreen(
                    navController = navController,
                    assetPath = "legacy/term_of_use.txt",
                    title = "Term Of Use"
                )
            }
            composable("legal_mentions") {
                LegalTextScreen(
                    navController = navController,
                    assetPath = "legacy/legacy_notice.txt",
                    title = "Legacy Notice"
                )
            }
        }
    }
}

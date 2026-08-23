package com.example.flipperdroid.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.navigation.NavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Scanner de QR codes / codes-barres via la caméra (bibliothèque ZXing embedded).
 * Affiche le contenu lu, permet de le copier et d'ouvrir les URL.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(navController: NavController) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var scannedContent by remember { mutableStateOf<String?>(null) }
    var scannedFormat by remember { mutableStateOf<String?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            scannedContent = result.contents
            scannedFormat = result.formatName
        }
    }

    fun launchScan() {
        val options = ScanOptions().apply {
            setPrompt("Align a QR code or barcode")
            setBeepEnabled(true)
            setOrientationLocked(false)
        }
        scanLauncher.launch(options)
    }

    val isUrl = scannedContent?.let {
        it.startsWith("http://", true) || it.startsWith("https://", true)
    } == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR Scanner") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = { launchScan() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan QR / Barcode")
            }

            val content = scannedContent
            if (content == null) {
                Text(
                    "Tap the button to scan a QR code or barcode with the camera.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Result (${scannedFormat ?: "?"})", style = MaterialTheme.typography.titleMedium)
                        SelectionContainerText(content)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("qr", content))
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Copy")
                            }
                            if (isUrl) {
                                OutlinedButton(onClick = { uriHandler.openUri(content) }) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionContainerText(text: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

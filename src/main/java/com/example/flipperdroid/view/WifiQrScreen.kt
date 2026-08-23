package com.example.flipperdroid.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/** Génère un QR code Wi-Fi (WIFI:...) que les téléphones scannent pour rejoindre le réseau. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiQrScreen(navController: NavController) {
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var security by remember { mutableStateOf("WPA") }
    var hidden by remember { mutableStateOf(false) }
    var payload by remember { mutableStateOf<String?>(null) }

    fun esc(s: String) = s
        .replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
        .replace(":", "\\:").replace("\"", "\\\"")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wi-Fi QR") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Generate a QR code that phones scan to join a Wi-Fi network.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(ssid, { ssid = it }, label = { Text("SSID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") }, singleLine = true,
                enabled = security != "nopass", modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("WPA" to "WPA/WPA2", "WEP" to "WEP", "nopass" to "Open").forEach { (v, label) ->
                    Row(Modifier.selectable(selected = security == v, onClick = { security = v }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = security == v, onClick = { security = v })
                        Text(label)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hidden, onCheckedChange = { hidden = it })
                Text("Hidden network")
            }
            Button(
                onClick = {
                    val pass = if (security == "nopass") "" else password
                    payload = "WIFI:S:${esc(ssid)};T:$security;P:${esc(pass)};H:$hidden;;"
                },
                enabled = ssid.isNotEmpty()
            ) { Text("Generate QR") }

            payload?.let { p ->
                val bmp = remember(p) { try { generateQrCodeBitmap(p) } catch (_: Exception) { null } }
                if (bmp != null) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = "Wi-Fi QR", modifier = Modifier.size(240.dp))
                }
                Text(p, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

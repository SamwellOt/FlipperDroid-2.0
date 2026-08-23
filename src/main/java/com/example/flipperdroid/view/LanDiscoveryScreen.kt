package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.LanDiscoveryViewModel

/** Découverte de services LAN (mDNS + SSDP) + bannières. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanDiscoveryScreen(
    navController: NavController,
    viewModel: LanDiscoveryViewModel = viewModel()
) {
    val services by viewModel.services.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val status by viewModel.status.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LAN Discovery") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { if (isScanning) viewModel.stop() else viewModel.start() }) {
                        Text(if (isScanning) "Stop" else "Scan")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Text(
                "Finds services on your Wi-Fi (mDNS/Bonjour + SSDP/UPnP): Chromecast, printers, " +
                    "NAS, IoT, routers. Tap Banner to fingerprint an HTTP/SSH/FTP port.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isScanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(services) { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(s.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(s.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("${s.host}:${s.port}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            if (s.info.isNotEmpty()) Text(s.info, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            s.banner?.let {
                                Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                            }
                            if (s.port > 0) {
                                TextButton(onClick = { viewModel.grabBanner(s.host, s.port) }) { Text("Banner") }
                            }
                        }
                    }
                }
            }
        }
    }
}

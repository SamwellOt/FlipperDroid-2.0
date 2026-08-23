package com.example.flipperdroid.view

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.BluetoothClassicViewModel

/** Bluetooth classique (BR/EDR) : scan + SDP + terminal SPP/RFCOMM. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothClassicScreen(
    navController: NavController,
    viewModel: BluetoothClassicViewModel = viewModel()
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val status by viewModel.status.collectAsState()
    val sppConnected by viewModel.sppConnected.collectAsState()
    val sppStatus by viewModel.sppStatus.collectAsState()
    val sppLog by viewModel.sppLog.collectAsState()

    val neededPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            arrayOf(Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.initialize(context) }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        val granted = neededPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (!granted) permissionLauncher.launch(neededPermissions)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth Classic") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (permissionsGranted) {
                        TextButton(onClick = { if (isScanning) viewModel.stopScan() else viewModel.startScan() }) {
                            Text(if (isScanning) "Stop" else "Scan")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!permissionsGranted) {
                Text("Bluetooth permissions are required.", color = MaterialTheme.colorScheme.error)
                Button(onClick = { permissionLauncher.launch(neededPermissions) }) { Text("Grant permissions") }
                return@Column
            }

            if (status.isNotEmpty()) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isScanning) LinearProgressIndicator(Modifier.fillMaxWidth())

            // Terminal SPP (visible une fois connecté)
            if (sppConnected) {
                SppTerminal(
                    status = sppStatus,
                    log = sppLog,
                    onSend = { txt, hex -> viewModel.sendSpp(txt, hex) },
                    onDisconnect = { viewModel.disconnectSpp() }
                )
                Spacer(Modifier.height(4.dp))
            } else if (sppStatus.isNotEmpty()) {
                Text(sppStatus, style = MaterialTheme.typography.bodySmall)
            }

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { d ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(d.name, fontWeight = FontWeight.Bold)
                                if (d.bonded) Text("paired", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                            Text(d.address, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${d.majorClass}${d.rssi?.let { "  •  $it dBm" } ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (d.services.isNotEmpty()) {
                                Text("Services: ${d.services.joinToString()}", style = MaterialTheme.typography.labelSmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.discoverServices(d.address) }) { Text("SDP") }
                                if (!d.bonded) TextButton(onClick = { viewModel.pair(d.address) }) { Text("Pair") }
                                TextButton(
                                    onClick = { viewModel.connectSpp(d.address) },
                                    enabled = !sppConnected
                                ) { Text("SPP") }
                            }
                        }
                    }
                }
            }

            Text(
                "Classic (BR/EDR) recon: scan, SDP service discovery, and an SPP/RFCOMM serial " +
                    "terminal (HC-05/06, Arduino BT). No root. Sniffing/MAC change need root+hardware.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SppTerminal(
    status: String,
    log: List<String>,
    onSend: (String, Boolean) -> Unit,
    onDisconnect: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var hex by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("SPP terminal", fontWeight = FontWeight.Bold)
                TextButton(onClick = onDisconnect) { Text("Disconnect") }
            }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.labelSmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                items(log) { line ->
                    Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(if (hex) "Bytes (hex)" else "Text (CR/LF added)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hex, onCheckedChange = { hex = it })
                    Text("Hex")
                }
                Button(
                    onClick = { if (text.isNotEmpty()) { onSend(text, hex); text = "" } },
                    enabled = text.isNotEmpty()
                ) { Text("Send") }
            }
        }
    }
}

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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.BluetoothViewModel

/**
 * Ecran scanner BLE + explorateur GATT. Scanne les appareils, se connecte,
 * découvre les services/caractéristiques et permet de lire les valeurs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleScannerScreen(
    navController: NavController,
    viewModel: BluetoothViewModel
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val services by viewModel.services.collectAsState()
    val log by viewModel.log.collectAsState()
    var writeHex by remember { mutableStateOf("00") }

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.checkPermissions() }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        val granted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (!granted) permissionLauncher.launch(permissions)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE Scanner") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isScanning) {
                        IconButton(onClick = { viewModel.stopScan() }) {
                            Icon(Icons.Default.Bluetooth, contentDescription = "Stop")
                        }
                    } else {
                        IconButton(onClick = { viewModel.startScan() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan")
                        }
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
        ) {
            if (!permissionsGranted) {
                Text(
                    "Bluetooth permissions are required to scan.",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { permissionLauncher.launch(permissions) }) {
                    Text("Grant permissions")
                }
                return@Column
            }

            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            // Panneau de connexion GATT
            if (connectedDevice != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${connectedDevice!!.name} (${connectedDevice!!.address})",
                            fontWeight = FontWeight.Bold
                        )
                        Text("State: $connectionState", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = { viewModel.disconnect() }) { Text("Disconnect") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (services.isNotEmpty()) {
                    item {
                        Text("GATT Services", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = writeHex,
                            onValueChange = { writeHex = it },
                            label = { Text("Write payload (hex)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    items(services) { service ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "Service ${shortUuid(service.uuid)}",
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                service.characteristics.forEach { ch ->
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(shortUuid(ch.uuid), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                            Text(ch.properties.joinToString(" "), style = MaterialTheme.typography.labelSmall)
                                            ch.value?.let { Text("= $it", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                                        }
                                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                            if (ch.properties.contains("READ")) {
                                                TextButton(onClick = { viewModel.readCharacteristic(ch.serviceUuid, ch.uuid) }) {
                                                    Text("Read")
                                                }
                                            }
                                            if (ch.properties.any { it.startsWith("WRITE") }) {
                                                TextButton(onClick = { viewModel.writeCharacteristic(ch.serviceUuid, ch.uuid, writeHex) }) {
                                                    Text("Write")
                                                }
                                            }
                                            if (ch.properties.contains("NOTIFY") || ch.properties.contains("INDICATE")) {
                                                TextButton(onClick = { viewModel.enableNotifications(ch.serviceUuid, ch.uuid) }) {
                                                    Text("Notify")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item { Text("Scanned devices", style = MaterialTheme.typography.titleMedium) }
                    items(devices) { device ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.connect(device) }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(device.name, fontWeight = FontWeight.Bold)
                                    Text(device.address, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                }
                                Text("${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Journal
            if (log.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Log", style = MaterialTheme.typography.titleSmall)
                LazyColumn(Modifier.heightIn(max = 120.dp)) {
                    items(log.reversed()) { line ->
                        Text(line, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

/** Raccourcit un UUID 128 bits standard en sa forme 16 bits quand c'est possible. */
private fun shortUuid(uuid: String): String {
    // 0000XXXX-0000-1000-8000-00805f9b34fb -> 0xXXXX
    val lower = uuid.lowercase()
    return if (lower.startsWith("0000") && lower.endsWith("-0000-1000-8000-00805f9b34fb")) {
        "0x" + lower.substring(4, 8)
    } else uuid
}

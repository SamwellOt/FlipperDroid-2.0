package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.IotProtocolScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IotProtocolScannerScreen(navController: NavController) {
    val viewModel: IotProtocolScannerViewModel = viewModel()

    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanLog by viewModel.scanLog.collectAsState()
    val scanMode by viewModel.scanMode.collectAsState()
    val targetRange by viewModel.targetRange.collectAsState()

    var editTargetRange by remember { mutableStateOf(targetRange) }
    var selectedMode by remember { mutableStateOf("common") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IoT Protocol Scanner") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🔍 IoT Scanner",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Discover MQTT, CoAP, and other IoT protocols",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Scan Mode", style = MaterialTheme.typography.labelSmall)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("mqtt", "coap", "common").forEach { mode ->
                                Button(
                                    onClick = { selectedMode = mode },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isScanning,
                                    colors = if (selectedMode == mode) {
                                        ButtonDefaults.buttonColors()
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    }
                                ) {
                                    Text(mode.uppercase())
                                }
                            }
                        }

                        Divider(Modifier.padding(vertical = 12.dp))

                        Text("Target Range", style = MaterialTheme.typography.labelSmall)
                        OutlinedTextField(
                            value = editTargetRange,
                            onValueChange = { editTargetRange = it },
                            label = { Text("IP Range") },
                            placeholder = { Text("e.g., 192.168.1.1-254") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            enabled = !isScanning
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.setTargetRange(editTargetRange)
                                    viewModel.setScanMode(selectedMode)
                                    viewModel.startScan(selectedMode, editTargetRange)
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isScanning
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Start Scan")
                            }

                            Button(
                                onClick = { viewModel.stopScan() },
                                modifier = Modifier.weight(1f),
                                enabled = isScanning
                            ) {
                                Icon(Icons.Default.Stop, null)
                            }
                        }
                    }
                }
            }

            item {
                Text("Discovered: ${discoveredDevices.size} devices", style = MaterialTheme.typography.titleSmall)
            }

            items(discoveredDevices) { device ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${device.protocol} - ${device.service}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${device.address}:${device.port}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                            if (device.version.isNotEmpty()) {
                                Text(
                                    device.version,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        if (device.protocol == "MQTT") {
                            Button(
                                onClick = { viewModel.scanMqttTopics(device.address) },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(top = 8.dp)
                            ) {
                                Text("Topics", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                            }
                        }

                        if (device.protocol == "CoAP") {
                            Button(
                                onClick = { viewModel.scanCoapResources(device.address) },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(top = 8.dp)
                            ) {
                                Text("Resources", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                            }
                        }
                    }
                }
            }

            item {
                Text("Scan Log", style = MaterialTheme.typography.titleSmall)
            }

            item {
                Card(modifier = Modifier.heightIn(min = 150.dp)) {
                    Text(
                        scanLog,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }

            item {
                Button(
                    onClick = { viewModel.clearResults() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear Results")
                }
            }
        }
    }
}

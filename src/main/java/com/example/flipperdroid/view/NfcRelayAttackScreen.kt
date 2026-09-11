package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.NfcRelayAttackViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcRelayAttackScreen(navController: NavController) {
    val viewModel: NfcRelayAttackViewModel = viewModel()
    val context = LocalContext.current

    val capturedData by viewModel.capturedData.collectAsState()
    val relayLog by viewModel.relayLog.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val relayAddress by viewModel.relayAddress.collectAsState()
    val relayPort by viewModel.relayPort.collectAsState()

    var newAddress by remember { mutableStateOf(relayAddress) }
    var newPort by remember { mutableStateOf(relayPort.toString()) }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NFC Relay Attack") },
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "⚠️ NFC Relay Attack",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Captures EMV/NFC exchanges and relays to server. Authorized testing only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Relay: $relayAddress:$relayPort", Modifier.weight(1f))
                            Button(
                                onClick = { showSettings = !showSettings },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Config")
                            }
                        }

                        if (showSettings) {
                            Divider(Modifier.padding(vertical = 12.dp))
                            OutlinedTextField(
                                value = newAddress,
                                onValueChange = { newAddress = it },
                                label = { Text("Relay Address") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newPort,
                                onValueChange = { newPort = it },
                                label = { Text("Relay Port") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                            Button(
                                onClick = {
                                    val port = newPort.toIntOrNull() ?: return@Button
                                    viewModel.setRelayServer(newAddress, port)
                                    showSettings = false
                                },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(top = 8.dp)
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }

            item {
                Text("Captured Exchanges: ${capturedData.size}", style = MaterialTheme.typography.titleSmall)
            }

            if (capturedData.isEmpty()) {
                item {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Scan an NFC card to capture relay data")
                        }
                    }
                }
            } else {
                items(capturedData) { capture ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                capture.description,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "CMD: ${capture.command.take(32).joinToString("") { "%02X".format(it) }}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            if (capture.response.isNotEmpty()) {
                                Text(
                                    "RSP: ${capture.response.take(32).joinToString("") { "%02X".format(it) }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text("Activity Log", style = MaterialTheme.typography.titleSmall)
            }

            item {
                Card(modifier = Modifier.heightIn(min = 150.dp)) {
                    Text(
                        relayLog,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.clearCaptures() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
        }
    }
}

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
import com.example.flipperdroid.viewmodel.PacketSnifferViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacketSnifferScreen(navController: NavController) {
    val viewModel: PacketSnifferViewModel = viewModel()

    val packets by viewModel.packets.collectAsState()
    val isSniffing by viewModel.isSniffing.collectAsState()
    val snifferLog by viewModel.snifferLog.collectAsState()
    val packetStats by viewModel.packetStats.collectAsState()

    var selectedInterface by remember { mutableStateOf("any") }
    var filterExpression by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Packet Sniffer") },
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
                            "📡 Packet Sniffer",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Capture and analyze network traffic. Requires root & tcpdump.",
                            style = MaterialTheme.typography.bodySmall,
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.setInterface(selectedInterface)
                                    viewModel.setFilter(filterExpression)
                                    viewModel.startSniffing()
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isSniffing
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Start")
                            }

                            Button(
                                onClick = { viewModel.stopSniffing() },
                                modifier = Modifier.weight(1f),
                                enabled = isSniffing
                            ) {
                                Icon(Icons.Default.Stop, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Stop")
                            }

                            Button(
                                onClick = { viewModel.clearPackets() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Delete, null)
                            }
                        }

                        Divider(Modifier.padding(vertical = 8.dp))

                        Text(
                            "Interface: $selectedInterface",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        OutlinedTextField(
                            value = filterExpression,
                            onValueChange = { filterExpression = it },
                            label = { Text("BPF Filter") },
                            placeholder = { Text("e.g., 'tcp port 80'") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.labelSmall
                        )

                        if (showAdvanced) {
                            Divider(Modifier.padding(vertical = 8.dp))
                            Text("Preset Filters:", style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(
                                    onClick = { filterExpression = "tcp port 80" },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("HTTP")
                                }
                                Button(
                                    onClick = { filterExpression = "tcp port 443" },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("HTTPS")
                                }
                                Button(
                                    onClick = { filterExpression = "udp port 53" },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("DNS")
                                }
                            }
                        }

                        Button(
                            onClick = { showAdvanced = !showAdvanced },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(if (showAdvanced) "Hide Advanced" else "Advanced")
                        }
                    }
                }
            }

            item {
                Text("Captured: ${packets.size} packets", style = MaterialTheme.typography.titleSmall)
            }

            if (packetStats.isNotEmpty()) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Protocol Summary:", style = MaterialTheme.typography.labelSmall)
                            packetStats.forEach { (protocol, count) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(protocol, style = MaterialTheme.typography.labelSmall)
                                    Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            items(packets.take(50)) { packet ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            "${packet.protocol} | ${packet.source} → ${packet.destination}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            packet.payload.take(80),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            item {
                Text("Log", style = MaterialTheme.typography.titleSmall)
            }

            item {
                Card(modifier = Modifier.heightIn(min = 150.dp)) {
                    Text(
                        snifferLog,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

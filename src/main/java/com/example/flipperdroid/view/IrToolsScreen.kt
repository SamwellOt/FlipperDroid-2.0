package com.example.flipperdroid.view

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.IrToolsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrToolsScreen(
    navController: NavController,
    viewModel: IrToolsViewModel = viewModel()
) {
    val running by viewModel.running.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val bruteCommand by viewModel.bruteCommand.collectAsState()

    var protocol by remember { mutableStateOf("NEC") }
    var addressHex by remember { mutableStateOf("04") }
    var delayMs by remember { mutableStateOf(300f) }
    val protocols = listOf("NEC", "NECext", "Samsung32", "SIRC", "RC5", "Kaseikyo")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IR Tools") },
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
                .verticalScroll(rememberScrollState())
        ) {
            if (!viewModel.hasEmitter) {
                Text(
                    "No IR emitter detected — patterns won't actually transmit.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
            }

            // --- TV-B-Gone ---
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("TV-B-Gone", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Fire every known power code in sequence to switch off any TV, " +
                            "projector or digital display in range.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.tvBGone() },
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Fire all power codes")
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { viewModel.tvBGone(rounds = 3) },
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Fire 3× (stubborn displays)")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Brute-forcer ---
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Command brute-forcer", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Sweep commands 0–255 for a protocol + address to map an unknown " +
                            "remote. Watch the device and note which value triggers it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        protocols.forEach { p ->
                            FilterChip(
                                selected = protocol == p,
                                onClick = { protocol = p },
                                label = { Text(p) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addressHex,
                        onValueChange = { addressHex = it.filter { c -> c.isLetterOrDigit() }.take(4) },
                        label = { Text("Address (hex)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Ascii
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("Delay between commands: ${delayMs.toInt()} ms")
                    Slider(
                        value = delayMs,
                        onValueChange = { delayMs = it },
                        valueRange = 150f..1000f
                    )

                    Spacer(Modifier.height(8.dp))
                    val shown = if (bruteCommand >= 0)
                        "0x${bruteCommand.toString(16).uppercase().padStart(2, '0')} ($bruteCommand)"
                    else "—"
                    Text(
                        "Current command: $shown",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val addr = addressHex.toIntOrNull(16) ?: 0
                            viewModel.bruteForce(protocol, addr, 0, 255, delayMs.toLong())
                        },
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start sweep (0–255)")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (running) {
                Button(
                    onClick = { viewModel.stop() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop")
                }
                Spacer(Modifier.height(8.dp))
            }

            if (progress.isNotEmpty()) {
                Text(progress, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

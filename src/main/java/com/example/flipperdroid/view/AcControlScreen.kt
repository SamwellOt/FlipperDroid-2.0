package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.infrared.AcProtocols
import com.example.flipperdroid.viewmodel.AcControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcControlScreen(
    navController: NavController,
    viewModel: AcControlViewModel = viewModel()
) {
    val status by viewModel.status.collectAsState()

    var brand by remember { mutableStateOf(AcProtocols.Brand.GREE) }
    var brandMenu by remember { mutableStateOf(false) }
    var power by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(AcProtocols.MODE_COOL) }
    var temp by remember { mutableStateOf(22f) }
    var fan by remember { mutableStateOf(AcProtocols.FAN_AUTO) }
    var swing by remember { mutableStateOf(false) }

    val modes = listOf(
        "Auto" to AcProtocols.MODE_AUTO,
        "Cool" to AcProtocols.MODE_COOL,
        "Dry" to AcProtocols.MODE_DRY,
        "Fan" to AcProtocols.MODE_FAN,
        "Heat" to AcProtocols.MODE_HEAT
    )
    val fans = listOf(
        "Auto" to AcProtocols.FAN_AUTO,
        "Low" to AcProtocols.FAN_LOW,
        "Med" to AcProtocols.FAN_MED,
        "High" to AcProtocols.FAN_HIGH
    )

    fun send() = viewModel.send(brand, power, mode, temp.toInt(), fan, swing)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AC Control") },
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

            // Sélecteur de marque.
            Text("Brand", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Box {
                OutlinedButton(onClick = { brandMenu = true }) {
                    Text(brand.name)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = brandMenu, onDismissRequest = { brandMenu = false }) {
                    AcProtocols.Brand.values().forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b.name) },
                            onClick = { brand = b; brandMenu = false }
                        )
                    }
                }
            }

            if (brand != AcProtocols.Brand.GREE) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠ Reference encoding — not verified on hardware. Field maps (mode/fan/temp) may differ on your unit.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Power", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = power,
                    onCheckedChange = { power = it; send() }
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("Mode", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { (label, value) ->
                    FilterChip(
                        selected = mode == value,
                        onClick = { mode = value },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Temperature: ${temp.toInt()}°C", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = temp,
                onValueChange = { temp = it },
                valueRange = 16f..30f,
                steps = 13
            )

            Spacer(Modifier.height(8.dp))
            Text("Fan", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fans.forEach { (label, value) ->
                    FilterChip(
                        selected = fan == value,
                        onClick = { fan = value },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Swing", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Switch(checked = swing, onCheckedChange = { swing = it })
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { send() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Send to AC")
            }

            if (status.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

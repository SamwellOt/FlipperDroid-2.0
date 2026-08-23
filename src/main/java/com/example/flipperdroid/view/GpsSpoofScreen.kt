package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.GpsSpoofViewModel

/** GPS spoofing via mock location. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsSpoofScreen(
    navController: NavController,
    viewModel: GpsSpoofViewModel = viewModel()
) {
    val active by viewModel.active.collectAsState()
    val status by viewModel.status.collectAsState()
    var lat by remember { mutableStateOf("48.8584") }
    var lon by remember { mutableStateOf("2.2945") }

    val presets = listOf(
        "Eiffel Tower" to (48.8584 to 2.2945),
        "Times Square" to (40.7580 to -73.9855),
        "Cristo Redentor" to (-22.9519 to -43.2105),
        "Null Island" to (0.0 to 0.0),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GPS Spoof") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            Modifier.fillMaxSize().padding(paddingValues).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Sets a fake GPS location. First enable this app in Developer Options → " +
                    "\"Select mock location app\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(lon, { lon = it }, label = { Text("Longitude") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Text("Presets:", style = MaterialTheme.typography.labelLarge)
            presets.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (name, coords) ->
                        OutlinedButton(
                            onClick = {
                                lat = coords.first.toString(); lon = coords.second.toString()
                                if (active) viewModel.update(coords.first, coords.second)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(name, style = MaterialTheme.typography.labelSmall) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (active) {
                Button(onClick = { viewModel.stop() }, modifier = Modifier.fillMaxWidth()) { Text("Stop spoofing") }
                Button(
                    onClick = {
                        val la = lat.toDoubleOrNull(); val lo = lon.toDoubleOrNull()
                        if (la != null && lo != null) viewModel.update(la, lo)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Update location") }
            } else {
                Button(
                    onClick = {
                        val la = lat.toDoubleOrNull(); val lo = lon.toDoubleOrNull()
                        if (la != null && lo != null) viewModel.start(la, lo)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Start spoofing") }
            }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

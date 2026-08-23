package com.example.flipperdroid.view

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
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
import com.example.flipperdroid.viewmodel.WardrivingViewModel

/**
 * Ecran Wardriving : cartographie des réseaux Wi-Fi avec position GPS et
 * export CSV compatible Wigle/WiGLE.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrivingScreen(
    navController: NavController,
    viewModel: WardrivingViewModel = viewModel()
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val status by viewModel.status.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasPermission = result.values.any { it } }

    LaunchedEffect(Unit) { viewModel.initialize() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wardriving") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportCsv() }, enabled = entries.isNotEmpty()) {
                        Icon(Icons.Default.Save, contentDescription = "Export CSV")
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
            if (!hasPermission) {
                Text("Precise location permission is required for wardriving.", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }) { Text("Grant location") }
                return@Column
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRunning) {
                    Button(onClick = { viewModel.stop() }) { Text("Stop") }
                } else {
                    Button(onClick = { viewModel.start() }) { Text("Start") }
                }
                OutlinedButton(onClick = { viewModel.exportCsv() }, enabled = entries.isNotEmpty()) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Export CSV")
                }
            }

            if (isRunning) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (status.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(status, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            Text("Networks: ${entries.size}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries) { e ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(if (e.ssid.isBlank()) "<Hidden>" else e.ssid, fontWeight = FontWeight.Bold)
                            Text(e.mac, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "ch ${e.channel} • ${e.rssi} dBm • ${"%.5f".format(e.lat)}, ${"%.5f".format(e.lon)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

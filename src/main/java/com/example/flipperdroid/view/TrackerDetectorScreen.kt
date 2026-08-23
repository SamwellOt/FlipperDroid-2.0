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
import com.example.flipperdroid.viewmodel.TrackerDetectorViewModel

/** Détecteur de trackers BLE (anti-stalking). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerDetectorScreen(
    navController: NavController,
    viewModel: TrackerDetectorViewModel = viewModel()
) {
    val context = LocalContext.current
    val trackers by viewModel.trackers.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val status by viewModel.status.collectAsState()

    val perms = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.initialize(context) }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        val granted = perms.all { ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (!granted) launcher.launch(perms)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracker Detector") },
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
        Column(Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Text(
                "Finds nearby BLE trackers (Apple Find My/AirTag, Samsung SmartTag, Tile). " +
                    "A tracker that follows you across places may indicate stalking.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            if (!permissionsGranted) {
                Button(onClick = { launcher.launch(perms) }) { Text("Grant Bluetooth permission") }
                return@Column
            }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isScanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))

            if (trackers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (isScanning) "Scanning…" else "No trackers detected. Tap Scan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(trackers) { t ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(t.type, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Text(t.name, style = MaterialTheme.typography.bodyMedium)
                                Text("${t.address}   •   ${t.rssi} dBm", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

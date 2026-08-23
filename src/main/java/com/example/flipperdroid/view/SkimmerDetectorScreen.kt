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
import androidx.compose.material.icons.filled.Warning
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
import com.example.flipperdroid.viewmodel.SkimmerDetectorViewModel

/**
 * Détecteur de skimmers : scanne Bluetooth Classic + BLE et signale les
 * modules série suspects (HC-05/HC-06, etc.).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkimmerDetectorScreen(
    navController: NavController,
    viewModel: SkimmerDetectorViewModel = viewModel()
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val status by viewModel.status.collectAsState()

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
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
                title = { Text("Skimmer Detector") },
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
        ) {
            if (!permissionsGranted) {
                Text("Bluetooth permissions are required to scan.", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { permissionLauncher.launch(permissions) }) { Text("Grant permissions") }
                return@Column
            }

            Text(
                "Scans Bluetooth for serial modules (HC-05/HC-06…) commonly used in card skimmers. " +
                    "A flag is a heuristic, not proof.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isScanning) {
                    Button(onClick = { viewModel.stopScan() }) { Text("Stop") }
                } else {
                    Button(onClick = { viewModel.startScan() }) { Text("Scan") }
                }
            }

            if (isScanning) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (status.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(status, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(devices) { d ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (d.suspicious) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (d.suspicious) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Suspicious",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(d.name, fontWeight = FontWeight.Bold)
                                Text("${d.address} • ${d.type} • ${d.rssi} dBm",
                                    style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                if (d.suspicious) {
                                    Text(d.reason, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

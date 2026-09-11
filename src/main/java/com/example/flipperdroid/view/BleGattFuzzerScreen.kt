package com.example.flipperdroid.view

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.BleGattFuzzerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleGattFuzzerScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: BleGattFuzzerViewModel = viewModel { BleGattFuzzerViewModel(context) }

    val isFuzzing by viewModel.isFuzzing.collectAsState()
    val log by viewModel.log.collectAsState()

    var permissionsGranted by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<String?>(null) }

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.BLUETOOTH)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissions)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE GATT Fuzzer") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "BLE GATT Fuzzer",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Write malicious payloads to BLE characteristics to discover vulnerabilities",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.startFuzzing(android.bluetooth.BluetoothAdapter.getDefaultAdapter()!!.bondedDevices.firstOrNull() ?: return@Button)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isFuzzing && permissionsGranted
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Fuzz")
                    }

                    Button(
                        onClick = { viewModel.stopFuzzing() },
                        modifier = Modifier.weight(1f),
                        enabled = isFuzzing
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop")
                    }

                    Button(
                        onClick = { viewModel.clearLogs() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, null)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 400.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp)
                    ) {
                        LazyColumn {
                            item {
                                Text(
                                    log,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "How it works:",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "• Connects to bonded BLE devices\n" +
                            "• Discovers all GATT services/characteristics\n" +
                            "• Writes malformed payloads (overflow, injection, etc)\n" +
                            "• Captures responses and potential crashes\n" +
                            "• Logs all interactions for analysis",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

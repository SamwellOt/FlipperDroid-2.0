package com.example.flipperdroid.view

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.BeaconViewModel

/**
 * Émetteur de balises BLE : iBeacon, Eddystone-URL et manufacturer data custom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeaconScreen(
    navController: NavController,
    viewModel: BeaconViewModel = viewModel()
) {
    val context = LocalContext.current
    val isAdvertising by viewModel.isAdvertising.collectAsState()
    val status by viewModel.status.collectAsState()

    var uuid by remember { mutableStateOf("f7826da6-4fa2-4e98-8024-bc5b71e0893e") }
    var major by remember { mutableStateOf("1") }
    var minor by remember { mutableStateOf("1") }
    var url by remember { mutableStateOf("https://example.com") }
    var companyId by remember { mutableStateOf("0x00FF") }
    var customHex by remember { mutableStateOf("01020304") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE Beacon") },
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (status.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Text(status, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (isAdvertising) {
                Button(onClick = { viewModel.stop() }, modifier = Modifier.fillMaxWidth()) { Text("Stop advertising") }
            }

            // iBeacon
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("iBeacon", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(uuid, { uuid = it }, label = { Text("UUID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(major, { major = it }, label = { Text("Major") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(minor, { minor = it }, label = { Text("Minor") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button(onClick = {
                        viewModel.startIBeacon(uuid.trim(), major.toIntOrNull() ?: 0, minor.toIntOrNull() ?: 0)
                    }) { Text("Broadcast iBeacon") }
                }
            }

            // Eddystone URL
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Eddystone-URL", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(url, { url = it }, label = { Text("URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { viewModel.startEddystoneUrl(url.trim()) }) { Text("Broadcast Eddystone") }
                }
            }

            // Custom manufacturer data
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Custom manufacturer data", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(companyId, { companyId = it }, label = { Text("Company ID (hex, e.g. 0x00FF)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(customHex, { customHex = it }, label = { Text("Data (hex)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        val cid = companyId.trim().removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: 0
                        viewModel.startCustom(cid, customHex)
                    }) { Text("Broadcast custom") }
                }
            }
        }
    }
}

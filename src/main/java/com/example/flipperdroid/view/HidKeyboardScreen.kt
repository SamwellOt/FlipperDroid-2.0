package com.example.flipperdroid.view

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.HidKeyboardViewModel

/** Clavier HID Bluetooth : tape du texte sur un PC/tablette apparié, sans câble. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HidKeyboardScreen(navController: NavController) {
    // BluetoothHidDevice n'existe qu'à partir d'Android 9 (API 28).
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        UnsupportedScreen(navController, "BLE Keyboard", "Requires Android 9 (API 28) or newer.")
        return
    }
    val viewModel: HidKeyboardViewModel = viewModel()
    val context = LocalContext.current
    val status by viewModel.status.collectAsState()
    val connected by viewModel.connected.collectAsState()
    var text by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { if (!initialized) { viewModel.initialize(context); initialized = true } }

    LaunchedEffect(Unit) {
        val needsPerm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsPerm) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            viewModel.initialize(context); initialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE Keyboard") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Status: $status", style = MaterialTheme.typography.bodyMedium)
                    Text(if (connected) "Host connected ✓" else "Waiting for host to pair…",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                "The phone appears as a Bluetooth keyboard. Pair it from the target PC/tablet " +
                    "(Bluetooth settings), then type below and send. Authorized use only.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text to type on host") },
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )
            Button(
                onClick = { viewModel.typeText(text) },
                enabled = connected && text.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Send keystrokes")
            }
        }
    }
}

/** Écran générique affiché quand une fonction n'est pas supportée par l'appareil/OS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnsupportedScreen(navController: NavController, title: String, message: String) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}

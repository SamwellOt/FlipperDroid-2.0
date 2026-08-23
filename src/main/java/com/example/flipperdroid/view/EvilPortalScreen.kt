package com.example.flipperdroid.view

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.EvilPortalViewModel

/** Evil Portal : hotspot local + page de capture d'identifiants (test autorisé). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvilPortalScreen(navController: NavController) {
    // LocalOnlyHotspot n'existe qu'à partir d'Android 8 (API 26).
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
        UnsupportedScreen(navController, "Evil Portal", "Requires Android 8 (API 26) or newer.")
        return
    }
    val viewModel: EvilPortalViewModel = viewModel()
    val context = LocalContext.current
    val isRunning by viewModel.isRunning.collectAsState()
    val ssid by viewModel.ssid.collectAsState()
    val password by viewModel.password.collectAsState()
    val status by viewModel.status.collectAsState()
    val captured by viewModel.captured.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasPermission = result.values.any { it } }

    LaunchedEffect(Unit) { viewModel.initialize(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Evil Portal") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Text(
                "Creates a local Wi-Fi hotspot and a fake login page that captures submitted " +
                    "credentials. Authorized security testing only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(12.dp))

            if (!hasPermission) {
                Button(onClick = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                }) { Text("Grant location (required for hotspot)") }
                return@Column
            }

            if (isRunning) {
                Button(onClick = { viewModel.stop() }) { Text("Stop portal") }
            } else {
                Button(onClick = { viewModel.start() }) { Text("Start portal") }
            }

            if (status.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(status, style = MaterialTheme.typography.bodySmall)
            }

            if (isRunning) {
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("SSID: $ssid", fontWeight = FontWeight.Bold)
                        Text("Password: $password", fontFamily = FontFamily.Monospace)
                        Text("Portal: http://192.168.49.1:8080", fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Captured credentials (${captured.size})", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f)) {
                items(captured) { c ->
                    Text(c, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

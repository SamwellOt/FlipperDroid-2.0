package com.example.flipperdroid.view

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.WifiAnalyzerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiAnalyzerScreen(
    navController: NavController,
    viewModel: WifiAnalyzerViewModel = viewModel()
) {
    val context = LocalContext.current
    val aps by viewModel.aps.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val status by viewModel.status.collectAsState()
    var macInput by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.checkPermissions() }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            viewModel.scan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi Analyzer") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.scan() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan")
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
                Text("Location permission is required to scan Wi-Fi.", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text("Grant location") }
                return@Column
            }

            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))

            Text("2.4 GHz channels (1–14)", style = MaterialTheme.typography.titleSmall)
            ChannelChart(viewModel.channelUsage("2.4 GHz"), 1..14)
            Spacer(Modifier.height(12.dp))
            Text("5 GHz channels", style = MaterialTheme.typography.titleSmall)
            ChannelChart(viewModel.channelUsage("5 GHz"), 34..165)

            Spacer(Modifier.height(12.dp))
            // Outil root : spoof MAC
            Text("MAC spoofing (root)", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = macInput,
                    onValueChange = { macInput = it },
                    label = { Text("MAC (empty = random)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.spoofMac(macInput) }) { Text("Spoof") }
            }

            Spacer(Modifier.height(12.dp))
            Text("Networks (${aps.size})", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(aps) { ap ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${ap.ssid}  (ch ${ap.channel}, ${ap.band})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("${ap.bssid} • ${ap.rssi} dBm", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelChart(usage: Map<Int, Int>, range: IntRange) {
    val max = (usage.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .padding(vertical = 8.dp)
    ) {
        val channels = range.toList()
        if (channels.isEmpty()) return@Canvas
        val slot = size.width / channels.size
        val barWidth = slot * 0.6f
        channels.forEachIndexed { i, ch ->
            val count = usage[ch] ?: 0
            val h = if (count == 0) 0f else (count.toFloat() / max) * (size.height - 6f)
            if (h > 0f) {
                drawRect(
                    color = barColor,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2f, size.height - h),
                    size = androidx.compose.ui.geometry.Size(barWidth, h)
                )
            }
        }
    }
}

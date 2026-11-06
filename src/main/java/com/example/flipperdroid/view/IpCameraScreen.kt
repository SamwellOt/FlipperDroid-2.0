package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.camera.IpCamera
import com.example.flipperdroid.viewmodel.IpCameraViewModel

/**
 * Caméras IP : découverte ONVIF (WS-Discovery) + scan de sous-réseau, sonde RTSP,
 * audit d'identifiants par défaut, et pilotage PTZ ONVIF (ContinuousMove/Stop).
 *
 * Réservé aux tests autorisés sur du matériel dont on a la permission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpCameraScreen(
    navController: NavController,
    viewModel: IpCameraViewModel = viewModel()
) {
    val cameras by viewModel.cameras.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val status by viewModel.status.collectAsState()
    val onvifPath by viewModel.onvifPath.collectAsState()

    var subnet by remember { mutableStateOf("192.168.1.0") }
    var manualHost by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("admin") }
    var pass by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("80") }
    var selected by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IP Cameras") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.clear() }) { Text("Clear") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Discover ONVIF cameras (WS-Discovery) or scan a /24 for camera ports, probe " +
                    "RTSP, audit default credentials, and drive PTZ. Authorized testing only.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))

            // --- Découverte ---
            Text("Discovery", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Button(
                onClick = { viewModel.discover() },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Default.Videocam, null); Spacer(Modifier.width(8.dp)); Text("ONVIF WS-Discovery") }

            OutlinedTextField(
                value = subnet, onValueChange = { subnet = it },
                label = { Text("Subnet base IP (e.g. 192.168.1.0)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.scanSubnet(subnet) },
                enabled = !isBusy, modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Scan /24 for camera ports") }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manualHost, onValueChange = { manualHost = it },
                    label = { Text("Add host manually") },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { viewModel.addManual(manualHost); manualHost = "" }) { Text("Add") }
            }

            Spacer(Modifier.height(8.dp))
            // --- Identifiants ONVIF partagés ---
            Text("ONVIF credentials", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = user, onValueChange = { user = it }, label = { Text("User") },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, label = { Text("Password") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("ONVIF port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = onvifPath, onValueChange = { viewModel.onvifPath.value = it },
                    label = { Text("Service path") },
                    singleLine = true, modifier = Modifier.weight(2f)
                )
            }

            if (isBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            if (status.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))
            val portInt = port.toIntOrNull() ?: 80
            cameras.forEach { cam ->
                CameraCard(
                    cam = cam,
                    expanded = selected == cam.host,
                    onToggle = { selected = if (selected == cam.host) null else cam.host },
                    onScan = { viewModel.scanHost(cam.host) },
                    onRtsp = { viewModel.probeRtsp(cam.host) },
                    onInfo = { viewModel.fetchDeviceInfo(cam.host, portInt, user, pass) },
                    onAudit = { viewModel.auditDefaultCredentials(cam.host, portInt) },
                    onProfiles = { viewModel.fetchProfiles(cam.host, portInt, user, pass) },
                    onPtzMove = { pan, tilt, zoom ->
                        viewModel.ptzMove(cam.host, portInt, user, pass, cam.profileTokens.firstOrNull() ?: "", pan, tilt, zoom)
                    },
                    onPtzStop = {
                        viewModel.ptzStop(cam.host, portInt, user, pass, cam.profileTokens.firstOrNull() ?: "")
                    }
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CameraCard(
    cam: IpCamera,
    expanded: Boolean,
    onToggle: () -> Unit,
    onScan: () -> Unit,
    onRtsp: () -> Unit,
    onInfo: () -> Unit,
    onAudit: () -> Unit,
    onProfiles: () -> Unit,
    onPtzMove: (Float, Float, Float) -> Unit,
    onPtzStop: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(cam.host, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    val sub = buildString {
                        if (cam.vendorHint.isNotEmpty()) append(cam.vendorHint)
                        if (cam.confidence > 0) append(" · ${cam.confidence}%")
                        if (cam.openPorts.isNotEmpty()) append(" · ports ${cam.openPorts.joinToString(",")}")
                    }
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onToggle) { Text(if (expanded) "Hide" else "Actions") }
            }

            if (cam.xaddr.isNotEmpty()) Text(cam.xaddr, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            if (cam.rtsp.isNotEmpty()) Text("RTSP: ${cam.rtsp}", style = MaterialTheme.typography.labelSmall)
            if (cam.deviceInfo.isNotEmpty()) Text("Device: ${cam.deviceInfo}", style = MaterialTheme.typography.labelSmall)
            if (cam.foundCredentials.isNotEmpty()) Text(
                "⚠ Default creds: ${cam.foundCredentials}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error
            )
            if (cam.profileTokens.isNotEmpty()) Text("Profiles: ${cam.profileTokens.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall)

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                FlowButtons(
                    "Scan ports" to onScan,
                    "RTSP probe" to onRtsp,
                    "Device info" to onInfo,
                    "Audit creds" to onAudit,
                    "Get profiles" to onProfiles
                )
                Spacer(Modifier.height(8.dp))
                Text("PTZ control", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                PtzPad(onMove = onPtzMove, onStop = onPtzStop)
            }
        }
    }
}

/** Petite grille de boutons d'action. */
@Composable
private fun FlowButtons(vararg actions: Pair<String, () -> Unit>) {
    Column {
        actions.toList().chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, action) ->
                    OutlinedButton(onClick = action, modifier = Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Croix directionnelle PTZ + zoom. Presse = ContinuousMove, "Stop" = arrêt. */
@Composable
private fun PtzPad(onMove: (Float, Float, Float) -> Unit, onStop: () -> Unit) {
    val speed = 0.5f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { onMove(0f, speed, 0f) }) { Icon(Icons.Default.KeyboardArrowUp, "Up") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onMove(-speed, 0f, 0f) }) { Icon(Icons.Default.KeyboardArrowLeft, "Left") }
            Button(onClick = onStop) { Text("Stop") }
            OutlinedButton(onClick = { onMove(speed, 0f, 0f) }) { Icon(Icons.Default.KeyboardArrowRight, "Right") }
        }
        OutlinedButton(onClick = { onMove(0f, -speed, 0f) }) { Icon(Icons.Default.KeyboardArrowDown, "Down") }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onMove(0f, 0f, speed) }) { Icon(Icons.Default.ZoomIn, "Zoom in"); Text("In") }
            OutlinedButton(onClick = { onMove(0f, 0f, -speed) }) { Icon(Icons.Default.ZoomOut, "Zoom out"); Text("Out") }
        }
    }
}

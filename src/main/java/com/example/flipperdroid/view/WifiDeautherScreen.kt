package com.example.flipperdroid.view

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.*

/**
 * Affiche l ecran principal de l outil wifi deauther
 *
 * Cette fonction compose l interface utilisateur permettant de scanner les réseaux wifi a proximite
 * Elle gere les permissions necessaires et affiche la liste des réseaux detectes sous forme de cartes
 *
 * @param navController controleur de navigation pour revenir a l ecran precedent
 * @param viewModel viewmodel contenant les donnees des réseaux et l etat du scan
 */
@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiDeautherScreen(
    navController: NavController,
    viewModel: WifiDeautherViewModel
) {
    val context = LocalContext.current
    val networks by viewModel.networks.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val selectedNetwork by viewModel.selectedNetwork.collectAsState()
    val isDeauthing by viewModel.isDeauthing.collectAsState()
    val deauthStatus by viewModel.deauthStatus.collectAsState()

    // Boîte de dialogue de l'attaque sur le réseau sélectionné
    selectedNetwork?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!isDeauthing) viewModel.clearSelection() },
            title = { Text("Target: ${target.ssid}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("BSSID: ${target.bssid}")
                    Text("Channel: ${target.channel}  •  ${target.frequency} MHz")
                    Text("Signal: ${target.rssi} dBm")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠ Deauthentication requires root and a Wi-Fi chipset with monitor mode + " +
                            "packet injection. Most phones cannot do this. Only attack networks you own " +
                            "or are authorized to test.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (deauthStatus.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(deauthStatus, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (isDeauthing) {
                    Button(onClick = { viewModel.stopDeauth() }) { Text("Stop") }
                } else {
                    Button(onClick = { viewModel.startDeauth(target) }) { Text("Start Deauth") }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.clearSelection() },
                    enabled = !isDeauthing
                ) { Text("Close") }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Réévalue les permissions après la réponse (même partielle) : sur Android < 13
        // NEARBY_WIFI_DEVICES n'existe pas, donc "tout accordé" ne serait jamais vrai
        // et le scan resterait bloqué.
        viewModel.initialize(context)
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi Deauther") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (permissionsGranted) {
                        IconButton(
                            onClick = { viewModel.startScan() },
                            enabled = !isScanning
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Scan Networks",
                                tint = if (isScanning) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                      else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (!permissionsGranted) {
                PermissionsRequest(
                    onRequestPermissions = {
                        val permissions = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE,
                            Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.ACCESS_NETWORK_STATE
                        )

                        // NEARBY_WIFI_DEVICES uniquement sur Android 13+ (sinon inexistante)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                        }

                        permissionLauncher.launch(permissions.toTypedArray())
                    },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
                return@Column
            }

            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (networks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isScanning) {
                            Text(
                                text = "Scanning for networks...",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Icon(
                                Icons.Default.WifiOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "No networks found\nTap refresh to start scanning",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Button(
                                onClick = { viewModel.startScan() },
                                enabled = !isScanning
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan Now")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(networks) { network ->
                        NetworkCard(network = network, viewModel = viewModel)
                    }
                }
            }
        }
    }
}
/**
 * Interface qui permet de demerder les permissions à l utilisateur
 *
 * Cette interface demander à l'utilisateur les permissions necessaires pour scanner les réseaux wifi
 *
 * @param onRequestPermissions fonction appelee lors de la demande de permissions
 * @param onOpenSettings fonction appelee pour ouvrir les parametres de l application
 */
@Composable
fun PermissionsRequest(
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.WifiLock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "This feature requires location and WiFi permissions to scan for nearby networks. Please grant the following permissions:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onRequestPermissions
        ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Grant Permissions")
        }
        
        TextButton(
            onClick = onOpenSettings
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Settings")
        }
    }
}
/**
 * Affiche une carte contenant les informations d un réseau wifi
 *
 * Cette carte presente le nom le bssid le niveau de signal le canal la frequence le type de securite et le moment de la derniere detection
 * Les couleurs de l icone wifi varient en fonction de la qualite du signal
 *
 * @param network objet representant un réseau wifi detecte
 * @param viewModel viewmodel utilise pour evaluer la force du signal et le type de securite
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkCard(
    network: WifiNetwork,
    viewModel: WifiDeautherViewModel
) {
    val networkStrength = viewModel.getNetworkStrength(network.rssi)
    val securityType = viewModel.getSecurityType(network.capabilities)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { viewModel.selectNetwork(network) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = network.ssid,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = network.bssid,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Signal Strength",
                    tint = when (networkStrength) {
                        NetworkStrength.EXCELLENT -> MaterialTheme.colorScheme.primary
                        NetworkStrength.GOOD -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        NetworkStrength.FAIR -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        NetworkStrength.POOR -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Channel ${network.channel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${network.frequency} MHz",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = securityType.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Signal: ${network.rssi} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            Text(
                text = "Last seen: ${network.lastSeen}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
} 
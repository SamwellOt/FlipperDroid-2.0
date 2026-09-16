package com.example.flipperdroid.view

import android.content.Context
import android.hardware.ConsumerIrManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flipperdroid.infrared.IrProtocols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class BrandProfile(
    val name: String,
    val protocol: String,
    val address: Int,
    val commands: Map<String, Int>,
    val repeatCount: Int = 1,
    val repeatGapMs: Long = 0
)

private val TV_BRANDS = listOf(
    BrandProfile("Samsung", "Samsung32", 0x07, mapOf(
        "power" to 0x02, "vol_up" to 0x07, "vol_dn" to 0x0B,
        "ch_up" to 0x12, "ch_dn" to 0x10, "mute" to 0x0F, "source" to 0x01,
        "menu" to 0x1A, "up" to 0x60, "down" to 0x61,
        "left" to 0x65, "right" to 0x62, "ok" to 0x68,
        "back" to 0x58, "home" to 0x79,
        "0" to 0x11, "1" to 0x04, "2" to 0x05, "3" to 0x06,
        "4" to 0x08, "5" to 0x09, "6" to 0x0A, "7" to 0x0C,
        "8" to 0x0D, "9" to 0x0E
    ), repeatCount = 2, repeatGapMs = 46),
    BrandProfile("LG", "NEC", 0x04, mapOf(
        "power" to 0x08, "vol_up" to 0x02, "vol_dn" to 0x03,
        "ch_up" to 0x00, "ch_dn" to 0x01, "mute" to 0x09, "source" to 0x0B,
        "menu" to 0x43, "up" to 0x40, "down" to 0x41,
        "left" to 0x07, "right" to 0x06, "ok" to 0x44, "back" to 0x28,
        "0" to 0x10, "1" to 0x11, "2" to 0x12, "3" to 0x13,
        "4" to 0x14, "5" to 0x15, "6" to 0x16, "7" to 0x17,
        "8" to 0x18, "9" to 0x19
    )),
    BrandProfile("Sony", "SIRC", 0x01, mapOf(
        "power" to 0x15, "vol_up" to 0x12, "vol_dn" to 0x13,
        "ch_up" to 0x10, "ch_dn" to 0x11, "mute" to 0x14, "source" to 0x25,
        "0" to 0x00, "1" to 0x01, "2" to 0x02, "3" to 0x03,
        "4" to 0x04, "5" to 0x05, "6" to 0x06, "7" to 0x07,
        "8" to 0x08, "9" to 0x09
    ), repeatCount = 3, repeatGapMs = 30),
    BrandProfile("Panasonic", "Kaseikyo", 0x80, mapOf(
        "power" to 0x3D, "vol_up" to 0x20, "vol_dn" to 0x21,
        "ch_up" to 0x34, "ch_dn" to 0x35, "mute" to 0x32, "source" to 0xA0,
        "0" to 0x00, "1" to 0x01, "2" to 0x02, "3" to 0x03,
        "4" to 0x04, "5" to 0x05, "6" to 0x06, "7" to 0x07,
        "8" to 0x08, "9" to 0x09
    )),
    BrandProfile("Philips", "RC5", 0x00, mapOf(
        "power" to 0x0C, "vol_up" to 0x10, "vol_dn" to 0x11,
        "ch_up" to 0x20, "ch_dn" to 0x21, "mute" to 0x0D,
        "0" to 0x00, "1" to 0x01, "2" to 0x02, "3" to 0x03,
        "4" to 0x04, "5" to 0x05, "6" to 0x06, "7" to 0x07,
        "8" to 0x08, "9" to 0x09
    )),
    BrandProfile("Toshiba", "NEC", 0x40, mapOf(
        "power" to 0x12, "vol_up" to 0x16, "vol_dn" to 0x17,
        "ch_up" to 0x1A, "ch_dn" to 0x1B, "mute" to 0x14, "source" to 0x0B,
        "0" to 0x00, "1" to 0x01, "2" to 0x02, "3" to 0x03,
        "4" to 0x04, "5" to 0x05, "6" to 0x06, "7" to 0x07,
        "8" to 0x08, "9" to 0x09
    )),
    BrandProfile("Vizio", "NEC", 0x00, mapOf(
        "power" to 0x08, "vol_up" to 0x02, "vol_dn" to 0x03,
        "ch_up" to 0x00, "ch_dn" to 0x01, "mute" to 0x09, "source" to 0x0E
    )),
    BrandProfile("TCL", "NEC", 0x04, mapOf(
        "power" to 0x08, "vol_up" to 0x10, "vol_dn" to 0x11,
        "ch_up" to 0x20, "ch_dn" to 0x21, "mute" to 0x0D, "source" to 0x0B
    )),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfraredScreen(navController: NavController) {
    val context = LocalContext.current
    val irManager = remember {
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    }
    val hasIr = irManager?.hasIrEmitter() == true
    var selectedBrand by remember { mutableStateOf(TV_BRANDS[0]) }
    var forceIrUi by remember { mutableStateOf(false) }
    var lastStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val sendCommand: (String) -> Unit = { cmdKey ->
        val brand = selectedBrand
        val cmd = brand.commands[cmdKey]
        if (cmd != null) {
            scope.launch {
                val errorMsg = withContext(Dispatchers.IO) {
                    val signal = IrProtocols.encode(brand.protocol, brand.address, cmd)
                        ?: return@withContext "Unsupported protocol: ${brand.protocol}"
                    val (freq, pattern) = signal
                    try {
                        repeat(brand.repeatCount) { i ->
                            irManager?.transmit(freq, pattern)
                            if (i < brand.repeatCount - 1 && brand.repeatGapMs > 0) {
                                delay(brand.repeatGapMs)
                            }
                        }
                        null
                    } catch (e: Exception) {
                        "Transmit error: ${e.message}"
                    }
                }
                lastStatus = errorMsg ?: "${brand.name}: $cmdKey (${brand.protocol})"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IR Remote") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (!hasIr && !forceIrUi) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No infrared emitter detected on this device.",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { forceIrUi = true }) {
                        Text("Show IR Remote anyway")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TV_BRANDS.forEach { brand ->
                        FilterChip(
                            selected = selectedBrand == brand,
                            onClick = { selectedBrand = brand },
                            label = { Text(brand.name) }
                        )
                    }
                }

                if (lastStatus.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(lastStatus, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RemoteButton(Icons.Default.Power, "Power") { sendCommand("power") }
                    RemoteButton(Icons.AutoMirrored.Filled.VolumeOff, "Mute") { sendCommand("mute") }
                    if (selectedBrand.commands.containsKey("source")) {
                        RemoteButton(Icons.AutoMirrored.Filled.Input, "Source") { sendCommand("source") }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Volume", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        RemoteButton(Icons.AutoMirrored.Filled.VolumeUp, "Vol+") { sendCommand("vol_up") }
                        Spacer(Modifier.height(8.dp))
                        RemoteButton(Icons.AutoMirrored.Filled.VolumeDown, "Vol-") { sendCommand("vol_dn") }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Channel", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        RemoteButton(Icons.Default.KeyboardArrowUp, "Ch+") { sendCommand("ch_up") }
                        Spacer(Modifier.height(8.dp))
                        RemoteButton(Icons.Default.KeyboardArrowDown, "Ch-") { sendCommand("ch_dn") }
                    }
                }

                if (selectedBrand.commands.containsKey("ok")) {
                    Spacer(Modifier.height(16.dp))
                    Text("Navigation", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        if (selectedBrand.commands.containsKey("menu")) {
                            RemoteButton(Icons.Default.Menu, "Menu") { sendCommand("menu") }
                            Spacer(Modifier.width(16.dp))
                        }
                        RemoteButton(Icons.Default.KeyboardArrowUp, "Up") { sendCommand("up") }
                        if (selectedBrand.commands.containsKey("home")) {
                            Spacer(Modifier.width(16.dp))
                            RemoteButton(Icons.Default.Home, "Home") { sendCommand("home") }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        RemoteButton(Icons.Default.KeyboardArrowLeft, "Left") { sendCommand("left") }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { sendCommand("ok") },
                            modifier = Modifier.size(56.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("OK") }
                        Spacer(Modifier.width(8.dp))
                        RemoteButton(Icons.Default.KeyboardArrowRight, "Right") { sendCommand("right") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        if (selectedBrand.commands.containsKey("back")) {
                            RemoteButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") { sendCommand("back") }
                            Spacer(Modifier.width(16.dp))
                        }
                        RemoteButton(Icons.Default.KeyboardArrowDown, "Down") { sendCommand("down") }
                    }
                }

                if (selectedBrand.commands.containsKey("0")) {
                    Spacer(Modifier.height(16.dp))
                    Text("Keypad", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    for (row in 0 until 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (col in 1..3) {
                                val digit = row * 3 + col
                                OutlinedButton(
                                    onClick = { sendCommand("$digit") },
                                    modifier = Modifier.size(56.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("$digit") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedButton(
                            onClick = { sendCommand("0") },
                            modifier = Modifier.size(56.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("0") }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun RemoteButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
    }
}

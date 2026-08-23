package com.example.flipperdroid.view

import android.content.Context
import android.hardware.ConsumerIrManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import android.util.Log

/**
 * Construit une trame IR au protocole NEC (le plus répandu) pour la paire
 * adresse/commande fournie. Retourne un motif de durées (microsecondes)
 * alternant état haut/bas, prêt pour ConsumerIrManager.transmit().
 */
private fun buildNecPattern(address: Int, command: Int): IntArray {
    val addr = address and 0xFF
    val addrInv = addr.inv() and 0xFF
    val cmd = command and 0xFF
    val cmdInv = cmd.inv() and 0xFF
    val pattern = ArrayList<Int>()
    // En-tête NEC : 9 ms haut, 4,5 ms bas
    pattern.add(9000); pattern.add(4500)
    val bytes = intArrayOf(addr, addrInv, cmd, cmdInv)
    for (b in bytes) {
        for (i in 0 until 8) {
            val bit = (b shr i) and 1 // LSB en premier
            pattern.add(560)
            pattern.add(if (bit == 1) 1690 else 560)
        }
    }
    pattern.add(560) // rafale finale
    return pattern.toIntArray()
}

/** Envoie une commande NEC via l'émetteur IR (porteuse 38 kHz). */
private fun ConsumerIrManager.sendNec(address: Int, command: Int) {
    try {
        transmit(38000, buildNecPattern(address, command))
    } catch (e: Exception) {
        Log.e("InfraredScreen", "Echec transmission IR: ${e.message}")
    }
}

/**
 * Adresse NEC générique par type d'appareil. Les codes IR sont propres à
 * chaque marque : ce sont des valeurs d'exemple. Une vraie télécommande
 * universelle nécessiterait une base de données de codes (voir roadmap).
 */
private fun deviceAddress(device: String?): Int = when (device) {
    "TV" -> 0x04
    "Air Conditioner" -> 0x10
    "Audio System" -> 0x20
    "Projector" -> 0x30
    else -> 0x00
}

// Commandes NEC d'exemple (fonctions communes)
private const val IR_CMD_POWER = 0x02
private const val IR_CMD_MUTE = 0x09
private const val IR_CMD_VOL_UP = 0x1A
private const val IR_CMD_VOL_DOWN = 0x1E
private const val IR_CMD_CH_UP = 0x1B
private const val IR_CMD_CH_DOWN = 0x1F
private const val IR_CMD_INPUT = 0x0B

// Pavé numérique (codes NEC d'exemple : 1-9 = 0x04..0x0C, 0 = 0x11)
private val IR_DIGIT_CMDS = intArrayOf(
    0x11, // 0
    0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C // 1..9
)

/**
 * Ecran de controle infrarouge permettant de selectionner un type de telecommande
 * et d afficher les boutons correspondants a la fonction choisie
 *
 * @param navController Controleur de navigation pour gerer les ecrans
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfraredScreen(navController: NavController) {
    val context = LocalContext.current
    val irManager = remember { context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager }
    val hasIr = irManager?.hasIrEmitter() == true
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var forceIrUi by remember { mutableStateOf(false) }

    // Envoie une commande IR pour l'appareil actuellement sélectionné
    val sendIr: (Int) -> Unit = { command ->
        irManager?.sendNec(deviceAddress(selectedDevice), command)
    }

    /**
     * Liste des types de telecommandes disponibles avec leur icone associee
     */
    val remoteTypes = listOf(
        "TV" to Icons.Default.Tv,
        "Air Conditioner" to Icons.Default.AcUnit,
        "Audio System" to Icons.AutoMirrored.Filled.VolumeUp,
        "Projector" to Icons.Default.Cast,
        "Custom Remote" to Icons.Default.Add
    )

    Scaffold(
        topBar = {
            /**
             * Barre superieure avec bouton retour et titre
             */
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
                    Text("No infrared emitter detected on this device.", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { forceIrUi = true }) {
                        Text("Show IR Remote anyway")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(remoteTypes) { (type, icon) ->
                        ElevatedCard(
                            onClick = { selectedDevice = type },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = { Text(type) },
                                supportingContent = {
                                    Text(
                                        when (type) {
                                            "TV" -> "Control your TV"
                                            "Air Conditioner" -> "Control your AC"
                                            "Audio System" -> "Control your audio system"
                                            "Projector" -> "Control your projector"
                                            else -> "Add custom remote"
                                        }
                                    )
                                },
                                leadingContent = {
                                    Icon(icon, contentDescription = null)
                                },
                                trailingContent = {
                                    if (selectedDevice == type) {
                                        Icon(Icons.Default.Check, contentDescription = "Selected")
                                    }
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (selectedDevice != null && selectedDevice != "Custom Remote") {
                    // Télécommande universelle simplifiée
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(
                                onClick = { sendIr(IR_CMD_POWER) },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(Icons.Default.Power, contentDescription = "Power", modifier = Modifier.size(32.dp))
                            }
                            IconButton(
                                onClick = { sendIr(IR_CMD_MUTE) },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = "Mute", modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(
                                onClick = { sendIr(IR_CMD_VOL_UP) },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume Up", modifier = Modifier.size(32.dp))
                            }
                            IconButton(
                                onClick = { sendIr(IR_CMD_VOL_DOWN) },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Volume Down", modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(
                                onClick = { sendIr(IR_CMD_CH_UP) },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Channel Up", modifier = Modifier.size(32.dp))
                            }
                            IconButton(
                                onClick = { sendIr(IR_CMD_CH_DOWN) },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Channel Down", modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(
                                onClick = { sendIr(IR_CMD_INPUT) },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Input, contentDescription = "Input Source", modifier = Modifier.size(32.dp))
                            }
                        }
                        // Pavé numérique (utile pour TV/box)
                        if (selectedDevice == "TV" || selectedDevice == "Projector") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Keypad", style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            for (row in 0 until 3) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    for (col in 1..3) {
                                        val digit = row * 3 + col
                                        OutlinedButton(
                                            onClick = { sendIr(IR_DIGIT_CMDS[digit]) },
                                            modifier = Modifier.size(56.dp)
                                        ) { Text("$digit") }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                OutlinedButton(
                                    onClick = { sendIr(IR_DIGIT_CMDS[0]) },
                                    modifier = Modifier.size(56.dp)
                                ) { Text("0") }
                            }
                        }
                    }
                }
            }
        }
    }
}

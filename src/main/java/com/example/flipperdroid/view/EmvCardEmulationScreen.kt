package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.EmvCardEmulationViewModel

/** Émulation HCE d'une carte ISO-DEP capturée (replay APDU). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmvCardEmulationScreen(
    navController: NavController,
    viewModel: EmvCardEmulationViewModel
) {
    val isActive by viewModel.isEmulationActive.collectAsState()
    val captured by viewModel.capturedLabel.collectAsState()
    val status by viewModel.status.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Card Emulation (HCE)") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Emulate a captured ISO-DEP card: the phone replays the card's APDU responses " +
                    "to a reader via HCE.",
                style = MaterialTheme.typography.bodyLarge
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Captured card: ${captured ?: "none"}", fontWeight = FontWeight.Bold)
                    if (captured == null) {
                        Text(
                            "Capture one first: NFC Reader → \"Capture ISO-DEP card for emulation\".",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (captured != null) {
                if (isActive) {
                    Button(onClick = { viewModel.stopEmulation() }, Modifier.fillMaxWidth()) {
                        Text("Stop emulation")
                    }
                } else {
                    Button(onClick = { viewModel.startEmulation() }, Modifier.fillMaxWidth()) {
                        Text("Start emulation")
                    }
                }
                OutlinedButton(onClick = { viewModel.clearCapture() }, Modifier.fillMaxWidth()) {
                    Text("Clear captured card")
                }
            }

            if (status.isNotEmpty()) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "Notes (honest capability):\n" +
                    "• Works only for static ISO-DEP cards (some access cards).\n" +
                    "• EMV/bank cards can't be emulated to pay (dynamic cryptogram).\n" +
                    "• Mifare Classic can't be emulated by Android — HCE is ISO-DEP only. " +
                    "Reproduce it instead via NFC Reader → \"Clone dump to another card\" (magic card).\n" +
                    "• Keep the screen on and unlocked while emulating.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

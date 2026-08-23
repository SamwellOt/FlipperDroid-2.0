package com.example.flipperdroid.view

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.RootBadUsbViewModel

/**
 * BadUSB via USB Gadget (root) : édite et exécute un script DuckyScript envoyé
 * au PC branché en USB, en écrivant des rapports HID dans /dev/hidgX.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootBadUsbScreen(
    navController: NavController,
    viewModel: RootBadUsbViewModel = viewModel()
) {
    val status by viewModel.status.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val script by viewModel.script.collectAsState()

    LaunchedEffect(Unit) { viewModel.checkEnvironment() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BadUSB (Root Gadget)") },
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Status", style = MaterialTheme.typography.labelLarge)
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text(
                "Requires root and a kernel HID gadget (/dev/hidgX). Plug the phone into the target " +
                    "over USB. Real 802.11-style keystroke injection to the host. Authorized use only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            OutlinedButton(onClick = { viewModel.checkEnvironment() }) {
                Text("Re-check environment")
            }

            Text("Load a payload:", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RootBadUsbViewModel.PAYLOADS.forEach { (name, payload) ->
                    OutlinedButton(onClick = { viewModel.updateScript(payload) }, enabled = !isRunning) {
                        Text(name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            OutlinedTextField(
                value = script,
                onValueChange = { viewModel.updateScript(it) },
                label = { Text("DuckyScript") },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                enabled = !isRunning
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.runScript() }, enabled = !isRunning) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Run")
                }
                if (isRunning) {
                    OutlinedButton(onClick = { viewModel.stop() }) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
            }

            Text(
                "Supported: REM, DELAY, DEFAULT_DELAY, STRING, STRINGLN, ENTER, TAB, SPACE, ESC, " +
                    "BACKSPACE, DELETE, UP/DOWN/LEFT/RIGHT, GUI/CTRL/ALT/SHIFT [key], CTRL-ALT-DELETE, F1–F12.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

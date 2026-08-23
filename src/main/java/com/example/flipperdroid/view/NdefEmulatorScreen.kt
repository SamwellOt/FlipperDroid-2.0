package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.NdefHceViewModel

/** Émule une tag NDEF (HCE Type 4) : le téléphone livre une URL/texte quand on le lit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NdefEmulatorScreen(
    navController: NavController,
    viewModel: NdefHceViewModel = viewModel()
) {
    val label by viewModel.label.collectAsState()
    val status by viewModel.status.collectAsState()
    var content by remember { mutableStateOf("") }
    var isUrl by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NDEF Tag Emulator") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            Modifier.fillMaxSize().padding(paddingValues).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "The phone acts as an NDEF tag: another phone/reader in reader mode reads the URL or " +
                    "text you set here.",
                style = MaterialTheme.typography.bodyMedium
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Current tag content", fontWeight = FontWeight.Bold)
                    Text(label ?: "(empty)", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row {
                Row(Modifier.selectable(isUrl) { isUrl = true }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isUrl, onClick = { isUrl = true }); Text("URL")
                }
                Spacer(Modifier.width(16.dp))
                Row(Modifier.selectable(!isUrl) { isUrl = false }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !isUrl, onClick = { isUrl = false }); Text("Text")
                }
            }
            OutlinedTextField(
                value = content, onValueChange = { content = it },
                label = { Text(if (isUrl) "URL (https://…)" else "Text") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { if (content.isNotBlank()) { if (isUrl) viewModel.setUri(content.trim()) else viewModel.setText(content) } },
                enabled = content.isNotBlank(), modifier = Modifier.fillMaxWidth()
            ) { Text("Set emulated tag") }
            OutlinedButton(onClick = { viewModel.clear() }, modifier = Modifier.fillMaxWidth()) { Text("Clear") }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "Note: works with an NFC reader / another phone in reader mode. Phone-to-phone NDEF push " +
                    "(Android Beam) was removed in Android 10+, so a plain phone tapping this may not auto-read.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

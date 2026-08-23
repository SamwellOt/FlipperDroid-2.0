package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.NfcViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcScreen(
    navController: NavController,
    nfcViewModel: NfcViewModel
) {
    val currentTagUid by nfcViewModel.currentTagUid.collectAsState()
    val currentTagType by nfcViewModel.currentTagType.collectAsState()
    val currentTagDump by nfcViewModel.currentTagDump.collectAsState()
    val scanHistory by nfcViewModel.scanHistory.collectAsState()
    val logs by nfcViewModel.logs.collectAsState()
    val ndefContent by nfcViewModel.ndefContent.collectAsState()
    val foundKeys by nfcViewModel.foundKeys.collectAsState()
    val isAttacking by nfcViewModel.isAttacking.collectAsState()
    val cloneArmed by nfcViewModel.cloneArmed.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var cloneUid by remember { mutableStateOf("") }
    var ndefWrite by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NFC") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = !showHistory }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "History")
                    }
                    IconButton(onClick = { showLogs = !showLogs }) {
                        Icon(Icons.Default.Save, contentDescription = "Logs")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Section Lecture
            Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Card Reading", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("UID: ${currentTagUid ?: "-"}")
                    Text("Type: ${currentTagType ?: "-"}")
                    if (currentTagDump.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Dump:", fontWeight = FontWeight.Bold)
                        LazyColumn(Modifier.heightIn(max = 200.dp)) {
                            items(currentTagDump.size) { i ->
                                Text(currentTagDump[i], fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                        }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { nfcViewModel.onDumpExport() }) {
                                Icon(Icons.Default.Save, contentDescription = "Export")
                                Spacer(Modifier.width(4.dp))
                                Text("Export")
                            }
                            OutlinedButton(onClick = { nfcViewModel.saveAsNfc() }) {
                                Text("Save .nfc")
                            }
                        }
                        // Reproduction : réécrit le dump complet sur une carte cible.
                        Spacer(Modifier.height(8.dp))
                        if (cloneArmed) {
                            Text(
                                "Clone armed — tap the TARGET (magic/blank) card now…",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedButton(
                                onClick = { nfcViewModel.cancelClone() },
                                modifier = Modifier.padding(top = 4.dp)
                            ) { Text("Cancel clone") }
                        } else {
                            Button(onClick = { nfcViewModel.armClone() }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Clone dump to another card")
                            }
                        }
                        Text(
                            "Reproduction works only on writable Mifare Classic (magic/blank) cards. " +
                                "Android cannot emulate Mifare Classic (HCE is ISO-DEP only); EMV/bank cards can't be cloned.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    // Attaque par dictionnaire de clés Mifare
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { nfcViewModel.runDictionaryAttack() },
                        enabled = !isAttacking
                    ) {
                        Text(if (isAttacking) "Attacking..." else "Dictionary attack (Mifare keys)")
                    }
                    if (foundKeys.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Keys found:", fontWeight = FontWeight.Bold)
                        LazyColumn(Modifier.heightIn(max = 150.dp)) {
                            items(foundKeys.size) { i ->
                                Text(foundKeys[i], fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                            }
                        }
                    }
                }
            }
            // Section NDEF (lecture)
            if (ndefContent != null) {
                Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("NDEF Content", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(ndefContent ?: "")
                    }
                }
            }
            // Section NDEF (écriture)
            Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("NDEF Write", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ndefWrite,
                        onValueChange = { ndefWrite = it },
                        label = { Text("Text or URL to write") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { nfcViewModel.writeNdef(ndefWrite) }, Modifier.padding(top = 8.dp)) {
                        Text("Write NDEF to tag")
                    }
                }
            }
            // Section Clonage
            Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("UID Cloning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cloneUid,
                        onValueChange = { cloneUid = it },
                        label = { Text("New UID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { nfcViewModel.onCloneUid(cloneUid) }, Modifier.padding(top = 8.dp)) {
                        Text("Clone UID to card")
                    }
                }
            }
            // Section Logs
            if (showLogs) {
                Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        LazyColumn(Modifier.heightIn(max = 150.dp)) {
                            items(logs.size) { i ->
                                Text(logs[i], fontSize = MaterialTheme.typography.bodySmall.fontSize)
                            }
                        }
                        Button(onClick = { nfcViewModel.clearLogs() }, Modifier.padding(top = 8.dp)) {
                            Text("Clear logs")
                        }
                    }
                }
            }
            // Section Historique
            if (showHistory) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Scan History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        LazyColumn(Modifier.heightIn(max = 150.dp)) {
                            items(scanHistory.size) { i ->
                                val scan = scanHistory[scanHistory.size - 1 - i]
                                Text("${scan.timestamp} - UID: ${scan.uid ?: "-"} - Type: ${scan.type ?: "-"}")
                            }
                        }
                    }
                }
            }
        }
    }
}

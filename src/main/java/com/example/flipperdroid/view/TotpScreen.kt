package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.TotpViewModel
import kotlinx.coroutines.delay

/** Coffre 2FA/TOTP : codes à 6 chiffres qui tournent toutes les 30 s. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotpScreen(
    navController: NavController,
    viewModel: TotpViewModel = viewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }

    // Horloge qui avance chaque seconde
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("2FA Vault") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            if (accounts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No 2FA accounts yet. Tap + to add one (label + Base32 secret, or an otpauth:// URI).")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accounts) { account ->
                        val code = viewModel.codeFor(account, now)
                        val remaining = viewModel.secondsRemaining(account, now)
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(account.label, fontWeight = FontWeight.Bold)
                                    Text(
                                        code.chunked(3).joinToString(" "),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 28.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    LinearProgressIndicator(
                                        progress = { remaining / account.period.toFloat() },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Text("${remaining}s", Modifier.padding(horizontal = 8.dp))
                                IconButton(onClick = { viewModel.removeAccount(account) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add 2FA account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text("Label (or otpauth:// URI)") }, singleLine = true)
                    OutlinedTextField(secret, { secret = it }, label = { Text("Base32 secret") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addAccount(label.trim(), secret.trim())
                    label = ""; secret = ""; showAdd = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

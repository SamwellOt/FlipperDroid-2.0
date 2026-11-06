package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.util.SecureWindow
import com.example.flipperdroid.viewmodel.TotpViewModel
import kotlinx.coroutines.delay

/** Coffre 2FA/TOTP : codes qui tournent selon la période de chaque compte. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotpScreen(
    navController: NavController,
    viewModel: TotpViewModel = viewModel()
) {
    // Empêche captures d'écran et aperçu des tâches récentes pendant l'affichage des codes
    SecureWindow()

    val accounts by viewModel.accounts.collectAsState()
    val error by viewModel.error.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }

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
            FloatingActionButton(onClick = {
                viewModel.dismissError()
                showAdd = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            if (error != null && !showAdd) {
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
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
                                    Text(
                                        "${account.algorithm} · ${account.digits} digits · ${account.period}s",
                                        style = MaterialTheme.typography.labelSmall
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
            onDismissRequest = {
                viewModel.dismissError()
                showAdd = false
            },
            title = { Text("Add 2FA account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label (or otpauth:// URI)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = { Text("Base32 secret") },
                        singleLine = true,
                        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showSecret = !showSecret }) {
                                Icon(
                                    if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showSecret) "Hide secret" else "Show secret"
                                )
                            }
                        }
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    // Le dialogue ne se ferme que si l'ajout réussit
                    if (viewModel.addAccount(label.trim(), secret.trim())) {
                        label = ""
                        secret = ""
                        showSecret = false
                        showAdd = false
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissError()
                    showAdd = false
                }) { Text("Cancel") }
            }
        )
    }
}

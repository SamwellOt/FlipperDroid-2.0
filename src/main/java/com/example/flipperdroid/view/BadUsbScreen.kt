package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.BadUsbViewModel

/**
 * Ecran BadUSB permettant d envoyer un script clavier depuis le telephone
 *
 * @param navController controleur de navigation
 * @param viewModel vue modele gere l etat de la connexion usb et le script clavier
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadUsbScreen(
    navController: NavController,
    viewModel: BadUsbViewModel
) {
    val isUsbHostAvailable by viewModel.isUsbHostAvailable
    val isConnected by viewModel.isConnected
    val currentScript = viewModel.currentScript.collectAsState()
    val status = viewModel.status.collectAsState()

    Scaffold(
        topBar = {
            /**
             * Barre superieure avec bouton retour et titre
             */
            TopAppBar(
                title = { Text("Bad USB") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            /**
             * Carte affichant le statut de la connexion USB
             */
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when {
                            !isUsbHostAvailable -> "USB Host not supported on this device"
                            !isConnected -> "No USB HID device attached (host mode)"
                            else -> "Attached USB HID device claimed (host mode)"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (!isUsbHostAvailable || !isConnected)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )

                    if (status.value.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = status.value,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            /**
             * Champ de saisie du script clavier
             */
            OutlinedTextField(
                value = currentScript.value,
                onValueChange = { viewModel.updateScript(it) },
                label = { Text("Enter text to type") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text
                ),
                enabled = isUsbHostAvailable && isConnected
            )

            /**
             * Bouton pour executer le script clavier
             */
            Button(
                onClick = { viewModel.executeScript() },
                enabled = isUsbHostAvailable && isConnected && currentScript.value.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Execute",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Execute Script")
            }

            /**
             * Carte d alerte si l appareil n est pas connecte en USB
             */
            if (isUsbHostAvailable && !isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "USB Connection Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Experimental: in host mode the phone cannot act as a keyboard for a PC. For real keystroke injection use \"BadUSB (root)\" (USB gadget).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

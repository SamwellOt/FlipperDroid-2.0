package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack

/**
 * Ecran de presentation de l application avec les informations legales et les fonctionnalites
 *
 * @param navController controleur de navigation pour gerer le retour arriere
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    val uriHandler = LocalUriHandler.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                /**
                 * Affiche le logo circulaire de l application
                 */
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Android,
                            contentDescription = "FlipperDroid Logo",
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "FlipperDroid",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Text(
                    "Version ${com.example.flipperdroid.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "FlipperDroid is an Android application that brings some of the Flipper Zero's functionality to your smartphone. It provides a set of tools for security research and hardware interaction.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Development Team",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                /**
                 * Affiche les membres de l equipe et un lien vers le GitHub
                 */
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Jeremy D.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                uriHandler.openUri("https://github.com/Jeremiznoo/FlipperDroid/")
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View on GitHub")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    "Features",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                /**
                 * Affiche les fonctionnalites principales de l application
                 */
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        ListItem(
                            headlineContent = { Text("NFC Reader") },
                            supportingContent = { Text("Read and analyze NFC tags") },
                            leadingContent = {
                                Icon(Icons.Default.Nfc, contentDescription = null)
                            }
                        )

                        ListItem(
                            headlineContent = { Text("Bluetooth LE Scanner") },
                            supportingContent = { Text("Scan and analyze BLE devices") },
                            leadingContent = {
                                Icon(Icons.Default.Bluetooth, contentDescription = null)
                            }
                        )

                        ListItem(
                            headlineContent = { Text("Network Tools") },
                            supportingContent = { Text("Various network analysis tools") },
                            leadingContent = {
                                Icon(Icons.Default.Router, contentDescription = null)
                            }
                        )

                        ListItem(
                            headlineContent = { Text("IR Remote") },
                            supportingContent = { Text("Control IR devices") },
                            leadingContent = {
                                Icon(Icons.Default.Settings, contentDescription = null)
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    "Legal",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                /**
                 * Affiche les mentions legales de l application
                 */
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        ListItem(
                            headlineContent = { Text("Open MIT licenses") },
                            supportingContent = { Text("View MIT licenses") },
                            leadingContent = {
                                Icon(Icons.Default.Info, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                navController.navigate("legal_mit")
                            }
                        )

                        ListItem(
                            headlineContent = { Text("Legal Notice ") },
                            supportingContent = { Text("View our Legal Notice") },
                            leadingContent = {
                                Icon(Icons.Default.Security, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                navController.navigate("legal_mentions")
                            }
                        )

                        ListItem(
                            headlineContent = { Text("Terms of Service") },
                            supportingContent = { Text("View terms of service") },
                            leadingContent = {
                                Icon(Icons.Default.Description, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                navController.navigate("legal_cgu")
                            }
                        )
                    }
                }
            }
        }
    }
}

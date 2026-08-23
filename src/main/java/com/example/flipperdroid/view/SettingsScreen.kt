package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.ThemeViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
/**
 * Affiche l ecran des parametres avec plusieurs sections configurables
 *
 * Cette interface permet de gerer les preferences liees a l apparence aux notifications au retour haptique et sonore ainsi qu au stockage
 * Les options incluent le theme sombre la persistance de l ecran les notifications les vibrations les sons la suppression du cache et l export des donnees
 *
 * @param navController controleur de navigation permettant de revenir a l ecran precedent
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, themeViewModel: ThemeViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE) }
    val darkMode by themeViewModel.isDarkMode.collectAsState()
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", true)) }
    var keepScreenOn by remember { mutableStateOf(prefs.getBoolean("keepScreenOn", false)) }
    var vibrationEnabled by remember { mutableStateOf(prefs.getBoolean("vibration", true)) }
    var soundEnabled by remember { mutableStateOf(prefs.getBoolean("sound", true)) }

    // Applique réellement "garder l'écran allumé" à la fenêtre de l'activité
    val activity = context as? android.app.Activity
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text("Dark Mode") },
                        supportingContent = { Text("Enable dark theme") },
                        leadingContent = {
                            Icon(Icons.Default.DarkMode, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = darkMode,
                                onCheckedChange = { themeViewModel.setDarkMode(it) }
                            )
                        }
                    )
                }
            }

            item {
                Text(
                    "Behavior",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        SettingToggle("Keep screen on", "Prevent the screen from sleeping", keepScreenOn) {
                            keepScreenOn = it; prefs.edit().putBoolean("keepScreenOn", it).apply()
                        }
                        SettingToggle("Notifications", "Enable in-app notifications", notifications) {
                            notifications = it; prefs.edit().putBoolean("notifications", it).apply()
                        }
                        SettingToggle("Vibration", "Haptic feedback", vibrationEnabled) {
                            vibrationEnabled = it; prefs.edit().putBoolean("vibration", it).apply()
                        }
                        SettingToggle("Sound", "Sound feedback", soundEnabled) {
                            soundEnabled = it; prefs.edit().putBoolean("sound", it).apply()
                        }
                    }
                }
            }

            item {
                Text(
                    "Help & Support",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text("Manage permissions") },
                        supportingContent = { Text("Open system settings to manage the app's permissions.") },
                        leadingContent = {
                            Icon(Icons.Default.Settings, contentDescription = null)
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open")
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) }
    )
}

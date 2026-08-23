package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

private data class Cred(val device: String, val user: String, val pass: String)

private val DEFAULT_CREDS = listOf(
    Cred("Cisco (generic)", "cisco", "cisco"),
    Cred("Cisco router", "admin", "admin"),
    Cred("TP-Link router", "admin", "admin"),
    Cred("D-Link router", "admin", "(blank)"),
    Cred("Netgear router", "admin", "password"),
    Cred("Linksys router", "admin", "admin"),
    Cred("ASUS router", "admin", "admin"),
    Cred("Huawei router", "admin", "admin"),
    Cred("ZTE router", "admin", "admin"),
    Cred("Technicolor/Thomson", "admin", "admin"),
    Cred("Ubiquiti UniFi", "ubnt", "ubnt"),
    Cred("MikroTik RouterOS", "admin", "(blank)"),
    Cred("pfSense", "admin", "pfsense"),
    Cred("Zyxel", "admin", "1234"),
    Cred("Belkin", "admin", "(blank)"),
    Cred("Tenda", "admin", "admin"),
    Cred("Hikvision camera", "admin", "12345"),
    Cred("Dahua camera", "admin", "admin"),
    Cred("Axis camera", "root", "pass"),
    Cred("Foscam camera", "admin", "(blank)"),
    Cred("Raspberry Pi (Raspbian)", "pi", "raspberry"),
    Cred("Kali Linux (old)", "root", "toor"),
    Cred("HP iLO", "Administrator", "(on label)"),
    Cred("Dell iDRAC", "root", "calvin"),
    Cred("Tomcat manager", "tomcat", "tomcat"),
    Cred("MySQL (default)", "root", "(blank)"),
    Cred("PostgreSQL", "postgres", "postgres"),
    Cred("Grafana", "admin", "admin"),
    Cred("Jenkins", "admin", "admin"),
    Cred("phpMyAdmin", "root", "(blank)"),
    Cred("Webmin", "admin", "admin"),
    Cred("HC-05/HC-06 BT module", "-", "1234 / 0000"),
    Cred("ESP8266/ESP32 AP", "-", "12345678"),
    Cred("Arris/Motorola modem", "admin", "motorola"),
)

/** Base offline de credenciais padrão (referência — teste autorizado apenas). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultCredsScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) DEFAULT_CREDS
        else DEFAULT_CREDS.filter {
            it.device.contains(query, true) || it.user.contains(query, true) || it.pass.contains(query, true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Default Credentials") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Search device / user / password") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Reference list for authorized testing. Try only on devices you own or are allowed to test.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered) { c ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(c.device, fontWeight = FontWeight.Bold)
                            Text("user: ${c.user}    pass: ${c.pass}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

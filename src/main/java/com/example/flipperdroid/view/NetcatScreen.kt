package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.NetcatViewModel

/** Netcat (TCP/UDP) + serveur HTTP de fichiers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetcatScreen(
    navController: NavController,
    viewModel: NetcatViewModel = viewModel()
) {
    val log by viewModel.log.collectAsState()
    val connected by viewModel.connected.collectAsState()
    val httpInfo by viewModel.httpInfo.collectAsState()

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var httpPort by remember { mutableStateOf("8000") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Netcat / HTTP") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TextButton(onClick = { viewModel.clearLog() }) { Text("Clear") } }
            )
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(host, { host = it }, label = { Text("Host") }, singleLine = true, modifier = Modifier.weight(2f))
                OutlinedTextField(port, { port = it }, label = { Text("Port") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val p = port.toIntOrNull()
                OutlinedButton(onClick = { p?.let { viewModel.tcpConnect(host, it) } }, enabled = !connected && p != null, modifier = Modifier.weight(1f)) { Text("TCP") }
                OutlinedButton(onClick = { p?.let { viewModel.tcpListen(it) } }, enabled = !connected && p != null, modifier = Modifier.weight(1f)) { Text("Listen") }
                OutlinedButton(onClick = { if (p != null && msg.isNotEmpty()) viewModel.udpSend(host, p, msg) }, enabled = p != null, modifier = Modifier.weight(1f)) { Text("UDP") }
            }
            if (connected) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(msg, { msg = it }, label = { Text("Message") }, singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = { if (msg.isNotEmpty()) { viewModel.send(msg); msg = "" } }) { Text("Send") }
                }
                OutlinedButton(onClick = { viewModel.disconnect() }) { Text("Disconnect") }
            }

            HorizontalDivider()
            Text("HTTP file server (serves app files dir)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(httpPort, { httpPort = it }, label = { Text("Port") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = { httpPort.toIntOrNull()?.let { viewModel.startHttp(it) } }) { Text("Start") }
                OutlinedButton(onClick = { viewModel.stopHttp() }) { Text("Stop") }
            }
            if (httpInfo.isNotEmpty()) Text(httpInfo, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)

            HorizontalDivider()
            LazyColumn(Modifier.weight(1f)) {
                items(log) { line -> Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

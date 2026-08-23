package com.example.flipperdroid.view

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flipperdroid.util.AppLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Journal central des actions de l'application. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(navController: NavController) {
    val context = LocalContext.current
    val entries by AppLog.entries.collectAsState()
    var exportMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { exportMsg = exportLogs(context, AppLog.dump()) }) {
                        Icon(Icons.Default.Save, contentDescription = "Export")
                    }
                    IconButton(onClick = { AppLog.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            exportMsg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }
            if (entries.isEmpty()) {
                Text("No logs yet. Actions from the tools will appear here.")
            } else {
                LazyColumn {
                    items(entries.reversed()) { line ->
                        Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun exportLogs(context: Context, content: String): String {
    return try {
        val dir = context.getExternalFilesDir("logs") ?: context.filesDir
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "flipperdroid_$ts.log")
        file.writeText(content)
        "Exported: ${file.absolutePath}"
    } catch (e: Exception) {
        "Export error: ${e.message}"
    }
}

package com.example.flipperdroid.view

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flipperdroid.flipper.FlipperNfc
import com.example.flipperdroid.flipper.FlipperSub
import com.example.flipperdroid.infrared.IrFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ouvre et analyse un fichier Flipper (.nfc, .sub, .ir) via le sélecteur de documents. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlipperFilesScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileName by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    try { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
                    catch (e: Exception) { null }
                }
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
                fileName = name
                summary = if (text == null) "Could not read file." else summarize(name, text)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flipper Files") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Open")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Open a Flipper .nfc, .sub or .ir file to parse and inspect it.",
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { launcher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.FileOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open file")
            }
            if (fileName != null) {
                Spacer(Modifier.height(16.dp))
                Text(fileName!!, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(summary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun summarize(name: String, text: String): String {
    return when {
        name.endsWith(".nfc", true) -> {
            val c = FlipperNfc.parse(text)
            buildString {
                append("Type: ${c.deviceType ?: "?"}\n")
                append("UID: ${c.uid ?: "?"}\n")
                append("ATQA: ${c.atqa ?: "?"}  SAK: ${c.sak ?: "?"}\n")
                append("Blocks: ${c.blocks.size}\n\n")
                c.blocks.take(16).forEachIndexed { i, b -> append("Block $i: $b\n") }
                if (c.blocks.size > 16) append("...")
            }
        }
        name.endsWith(".sub", true) -> FlipperSub.summarize(FlipperSub.parse(text))
        name.endsWith(".ir", true) -> {
            val buttons = IrFile.parse(text)
            "IR remote: ${buttons.size} buttons\n\n" +
                buttons.joinToString("\n") { "• ${it.name} (${it.protocol ?: it.type})" }
        }
        else -> "Unknown file type. Supported: .nfc, .sub, .ir"
    }
}

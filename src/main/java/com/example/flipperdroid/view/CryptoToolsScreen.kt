package com.example.flipperdroid.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flipperdroid.util.CryptoTools

/** Multitool crypto/encodage (hors ligne) : hachages, Base64/Hex/URL, ROT13, HMAC, JWT. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoToolsScreen(navController: NavController) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    val ops = listOf(
        "MD5", "SHA-1", "SHA-256", "SHA-512", "CRC32",
        "Base64 enc", "Base64 dec", "Hex enc", "Hex dec",
        "URL enc", "URL dec", "ROT13", "HMAC-256", "JWT decode", "ID hash"
    )
    fun compute(op: String): String = when (op) {
        "MD5" -> CryptoTools.md5(input)
        "SHA-1" -> CryptoTools.sha1(input)
        "SHA-256" -> CryptoTools.sha256(input)
        "SHA-512" -> CryptoTools.sha512(input)
        "CRC32" -> CryptoTools.crc32(input)
        "Base64 enc" -> CryptoTools.base64Encode(input)
        "Base64 dec" -> CryptoTools.base64Decode(input)
        "Hex enc" -> CryptoTools.hexEncode(input)
        "Hex dec" -> CryptoTools.hexDecode(input)
        "URL enc" -> CryptoTools.urlEncode(input)
        "URL dec" -> CryptoTools.urlDecode(input)
        "ROT13" -> CryptoTools.rot13(input)
        "HMAC-256" -> CryptoTools.hmacSha256(key, input)
        "JWT decode" -> CryptoTools.jwtDecode(input)
        "ID hash" -> CryptoTools.identifyHash(input)
        else -> ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crypto / Encoding") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                label = { Text("Input") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
            )
            OutlinedTextField(
                value = key, onValueChange = { key = it },
                label = { Text("Key (for HMAC)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            ops.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { label ->
                        OutlinedButton(
                            onClick = { output = compute(label) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) { Text(label, style = MaterialTheme.typography.labelMedium) }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (output.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Output", style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("crypto", output))
                            }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy") }
                        }
                        SelectionContainer { Text(output, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

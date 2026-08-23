package com.example.flipperdroid.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.security.SecureRandom
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorScreen(navController: NavController) {
    val context = LocalContext.current
    var length by remember { mutableStateOf(16) }
    var useUpper by remember { mutableStateOf(true) }
    var useLower by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    fun generatePassword(): String {
        val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val digits = "0123456789"
        val symbols = "!@#${'$'}%^&*()-_=+[]{};:,.<>?/|"
        var chars = ""
        if (useUpper) chars += upper
        if (useLower) chars += lower
        if (useDigits) chars += digits
        if (useSymbols) chars += symbols
        if (chars.isEmpty()) return ""
        // SecureRandom : générateur cryptographiquement sûr (indispensable pour des mots de passe)
        val rng = SecureRandom()
        return (1..length).map { chars[rng.nextInt(chars.length)] }.joinToString("")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Password Generator") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
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
            Text("Generate a secure password and copy it to the clipboard.", style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Length: $length", modifier = Modifier.weight(1f))
                Slider(
                    value = length.toFloat(),
                    onValueChange = { length = it.toInt() },
                    valueRange = 8f..64f,
                    steps = 55,
                    modifier = Modifier.weight(2f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useUpper, onCheckedChange = { useUpper = it })
                Text("Uppercase", modifier = Modifier.weight(1f))
                Checkbox(checked = useLower, onCheckedChange = { useLower = it })
                Text("Lowercase", modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useDigits, onCheckedChange = { useDigits = it })
                Text("Digits", modifier = Modifier.weight(1f))
                Checkbox(checked = useSymbols, onCheckedChange = { useSymbols = it })
                Text("Symbols", modifier = Modifier.weight(1f))
            }
            Button(onClick = {
                password = generatePassword()
                copied = false
            }) {
                Text("Generate")
            }
            if (password.isNotEmpty()) {
                OutlinedTextField(
                    value = password,
                    onValueChange = {},
                    label = { Text("Generated password") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("password", password)
                    clipboard.setPrimaryClip(clip)
                    copied = true
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (copied) "Copied!" else "Copy")
                }
                // Génération et affichage du QR code sécurisé
                Spacer(Modifier.height(16.dp))
                if (password.length <= 128) {
                    val qrBitmap = remember(password) {
                        try { generateQrCodeBitmap(password) } catch (_: Exception) { null }
                    }
                    if (qrBitmap != null) {
                        Text("Password QR Code:", fontSize = 16.sp)
                        Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(200.dp))
                    } else {
                        Text("Unable to generate QR code.", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Text("Password too long to generate a QR code.", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// Fonction utilitaire pour générer un QR code Bitmap
fun generateQrCodeBitmap(text: String, size: Int = 512): Bitmap? {
    return try {
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        null
    }
} 
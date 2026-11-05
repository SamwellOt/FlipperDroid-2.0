package com.example.flipperdroid.view

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.PersistableBundle
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.flipperdroid.util.SecureWindow
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/** État de génération du QR code du mot de passe. */
private sealed interface QrState {
    object Idle : QrState
    object TooLong : QrState
    object Failed : QrState
    class Ready(val bitmap: Bitmap) : QrState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorScreen(navController: NavController) {
    // Empêche captures d'écran et aperçu des tâches récentes : le mot de passe est affiché
    SecureWindow()

    val context = LocalContext.current
    var length by remember { mutableStateOf(16) }
    var useUpper by remember { mutableStateOf(true) }
    var useLower by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var genError by remember { mutableStateOf<String?>(null) }

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

    // QR code généré hors thread principal ; relancé à chaque nouveau mot de passe
    val qrState by produceState<QrState>(QrState.Idle, password) {
        value = QrState.Idle
        if (password.isEmpty()) return@produceState
        if (password.length > 128) {
            value = QrState.TooLong
            return@produceState
        }
        val bmp = withContext(Dispatchers.Default) { generateQrCodeBitmap(password) }
        value = if (bmp != null) QrState.Ready(bmp) else QrState.Failed
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
                val generated = generatePassword()
                if (generated.isEmpty()) {
                    genError = "Select at least one character set (uppercase, lowercase, digits or symbols)."
                    password = ""
                } else {
                    genError = null
                    password = generated
                }
                copied = false
            }) {
                Text("Generate")
            }
            genError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                    if (Build.VERSION.SDK_INT >= 33) {
                        // Marque le contenu comme sensible : masqué dans l'aperçu du presse-papiers
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    clipboard.setPrimaryClip(clip)
                    copied = true
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (copied) "Copied!" else "Copy")
                }
                // Affichage du QR code du mot de passe
                Spacer(Modifier.height(16.dp))
                when (val state = qrState) {
                    is QrState.Ready -> {
                        Text("Password QR Code:", fontSize = 16.sp)
                        Image(
                            bitmap = state.bitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(200.dp)
                        )
                    }
                    QrState.TooLong ->
                        Text("Password too long to generate a QR code.", color = MaterialTheme.colorScheme.error)
                    QrState.Failed ->
                        Text("Unable to generate QR code.", color = MaterialTheme.colorScheme.error)
                    QrState.Idle ->
                        CircularProgressIndicator(Modifier.size(32.dp))
                }
            }
        }
    }
}

// Fonction utilitaire pour générer un QR code Bitmap (appelée hors thread principal)
fun generateQrCodeBitmap(text: String, size: Int = 512): Bitmap? {
    return try {
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        // Remplissage d'un tableau de pixels puis une seule écriture dans le Bitmap
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val row = y * size
            for (x in 0 until size) {
                pixels[row + x] = if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) {
        null
    }
}

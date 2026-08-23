package com.example.flipperdroid.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * BadUSB "vrai" via le mode USB Gadget (root) : le téléphone est branché sur
 * un PC et agit comme un clavier HID en écrivant des rapports dans /dev/hidg0.
 * Exécute des scripts au format DuckyScript.
 *
 * Prérequis : root + noyau avec la fonction HID gadget exposée sur /dev/hidgX.
 * Le chemin exact et la config du gadget dépendent de l'appareil/ROM ; on
 * vérifie honnêtement la disponibilité et on le signale.
 */
class RootBadUsbViewModel : ViewModel() {

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _script = MutableStateFlow(SAMPLE_SCRIPT)
    val script: StateFlow<String> = _script

    private var job: Job? = null

    fun updateScript(s: String) { _script.value = s }

    /** Vérifie root + présence d'un périphérique HID gadget. */
    fun checkEnvironment() {
        viewModelScope.launch(Dispatchers.IO) {
            val root = hasRoot()
            val dev = if (root) firstHidDevice() else null
            withContext(Dispatchers.Main) {
                _status.value = when {
                    !root -> "No root access — USB Gadget BadUSB requires root."
                    dev == null -> "Root OK, but no /dev/hidgX found. Kernel HID gadget not enabled on this device."
                    else -> "Ready: root + $dev available."
                }
            }
        }
    }

    fun runScript() {
        if (_isRunning.value) return
        job = viewModelScope.launch(Dispatchers.IO) {
            _isRunning.value = true
            try {
                if (!hasRoot()) { _status.value = "No root access."; return@launch }
                val dev = firstHidDevice()
                if (dev == null) { _status.value = "No /dev/hidgX device (HID gadget not enabled)."; return@launch }

                _status.value = "Opening $dev..."
                // Shell root persistant dont stdin est redirigé vers le device HID.
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $dev"))
                val out = process.outputStream
                _status.value = "Executing DuckyScript..."
                com.example.flipperdroid.util.AppLog.log("BadUSB", "Executing DuckyScript on $dev")
                executeDucky(_script.value, out)
                try { out.flush(); out.close() } catch (_: Exception) {}
                try { process.destroy() } catch (_: Exception) {}
                _status.value = "Script finished."
            } catch (e: Exception) {
                _status.value = "Error: ${e.message}"
                Log.e("RootBadUsb", "runScript", e)
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stop() {
        _isRunning.value = false
        job?.cancel()
        job = null
        _status.value = "Stopped."
    }

    // --- Moteur DuckyScript ---

    private suspend fun executeDucky(script: String, out: OutputStream) {
        var defaultDelay = 0L
        val lines = script.split("\n")
        for (raw in lines) {
            if (!isActive || !_isRunning.value) break
            val line = raw.trim()
            if (line.isEmpty()) continue
            val parts = line.split(" ", limit = 2)
            val cmd = parts[0].uppercase()
            val arg = if (parts.size > 1) parts[1] else ""
            when (cmd) {
                "REM" -> { /* commentaire */ }
                "DELAY" -> delay(arg.trim().toLongOrNull() ?: 0L)
                "DEFAULT_DELAY", "DEFAULTDELAY" -> defaultDelay = arg.trim().toLongOrNull() ?: 0L
                "STRING" -> typeString(arg, out)
                "STRINGLN" -> { typeString(arg, out); sendKey(out, 0, KEY_ENTER) }
                "ENTER" -> sendKey(out, 0, KEY_ENTER)
                "TAB" -> sendKey(out, 0, 0x2B)
                "SPACE" -> sendKey(out, 0, 0x2C)
                "ESCAPE", "ESC" -> sendKey(out, 0, 0x29)
                "BACKSPACE" -> sendKey(out, 0, 0x2A)
                "DELETE" -> sendKey(out, 0, 0x4C)
                "UP", "UPARROW" -> sendKey(out, 0, 0x52)
                "DOWN", "DOWNARROW" -> sendKey(out, 0, 0x51)
                "LEFT", "LEFTARROW" -> sendKey(out, 0, 0x50)
                "RIGHT", "RIGHTARROW" -> sendKey(out, 0, 0x4F)
                "GUI", "WINDOWS", "COMMAND" -> sendCombo(out, MOD_GUI, arg)
                "CTRL", "CONTROL" -> sendCombo(out, MOD_CTRL, arg)
                "ALT" -> sendCombo(out, MOD_ALT, arg)
                "SHIFT" -> sendCombo(out, MOD_SHIFT, arg)
                "CTRL-ALT", "CTRL-ALT-DELETE" -> {
                    if (arg.equals("DELETE", true) || cmd == "CTRL-ALT-DELETE")
                        sendKey(out, MOD_CTRL or MOD_ALT, 0x4C)
                    else sendCombo(out, MOD_CTRL or MOD_ALT, arg)
                }
                "CTRL-SHIFT" -> sendCombo(out, MOD_CTRL or MOD_SHIFT, arg)
                else -> {
                    // Touches de fonction F1..F12
                    if (cmd.matches(Regex("F([1-9]|1[0-2])"))) {
                        val n = cmd.substring(1).toInt()
                        sendKey(out, 0, 0x3A + (n - 1))
                    }
                }
            }
            if (defaultDelay > 0) delay(defaultDelay)
        }
    }

    private suspend fun sendCombo(out: OutputStream, modifiers: Int, arg: String) {
        val token = arg.trim()
        if (token.isEmpty()) { sendKey(out, modifiers, 0); return }
        // Combos multiples (ex: "CTRL SHIFT ESC") : accumule les modificateurs
        val tokens = token.split(" ")
        var mods = modifiers
        var key = 0
        for (t in tokens) {
            when (t.uppercase()) {
                "CTRL", "CONTROL" -> mods = mods or MOD_CTRL
                "ALT" -> mods = mods or MOD_ALT
                "SHIFT" -> mods = mods or MOD_SHIFT
                "GUI", "WINDOWS" -> mods = mods or MOD_GUI
                "ENTER" -> key = KEY_ENTER
                "DELETE" -> key = 0x4C
                "TAB" -> key = 0x2B
                else -> if (t.length == 1) { key = charToKeycode(t[0]).second }
            }
        }
        sendKey(out, mods, key)
    }

    private suspend fun typeString(text: String, out: OutputStream) {
        for (c in text) {
            val (mod, code) = charToKeycode(c)
            if (code != 0) sendKey(out, mod, code)
        }
    }

    /** Envoie un rapport HID de 8 octets (appui) puis un rapport nul (relâchement). */
    private suspend fun sendKey(out: OutputStream, modifiers: Int, keycode: Int) {
        val report = byteArrayOf(modifiers.toByte(), 0, keycode.toByte(), 0, 0, 0, 0, 0)
        out.write(report); out.flush()
        delay(5)
        out.write(ByteArray(8)); out.flush() // relâchement
        delay(5)
    }

    // --- Environnement root ---

    private fun hasRoot(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val o = BufferedReader(InputStreamReader(p.inputStream)).readText()
            p.waitFor()
            o.contains("uid=0")
        } catch (e: Exception) { false }
    }

    private fun firstHidDevice(): String? {
        for (i in 0..3) {
            val path = "/dev/hidg$i"
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls $path"))
                val out = BufferedReader(InputStreamReader(p.inputStream)).readText().trim()
                p.waitFor()
                if (out == path) return path
            } catch (_: Exception) {}
        }
        return null
    }

    companion object {
        private const val MOD_CTRL = 0x01
        private const val MOD_SHIFT = 0x02
        private const val MOD_ALT = 0x04
        private const val MOD_GUI = 0x08
        private const val KEY_ENTER = 0x28

        private val SAMPLE_SCRIPT = """
            REM Exemple : ouvre le Bloc-notes sur Windows
            DELAY 1000
            GUI r
            DELAY 300
            STRING notepad
            ENTER
            DELAY 600
            STRING Hello from FlipperDroid BadUSB!
        """.trimIndent()

        /** Convertit un caractère en (modificateur, keycode) HID US. */
        fun charToKeycode(c: Char): Pair<Int, Int> = when (c) {
            in 'a'..'z' -> 0 to (c - 'a' + 0x04)
            in 'A'..'Z' -> MOD_SHIFT to (c.lowercaseChar() - 'a' + 0x04)
            '1' -> 0 to 0x1E; '2' -> 0 to 0x1F; '3' -> 0 to 0x20; '4' -> 0 to 0x21
            '5' -> 0 to 0x22; '6' -> 0 to 0x23; '7' -> 0 to 0x24; '8' -> 0 to 0x25
            '9' -> 0 to 0x26; '0' -> 0 to 0x27
            ' ' -> 0 to 0x2C; '\n' -> 0 to 0x28; '\t' -> 0 to 0x2B
            '-' -> 0 to 0x2D; '_' -> MOD_SHIFT to 0x2D
            '=' -> 0 to 0x2E; '+' -> MOD_SHIFT to 0x2E
            '[' -> 0 to 0x2F; '{' -> MOD_SHIFT to 0x2F
            ']' -> 0 to 0x30; '}' -> MOD_SHIFT to 0x30
            '\\' -> 0 to 0x31; '|' -> MOD_SHIFT to 0x31
            ';' -> 0 to 0x33; ':' -> MOD_SHIFT to 0x33
            '\'' -> 0 to 0x34; '"' -> MOD_SHIFT to 0x34
            '`' -> 0 to 0x35; '~' -> MOD_SHIFT to 0x35
            ',' -> 0 to 0x36; '<' -> MOD_SHIFT to 0x36
            '.' -> 0 to 0x37; '>' -> MOD_SHIFT to 0x37
            '/' -> 0 to 0x38; '?' -> MOD_SHIFT to 0x38
            '!' -> MOD_SHIFT to 0x1E; '@' -> MOD_SHIFT to 0x1F; '#' -> MOD_SHIFT to 0x20
            '$' -> MOD_SHIFT to 0x21; '%' -> MOD_SHIFT to 0x22; '^' -> MOD_SHIFT to 0x23
            '&' -> MOD_SHIFT to 0x24; '*' -> MOD_SHIFT to 0x25
            '(' -> MOD_SHIFT to 0x26; ')' -> MOD_SHIFT to 0x27
            else -> 0 to 0
        }
    }
}

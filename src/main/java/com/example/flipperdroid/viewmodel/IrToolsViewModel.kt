package com.example.flipperdroid.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.ConsumerIrManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flipperdroid.infrared.IrFile
import com.example.flipperdroid.infrared.IrProtocols
import com.example.flipperdroid.infrared.PowerCodes
import com.example.flipperdroid.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Outils IR offensifs : TV-B-Gone (balayage de tous les codes d'extinction) et
 * brute-force de commandes (cartographier une télécommande inconnue). Pour tests
 * autorisés uniquement.
 */
class IrToolsViewModel(app: Application) : AndroidViewModel(app) {

    private val irManager =
        app.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val hasEmitter: Boolean get() = irManager?.hasIrEmitter() == true

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress

    private val _bruteCommand = MutableStateFlow(-1)
    val bruteCommand: StateFlow<Int> = _bruteCommand

    private var job: Job? = null

    /** Émet tous les codes power connus (fichiers .ir empaquetés + liste curée). */
    fun tvBGone(rounds: Int = 1) {
        if (_running.value) return
        job = viewModelScope.launch(Dispatchers.IO) {
            _running.value = true
            try {
                val codes = collectPowerSignals()
                _progress.value = "TV-B-Gone: ${codes.size} codes chargés"
                var sent = 0
                repeat(rounds) {
                    for ((label, signal) in codes) {
                        if (!isActive) return@launch
                        val (freq, pattern) = signal
                        try {
                            irManager?.transmit(freq, pattern)
                            sent++
                        } catch (_: Exception) {}
                        _progress.value = "Envoyé $sent — $label"
                        delay(120)
                    }
                }
                _progress.value = "TV-B-Gone terminé : $sent émissions"
                AppLog.log("IRTools", "TV-B-Gone fired $sent codes")
            } finally {
                _running.value = false
            }
        }
    }

    private fun collectPowerSignals(): List<Pair<String, Pair<Int, IntArray>>> {
        val out = mutableListOf<Pair<String, Pair<Int, IntArray>>>()
        try {
            val assets = getApplication<Application>().assets
            val files = assets.list("infrared")?.filter { it.endsWith(".ir") } ?: emptyList()
            for (file in files) {
                val text = assets.open("infrared/$file").bufferedReader().use { it.readText() }
                IrFile.parse(text)
                    .filter {
                        it.name.contains("power", true) ||
                            it.name.equals("on", true) ||
                            it.name.equals("off", true)
                    }
                    .forEach { b ->
                        b.toSignal()?.let { out.add("${file.removeSuffix(".ir")}/${b.name}" to it) }
                    }
            }
        } catch (_: Exception) {}
        for (pc in PowerCodes.EXTRA) {
            pc.toSignal()?.let { out.add(pc.brand to it) }
        }
        return out
    }

    /** Balaye les commandes [start]..[end] pour (protocole, adresse). */
    fun bruteForce(protocol: String, address: Int, start: Int, end: Int, delayMs: Long) {
        if (_running.value) return
        job = viewModelScope.launch(Dispatchers.IO) {
            _running.value = true
            try {
                var cmd = start
                while (cmd <= end && isActive) {
                    _bruteCommand.value = cmd
                    IrProtocols.encode(protocol, address, cmd)?.let { (freq, pattern) ->
                        try { irManager?.transmit(freq, pattern) } catch (_: Exception) {}
                    }
                    val hex = cmd.toString(16).uppercase()
                    _progress.value = "Brute $protocol addr=0x${address.toString(16).uppercase()} cmd=0x$hex ($cmd)"
                    delay(delayMs)
                    cmd++
                }
                if (isActive) _progress.value = "Brute-force terminé (jusqu'à 0x${end.toString(16).uppercase()})"
            } finally {
                _running.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        _running.value = false
        if (_progress.value.isNotEmpty()) _progress.value = "${_progress.value} — arrêté"
    }
}

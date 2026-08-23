package com.example.flipperdroid.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.ConsumerIrManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flipperdroid.infrared.IrButton
import com.example.flipperdroid.infrared.IrFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Télécommande universelle pilotée par une base de codes au format .ir (Flipper).
 * Charge les remotes empaquetés (assets/infrared/) ou importés par l'utilisateur,
 * et transmet chaque bouton via l'émetteur IR.
 */
class IrRemoteViewModel(app: Application) : AndroidViewModel(app) {

    private val irManager =
        app.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    private val _remotes = MutableStateFlow<List<String>>(emptyList())
    val remotes: StateFlow<List<String>> = _remotes

    private val _currentRemote = MutableStateFlow<String?>(null)
    val currentRemote: StateFlow<String?> = _currentRemote

    private val _buttons = MutableStateFlow<List<IrButton>>(emptyList())
    val buttons: StateFlow<List<IrButton>> = _buttons

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    val hasEmitter: Boolean get() = irManager?.hasIrEmitter() == true

    fun loadAssetList() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = getApplication<Application>().assets.list("infrared")
                    ?.filter { it.endsWith(".ir") }?.sorted() ?: emptyList()
                _remotes.value = files
                if (!hasEmitter) _status.value = "No IR emitter on this device (patterns won't transmit)."
            } catch (e: Exception) {
                _status.value = "Error listing remotes: ${e.message}"
            }
        }
    }

    fun loadRemote(assetName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = getApplication<Application>().assets.open("infrared/$assetName")
                    .bufferedReader().use { it.readText() }
                val buttons = IrFile.parse(text)
                _buttons.value = buttons
                _currentRemote.value = assetName
                _status.value = "${buttons.size} buttons loaded from $assetName"
            } catch (e: Exception) {
                _status.value = "Error loading $assetName: ${e.message}"
            }
        }
    }

    /** Charge un remote importé par l'utilisateur (contenu déjà lu depuis un Uri). */
    fun loadFromText(name: String, text: String) {
        val buttons = IrFile.parse(text)
        _buttons.value = buttons
        _currentRemote.value = name
        _status.value = if (buttons.isEmpty()) "No valid buttons in $name"
        else "${buttons.size} buttons loaded from $name"
    }

    fun transmit(button: IrButton) {
        val signal = button.toSignal()
        if (signal == null) {
            _status.value = "Cannot encode '${button.name}' (protocol ${button.protocol ?: button.type})"
            return
        }
        val (freq, pattern) = signal
        try {
            irManager?.transmit(freq, pattern)
            _status.value = "Sent '${button.name}' (${freq / 1000} kHz)"
            com.example.flipperdroid.util.AppLog.log("IR", "Sent '${button.name}' via ${button.protocol ?: button.type}")
        } catch (e: Exception) {
            _status.value = "Transmit failed: ${e.message}"
        }
    }

    /** "TV-B-Gone" simplifié : envoie tous les boutons Power de tous les remotes. */
    fun powerSweep() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val assets = getApplication<Application>().assets
                val files = assets.list("infrared")?.filter { it.endsWith(".ir") } ?: emptyList()
                var sent = 0
                for (file in files) {
                    val text = assets.open("infrared/$file").bufferedReader().use { it.readText() }
                    val powers = IrFile.parse(text).filter { it.name.contains("power", true) }
                    for (p in powers) {
                        p.toSignal()?.let { (freq, pattern) ->
                            try { irManager?.transmit(freq, pattern); sent++ } catch (_: Exception) {}
                            delay(200)
                        }
                    }
                }
                withContext(Dispatchers.Main) { _status.value = "Power sweep: $sent codes sent." }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _status.value = "Power sweep error: ${e.message}" }
            }
        }
    }
}

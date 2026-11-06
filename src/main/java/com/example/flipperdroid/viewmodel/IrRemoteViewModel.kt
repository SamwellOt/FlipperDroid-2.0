package com.example.flipperdroid.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.ConsumerIrManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flipperdroid.infrared.IrButton
import com.example.flipperdroid.infrared.IrFile
import com.example.flipperdroid.infrared.IrProtocols
import com.example.flipperdroid.infrared.PowerCodes
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

    // Remotes importés en mémoire (nom -> contenu .ir), fusionnés aux assets.
    private val importedTexts = mutableMapOf<String, String>()

    fun loadAssetList() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = getApplication<Application>().assets.list("infrared")
                    ?.filter { it.endsWith(".ir") }?.sorted() ?: emptyList()
                _remotes.value = files
                if (!hasEmitter) _status.value = IrProtocols.NO_EMITTER_MSG
            } catch (e: Exception) {
                _status.value = "Error listing remotes: ${e.message}"
            }
        }
    }

    fun loadRemote(assetName: String) {
        val imported = importedTexts[assetName]
        if (imported != null) {
            loadFromText(assetName, imported)
            return
        }
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

    /**
     * Import en masse : ajoute tous les .ir d'un dossier à la liste des remotes.
     * Le parsing se fait sur Dispatchers.Default : un gros dossier Flipper-IRDB ne doit pas
     * figer l'UI. Les écritures d'état restent sur le thread principal.
     */
    fun importFolder(files: List<Pair<String, String>>) {
        viewModelScope.launch {
            val valid = withContext(Dispatchers.Default) {
                files.filter { (_, text) -> IrFile.parse(text).isNotEmpty() }
            }
            for ((name, text) in valid) importedTexts[name] = text
            val merged = _remotes.value.toMutableSet()
            merged.addAll(importedTexts.keys)
            _remotes.value = merged.sorted()
            _status.value = if (valid.isEmpty()) "No valid .ir files found in folder" else "Imported ${valid.size} remote(s) from folder"
        }
    }

    /**
     * Charge un remote importé par l'utilisateur (contenu déjà lu depuis un Uri).
     * Le parsing se fait sur Dispatchers.Default (voir [importFolder]).
     */
    fun loadFromText(name: String, text: String) {
        viewModelScope.launch {
            val buttons = withContext(Dispatchers.Default) { IrFile.parse(text) }
            _buttons.value = buttons
            _currentRemote.value = name
            _status.value = if (buttons.isEmpty()) "No valid buttons in $name" else "${buttons.size} buttons loaded from $name"
        }
    }

    fun transmit(button: IrButton) {
        val manager = irManager
        if (manager == null || !manager.hasIrEmitter()) {
            _status.value = IrProtocols.NO_EMITTER_MSG
            return
        }
        val signal = button.toSignal()
        if (signal == null) {
            _status.value = "Cannot encode '${button.name}' (protocol ${button.protocol ?: button.type})"
            return
        }
        val (freq, pattern) = signal
        viewModelScope.launch(Dispatchers.IO) {
            try {
                manager.transmit(freq, pattern)
                _status.value = "Sent '${button.name}' (${freq / 1000} kHz)"
                com.example.flipperdroid.util.AppLog.log("IR", "Sent '${button.name}' via ${button.protocol ?: button.type}")
            } catch (e: Exception) {
                _status.value = "Transmit failed: ${e.message}"
            }
        }
    }

    /**
     * "TV-B-Gone" simplifié : envoie les boutons power **bascule** de tous les remotes.
     * Les paires on/off explicites sont exclues et les codes identiques (protocole, adresse,
     * commande) ne sont envoyés qu'une fois. Les boutons parsed partent en rafale répétée.
     */
    fun powerSweep() {
        viewModelScope.launch(Dispatchers.IO) {
            val manager = irManager
            if (manager == null || !manager.hasIrEmitter()) {
                withContext(Dispatchers.Main) { _status.value = IrProtocols.NO_EMITTER_MSG }
                return@launch
            }
            try {
                val assets = getApplication<Application>().assets
                val files = assets.list("infrared")?.filter { it.endsWith(".ir") } ?: emptyList()
                val all = mutableListOf<IrButton>()
                for (file in files) {
                    val text = assets.open("infrared/$file").bufferedReader().use { it.readText() }
                    all += IrFile.parse(text)
                }
                val powers = PowerCodes.distinctPowerToggles(all)
                var sent = 0
                var lastError: String? = null
                for (p in powers) {
                    // Parsed : rafale répétée (une trame isolée est ignorée). Raw : capture telle quelle.
                    val signal = (if (p.type.equals("parsed", ignoreCase = true)) p.toBurst() else p.toSignal()) ?: continue
                    try {
                        manager.transmit(signal.first, signal.second)
                        sent++
                    } catch (e: Exception) {
                        lastError = e.message ?: e.javaClass.simpleName
                    }
                    delay(200)
                }
                val errTail = lastError?.let { " Last error: $it" } ?: ""
                withContext(Dispatchers.Main) { _status.value = "Power sweep: $sent codes sent.$errTail" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _status.value = "Power sweep error: ${e.message}" }
            }
        }
    }
}

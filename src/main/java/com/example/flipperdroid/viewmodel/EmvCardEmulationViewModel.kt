package com.example.flipperdroid.viewmodel

import android.app.Application
import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import androidx.lifecycle.AndroidViewModel
import com.example.flipperdroid.nfc.EmulationStore
import com.example.flipperdroid.nfc.EmvCardEmulationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrôle l'émulation HCE d'une carte ISO-DEP capturée : enregistre dynamiquement
 * ses AID auprès du service [EmvCardEmulationService] qui rejoue les réponses.
 */
class EmvCardEmulationViewModel(app: Application) : AndroidViewModel(app) {

    private val _isEmulationActive = MutableStateFlow(false)
    val isEmulationActive: StateFlow<Boolean> = _isEmulationActive

    private val _capturedLabel = MutableStateFlow<String?>(null)
    val capturedLabel: StateFlow<String?> = _capturedLabel

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    init { refresh() }

    fun refresh() {
        _capturedLabel.value = EmulationStore.label(getApplication<Application>())
    }

    private fun cardEmulation(): Pair<CardEmulation, ComponentName>? {
        val ctx = getApplication<Application>()
        val adapter = NfcAdapter.getDefaultAdapter(ctx) ?: return null
        return CardEmulation.getInstance(adapter) to
            ComponentName(ctx, EmvCardEmulationService::class.java)
    }

    fun startEmulation() {
        val ctx = getApplication<Application>()
        val aids = EmulationStore.aids(ctx)
        if (aids.isEmpty()) {
            _status.value = "No captured ISO-DEP card. Capture one from NFC Reader first."
            return
        }
        val ce = cardEmulation()
        if (ce == null) { _status.value = "No NFC on this device."; return }
        val ok = try {
            ce.first.registerAidsForService(ce.second, CardEmulation.CATEGORY_OTHER, aids)
        } catch (e: Exception) { false }
        _isEmulationActive.value = ok
        _status.value = if (ok)
            "Emulating (AIDs: ${aids.joinToString()}). Keep the screen on & unlocked and hold the " +
                "phone to the reader. Experimental — works only for static ISO-DEP cards."
        else "Failed to register AIDs for emulation."
    }

    fun stopEmulation() {
        cardEmulation()?.let { (ce, comp) ->
            try { ce.removeAidsForService(comp, CardEmulation.CATEGORY_OTHER) } catch (_: Exception) {}
        }
        _isEmulationActive.value = false
        _status.value = "Emulation stopped."
    }

    fun clearCapture() {
        stopEmulation()
        EmulationStore.clear(getApplication<Application>())
        refresh()
        _status.value = "Captured card cleared."
    }
}

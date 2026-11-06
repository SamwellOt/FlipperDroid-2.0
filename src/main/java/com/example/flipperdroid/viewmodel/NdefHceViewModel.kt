package com.example.flipperdroid.viewmodel

import android.app.Application
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import androidx.lifecycle.AndroidViewModel
import com.example.flipperdroid.nfc.NdefHceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Configure la tag NDEF émulée (HCE Type 4) servie par NdefHceService. */
class NdefHceViewModel(app: Application) : AndroidViewModel(app) {

    private val _label = MutableStateFlow(NdefHceStore.label(app))
    val label: StateFlow<String?> = _label

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    fun setUri(uri: String) {
        try {
            val msg = NdefMessage(arrayOf(NdefRecord.createUri(uri)))
            store(msg, "URI: $uri")
        } catch (e: Exception) { _status.value = "Error: ${e.message}" }
    }

    fun setText(text: String) {
        try {
            val msg = NdefMessage(arrayOf(NdefRecord.createTextRecord("en", text)))
            store(msg, "Text: $text")
        } catch (e: Exception) { _status.value = "Error: ${e.message}" }
    }

    /** Enregistre le message si sa taille tient dans le fichier NDEF émulé (1022 octets max). */
    private fun store(msg: NdefMessage, label: String) {
        val bytes = msg.toByteArray()
        if (bytes.size > NdefHceStore.MAX_MESSAGE_BYTES) {
            _status.value = "Message too large: ${bytes.size} bytes (maximum ${NdefHceStore.MAX_MESSAGE_BYTES})."
            return
        }
        NdefHceStore.save(getApplication<Application>(), label, bytes)
        _label.value = label
        _status.value = "Ready. Hold another phone/reader (reader mode) to this one."
    }

    fun clear() {
        NdefHceStore.clear(getApplication<Application>())
        _label.value = null
        _status.value = "Cleared. Emulated tag is now empty."
    }
}

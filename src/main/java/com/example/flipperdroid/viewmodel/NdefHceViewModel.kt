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
            NdefHceStore.save(getApplication<Application>(), "URI: $uri", msg.toByteArray())
            _label.value = "URI: $uri"
            _status.value = "Ready. Hold another phone/reader (reader mode) to this one."
        } catch (e: Exception) { _status.value = "Error: ${e.message}" }
    }

    fun setText(text: String) {
        try {
            val msg = NdefMessage(arrayOf(NdefRecord.createTextRecord("en", text)))
            NdefHceStore.save(getApplication<Application>(), "Text: $text", msg.toByteArray())
            _label.value = "Text: $text"
            _status.value = "Ready. Hold another phone/reader (reader mode) to this one."
        } catch (e: Exception) { _status.value = "Error: ${e.message}" }
    }

    fun clear() {
        NdefHceStore.clear(getApplication<Application>())
        _label.value = null
        _status.value = "Cleared. Emulated tag is now empty."
    }
}

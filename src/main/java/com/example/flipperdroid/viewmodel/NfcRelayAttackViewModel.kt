package com.example.flipperdroid.viewmodel

import android.nfc.Tag
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flipperdroid.nfc.NfcRelayAttack
import com.example.flipperdroid.nfc.RelayCapture
import com.example.flipperdroid.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NfcRelayAttackViewModel : androidx.lifecycle.ViewModel() {

    private val _capturedData = MutableStateFlow<List<RelayCapture>>(emptyList())
    val capturedData: StateFlow<List<RelayCapture>> = _capturedData

    private val _relayLog = MutableStateFlow<String>("")
    val relayLog: StateFlow<String> = _relayLog

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing

    private val _relayAddress = MutableStateFlow("127.0.0.1")
    val relayAddress: StateFlow<String> = _relayAddress

    private val _relayPort = MutableStateFlow(6669)
    val relayPort: StateFlow<Int> = _relayPort

    fun addLog(message: String) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val newLog = "[$timestamp] $message\n" + _relayLog.value
        _relayLog.value = newLog.take(5000)
        AppLog.i("NFC-Relay: $message")
    }

    fun captureRelayAttack(tag: Tag) {
        if (_isCapturing.value) return

        viewModelScope.launch {
            _isCapturing.value = true
            addLog("Starting NFC relay attack capture...")

            try {
                val captures = NfcRelayAttack.captureAndRelay(
                    tag,
                    _relayAddress.value,
                    _relayPort.value
                )

                _capturedData.value = captures
                addLog("Captured ${captures.size} APDU exchanges")

                captures.forEach { capture ->
                    addLog("${capture.description}: ${capture.command.size} bytes sent")
                }

            } catch (e: Exception) {
                addLog("Capture error: ${e.message}")
            } finally {
                _isCapturing.value = false
            }
        }
    }

    fun replayCapture(tag: Tag, capture: RelayCapture) {
        viewModelScope.launch {
            try {
                val response = NfcRelayAttack.replayCapture(tag, capture)
                if (response != null) {
                    addLog("Replayed: ${capture.description} -> ${response.size} bytes")
                } else {
                    addLog("Replay failed for: ${capture.description}")
                }
            } catch (e: Exception) {
                addLog("Replay error: ${e.message}")
            }
        }
    }

    fun setRelayServer(address: String, port: Int) {
        _relayAddress.value = address
        _relayPort.value = port
        addLog("Relay server set to: $address:$port")
    }

    fun generatePlayback(): String {
        return NfcRelayAttack.generateRelayPlayback(_capturedData.value)
    }

    fun clearCaptures() {
        _capturedData.value = emptyList()
        _relayLog.value = ""
        addLog("Captures cleared")
    }

    fun exportCaptures(): String {
        val sb = StringBuilder()
        sb.append("NFC Relay Attack Export\n")
        sb.append("=======================\n")
        sb.append("Relay Server: ${_relayAddress.value}:${_relayPort.value}\n")
        sb.append("Captured: ${_capturedData.value.size} exchanges\n\n")

        _capturedData.value.forEach { capture ->
            sb.append("${capture.description}\n")
            sb.append("CMD: ${capture.command.joinToString("") { "%02X".format(it) }}\n")
            sb.append("RSP: ${capture.response.joinToString("") { "%02X".format(it) }}\n\n")
        }

        return sb.toString()
    }
}
